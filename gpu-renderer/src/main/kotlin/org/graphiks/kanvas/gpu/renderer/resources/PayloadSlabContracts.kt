package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest
import java.util.Collections

/** Backend-neutral batch request for uniform-payload slab planning. */
class GPUPayloadSlabBatchRequest(
    val targetId: String,
    val frameId: String,
    val sourceLabel: String,
    val deviceGeneration: Long,
    val alignmentBytes: Long,
    val uploadBudgetBytes: Long,
    payloadRequests: List<GPUPayloadMaterializationRequest>,
) {
    private val payloadRequestsSnapshot: List<GPUPayloadMaterializationRequest> =
        Collections.unmodifiableList(payloadRequests.toList())

    val payloadRequests: List<GPUPayloadMaterializationRequest>
        get() = payloadRequestsSnapshot

    init {
        require(targetId.isNotBlank()) { "GPUPayloadSlabBatchRequest.targetId must not be blank" }
        require(frameId.isNotBlank()) { "GPUPayloadSlabBatchRequest.frameId must not be blank" }
        require(sourceLabel.isNotBlank()) { "GPUPayloadSlabBatchRequest.sourceLabel must not be blank" }
        require(deviceGeneration >= 0L) {
            "GPUPayloadSlabBatchRequest.deviceGeneration must be non-negative"
        }
        require(alignmentBytes > 0L) { "GPUPayloadSlabBatchRequest.alignmentBytes must be positive" }
        require(uploadBudgetBytes >= 0L) {
            "GPUPayloadSlabBatchRequest.uploadBudgetBytes must be non-negative"
        }
    }
}

/** Mapping between an accepted slab slot and the source payload request facts. */
data class GPUPayloadSlabSlotBinding(
    val slotLabel: String,
    val packetId: String,
    val uniformSlotId: String,
    val resourceSlotId: String,
    val payloadFingerprint: String,
    val reflectedBindingLayoutHash: String,
    val alignedOffset: Long,
    val payloadBytes: Long,
) {
    init {
        listOf(
            "GPUPayloadSlabSlotBinding.slotLabel" to slotLabel,
            "GPUPayloadSlabSlotBinding.packetId" to packetId,
            "GPUPayloadSlabSlotBinding.uniformSlotId" to uniformSlotId,
            "GPUPayloadSlabSlotBinding.resourceSlotId" to resourceSlotId,
            "GPUPayloadSlabSlotBinding.payloadFingerprint" to payloadFingerprint,
            "GPUPayloadSlabSlotBinding.reflectedBindingLayoutHash" to reflectedBindingLayoutHash,
        ).forEach { (field, value) ->
            require(value.isNotBlank()) { "$field must not be blank" }
            requireDumpSafePayloadSlabValue(field, value)
        }
        require(alignedOffset >= 0L) { "GPUPayloadSlabSlotBinding.alignedOffset must be non-negative" }
        require(payloadBytes > 0L) { "GPUPayloadSlabSlotBinding.payloadBytes must be positive" }
    }
}

/** Accepted backend-neutral payload slab plan. */
class GPUPayloadSlabBatchPlan(
    val planHash: String,
    val sourceLabel: String,
    val targetId: String,
    val frameId: String,
    val deviceGeneration: Long,
    val uniformSlabPlan: GPUUniformSlabPlan,
    slotBindings: List<GPUPayloadSlabSlotBinding>,
) {
    private val slotBindingsSnapshot: List<GPUPayloadSlabSlotBinding> =
        Collections.unmodifiableList(slotBindings.toList())

    val slotBindings: List<GPUPayloadSlabSlotBinding>
        get() = slotBindingsSnapshot

    init {
        listOf(
            "GPUPayloadSlabBatchPlan.planHash" to planHash,
            "GPUPayloadSlabBatchPlan.sourceLabel" to sourceLabel,
            "GPUPayloadSlabBatchPlan.targetId" to targetId,
            "GPUPayloadSlabBatchPlan.frameId" to frameId,
        ).forEach { (field, value) ->
            require(value.isNotBlank()) { "$field must not be blank" }
            requireDumpSafePayloadSlabValue(field, value)
        }
        require(deviceGeneration >= 0L) {
            "GPUPayloadSlabBatchPlan.deviceGeneration must be non-negative"
        }
        require(slotBindingsSnapshot.isNotEmpty()) {
            "GPUPayloadSlabBatchPlan.slotBindings must not be empty"
        }
        require(slotBindingsSnapshot.size == uniformSlabPlan.slots.size) {
            "GPUPayloadSlabBatchPlan.slotBindings must cover every uniform slab slot"
        }

        val bindingsByLabel = linkedMapOf<String, GPUPayloadSlabSlotBinding>()
        slotBindingsSnapshot.forEach { binding ->
            require(bindingsByLabel.put(binding.slotLabel, binding) == null) {
                "GPUPayloadSlabBatchPlan.slotBindings must use unique slotLabel values"
            }
        }

        uniformSlabPlan.slots.forEach { slot ->
            val binding = requireNotNull(bindingsByLabel[slot.slotLabel]) {
                "GPUPayloadSlabBatchPlan.slotBindings must contain ${slot.slotLabel}"
            }
            require(binding.alignedOffset == slot.alignedOffset) {
                "GPUPayloadSlabBatchPlan.binding offset must match uniform slab slot"
            }
            require(binding.payloadBytes == slot.payloadBytes) {
                "GPUPayloadSlabBatchPlan.binding payloadBytes must match uniform slab slot"
            }
        }
    }

    fun dumpLines(): List<String> {
        val lines = buildList {
            add(
                "payload-slab.batch.plan " +
                    "source=$sourceLabel " +
                    "target=$targetId " +
                    "frame=$frameId " +
                    "deviceGeneration=$deviceGeneration " +
                    "slots=${slotBindingsSnapshot.size} " +
                    "totalBytes=${uniformSlabPlan.totalBytes} " +
                    "hash=$planHash",
            )
            slotBindingsSnapshot.forEach { binding ->
                add(
                    "payload-slab.batch.slot " +
                        "source=$sourceLabel " +
                        "slot=${binding.slotLabel} " +
                        "packet=${binding.packetId} " +
                        "uniformSlot=${binding.uniformSlotId} " +
                        "resourceSlot=${binding.resourceSlotId} " +
                        "offset=${binding.alignedOffset} " +
                        "payloadBytes=${binding.payloadBytes} " +
                        "payloadFingerprint=${binding.payloadFingerprint} " +
                        "layout=${binding.reflectedBindingLayoutHash}",
                )
            }
            addAll(uniformSlabPlan.dumpLines())
        }
        lines.forEachIndexed { index, line ->
            requireDumpSafePayloadSlabValue("GPUPayloadSlabBatchPlan.dumpLines[$index]", line)
        }
        return lines
    }
}

/** Diagnostic surfaced when payload slab planning refuses a batch. */
class GPUPayloadSlabBatchDiagnostic(
    val code: String,
    val terminal: Boolean = true,
    factEntries: Map<String, String> = emptyMap(),
) {
    val facts: Map<String, String> = factEntries.toMap()

    init {
        require(code.isNotBlank()) { "GPUPayloadSlabBatchDiagnostic.code must not be blank" }
        requireDumpSafePayloadSlabValue("GPUPayloadSlabBatchDiagnostic.code", code)
        factEntries.forEach { (key, value) ->
            requireDumpSafePayloadSlabValue("GPUPayloadSlabBatchDiagnostic.facts key", key)
            requireDumpSafePayloadSlabValue("GPUPayloadSlabBatchDiagnostic.facts value", value)
        }
    }
}

/** Planner outcome for backend-neutral payload slab batches. */
sealed class GPUPayloadSlabBatchPlanningResult {
    data class Accepted(val plan: GPUPayloadSlabBatchPlan) : GPUPayloadSlabBatchPlanningResult()

    data class Refused(val diagnostic: GPUPayloadSlabBatchDiagnostic) : GPUPayloadSlabBatchPlanningResult() {
        fun dumpLines(): List<String> =
            listOf(
                "payload-slab.batch.refused " +
                    "code=${diagnostic.code} " +
                    "terminal=${diagnostic.terminal} " +
                    "facts=${diagnostic.facts.dumpFacts()}",
            )
    }
}

/** Planner that lifts payload materialization facts into a backend-neutral slab plan. */
object GPUPayloadSlabBatchPlanner {
    fun plan(request: GPUPayloadSlabBatchRequest): GPUPayloadSlabBatchPlanningResult {
        val unsafeBatchField = listOf(
            "targetId" to request.targetId,
            "frameId" to request.frameId,
            "sourceLabel" to request.sourceLabel,
        ).firstOrNull { (_, value) -> !isDumpSafePayloadSlabValue(value) }
        if (unsafeBatchField != null) {
            return refused("unsupported.payload_slab_dump_unsafe", mapOf("field" to unsafeBatchField.first))
        }

        val unsafePayloadField = request.payloadRequests.asSequence()
            .mapNotNull { payload ->
                listOf(
                    "packetId" to payload.packetId,
                    "uniformSlotId" to payload.uniformSlot.slotId.value,
                    "resourceSlotId" to payload.resourceSlot.slotId.value,
                    "payloadFingerprint" to payload.uniformBlock.fingerprint.value,
                    "reflectedBindingLayoutHash" to payload.reflectedBindingLayoutHash,
                    "resourceBindingLayoutHash" to payload.resourceBlock.bindingPlanHash,
                ).firstOrNull { (_, value) -> !isDumpSafePayloadSlabValue(value) }
            }
            .firstOrNull()
        if (unsafePayloadField != null) {
            return refused("unsupported.payload_slab_dump_unsafe", mapOf("field" to unsafePayloadField.first))
        }

        if (request.payloadRequests.isEmpty()) {
            return refused(
                "unsupported.payload_slab_empty_batch",
                mapOf("targetId" to request.targetId),
            )
        }

        val targetMismatch = request.payloadRequests.firstOrNull { payload -> payload.targetId != request.targetId }
        if (targetMismatch != null) {
            return refused(
                "unsupported.payload_slab_target_mismatch",
                mapOf("packetId" to targetMismatch.packetId),
            )
        }

        val generationMismatch = request.payloadRequests.firstOrNull { payload ->
            payload.deviceGeneration != request.deviceGeneration
        }
        if (generationMismatch != null) {
            return refused(
                "unsupported.payload_slab_generation_mismatch",
                mapOf("packetId" to generationMismatch.packetId),
            )
        }

        val layoutReference = request.payloadRequests.first().reflectedBindingLayoutHash
        val layoutMismatch = request.payloadRequests.firstOrNull { payload ->
            payload.alignmentBytes != request.alignmentBytes ||
                payload.reflectedBindingLayoutHash != payload.resourceBlock.bindingPlanHash ||
                payload.reflectedBindingLayoutHash != layoutReference
        }
        if (layoutMismatch != null) {
            return refused(
                "unsupported.payload_slab_layout_mismatch",
                mapOf("packetId" to layoutMismatch.packetId),
            )
        }

        val duplicate = duplicateSlotDiagnostic(request.payloadRequests)
        if (duplicate != null) {
            return refused("unsupported.payload_slab_duplicate_slot", duplicate)
        }

        val emptyPayload = request.payloadRequests.firstOrNull { payload ->
            payload.uniformBlock.bytes.isEmpty() || payload.uniformBlock.byteSize <= 0L
        }
        if (emptyPayload != null) {
            return refused(
                "unsupported.payload_slab_empty_payload",
                mapOf("packetId" to emptyPayload.packetId),
            )
        }

        val uniformMissing = request.payloadRequests.firstOrNull { payload ->
            payload.uniformBlock.bytes.size.toLong() != payload.uniformBlock.byteSize
        }
        if (uniformMissing != null) {
            return refused(
                "unsupported.payload_slab_uniform_missing",
                mapOf("packetId" to uniformMissing.packetId),
            )
        }

        val overBudgetPayload = request.payloadRequests.firstOrNull { payload ->
            payload.uniformBlock.byteSize > payload.uploadBudgetBytes
        }
        if (overBudgetPayload != null) {
            return refused(
                "unsupported.payload_slab_budget_exceeded",
                mapOf("packetId" to overBudgetPayload.packetId),
            )
        }

        val payloadsBySlotLabel = linkedMapOf<String, GPUPayloadMaterializationRequest>()
        val slabPayloads = buildList {
            request.payloadRequests.forEach { payload ->
                val slotLabel = payload.payloadSlabSlotLabel()
                val bytes = payload.uniformBlock.bytes.toUnsignedByteArray()
                payloadsBySlotLabel[slotLabel] = payload
                add(
                    GPUUniformSlabPayload(
                        slotLabel = slotLabel,
                        bytes = bytes,
                    ),
                )
            }
        }

        return when (
            val uniformPlan = GPUUniformSlabPlanner.plan(
                sourceLabel = request.sourceLabel,
                deviceGeneration = request.deviceGeneration,
                alignmentBytes = request.alignmentBytes,
                uploadBudgetBytes = request.uploadBudgetBytes,
                payloads = slabPayloads,
            )
        ) {
            is GPUUniformSlabPlanningResult.Refused -> {
                refused(
                    code = uniformPlan.diagnostic.code.toPayloadSlabCode(),
                    facts = uniformPlan.diagnostic.facts,
                )
            }

            is GPUUniformSlabPlanningResult.Accepted -> {
                val slotBindings = uniformPlan.plan.slots.map { slot ->
                    val payload = requireNotNull(payloadsBySlotLabel[slot.slotLabel]) {
                        "Uniform slab planner returned unknown slot ${slot.slotLabel}"
                    }
                    GPUPayloadSlabSlotBinding(
                        slotLabel = slot.slotLabel,
                        packetId = payload.packetId,
                        uniformSlotId = payload.uniformSlot.slotId.value,
                        resourceSlotId = payload.resourceSlot.slotId.value,
                        payloadFingerprint = payload.uniformBlock.fingerprint.value,
                        reflectedBindingLayoutHash = payload.reflectedBindingLayoutHash,
                        alignedOffset = slot.alignedOffset,
                        payloadBytes = slot.payloadBytes,
                    )
                }
                GPUPayloadSlabBatchPlanningResult.Accepted(
                    GPUPayloadSlabBatchPlan(
                        planHash = request.planHashFor(uniformPlan.plan, slotBindings),
                        sourceLabel = request.sourceLabel,
                        targetId = request.targetId,
                        frameId = request.frameId,
                        deviceGeneration = request.deviceGeneration,
                        uniformSlabPlan = uniformPlan.plan,
                        slotBindings = slotBindings,
                    ),
                )
            }
        }
    }

    private fun refused(
        code: String,
        facts: Map<String, String>,
    ): GPUPayloadSlabBatchPlanningResult.Refused =
        GPUPayloadSlabBatchPlanningResult.Refused(
            GPUPayloadSlabBatchDiagnostic(
                code = code,
                terminal = true,
                factEntries = facts,
            ),
        )

    private fun duplicateSlotDiagnostic(
        payloadRequests: List<GPUPayloadMaterializationRequest>,
    ): Map<String, String>? {
        val seenPacketIds = linkedSetOf<String>()
        payloadRequests.firstOrNull { payload -> !seenPacketIds.add(payload.packetId) }?.let { payload ->
            return mapOf("field" to "packetId", "packetId" to payload.packetId)
        }

        val seenUniformSlots = linkedSetOf<String>()
        payloadRequests.firstOrNull { payload -> !seenUniformSlots.add(payload.uniformSlot.slotId.value) }?.let { payload ->
            return mapOf("field" to "uniformSlotId", "slotId" to payload.uniformSlot.slotId.value)
        }

        val seenResourceSlots = linkedSetOf<String>()
        payloadRequests.firstOrNull { payload -> !seenResourceSlots.add(payload.resourceSlot.slotId.value) }?.let { payload ->
            return mapOf("field" to "resourceSlotId", "slotId" to payload.resourceSlot.slotId.value)
        }

        val seenSlotLabels = linkedSetOf<String>()
        payloadRequests.firstOrNull { payload -> !seenSlotLabels.add(payload.payloadSlabSlotLabel()) }?.let { payload ->
            return mapOf("field" to "slotLabel", "slotLabel" to payload.payloadSlabSlotLabel())
        }

        return null
    }
}

private fun GPUPayloadMaterializationRequest.payloadSlabSlotLabel(): String =
    "$packetId:${uniformSlot.slotId.value}:${resourceSlot.slotId.value}"

private fun List<Int>.toUnsignedByteArray(): ByteArray =
    ByteArray(size) { index ->
        val value = this[index]
        require(value in 0..255) { "Payload byte at index $index must be in 0..255" }
        value.toByte()
    }

private fun String.toPayloadSlabCode(): String =
    when (this) {
        "unsupported.uniform_slab_empty_payload" -> "unsupported.payload_slab_empty_payload"
        "unsupported.uniform_slab_budget_exceeded" -> "unsupported.payload_slab_budget_exceeded"
        "unsupported.uniform_slab_duplicate_slot_label" -> "unsupported.payload_slab_duplicate_slot"
        "unsupported.uniform_slab_dump_unsafe" -> "unsupported.payload_slab_dump_unsafe"
        else -> replace("unsupported.uniform_slab_", "unsupported.payload_slab_")
    }

private fun GPUPayloadSlabBatchRequest.planHashFor(
    uniformSlabPlan: GPUUniformSlabPlan,
    slotBindings: List<GPUPayloadSlabSlotBinding>,
): String =
    sha256Hex(
        buildString {
            append("payload-slab-plan")
            append("|target=").append(targetId)
            append("|frame=").append(frameId)
            append("|source=").append(sourceLabel)
            append("|deviceGeneration=").append(deviceGeneration)
            append("|uniformSlabPlan=").append(uniformSlabPlan.planHash)
            slotBindings.forEachIndexed { index, binding ->
                append("|slot[").append(index).append("].label=").append(binding.slotLabel)
                append("|slot[").append(index).append("].packet=").append(binding.packetId)
                append("|slot[").append(index).append("].uniform=").append(binding.uniformSlotId)
                append("|slot[").append(index).append("].resource=").append(binding.resourceSlotId)
                append("|slot[").append(index).append("].fingerprint=").append(binding.payloadFingerprint)
                append("|slot[").append(index).append("].layout=").append(binding.reflectedBindingLayoutHash)
                append("|slot[").append(index).append("].offset=").append(binding.alignedOffset)
                append("|slot[").append(index).append("].bytes=").append(binding.payloadBytes)
            }
        },
    )

private fun sha256Hex(input: String): String =
    sha256Hex(input.toByteArray(Charsets.UTF_8))

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun isDumpSafePayloadSlabValue(value: String): Boolean =
    value.isNotBlank() &&
        !RAW_HANDLE_PAYLOAD_SLAB_DUMP_PATTERN.containsMatchIn(value) &&
        '@' !in value

private fun requireDumpSafePayloadSlabValue(fieldName: String, value: String) {
    require(isDumpSafePayloadSlabValue(value)) {
        "$fieldName must not contain raw backend handle evidence"
    }
}

private fun Map<String, String>.dumpFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(",") { entry -> "${entry.key}=${entry.value}" }
    }

private val RAW_HANDLE_PAYLOAD_SLAB_DUMP_PATTERN =
    Regex("""(?i)(wgpu|externaltexturehandle|gpu[a-z0-9]*handle|0x)""")
