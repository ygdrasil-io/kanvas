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
        draws: List<SolidRectDraw>,
        material: MaterialPlanTable,
        targetBytesI64: Long,
        stagingBytesI64: Long,
        rowBytesI64: Long,
    ): RenderGraph {
        require(capabilities.maxBindGroupsI32?.let { it >= 3 } == true &&
            capabilities.maxBindingsPerBindGroupI32?.let { it >= 2 } == true &&
            capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= 1 } == true &&
            capabilities.maxSamplersPerShaderStageI32?.let { it >= 1 } == true &&
            capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
            capabilities.maxUniformBufferBindingSizeBytesI64?.let { it >= 32L } == true) {
            "unsupported.w5b.destination-capability"
        }
        val format = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        require(capabilities.supportsTexture(format, 1, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled))) {
            "unsupported.w5b.destination-texture"
        }
        val destinationCountI32 = draws.count { it.blend is BlendPlan.DestinationReadV1 }
        require(destinationCountI32 > 0)
        val initialClearI32 = if (draws.first().blend is BlendPlan.DestinationReadV1) 1 else 0
        val passCountI32 = Math.addExact(Math.addExact(draws.size, destinationCountI32), initialClearI32 + 1)
        // One snapshot is reused only after its preceding consumer; native storage stays live
        // through frame completion, so all three physical resources overlap in the budget.
        val peakI64 = Math.addExact(Math.multiplyExact(targetBytesI64, 2L), stagingBytesI64)
        val sourceRequirements = draws.map { draw -> RawMaterialRequirementsV2.of(material,
            (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref) }
        require(sourceRequirements.all { it.uniformByteCountI64 <= requireNotNull(capabilities.maxUniformBufferBindingSizeBytesI64) &&
            it.uniformByteCountI64 <= capabilities.maxBufferSizeBytes }) { "resource-limit.w5b.source-binding" }
        val sourceBytesI64 = sourceRequirements.fold(0L) { totalI64, source -> Math.addExact(totalI64, source.uniformByteCountI64) }
        require(Math.addExact(peakI64, sourceBytesI64) <= budget.maxFrameLocalBytes) { "resource-limit.w5b.destination-budget" }
        val target = PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            format, extent, targetBytesI64, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            PlanResourceLifetime.FrameLocal, 0, passCountI32)
        val snapshot = PlanResource.of(PlanResourceRole.DestinationSnapshot, 0, PlanResourceKind.Texture2D,
            format, extent, targetBytesI64, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, passCountI32)
        val staging = PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer,
            null, null, stagingBytesI64, setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal, 0, passCountI32)
        var versionI64 = 0L
        var renderOrdinalI32 = 0
        var copyOrdinalI32 = 0
        val passes = mutableListOf<PlanPass>()
        fun render(draw: SolidRectDraw?) {
            if (draw != null) versionI64 = Math.addExact(versionI64, 1L)
            passes += PlanPass.RenderPass(renderOrdinalI32++, target.id, listOfNotNull(draw),
                if (passes.isEmpty()) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load,
                AttachmentStorePlan.Store, destinationVersionAfter = DestinationVersionI64(versionI64))
        }
        if (initialClearI32 == 1) render(null)
        draws.forEach { draw ->
            val blend = draw.blend
            if (blend is BlendPlan.DestinationReadV1) {
                require(blend.compositionAbiI32 == 3 && blend.coverage == BlendCoverageEncodingV1.FullOrScissor)
                val version = DestinationVersionI64(versionI64)
                passes += PlanPass.TextureCopy(copyOrdinalI32++, target.id, snapshot.id, version)
                render(SolidRectDraw.ofMaterial(draw.commandIndex,
                    (draw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).ref,
                    draw.copyVisibleBounds(), draw.copyScissor(), draw.coverage, draw.sample,
                    blend.copy(requiredDestinationVersion = version, snapshotResource = snapshot.id)))
            } else render(draw)
        }
        passes += PlanPass.ReadbackPass(0, target.id, staging.id, rowBytesI64)
        return RenderGraph.of(id, capabilityId, extent, format.value, capabilities, budget, draws.size,
            listOf(target, snapshot, staging), passes,
            passes.zipWithNext { before, after -> PlanPassDependency(before.id, after.id) }, peakI64,
            materialPlanTable = material)
    }
}

/** Validates every read against the last write and exact immediately preceding copy. */
internal fun validateW5bDestinationVersions(passes: List<PlanPass>) {
    if (passes.none { it is PlanPass.RenderPass && it.draws().any { draw -> draw.blend is BlendPlan.DestinationReadV1 } }) return
    var versionI64 = 0L
    passes.forEachIndexed { indexI32, pass ->
        when (pass) {
            is PlanPass.RenderPass -> {
                val initialClear = indexI32 == 0 && pass.draws().isEmpty() && pass.load == AttachmentLoadPlan.ClearTransparent
                require(pass.destinationVersionAfter?.valueI64 == if (initialClear) 0L else Math.addExact(versionI64, 1L)) { "invalid.w5b.destination-version" }
                pass.draws().forEach { draw ->
                    val blend = draw.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
                    val copy = passes.getOrNull(indexI32 - 1) as? PlanPass.TextureCopy
                    require(pass.draws().size == 1 && copy != null && copy.source == pass.target &&
                        copy.destination == blend.snapshotResource && copy.destinationVersion == blend.requiredDestinationVersion &&
                        blend.requiredDestinationVersion.valueI64 == versionI64 && blend.compositionAbiI32 == 3) {
                        "invalid.w5b.stale-destination-snapshot"
                    }
                }
                versionI64 = requireNotNull(pass.destinationVersionAfter).valueI64
            }
            is PlanPass.TextureCopy -> require(pass.destinationVersion?.valueI64 == versionI64 && indexI32 > 0) {
                "invalid.w5b.destination-copy-version"
            }
            is PlanPass.ReadbackPass -> Unit
            else -> error("invalid.w5b.destination-pass")
        }
    }
}
