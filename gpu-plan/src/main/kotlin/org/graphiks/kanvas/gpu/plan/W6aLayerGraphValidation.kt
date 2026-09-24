package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.rebaseAtOriginI32OrNull

/** Per-target initialization, ordering, attachment/sampling and restore validation at freeze. */
internal fun validateW6aLayerTopology(
    resources: List<PlanResource>,
    passes: List<PlanPass>,
    dependencies: List<PlanPassDependency>,
    extent: SizeI32,
    visualCountI32: Int,
    copyRowAlignmentI32: Int,
) {
    val byId = resources.associateBy { it.id }
    val root = resources.single { it.role == PlanResourceRole.LogicalTarget }
    require(root.copyExtent() == extent)
    val layers = resources.filter { it.role == PlanResourceRole.LayerTarget }
    val initialized = mutableSetOf<PlanResourceId>()
    val restored = mutableSetOf<PlanResourceId>()
    val commands = mutableListOf<Int>()
    val versions = mutableMapOf<PlanResourceId, Long>()
    val preparedMasks = mutableSetOf<PlanResourceId>()
    val sealedPictureSources = mutableSetOf<PlanResourceId>()
    fun validatePictureTerminal(operand: PictureCompositeOperandsV1, source: PlanResourceId,
        destination: PlanResourceId, indexI32: Int): Long {
        require(operand.source == source && operand.sourceGenerationI64 == versions[source] &&
            operand.destinationVersionBefore.valueI64 == versions[destination] &&
            operand.load == AttachmentLoadPlan.Load && operand.store == AttachmentStorePlan.Store)
        val sourceExtent = requireNotNull(byId.getValue(source).copyExtent())
        val targetExtent = requireNotNull(byId.getValue(destination).copyExtent())
        val rect = operand.copySourceBoundsTargetI32()
        val origin = operand.copyDestinationOriginTargetI32()
        require(rect.left >= 0 && rect.top >= 0 && rect.right <= sourceExtent.width && rect.bottom <= sourceExtent.height &&
            origin.x >= 0 && origin.y >= 0 && origin.x.toLong() + rect.width() <= targetExtent.width &&
            origin.y.toLong() + rect.height() <= targetExtent.height)
        val sourceSampleOffset = operand.copySourceSampleOffsetTargetLocalI32()
        require(sourceSampleOffset == Point2I32(
            Math.subtractExact(rect.left, origin.x),
            Math.subtractExact(rect.top, origin.y),
        ))
        val sourceInDestination = RectI32(origin.x, origin.y,
            Math.addExact(origin.x, rect.width()), Math.addExact(origin.y, rect.height()))
        operand.copyCompositeScissorTargetLocalI32()?.let { scissor ->
            require(scissor.left >= sourceInDestination.left && scissor.top >= sourceInDestination.top &&
                scissor.right <= sourceInDestination.right && scissor.bottom <= sourceInDestination.bottom &&
                scissor.left >= 0 && scissor.top >= 0 && scissor.right <= targetExtent.width && scissor.bottom <= targetExtent.height)
        }
        (operand.blend as? BlendPlan.DestinationReadV1)?.let { blend ->
            val copy = passes.getOrNull(indexI32 - 1) as? PlanPass.TextureCopy
            require(copy?.source == destination && copy.destination == blend.snapshotResource &&
                copy.destinationVersion == operand.destinationVersionBefore &&
                blend.requiredDestinationVersion == operand.destinationVersionBefore)
        }
        return if (operand.blend.compositionFacts.writesParentDevice)
            Math.addExact(operand.destinationVersionBefore.valueI64, 1L) else operand.destinationVersionBefore.valueI64
    }

    passes.forEachIndexed { indexI32, pass -> when (pass) {
        is PlanPass.ClipMaskInitialize -> {
            val row = byId.getValue(pass.output)
            require(row.format == PlanTextureFormat.CoverageMask && row.sampleCountI32 == 1 &&
                PlanResourceUsage.RenderAttachment in row.usages() && PlanResourceUsage.Sampled in row.usages())
            require(pass.copyDomainI32() == requireNotNull(row.copyExtent()).let { RectI32(0, 0, it.width, it.height) })
            preparedMasks += pass.output
        }
        is PlanPass.ClipMaskProducer -> {
            val row = byId.getValue(pass.target)
            require(row.format == PlanTextureFormat.CoverageMask && row.sampleCountI32 == pass.sampleCountI32 &&
                PlanResourceUsage.RenderAttachment in row.usages())
            pass.depthStencil?.let { id -> require(byId.getValue(id).format is PlanTextureFormat.DepthStencil &&
                byId.getValue(id).copyExtent() == row.copyExtent() && byId.getValue(id).sampleCountI32 == row.sampleCountI32) }
            pass.resolveTarget?.let { id -> require(byId.getValue(id).format == PlanTextureFormat.CoverageMask &&
                byId.getValue(id).copyExtent() == row.copyExtent() && byId.getValue(id).sampleCountI32 == 1) }
            preparedMasks += pass.resolveTarget ?: pass.target
        }
        is PlanPass.ClipMaskFold -> {
            require(pass.previous in preparedMasks && pass.source in preparedMasks &&
                pass.output != pass.previous && pass.output != pass.source)
            val rows = listOf(pass.previous, pass.source, pass.output).map(byId::getValue)
            require(rows.all { it.format == PlanTextureFormat.CoverageMask && it.sampleCountI32 == 1 &&
                PlanResourceUsage.Sampled in it.usages() && PlanResourceUsage.RenderAttachment in it.usages() } &&
                rows.map { it.copyExtent() }.distinct().size == 1)
            require(pass.copyDomainI32() == requireNotNull(rows.first().copyExtent()).let { RectI32(0, 0, it.width, it.height) })
            preparedMasks += pass.output
        }
        is PlanPass.RenderPass -> {
            val target = byId.getValue(pass.target)
            require(target.role in setOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.LayerTarget,
                PlanResourceRole.FilterSource,
                PlanResourceRole.PictureAggregateSource,
                PlanResourceRole.FilterTransparentBlack,
            ))
            require(target.id !in restored && target.id !in sealedPictureSources && target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
            val alreadyInitialized = target.id in initialized
            require(pass.load == if (alreadyInitialized) AttachmentLoadPlan.Load else AttachmentLoadPlan.ClearTransparent)
            initialized += target.id
            require(pass.store == AttachmentStorePlan.Store)
            if (pass.w6bMaskSourceBinding != null) {
                require(pass.draws().all { it.blend == BlendPlan.LegacySrcOverV1 })
            }
            pass.coverageSource?.let { coverage ->
                val row = byId.getValue(coverage)
                require(row.role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.CoverageOriginal,
                    PlanResourceRole.FilterTarget) && coverage in initialized && PlanResourceUsage.Sampled in row.usages())
            }
            val targetExtent = requireNotNull(target.copyExtent())
            pass.draws().forEach { draw ->
                require((draw is SolidRectDraw || draw is AnalyticRectDraw || draw is AnalyticRRectDraw ||
                    draw is PathFillDraw || draw is PathStrokeDraw || draw is GeneralPathDraw || draw is W5bPointDraw ||
                    draw is W5bVerticesDraw || draw is W5bW4ePathDraw) &&
                    draw.sample == SamplePlan.SingleSample && draw.blend != BlendPlan.NoOpV1)
                (draw.blend as? BlendPlan.DestinationReadV1)?.let { blend ->
                    val copy = passes.getOrNull(indexI32 - 1) as? PlanPass.TextureCopy
                    require(copy != null && copy.source == pass.target && copy.destination == blend.snapshotResource &&
                        copy.destinationVersion == blend.requiredDestinationVersion && blend.requiredDestinationVersion.valueI64 == versions[target.id])
                }
                val bounds = w6aScissorI32(draw)
                require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= targetExtent.width && bounds.bottom <= targetExtent.height)
                if (draw !is SolidRectDraw) {
                    val data = requireNotNull(pass.drawDataResources)
                    listOf(data.vertex to PlanResourceRole.VertexData, data.index to PlanResourceRole.IndexData,
                        data.uniform to PlanResourceRole.UniformData).forEach { (id, role) ->
                        require(byId.getValue(id).role == role && byId.getValue(id).kind == PlanResourceKind.Buffer)
                    }
                }
                commands += draw.commandIndex
            }
            val after = Math.addExact(versions[target.id] ?: 0L, pass.draws().size.toLong())
            versions[target.id] = after
            require(pass.destinationVersionAfter?.valueI64 == after)
        }
        is PlanPass.StencilGeometryProducerV3 -> {
            val target = byId.getValue(pass.target)
            val depth = byId.getValue(pass.depthStencil)
            require(target.id in initialized && target.id !in restored && pass.load == AttachmentLoadPlan.Load)
            require(depth.role == PlanResourceRole.DepthStencil && depth.copyExtent() == target.copyExtent() &&
                depth.sampleCountI32 == 1 && depth.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8))
            val cover = passes.getOrNull(indexI32 + 1) as? PlanPass.StencilCover
            require(cover != null && cover.target == pass.target && cover.depthStencil == pass.depthStencil &&
                cover.atomicGroup == pass.atomicGroup && cover.drawDataResources == pass.drawDataResources &&
                cover.draw.commandIndex == pass.commandIndexI32 && cover.draw.copyPathGeometry() == pass.copyGeometry() &&
                cover.draw.copyScissorI32() == pass.copyScissorI32())
        }
        is PlanPass.StencilCover -> {
            require(passes.getOrNull(indexI32 - 1) is PlanPass.StencilGeometryProducerV3 &&
                pass.load == AttachmentLoadPlan.Load && pass.store == AttachmentStorePlan.Store &&
                pass.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset &&
                pass.draw.strategy == PathFillStrategy.StencilCover && pass.draw.sample == SamplePlan.SingleSample &&
                pass.draw.blend != BlendPlan.NoOpV1)
            if (pass.coverageSource != null) require(pass.draw.blend == BlendPlan.LegacySrcOverV1)
            (pass.draw.blend as? BlendPlan.DestinationReadV1)?.let { blend ->
                val copy = passes.getOrNull(indexI32 - 2) as? PlanPass.TextureCopy
                require(copy != null && copy.source == pass.target && copy.destination == blend.snapshotResource &&
                    copy.destinationVersion == blend.requiredDestinationVersion && blend.requiredDestinationVersion.valueI64 == versions[pass.target])
            }
            val targetExtent = requireNotNull(byId.getValue(pass.target).copyExtent())
            pass.coverageSource?.let { coverage ->
                val row = byId.getValue(coverage)
                require(row.role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.CoverageOriginal,
                    PlanResourceRole.FilterTarget) && coverage in initialized && PlanResourceUsage.Sampled in row.usages())
            }
            val scissor = pass.draw.copyScissorI32()
            require(scissor.left >= 0 && scissor.top >= 0 && scissor.right <= targetExtent.width && scissor.bottom <= targetExtent.height)
            commands += pass.draw.commandIndex
            val after = Math.addExact(versions.getValue(pass.target), 1L)
            versions[pass.target] = after
            require(pass.destinationVersionAfter?.valueI64 == after)
        }
        is PlanPass.LayerComposite -> {
            val source = byId.getValue(pass.source)
            val target = byId.getValue(pass.destination)
            require(source.role == PlanResourceRole.LayerTarget && target.role in setOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.LayerTarget,
                PlanResourceRole.PictureAggregateSource,
            ))
            require(source.id != target.id && source.id in initialized && target.id in initialized &&
                target.id !in sealedPictureSources)
            require(restored.add(source.id) && PlanResourceUsage.Sampled in source.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            val destinationExtent = requireNotNull(target.copyExtent())
            val bounds = pass.copySourceBoundsLayerI32()
            val origin = pass.copyDestinationOriginParentI32()
            require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= sourceExtent.width && bounds.bottom <= sourceExtent.height)
            require(origin.x >= 0 && origin.y >= 0 && origin.x.toLong() + bounds.width() <= destinationExtent.width &&
                origin.y.toLong() + bounds.height() <= destinationExtent.height)
            require(pass.load == AttachmentLoadPlan.Load && pass.store == AttachmentStorePlan.Store)
            require(pass.restore.alphaF32.isFinite())
            require(!pass.restore.blend.compositionFacts.readsPriorDevice || pass.restore.readsPriorDevice)
            require(pass.restore.writesParentDevice == pass.restore.blend.compositionFacts.writesParentDevice)
            require(pass.restore.restoreAffectsTransparentBlack ==
                pass.restore.blend.finalRestoreAffectsTransparentBlackV1(pass.restore.colorFilter))
            require((pass.restore.colorFilter == null) == (pass.restore.colorFilterUniformOffsetI64 == null))
            require(pass.restore.parentVersionBefore.valueI64 == versions[target.id])
            if (pass.restore.writesParentDevice) versions[target.id] = Math.addExact(requireNotNull(versions[target.id]), 1L)
            require(pass.destinationVersionAfter == pass.restore.parentVersionAfter && pass.destinationVersionAfter.valueI64 == versions[target.id])
        }
        is PlanPass.FilterSourceClear -> {
            val output = byId.getValue(pass.output)
            val boundSource = byId.getValue(pass.boundSourceId)
            require(output.role == PlanResourceRole.FilterTransparentBlack && output.kind == PlanResourceKind.Texture2D &&
                output.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in output.usages() &&
                PlanResourceUsage.Sampled in output.usages())
            require(boundSource.role == PlanResourceRole.FilterSource && boundSource.id in initialized)
            require(initialized.add(output.id))
            versions[output.id] = 0L
        }
        is PlanPass.FilterCoverageSourcePass -> {
            val output = byId.getValue(pass.output)
            require(output.role == PlanResourceRole.CoverageSource && output.kind == PlanResourceKind.Texture2D &&
                output.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in output.usages() &&
                PlanResourceUsage.Sampled in output.usages())
            require(!pass.deferSourceDrawClip || pass.occurrence.sourceDraw?.geometry is GeometryNode.Picture)
            pass.sealedAlphaSource?.let { alpha ->
                val source = byId.getValue(alpha.sealedSourceId)
                require(if (alpha.aggregateId != null) {
                    alpha.sealedSourceId in sealedPictureSources && source.role == PlanResourceRole.PictureAggregateSource
                } else {
                    source.role == PlanResourceRole.LayerTarget && alpha.sealedSourceId in initialized &&
                        alpha.sealedSourceId !in restored
                })
                require(versions[alpha.sealedSourceId] == alpha.sealedSourceGenerationI64 &&
                    PlanResourceUsage.Sampled in source.usages())
                require(alpha.copySampleBoundsTargetI32() == requireNotNull(source.copyExtent()).let {
                    RectI32(0, 0, it.width, it.height)
                })
            }
            pass.rasterBinding?.let { binding ->
                if (binding.draw !is SolidRectDraw) {
                    val data = requireNotNull(binding.drawDataResources)
                    require(listOf(data.vertex, data.index, data.uniform).all { it in byId })
                }
                binding.depthStencil?.let { depthId ->
                    val depth = byId.getValue(depthId)
                    require(depth.role == PlanResourceRole.DepthStencil && depth.copyExtent() == output.copyExtent())
                }
            }
            require(initialized.add(output.id))
            versions[output.id] = 0L
        }
        is PlanPass.FilterCoverageRetainPass -> {
            val source = byId.getValue(pass.source)
            val output = byId.getValue(pass.output)
            require(source.role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.FilterTarget) &&
                source.id in initialized && output.role == PlanResourceRole.CoverageOriginal &&
                source.copyExtent() == output.copyExtent() && initialized.add(output.id))
            versions[output.id] = 0L
        }
        is PlanPass.PictureAggregateBeginPass -> {
            val target = byId.getValue(pass.target)
            val parent = byId.getValue(pass.parentTarget)
            require(target.role == PlanResourceRole.PictureAggregateSource && target.kind == PlanResourceKind.Texture2D &&
                target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() &&
                PlanResourceUsage.Sampled in target.usages() && pass.target !in initialized && pass.target !in sealedPictureSources)
            // A re-entrant Picture leaf can consume the output of an earlier W6b filter in the
            // same frozen schedule.  That existing FilterTarget is initialized by its preceding
            // FilterPass and is a valid aggregate parent alongside ordinary W6a source targets.
            require(parent.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget,
                PlanResourceRole.PictureAggregateSource, PlanResourceRole.FilterSource, PlanResourceRole.FilterTarget) &&
                pass.parentTarget in initialized) {
                "Picture aggregate parent must be an initialized W6a or W6b source target."
            }
            initialized += pass.target
            versions[pass.target] = 0L
        }
        is PlanPass.PictureAggregateSealPass -> {
            val source = byId.getValue(pass.sealedSource)
            require(pass.aggregateTarget == pass.sealedSource && source.role == PlanResourceRole.PictureAggregateSource &&
                pass.aggregateTarget in initialized && sealedPictureSources.add(pass.aggregateTarget) &&
                versions.getValue(pass.aggregateTarget) == pass.sourceGenerationI64)
        }
        is PlanPass.PictureSourcePass -> {
            val output = byId.getValue(pass.output)
            pass.coverageSource?.let { coverage ->
                val row = byId.getValue(coverage)
                require(row.role in setOf(PlanResourceRole.CoverageSource, PlanResourceRole.CoverageOriginal,
                    PlanResourceRole.FilterTarget) && coverage in initialized && PlanResourceUsage.Sampled in row.usages())
            }
            pass.layerInput?.let { layerInput ->
                val row = byId.getValue(layerInput)
                require(row.role == PlanResourceRole.LayerTarget && layerInput in initialized &&
                    PlanResourceUsage.Sampled in row.usages() && pass.occurrence?.layerDescriptor != null)
            }
            pass.parentTarget?.let { parentTarget ->
                val row = byId.getValue(parentTarget)
                require(parentTarget in initialized && row.role in setOf(PlanResourceRole.LogicalTarget,
                    PlanResourceRole.LayerTarget, PlanResourceRole.PictureAggregateSource))
            }
            pass.graphTextureRequest?.let { request ->
                require(request.sealedSourceId in sealedPictureSources && request.aggregateId == pass.aggregateId)
            }
            pass.graphTextureOperand?.let { operand ->
                val uniform = byId.getValue(operand.uniformResource)
                require(operand.sealedSourceId in sealedPictureSources && operand.aggregateId == pass.aggregateId &&
                    uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer &&
                    PlanResourceUsage.Uniform in uniform.usages())
                operand.colorFilter?.let { filter ->
                    val offset = requireNotNull(operand.colorFilterUniformOffsetI64)
                    val capacity = requireNotNull(operand.colorFilterUniformByteCountI64)
                    require(uniform.byteSize == capacity &&
                        Math.addExact(offset, maxOf(16L, filter.dynamicByteCountI64)) <= capacity)
                }
                require(operand.sealedSourceGenerationI64 == versions[operand.sealedSourceId])
                require((pass.coverageSource != null) ==
                    (operand.coverageOperation == GraphTextureCoverageOperationV1.REPLACE_ALPHA_FROM_MASK))
            }
            require(output.role in setOf(PlanResourceRole.FilterSource, PlanResourceRole.LogicalTarget,
                PlanResourceRole.LayerTarget, PlanResourceRole.PictureAggregateSource) &&
                output.kind == PlanResourceKind.Texture2D &&
                output.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in output.usages() &&
                (output.role != PlanResourceRole.FilterSource || PlanResourceUsage.Sampled in output.usages()))
            if (output.role == PlanResourceRole.FilterSource) {
                require(initialized.add(output.id))
                versions[output.id] = 0L
            } else {
                require(output.id in initialized && output.id !in sealedPictureSources)
                versions[output.id] = Math.addExact(requireNotNull(versions[output.id]), 1L)
            }
        }
        is PlanPass.PictureComposite -> {
            val source = byId.getValue(pass.source)
            val destination = byId.getValue(pass.destination)
            require(source.role == PlanResourceRole.FilterSource && source.id in initialized &&
                destination.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget,
                    PlanResourceRole.PictureAggregateSource) && destination.id in initialized &&
                destination.id !in sealedPictureSources && PlanResourceUsage.Sampled in source.usages())
            val after = validatePictureTerminal(requireNotNull(pass.operands), source.id, destination.id, indexI32)
            versions[destination.id] = after
            require(pass.destinationVersionAfter.valueI64 == after)
        }
        is PlanPass.FilterPass -> {
            val output = byId.getValue(pass.output)
            val inputs = pass.inputs().map(byId::getValue)
            require(output.role == PlanResourceRole.FilterTarget && output.kind == PlanResourceKind.Texture2D &&
                output.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in output.usages() &&
                PlanResourceUsage.Sampled in output.usages())
            require(inputs.all { it.kind == PlanResourceKind.Texture2D && PlanResourceUsage.Sampled in it.usages() })
            val targetExtent = requireNotNull(output.copyExtent())
            fun targetLocal(region: RectI32?): RectI32? = region?.rebaseAtOriginI32OrNull(
                pass.operation.bounds.copyTargetOriginDeviceI32(),
            )
            listOf(
                "known content" to targetLocal(pass.operation.bounds.copyKnownContentDeviceI32()),
                "desired output" to targetLocal(pass.operation.bounds.copyDesiredOutputDeviceI32()),
                "produced output" to targetLocal(pass.operation.bounds.copyProducedOutputDeviceI32()),
            ).forEach { (name, local) -> local?.let {
                require(local.left >= 0 && local.top >= 0 && local.right <= targetExtent.width && local.bottom <= targetExtent.height) {
                    "Frozen $name bounds $local escape $targetExtent at ${pass.operation.bounds.copyTargetOriginDeviceI32()}."
                }
            } }
            // Required input belongs to the preceding source generation, not this output target.
            // It can extend beyond a separable pass's produced output by exactly the next axis's
            // blur support; treating it as output bounds would collapse the frozen X→Y contract.
            require(pass.operation.bounds.copyRequiredInputDeviceI32().isEmpty.not())
            require(targetLocal(pass.operation.bounds.copyDesiredOutputDeviceI32()) ==
                RectI32(0, 0, targetExtent.width, targetExtent.height))
            (pass.operation as? FilterPassOperationV1.Picture)?.let { operation ->
                val sealed = operation.copySealedSource()
                val source = byId.getValue(sealed.resourceId)
                val seal = passes.take(indexI32).filterIsInstance<PlanPass.PictureAggregateSealPass>().singleOrNull {
                    it.aggregateId == sealed.aggregateId && it.sealedSource == sealed.resourceId
                }
                require(pass.inputs() == listOf(sealed.resourceId) &&
                    source.role == PlanResourceRole.PictureAggregateSource &&
                    sealed.resourceId in sealedPictureSources &&
                    seal?.sourceGenerationI64 == sealed.sourceGenerationI64 &&
                    versions[sealed.resourceId] == sealed.sourceGenerationI64 &&
                    sealed.copyOwner().authenticates(pass.evaluationKey)) {
                    "W6d Picture filter must read exactly one previously sealed aggregate generation."
                }
            }
            // Every typed filter output is an immutable source generation for its immediate
            // next operation, material pass, or terminal composite.  This is the same
            // publication boundary used by raw coverage and transparent-black sources.
            require(initialized.add(output.id))
            versions[output.id] = 0L
            (pass.operation as? FilterPassOperationV1.MaskShader)?.materialBinding?.let { binding ->
                if (binding is FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned) {
                    val uniform = byId.getValue(binding.uniformResource)
                    require(uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer &&
                        PlanResourceUsage.Uniform in uniform.usages() && binding.uniformOffsetBytesI64 == 0L &&
                        binding.uniformCapacityBytesI64 == uniform.byteSize &&
                        binding.materialAuthority.materialPlanRef() == binding.material)
                }
            }
            (pass.operation as? FilterPassOperationV1.MaskTable)?.let { table ->
                val resource = byId.getValue(table.tableResourceId)
                require(table.entryCountI32 == 256 && table.copyTable().sizeI32 == 256 &&
                    table.generationI64 == 0L && pass.evaluationKey.maskOccurrenceI32 == table.ownerMaskOccurrenceI32 &&
                    resource.role == PlanResourceRole.MaskTableData && resource.kind == PlanResourceKind.Buffer &&
                    resource.byteSize == 256L && resource.usages() ==
                    setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination) &&
                    resource.lifetime == PlanResourceLifetime.FrameLocal)
            }
        }
        is PlanPass.FilterComposite -> {
            val source = byId.getValue(pass.source)
            val destination = byId.getValue(pass.destination)
            require(source.role == PlanResourceRole.FilterTarget && destination.role in setOf(
                PlanResourceRole.LogicalTarget,
                PlanResourceRole.LayerTarget,
                PlanResourceRole.FilterSource,
                PlanResourceRole.PictureAggregateSource,
            ) && destination.id in initialized && destination.id !in sealedPictureSources && PlanResourceUsage.Sampled in source.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            val destinationExtent = requireNotNull(destination.copyExtent())
            val sourceBounds = pass.copySourceBoundsTargetI32()
            val destinationOrigin = pass.copyDestinationOriginParentI32()
            require(sourceBounds.left >= 0 && sourceBounds.top >= 0 && sourceBounds.right <= sourceExtent.width &&
                sourceBounds.bottom <= sourceExtent.height && destinationOrigin.x >= 0 && destinationOrigin.y >= 0 &&
                destinationOrigin.x.toLong() + sourceBounds.width() <= destinationExtent.width &&
                destinationOrigin.y.toLong() + sourceBounds.height() <= destinationExtent.height)
            val expectedOffset = Point2I32(
                Math.subtractExact(sourceBounds.left, destinationOrigin.x),
                Math.subtractExact(sourceBounds.top, destinationOrigin.y),
            )
            require(pass.copySourceSampleOffsetTargetLocalI32() == expectedOffset)
            val sourceInDestination = RectI32(destinationOrigin.x, destinationOrigin.y,
                Math.addExact(destinationOrigin.x, sourceBounds.width()),
                Math.addExact(destinationOrigin.y, sourceBounds.height()))
            val scissor = pass.copyCompositeScissorTargetLocalI32()
            require(scissor == null || scissor.left >= sourceInDestination.left && scissor.top >= sourceInDestination.top &&
                scissor.right <= sourceInDestination.right && scissor.bottom <= sourceInDestination.bottom &&
                scissor.left >= 0 && scissor.top >= 0 && scissor.right <= destinationExtent.width && scissor.bottom <= destinationExtent.height)
            val before = requireNotNull(versions[destination.id])
            when (val operation = pass.operation) {
                is FilterCompositeOperationV1.Draw -> {
                    require((operation.noOp && scissor == null) || (!operation.noOp && scissor == sourceInDestination))
                    require(operation.blend !is BlendPlan.DestinationReadV1)
                    val after = if (!operation.noOp && operation.blend.compositionFacts.writesParentDevice) Math.addExact(before, 1L) else before
                    versions[destination.id] = after
                    require(pass.destinationVersionAfter.valueI64 == after)
                    require(pass.replacedLayerSource == null)
                }
                is FilterCompositeOperationV1.Layer -> {
                    require((operation.noOp && scissor == null) || (!operation.noOp && scissor == sourceInDestination))
                    val replaced = requireNotNull(pass.replacedLayerSource)
                    require(byId.getValue(replaced).role == PlanResourceRole.LayerTarget && replaced in initialized && restored.add(replaced))
                    val restore = operation.restore
                    require(restore.parentVersionBefore.valueI64 == before &&
                        (operation.noOp || restore.parentVersionAfter == pass.destinationVersionAfter) &&
                        restore.writesParentDevice == restore.blend.compositionFacts.writesParentDevice)
                    val after = if (!operation.noOp && restore.writesParentDevice) Math.addExact(before, 1L) else before
                    versions[destination.id] = after
                    require(pass.destinationVersionAfter.valueI64 == after)
                }
                is FilterCompositeOperationV1.Picture -> {
                    require(pass.replacedLayerSource == null)
                    val terminal = requireNotNull(operation.terminal)
                    require(terminal.copySourceBoundsTargetI32() == sourceBounds &&
                        terminal.copyDestinationOriginTargetI32() == destinationOrigin &&
                        terminal.copySourceSampleOffsetTargetLocalI32() == pass.copySourceSampleOffsetTargetLocalI32() &&
                        terminal.copyCompositeScissorTargetLocalI32() == scissor)
                    val after = validatePictureTerminal(terminal, source.id, destination.id, indexI32)
                    versions[destination.id] = after
                    require(pass.destinationVersionAfter.valueI64 == after)
                }
            }
        }
        is PlanPass.TextureCopy -> {
            val source = byId.getValue(pass.source)
            val destination = byId.getValue(pass.destination)
            require(source.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget,
                PlanResourceRole.PictureAggregateSource) &&
                source.id in initialized && PlanResourceUsage.CopySource in source.usages() &&
                PlanResourceUsage.CopyDestination in destination.usages())
            val sourceExtent = requireNotNull(source.copyExtent())
            val sourceBounds = requireNotNull(pass.copySourceBoundsI32())
            require(sourceBounds.left >= 0 && sourceBounds.top >= 0 &&
                sourceBounds.right <= sourceExtent.width && sourceBounds.bottom <= sourceExtent.height)
            require(pass.destinationVersion?.valueI64 == versions[source.id])
            when (destination.role) {
                PlanResourceRole.DestinationSnapshot -> {
                    val destinationExtent = requireNotNull(destination.copyExtent())
                    require(pass.copyDestinationOriginI32() == Point2I32.Origin &&
                        destinationExtent.width >= sourceBounds.width() && destinationExtent.height >= sourceBounds.height())
                    val consumer = passes.getOrNull(indexI32 + 1)
                    // A filtered restore has to materialize its coverage/source and X/Y passes
                    // between this frozen snapshot and its FilterComposite.  Its consumer is
                    // therefore not necessarily the adjacent pass; link it by the immutable
                    // snapshot resource rather than letting a lowerer rediscover the blend.
                    val filteredLayerBlend = passes.drop(indexI32 + 1)
                        .filterIsInstance<PlanPass.FilterComposite>()
                        .mapNotNull { composite ->
                            val layer = composite.operation as? FilterCompositeOperationV1.Layer
                                ?: return@mapNotNull null
                            layer.restore.blend.takeIf {
                                composite.destination == source.id &&
                                    it.destinationReadSnapshotResourceV1() == destination.id &&
                                    it.requiredDestinationVersionV1() == pass.destinationVersion
                            }
                        }
                        .singleOrNull()
                    val blend = when (consumer) {
                        is PlanPass.LayerComposite -> {
                            require(consumer.destination == source.id && sourceBounds == RectI32(0, 0, sourceExtent.width, sourceExtent.height) &&
                                destinationExtent == sourceExtent)
                            consumer.restore.blend
                        }
                        is PlanPass.RenderPass -> {
                            require(consumer.target == source.id && consumer.draws().size == 1)
                            consumer.draws().single().blend
                        }
                        is PlanPass.StencilGeometryProducerV3 -> {
                            require(consumer.target == source.id)
                            requireNotNull(passes.getOrNull(indexI32 + 2) as? PlanPass.StencilCover).draw.blend
                        }
                        is PlanPass.PictureComposite -> {
                            require(consumer.destination == source.id)
                            requireNotNull(consumer.operands).blend
                        }
                        is PlanPass.FilterComposite -> {
                            require(consumer.destination == source.id)
                            when (val operation = consumer.operation) {
                                is FilterCompositeOperationV1.Picture -> requireNotNull(operation.terminal).blend
                                is FilterCompositeOperationV1.Layer -> operation.restore.blend
                                else -> error("Invalid destination snapshot consumer")
                            }
                        }
                        else -> filteredLayerBlend ?: error("Invalid destination snapshot consumer")
                    }
                    require(blend.compositionFacts.readsPriorDevice)
                    require(blend.destinationReadSnapshotResourceV1() == destination.id &&
                        blend.requiredDestinationVersionV1() == pass.destinationVersion)
                }
                PlanResourceRole.LayerTarget, PlanResourceRole.FilterSource -> {
                    val destinationExtent = requireNotNull(destination.copyExtent())
                    require(initialized.add(destination.id))
                    require(pass.copyDestinationOriginI32() == Point2I32.Origin &&
                        sourceBounds.width() == destinationExtent.width && sourceBounds.height() == destinationExtent.height)
                    versions[destination.id] = 0L
                }
                else -> error("w6a.layer.unsupported_child")
            }
        }
        is PlanPass.ReadbackPass -> {
            require(pass === passes.last() && pass.source == root.id)
            require(byId.getValue(pass.staging).role == PlanResourceRole.ReadbackStaging)
            require(pass.bytesPerRow % copyRowAlignmentI32 == 0L && pass.bytesPerRow >= extent.width.toLong() * 4L &&
                byId.getValue(pass.staging).byteSize == Math.multiplyExact(pass.bytesPerRow, extent.height.toLong()))
            require(pass.mappedBytesI64 == Math.addExact(Math.multiplyExact(pass.bytesPerRow, (extent.height - 1).toLong()), extent.width.toLong() * 4L))
        }
        else -> error("w6a.layer.unsupported_child")
    } }
    if (passes.any { it is PlanPass.FilterPass }) W6bFilterGraphWitnessV1.seal(resources, passes)
    require(restored == layers.map { it.id }.toSet())
    require(commands.size == visualCountI32 && commands.distinct().size == commands.size)
    // Picture streams prove source order and occurrence ownership independently. Their W4/W5
    // lanes use frame-unique command IDs beyond the root scene's captured command indices.
    if (passes.none { it is PlanPass.PictureAggregateBeginPass || it is PlanPass.PictureSourcePass ||
            it is PlanPass.RenderPass && it.plannedCommandId != null ||
            it is PlanPass.StencilCover && it.plannedCommandId != null ||
            it is PlanPass.FilterComposite && it.operation is FilterCompositeOperationV1.Picture }) {
        require(commands.zipWithNext().all { (a, b) -> a < b })
    }
    require(passes.filterIsInstance<PlanPass.ReadbackPass>().size == 1)
    require(dependencies == passes.zipWithNext { first, second -> PlanPassDependency(first.id, second.id) })
}

internal fun w6aRasterBoundsI32(draw: PlanDraw): RectI32 = when (draw) {
    is SolidRectDraw -> draw.copyVisibleBounds()
    is AnalyticRectDraw -> draw.copyRasterBounds()
    is AnalyticRRectDraw -> draw.copyRasterBounds()
    is PathFillDraw -> draw.copyGeometryF32().copyConservativeScissorI32()
    is PathStrokeDraw -> draw.copyGeometryF32().copyConservativeScissorI32()
    is GeneralPathDraw -> when (val geometry = draw.copyPathGeometry()) {
        is PathDrawGeometry.Fill -> geometry.valueF32.copyConservativeScissorI32()
        is PathDrawGeometry.Stroke -> geometry.valueF32.copyConservativeScissorI32()
        is PathDrawGeometry.InverseDomainSource, PathDrawGeometry.Empty -> draw.copyScissorI32()
    }
    is W5bPointDraw -> draw.copyBoundsI32()
    is W5bVerticesDraw -> draw.copyBoundsI32()
    is W5bW4ePathDraw -> draw.copyScissorI32()
    else -> error("w6a.layer.unsupported_child")
}

internal fun w6aScissorI32(draw: PlanDraw): RectI32 = when (draw) {
    is SolidRectDraw -> draw.copyScissor()
    is AnalyticRectDraw -> draw.copyScissor()
    is AnalyticRRectDraw -> draw.copyScissor()
    is PathDraw -> draw.copyScissorI32()
    is W5bPointDraw -> draw.copyScissorI32()
    is W5bVerticesDraw -> draw.copyScissorI32()
    else -> error("w6a.layer.unsupported_child")
}
