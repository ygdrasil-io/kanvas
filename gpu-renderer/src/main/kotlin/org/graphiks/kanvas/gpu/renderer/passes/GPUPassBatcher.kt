package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

enum class GPUPassBatchKind(val dumpLabel: String) {
    SolidFill("solid-fill"),
    SimpleGradient("simple-gradient"),
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

data class GPUPassBatchQueueGuard(
    val requiredRetainedRefs: List<String>,
    val retainedRefs: List<String>,
) {
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
    val fixedStateHash: String,
    val queueGuard: GPUPassBatchQueueGuard = GPUPassBatchQueueGuard(
        requiredRetainedRefs = emptyList(),
        retainedRefs = emptyList(),
    ),
) {
    init {
        require(fixedStateHash.isNotBlank()) {
            "GPUPassBatchEligibility.fixedStateHash must not be blank"
        }
    }
}

class GPUPassBatcherRequest(
    val packetStream: GPUDrawPacketStream,
    eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility>,
) {
    val eligibilityByPacketId: Map<GPUDrawPacketID, GPUPassBatchEligibility> =
        eligibilityByPacketId.toMap()
}

class GPUPassBatch(
    val batchId: String,
    packets: List<GPUDrawPacket>,
    val kind: GPUPassBatchKind,
    val targetStateHash: String,
    val fixedStateHash: String,
    val queueGuard: GPUPassBatchQueueGuard,
) {
    val packets: List<GPUDrawPacket> = packets.toList()
    val packetIds: List<GPUDrawPacketID>
        get() = packets.map { it.packetId }
    val packetCount: Int
        get() = packets.size
    val renderPipelineKeys: List<GPURenderPipelineKey> =
        packets.mapNotNull { it.renderPipelineKey }.distinct()
    val acceptedForBatching: Boolean
        get() = packetCount >= 2 && queueGuard.retained

    init {
        require(batchId.isNotBlank()) { "GPUPassBatch.batchId must not be blank" }
        require(packets.isNotEmpty()) { "GPUPassBatch.packets must not be empty" }
        require(targetStateHash.isNotBlank()) { "GPUPassBatch.targetStateHash must not be blank" }
        require(fixedStateHash.isNotBlank()) { "GPUPassBatch.fixedStateHash must not be blank" }
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
) {
    val batches: List<GPUPassBatch> = batches.toList()
    val cuts: List<GPUPassBatchCut> = cuts.toList()
    val diagnostics: List<GPUPassDiagnostic> = diagnostics.toList()
    val packetCount: Int
        get() = batches.sumOf { it.packetCount }
    val acceptedBatchCount: Int
        get() = batches.count { it.acceptedForBatching }

    init {
        require(streamId.isNotBlank()) { "GPUPassBatchPlan.streamId must not be blank" }
        require(passId.isNotBlank()) { "GPUPassBatchPlan.passId must not be blank" }
    }
}

class GPUPassBatcher {
    fun plan(request: GPUPassBatcherRequest): GPUPassBatchPlan {
        val stream = request.packetStream
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
                    fixedStateHash = eligibility.fixedStateHash,
                    queueGuard = mergeQueueGuards(packets, request.eligibilityByPacketId),
                )
            }
            currentPackets = mutableListOf()
            currentEligibility = null
            currentTarget = null
        }

        stream.packets.forEach { packet ->
            val eligibility = request.eligibilityByPacketId[packet.packetId]
            val singlePacketCut = packet.singlePacketCut(eligibility)
            if (singlePacketCut != null) {
                emitCurrent()
                cuts += singlePacketCut
                return@forEach
            }

            val acceptedEligibility = requireNotNull(eligibility)
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
            streamId = stream.streamId,
            passId = stream.passId,
            batches = batches,
            cuts = cuts,
            diagnostics = stream.diagnostics,
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
            nextEligibility.kind != currentEligibility.kind ||
                nextEligibility.fixedStateHash != currentEligibility.fixedStateHash ->
                GPUPassBatchCut(
                    beforePacketId = previousPacket.packetId,
                    afterPacketId = nextPacket.packetId,
                    reasonCode = GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED,
                    message = "fixed state ${currentEligibility.fixedStateHash} cannot batch with ${nextEligibility.fixedStateHash}",
                )
            !nextEligibility.queueGuard.retained ->
                GPUPassBatchCut(
                    beforePacketId = previousPacket.packetId,
                    afterPacketId = nextPacket.packetId,
                    reasonCode = GPUPassBatchReason.UNRETAINED_MATERIALIZED_RESOURCE,
                    message = "materialized resource is not retained by queue",
                )
            else -> null
        }

    private fun GPUDrawPacket.singlePacketCut(
        eligibility: GPUPassBatchEligibility?,
    ): GPUPassBatchCut? =
        when {
            role == GPUDrawPacketRole.Copy || role == GPUDrawPacketRole.Readback ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.COPY_OR_READBACK,
                    message = "packet ${packetId.value} role $role cuts simple pass batching",
                )
            role == GPUDrawPacketRole.Upload ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.UPLOAD_BARRIER,
                    message = "packet ${packetId.value} upload role cuts simple pass batching",
                )
            diagnostics.any { it.code.contains("destination-read") } ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.DESTINATION_READ,
                    message = "packet ${packetId.value} requires destination-read",
                )
            diagnostics.any { it.code.contains("save-layer") || it.code.contains("layer") } ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.SAVE_LAYER,
                    message = "packet ${packetId.value} requires layer isolation",
                )
            diagnostics.any { it.code.contains("filter-intermediate") || it.code.contains("filter") } ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.FILTER_INTERMEDIATE,
                    message = "packet ${packetId.value} requires filter intermediate",
                )
            diagnostics.any { it.code.contains("text-complex") || it.code.contains("glyph") } ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.TEXT_COMPLEX,
                    message = "packet ${packetId.value} requires text complex route",
                )
            renderPipelineKey == null ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.MISSING_PIPELINE_KEY,
                    message = "packet ${packetId.value} has no render pipeline key",
                )
            role != GPUDrawPacketRole.Shading ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED,
                    message = "packet ${packetId.value} role $role is not a simple shading packet",
                )
            eligibility == null ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.BLEND_OR_FIXED_STATE_CHANGED,
                    message = "packet ${packetId.value} has no simple-pass eligibility",
                )
            !eligibility.queueGuard.retained ->
                GPUPassBatchCut(
                    beforePacketId = null,
                    afterPacketId = packetId,
                    reasonCode = GPUPassBatchReason.UNRETAINED_MATERIALIZED_RESOURCE,
                    message = "materialized resource is not retained by queue",
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
        "passes.batch-plan stream=$streamId pass=$passId batches=${batches.size} " +
            "accepted=$acceptedBatchCount cuts=${cuts.size} packets=$packetCount " +
            "diagnostics=${diagnostics.dumpPassBatchCodes()}",
    ) +
        batches.flatMap { it.dumpLines() } +
        cuts.map { it.dumpLine() } +
        diagnostics.dumpPassBatchLines()

fun GPUPassBatch.dumpLines(): List<String> =
    listOf(
        "passes.batch id=$batchId kind=${kind.dumpLabel} target=$targetStateHash " +
            "packets=${packetIds.map { it.value }.joinToString(",")} " +
            "pipelines=${renderPipelineKeys.map { it.value }.ifEmpty { listOf("none") }.joinToString(",")} " +
            "queueRetained=${queueGuard.retained}",
        "passes.batch-queue-guard batch=$batchId retained=${queueGuard.retained} " +
            "required=${queueGuard.requiredRetainedRefs.ifEmpty { listOf("none") }.joinToString(",")} " +
            "retainedRefs=${queueGuard.retainedRefs.ifEmpty { listOf("none") }.joinToString(",")}",
    )

private fun GPUPassBatchCut.dumpLine(): String =
    "passes.batch-cut before=${beforePacketId?.value ?: "none"} after=${afterPacketId.value} " +
        "code=$reasonCode message=$message"

private fun List<GPUPassDiagnostic>.dumpPassBatchCodes(): String =
    if (isEmpty()) "none" else joinToString(",") { it.code }

private fun List<GPUPassDiagnostic>.dumpPassBatchLines(): List<String> =
    sortedWith(
        compareBy<GPUPassDiagnostic> { it.code }
            .thenBy { it.passId ?: "" }
            .thenBy { it.invocationId ?: "" }
            .thenBy { it.terminal.toString() },
    )
        .map { diagnostic ->
            "passes.batch-diagnostic code=${diagnostic.code} " +
                "pass=${diagnostic.passId ?: "none"} " +
                "invocation=${diagnostic.invocationId ?: "none"} " +
                "terminal=${diagnostic.terminal}"
        }

private fun String.isPassBatchDumpSafeToken(): Boolean =
    isNotBlank() &&
        matches(Regex("^[A-Za-z0-9._:-]+$")) &&
        !contains("@") &&
        !contains("0x", ignoreCase = true) &&
        !contains("wgpu", ignoreCase = true)
