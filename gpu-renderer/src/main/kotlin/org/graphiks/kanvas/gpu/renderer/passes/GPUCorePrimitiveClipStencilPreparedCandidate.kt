package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.collections.immutableList

/**
 * Immutable recording-time candidate for one bounded clip-stencil route.
 *
 * It contains no resource generation or backend handle. Pure preflight must revalidate every fact
 * before it may issue the final frame/scope route seal.
 */
internal class GPUCorePrimitiveClipStencilPreparedCandidate(
    val contentKey: String,
    val planCanonicalIdentity: String,
    val producerPacketId: GPUDrawPacketID,
    val producerCommandId: Int,
    producerNdcVertices: List<Float>,
    producerContourStarts: List<Int>,
    producerFanVertices: List<Float>,
    producerFanIndices: List<Int>,
    val producerStructuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
    consumers: List<Consumer>,
    val attachmentLogicalReference: String,
    val attachmentWidth: Int,
    val attachmentHeight: Int,
    val attachmentSampleCount: Int,
) {
    val producerNdcVertices: List<Float> = immutableList(producerNdcVertices)
    val producerContourStarts: List<Int> = immutableList(producerContourStarts)
    val producerFanVertices: List<Float> = immutableList(producerFanVertices)
    val producerFanIndices: List<Int> = immutableList(producerFanIndices)
    val consumers: List<Consumer> = immutableList(consumers)

    data class Consumer(
        val packetId: GPUDrawPacketID,
        val commandId: Int,
        val sourceOrder: Int,
        val structuralKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        val dependencyFromPreviousConsumerToken: String?,
    )

    init {
        require(contentKey.isNotBlank() && planCanonicalIdentity.isNotBlank()) {
            "Clip-stencil candidate requires stable plan identities"
        }
        require(producerCommandId >= 0 && consumers.isNotEmpty()) {
            "Clip-stencil candidate requires one producer and at least one consumer"
        }
        require(producerNdcVertices.size >= 6 && producerNdcVertices.size % 2 == 0 &&
            producerNdcVertices.all(Float::isFinite)
        ) { "Clip-stencil candidate NDC vertices must be finite x/y pairs" }
        require(producerContourStarts.isNotEmpty() && producerContourStarts.first() == 0) {
            "Clip-stencil candidate contour starts must begin at zero"
        }
        require(producerFanVertices.size >= 6 && producerFanVertices.size % 6 == 0 &&
            producerFanVertices.all(Float::isFinite) && producerFanIndices.size % 3 == 0 &&
            producerFanIndices.all { it in 0 until producerFanVertices.size / 2 }
        ) { "Clip-stencil candidate fan must be exact finite indexed triangle geometry" }
        require(consumers.map(Consumer::packetId).distinct().size == consumers.size &&
            consumers.none { it.packetId == producerPacketId } &&
            consumers.map(Consumer::commandId).distinct().size == consumers.size &&
            consumers.all { it.sourceOrder >= 0 } &&
            consumers.zipWithNext().all { (left, right) -> left.sourceOrder < right.sourceOrder } &&
            consumers.first().dependencyFromPreviousConsumerToken == null &&
            consumers.drop(1).all { consumer ->
                consumer.dependencyFromPreviousConsumerToken ==
                    corePrimitiveClipStencilConsumerDependencyToken(consumer.commandId)
            }
        ) { "Clip-stencil candidate consumers must retain exact unique source order" }
        require(attachmentLogicalReference.isNotBlank() && attachmentWidth > 0 &&
            attachmentHeight > 0 && attachmentSampleCount == 1
        ) { "Clip-stencil candidate requires one logical single-sample attachment" }
    }
}

internal fun corePrimitiveClipStencilConsumerDependencyToken(commandId: Int): String {
    require(commandId >= 0) { "Clip-stencil consumer dependency requires a command identity" }
    return "prepared-core-primitive.clip-stencil.consumer.$commandId"
}
