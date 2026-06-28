package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

/** Per-instance vertex buffer descriptor for instanced draw batches. */
data class GPUInstancedVertexBuffer(
    val bufferLabel: String,
    val divisor: Int,
) {
    init {
        require(bufferLabel.isNotBlank()) { "GPUInstancedVertexBuffer.bufferLabel must not be blank" }
        require(divisor > 0) { "GPUInstancedVertexBuffer.divisor must be positive" }
    }
}

/** Delivers instance-varying uniforms in a single buffer with a configurable byte stride. */
data class GPUInstancedUniformStrategy(
    val bufferLabel: String,
    val strideBytes: Int,
) {
    /** Uniform byte stride between consecutive instances. */
    val uniformByteStride: Int get() = strideBytes

    init {
        require(bufferLabel.isNotBlank()) { "GPUInstancedUniformStrategy.bufferLabel must not be blank" }
        require(strideBytes > 0) { "GPUInstancedUniformStrategy.strideBytes must be positive" }
    }
}

/** Delivers per-instance vertex data through instanced vertex buffers with divisor = 1. */
class GPUInstancedVertexStrategy(
    vertexBuffers: List<GPUInstancedVertexBuffer>,
) {
    /** Vertex buffers copied in declaration order. */
    val vertexBuffers: List<GPUInstancedVertexBuffer> = vertexBuffers.toList()

    /** Buffer summaries for dump evidence. */
    val bufferSummaries: List<String>
        get() = if (vertexBuffers.isEmpty()) emptyList()
        else vertexBuffers.map { buffer -> "${buffer.bufferLabel}:div${buffer.divisor}" }
}

/** Issues a single instanced draw call with instanceCount equal to the group size. */
data class GPUInstancedDrawCommand(
    val indexCount: Int,
    val instanceCount: Int,
    val uniformStrategy: GPUInstancedUniformStrategy?,
    val vertexStrategy: GPUInstancedVertexStrategy?,
) {
    init {
        require(indexCount >= 0) { "GPUInstancedDrawCommand.indexCount must be non-negative" }
        require(instanceCount > 0) { "GPUInstancedDrawCommand.instanceCount must be positive" }
    }
}

/**
 * Groups N compatible packets that share the same render step identifier, render pipeline key,
 * and bind group layout key for a single instanced draw call.
 */
class GPUInstancedPacketGroup(
    packets: List<GPUDrawPacket>,
    val renderStepId: GPURenderStepID,
    val renderPipelineKey: GPURenderPipelineKey,
    val bindingLayoutKey: String,
) {
    /** Packets copied in group order. */
    val packets: List<GPUDrawPacket> = packets.toList()

    /** Packet identifiers in group order. */
    val packetIds: List<GPUDrawPacketID>
        get() = packets.map { packet -> packet.packetId }

    /** Original draw-command identifiers in group order. */
    val commandIds: List<Int>
        get() = packets.map { packet -> packet.commandIdValue }

    /** Sort keys in group order. */
    val sortKeys: List<Long>
        get() = packets.map { packet -> packet.sortKey }

    /** Number of packets in this group. */
    val packetCount: Int
        get() = packets.size

    init {
        require(packets.isNotEmpty()) { "GPUInstancedPacketGroup.packets must not be empty" }
        require(bindingLayoutKey.isNotBlank()) { "GPUInstancedPacketGroup.bindingLayoutKey must not be blank" }
        for (packet in packets) {
            require(packet.renderStepId == renderStepId) {
                "GPUInstancedPacketGroup packet ${packet.packetId.value} renderStepId ${packet.renderStepId.value} does not match group renderStepId ${renderStepId.value}"
            }
            require(packet.renderPipelineKey == renderPipelineKey) {
                "GPUInstancedPacketGroup packet ${packet.packetId.value} renderPipelineKey ${packet.renderPipelineKey?.value ?: "none"} does not match group renderPipelineKey ${renderPipelineKey.value}"
            }
            require(packet.bindingLayoutHash == bindingLayoutKey) {
                "GPUInstancedPacketGroup packet ${packet.packetId.value} bindingLayoutKey ${packet.bindingLayoutHash} does not match group bindingLayoutKey $bindingLayoutKey"
            }
        }
    }
}

/** Result of instanced draw batching for a stream of packets. */
sealed interface GpuInstancedBatchResult {
    /** Packet group suitable for a single instanced draw call. */
    data class Grouped(val group: GPUInstancedPacketGroup) : GpuInstancedBatchResult
}

/**
 * Groups consecutive compatible packets from a stream into instanced draw batches.
 *
 * Packets are grouped if they share the same [GPURenderStepID], [GPURenderPipelineKey], and
 * binding layout key. When any of these axes changes across consecutive packets, a new group
 * is started.
 */
fun batchForInstancedDraw(packets: List<GPUDrawPacket>): List<GpuInstancedBatchResult> {
    if (packets.isEmpty()) return emptyList()

    val results = mutableListOf<GpuInstancedBatchResult>()
    var currentGroup = mutableListOf<GPUDrawPacket>()
    var currentStepId: GPURenderStepID? = null
    var currentPipelineKey: GPURenderPipelineKey? = null
    var currentLayoutKey: String? = null

    for (packet in packets) {
        val renderPipelineKey = requireNotNull(packet.renderPipelineKey) {
            "Packet ${packet.packetId.value} missing renderPipelineKey for instanced batching"
        }
        val canGroup = currentStepId == null ||
            (packet.renderStepId == currentStepId &&
                renderPipelineKey == currentPipelineKey &&
                packet.bindingLayoutHash == currentLayoutKey)

        if (canGroup) {
            currentGroup.add(packet)
            if (currentStepId == null) {
                currentStepId = packet.renderStepId
                currentPipelineKey = renderPipelineKey
                currentLayoutKey = packet.bindingLayoutHash
            }
        } else {
            results.add(
                GpuInstancedBatchResult.Grouped(
                    GPUInstancedPacketGroup(
                        packets = currentGroup.toList(),
                        renderStepId = requireNotNull(currentStepId),
                        renderPipelineKey = requireNotNull(currentPipelineKey),
                        bindingLayoutKey = requireNotNull(currentLayoutKey),
                    ),
                ),
            )
            currentGroup = mutableListOf(packet)
            currentStepId = packet.renderStepId
            currentPipelineKey = renderPipelineKey
            currentLayoutKey = packet.bindingLayoutHash
        }
    }

    if (currentGroup.isNotEmpty()) {
        results.add(
            GpuInstancedBatchResult.Grouped(
                GPUInstancedPacketGroup(
                    packets = currentGroup.toList(),
                    renderStepId = requireNotNull(currentStepId),
                    renderPipelineKey = requireNotNull(currentPipelineKey),
                    bindingLayoutKey = requireNotNull(currentLayoutKey),
                ),
            ),
        )
    }

    return results
}

/** Emits deterministic instanced-draw command evidence lines. */
fun GPUInstancedDrawCommand.dumpLines(): List<String> =
    listOf(
        "instanced.draw indexCount=$indexCount instanceCount=$instanceCount " +
            "uniform=${uniformStrategy?.dumpLabel() ?: NONE_DUMP_VALUE} " +
            "vertex=${vertexStrategy?.dumpLabel() ?: NONE_DUMP_VALUE}",
    )

/** Emits deterministic instanced uniform strategy evidence lines. */
fun GPUInstancedUniformStrategy.dumpLines(): List<String> =
    listOf(
        "instanced.uniform buffer=$bufferLabel stride=$strideBytes",
    )

/** Emits deterministic instanced vertex strategy evidence lines. */
fun GPUInstancedVertexStrategy.dumpLines(): List<String> =
    listOf(
        "instanced.vertex buffers=${bufferSummaries.ifEmpty { listOf(NONE_DUMP_VALUE) }.joinToString(",")}",
    )

/** Emits deterministic instanced packet group evidence lines. */
fun GPUInstancedPacketGroup.dumpLines(): List<String> =
    listOf(
        "passes.instanced-group step=${renderStepId.value} pipeline=${renderPipelineKey.value} " +
            "layout=$bindingLayoutKey size=$packetCount " +
            "commands=${commandIds.map { commandId -> commandId.toString() }.joinToString(",")} " +
            "sortKeys=${sortKeys.map { sortKey -> sortKey.toString() }.joinToString(",")}",
    )

/** Emits deterministic batch result evidence lines. */
fun GpuInstancedBatchResult.dumpLines(): List<String> =
    when (this) {
        is GpuInstancedBatchResult.Grouped ->
            listOf(
                "passes.instanced-batch group size=${group.packetCount} " +
                    "step=${group.renderStepId.value} " +
                    "pipeline=${group.renderPipelineKey.value} " +
                    "layout=${group.bindingLayoutKey} " +
                    "packets=${group.packetIds.map { packetId -> packetId.value }.joinToString(",")}",
            ) + group.dumpLines()
    }

private const val NONE_DUMP_VALUE = "none"

private fun GPUInstancedUniformStrategy.dumpLabel(): String =
    "buffer:$bufferLabel stride=$strideBytes"

private fun GPUInstancedVertexStrategy.dumpLabel(): String =
    "buffers:${bufferSummaries.ifEmpty { listOf(NONE_DUMP_VALUE) }.joinToString(",")}"
