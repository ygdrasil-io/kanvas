package org.graphiks.kanvas.gpu.plan

/** Checked metadata and exact recipe owners, never packed arrays. */
public class MaterialSourceFootprintV4 internal constructor(internal val table: MaterialPlanTable,
    internal val root: MaterialPlanRef, internal val proof: ColorSourceProofV1) {
    public val canonicalIdentity: String = "material-source-footprint-v4:${proof.canonicalIdentity}"
    public val uniformByteCountI64: Long = Math.multiplyExact(proof.uniformWordCountI64,4L)
    public val sourceUniformByteCountI64: Long = Math.multiplyExact(proof.sourceUniformWordCountI64,4L)
    public val storageByteCountI64: Long = proof.gradientStopSlab?.byteSizeI64 ?: 0L
    public val bindingCountI32: Int = (if (storageByteCountI64 == 0L) 1 else 2) + (if (proof.imageExecution == null) 0 else 1)
    internal fun authenticates(): Boolean = table.colorSourceProofV4(root) === proof && proof.authenticates(table,root,proof.coordinates)
}

public class MaterialSourcePackingPermitV4 private constructor(private val footprints: List<MaterialSourceFootprintV4>) {
    internal fun permits(footprint: MaterialSourceFootprintV4): Boolean = footprints.any { it === footprint } && footprint.authenticates()
    internal companion object {
        fun issue(footprints: List<MaterialSourceFootprintV4>, nonUniformBytesI64: Long,
            budget: PlanBudget, capabilities: PlanCapabilitySnapshot, legacyCode: String): MaterialSourcePackingPermitV4 {
            fun fail(code: String): Nothing = throw RawMaterialRequirementsV2.Refusal(code)
            val unique = footprints.distinctBy { it.canonicalIdentity }
            var baseI64 = nonUniformBytesI64
            require(baseI64 >= 0L)
            try {
                unique.forEach { footprint ->
                    require(footprint.authenticates()) { W5fPlanDiagnostics.Schema }
                    baseI64 = Math.addExact(baseI64,footprint.sourceUniformByteCountI64)
                }
                if (baseI64 > budget.maxFrameLocalBytes) fail(legacyCode)
                for (footprint in unique) {
                    val sizeI64 = footprint.uniformByteCountI64
                    requireColorUniformBindingV4(sizeI64,capabilities,footprint.bindingCountI32)
                    if (footprint.storageByteCountI64 > 0L)
                        requireGradientStorageCapabilitiesV4(footprint.storageByteCountI64,capabilities)
                    baseI64 = Math.addExact(baseI64,sizeI64-footprint.sourceUniformByteCountI64)
                }
            } catch (_: ArithmeticException) { fail(W5fPlanDiagnostics.FilterUniform) }
            if (baseI64 > budget.maxFrameLocalBytes) fail(W5fPlanDiagnostics.FilterUniform)
            return MaterialSourcePackingPermitV4(immutableList(footprints))
        }
    }
}

/** The identical scalar layout check is usable before pending stop preparation. */
internal fun requireColorUniformBindingV4(sizeI64: Long, capabilities: PlanCapabilitySnapshot,
    bindingCountI32: Int = 1, code: String = W5fPlanDiagnostics.FilterBinding) {
    if (sizeI64 !in 16L..Int.MAX_VALUE.toLong() || sizeI64 % 16L != 0L ||
        sizeI64 > capabilities.maxBufferSizeBytes ||
        capabilities.maxUniformBufferBindingSizeBytesI64?.let { sizeI64 <= it } != true ||
        capabilities.maxBindingsPerBindGroupI32?.let { it >= bindingCountI32 } != true ||
        capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } != true ||
        capabilities.maxBindGroupsI32?.let { it >= 2 } != true ||
        capabilities.minUniformBufferOffsetAlignment <= 0 ||
        capabilities.minUniformBufferOffsetAlignment.let { it and (it-1) != 0 }) throw RawMaterialRequirementsV2.Refusal(code)
}
