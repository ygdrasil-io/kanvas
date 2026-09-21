package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.artifacts.*
import org.graphiks.kanvas.gpu.renderer.clips.*
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.coordinates.*
import org.graphiks.kanvas.gpu.renderer.materials.*
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveScissorAuthority
import org.graphiks.kanvas.gpu.renderer.recording.corePrimitiveTargetStateHash
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.vertices.*
import org.graphiks.math.geometry.TriangleTopologyI32

/** Projection into the existing prepared-vertices ABI; no source or physical allocation owner. */
internal fun lowerW5bVerticesDraw(draw: W5bVerticesDraw, target: GPUPixelBounds, graph: RenderGraph,
    frameSource: PreparedSourceFrameV6, capabilityHash: String, binding: PlanGeometryBufferBindingV1): GPUDrawPacket {
    require(graph.verifyW6aLayerCompilerWitness() && frameSource.table === graph.materialPlanTableOrNull() &&
        graph.passes().filterIsInstance<PlanPass.RenderPass>().flatMap { it.draws() }.any { it === draw })
    val geometry = draw.geometryF32
    val input = GPUPreparedVerticesArtifactInput(
        if (geometry.topologyI32 == TriangleTopologyI32.Strip) GPUVertexMode.TriangleStrip else GPUVertexMode.Triangles,
        geometry.copyPositionsF32(), draw.copyColorsRgba8(), geometry.copyCoordinatesF32(), geometry.copyIndicesI32(),
        "plan.vertices.${draw.commandIndex}")
    require(binding.vertexCountI32 == geometry.vertexCountI32 && binding.indexCountI32 == (geometry.indexCountI32 ?: 0) &&
        binding.vertexStrideBytesI32 == draw.vertexStrideBytesI32 && binding.indexElementBytesI32 == (draw.indexElementBytesI32 ?: 0))
    val vertexBytes = binding.vertexBytesI64
    val indexBytes = binding.indexBytesI64
    val packed = GPUPreparedVerticesPacker.pack(input, GPUPreparedVerticesPackingLimits(binding.vertexCountI32,
        binding.indexCountI32, vertexBytes, indexBytes, binding.indexCountI32),
        PlanOperationCapability.Uint32Index in graph.capabilities.supportedOperations())
    require(packed is GPUPreparedVerticesPackingResult.Ready) { packed.toString() }
    val artifact = packed.artifact.let { value ->
        require(value.layout.strideBytes == draw.vertexStrideBytesI32 && value.vertexCount == geometry.vertexCountI32 &&
            value.indexCount == geometry.indexCountI32 && value.vertexBytesForUpload().size.toLong() == vertexBytes &&
            (value.indexBytesForUpload()?.size?.toLong() ?: 0L) == indexBytes &&
            value.indexFormat == draw.indexElementBytesI32?.let { if (it == 2) "uint16" else "uint32" })
        if (!geometry.fanExpanded) value else GPUPreparedVerticesUploadArtifact(value.topology, value.layout,
            value.vertexBytesForUpload(), value.indexBytesForUpload(), value.vertexCount, value.indexCount, value.indexFormat,
            value.provenance, GPUPreparedVerticesCanonicalizationIdentity.TriangleFanToTriangleListV1)
    }
    val material = GPUPreparedMaterialProgram.fromCommonSource(frameSource, draw.commandIndex)
    val blend = W5bBlendPlanLowerer.lower(draw.blend)
    val primitiveBlend = draw.primitiveBlend?.let { GPUPrimitiveBlendPlan(W5bBlendPlanLowerer.lower(it)) }
    val scissor = draw.copyScissorI32().let { GPUPixelBounds(it.left, it.top, it.right, it.bottom) }
    val clip = if (scissor == target) GPUClipExecutionPlan.NoClip else GPUClipExecutionPlan.ScissorOnly(scissor)
    val coverage = if (scissor == target) GPUClipCoveragePlan.NoClip else GPUClipCoveragePlan.Scissor(
        GPUBounds(scissor.left.toFloat(), scissor.top.toFloat(), scissor.right.toFloat(), scissor.bottom.toFloat()))
    val matrix = draw.transformF32
    val gathered = GPUPreparedVerticesPayloadGatherer.gather(GPUPreparedVerticesPayloadInput(
        GPUDrawPayloadRef(draw.commandIndex, PREPARED_VERTICES_RENDER_STEP_IDENTITY), artifact, material,
        materialPlanEmission = GPUPreparedVerticesMaterialPlanEmission.common(material),
        topologyIdentity = if (geometry.topologyI32 == TriangleTopologyI32.Strip) GPUPreparedVerticesTopologyIdentity.TriangleStrip else GPUPreparedVerticesTopologyIdentity.Triangles,
        transformBytes = listOf(matrix.sx, matrix.kx, matrix.tx, matrix.ky, matrix.sy, matrix.ty,
            matrix.persp0, matrix.persp1, matrix.persp2).map(Float::toRawBits),
        targetBounds = target, scissorBounds = scissor,
        conservativeDrawBounds = draw.copyBoundsI32().let { GPUPixelBounds(it.left, it.top, it.right, it.bottom) },
        targetFormat = "rgba8unorm-srgb", clipIdentity = clip.canonicalIdentity(), clipCoverageIdentity = clip.canonicalIdentity(),
        primitiveColorPresent = draw.copyColorsRgba8() != null, primitiveBlendIdentity = primitiveBlend?.plan?.canonicalIdentity(),
        primitiveBlendPlan = primitiveBlend, w5bFinalBlendPlan = draw.blend, finalBlendIdentity = blend.canonicalIdentity(),
        capabilitySnapshotHash = capabilityHash, drawProvenance = "plan.vertices.${draw.commandIndex}", frameProvenance = GPUFrameProvenance.None))
    require(gathered is GPUPreparedVerticesPayloadResult.Ready) { gathered.toString() }
    return GPUDrawPacket(GPUDrawPacketID("packet.vertices.${draw.commandIndex}"), draw.commandIndex,
        "analysis.vertices.${draw.commandIndex}", "pass.vertices.${draw.commandIndex}", "frame", "binding.vertices.${draw.commandIndex}",
        "planned-vertices", draw.commandIndex.toLong(), "paint-order:${draw.commandIndex}",
        GPURenderStepID(PREPARED_VERTICES_RENDER_STEP_IDENTITY), 1, GPUDrawPacketRole.Shading, blend,
        PREPARED_VERTICES_RENDER_PIPELINE_KEY, bindingLayoutHash = PREPARED_VERTICES_BINDING_LAYOUT_HASH,
        semanticPayload = gathered.payload, vertexSourceLabel = PREPARED_VERTICES_VERTEX_SOURCE_LABEL,
        scissorBoundsHash = corePrimitiveScissorAuthority(scissor), targetStateHash = corePrimitiveTargetStateHash(1, GPUColorFormat.RGBA8UnormSrgb),
        originalPaintOrder = draw.commandIndex, resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
        clipCoveragePlan = coverage, clipExecutionPlan = clip)
}
