package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

/** Typed identity for one provisional render-pass segment across recorded commands. */
@JvmInline
value class GPUProvisionalRenderSegmentKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUProvisionalRenderSegmentKey.value must not be blank" }
    }
}

enum class GPUPassBatchKind(val dumpLabel: String) {
    SolidFill("solid-fill"),
    SimpleGradient("simple-gradient"),
    Isolated("isolated"),
}

/** Whether pass analysis proved that a packet may join an adjacent compatible packet. */
enum class GPUPassBatchAdjacency {
    Compatible,
    Isolated,
}

object GPUPassBatchReason {
    const val TARGET_CHANGED = "unsupported.batch.target_changed"
    const val BLEND_OR_FIXED_STATE_CHANGED = "unsupported.batch.blend_or_fixed_state_changed"
    const val DESTINATION_READ = "unsupported.batch.destination_read"
    const val SAVE_LAYER = "unsupported.batch.save_layer"
    const val FILTER_INTERMEDIATE = "unsupported.batch.filter_intermediate"
    const val TEXT_COMPLEX = "unsupported.batch.text_complex"
    const val COPY_OR_READBACK = "unsupported.batch.copy_or_readback"
    const val UPLOAD_BARRIER = "unsupported.batch.upload_barrier"
    const val UNRETAINED_MATERIALIZED_RESOURCE = "unsupported.batch.unretained_materialized_resource"
    const val MISSING_PIPELINE_KEY = "invalid.batch.missing_pipeline_key"
    const val STALE_RESOURCE_GENERATION = "stale.batch.resource_generation"
}

class GPUPassBatchQueueGuard(
    requiredRetainedRefs: List<String>,
    retainedRefs: List<String>,
) {
    val requiredRetainedRefs: List<String> = immutableList(requiredRetainedRefs)
    val retainedRefs: List<String> = immutableList(retainedRefs)

    val retained: Boolean
        get() = retainedRefs.containsAll(requiredRetainedRefs)

    init {
        require(requiredRetainedRefs.none { it.isBlank() }) {
            "GPUPassBatchQueueGuard.requiredRetainedRefs must not contain blanks"
        }
        require(retainedRefs.none { it.isBlank() }) {
            "GPUPassBatchQueueGuard.retainedRefs must not contain blanks"
        }
        (requiredRetainedRefs + retainedRefs).forEach { label ->
            require(label.isPassBatchDumpSafeToken()) {
                "GPUPassBatchQueueGuard labels must be dump-safe: $label"
            }
        }
    }
}

data class GPUPassBatchEligibility(
    val kind: GPUPassBatchKind,
    val adjacency: GPUPassBatchAdjacency = GPUPassBatchAdjacency.Compatible,
    val queueGuard: GPUPassBatchQueueGuard = GPUPassBatchQueueGuard(
        requiredRetainedRefs = emptyList(),
        retainedRefs = emptyList(),
    ),
)

class GPUPassBatcherRequest(
    val segmentKey: GPUProvisionalRenderSegmentKey,
    packets: List<GPUDrawPacket>,
    eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility>,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
    val streamId: String = "segment.${segmentKey.value}",
    val passId: String = segmentKey.value,
) {
    val packets: List<GPUDrawPacket> = immutableList(packets)
    val eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility> =
        immutableMap(eligibilityByPacketId)
    val diagnostics: List<GPUPassDiagnostic> = immutableList(diagnostics)

    init {
        require(this.eligibilityByPacketId.keys == packets.map(GPUDrawPacket::packetId).toSet()) {
            "GPUPassBatcherRequest eligibility must cover every packet exactly"
        }
    }

    /** Compatibility bridge for legacy pass-local tests and Task 8 command-stream fixtures. */
    constructor(
        packetStream: GPUDrawPacketStream,
        eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility>,
    ) : this(
        segmentKey = GPUProvisionalRenderSegmentKey(packetStream.passId),
        packets = packetStream.packets,
        eligibilityByPacketId = eligibilityByPacketId,
        diagnostics = packetStream.diagnostics,
        streamId = packetStream.streamId,
        passId = packetStream.passId,
    )
}

class GPUPassBatch(
    val batchId: String,
    packets: List<GPUDrawPacket>,
    val kind: GPUPassBatchKind,
    val targetStateHash: String,
    val queueGuard: GPUPassBatchQueueGuard,
) {
    val packets: List<GPUDrawPacket> = immutableList(packets)
    val packetIds: List<GPUDrawPacketID>
        get() = packets.map { it.packetId }
    val packetCount: Int
        get() = packets.size
    val renderPipelineKeys: List<GPURenderPipelineKey> =
        packets.mapNotNull { it.renderPipelineKey }.distinct()
    val acceptedForBatching: Boolean
        get() = packetCount >= 2

    init {
        require(batchId.isNotBlank()) { "GPUPassBatch.batchId must not be blank" }
        require(packets.isNotEmpty()) { "GPUPassBatch.packets must not be empty" }
        require(targetStateHash.isNotBlank()) { "GPUPassBatch.targetStateHash must not be blank" }
    }
}

data class GPUPassBatchCut(
    val beforePacketId: GPUDrawPacketID?,
    val afterPacketId: GPUDrawPacketID,
    val reasonCode: String,
    val message: String,
) {
    init {
        require(reasonCode.isNotBlank()) { "GPUPassBatchCut.reasonCode must not be blank" }
        require(message.isNotBlank()) { "GPUPassBatchCut.message must not be blank" }
    }
}

class GPUPassBatchPlan(
    val streamId: String,
    val passId: String,
    batches: List<GPUPassBatch>,
    cuts: List<GPUPassBatchCut>,
    diagnostics: List<GPUPassDiagnostic> = emptyList(),
    inputPacketCount: Int,
) {
    val batches: List<GPUPassBatch> = immutableList(batches)
    val cuts: List<GPUPassBatchCut> = immutableList(cuts)
    val diagnostics: List<GPUPassDiagnostic> = immutableList(diagnostics)
    val packetCount: Int = inputPacketCount
    val acceptedBatchCount: Int
        get() = batches.count { it.acceptedForBatching }

    init {
        require(streamId.isNotBlank()) { "GPUPassBatchPlan.streamId must not be blank" }
        require(passId.isNotBlank()) { "GPUPassBatchPlan.passId must not be blank" }
        require(inputPacketCount >= 0) { "GPUPassBatchPlan.inputPacketCount must be non-negative" }
    }
}

class GPUPassBatcher {
    fun plan(request: GPUPassBatcherRequest): GPUPassBatchPlan {
        val batches = mutableListOf<GPUPassBatch>()
        val cuts = mutableListOf<GPUPassBatchCut>()
        var currentPackets = mutableListOf<GPUDrawPacket>()
        var currentEligibility: GPUPassBatchEligibility? = null
        var currentTarget: String? = null
        var batchOrdinal = 1

        fun emitCurrent() {
            val packets = currentPackets.toList()
            val eligibility = currentEligibility
            val target = currentTarget
            if (packets.isNotEmpty() && eligibility != null && target != null) {
                batches += GPUPassBatch(
                    batchId = "batch-${batchOrdinal++}",
                    packets = packets,
                    kind = eligibility.kind,
                    targetStateHash = target,
                    queueGuard = mergeQueueGuards(packets, request.eligibilityByPacketId),
                )
            }
            currentPackets = mutableListOf()
            currentEligibility = null
            currentTarget = null
        }

        request.packets.forEach { packet ->
            val acceptedEligibility = request.eligibilityByPacketId.getValue(packet.packetId)
            if (currentPackets.isEmpty()) {
                currentPackets += packet
                currentEligibility = acceptedEligibility
                currentTarget = packet.targetStateHash
                return@forEach
            }

            val previousPacket = currentPackets.last()
            val cut = compatibilityCut(
                previousPacket = previousPacket,
                nextPacket = packet,
                currentEligibility = requireNotNull(currentEligibility),
                nextEligibility = acceptedEligibility,
                currentTarget = requireNotNull(currentTarget),
            )
            if (cut == null) {
                currentPackets += packet
            } else {
                emitCurrent()
                cuts += cut
                currentPackets += packet
                currentEligibility = acceptedEligibility
                currentTarget = packet.targetStateHash
            }
        }

        emitCurrent()

        return GPUPassBatchPlan(
            streamId = request.streamId,
            passId = request.passId,
            batches = batches,
            cuts = cuts,
            diagnostics = request.diagnostics,
            inputPacketCount = request.packets.size,
        )
    }

    private fun compatibilityCut(
        previousPacket: GPUDrawPacket,
        nextPacket: GPUDrawPacket,
        currentEligibility: GPUPassBatchEligibility,
        nextEligibility: GPUPassBatchEligibility,
        currentTarget: String,
    ): GPUPassBatchCut? =
        when {
            nextPacket.targetStateHash != currentTarget ->
                GPUPassBatchCut(
                    beforePacketId = previousPacket.packetId,
                    afterPacketId = nextPacket.packetId,
                    reasonCode = GPUPassBatchReason.TARGET_CHANGED,
                    message = "target $currentTarget cannot batch with ${nextPacket.targetStateHash}",
                )
            currentEligibility.adjacency != GPUPassBatchAdjacency.Compatible ||
                nextEligibility.adjacency != GPUPassBatchAdjacency.Compatible ||
                nextEligibility.kind != currentEligibility.kind ||
                nextPacket.role != previousPacket.role ||
                nextPacket.blendPlan != previousPacket.blendPlan ||
                nextPacket.renderStepId != previousPacket.renderStepId ||
                nextPacket.renderStepVersion != previousPacket.renderStepVersion ||
                nextPacket.renderPipelineKey != previousPacket.renderPipelineKey ||
                nextPacket.computePipelineKey != previousPacket.computePipelineKey ||
                nextPacket.bindingLayoutHash != previousPacket.bindingLayoutHash ||
                nextPacket.resourceGeneration != previousPacket.resourceGeneration ->
                GPUPassBatchCut(
                    beforePacketId = previousPacket.packetId,
                    afterPacketId = nextPacket.packetId,
                    reasonCode = GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED,
                    message = "typed packet state or batch eligibility changed",
                )
            else -> null
        }

    private fun mergeQueueGuards(
        packets: List<GPUDrawPacket>,
        eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility>,
    ): GPUPassBatchQueueGuard {
        val required = packets.flatMap { packet ->
            eligibilityByPacketId.getValue(packet.packetId).queueGuard.requiredRetainedRefs
        }.distinct()
        val retained = packets.flatMap { packet ->
            eligibilityByPacketId.getValue(packet.packetId).queueGuard.retainedRefs
        }.distinct()
        return GPUPassBatchQueueGuard(requiredRetainedRefs = required, retainedRefs = retained)
    }
}

fun GPUPassBatchPlan.dumpLines(): List<String> =
    listOf(
        "passes.batch-plan stream=${streamId.toPassBatchDumpToken()} pass=${passId.toPassBatchDumpToken()} batches=${batches.size} " +
            "accepted=$acceptedBatchCount cuts=${cuts.size} packets=$packetCount " +
            "diagnostics=${diagnostics.dumpPassBatchCodes()}",
    ) +
        batches.flatMap { it.dumpLines() } +
        cuts.map { it.dumpLine() } +
        diagnostics.dumpPassBatchLines()

fun GPUPassBatch.dumpLines(): List<String> =
    listOf(
        "passes.batch id=${batchId.toPassBatchDumpToken()} kind=${kind.dumpLabel} target=${targetStateHash.toPassBatchDumpToken()} " +
            "packets=${packetIds.map { it.value.toPassBatchDumpToken() }.joinToString(",")} " +
            "pipelines=${renderPipelineKeys.map { it.value.toPassBatchDumpToken() }.ifEmpty { listOf("none") }.joinToString(",")} " +
            "queueRetained=${queueGuard.retained}",
        "passes.batch-queue-guard batch=${batchId.toPassBatchDumpToken()} retained=${queueGuard.retained} " +
            "required=${queueGuard.requiredRetainedRefs.ifEmpty { listOf("none") }.map { it.toPassBatchDumpToken() }.joinToString(",")} " +
            "retainedRefs=${queueGuard.retainedRefs.ifEmpty { listOf("none") }.map { it.toPassBatchDumpToken() }.joinToString(",")}",
    )

private fun GPUPassBatchCut.dumpLine(): String =
    "passes.batch-cut before=${beforePacketId?.value?.toPassBatchDumpToken() ?: "none"} after=${afterPacketId.value.toPassBatchDumpToken()} " +
        "code=${reasonCode.toPassBatchDumpToken()} message=${message.toPassBatchDumpMessage()}"

private fun List<GPUPassDiagnostic>.dumpPassBatchCodes(): String =
    if (isEmpty()) "none" else joinToString(",") { it.code.toPassBatchDumpToken() }

private fun List<GPUPassDiagnostic>.dumpPassBatchLines(): List<String> =
    sortedWith(
        compareBy<GPUPassDiagnostic> { it.code }
            .thenBy { it.passId ?: "" }
            .thenBy { it.invocationId ?: "" }
            .thenBy { it.terminal.toString() },
    )
        .map { diagnostic ->
            "passes.batch-diagnostic code=${diagnostic.code.toPassBatchDumpToken()} " +
                "pass=${diagnostic.passId?.toPassBatchDumpToken() ?: "none"} " +
                "invocation=${diagnostic.invocationId?.toPassBatchDumpToken() ?: "none"} " +
                "terminal=${diagnostic.terminal}"
        }

private fun String.isPassBatchDumpSafeToken(): Boolean =
    isNotBlank() &&
        matches(Regex("^[A-Za-z0-9._:-]+$")) &&
        !contains("@") &&
        !contains("0x", ignoreCase = true) &&
        !contains("wgpu", ignoreCase = true)

private fun String.toPassBatchDumpToken(): String =
    if (isPassBatchDumpSafeToken()) {
        this
    } else {
        "sanitized-${stablePassBatchDumpFingerprint()}"
    }

private fun String.toPassBatchDumpMessage(): String =
    trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { token -> token.toPassBatchDumpToken() }

private fun String.stablePassBatchDumpFingerprint(): Long =
    fold(17L) { acc, char ->
        ((acc * 131L) + char.code.toLong()).mod(1_000_000_007L)
    }
