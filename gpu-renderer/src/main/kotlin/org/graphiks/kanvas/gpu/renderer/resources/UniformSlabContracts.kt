package org.graphiks.kanvas.gpu.renderer.resources

import java.security.MessageDigest

/** CPU-owned bytes for one pass-local uniform payload that can be placed in a slab. */
data class GPUUniformSlabPayload(
    val slotLabel: String,
    val bytes: ByteArray,
) {
    init {
        require(slotLabel.isNotBlank()) { "GPUUniformSlabPayload.slotLabel must not be blank" }
    }
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
        require(payloadHash.isNotBlank()) { "GPUUniformSlabSlot.payloadHash must not be blank" }
        require(payloadBytes > 0L) { "GPUUniformSlabSlot.payloadBytes must be positive" }
        require(alignedOffset >= 0L) { "GPUUniformSlabSlot.alignedOffset must be non-negative" }
        require(allocatedBytes >= payloadBytes) {
            "GPUUniformSlabSlot.allocatedBytes must cover payloadBytes"
        }
    }
}

/** Backend-neutral uniform slab layout accepted before runtime materialization. */
data class GPUUniformSlabPlan(
    val planHash: String,
    val sourceLabel: String,
    val deviceGeneration: Long,
    val alignmentBytes: Long,
    val totalBytes: Long,
    val uploadBudgetBytes: Long,
    val slots: List<GPUUniformSlabSlot>,
) {
    init {
        require(planHash.isNotBlank()) { "GPUUniformSlabPlan.planHash must not be blank" }
        require(sourceLabel.isNotBlank()) { "GPUUniformSlabPlan.sourceLabel must not be blank" }
        require(deviceGeneration >= 0L) { "GPUUniformSlabPlan.deviceGeneration must be non-negative" }
        require(alignmentBytes > 0L) { "GPUUniformSlabPlan.alignmentBytes must be positive" }
        require(totalBytes >= 0L) { "GPUUniformSlabPlan.totalBytes must be non-negative" }
        require(uploadBudgetBytes >= 0L) { "GPUUniformSlabPlan.uploadBudgetBytes must be non-negative" }
        require(slots.isNotEmpty()) { "GPUUniformSlabPlan.slots must not be empty" }
        require(slots.none { slot -> !isDumpSafeUniformSlabValue(slot.slotLabel) }) {
            "GPUUniformSlabPlan.slots must be dump-safe"
        }
        require(totalBytes >= slots.maxOf { slot -> slot.alignedOffset + slot.allocatedBytes }) {
            "GPUUniformSlabPlan.totalBytes must cover every slot"
        }
    }

    fun dumpLines(): List<String> {
        requireDumpSafeUniformSlabValue("GPUUniformSlabPlan.sourceLabel", sourceLabel)
        slots.forEach { slot ->
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
                    "slots=${slots.size} " +
                    "hash=$planHash",
            )
            slots.forEach { slot ->
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
}

/** Diagnostic for a refused uniform slab plan. */
data class GPUUniformSlabDiagnostic(
    val code: String,
    val terminal: Boolean = true,
    val facts: Map<String, String> = emptyMap(),
) {
    init {
        require(code.isNotBlank()) { "GPUUniformSlabDiagnostic.code must not be blank" }
        require(facts.keys.none { key -> key.isBlank() }) {
            "GPUUniformSlabDiagnostic.facts must not contain blank keys"
        }
        require(facts.values.none { value -> value.isBlank() }) {
            "GPUUniformSlabDiagnostic.facts must not contain blank values"
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

        val slots = buildList {
            var nextOffset = 0L
            payloads.forEach { payload ->
                val payloadBytes = payload.bytes.size.toLong()
                val payloadHash = sha256Hex(payload.bytes)
                val alignedOffset = alignUp(nextOffset, alignmentBytes)
                val allocatedBytes = alignUp(payloadBytes, alignmentBytes)
                add(
                    GPUUniformSlabSlot(
                        slotLabel = payload.slotLabel,
                        payloadHash = payloadHash,
                        payloadBytes = payloadBytes,
                        alignedOffset = alignedOffset,
                        allocatedBytes = allocatedBytes,
                    ),
                )
                nextOffset = alignedOffset + allocatedBytes
            }
        }
        val totalBytes = slots.last().alignedOffset + slots.last().allocatedBytes
        if (totalBytes > uploadBudgetBytes) {
            return refused(
                code = "unsupported.uniform_slab_budget_exceeded",
                facts = mapOf(
                    "budgetBytes" to uploadBudgetBytes.toString(),
                    "requestedBytes" to totalBytes.toString(),
                ),
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
                facts = facts,
            ),
        )
}

private fun alignUp(value: Long, alignmentBytes: Long): Long {
    require(alignmentBytes > 0L) { "alignmentBytes must be positive" }
    if (value <= 0L) {
        return 0L
    }
    val remainder = value % alignmentBytes
    return if (remainder == 0L) value else value + (alignmentBytes - remainder)
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
