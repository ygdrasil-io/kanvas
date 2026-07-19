package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.geometry.corePrimitiveClipStencilEdgeFan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilAttachmentAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilConsumerInput
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilNativeRouteRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilPreparedCandidate
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveClipStencilProducerGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilNativePathOrNull
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveClipStencilNdcVertices
import org.graphiks.kanvas.gpu.renderer.passes.sealGPUCorePrimitiveClipStencilNativeRoute
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload

internal sealed interface GPUCorePrimitiveClipStencilPreparedCandidateValidation {
    class Accepted(
        val route: GPUCorePrimitiveClipStencilNativeRoute.Accepted,
        producerFanVertices: List<Float>,
        producerFanIndices: List<Int>,
    ) : GPUCorePrimitiveClipStencilPreparedCandidateValidation {
        val producerFanVertices = producerFanVertices.toList()
        val producerFanIndices = producerFanIndices.toList()
    }

    data class Refused(val message: String) :
        GPUCorePrimitiveClipStencilPreparedCandidateValidation
}

/** Pure bridge: semantic/path/fan validation stays outside the execution package. */
internal fun validateCorePrimitiveClipStencilPreparedCandidate(
    candidate: GPUCorePrimitiveClipStencilPreparedCandidate,
    producerPacket: GPUDrawPacket,
    consumerPackets: List<GPUDrawPacket>,
    attachment: GPUCorePrimitiveClipStencilAttachmentAuthority,
): GPUCorePrimitiveClipStencilPreparedCandidateValidation {
    fun refuse(message: String) =
        GPUCorePrimitiveClipStencilPreparedCandidateValidation.Refused(message)

    val plan = producerPacket.clipExecutionPlan as? GPUClipExecutionPlan.StencilCoverage
        ?: return refuse("Prepared clip-stencil producer lost its typed stencil plan.")
    val path = plan.corePrimitiveClipStencilNativePathOrNull()
        ?: return refuse("Prepared clip-stencil producer no longer has exact native path state.")
    if (candidate.contentKey != plan.contentKey ||
        candidate.planCanonicalIdentity != plan.canonicalIdentity() ||
        (listOf(producerPacket) + consumerPackets).any {
            it.clipExecutionPlan?.canonicalIdentity() != candidate.planCanonicalIdentity
        }
    ) return refuse("Prepared clip-stencil content or canonical plan identity was substituted.")

    val recomputedNdc = corePrimitiveClipStencilNdcVertices(
        path.vertices,
        attachment.width,
        attachment.height,
    ) ?: return refuse("Prepared clip-stencil path could not be converted to finite NDC.")
    val recomputedFan = try {
        corePrimitiveClipStencilEdgeFan(recomputedNdc, path.contourStarts)
    } catch (_: IllegalArgumentException) {
        return refuse("Prepared clip-stencil edge fan could not be recomputed.")
    }
    if (candidate.producerNdcVertices != recomputedNdc ||
        candidate.producerContourStarts != path.contourStarts ||
        candidate.producerFanVertices != recomputedFan.vertices.toList() ||
        candidate.producerFanIndices != recomputedFan.indices.toList()
    ) return refuse("Prepared clip-stencil path or fan evidence was substituted.")

    if (consumerPackets.size != candidate.consumers.size) {
        return refuse("Prepared clip-stencil consumer count was substituted.")
    }
    val consumerInputs = consumerPackets.zip(candidate.consumers).mapIndexed {
            index, (packet, candidateConsumer) ->
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return refuse("Prepared clip-stencil consumer semantic payload is missing.")
        val preparedAuthority = packet.corePrimitivePreparedAuthority
            ?: return refuse("Prepared clip-stencil consumer packet authority is missing.")
        if (preparedAuthority.structuralPipelineKey != candidateConsumer.structuralKey ||
            packet.renderPipelineKey != candidateConsumer.structuralKey
                .stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY) ||
            semantic.scissorBounds != plan.consumer.scissor
        ) return refuse("Prepared clip-stencil consumer key or scissor was substituted.")
        val blendPlan = packet.blendPlan
            ?: return refuse("Prepared clip-stencil consumer blend authority is missing.")
        GPUCorePrimitiveClipStencilConsumerInput(
            commandId = packet.commandIdValue,
            sourceOrder = packet.originalPaintOrder,
            geometry = semantic.geometry,
            coverageMode = semantic.coverageMode,
            blendPlan = blendPlan,
            inverseFill = path.inverseFill,
            stencilReference = plan.consumer.reference,
            atomicGroup = plan.atomicGroup,
            orderingToken = plan.orderingToken,
            scissor = semantic.scissorBounds,
            attachment = attachment,
            isLastConsumer = index == consumerPackets.lastIndex,
        )
    }

    return when (val route = sealGPUCorePrimitiveClipStencilNativeRoute(
        GPUCorePrimitiveClipStencilNativeRouteRequest(
            clipArtifacts = listOf(plan),
            consumers = consumerInputs,
            producerGeometry = GPUCorePrimitiveClipStencilProducerGeometryAuthority(
                path.vertices,
                path.contourStarts,
            ),
            producerAttachment = attachment,
            producerAntiAlias = false,
            expectedLastConsumerCommandId = consumerPackets.last().commandIdValue,
        ),
    )) {
        is GPUCorePrimitiveClipStencilNativeRoute.Accepted ->
            GPUCorePrimitiveClipStencilPreparedCandidateValidation.Accepted(
                route,
                recomputedFan.vertices.toList(),
                recomputedFan.indices.toList(),
            )
        is GPUCorePrimitiveClipStencilNativeRoute.Refused ->
            refuse("${route.code}: ${route.message}")
    }
}
