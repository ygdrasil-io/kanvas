package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/** Runtime-effect identifier. */
@JvmInline
value class GPURuntimeEffectID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURuntimeEffectID.value must not be blank" }
    }
}

/** Runtime-effect descriptor version. */
@JvmInline
value class GPURuntimeEffectDescriptorVersion(val value: Int) {
    init {
        require(value >= 0) { "GPURuntimeEffectDescriptorVersion.value must be non-negative" }
    }
}

/** Registry for product-supported runtime effects. */
interface GPURuntimeEffectRegistry {
    /** Looks up a registered descriptor by ID. */
    fun lookup(id: GPURuntimeEffectID): GPURuntimeEffectDescriptor? = TODO("Wire GPURuntimeEffectRegistry to registered Kotlin/WGSL descriptors")
}

/** Runtime-effect uniform schema. */
data class GPURuntimeEffectUniformSchema(
    val schemaHash: String,
    val fields: List<String>,
    val packingPolicy: String,
)

/** Runtime-effect uniform block plan. */
data class GPURuntimeEffectUniformBlockPlan(
    val schema: GPURuntimeEffectUniformSchema,
    val blockSizeBytes: Long,
    val dynamicOffsets: Boolean,
)

/** Runtime-effect child slot plan. */
data class GPURuntimeEffectChildSlotPlan(
    val slotName: String,
    val acceptedSourceKinds: Set<String>,
    val required: Boolean,
)

/** Runtime-effect resource plan. */
data class GPURuntimeEffectResourcePlan(
    val resourceLabels: List<String>,
    val bindingPlanHash: String,
)

/** Runtime-effect WGSL plan. */
data class GPURuntimeEffectWGSLPlan(
    val moduleHash: String,
    val entryPoint: String,
    val reflectionHash: String,
)

/** CPU oracle contract for reference validation only. */
interface GPURuntimeEffectCPUOracle {
    /** Evaluates reference output for evidence, not product fallback. */
    fun evaluate(): GPURuntimeEffectOracleResult = TODO("Wire GPURuntimeEffectCPUOracle to explicit validation-only evidence")
}

/** CPU oracle result used only for validation evidence. */
data class GPURuntimeEffectOracleResult(
    val effectId: GPURuntimeEffectID,
    val evidenceHash: String,
    val diagnostics: List<GPURuntimeEffectDiagnostic> = emptyList(),
)

/** Runtime-effect route contract. */
data class GPURuntimeEffectRouteContract(
    val nativeSupported: Boolean,
    val cpuOracleOnly: Boolean,
    val refusalCode: String? = null,
)

/** Runtime-effect live edit plan. */
data class GPURuntimeEffectLiveEditPlan(
    val enabled: Boolean,
    val descriptorVersion: GPURuntimeEffectDescriptorVersion,
    val validationPolicy: String,
)

/** Runtime-effect usage set captured by a recording. */
data class GPURuntimeEffectUsageSet(
    val effectIds: Set<GPURuntimeEffectID>,
    val descriptorVersions: Map<GPURuntimeEffectID, GPURuntimeEffectDescriptorVersion>,
)

/** Runtime-effect descriptor. */
data class GPURuntimeEffectDescriptor(
    val id: GPURuntimeEffectID,
    val version: GPURuntimeEffectDescriptorVersion,
    val uniformSchema: GPURuntimeEffectUniformSchema,
    val uniformBlockPlan: GPURuntimeEffectUniformBlockPlan,
    val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    val resources: GPURuntimeEffectResourcePlan,
    val wgslPlan: GPURuntimeEffectWGSLPlan,
    val routeContract: GPURuntimeEffectRouteContract,
    val liveEditPlan: GPURuntimeEffectLiveEditPlan,
    val diagnostics: List<GPURuntimeEffectDiagnostic> = emptyList(),
)

/** Runtime-effect diagnostic. */
data class GPURuntimeEffectDiagnostic(
    val code: String,
    val effectId: GPURuntimeEffectID? = null,
    val message: String,
    val terminal: Boolean,
)
