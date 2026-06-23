package org.graphiks.kanvas.gpu.renderer.payloads

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

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

/**
 * Uniform payload field placement fact with byte-level zero-fill evidence.
 *
 * A field describes one contiguous range inside its owning [GPUUniformPayloadBlock].
 * [byteOffset] is measured from the start of that block and [byteSize] must be
 * positive unless a later ABI revision explicitly introduces zero-width markers.
 * Producers must keep every field range inside the block, avoid overlapping field
 * ranges, and encode values with the byte order required by the packing plan.
 * [zeroFilled] means all bytes in this field range are zero after packing; for
 * real value fields this is value evidence, while padding fields use it as ABI
 * evidence that unused bytes were cleared before upload. These placement
 * invariants are not enforced by the data-class constructor; payload producers
 * and validation/replay consumers must reject malformed ranges before upload or
 * promotion evidence is accepted.
 */
data class GPUUniformPayloadField(
    val fieldPath: String,
    val byteOffset: Long,
    val byteSize: Long,
    val valueClass: String,
    val zeroFilled: Boolean = false,
)

/**
 * Uniform payload block prepared for upload, before any resource or staging allocation.
 *
 * The block is CPU-owned evidence for the exact bytes that would be copied into a
 * uniform buffer. [byteSize] is the required upload size and, when [bytes] are
 * present, callers must preserve `bytes.size == byteSize`; each byte is stored as
 * an unsigned `0..255` value even though Kotlin represents it as [Int]. Multi-byte
 * scalar values follow the byte order named by the packing plan used to produce
 * [packingPlanHash] (the first-route solid rect packer writes little-endian
 * floats). [fields] must describe byte ranges within this block. [zeroedPadding]
 * is true only when padding bytes outside value fields are zero, so reviewers can
 * distinguish cleared ABI padding from real field values that happen to be zero.
 * The constructor snapshots none of these ABI invariants by itself; invalid
 * byte counts, out-of-range byte values, or overlapping fields must be refused by
 * the producer/consumer that interprets the block.
 */
data class GPUUniformPayloadBlock(
    val fingerprint: GPUPayloadFingerprint,
    val packingPlanHash: String,
    val byteSize: Long,
    val zeroedPadding: Boolean,
    val scope: String,
    val bytes: List<Int> = emptyList(),
    val fields: List<GPUUniformPayloadField> = emptyList(),
)

/**
 * Uniform payload slot binding.
 *
 * A slot names a pass-local byte range in a uniform payload block. [byteOffset] is expected to be
 * non-negative and aligned for the referenced ABI layout, and [fingerprint] must name a stable
 * block produced by the gatherer. These invariants are not constructor-enforced; upload and submit
 * producers must refuse invalid offsets or stale fingerprints before promotion evidence is accepted.
 */
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
    val bindingFacts: List<GPUResourceBindingFact> = emptyList(),
)

/** Resource binding kind described by a payload binding block. */
enum class GPUResourceBindingKind {
    /** The binding references the uploaded uniform payload buffer. */
    UniformBuffer,
    /** The binding references a storage payload buffer. */
    StorageBuffer,
    /** The binding references a sampled texture view. */
    SampledTexture,
    /** The binding references a sampler. */
    Sampler,
}

/**
 * Binding-level resource facts required before bind group materialization.
 *
 * These are descriptor and generation facts, not backend handles. Texture and
 * sampler bindings may be validated here, while live image/sampler ownership
 * remains gated by the later texture materialization lane.
 */
data class GPUResourceBindingFact(
    val bindingLabel: String,
    val kind: GPUResourceBindingKind,
    val descriptorHash: String,
    val requiredUsageLabels: Set<String>,
    val availableUsageLabels: Set<String>,
    val expectedResourceGeneration: Long,
    val actualResourceGeneration: Long,
    val evictedReason: String? = null,
) {
    init {
        require(bindingLabel.isNotBlank()) { "GPUResourceBindingFact.bindingLabel must not be blank" }
        require(descriptorHash.isNotBlank()) { "GPUResourceBindingFact.descriptorHash must not be blank" }
        require(requiredUsageLabels.none { label -> label.isBlank() }) {
            "GPUResourceBindingFact.requiredUsageLabels must not contain blank labels"
        }
        require(availableUsageLabels.none { label -> label.isBlank() }) {
            "GPUResourceBindingFact.availableUsageLabels must not contain blank labels"
        }
        require(expectedResourceGeneration >= 0L) {
            "GPUResourceBindingFact.expectedResourceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUResourceBindingFact.actualResourceGeneration must be non-negative"
        }
        require(evictedReason == null || evictedReason.isNotBlank()) {
            "GPUResourceBindingFact.evictedReason must not be blank"
        }
    }
}

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
    val uniformBlock: GPUUniformPayloadBlock? = null,
    val resourceBlock: GPUResourceBindingBlock? = null,
)

/** Payload gathering contract. */
interface GPUPayloadGatherer {
    /** Gathers one payload reference without uploading it. */
    fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef = TODO("Wire GPUPayloadGatherer to concrete payload packing")

    /** Resets pass-local state for a payload scope. */
    fun reset(scopeId: String): Unit = TODO("Wire GPUPayloadGatherer.reset to pass-local payload storage")
}

/**
 * Minimal pass-local gatherer for first-slice solid rect payloads.
 *
 * This gatherer owns only CPU-side payload value validation and deterministic
 * slot facts. It refuses malformed required values before fingerprinting, does
 * not upload buffers, and does not create resource bindings or backend handles.
 */
class GPUSolidPayloadGatherer : GPUPayloadGatherer {
    private val uniformSlots = LinkedHashMap<GPUPayloadFingerprint, GPUUniformPayloadSlot>()
    private var currentScopeId: String? = null

    override fun gather(plan: GPUPayloadGatherPlan, payload: GPUMaterialPayload): GPUDrawPayloadRef {
        require(plan.unsupportedReason == null) { "Cannot gather unsupported payload plan ${plan.planHash}" }
        require(payload.payloadClass == solidPayloadClass) {
            "GPUSolidPayloadGatherer only supports $solidPayloadClass payloads"
        }
        enterScope(plan.dedupScope)

        val fieldValues = solidRectFieldValues(payload.valueFacts)
        val bytes = solidRectPayloadBytes(fieldValues)
        val fingerprint = GPUPayloadFingerprint(
            sha256Hex(
                listOf(
                    "kind=solid-rect-uniform",
                    "material=${payload.materialKeyHash}",
                    "write=${plan.writePlanHash}",
                    "bytes=${bytes.joinToString(",")}",
                ).joinToString("\n"),
            ),
        )
        val block = GPUUniformPayloadBlock(
            fingerprint = fingerprint,
            packingPlanHash = solidRectPackingPlanHash,
            byteSize = solidRectByteSize.toLong(),
            zeroedPadding = bytes.drop(solidRectUsedByteSize).all { it == 0 },
            scope = plan.dedupScope,
            bytes = bytes,
            fields = solidRectFields(fieldValues),
        )
        val slot = uniformSlots.getOrPut(fingerprint) {
            GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("${plan.dedupScope}:uniform:${uniformSlots.size}"),
                fingerprint = fingerprint,
                byteOffset = 0L,
            )
        }

        return GPUDrawPayloadRef(
            commandIdValue = payload.valueFacts.requiredInt("command.id"),
            renderStepIdentity = plan.renderStepIdentity,
            uniformSlot = slot,
            uniformBlock = block,
        )
    }

    override fun reset(scopeId: String) {
        enterScope(scopeId)
        uniformSlots.clear()
    }

    private fun enterScope(scopeId: String) {
        require(scopeId.isNotBlank()) { "scopeId must not be blank" }
        if (currentScopeId != scopeId) {
            uniformSlots.clear()
            currentScopeId = scopeId
        }
    }

    private fun solidRectFieldValues(valueFacts: Map<String, String>): List<Pair<String, Float>> =
        solidRectFloatFields.map { fieldPath ->
            fieldPath to valueFacts.requiredFiniteFloat(fieldPath)
        }

    private fun solidRectPayloadBytes(fieldValues: List<Pair<String, Float>>): List<Int> {
        val buffer = ByteBuffer.allocate(solidRectByteSize).order(ByteOrder.LITTLE_ENDIAN)
        fieldValues.forEach { (_, value) ->
            buffer.putFloat(value)
        }
        return buffer.array().map { byte -> byte.toInt() and 0xff }
    }

    private fun solidRectFields(fieldValues: List<Pair<String, Float>>): List<GPUUniformPayloadField> =
        fieldValues.mapIndexed { index, (fieldPath, value) ->
            GPUUniformPayloadField(
                fieldPath = fieldPath,
                byteOffset = index * Float.SIZE_BYTES.toLong(),
                byteSize = Float.SIZE_BYTES.toLong(),
                valueClass = "f32",
                zeroFilled = value.toRawBits() == 0,
            )
        } + GPUUniformPayloadField(
            fieldPath = "padding.reserved",
            byteOffset = solidRectUsedByteSize.toLong(),
            byteSize = (solidRectByteSize - solidRectUsedByteSize).toLong(),
            valueClass = "padding",
            zeroFilled = true,
        )

    private fun Map<String, String>.requiredFiniteFloat(fieldPath: String): Float {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        val value = rawValue.toFloatOrNull()
        require(value != null) { "Payload field $fieldPath must be a float" }
        require(value.isFinite()) { "Payload field $fieldPath must be finite" }
        if (fieldPath.startsWith("color.")) {
            require(value in 0f..1f) { "Payload field $fieldPath must be in 0..1" }
        }
        if (fieldPath.startsWith("radii.")) {
            require(value >= 0f) { "Payload field $fieldPath must be non-negative" }
        }
        return value
    }

    private fun Map<String, String>.requiredInt(fieldPath: String): Int {
        val rawValue = this[fieldPath]
        require(rawValue != null) { "Payload field $fieldPath is required" }
        return requireNotNull(rawValue.toIntOrNull()) { "Payload field $fieldPath must be an integer" }
    }

    private companion object {
        private const val solidPayloadClass = "solid-rgba-rect"
        private const val solidRectPackingPlanHash = "solid-rect-layout-v1"
        private const val solidRectByteSize = 64
        private const val solidRectUsedByteSize = 48

        private val solidRectFloatFields = listOf(
            "rect.left",
            "rect.top",
            "rect.right",
            "rect.bottom",
            "radii.topLeft",
            "radii.topRight",
            "radii.bottomRight",
            "radii.bottomLeft",
            "color.r",
            "color.g",
            "color.b",
            "color.a",
        )

    }
}

/** Payload diagnostic. */
data class GPUPayloadDiagnostic(
    val code: String,
    val planHash: String? = null,
    val slotId: GPUPayloadSlotID? = null,
    val field: String? = null,
    val terminal: Boolean,
)

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
