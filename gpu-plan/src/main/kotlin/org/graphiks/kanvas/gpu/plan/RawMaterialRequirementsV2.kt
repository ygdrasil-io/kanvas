package org.graphiks.kanvas.gpu.plan

/** Handle-free raw V2 binding layout, shared by capability sealing and native packing. */
public class RawMaterialRequirementsV2 private constructor(
    public val bindingCountI32: Int,
    public val uniformByteCountI64: Long,
    public val hasCoordinatesV2: Boolean,
    private val bindGroupEntryCountI32: Int,
) {
    /** Native buffers bind at offset zero; alignment never adds a dynamic stride. */
    public fun fitsUniformBinding(capabilities: PlanCapabilitySnapshot): Boolean =
        uniformByteCountI64 in 1L..Int.MAX_VALUE.toLong() &&
            uniformByteCountI64 <= UInt.MAX_VALUE.toLong() &&
            uniformByteCountI64 % BINDING_STRIDE_BYTES_I64 == 0L &&
            capabilities.maxUniformBufferBindingSizeBytesI64?.let { uniformByteCountI64 <= it } == true &&
            uniformByteCountI64 <= capabilities.maxBufferSizeBytes &&
            (!hasCoordinatesV2 || capabilities.minUniformBufferOffsetAlignment.let { it > 0 && it and (it - 1) == 0 } &&
                capabilities.maxBindingsPerBindGroupI32?.let { it >= bindGroupEntryCountI32 } == true &&
                capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
                capabilities.maxBindGroupsI32?.let { it >= 2 } == true)

    public class Refusal internal constructor(public val code: String) : IllegalArgumentException(code)

    public companion object {
        public const val BINDING_STRIDE_BYTES_I64: Long = 16L

        /** Geometry, target/readback, snapshots and stops enter exactly once from their owners. */
        public fun requireFrameBudget(
            sources: List<RawMaterialRequirementsV2>,
            nonUniformBytesI64: Long,
            budget: PlanBudget,
            legacyCode: String,
        ) {
            val code = if (sources.any { it.hasCoordinatesV2 }) W5dPlanDiagnostics.CoordinateUniformBudget else legacyCode
            val totalI64 = try {
                require(nonUniformBytesI64 >= 0L)
                sources.fold(nonUniformBytesI64) { totalI64, source -> Math.addExact(totalI64, source.uniformByteCountI64) }
            } catch (_: ArithmeticException) { throw Refusal(code) }
            if (totalI64 > budget.maxFrameLocalBytes) throw Refusal(code)
        }

        public fun of(table: MaterialPlanTable, root: MaterialPlanRef): RawMaterialRequirementsV2 {
            require(root.indexI32 in 0 until table.sizeI32)
            var indexI32 = root.indexI32
            var countI32 = 1
            while (table.entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) {
                require(indexI32 > 0) { "Raw material opacity requires its captured child" }
                indexI32--
                countI32 = Math.addExact(countI32, 1)
            }
            val entry = table.entry(MaterialPlanRef(indexI32))
            val binding = entry.bindings
            val gradient = binding is MaterialBindingPlan.GradientV1
            if (binding is MaterialBindingPlan.GradientV2) {
                val coordinates = requireNotNull(table.coordinatesV2(root)) { W5dPlanDiagnostics.CoordinatePlanSchema }
                val slab = requireNotNull(table.gradientStopSlab) { W5dPlanDiagnostics.CoordinatePlanSchema }
                val program = entry.program as? GradientAddressingProgramV2
                require(program != null && binding.numericAuthority.authenticates(program, binding, slab, coordinates)) {
                    W5dPlanDiagnostics.CoordinatePlanSchema
                }
                val endI64 = Math.addExact(binding.stopRange.baseIndexU32.toLong(), binding.stopRange.countU32.toLong())
                require(binding.stopRange.countU32 > 0u && endI64 <= UInt.MAX_VALUE.toLong() &&
                    endI64 <= Int.MAX_VALUE.toLong() && endI64 <= slab.copyStops().size.toLong()) {
                    W5dPlanDiagnostics.CoordinatePlanSchema
                }
            }
            val bytesI64 = Math.addExact(
                Math.multiplyExact(countI32.toLong(), BINDING_STRIDE_BYTES_I64),
                when (binding) {
                    is MaterialBindingPlan.GradientV2 -> {
                        val familyBytesI64 = when (binding) {
                            is MaterialBindingPlan.LinearGradientV2 -> 32L
                            is MaterialBindingPlan.RadialGradientV2 -> 0L
                            is MaterialBindingPlan.SweepGradientV2 -> 16L
                            is MaterialBindingPlan.ConicalGradientV2 -> 96L
                        }
                        // Two headers, family fields, optional straight average, then
                        // the exact ordered coordinate fields. The stop-buffer ABI is separate.
                        listOf(32L, familyBytesI64, if (binding.degenerateAverageSrgbaF32 != null) 16L else 0L,
                            requireNotNull(table.coordinatesV2(root)).uniformByteSizeI64).fold(0L, Math::addExact)
                    }
                    is MaterialBindingPlan.LinearGradientV1 -> 112L // common 80 + 2 sealed scalar vectors
                    is MaterialBindingPlan.ConicalGradientV1 -> 176L // common 80 + 4 scalar vectors + 2 flag vectors
                    is MaterialBindingPlan.SweepGradientV1 -> 96L
                    else -> if (gradient) 80L else 0L
                })
            require(bytesI64 <= UInt.MAX_VALUE.toLong() && bytesI64 <= Int.MAX_VALUE.toLong()) {
                if (binding is MaterialBindingPlan.GradientV2) W5dPlanDiagnostics.CoordinateUniformBudget
                else "resource-limit.w5b.source-binding"
            }
            return RawMaterialRequirementsV2(countI32, bytesI64, binding is MaterialBindingPlan.GradientV2,
                if (gradient || binding is MaterialBindingPlan.GradientV2) 2 else 1)
        }
    }
}
