package org.graphiks.kanvas.gpu.renderer.resources

import java.util.Collections
import java.security.MessageDigest

/** CPU-owned bytes for one pass-local uniform payload that can be placed in a slab. */
class GPUUniformSlabPayload(
    val slotLabel: String,
    bytes: ByteArray,
) {
    private val bytesSnapshot: ByteArray = bytes.copyOf()

    val bytes: ByteArray
        get() = bytesSnapshot.copyOf()

    init {
        require(slotLabel.isNotBlank()) { "GPUUniformSlabPayload.slotLabel must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabPayload.slotLabel", slotLabel)
    }

    override fun equals(other: Any?): Boolean =
        other is GPUUniformSlabPayload &&
            slotLabel == other.slotLabel &&
            bytesSnapshot.contentEquals(other.bytesSnapshot)

    override fun hashCode(): Int =
        31 * slotLabel.hashCode() + bytesSnapshot.contentHashCode()

    override fun toString(): String =
        "GPUUniformSlabPayload(slotLabel=$slotLabel, bytes=${bytesSnapshot.size}b)"
}

/** One aligned payload range inside a backend-neutral uniform slab plan. */
data class GPUUniformSlabSlot(
    val slotLabel: String,
    val payloadHash: String,
    val payloadBytes: Long,
    val alignedOffset: Long,
    val allocatedBytes: Long,
) {
    init {
        require(slotLabel.isNotBlank()) { "GPUUniformSlabSlot.slotLabel must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabSlot.slotLabel", slotLabel)
        require(payloadHash.isNotBlank()) { "GPUUniformSlabSlot.payloadHash must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabSlot.payloadHash", payloadHash)
        require(payloadBytes > 0L) { "GPUUniformSlabSlot.payloadBytes must be positive" }
        require(alignedOffset >= 0L) { "GPUUniformSlabSlot.alignedOffset must be non-negative" }
        require(allocatedBytes >= payloadBytes) {
            "GPUUniformSlabSlot.allocatedBytes must cover payloadBytes"
        }
    }
}

/** Backend-neutral uniform slab layout accepted before runtime materialization. */
class GPUUniformSlabPlan(
    val planHash: String,
    val sourceLabel: String,
    val deviceGeneration: Long,
    val alignmentBytes: Long,
    val totalBytes: Long,
    val uploadBudgetBytes: Long,
    slots: List<GPUUniformSlabSlot>,
) {
    private val slotsSnapshot: List<GPUUniformSlabSlot> = Collections.unmodifiableList(slots.toList())

    val slots: List<GPUUniformSlabSlot>
        get() = slotsSnapshot

    init {
        require(planHash.isNotBlank()) { "GPUUniformSlabPlan.planHash must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.planHash", planHash)
        require(sourceLabel.isNotBlank()) { "GPUUniformSlabPlan.sourceLabel must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.sourceLabel", sourceLabel)
        require(deviceGeneration >= 0L) { "GPUUniformSlabPlan.deviceGeneration must be non-negative" }
        require(alignmentBytes > 0L) { "GPUUniformSlabPlan.alignmentBytes must be positive" }
        require(totalBytes >= 0L) { "GPUUniformSlabPlan.totalBytes must be non-negative" }
        require(uploadBudgetBytes >= 0L) { "GPUUniformSlabPlan.uploadBudgetBytes must be non-negative" }
        require(slotsSnapshot.isNotEmpty()) { "GPUUniformSlabPlan.slots must not be empty" }
        val seenSlotLabels = linkedSetOf<String>()
        slotsSnapshot.forEachIndexed { index, slot ->
            require(slot.alignedOffset % alignmentBytes == 0L) {
                "GPUUniformSlabPlan.slot.alignedOffset must be aligned"
            }
            require(slot.allocatedBytes % alignmentBytes == 0L) {
                "GPUUniformSlabPlan.slot.allocatedBytes must be aligned"
            }
            require(seenSlotLabels.add(slot.slotLabel)) {
                "GPUUniformSlabPlan.slotLabel must be unique"
            }
            requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.slotLabel", slot.slotLabel)
            requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.slot.payloadHash", slot.payloadHash)
            if (index > 0) {
                val previousSlot = slotsSnapshot[index - 1]
                require(slot.alignedOffset >= previousSlot.alignedOffset) {
                    "GPUUniformSlabPlan.slots must be ordered by alignedOffset"
                }
                require(slot.alignedOffset >= previousSlot.alignedOffset + previousSlot.allocatedBytes) {
                    "GPUUniformSlabPlan.slots must not overlap"
                }
            }
        }
        val coveredBytes = slotsSnapshot.maxOf { slot -> slot.alignedOffset + slot.allocatedBytes }
        require(totalBytes >= coveredBytes) {
            "GPUUniformSlabPlan.totalBytes must cover every slot"
        }
        require(totalBytes <= uploadBudgetBytes) {
            "GPUUniformSlabPlan.totalBytes must not exceed uploadBudgetBytes"
        }
        require(totalBytes % alignmentBytes == 0L) {
            "GPUUniformSlabPlan.totalBytes must be aligned"
        }
    }

    fun dumpLines(): List<String> {
        requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.sourceLabel", sourceLabel)
        slotsSnapshot.forEach { slot ->
            requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.slotLabel", slot.slotLabel)
        }
        return buildList {
            add(
                "uniform-slab.plan " +
                    "source=$sourceLabel " +
                    "deviceGeneration=$deviceGeneration " +
                    "alignment=$alignmentBytes " +
                    "totalBytes=$totalBytes " +
                    "uploadBudgetBytes=$uploadBudgetBytes " +
                    "slots=${slotsSnapshot.size} " +
                    "hash=$planHash",
            )
            slotsSnapshot.forEach { slot ->
                add(
                    "uniform-slab.slot " +
                        "source=$sourceLabel " +
                        "slot=${slot.slotLabel} " +
                        "offset=${slot.alignedOffset} " +
                        "payloadBytes=${slot.payloadBytes} " +
                        "allocatedBytes=${slot.allocatedBytes} " +
                        "payloadHash=${slot.payloadHash}",
                )
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is GPUUniformSlabPlan &&
            planHash == other.planHash &&
            sourceLabel == other.sourceLabel &&
            deviceGeneration == other.deviceGeneration &&
            alignmentBytes == other.alignmentBytes &&
            totalBytes == other.totalBytes &&
            uploadBudgetBytes == other.uploadBudgetBytes &&
            slotsSnapshot == other.slots

    override fun hashCode(): Int =
        listOf(
            planHash,
            sourceLabel,
            deviceGeneration,
            alignmentBytes,
            totalBytes,
            uploadBudgetBytes,
            slotsSnapshot,
        ).hashCode()

    override fun toString(): String =
        "GPUUniformSlabPlan(planHash=$planHash, sourceLabel=$sourceLabel, deviceGeneration=$deviceGeneration, " +
            "alignmentBytes=$alignmentBytes, totalBytes=$totalBytes, uploadBudgetBytes=$uploadBudgetBytes, " +
            "slots=${slotsSnapshot.size})"
}

/** Diagnostic for a refused uniform slab plan. */
class GPUUniformSlabDiagnostic(
    val code: String,
    val terminal: Boolean = true,
    factEntries: Map<String, String> = emptyMap(),
) {
    val facts: Map<String, String> = factEntries.toMap()

    init {
        require(code.isNotBlank()) { "GPUUniformSlabDiagnostic.code must not be blank" }
        requireDumpSafeUniformSlabValue("GPUUniformSlabDiagnostic.code", code)
        factEntries.forEach { (key, value) ->
            requireDumpSafeUniformSlabValue("GPUUniformSlabDiagnostic.facts key", key)
            requireDumpSafeUniformSlabValue("GPUUniformSlabDiagnostic.facts value", value)
        }
    }

    fun dumpLines(): List<String> =
        listOf(
            "uniform-slab.diagnostic " +
                "code=$code " +
                "terminal=$terminal " +
                "facts=${facts.dumpFacts()}",
        )
}

/** Planner outcome for backend-neutral slab layout. */
sealed class GPUUniformSlabPlanningResult {
    data class Accepted(val plan: GPUUniformSlabPlan) : GPUUniformSlabPlanningResult()

    data class Refused(val diagnostic: GPUUniformSlabDiagnostic) : GPUUniformSlabPlanningResult() {
        fun dumpLines(): List<String> =
            listOf(
                "uniform-slab.refused " +
                    "code=${diagnostic.code} " +
                    "terminal=${diagnostic.terminal} " +
                    "facts=${diagnostic.facts.dumpFacts()}",
            )
    }
}

/** Backend-neutral uniform slab planner. */
object GPUUniformSlabPlanner {
    fun plan(
        sourceLabel: String,
        deviceGeneration: Long,
        alignmentBytes: Long,
        uploadBudgetBytes: Long,
        payloads: List<GPUUniformSlabPayload>,
        maxBufferSize: Long = Long.MAX_VALUE,
        maxDynamicUniformBuffersPerPipelineLayout: Long = Long.MAX_VALUE,
    ): GPUUniformSlabPlanningResult {
        if (!isDumpSafeUniformSlabValue(sourceLabel)) {
            return refused(
                code = "unsupported.uniform_slab_dump_unsafe",
                facts = mapOf("field" to "sourceLabel"),
            )
        }
        if (alignmentBytes <= 0L) {
            return refused(
                code = "unsupported.uniform_slab_alignment_invalid",
                facts = mapOf("alignmentBytes" to alignmentBytes.toString()),
            )
        }
        if (deviceGeneration < 0L) {
            return refused(
                code = "unsupported.uniform_slab_stale_generation",
                facts = mapOf("deviceGeneration" to deviceGeneration.toString()),
            )
        }
        if (maxDynamicUniformBuffersPerPipelineLayout < 1L) {
            return refused(
                code = "unsupported.uniform_slab_dynamic_uniform_unavailable",
                facts = mapOf(
                    "maxDynamicUniformBuffersPerPipelineLayout" to
                        maxDynamicUniformBuffersPerPipelineLayout.toString(),
                ),
            )
        }
        if (maxBufferSize <= 0L) {
            return refused(
                code = "unsupported.uniform_slab_max_buffer_size_exceeded",
                facts = mapOf("maxBufferSize" to maxBufferSize.toString()),
            )
        }

        val unsafePayloadLabel = payloads.firstOrNull { payload -> !isDumpSafeUniformSlabValue(payload.slotLabel) }
        if (unsafePayloadLabel != null) {
            return refused(
                code = "unsupported.uniform_slab_dump_unsafe",
                facts = mapOf("field" to "slotLabel"),
            )
        }

        if (payloads.isEmpty() || payloads.any { payload -> payload.bytes.isEmpty() }) {
            return refused(
                code = "unsupported.uniform_slab_empty_payload",
                facts = mapOf("payloadCount" to payloads.size.toString()),
            )
        }
        val seenSlotLabels = linkedSetOf<String>()
        val duplicateSlotLabel = payloads.firstOrNull { payload -> !seenSlotLabels.add(payload.slotLabel) }?.slotLabel
        if (duplicateSlotLabel != null) {
            return refused(
                code = "unsupported.uniform_slab_duplicate_slot_label",
                facts = mapOf(
                    "payloadCount" to payloads.size.toString(),
                    "slotLabel" to duplicateSlotLabel,
                ),
            )
        }

        val slots = mutableListOf<GPUUniformSlabSlot>()
        try {
            var nextOffset = 0L
            payloads.forEach { payload ->
                val payloadBytesSnapshot = payload.bytes
                val payloadBytes = payloadBytesSnapshot.size.toLong()
                val payloadHash = sha256Hex(payloadBytesSnapshot)
                val alignedOffset = alignUpChecked(nextOffset, alignmentBytes)
                val allocatedBytes = alignUpChecked(payloadBytes, alignmentBytes)
                if (alignedOffset > UInt.MAX_VALUE.toLong()) {
                    return refused(
                        code = "unsupported.uniform_slab_dynamic_offset_uint_overflow",
                        facts = mapOf("alignedOffset" to alignedOffset.toString()),
                    )
                }
                Math.addExact(alignedOffset, payloadBytes)
                slots +=
                    GPUUniformSlabSlot(
                        slotLabel = payload.slotLabel,
                        payloadHash = payloadHash,
                        payloadBytes = payloadBytes,
                        alignedOffset = alignedOffset,
                        allocatedBytes = allocatedBytes,
                    )
                nextOffset = Math.addExact(alignedOffset, allocatedBytes)
            }
        } catch (_: ArithmeticException) {
            return refused(
                code = "unsupported.uniform_slab_size_overflow",
                facts = mapOf(
                    "alignmentBytes" to alignmentBytes.toString(),
                    "payloadCount" to payloads.size.toString(),
                ),
            )
        }
        val totalBytes = try {
            Math.addExact(slots.last().alignedOffset, slots.last().allocatedBytes)
        } catch (_: ArithmeticException) {
            return refused(
                code = "unsupported.uniform_slab_size_overflow",
                facts = mapOf("payloadCount" to payloads.size.toString()),
            )
        }
        if (totalBytes > uploadBudgetBytes) {
            return refused(
                code = "unsupported.uniform_slab_budget_exceeded",
                facts = mapOf(
                    "budgetBytes" to uploadBudgetBytes.toString(),
                    "requestedBytes" to totalBytes.toString(),
                ),
            )
        }
        if (totalBytes > maxBufferSize) {
            return refused(
                code = "unsupported.uniform_slab_max_buffer_size_exceeded",
                facts = mapOf(
                    "maxBufferSize" to maxBufferSize.toString(),
                    "requestedBytes" to totalBytes.toString(),
                ),
            )
        }
        if (slots.any { slot ->
                try {
                    Math.addExact(slot.alignedOffset, slot.payloadBytes) > totalBytes
                } catch (_: ArithmeticException) {
                    true
                }
            }
        ) {
            return refused(
                code = "unsupported.uniform_slab_slot_range_invalid",
                facts = mapOf("totalBytes" to totalBytes.toString()),
            )
        }

        val planHash = sha256Hex(
            buildString {
                append("uniform-slab-plan")
                append("|source=").append(sourceLabel)
                append("|deviceGeneration=").append(deviceGeneration)
                append("|alignmentBytes=").append(alignmentBytes)
                append("|uploadBudgetBytes=").append(uploadBudgetBytes)
                slots.forEachIndexed { index, slot ->
                    append("|slot[").append(index).append("].label=").append(slot.slotLabel)
                    append("|slot[").append(index).append("].hash=").append(slot.payloadHash)
                    append("|slot[").append(index).append("].payloadBytes=").append(slot.payloadBytes)
                    append("|slot[").append(index).append("].alignedOffset=").append(slot.alignedOffset)
                    append("|slot[").append(index).append("].allocatedBytes=").append(slot.allocatedBytes)
                }
            },
        )

        return GPUUniformSlabPlanningResult.Accepted(
            GPUUniformSlabPlan(
                planHash = planHash,
                sourceLabel = sourceLabel,
                deviceGeneration = deviceGeneration,
                alignmentBytes = alignmentBytes,
                totalBytes = totalBytes,
                uploadBudgetBytes = uploadBudgetBytes,
                slots = slots,
            ),
        )
    }

    private fun refused(code: String, facts: Map<String, String>): GPUUniformSlabPlanningResult.Refused =
        GPUUniformSlabPlanningResult.Refused(
            GPUUniformSlabDiagnostic(
                code = code,
                terminal = true,
                factEntries = facts,
            ),
        )
}

private fun alignUpChecked(value: Long, alignmentBytes: Long): Long {
    require(alignmentBytes > 0L) { "alignmentBytes must be positive" }
    if (value <= 0L) {
        return 0L
    }
    val remainder = value % alignmentBytes
    return if (remainder == 0L) value else Math.addExact(value, alignmentBytes - remainder)
}

private fun sha256Hex(input: String): String =
    sha256Hex(input.toByteArray(Charsets.UTF_8))

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun isDumpSafeUniformSlabValue(value: String): Boolean =
    value.isNotBlank() &&
        !RAW_HANDLE_DUMP_PATTERN.containsMatchIn(value) &&
        '@' !in value

private fun requireDumpSafeUniformSlabValue(fieldName: String, value: String) {
    require(isDumpSafeUniformSlabValue(value)) {
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

private val RAW_HANDLE_DUMP_PATTERN =
    Regex("""(?i)(wgpu|externaltexturehandle|gpu[a-z0-9]*handle|0x[0-9a-f]{6,})""")
