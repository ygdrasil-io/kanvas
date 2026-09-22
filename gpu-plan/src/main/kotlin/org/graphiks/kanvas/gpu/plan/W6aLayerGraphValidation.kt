package org.graphiks.kanvas.gpu.plan

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
            require(target.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget))
            require(target.id !in restored && target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
            val alreadyInitialized = target.id in initialized
            require(pass.load == if (alreadyInitialized) AttachmentLoadPlan.Load else AttachmentLoadPlan.ClearTransparent)
            initialized += target.id
            require(pass.store == AttachmentStorePlan.Store)
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
            (pass.draw.blend as? BlendPlan.DestinationReadV1)?.let { blend ->
                val copy = passes.getOrNull(indexI32 - 2) as? PlanPass.TextureCopy
                require(copy != null && copy.source == pass.target && copy.destination == blend.snapshotResource &&
                    copy.destinationVersion == blend.requiredDestinationVersion && blend.requiredDestinationVersion.valueI64 == versions[pass.target])
            }
            val targetExtent = requireNotNull(byId.getValue(pass.target).copyExtent())
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
            require(source.role == PlanResourceRole.LayerTarget && target.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget))
            require(source.id != target.id && source.id in initialized && target.id in initialized)
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
        is PlanPass.FilterPass -> {
            val output = byId.getValue(pass.output)
            val inputs = pass.inputs().map(byId::getValue)
            require(output.role == PlanResourceRole.FilterTarget && output.kind == PlanResourceKind.Texture2D &&
                output.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in output.usages() &&
                PlanResourceUsage.Sampled in output.usages())
            require(inputs.all { it.kind == PlanResourceKind.Texture2D && PlanResourceUsage.Sampled in it.usages() })
            require(pass.evaluationKey.copyDesiredOutputDeviceI32() == pass.operation.bounds.copyDesiredOutputDeviceI32())
            val targetExtent = requireNotNull(output.copyExtent())
            fun targetLocal(region: RectI32?): RectI32? = region?.rebaseAtOriginI32OrNull(
                pass.operation.bounds.copyTargetOriginDeviceI32(),
            )
            listOf(
                targetLocal(pass.operation.bounds.copyKnownContentDeviceI32()),
                targetLocal(pass.operation.bounds.copyDesiredOutputDeviceI32()),
                targetLocal(pass.operation.bounds.copyRequiredInputDeviceI32()),
                targetLocal(pass.operation.bounds.copyProducedOutputDeviceI32()),
            ).filterNotNull().forEach { local ->
                require(local.left >= 0 && local.top >= 0 && local.right <= targetExtent.width && local.bottom <= targetExtent.height)
            }
            require(targetLocal(pass.operation.bounds.copyDesiredOutputDeviceI32()) ==
                RectI32(0, 0, targetExtent.width, targetExtent.height))
        }
        is PlanPass.TextureCopy -> {
            val source = byId.getValue(pass.source)
            val destination = byId.getValue(pass.destination)
            require(source.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget) &&
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
                        else -> error("Invalid destination snapshot consumer")
                    }
                    require(blend.compositionFacts.readsPriorDevice)
                    require(blend.destinationReadSnapshotResourceV1() == destination.id &&
                        blend.requiredDestinationVersionV1() == pass.destinationVersion)
                }
                PlanResourceRole.LayerTarget -> {
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
    require(restored == layers.map { it.id }.toSet())
    require(commands.size == visualCountI32 && commands.zipWithNext().all { (a, b) -> a < b })
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
