package org.graphiks.kanvas.gpu.plan

/** Ordered final-color authority. Native geometry and upload plans remain with their issuers. */
public class W5bMixedFramePlanV1 private constructor(
    public val targetId: PlanResourceId,
    public val capabilities: PlanCapabilitySnapshot,
    public val budget: PlanBudget,
    draws: List<Draw>,
) {
    public val draws: List<Draw> = java.util.Collections.unmodifiableList(ArrayList(draws))

    public enum class RefusalReason(public val code: String) {
        Capability("unsupported.w5b.mixed-capability"),
        DestinationTexture("unsupported.w5b.mixed-destination-texture"),
        SourceBinding("resource-limit.w5b.mixed-source-binding"),
        SourceBudget("resource-limit.w5b.mixed-source-budget"),
    }

    /** Only this module's sealer can issue a typed admission refusal. */
    public class Refusal internal constructor(public val reason: RefusalReason) : IllegalArgumentException(reason.code)

    public data class Input(
        public val commandIndexI32: Int,
        public val sourceTable: MaterialPlanTable,
        public val sourceRef: MaterialPlanRef,
        public val blend: BlendPlan,
        public val snapshotResource: PlanResourceId?,
    )

    public data class Draw(
        public val commandIndexI32: Int,
        public val sourceTable: MaterialPlanTable,
        public val sourceRef: MaterialPlanRef,
        public val blend: BlendPlan,
        public val versionBefore: DestinationVersionI64,
        public val versionAfter: DestinationVersionI64,
    )

    public companion object {
        /** Inputs have already passed geometry/coverage admission; this method never classifies. */
        public fun seal(
            targetId: PlanResourceId,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
            inputs: List<Input>,
        ): W5bMixedFramePlanV1 {
            require(inputs.map { it.commandIndexI32 }.distinct().size == inputs.size &&
                inputs.zipWithNext().all { (a, b) -> a.commandIndexI32 < b.commandIndexI32 }) {
                "invalid.w5b.mixed-command-order"
            }
            val readsDestination = inputs.any { it.blend is BlendPlan.DestinationReadV1 }
            val slabs = inputs.mapNotNull { it.sourceTable.gradientStopSlab }.distinctBy { it.canonicalIdentity }
            slabs.forEach { it.requireStorageCapabilities(capabilities) }
            // Common source + geometry + destination ABI. Prepared child witnesses retain
            // their additional atlas/vertex checks; gradient storage was admitted above.
            admit(RefusalReason.Capability, capabilities.maxBindGroupsI32?.let { it >= if (readsDestination) 3 else 2 } == true &&
                capabilities.maxBindingsPerBindGroupI32?.let { it >= if (readsDestination) 2 else 1 } == true &&
                (!readsDestination || capabilities.maxSampledTexturesPerShaderStageI32?.let { it >= 1 } == true &&
                    capabilities.maxSamplersPerShaderStageI32?.let { it >= 1 } == true) &&
                capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
                capabilities.maxUniformBufferBindingSizeBytesI64?.let { it >= 32L } == true)
            admit(RefusalReason.DestinationTexture, !readsDestination || capabilities.supportsTexture(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1,
                setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.Sampled)))
            var versionI64 = 0L
            var sourceBytesI64 = slabs.fold(0L) { totalI64, slab -> Math.addExact(totalI64, slab.byteSizeI64) }
            val draws = inputs.map { input ->
                require(input.commandIndexI32 >= 0)
                val source = RawMaterialRequirementsV2.of(input.sourceTable, input.sourceRef)
                if (input.blend != BlendPlan.NoOpV1) sourceBytesI64 = Math.addExact(sourceBytesI64, source.uniformByteCountI64)
                admit(RefusalReason.SourceBinding, input.blend == BlendPlan.NoOpV1 || capabilities.maxUniformBufferBindingSizeBytesI64?.let {
                    source.uniformByteCountI64 <= it
                } == true && source.uniformByteCountI64 <= capabilities.maxBufferSizeBytes)
                val before = DestinationVersionI64(versionI64)
                val blend = when (val selected = input.blend) {
                    is BlendPlan.DestinationReadV1 -> selected.copy(
                        requiredDestinationVersion = before,
                        snapshotResource = requireNotNull(input.snapshotResource) {
                            "invalid.w5b.mixed-snapshot-missing"
                        },
                    )
                    else -> {
                        require(input.snapshotResource == null) { "invalid.w5b.mixed-unused-snapshot" }
                        selected
                    }
                }
                if (blend != BlendPlan.NoOpV1) versionI64 = Math.addExact(versionI64, 1L)
                Draw(input.commandIndexI32, input.sourceTable, input.sourceRef, blend,
                    before, DestinationVersionI64(versionI64))
            }
            admit(RefusalReason.SourceBudget, sourceBytesI64 <= budget.maxFrameLocalBytes)
            return W5bMixedFramePlanV1(targetId, capabilities, budget, draws)
        }

        private fun admit(reason: RefusalReason, condition: Boolean) {
            if (!condition) throw Refusal(reason)
        }
    }
}
