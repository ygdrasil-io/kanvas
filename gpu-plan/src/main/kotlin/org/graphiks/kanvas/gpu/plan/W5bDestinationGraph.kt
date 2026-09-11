package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

/** Preflight and graph issuance for the integral W3 destination-read successor. */
internal object W5bDestinationGraphSealer {
    fun seal(
        id: PlanId,
        capabilityId: String,
        extent: SizeI32,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        draws: List<PlanDraw>,
        material: MaterialPlanTable?,
        targetBytesI64: Long,
        stagingBytesI64: Long,
        rowBytesI64: Long,
        geometryResources: List<PlanResource> = emptyList(),
        drawDataResources: PlanDrawDataResources? = null,
        drawDataByCommandI32: Map<Int, PlanDrawDataResources> = emptyMap(),
        depthStencilByCommandI32: Map<Int, PlanResourceId> = emptyMap(),
        w4eSource: RenderGraph? = null,
    ): RenderGraph {
        require(w4eSource == null || w4eSource.verifyW4eCompilerWitness() &&
            w4eSource.capabilityId == W4eClipPlanCompiler.W5A_HARD_CAPABILITY_ID && capabilityId == W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID)
        val nativePrefix = w4eSource?.passes()?.filter { it is PlanPass.ClipMaskInitialize || it is PlanPass.ClipMaskProducer || it is PlanPass.ClipMaskFold }.orEmpty()
        val clips = draws.filterIsInstance<W5bPointDraw>().mapNotNull { it.clipOnly }.distinct()
        require(clips.size <= 1)
        val clip = clips.singleOrNull()
        require(clip == null || capabilityId == W5bCorePrimitiveGraph.CAPABILITY_ID &&
            clip.capabilities == capabilities && clip.budget == budget && clip.copyExtentI32() == extent)
        require(draws.none { it.blend == BlendPlan.NoOpV1 }) { "NoOp draws must be elided before graph issuance" }
        val destinationCountI32 = draws.count { it.blend is BlendPlan.DestinationReadV1 }
        val readsDestination = destinationCountI32 > 0
        require(capabilities.maxBindGroupsI32?.let { it >= if (clip != null) 4 else if (readsDestination) 3 else 2 } == true &&
            capabilities.maxBindingsPerBindGroupI32?.let { it >= if (readsDestination) 2 else 1 } == true &&
            (!readsDestination || capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= if (clip == null && w4eSource == null) 1 else 2 } == true) &&
            (!readsDestination || capabilities.maxSamplersPerShaderStageI32?.let { it >= 1 } == true) &&
            capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
            capabilities.maxUniformBufferBindingSizeBytesI64?.let { it >= 32L } == true) {
            "unsupported.w5b.destination-capability"
        }
        val format = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        require(!readsDestination || capabilities.supportsTexture(format, 1, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled))) {
            "unsupported.w5b.destination-texture"
        }
        require(destinationCountI32 > 0 || capabilityId in setOf(W5bCorePrimitiveGraph.CAPABILITY_ID,
            W4aAnalyticRectPlanCompiler.W5B_CAPABILITY_ID, W4bAnalyticRRectPlanCompiler.W5B_CAPABILITY_ID,
            W4cPathFillPlanCompiler.W5B_CAPABILITY_ID, W4dPathStrokePlanCompiler.W5B_CAPABILITY_ID, W4dGeneralPathPlanCompiler.W5B_HARD_CAPABILITY_ID, W4eClipPlanCompiler.W5B_HARD_CAPABILITY_ID, W5bGeometryLanePlanV3.COMPOSITE_CAPABILITY_ID))
        val initialClearI32 = if (draws.isEmpty() || draws.first().blend is BlendPlan.DestinationReadV1) 1 else 0
        val stencilCountI32 = draws.count { it is PathDraw && it.strategy == PathFillStrategy.StencilCover }
        val passCountI32 = Math.addExact(Math.addExact(draws.size, destinationCountI32), initialClearI32 + stencilCountI32 + 1 + (clip?.passes()?.size ?: 0) + nativePrefix.size)
        // One snapshot is reused only after its preceding consumer; native storage stays live
        // through frame completion, so all three physical resources overlap in the budget.
        val peakI64 = Math.addExact(geometryResources.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) },
            Math.addExact(Math.addExact(Math.multiplyExact(targetBytesI64,
            if (destinationCountI32 == 0) 1L else 2L), stagingBytesI64),
            clip?.resources()?.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) } ?: 0L))
        val sourceRequirements = draws.map { draw -> RawMaterialRequirementsV2.of(requireNotNull(material),
            (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref) }
        require(sourceRequirements.all { it.uniformByteCountI64 <= requireNotNull(capabilities.maxUniformBufferBindingSizeBytesI64) &&
            it.uniformByteCountI64 <= capabilities.maxBufferSizeBytes }) { "resource-limit.w5b.source-binding" }
        val sourceBytesI64 = sourceRequirements.fold(0L) { totalI64, source -> Math.addExact(totalI64, source.uniformByteCountI64) }
        require(Math.addExact(peakI64, sourceBytesI64) <= budget.maxFrameLocalBytes) { "resource-limit.w5b.destination-budget" }
        val target = PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            format, extent, targetBytesI64, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            PlanResourceLifetime.FrameLocal, 0, passCountI32)
        val snapshot = if (destinationCountI32 == 0) null else PlanResource.of(PlanResourceRole.DestinationSnapshot, 0, PlanResourceKind.Texture2D,
            format, extent, targetBytesI64, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, passCountI32)
        val staging = PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer,
            null, null, stagingBytesI64, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal, if (draws.isEmpty()) 1 else 0, passCountI32)
        var versionI64 = 0L
        var renderOrdinalI32 = 0
        var copyOrdinalI32 = 0
        var producerOrdinalI32 = 0
        var coverOrdinalI32 = 0
        var hasColorAttachment = false
        val passes = (nativePrefix + clip?.passes().orEmpty()).toMutableList()
        fun render(draw: PlanDraw?) {
            if (draw != null) versionI64 = Math.addExact(versionI64, 1L)
            val data = draw?.let { drawDataByCommandI32[it.commandIndex] } ?: drawDataResources
            val load = if (hasColorAttachment) AttachmentLoadPlan.Load else AttachmentLoadPlan.ClearTransparent
            hasColorAttachment = true
            if (draw is PathDraw && draw.strategy == PathFillStrategy.StencilCover) {
                val depth = requireNotNull(depthStencilByCommandI32[draw.commandIndex])
                val atomic = canonicalPathAtomicGroup(draw)
                passes += PlanPass.StencilGeometryProducerV3(producerOrdinalI32++, target.id, depth,
                    draw.commandIndex, draw.copyPathGeometry(), draw.copyScissorI32(), requireNotNull(data), atomic,
                    load, AttachmentStorePlan.Store)
                passes += PlanPass.StencilCover(coverOrdinalI32++, target.id, depth, draw, data, atomic,
                    AttachmentLoadPlan.Load, AttachmentStorePlan.Store, PlanDepthStencilAccess.ReadWrite,
                    PlanDepthStencilLoadStore.LoadStoreTestReset, DestinationVersionI64(versionI64))
                return
            }
            passes += PlanPass.RenderPass(renderOrdinalI32++, target.id, listOfNotNull(draw),
                load, AttachmentStorePlan.Store, data, destinationVersionAfter = DestinationVersionI64(versionI64))
        }
        if (initialClearI32 == 1) render(null)
        draws.forEach { draw ->
            val blend = draw.blend
            if (blend is BlendPlan.DestinationReadV1) {
                require(if (draw is W5bW4ePathDraw && blend.coverage == BlendCoverageEncodingV1.ScalarCoverageInShader)
                    blend.compositionAbiI32 == 3
                    else if ((draw as? W5bPointDraw)?.clipOnly != null)
                    blend.compositionAbiI32 == 4 && blend.coverage == BlendCoverageEncodingV1.ScalarCoverageInShader
                    else blend.compositionAbiI32 == 3 && (blend.coverage == BlendCoverageEncodingV1.FullOrScissor ||
                        draw is AnalyticRectDraw || draw is AnalyticRRectDraw))
                val version = DestinationVersionI64(versionI64)
                passes += PlanPass.TextureCopy(copyOrdinalI32++, target.id, requireNotNull(snapshot).id, version)
                val sealed = blend.copy(requiredDestinationVersion = version, snapshotResource = snapshot.id)
                render(when (draw) {
                    is SolidRectDraw -> SolidRectDraw.ofMaterial(draw.commandIndex,
                        (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref,
                        draw.copyVisibleBounds(), draw.copyScissor(), draw.coverage, draw.sample, sealed)
                    is W5bPointDraw -> draw.withBlend(sealed)
                    is AnalyticRectDraw -> AnalyticRectDraw.ofMaterial(draw.commandIndex,
                        (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref,
                        draw.copyDeviceBounds(), draw.copyRasterBounds(), draw.copyScissor(), sealed)
                    is AnalyticRRectDraw -> AnalyticRRectDraw.ofMaterial(draw.commandIndex,
                        (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref, draw.origin,
                        draw.copyDeviceShape(), draw.copyRasterBounds(), draw.copyScissor(), sealed)
                    is PathFillDraw -> PathFillDraw.ofMaterial(draw.commandIndex,
                        (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref,
                        draw.copyGeometryF32(), draw.strategy, draw.copyScissorI32(), sealed)
                    is W5bW4ePathDraw -> draw.withBlend(sealed)
                    is GeneralPathDraw -> draw.withBlend(sealed)
                    is PathStrokeDraw -> PathStrokeDraw.ofMaterial(draw.commandIndex,
                        (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref, draw.copyGeometryF32(),
                        draw.copyScissorI32(), draw.mode, draw.styleF64, sealed)
                    else -> error("unsupported.w5b.destination-geometry")
                })
            } else render(draw)
        }
        passes += PlanPass.ReadbackPass(0, target.id, staging.id, rowBytesI64)
        val clipResources = clip?.resources().orEmpty().map { resource ->
            val last = passes.indexOfLast { pass -> when (pass) {
                is PlanPass.ClipMaskInitialize -> pass.output == resource.id
                is PlanPass.ClipMaskProducer -> resource.id in listOfNotNull(pass.target, pass.resolveTarget, pass.depthStencil)
                is PlanPass.ClipMaskFold -> resource.id in listOf(pass.previous, pass.source, pass.output)
                is PlanPass.RenderPass -> pass.draws().filterIsInstance<W5bPointDraw>().any { it.clipOnly?.maskResource == resource.id }
                else -> false
            } }.let { if (it < 0) requireNotNull(clip).passes().lastIndex else it }
            PlanResource.of(resource.role, resource.ordinal, resource.kind, resource.format, resource.copyExtent(), resource.byteSize,
                resource.usages(), resource.lifetime, 0, last + 1, resource.sampleCountI32)
        }
        return RenderGraph.of(id, capabilityId, extent, format.value, capabilities, budget, draws.size,
            listOfNotNull(target, snapshot, staging) + clipResources + geometryResources.map { resource ->
                val firstUseI32 = if (resource.role == PlanResourceRole.DepthStencil && w4eSource == null)
                    passes.indexOfFirst { it is PlanPass.StencilGeometryProducerV3 && it.depthStencil == resource.id }
                    else 0
                PlanResource.of(resource.role, resource.ordinal, resource.kind, resource.format, resource.copyExtent(),
                    resource.byteSize, resource.usages(), resource.lifetime, firstUseI32, passCountI32, resource.sampleCountI32)
            }, passes,
            passes.zipWithNext { before, after -> PlanPassDependency(before.id, after.id) }, peakI64,
            materialPlanTable = material.takeIf { draws.isNotEmpty() }, w5bW4eSource = w4eSource)
    }
}

/** Validates every read against the last write and exact immediately preceding copy. */
internal fun validateW5bDestinationVersions(passes: List<PlanPass>) {
    if (passes.none { it is PlanPass.StencilGeometryProducerV3 || it is PlanPass.StencilCover &&
            it.destinationVersionAfter != null || it is PlanPass.RenderPass && it.draws().any { draw ->
        draw.blend is BlendPlan.DestinationReadV1 || draw is W5bPointDraw
    } }) return
    var versionI64 = 0L
    var hasRendered = false
    passes.forEachIndexed { indexI32, pass ->
        when (pass) {
            is PlanPass.RenderPass -> {
                require(pass.draws().none { it.blend == BlendPlan.NoOpV1 }) { "invalid.w5b.noop-write" }
                val initialClear = !hasRendered && pass.draws().isEmpty() && pass.load == AttachmentLoadPlan.ClearTransparent
                require(pass.destinationVersionAfter?.valueI64 == if (initialClear) 0L else Math.addExact(versionI64, 1L)) { "invalid.w5b.destination-version" }
                pass.draws().forEach { draw ->
                    val blend = draw.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
                    val copy = passes.getOrNull(indexI32 - 1) as? PlanPass.TextureCopy
                    require(pass.draws().size == 1 && copy != null && copy.source == pass.target &&
                        copy.destination == blend.snapshotResource && copy.destinationVersion == blend.requiredDestinationVersion &&
                        blend.requiredDestinationVersion.valueI64 == versionI64 &&
                        (blend.compositionAbiI32 == 3 || blend.compositionAbiI32 == 4 && (draw as? W5bPointDraw)?.clipOnly != null)) {
                        "invalid.w5b.stale-destination-snapshot"
                    }
                }
                versionI64 = requireNotNull(pass.destinationVersionAfter).valueI64
                hasRendered = true
            }
            is PlanPass.TextureCopy -> require(pass.destinationVersion?.valueI64 == versionI64 && indexI32 > 0) {
                "invalid.w5b.destination-copy-version"
            }
            is PlanPass.StencilGeometryProducerV3 -> Unit
            is PlanPass.StencilCover -> {
                require(passes.getOrNull(indexI32 - 1) is PlanPass.StencilGeometryProducerV3)
                require(pass.destinationVersionAfter?.valueI64 == Math.addExact(versionI64, 1L))
                (pass.draw.blend as? BlendPlan.DestinationReadV1)?.let { blend ->
                    val copy = passes.getOrNull(indexI32 - 2) as? PlanPass.TextureCopy
                    require(copy != null && copy.source == pass.target && copy.destination == blend.snapshotResource &&
                        copy.destinationVersion == blend.requiredDestinationVersion &&
                        blend.requiredDestinationVersion.valueI64 == versionI64 && blend.compositionAbiI32 == 3)
                }
                versionI64 = requireNotNull(pass.destinationVersionAfter).valueI64
                hasRendered = true
            }
            is PlanPass.ReadbackPass -> Unit
            is PlanPass.ClipMaskInitialize, is PlanPass.ClipMaskProducer, is PlanPass.ClipMaskFold ->
                require(!hasRendered) { "invalid.w5b.clip-prefix-order" }
            else -> error("invalid.w5b.destination-pass")
        }
    }
}
