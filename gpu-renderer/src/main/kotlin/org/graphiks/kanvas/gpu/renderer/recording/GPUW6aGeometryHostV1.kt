package org.graphiks.kanvas.gpu.renderer.recording

import io.ygdrasil.webgpu.*
import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.execution.MaterialCoordinateSlotV1
import org.graphiks.kanvas.gpu.renderer.execution.preparedVerticesDrawLayoutV6
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralBlend
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveStructuralClip
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer

internal const val W6A_VERTEX_SHADER: String = """
    @vertex fn vs_main(@builtin(vertex_index) index: u32) -> @builtin(position) vec4<f32> {
        let positions = array<vec2<f32>, 3>(vec2<f32>(-1.0, -1.0), vec2<f32>(3.0, -1.0), vec2<f32>(-1.0, 3.0));
        return vec4<f32>(positions[index], 0.0, 1.0);
    }
"""
internal const val W6A_RECT_SHADER: String = W6A_VERTEX_SHADER + """
    struct Core { premul_rgba: vec4<f32>, }
    @group(0) @binding(0) var<uniform> core: Core;
    @fragment fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
        return core.premul_rgba;
    }
"""

/** Mechanically lowers the planner-owned recipe; it deliberately does not inspect the draw packet's authority. */
internal fun w6aGeometryTemplate(packet: GPUDrawPacket, recipe: W6SolidRectHostRecipeV1): GPUW5aGeometryHostTemplateV1 {
    require(recipe.family == W6SolidRectGeometryFamilyV1.FullscreenTriangle)
    require(recipe.target.sampleCountI32 == 1)
    require(recipe.coordinateSlot == W6SolidRectCoordinateSlotV1.FragmentPosition)
    val frozen = (recipe.colorMode as? W6SolidRectColorModeV1.FrozenColor)?.color?.copyColorF32()
    val source = frozen?.let(::w6aFrozenColorShader) ?: W6A_RECT_SHADER
    val layout = when (recipe.groupZeroAbi) {
        W6SolidRectGroupZeroAbiV1.UniformColor16 -> {
            require(recipe.colorMode is W6SolidRectColorModeV1.UniformColor16)
            GPUW5aHostBindGroupLayoutV1.of(listOf(GPUW5aHostBindGroupEntryV1(0, 2u,
                GPUW5aHostBindingLayoutV1.Buffer(GPUBufferBindingType.Uniform, false, 16L))))
        }
        W6SolidRectGroupZeroAbiV1.Empty -> {
            require(frozen != null)
            GPUW5aHostBindGroupLayoutV1.of(emptyList())
        }
    }
    val origin = recipe.materialOriginDeviceI32
    val colorKey = frozen?.let { ".${it.red.toBits()}.${it.green.toBits()}.${it.blue.toBits()}.${it.alpha.toBits()}" }.orEmpty()
    return GPUW5aGeometryHostTemplateV1(packet.packetId.value,
        "w6a.rect.v1.${recipe.site.ownerPassId.value}.${recipe.site.drawOrdinalI32}.${origin.x}.${origin.y}$colorKey", source, "vs_main", "fs_main",
        w6aColorTarget(recipe.blend, recipe.target.w6aTextureFormat()).hostTargetV1(), layout, null, MaterialCoordinateSlotV1.FragmentPosition,
        materialDevicePointWgsl = "fragment_position.xy + vec2<f32>(${origin.x}.0, ${origin.y}.0)")
}

private fun W6SolidRectTargetV1.w6aTextureFormat(): GPUTextureFormat = when (format) {
    W6SolidRectTargetFormatV1.RGBA8UnormSrgb -> GPUTextureFormat.RGBA8UnormSrgb
    W6SolidRectTargetFormatV1.RGBA8Unorm -> GPUTextureFormat.RGBA8Unorm
}

/**
 * Builds the existing CorePrimitive key from the final planner recipe only.  The packet still
 * supplies immutable shape bytes, but it is not allowed to select the W6 host route.
 */
internal fun w6aCorePrimitiveStructuralKey(recipe: W6CorePrimitiveHostRecipeV1,
    targetBounds: GPUPixelBounds): GPUCorePrimitiveRenderPipelineStructuralKey {
    val selector = recipe.selector
    require(selector.family in setOf(
        W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect,
        W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect,
    ))
    require(selector.uniformAbi == W6CorePrimitiveHostUniformAbiV1.AnalyticShape80)
    require(selector.target == W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample)
    require(selector.coverage == W6CorePrimitiveHostCoverageV1.AnalyticScalarAA)
    require(selector.topology == W6CorePrimitiveHostTopologyV1.IndexedTriangleList)
    require(selector.coordinateSlot == W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition &&
        selector.groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform80)
    val scissor = recipe.scissor
    val clip = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom).let { bounds ->
        if (bounds == targetBounds) GPUClipExecutionPlan.NoClip else GPUClipExecutionPlan.ScissorOnly(bounds)
    }
    return GPUCorePrimitiveRenderPipelineStructuralKey(
        shader = GPUCorePrimitiveRenderPipelineStructuralKey.Shader.AnalyticShape,
        topology = GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList,
        blend = W5bBlendPlanLowerer.lower(selector.blend).corePrimitiveStructuralBlend(),
        clip = clip.corePrimitiveStructuralClip(),
        colorFormat = GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb,
        sampleCount = selector.target.sampleCountI32,
    )
}

/** Mechanically projects the final planner recipe into the existing CorePrimitive host template. */
internal fun w6aCorePrimitiveGeometryTemplate(packet: GPUDrawPacket, recipe: W6CorePrimitiveHostRecipeV1,
    key: GPUCorePrimitiveRenderPipelineStructuralKey): GPUW5aGeometryHostTemplateV1 {
    val template = requireNotNull(sealCorePrimitiveGeometryHostTemplateV1(packet, key))
    val origin = recipe.materialOriginDeviceI32
    val family = when (recipe.selector.family) {
        W6CorePrimitiveHostGeometryFamilyV1.AnalyticRect -> "analytic-rect"
        W6CorePrimitiveHostGeometryFamilyV1.AnalyticRRect -> "analytic-rrect"
        W6CorePrimitiveHostGeometryFamilyV1.Point -> error("W6 Point uses its dedicated host template.")
    }
    return template.copy(
        pipelineRecipeId = "w6a.$family.v1.${recipe.site.ownerPassId.value}.${recipe.site.drawOrdinalI32}",
        materialCoordinateSlot = MaterialCoordinateSlotV1.FragmentPosition,
        materialDevicePointWgsl = "fragment_position.xy + vec2<f32>(${origin.x}.0, ${origin.y}.0)",
    )
}

/** Builds a Point key from its frozen site axes; packet material bytes never select geometry axes. */
internal fun w6aPointStructuralKey(packet: GPUDrawPacket, recipe: W6PointHostRecipeV1,
    targetBounds: GPUPixelBounds): GPUCorePrimitiveRenderPipelineStructuralKey {
    val selector = recipe.selector
    require(selector.family == W6CorePrimitiveHostGeometryFamilyV1.Point &&
        selector.uniformAbi == W6CorePrimitiveHostUniformAbiV1.Point32 &&
        selector.coverage == W6CorePrimitiveHostCoverageV1.FullOrScissor &&
        selector.topology == W6CorePrimitiveHostTopologyV1.IndexedTriangleList &&
        selector.target == W6CorePrimitiveHostTargetV1.Rgba8UnormSrgbSingleSample &&
        selector.coordinateSlot == W6CorePrimitiveHostCoordinateSlotV1.FragmentPosition &&
        selector.groupZeroAbi == W6CorePrimitiveHostGroupZeroAbiV1.DynamicUniform32)
    val scissor = recipe.scissor
    val clip = GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom).let { bounds ->
        if (bounds == targetBounds) GPUClipExecutionPlan.NoClip else GPUClipExecutionPlan.ScissorOnly(bounds)
    }
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
        ?: error("W6 Point host recipe requires a CorePrimitive packet.")
    val key = corePrimitiveRenderPipelineStructuralKey(semantic, clip,
        W5bBlendPlanLowerer.lower(selector.blend), selector.target.sampleCountI32,
        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb)
    require(key.topology == GPUCorePrimitiveRenderPipelineStructuralKey.Topology.DirectTriangleList &&
        key.blend == W5bBlendPlanLowerer.lower(selector.blend).corePrimitiveStructuralBlend() &&
        key.colorFormat == GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb &&
        key.sampleCount == selector.target.sampleCountI32) {
        "W6 Point host recipe selected an unexpected native geometry axis."
    }
    return key
}

/** Mechanically projects the final Point recipe into the existing CorePrimitive host template. */
internal fun w6aPointGeometryTemplate(packet: GPUDrawPacket, recipe: W6PointHostRecipeV1,
    key: GPUCorePrimitiveRenderPipelineStructuralKey): GPUW5aGeometryHostTemplateV1 {
    val template = requireNotNull(sealCorePrimitiveGeometryHostTemplateV1(packet, key))
    val origin = recipe.materialOriginDeviceI32
    return template.copy(
        pipelineRecipeId = "w6a.point.v1.${recipe.site.ownerPassId.value}.${recipe.site.drawOrdinalI32}",
        materialCoordinateSlot = MaterialCoordinateSlotV1.FragmentPosition,
        materialDevicePointWgsl = "fragment_position.xy + vec2<f32>(${origin.x}.0, ${origin.y}.0)",
    )
}

/** Mechanically translates the sealed Prepared Vertices selector; packet facts only authenticate it. */
internal fun w6aPreparedVerticesGeometryTemplate(packet: GPUDrawPacket,
    recipe: W6PreparedVerticesHostRecipeV1): GPUW5aGeometryHostTemplateV1 {
    require(w6aPreparedVerticesPacketMatchesRecipe(packet, recipe)) {
        "W6 prepared-vertices packet differs from its frozen host recipe."
    }
    val template = requireNotNull(sealW5aGeometryHostTemplateV1(packet))
    require(template.target == w6aColorTarget(recipe.blend).hostTargetV1() &&
        template.groupZeroLayout.entries == preparedVerticesDrawLayoutV6().hostLayoutV1().entries &&
        template.primitiveEncodedInput == (recipe.primitiveAlpha == W6PreparedVerticesHostPrimitiveAlphaV1.VertexColor)) {
        "W6 prepared-vertices renderer template differs from its frozen layout, blend, or primitive-alpha selector."
    }
    val origin = recipe.materialOriginDeviceI32
    return template.copy(pipelineRecipeId = recipe.hostProgramIdentity.canonicalIdentity,
        materialDevicePointWgsl = "input.position.xy + vec2<f32>(${origin.x}.0, ${origin.y}.0)")
}

/** Authenticates the renderer packet against planner-owned layout, uniform, blend, and source facts. */
internal fun w6aPreparedVerticesPacketMatchesRecipe(packet: GPUDrawPacket,
    recipe: W6PreparedVerticesHostRecipeV1): Boolean {
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.Vertices ?: return false
    val artifact = semantic.artifact
    val provenance = semantic.materialPlanProvenance ?: return false
    val layout = recipe.layout
    val attributes = layout.attributes().map { attribute -> when (attribute) {
        W6PreparedVerticesHostAttributeV1.Position -> "position"
        W6PreparedVerticesHostAttributeV1.Color -> "color"
        W6PreparedVerticesHostAttributeV1.TexCoord -> "texcoord"
    } }
    val offsets = layout.offsetsBytesI32().entries.associate { (attribute, offset) ->
        when (attribute) {
            W6PreparedVerticesHostAttributeV1.Position -> "position"
            W6PreparedVerticesHostAttributeV1.Color -> "color"
            W6PreparedVerticesHostAttributeV1.TexCoord -> "texcoord"
        } to offset
    }
    val sourceTable = provenance.sourcePlanTable
    val expectedTopology = when (recipe.topology) {
        W6PreparedVerticesHostTopologyV1.TriangleList -> "Triangles"
        W6PreparedVerticesHostTopologyV1.TriangleStrip -> "TriangleStrip"
    }
    val expectedIndex = when (recipe.indexWidth) {
        W6PreparedVerticesHostIndexWidthV1.None -> null
        W6PreparedVerticesHostIndexWidthV1.Uint16 -> "uint16"
        W6PreparedVerticesHostIndexWidthV1.Uint32 -> "uint32"
    }
    return semantic.targetFormat == "rgba8unorm-srgb" && semantic.topologyIdentity.sourceLabel == expectedTopology &&
        artifact.layout.attributes == attributes && artifact.layout.offsets == offsets &&
        artifact.layout.strideBytes == layout.strideBytesI32 && artifact.indexFormat == expectedIndex &&
        semantic.primitiveColorPresent == (recipe.primitiveAlpha == W6PreparedVerticesHostPrimitiveAlphaV1.VertexColor) &&
        semantic.w5bFinalBlendPlan == recipe.blend && provenance.ref == recipe.source.materialRef &&
        recipe.source.authenticates(sourceTable)
}

/** Authenticates the packet bytes against the frozen Point geometry before native allocation. */
internal fun w6aPointPacketMatchesRecipe(packet: GPUDrawPacket, draw: W5bPointDraw,
    recipe: W6PointHostRecipeV1): Boolean {
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
    val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return false
    val uniform = semantic.payloadRef.uniformBlock ?: return false
    val bounds = recipe.bounds
    val scissor = recipe.scissor
    return draw.clipOnly == null && draw.pointMode == recipe.pointMode &&
        semantic.sourceFamily == org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily.PointLine &&
        semantic.coverageMode == GPUCorePrimitiveCoverageMode.FullOrScissor &&
        geometry.geometryMode == org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode.DirectTriangles &&
        uniform.byteSize == 32L && uniform.bytes.size == 32 &&
        geometry.vertices == recipe.copyVerticesF32().toList() && geometry.indices == recipe.copyIndicesI32().toList() &&
        geometry.sourceVertexCount == recipe.vertexCountI32 &&
        geometry.coverBounds == GPUPixelBounds(bounds.left, bounds.top, bounds.right, bounds.bottom) &&
        semantic.scissorBounds == GPUPixelBounds(scissor.left, scissor.top, scissor.right, scissor.bottom) &&
        draw.copyVerticesF32().contentEquals(recipe.copyVerticesF32()) &&
        draw.copyIndicesI32().contentEquals(recipe.copyIndicesI32()) &&
        draw.copyBoundsI32() == bounds && draw.copyScissorI32() == scissor
}

/** Validates the packet's immutable shape payload against the final RenderPass recipe. */
internal fun w6aCorePrimitivePacketMatchesRecipe(packet: GPUDrawPacket, draw: PlanDraw,
    recipe: W6CorePrimitiveHostRecipeV1): Boolean {
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
    if (semantic.coverageMode != GPUCorePrimitiveCoverageMode.ScalarAA ||
        semantic.scissorBounds.left != recipe.scissor.left || semantic.scissorBounds.top != recipe.scissor.top ||
        semantic.scissorBounds.right != recipe.scissor.right || semantic.scissorBounds.bottom != recipe.scissor.bottom) return false
    return when (recipe) {
        is W6AnalyticRectHostRecipeV1 -> {
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.Rect ?: return false
            draw is AnalyticRectDraw && sameRectF32(draw.copyDeviceBounds(), recipe.deviceBounds) &&
                geometry.left.sameF32(recipe.deviceBounds.left) && geometry.top.sameF32(recipe.deviceBounds.top) &&
                geometry.right.sameF32(recipe.deviceBounds.right) && geometry.bottom.sameF32(recipe.deviceBounds.bottom) &&
                draw.copyRasterBounds() == recipe.rasterBounds && draw.copyScissor() == recipe.scissor
        }
        is W6AnalyticRRectHostRecipeV1 -> {
            val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.RRect ?: return false
            draw is AnalyticRRectDraw && draw.origin == recipe.drawOrigin &&
                sameRRectF32(draw.copyDeviceShape(), recipe.deviceShape) &&
                geometry.left.sameF32(recipe.deviceShape.rect.left) && geometry.top.sameF32(recipe.deviceShape.rect.top) &&
                geometry.right.sameF32(recipe.deviceShape.rect.right) && geometry.bottom.sameF32(recipe.deviceShape.rect.bottom) &&
                geometry.radii.size == 8 && geometry.radii.zip(rrectRadii(recipe.deviceShape)).all { (actual, expected) -> actual.sameF32(expected) } &&
                draw.copyRasterBounds() == recipe.rasterBounds && draw.copyScissor() == recipe.scissor
        }
        is W6PointHostRecipeV1 -> false
    }
}

private fun sameRectF32(left: org.graphiks.math.geometry.RectF32, right: org.graphiks.math.geometry.RectF32): Boolean =
    left.left.sameF32(right.left) && left.top.sameF32(right.top) &&
        left.right.sameF32(right.right) && left.bottom.sameF32(right.bottom)

private fun sameRRectF32(left: org.graphiks.math.geometry.RRectF32, right: org.graphiks.math.geometry.RRectF32): Boolean =
    sameRectF32(left.rect, right.rect) && rrectRadii(left).zip(rrectRadii(right)).all { (a, b) -> a.sameF32(b) }

private fun rrectRadii(shape: org.graphiks.math.geometry.RRectF32): List<Float> = listOf(
    shape.topLeft.x, shape.topLeft.y, shape.topRight.x, shape.topRight.y,
    shape.bottomRight.x, shape.bottomRight.y, shape.bottomLeft.x, shape.bottomLeft.y,
)

private fun Float.sameF32(other: Float): Boolean = toRawBits() == other.toRawBits()

/** A legacy colour is already sealed into the picture stream and has no W5 uniform row. */
private fun w6aFrozenColorShader(color: org.graphiks.math.color.ColorF32): String {
    fun component(value: Float): String {
        require(value.isFinite()) { "Frozen W6 colour must be finite" }
        return if (value == 0f) "0.0" else value.toString().replace('E', 'e')
    }
    return W6A_VERTEX_SHADER + """
        @fragment fn fs_main(@builtin(position) fragment_position: vec4<f32>) -> @location(0) vec4<f32> {
            return vec4<f32>(${component(color.red)}, ${component(color.green)}, ${component(color.blue)}, ${component(color.alpha)});
        }
    """
}

internal fun w6aColorTarget(
    blend: BlendPlan,
    format: GPUTextureFormat = GPUTextureFormat.RGBA8UnormSrgb,
): ColorTargetState {
    fun factor(value: BlendFactorV1): GPUBlendFactor = when (value) {
        BlendFactorV1.Zero -> GPUBlendFactor.Zero
        BlendFactorV1.One -> GPUBlendFactor.One
        BlendFactorV1.SrcAlpha -> GPUBlendFactor.SrcAlpha
        BlendFactorV1.OneMinusSrcAlpha -> GPUBlendFactor.OneMinusSrcAlpha
        BlendFactorV1.DstAlpha -> GPUBlendFactor.DstAlpha
        BlendFactorV1.OneMinusDstAlpha -> GPUBlendFactor.OneMinusDstAlpha
        BlendFactorV1.SrcColor -> GPUBlendFactor.Src
        BlendFactorV1.OneMinusSrcColor -> GPUBlendFactor.OneMinusSrc
    }
    if (blend is BlendPlan.DestinationReadV1) return ColorTargetState(format,
        BlendState(BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.One, GPUBlendFactor.Zero),
            BlendComponent(GPUBlendOperation.Add, GPUBlendFactor.One, GPUBlendFactor.Zero)))
    val fixed = blend as? BlendPlan.FixedFunctionV1
    val noOp = blend == BlendPlan.NoOpV1
    require(fixed != null || blend == BlendPlan.LegacySrcOverV1 || noOp)
    return ColorTargetState(format, BlendState(
        BlendComponent(GPUBlendOperation.Add,
            if (noOp) GPUBlendFactor.Zero else fixed?.colorSource?.let(::factor) ?: GPUBlendFactor.One,
            if (noOp) GPUBlendFactor.One else fixed?.colorDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha),
        BlendComponent(GPUBlendOperation.Add,
            if (noOp) GPUBlendFactor.Zero else fixed?.alphaSource?.let(::factor) ?: GPUBlendFactor.One,
            if (noOp) GPUBlendFactor.One else fixed?.alphaDestination?.let(::factor) ?: GPUBlendFactor.OneMinusSrcAlpha)))
}
