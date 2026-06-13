package org.graphiks.kanvas.gpu.renderer.payloads

/** Opaque payload slot identifier. */
@JvmInline
value class GPUPayloadSlotID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPayloadSlotID.value must not be blank" }
    }
}

/** Stable payload fingerprint. */
@JvmInline
value class GPUPayloadFingerprint(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPayloadFingerprint.value must not be blank" }
    }
}

/** Material payload facts gathered before upload. */
data class GPUMaterialPayload(
    val materialKeyHash: String,
    val payloadClass: String,
    val valueFacts: Map<String, String>,
    val resourceFacts: Map<String, String>,
    val diagnosticLabel: String,
)

/** Payload gather plan. */
data class GPUPayloadGatherPlan(
    val planHash: String,
    val commandFamily: String,
    val materialAssemblyHash: String,
    val renderStepIdentity: String,
    val writePlanHash: String,
    val bindingPlanHash: String,
    val uploadPlanHash: String,
    val dedupScope: String,
    val unsupportedReason: String? = null,
)

/** Payload write plan for one draw or pass. */
data class GPUPayloadWritePlan(
    val planHash: String,
    val packingPlanHash: String,
    val bindingLayoutHash: String,
    val fieldWriteOrder: List<String>,
    val sourceValuePaths: List<String>,
    val resourceBindingOrder: List<String>,
)

/** Uniform payload block prepared for upload. */
data class GPUUniformPayloadBlock(
    val fingerprint: GPUPayloadFingerprint,
    val packingPlanHash: String,
    val byteSize: Long,
    val zeroedPadding: Boolean,
    val scope: String,
)

/** Uniform payload slot binding. */
data class GPUUniformPayloadSlot(
    val slotId: GPUPayloadSlotID,
    val fingerprint: GPUPayloadFingerprint,
    val byteOffset: Long,
)

/** Resource binding block prepared for a pass. */
data class GPUResourceBindingBlock(
    val fingerprint: GPUPayloadFingerprint,
    val bindingPlanHash: String,
    val bindingCount: Int,
    val resourceDescriptorLabels: List<String>,
    val dynamicOffsets: List<Long> = emptyList(),
)

/** Resource binding slot. */
data class GPUResourceBindingSlot(
    val slotId: GPUPayloadSlotID,
    val fingerprint: GPUPayloadFingerprint,
    val bindingIndex: Int,
)

/** Payload binding plan. */
data class GPUPayloadBindingPlan(
    val planHash: String,
    val bindGroupRole: String,
    val bindingOrder: List<String>,
    val resourceClasses: List<String>,
    val dynamicOffsetPolicy: String,
)

/** Payload upload plan. */
data class GPUPayloadUploadPlan(
    val planHash: String,
    val byteRanges: List<LongRange>,
    val stagingScope: String,
    val budgetClass: String,
    val beforeUseToken: String,
)

/** Gradient payload storage plan. */
data class GPUGradientPayloadStore(
    val fingerprint: GPUPayloadFingerprint,
    val stopCount: Int,
    val storageLayoutHash: String,
    val byteSize: Long,
    val passLocalOffset: Long,
    val uploadPlanHash: String,
)

/** Reference from a draw invocation to pass-local payload. */
data class GPUDrawPayloadRef(
    val commandIdValue: Int,
    val renderStepIdentity: String,
    val uniformSlot: GPUUniformPayloadSlot? = null,
    val resourceSlot: GPUResourceBindingSlot? = null,
    val gradientStore: GPUGradientPayloadStore? = null,
)

/** Payload gathering contract. */
interface GPUPayloadGatherer {
    /** Gathers one payload reference without uploading it. */
    fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef = TODO("Wire GPUPayloadGatherer to concrete payload packing")

    /** Resets pass-local state for a payload scope. */
    fun reset(scopeId: String): Unit = TODO("Wire GPUPayloadGatherer.reset to pass-local payload storage")
}

/** Payload diagnostic. */
data class GPUPayloadDiagnostic(
    val code: String,
    val planHash: String? = null,
    val slotId: GPUPayloadSlotID? = null,
    val field: String? = null,
    val terminal: Boolean,
)
