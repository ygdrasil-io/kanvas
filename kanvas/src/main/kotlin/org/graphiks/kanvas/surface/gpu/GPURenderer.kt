package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw

import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r

import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.blurKernelUniform
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb as GpuPathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.isAffine
import org.graphiks.kanvas.font.colr.COLRPaintNode
import org.graphiks.kanvas.font.colr.COLRV1ColorLineExtend
import org.graphiks.kanvas.font.colr.COLR_FOREGROUND_PALETTE_INDEX
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.OutlineCommand
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.GlyphCoordinateMapper
import org.graphiks.kanvas.text.GpuTextBlob
import org.graphiks.kanvas.text.MappedGlyph
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.TextBridge
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Applies DrawText paint alpha to a CPAL color without tinting its palette RGB channels. */
internal fun modulateCpalLayerAlpha(cpalColor: Color, paintColor: Color): Color =
    Color.fromRGBA(cpalColor.r, cpalColor.g, cpalColor.b, cpalColor.a * paintColor.a)

/** G records glyph shape coverage only; CPAL/paint alpha belongs exclusively to S. */
internal fun colorGlyphSourceColor(color: Color, geometryCoverage: Boolean): Color =
    if (geometryCoverage) Color.WHITE else color

internal fun productIntermediatePlannerScopeDiagnostics(): List<String> =
    listOf(
        "gpu.product.phase5 phase5PlannerActivation=false " +
            "reason=product-display-list-route-not-yet-planner-backed " +
            "route=kanvas:scene-src-snap advancedBlend=local-procedural " +
            "scenePlannerActivation=out-of-scope",
    )

internal fun selectPathVerticesForCommand(
    isStroke: Boolean,
    flattened: List<Point>,
    triangulated: List<Point>,
): List<Point> =
    if (isStroke) flattened else triangulated

private fun DisplayOp.hasActiveMaskBlur(): Boolean = when (this) {
    is DisplayOp.DrawRect -> paint.hasActiveMaskBlur()
    is DisplayOp.DrawPath -> paint.hasActiveMaskBlur()
    is DisplayOp.DrawRRect -> paint.hasActiveMaskBlur()
    else -> false
}

private fun Paint.hasActiveMaskBlur(): Boolean =
    (maskFilter as? MaskFilter.Blur)?.let { it.sigma != 0f } ?: false

/** The two source products required before a coverage-correct final blend. */
private enum class GPUClipSourcePlane {
    Color,
    GeometryCoverage,
}

internal fun DisplayOp.requiresSeparateGeometryCoverage(): Boolean = when (this) {
    is DisplayOp.DrawRect -> paint.antiAlias
    is DisplayOp.DrawRRect -> paint.antiAlias
    is DisplayOp.DrawPath -> paint.antiAlias
    // Image alpha describes S, never the destination-rect geometry G.
    is DisplayOp.DrawImage,
    is DisplayOp.DrawImageNine,
    is DisplayOp.DrawImageLattice,
    is DisplayOp.DrawAtlas,
    -> true
    // Triangle edges and glyph masks are geometric coverage, independent of
    // their sampled or paint alpha.
    is DisplayOp.DrawVertices,
    is DisplayOp.DrawMesh,
    is DisplayOp.DrawText -> true
    is DisplayOp.DrawPoint -> paint.antiAlias
    is DisplayOp.DrawPoints -> paint.antiAlias
    is DisplayOp.DrawDRRect -> paint.antiAlias
    is DisplayOp.DrawPicture -> true
    else -> false
}

private val geometryCoverageMaterial =
    org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor(1f, 1f, 1f, 1f)

private fun NormalizedDrawCommand.FillRect.forGeometryCoverage(): NormalizedDrawCommand.FillRect =
    copy(material = geometryCoverageMaterial)

private fun NormalizedDrawCommand.FillRRect.forGeometryCoverage(): NormalizedDrawCommand.FillRRect =
    copy(material = geometryCoverageMaterial)

private fun NormalizedDrawCommand.FillPath.forGeometryCoverage(): NormalizedDrawCommand.FillPath =
    copy(material = geometryCoverageMaterial)

/** Rebinds a shape source to opaque white without inheriting paint/image alpha into G. */
private fun NormalizedDrawCommand.forGeometryCoverage(): NormalizedDrawCommand = when (this) {
    is NormalizedDrawCommand.FillRect -> forGeometryCoverage()
    is NormalizedDrawCommand.FillRRect -> forGeometryCoverage()
    is NormalizedDrawCommand.FillPath -> forGeometryCoverage()
    else -> error("Geometry coverage is not defined for ${javaClass.simpleName}")
}

/** Layer restore source: premultiplied child color modulated only by layer opacity. */
private val LAYER_OPACITY_WGSL: String = """
    struct Uniforms {
        opacity: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    @group(1) @binding(1) var inputTex: texture_2d<f32>;
    @group(1) @binding(2) var inputSam: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dims = textureDimensions(inputTex);
        let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
        return textureSample(inputTex, inputSam, uv) * uniforms.opacity.x;
    }
""".trimIndent()

private fun layerOpacityUniformDraw(opacity: Float, bounds: LayerBounds): GPUBackendRawUniformDraw {
    val uniformBytes = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(opacity)
        putFloat(0f); putFloat(0f); putFloat(0f)
    }.array()
    return GPUBackendRawUniformDraw(
        uniformBytes = uniformBytes,
        scissorX = bounds.x,
        scissorY = bounds.y,
        scissorWidth = bounds.width,
        scissorHeight = bounds.height,
    )
}

private fun MaskBlurPlan.Ready.maskBlurDiagnosticFacts(): List<DiagnosticFact> {
    val kernel = blurKernelUniform(this)
    return listOf(
        DiagnosticFact("mask.blur.requested-sigma", requestedSigma.toString()),
        DiagnosticFact("mask.blur.normalized-sigma", normalizedSigma.toString()),
        DiagnosticFact("mask.blur.effective-sigma", effectiveSigma.toString()),
        DiagnosticFact("mask.blur.halo", halo.toString()),
        DiagnosticFact("mask.blur.dimensions", "${localWidth}x$localHeight"),
        DiagnosticFact("mask.blur.bytes", "$bytesPerTexture/$requiredBytes"),
        DiagnosticFact("blur.tap_count", kernel.tapCount.toString()),
        DiagnosticFact("mask.blur.module-keys", blurModuleKeysFor(this).joinToString(",")),
        DiagnosticFact("mask.blur.passes", "mask,horizontal,vertical,style,source"),
    )
}

/** Every shader destination-read mode has one stable uniform index shared by both formula shaders. */
internal fun destinationReadBlendModeIndex(mode: GPUBlendMode): Int? = when (mode) {
    GPUBlendMode.MULTIPLY -> 0
    GPUBlendMode.SCREEN -> 1
    GPUBlendMode.OVERLAY -> 2
    GPUBlendMode.DARKEN -> 3
    GPUBlendMode.LIGHTEN -> 4
    GPUBlendMode.DIFFERENCE -> 5
    GPUBlendMode.EXCLUSION -> 6
    GPUBlendMode.COLOR_DODGE -> 7
    GPUBlendMode.COLOR_BURN -> 8
    GPUBlendMode.HARD_LIGHT -> 9
    GPUBlendMode.SOFT_LIGHT -> 10
    GPUBlendMode.HUE -> 11
    GPUBlendMode.SATURATION -> 12
    GPUBlendMode.COLOR -> 13
    GPUBlendMode.LUMINOSITY -> 14
    else -> null
}

/** Stable uniform indices for every mapped blend mode in [CLIP_COVERAGE_BLEND_WGSL]. */
internal fun clipCoverageBlendModeIndex(mode: GPUBlendMode): Int = when (mode) {
    GPUBlendMode.CLEAR -> 0
    GPUBlendMode.SRC_OVER -> 1
    GPUBlendMode.SRC -> 2
    GPUBlendMode.DST -> 3
    GPUBlendMode.DST_OVER -> 4
    GPUBlendMode.SRC_IN -> 5
    GPUBlendMode.DST_IN -> 6
    GPUBlendMode.SRC_OUT -> 7
    GPUBlendMode.DST_OUT -> 8
    GPUBlendMode.SRC_ATOP -> 9
    GPUBlendMode.DST_ATOP -> 10
    GPUBlendMode.XOR -> 11
    GPUBlendMode.PLUS -> 12
    GPUBlendMode.MODULATE -> 13
    GPUBlendMode.MULTIPLY -> 14
    GPUBlendMode.SCREEN -> 15
    GPUBlendMode.OVERLAY -> 16
    GPUBlendMode.DARKEN -> 17
    GPUBlendMode.LIGHTEN -> 18
    GPUBlendMode.COLOR_DODGE -> 19
    GPUBlendMode.COLOR_BURN -> 20
    GPUBlendMode.HARD_LIGHT -> 21
    GPUBlendMode.SOFT_LIGHT -> 22
    GPUBlendMode.DIFFERENCE -> 23
    GPUBlendMode.EXCLUSION -> 24
    GPUBlendMode.HUE -> 25
    GPUBlendMode.SATURATION -> 26
    GPUBlendMode.COLOR -> 27
    GPUBlendMode.LUMINOSITY -> 28
}

internal fun destinationReadBlendUniformDraw(
    mode: GPUBlendMode,
    width: Int,
    height: Int,
): GPUBackendRawUniformDraw? {
    val index = destinationReadBlendModeIndex(mode) ?: return null
    val bytes = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
    bytes.putInt(index)
    return GPUBackendRawUniformDraw(
        uniformBytes = bytes.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = width,
        scissorHeight = height,
    )
}

/** Packs the reflected 16-byte mode block for coverage-correct alpha-mask composition. */
internal fun clipCoverageBlendUniformDraw(
    mode: GPUBlendMode,
    width: Int,
    height: Int,
): GPUBackendRawUniformDraw {
    val bytes = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
    bytes.putInt(clipCoverageBlendModeIndex(mode))
    return GPUBackendRawUniformDraw(
        uniformBytes = bytes.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = width,
        scissorHeight = height,
    )
}

private fun coverageCombineUniformDraw(width: Int, height: Int): GPUBackendRawUniformDraw =
    GPUBackendRawUniformDraw(
        uniformBytes = ByteArray(16),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = width,
        scissorHeight = height,
    )

/** Packs the scissor-aware destination-read formula uniform block. */
internal fun destinationReadScissorBlendUniformDraw(
    mode: GPUBlendMode,
    clipBounds: GPUBounds,
    width: Int,
    height: Int,
): GPUBackendRawUniformDraw? {
    val index = destinationReadBlendModeIndex(mode) ?: return null
    val bytes = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
        putInt(index)
        putInt(0); putInt(0); putInt(0)
        putFloat(clipBounds.left)
        putFloat(clipBounds.top)
        putFloat(clipBounds.right)
        putFloat(clipBounds.bottom)
    }
    return GPUBackendRawUniformDraw(
        uniformBytes = bytes.array(),
        scissorX = 0,
        scissorY = 0,
        scissorWidth = width,
        scissorHeight = height,
    )
}

/**
 * Replaces the scene with the destination-read blend result. The destination texture is never
 * mapped to the CPU: an existing scene is copied GPU-to-GPU, while an empty scene gets a
 * transparent snapshot before the three-texture formula pass. A device scissor is encoded into
 * the source coverage texture, so final coverage remains zero outside that clip.
 */
internal fun GPUBackendOffscreenTarget.renderDestinationReadBlend(
    sceneLabel: String,
    sceneHasContent: Boolean,
    sourceSurface: GPUClipSourceSurface,
    snapshotLabel: String,
    combinedCoverageLabel: String,
    clipMaskLabel: String?,
    mode: GPUBlendMode,
    colorFormat: String,
    width: Int,
    height: Int,
): Boolean {
    val draw = clipCoverageBlendUniformDraw(mode, width, height)
    val transparent = GPUClearColor(0.0, 0.0, 0.0, 0.0)
    val finalCoverageLabel = if (clipMaskLabel == null) {
        sourceSurface.geometryCoverageLabel
    } else {
        encodeOffscreenTexture(combinedCoverageLabel, transparent) {
            drawTwoTexturePass(
                wgsl = COMBINE_COVERAGE_WGSL,
                colorFormat = colorFormat,
                firstTextureLabel = sourceSurface.geometryCoverageLabel,
                secondTextureLabel = clipMaskLabel,
                draws = listOf(coverageCombineUniformDraw(width, height)),
                blendMode = GPUBlendMode.SRC,
            )
        }
        combinedCoverageLabel
    }
    if (sceneHasContent) {
        copyOffscreenTexture(sceneLabel, snapshotLabel)
    } else {
        encodeOffscreenTexture(snapshotLabel, transparent) {}
    }
    encodeOffscreenTexture(sceneLabel, transparent) {
        drawThreeTexturePass(
            wgsl = CLIP_COVERAGE_BLEND_WGSL,
            colorFormat = colorFormat,
            firstTextureLabel = sourceSurface.colorLabel,
            secondTextureLabel = snapshotLabel,
            thirdTextureLabel = finalCoverageLabel,
            draws = listOf(draw),
            blendMode = GPUBlendMode.SRC,
        )
    }
    return true
}

internal data class LayerBounds(val x: Int, val y: Int, val width: Int, val height: Int)

private sealed interface LayerPlan {
    data class Supported(
        val bounds: LayerBounds?,
        val composite: LayerCompositePlan,
        /** Clip to apply when a synthetic picture group is restored to its parent. */
        val compositeClip: ClipStack? = null,
    ) : LayerPlan
    data class Refused(val reason: String) : LayerPlan
}

private data class LayerCompositePlan(
    val opacity: Float = 1f,
    val blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
    val backdrop: BackdropPlan? = null,
)

private data class BackdropPlan(
    val sourceLabel: String,
    val filteredLabel: String,
    val bounds: LayerBounds,
)

private data class SceneTargetFrame(
    val label: String,
    val hasContent: Boolean,
    val plan: LayerPlan.Supported,
)

internal class LayerScissorOffscreenTarget(
    private val delegate: GPUBackendOffscreenTarget,
    private val sceneLayerBounds: (String) -> LayerBounds?,
) : GPUBackendOffscreenTarget by delegate {
    override fun encodeOffscreenTexture(
        textureLabel: String,
        clearColor: GPUClearColor?,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        delegate.encodeOffscreenTexture(textureLabel, clearColor) {
            val scopedRecorder = sceneLayerBounds(textureLabel)?.let { bounds ->
                LayerScissorRenderRecorder(this, bounds)
            } ?: this
            block(scopedRecorder)
        }
    }
}

private class LayerScissorRenderRecorder(
    private val delegate: GPUBackendRenderRecorder,
    private val layerBounds: LayerBounds,
) : GPUBackendRenderRecorder by delegate {
    override fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
        blendMode: GPUBlendMode?,
        passBatchKind: GPUBackendSimplePassBatchKind?,
    ) {
        delegate.drawFullscreenPass(wgsl, colorFormat, draws.mapNotNull { it.intersectLayerScissor(layerBounds) }, blendMode, passBatchKind)
    }

    override fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
        blendMode: GPUBlendMode?,
        sourceLabel: String,
        passBatchKind: GPUBackendSimplePassBatchKind?,
    ) {
        delegate.drawFullscreenUniformPayloadPass(
            wgsl,
            colorFormat,
            draws.mapNotNull { it.intersectLayerScissor(layerBounds) },
            blendMode,
            sourceLabel,
            passBatchKind,
        )
    }

    override fun drawFullscreenRawUniformPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
        passBatchKind: GPUBackendSimplePassBatchKind?,
    ) {
        delegate.drawFullscreenRawUniformPass(wgsl, colorFormat, draws.mapNotNull { it.intersectLayerScissor(layerBounds) }, blendMode, passBatchKind)
    }

    override fun drawFullscreenTextureUniformPass(
        wgsl: String,
        colorFormat: String,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
        stencilMode: GPUBackendStencilMode?,
        stencilConfig: GPUBackendStencilCoverConfig,
    ) {
        delegate.drawFullscreenTextureUniformPass(
            wgsl,
            colorFormat,
            textureRgba,
            textureWidth,
            textureHeight,
            textureFormat,
            draws.mapNotNull { it.intersectLayerScissor(layerBounds) },
            blendMode,
            stencilMode,
            stencilConfig,
        )
    }

    override fun drawFullscreenStencilPass(
        wgsl: String,
        colorFormat: String,
        stencilMode: GPUBackendStencilMode,
        triangleData: GPUBackendTriangleData?,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
        stencilConfig: GPUBackendStencilCoverConfig,
    ) {
        val scopedDraws = draws.mapNotNull { it.intersectLayerScissor(layerBounds) }
        if (stencilMode == GPUBackendStencilMode.Test && scopedDraws.isEmpty()) return
        delegate.drawFullscreenStencilPass(
            wgsl,
            colorFormat,
            stencilMode,
            triangleData,
            scopedDraws,
            blendMode,
            stencilConfig,
        )
    }

    override fun drawVertexColorIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        blendMode: GPUBlendMode?,
    ) {
        uniformDraw.intersectLayerScissor(layerBounds)?.let {
            delegate.drawVertexColorIndexed(vertexBufferLabel, indexCount, it, blendMode)
        }
    }

    override fun drawVertexPositionUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        blendMode: GPUBlendMode?,
    ) {
        uniformDraw.intersectLayerScissor(layerBounds)?.let {
            delegate.drawVertexPositionUVIndexed(
                vertexBufferLabel,
                indexCount,
                it,
                textureRgba,
                textureWidth,
                textureHeight,
                textureFormat,
                blendMode,
            )
        }
    }

    override fun drawVertexPositionDualUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        texture1Rgba: ByteArray,
        texture1Width: Int,
        texture1Height: Int,
        texture2Rgba: ByteArray,
        texture2Width: Int,
        texture2Height: Int,
        textureFormat: String,
        blendMode: GPUBlendMode?,
    ) {
        uniformDraw.intersectLayerScissor(layerBounds)?.let {
            delegate.drawVertexPositionDualUVIndexed(
                vertexBufferLabel,
                indexCount,
                it,
                texture1Rgba,
                texture1Width,
                texture1Height,
                texture2Rgba,
                texture2Width,
                texture2Height,
                textureFormat,
                blendMode,
            )
        }
    }

    override fun drawCompositePass(
        wgsl: String,
        colorFormat: String,
        textureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        delegate.drawCompositePass(wgsl, colorFormat, textureLabel, draws.mapNotNull { it.intersectLayerScissor(layerBounds) }, blendMode)
    }

    override fun drawBlendPass(
        wgsl: String,
        colorFormat: String,
        srcTextureLabel: String,
        dstTextureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
    ) {
        delegate.drawBlendPass(wgsl, colorFormat, srcTextureLabel, dstTextureLabel, draws.mapNotNull { it.intersectLayerScissor(layerBounds) })
    }

    override fun drawTextAtlasPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        delegate.drawTextAtlasPass(
            atlasRgba,
            atlasWidth,
            atlasHeight,
            atlasFormat,
            vertexData,
            indexData,
            draws.mapNotNull { it.intersectLayerScissor(layerBounds) },
            blendMode,
        )
    }

    override fun drawColorGlyphPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        delegate.drawColorGlyphPass(
            atlasRgba,
            atlasWidth,
            atlasHeight,
            atlasFormat,
            vertexData,
            indexData,
            draws.mapNotNull { it.intersectLayerScissor(layerBounds) },
            blendMode,
        )
    }
}

internal fun GPUBackendRawUniformDraw.intersectLayerScissor(
    layerX: Int,
    layerY: Int,
    layerWidth: Int,
    layerHeight: Int,
): GPUBackendRawUniformDraw? = intersectLayerScissor(LayerBounds(layerX, layerY, layerWidth, layerHeight))

private fun GPUBackendRawUniformDraw.intersectLayerScissor(layerBounds: LayerBounds): GPUBackendRawUniformDraw? =
    intersectScissor(layerBounds)?.let { copy(scissorX = it.x, scissorY = it.y, scissorWidth = it.width, scissorHeight = it.height) }

private fun GPUBackendRectDraw.intersectLayerScissor(layerBounds: LayerBounds): GPUBackendRectDraw? =
    intersectScissor(layerBounds)?.let { copy(scissorX = it.x, scissorY = it.y, scissorWidth = it.width, scissorHeight = it.height) }

private fun GPUBackendUniformPayloadDraw.intersectLayerScissor(layerBounds: LayerBounds): GPUBackendUniformPayloadDraw? =
    intersectScissor(layerBounds)?.let {
        GPUBackendUniformPayloadDraw(uniformBytes(), materialization, it.x, it.y, it.width, it.height)
    }

private fun GPUBackendRawUniformDraw.intersectScissor(layerBounds: LayerBounds): LayerBounds? {
    val left = maxOf(scissorX, layerBounds.x)
    val top = maxOf(scissorY, layerBounds.y)
    val right = minOf(scissorX + scissorWidth, layerBounds.x + layerBounds.width)
    val bottom = minOf(scissorY + scissorHeight, layerBounds.y + layerBounds.height)
    return if (left < right && top < bottom) LayerBounds(left, top, right - left, bottom - top) else null
}

private fun GPUBackendRectDraw.intersectScissor(layerBounds: LayerBounds): LayerBounds? =
    GPUBackendRawUniformDraw(byteArrayOf(0), scissorX, scissorY, scissorWidth, scissorHeight).intersectScissor(layerBounds)

private fun GPUBackendUniformPayloadDraw.intersectScissor(layerBounds: LayerBounds): LayerBounds? =
    GPUBackendRawUniformDraw(byteArrayOf(0), scissorX, scissorY, scissorWidth, scissorHeight).intersectScissor(layerBounds)

@OptIn(ExperimentalUnsignedTypes::class)
internal fun renderViaGpu(
    buffer: DisplayListBuffer,
    width: Int,
    height: Int,
    format: PixelFormat,
    config: RenderConfig,
    routeTrace: GPUClipRouteTrace? = null,
): RenderResult {
    // Expand supported Pictures before the clip prepass. This ensures every captured child
    // (including advanced blends) reaches the normal per-operation S/G compositor in order.
    val ops = buffer.ops().expandPicturesForGpuReplay()
    val diagnostics = Diagnostics()
    val dispatched = mutableListOf<String>()
    val targets = GPUTargetFacts(width = width, height = height, colorFormat = config.gpuColorFormat.gpuLabel)

    val session = GPUBackendRuntimeFactory.createOrNull()
        ?: error("webgpu-context-unavailable")

    session.use { s ->
        val target = s.createOffscreenTarget(
            GPUOffscreenTargetRequest(
                width = width,
                height = height,
                colorFormat = config.gpuColorFormat.gpuLabel,
            ),
        )
        target.use { target ->
            val texFormat = config.gpuColorFormat.gpuLabel
            val rootLayerPlan = LayerPlan.Supported(bounds = null, composite = LayerCompositePlan())
            var sceneLabel = ""
            var scenePlan = rootLayerPlan
            val t = LayerScissorOffscreenTarget(target) { textureLabel ->
                scenePlan.bounds?.takeIf { textureLabel == sceneLabel }
            }
            sceneLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:scene", width = width, height = height, format = texFormat),
            )
            val srcLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:src", width = width, height = height, format = texFormat),
            )
            val geometryCoverageLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:geometry-coverage", width = width, height = height, format = texFormat),
            )
            val combinedCoverageLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:combined-coverage", width = width, height = height, format = texFormat),
            )
            val snapLabel = t.createOffscreenTexture(
                GPUBackendOffscreenTexture(label = "kanvas:snap", width = width, height = height, format = texFormat),
            )
            val clipFrameCache = GPUClipCoverageFrameCache(config.maxClipIntermediateBytes.toLong())

            var sceneHasContent = false
            var clipSourceRoute = false
            var clipSourcePreservesClip = false
            var clipSourcePlane: GPUClipSourcePlane? = null
            var sourceHasContent = false
            var maskBlurSourceBounds: GPUBounds? = null
            val layerStack = java.util.ArrayDeque<SceneTargetFrame>()
            var layerOrdinal = 0
            var suppressedLayerDepth = 0
            val clearTransparent = GPUClearColor(0.0, 0.0, 0.0, 0.0)
            fun sceneClear() = when {
                clipSourceRoute && sourceHasContent -> null
                clipSourceRoute -> clearTransparent
                sceneHasContent -> null
                else -> clearTransparent
            }

            fun classifyLayerRequest(rec: SaveLayerRec, transform: Matrix33): LayerPlan {
                rec.gpuCompositePreflightRefusalOrNull()?.let { return LayerPlan.Refused(it) }
                val layerPaint = rec.paint
                val composite = LayerCompositePlan(
                    opacity = layerPaint?.color?.alphaByte?.toFloat()?.div(255f) ?: 1f,
                    blend = layerPaint?.blendMode?.toGpuBlendFacts() ?: GPUBlendFacts.srcOver(),
                )

                val bounds = rec.bounds ?: return LayerPlan.Supported(null, composite, rec.compositeClip)
                val mappedCorners = listOf(
                    transform * org.graphiks.kanvas.types.Point(bounds.left, bounds.top),
                    transform * org.graphiks.kanvas.types.Point(bounds.right, bounds.top),
                    transform * org.graphiks.kanvas.types.Point(bounds.left, bounds.bottom),
                    transform * org.graphiks.kanvas.types.Point(bounds.right, bounds.bottom),
                )
                if (mappedCorners.any { !it.x.isFinite() || !it.y.isFinite() }) {
                    return LayerPlan.Refused("unsupported.layer.bounds.non_finite")
                }

                val left = mappedCorners.minOf { it.x }
                val top = mappedCorners.minOf { it.y }
                val right = mappedCorners.maxOf { it.x }
                val bottom = mappedCorners.maxOf { it.y }
                val x = floor(left).toInt().coerceIn(0, width)
                val y = floor(top).toInt().coerceIn(0, height)
                val endX = ceil(right).toInt().coerceIn(x, width)
                val endY = ceil(bottom).toInt().coerceIn(y, height)
                return LayerPlan.Supported(
                    bounds = LayerBounds(x, y, endX - x, endY - y),
                    composite = composite,
                    compositeClip = rec.compositeClip,
                )
            }

            fun layerScissor(plan: LayerPlan.Supported): LayerBounds =
                plan.bounds ?: LayerBounds(x = 0, y = 0, width = width, height = height)

            fun beginLayer(rec: SaveLayerRec, transform: Matrix33, cmdId: GPUDrawCommandID): Boolean {
                if (suppressedLayerDepth > 0) {
                    suppressedLayerDepth++
                    return false
                }
                when (val plan = classifyLayerRequest(rec, transform)) {
                    is LayerPlan.Refused -> {
                        diagnostics.fatal("refuse:saveLayer:${cmdId.value}", "saveLayer", plan.reason)
                        suppressedLayerDepth = 1
                        return false
                    }
                    is LayerPlan.Supported -> {
                        if (plan.bounds?.let { it.width == 0 || it.height == 0 } == true) {
                            suppressedLayerDepth = 1
                            return false
                        }
                        layerStack.addLast(SceneTargetFrame(sceneLabel, sceneHasContent, scenePlan))
                        sceneLabel = t.createOffscreenTexture(
                            GPUBackendOffscreenTexture(
                                label = "kanvas:saveLayer:${layerOrdinal++}",
                                width = width,
                                height = height,
                                format = texFormat,
                            ),
                        )
                        sceneHasContent = false
                        scenePlan = plan
                        return true
                    }
                }
            }

            fun endLayer(cmdId: GPUDrawCommandID): Boolean {
                if (suppressedLayerDepth > 0) {
                    suppressedLayerDepth--
                    return false
                }
                if (layerStack.isEmpty()) {
                    diagnostics.fatal("refuse:saveLayer:${cmdId.value}", "saveLayer", "unsupported.layer.unbalanced_end")
                    return false
                }
                val childLabel = sceneLabel
                val childPlan = scenePlan
                val parent = layerStack.removeLast()
                sceneLabel = parent.label
                sceneHasContent = parent.hasContent
                scenePlan = parent.plan
                val bounds = layerScissor(childPlan)
                if (bounds.width == 0 || bounds.height == 0) return true

                if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    // A nested layer in a G pass carries the already-rasterized geometry mask;
                    // its layer paint must not turn alpha/color into coverage.
                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                        drawCompositePass(
                            wgsl = COPY_WGSL,
                            colorFormat = texFormat,
                            textureLabel = childLabel,
                            draws = listOf(layerOpacityUniformDraw(1f, bounds)),
                            blendMode = GPUBlendMode.SRC_OVER,
                        )
                    }
                    sceneHasContent = true
                    return true
                }

                val layerSourceLabel = if (childPlan.composite.opacity == 1f) {
                    childLabel
                } else {
                    val opacityLabel = if (clipSourceRoute) combinedCoverageLabel else srcLabel
                    t.encodeOffscreenTexture(opacityLabel, clearTransparent) {
                        drawCompositePass(
                            wgsl = LAYER_OPACITY_WGSL,
                            colorFormat = texFormat,
                            textureLabel = childLabel,
                            draws = listOf(layerOpacityUniformDraw(childPlan.composite.opacity, bounds)),
                            blendMode = GPUBlendMode.SRC,
                        )
                    }
                    opacityLabel
                }
                val compositeClipPlan = childPlan.compositeClip
                    ?.toGPUClipFacts(targets)
                    ?.coverageRequest
                    ?.let { GPUClipCoveragePlanner.plan(it, config, t.maxTextureDimension2D) }
                    ?: GPUClipCoveragePlan.NoClip
                if (compositeClipPlan is GPUClipCoveragePlan.Refused) {
                    diagnostics.fatal(
                        "refuse:saveLayer:${cmdId.value}",
                        "saveLayer",
                        compositeClipPlan.code,
                    )
                    return false
                }
                val coverageCommand = DisplayOp.DrawRect(
                    Rect(
                        bounds.x.toFloat(),
                        bounds.y.toFloat(),
                        (bounds.x + bounds.width).toFloat(),
                        (bounds.y + bounds.height).toFloat(),
                    ),
                    Paint.fill(Color.WHITE).copy(antiAlias = false),
                    Matrix33.identity(),
                    // Scissors constrain the restore directly; a non-scissor clip is folded into
                    // the final coverage texture below so the group still composites atomically.
                    if (compositeClipPlan is GPUClipCoveragePlan.Scissor) {
                        requireNotNull(childPlan.compositeClip)
                    } else {
                        ClipStack.WideOpen
                    },
                ).toNormalizedCommand(cmdId, targets)
                t.encodeOffscreenTexture(geometryCoverageLabel, clearTransparent) {
                    dispatchFillRect(
                        coverageCommand,
                        dispatched,
                        diagnostics,
                        width,
                        height,
                        config,
                        recordResult = false,
                    )
                }
                val layerBlend = childPlan.composite.blend
                val mode = when (layerBlend.kind) {
                    GPUBlendKind.SrcOver -> GPUBlendMode.SRC_OVER
                    else -> layerBlend.blendMode
                } ?: return false.also {
                        diagnostics.fatal(
                            "refuse:saveLayer:${cmdId.value}",
                            "saveLayer",
                            "unsupported.layer.blend:${layerBlend.modeLabel.lowercase()}",
                        )
                    }
                val rendered = try {
                    val clipMaskLease = when (compositeClipPlan) {
                        is GPUClipCoveragePlan.Mask ->
                            t.acquireClipMask(compositeClipPlan, clipFrameCache, diagnostics, config)
                        else -> null
                    }
                    clipMaskLease.use {
                        t.renderDestinationReadBlend(
                            sceneLabel = sceneLabel,
                            sceneHasContent = sceneHasContent || (clipSourceRoute && sourceHasContent),
                            sourceSurface = GPUClipSourceSurface(layerSourceLabel, geometryCoverageLabel),
                            snapshotLabel = snapLabel,
                            combinedCoverageLabel = combinedCoverageLabel,
                            clipMaskLabel = it?.mask?.sampleLabel,
                            mode = mode,
                            colorFormat = texFormat,
                            width = width,
                            height = height,
                        )
                    }
                } catch (_: GPUClipCoverageFrameBudgetExceededException) {
                    diagnostics.fatal(
                        "refuse:saveLayer:${cmdId.value}",
                        "saveLayer",
                        "unsupported.clip.frame_budget",
                    )
                    false
                }
                if (rendered) {
                    sceneHasContent = true
                    if (layerBlend.requiresDestinationRead) {
                        diagnostics.degrade(
                            code = "route:destination-read:saveLayer:${cmdId.value}",
                            operation = "saveLayer:${cmdId.value}",
                            reason = "gpu-copy-then-formula",
                            facts = listOf(
                                DiagnosticFact("destination-read.source", layerSourceLabel),
                                DiagnosticFact("destination-read.snapshot", snapLabel),
                                DiagnosticFact("destination-read.mode", layerBlend.modeLabel),
                                DiagnosticFact(
                                    "clip.strategy",
                                    when (compositeClipPlan) {
                                        is GPUClipCoveragePlan.Mask -> "alpha-mask"
                                        is GPUClipCoveragePlan.Scissor -> "scissor"
                                        else -> "direct"
                                    },
                                ),
                                DiagnosticFact("destination-read.action", "copy-then-formula"),
                            ),
                        )
                    }
                }
                return rendered
            }

            /**
             * A scissor clip is represented by the temporary source texture itself during a
             * destination-read blend. Pass that same scissor to source dispatchers which do not
             * otherwise consume a [ClipStack], so the formula cannot alter pixels outside it.
             */
            fun destinationReadSourceScissor(clip: ClipStack): GPUCoverageScissor? {
                if (!clipSourcePreservesClip) return null
                val deviceRect = clip as? ClipStack.DeviceRect ?: return null
                return truncatedScissor(
                    bounds = GPUBounds(0f, 0f, width.toFloat(), height.toFloat()),
                    clipBounds = GPUBounds(
                        deviceRect.rect.left,
                        deviceRect.rect.top,
                        deviceRect.rect.right,
                        deviceRect.rect.bottom,
                    ),
                    surfaceWidth = width,
                    surfaceHeight = height,
                )
            }

            /**
             * `null` is normally the no-scissor default for a dispatcher. During a preserved
             * scissor source, however, it can also mean that the device rect has no target
             * intersection. Do not let that empty source fall back to a full-target dispatch.
             */
            fun destinationReadSourceClipIsEmpty(clip: ClipStack): Boolean =
                clipSourcePreservesClip &&
                    clip is ClipStack.DeviceRect &&
                    destinationReadSourceScissor(clip) == null

            /**
             * Keeps a render target transparent only until its first successful sub-pass.
             *
             * A single DisplayOp can encode several passes (points, nine-patch cells, atlas
             * sprites, color-glyph layers). Marking only after the outer operation returns makes
             * every sub-pass clear the same target, so only the final sub-pass survives.
             */
            fun recordSourcePart(rendered: Boolean): Boolean {
                if (rendered) {
                    if (clipSourceRoute) sourceHasContent = true else sceneHasContent = true
                }
                return rendered
            }

            fun planMaskBlur(command: NormalizedDrawCommand): MaskBlurPlan =
                command.maskBlurPreflightRefusalReasonOrNull()?.let(MaskBlurPlan::Refused)
                    ?: MaskBlurPlanner.plan(
                        command.toMaskBlurRequest(width, height, t.maxTextureDimension2D, config),
                    )

            fun refuseMaskBlur(command: NormalizedDrawCommand, plan: MaskBlurPlan.Refused) {
                diagnostics.fatal(
                    "refuse:${command.diagnosticName}",
                    command.diagnosticName,
                    plan.code,
                )
            }

            fun renderMaskBlur(command: NormalizedDrawCommand, plan: MaskBlurPlan.Ready): Boolean {
                maskBlurSourceBounds = plan.deviceBounds
                val routedCommand = when {
                    !clipSourceRoute -> command
                    clipSourcePreservesClip -> command.copyForDestinationReadSource()
                    else -> command.copyForClipSource(width, height)
                }
                if (clipSourceRoute && clipSourcePlane == GPUClipSourcePlane.Color) {
                    // S is the unmodulated paint material over the blur allocation. The blurred
                    // alpha belongs solely to G, otherwise a halo receives its coverage twice.
                    val material = routedCommand.material as? org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor
                        ?: return false
                    val sourceRect = DisplayOp.DrawRect(
                        rect = Rect(
                            plan.deviceBounds.left,
                            plan.deviceBounds.top,
                            plan.deviceBounds.right,
                            plan.deviceBounds.bottom,
                        ),
                        paint = Paint.fill(Color.fromRGBA(material.r, material.g, material.b, material.a))
                            .copy(antiAlias = false),
                        transform = Matrix33.identity(),
                        clip = ClipStack.WideOpen,
                    ).toNormalizedCommand(command.commandId, targets)
                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                        dispatchFillRect(
                            sourceRect,
                            dispatched,
                            diagnostics,
                            width,
                            height,
                            config,
                            recordResult = false,
                            uncoveredSourceColor = true,
                        )
                    }
                    return recordSourcePart(true)
                }
                val sourceCommand = if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    routedCommand.forGeometryCoverage()
                } else {
                    routedCommand
                }
                plan.diagnostics.forEach { diagnostic ->
                    diagnostics.degrade(
                        "degrade:${command.diagnosticName}:${command.commandId.value}:${diagnostic.code}",
                        command.diagnosticName,
                        diagnostic.code,
                    )
                }
                diagnostics.degrade(
                    code = "route:mask-blur:${command.commandId.value}",
                    operation = command.diagnosticName,
                    reason = "mask-blur-source",
                    facts = plan.maskBlurDiagnosticFacts(),
                )
                return recordSourcePart(t.renderMaskBlurCommand(
                    sceneLabel,
                    sourceCommand,
                    plan,
                    sceneClear(),
                    dispatched,
                    diagnostics,
                    texFormat,
                    recordResult = !clipSourceRoute,
                ).rendered)
            }

            fun dispatchRectDirect(command: NormalizedDrawCommand.FillRect): Boolean {
                val routedCommand = when {
                    !clipSourceRoute -> command
                    clipSourcePreservesClip -> command.copy(blend = GPUBlendFacts.srcOver())
                    else -> command.copyForClipSource(width, height)
                }
                val sourceCommand = if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    routedCommand.forGeometryCoverage()
                } else {
                    routedCommand
                }
                if (sourceCommand.blend.requiresDestinationRead) {
                    diagnostics.fatal(
                        "refuse:${sourceCommand.diagnosticName}",
                        sourceCommand.diagnosticName,
                        "unsupported.destination_read.source_recursion",
                    )
                    return false
                } else {
                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                        dispatchFillRect(
                            sourceCommand, dispatched, diagnostics, width, height, config,
                            recordResult = !clipSourceRoute,
                            uncoveredSourceColor = clipSourcePlane == GPUClipSourcePlane.Color,
                        )
                    }
                }
                return recordSourcePart(true)
            }

            fun dispatchPathDirect(command: NormalizedDrawCommand.FillPath): Boolean {
                val routedCommand = when {
                    !clipSourceRoute -> command
                    clipSourcePreservesClip -> command.copy(blend = GPUBlendFacts.srcOver())
                    else -> command.copyForClipSource(width, height)
                }
                val sourceCommand = if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    routedCommand.forGeometryCoverage()
                } else {
                    routedCommand
                }
                if (sourceCommand.blend.requiresDestinationRead) {
                    diagnostics.fatal("refuse:drawPath:${sourceCommand.commandId.value}", "drawPath", "unsupported_blend:advanced")
                    return false
                }
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillPath(
                        sourceCommand, dispatched, diagnostics, width, height, config,
                        recordResult = !clipSourceRoute,
                    )
                }
                return recordSourcePart(true)
            }

            fun dispatchRRectDirect(command: NormalizedDrawCommand.FillRRect): Boolean {
                val routedCommand = when {
                    !clipSourceRoute -> command
                    clipSourcePreservesClip -> command.copy(blend = GPUBlendFacts.srcOver())
                    else -> command.copyForClipSource(width, height)
                }
                val sourceCommand = if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    routedCommand.forGeometryCoverage()
                } else {
                    routedCommand
                }
                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                    dispatchFillRRect(
                        sourceCommand, dispatched, diagnostics, width, height, config,
                        recordResult = !clipSourceRoute,
                        uncoveredSourceColor = clipSourcePlane == GPUClipSourcePlane.Color,
                    )
                }
                return recordSourcePart(true)
            }

            fun drawGlyphPath(
                commands: List<OutlineCommand>,
                offsetX: Float,
                offsetY: Float,
                color: Color,
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ): Boolean {
                val tx = op.transform
                val sx = tx.scaleX; val kx = tx.skewX; val txx = tx.transX
                val ky = tx.skewY; val sy = tx.scaleY; val ty = tx.transY
                val verbs = mutableListOf<GpuPathVerb>()
                for (cmd in commands) {
                    when (cmd) {
                        is OutlineCommand.MoveTo -> {
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.MoveTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                        }
                        is OutlineCommand.LineTo -> {
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.LineTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                        }
                        is OutlineCommand.QuadraticTo -> {
                            val cx = cmd.controlX.toFloat() + offsetX
                            val cy = cmd.controlY.toFloat() + offsetY
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.QuadTo(
                                Point(sx * cx + kx * cy + txx, ky * cx + sy * cy + ty),
                                Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                            ))
                        }
                        is OutlineCommand.CubicTo -> {
                            val c1x = cmd.controlX1.toFloat() + offsetX
                            val c1y = cmd.controlY1.toFloat() + offsetY
                            val c2x = cmd.controlX2.toFloat() + offsetX
                            val c2y = cmd.controlY2.toFloat() + offsetY
                            val x = cmd.x.toFloat() + offsetX
                            val y = cmd.y.toFloat() + offsetY
                            verbs.add(GpuPathVerb.CubicTo(
                                Point(sx * c1x + kx * c1y + txx, ky * c1x + sy * c1y + ty),
                                Point(sx * c2x + kx * c2y + txx, ky * c2x + sy * c2y + ty),
                                Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                            ))
                        }
                        is OutlineCommand.Close -> verbs.add(GpuPathVerb.Close)
                    }
                }
                val pathData = PathData(verbs, emptyList())
                val tessellator = PathTessellator(
                    tolerance = config.curveTolerance,
                    maxVertices = config.maxPathVertices.toInt(),
                )
                val flattened = tessellator.flattenWithContours(pathData)
                val flat = flattened.points
                if (flat.size < 3) return false
                val vertices = flat.flatMap { listOf(it.x, it.y) }
                val syntheticOp = DisplayOp.DrawPath(
                    path = Path { },
                    paint = org.graphiks.kanvas.paint.Paint(color = color),
                    transform = Matrix33.identity(),
                    clip = op.clip,
                )
                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                val cmd = syntheticOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                return dispatchPathDirect(cmd)
            }

            fun renderShaderText(
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ): Boolean {
                val tf = op.blob.typeface as? FontTypeface ?: run {
                    diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "unsupported.text.outline.no_typeface")
                    return false
                }
                val scaler = tf.scaler ?: run {
                    diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "unsupported.text.outline.no_scaler")
                    return false
                }
                val tx = op.transform
                val sx = tx.scaleX; val kx = tx.skewX; val txx = tx.transX
                val ky = tx.skewY; val sy = tx.scaleY; val ty = tx.transY

                var rendered = false
                for (run in op.blob.glyphRuns) {
                    for ((idx, gid) in run.glyphs.withIndex()) {
                        val pos = run.positions[idx]
                        val scaled = scaler.scaleGlyph(gid.toInt(), run.fontSize)
                        val mapped = GlyphCoordinateMapper.map(scaled)
                        if (mapped !is MappedGlyph.Drawn) continue
                        val baselineX = pos.x + op.x
                        val baselineY = pos.y + op.y

                        val verbs = mutableListOf<GpuPathVerb>()
                        for (cmd in mapped.outlineCommands) {
                            when (cmd) {
                                is OutlineCommand.MoveTo -> {
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.MoveTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                                }
                                is OutlineCommand.LineTo -> {
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.LineTo(Point(sx * x + kx * y + txx, ky * x + sy * y + ty)))
                                }
                                is OutlineCommand.QuadraticTo -> {
                                    val cx = cmd.controlX.toFloat() + baselineX
                                    val cy = cmd.controlY.toFloat() + baselineY
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.QuadTo(
                                        Point(sx * cx + kx * cy + txx, ky * cx + sy * cy + ty),
                                        Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                                    ))
                                }
                                is OutlineCommand.CubicTo -> {
                                    val c1x = cmd.controlX1.toFloat() + baselineX
                                    val c1y = cmd.controlY1.toFloat() + baselineY
                                    val c2x = cmd.controlX2.toFloat() + baselineX
                                    val c2y = cmd.controlY2.toFloat() + baselineY
                                    val x = cmd.x.toFloat() + baselineX
                                    val y = cmd.y.toFloat() + baselineY
                                    verbs.add(GpuPathVerb.CubicTo(
                                        Point(sx * c1x + kx * c1y + txx, ky * c1x + sy * c1y + ty),
                                        Point(sx * c2x + kx * c2y + txx, ky * c2x + sy * c2y + ty),
                                        Point(sx * x + kx * y + txx, ky * x + sy * y + ty),
                                    ))
                                }
                                is OutlineCommand.Close -> verbs.add(GpuPathVerb.Close)
                            }
                        }
                        val pathData = PathData(verbs, emptyList())
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        if (flat.size < 3) continue
                        val vertices = flat.flatMap { listOf(it.x, it.y) }
                        val syntheticOp = DisplayOp.DrawPath(
                            path = Path { },
                            paint = op.paint,
                            transform = Matrix33.identity(),
                            clip = op.clip,
                        )
                        val glyphCmdId = GPUDrawCommandID(dispatched.size)
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                        val cmd = syntheticOp.toNormalizedCommand(glyphCmdId, targets, vertices, contourStarts, flat.size)
                        if (!clipSourceRoute && cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawText:shader:${glyphCmdId.value}", "drawText", "unsupported_blend:advanced")
                            continue
                        }
                        rendered = dispatchPathDirect(cmd) || rendered
                    }
                }
                return rendered
            }

            fun dispatchColrV1Node(
                node: COLRPaintNode,
                scaler: GlyphScaler,
                fontSize: Float,
                posX: Float,
                posY: Float,
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
                solidColors: Map<Int, Color>,
            ): Boolean {
                val kind = node.kind
                return when {
                    kind == "colr-v1-paint-glyph" -> {
                        val refGlyphId = node.glyphId
                        if (refGlyphId == null) {
                            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_glyph_no_ref")
                            false
                        } else {
                            val scaled = scaler.scaleGlyph(refGlyphId, fontSize)
                            val glyphColor = colorGlyphSourceColor(
                                node.children.firstNotNullOfOrNull { solidColors[it] } ?: op.paint.color,
                                geometryCoverage = clipSourcePlane == GPUClipSourcePlane.GeometryCoverage,
                            )
                            val mapped = GlyphCoordinateMapper.map(scaled)
                            if (mapped is MappedGlyph.Drawn) {
                                drawGlyphPath(mapped.outlineCommands, posX + op.x, posY + op.y, glyphColor, op, cmdId)
                            } else {
                                false
                            }
                        }
                    }
                    kind == "colr-v1-paint-solid" -> false // color resolved by parent glyph via solidColors
                    kind == "colr-v1-paint-linear-gradient" ||
                    kind == "colr-v1-paint-radial-gradient" -> {
                        diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_gradient_needs_paint_tree")
                        false
                    }
                    kind == "colr-v1-paint-sweep-gradient" -> {
                        diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "colrv1_sweep_not_routed")
                        false
                    }
                    kind.startsWith("colr-v1-paint-translate") ||
                    kind.startsWith("colr-v1-paint-scale") ||
                    kind.startsWith("colr-v1-paint-rotate") ||
                    kind.startsWith("colr-v1-paint-skew") ||
                    kind == "colr-v1-paint-transform" ||
                    kind.startsWith("colr-v1-paint-composite") ||
                    kind == "colr-v1-paint-layers" ||
                    kind == "colr-v1-paint-colr-glyph" ||
                    kind == "colr-v1-glyph" -> false // passthrough
                    else -> false
                }
            }

            fun renderColorText(
                op: DisplayOp.DrawText,
                cmdId: GPUDrawCommandID,
            ): Boolean {
                val tf = op.blob.typeface as? FontTypeface ?: run {
                    diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "unsupported.text.color.no_typeface")
                    return false
                }
                val scaler = tf.scaler ?: run {
                    diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "unsupported.text.color.no_scaler")
                    return false
                }
                val foregroundColor = op.paint.color

                fun resolveSolidColors(nodes: List<COLRPaintNode>): Map<Int, Color> {
                    val result = mutableMapOf<Int, Color>()
                    for (n in nodes) {
                        if (n.kind != "colr-v1-paint-solid") continue
                        val pi = n.paletteIndex ?: continue
                        if (pi == COLR_FOREGROUND_PALETTE_INDEX) {
                            result[n.id] = foregroundColor
                            continue
                        }
                        val argb = scaler.resolveCpalColor(pi) ?: continue
                        result[n.id] = modulateCpalLayerAlpha(
                            Color.fromRGBA(
                                ((argb shr 16) and 0xFF) / 255f,
                                ((argb shr 8) and 0xFF) / 255f,
                                (argb and 0xFF) / 255f,
                                ((argb shr 24) and 0xFF) / 255f,
                            ),
                            foregroundColor,
                        )
                    }
                    return result
                }

                var rendered = false
                for (run in op.blob.glyphRuns) {
                    for ((idx, gid) in run.glyphs.withIndex()) {
                        val pos = run.positions[idx]
                        val rep = scaler.getGlyphRepresentation(gid.toInt(), op.blob.fontSize) ?: continue

                        when (rep) {
                            is GlyphRepresentation.ColorLayersV1 -> {
                                val solidColors = resolveSolidColors(rep.paintGraph.nodes)
                                for (node in rep.paintGraph.nodes) {
                                    rendered = dispatchColrV1Node(
                                        node, scaler, op.blob.fontSize, pos.x, pos.y, op, cmdId, solidColors,
                                    ) || rendered
                                }
                            }
                            is GlyphRepresentation.ColorLayers -> {
                                for (layer in rep.layers) {
                                    val scaled = scaler.scaleGlyph(layer.glyphId, op.blob.fontSize)
                                    val color = colorGlyphSourceColor(
                                        modulateCpalLayerAlpha(
                                            Color.fromRGBA(
                                                ((layer.paletteColorArgb shr 16) and 0xFF) / 255f,
                                                ((layer.paletteColorArgb shr 8) and 0xFF) / 255f,
                                                (layer.paletteColorArgb and 0xFF) / 255f,
                                                ((layer.paletteColorArgb shr 24) and 0xFF) / 255f,
                                            ),
                                            op.paint.color,
                                        ),
                                        geometryCoverage = clipSourcePlane == GPUClipSourcePlane.GeometryCoverage,
                                    )
                                    val mapped = GlyphCoordinateMapper.map(scaled)
                                    if (mapped is MappedGlyph.Drawn) {
                                        rendered = drawGlyphPath(
                                            mapped.outlineCommands,
                                            pos.x + op.x,
                                            pos.y + op.y,
                                            color,
                                            op,
                                            cmdId,
                                        ) || rendered
                                    }
                                }
                            }
                            is GlyphRepresentation.Bitmap -> {
                                diagnostics.degrade(
                                    "degrade:drawText:${cmdId.value}",
                                    "drawText",
                                    "unsupported.text.color_bitmap_glyph",
                                )
                            }
                            else -> {}
                        }
                    }
                }
                return rendered
            }

            fun extendToTileMode(extend: COLRV1ColorLineExtend?): TileMode = when (extend) {
                COLRV1ColorLineExtend.PAD -> TileMode.CLAMP
                COLRV1ColorLineExtend.REPEAT -> TileMode.REPEAT
                COLRV1ColorLineExtend.REFLECT -> TileMode.MIRROR
                null -> TileMode.CLAMP
            }

            val textureCache = mutableMapOf<String, ByteArray>()
            fun cachePixels(image: org.graphiks.kanvas.image.Image) {
                if (image.sourceId !in textureCache) {
                    val px = image.pixels
                    if (px != null) {
                        textureCache[image.sourceId] = image.expandToRgbaForGpu()
                    } else {
                        diagnostics.warn("no_pixels:${image.sourceId}", "cachePixels", "cpu_pixels_unavailable")
                    }
                }
            }
            fun scanImages(scanOps: List<DisplayOp>) {
                for (op in scanOps) {
                    if (op.perspectiveCaptureRefusalReasonOrNull() != null) continue
                    when (op) {
                        is DisplayOp.DrawImage -> cachePixels(op.image)
                        is DisplayOp.DrawImageNine -> cachePixels(op.image)
                        is DisplayOp.DrawImageLattice -> cachePixels(op.image)
                        is DisplayOp.DrawAtlas -> cachePixels(op.atlas)
                        is DisplayOp.DrawPicture -> scanImages(op.picture.ops)
                        else -> {}
                    }
                }
            }
            scanImages(ops)

            /** Image geometry ignores sampled alpha; a blur expands it to the filtered output bounds. */
            fun renderImageGeometryCoverage(op: DisplayOp.DrawImage, cmdId: GPUDrawCommandID): Boolean {
                val imageCommand = op.toImageRectCommand(cmdId, targets)
                val routedImageCommand = when {
                    clipSourceRoute && !clipSourcePreservesClip -> imageCommand.copyForClipSource(width, height)
                    clipSourceRoute -> imageCommand.copy(blend = GPUBlendFacts.srcOver())
                    else -> imageCommand
                }
                val geometryBounds = (routedImageCommand.imageFilterPlan as? GPUImageFilterPlan.Blur)
                    ?.outputBounds
                val geometryRect = geometryBounds?.let { bounds ->
                    Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                } ?: op.dst
                val coverage = DisplayOp.DrawRect(
                    rect = geometryRect,
                    paint = Paint.fill(Color.WHITE).copy(antiAlias = op.paint?.antiAlias ?: true),
                    transform = op.transform,
                    clip = if (clipSourceRoute && !clipSourcePreservesClip) ClipStack.WideOpen else op.clip,
                ).toNormalizedCommand(cmdId, targets)
                return dispatchRectDirect(coverage)
            }

            fun renderImageColorCommand(cmd: NormalizedDrawCommand.DrawImageRect): Boolean {
                val routedCommand = when {
                    !clipSourceRoute -> cmd
                    clipSourcePreservesClip -> cmd.copy(blend = GPUBlendFacts.srcOver())
                    else -> cmd.copyForClipSource(width, height)
                }
                val fatalBefore = diagnostics.fatalCount
                val plan = routedCommand.imageFilterPlan
                when (plan) {
                    GPUImageFilterPlan.None, GPUImageFilterPlan.Identity -> {
                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                            dispatchImageRect(
                                routedCommand, textureCache, dispatched, diagnostics, width, height, config,
                                recordResult = !clipSourceRoute,
                            )
                        }
                    }
                    is GPUImageFilterPlan.Refused -> {
                        diagnostics.fatal("refuse:${routedCommand.diagnosticName}", routedCommand.diagnosticName, plan.code)
                    }
                    is GPUImageFilterPlan.Blur -> {
                        t.renderImageCommand(
                            sceneTextureLabel = sceneLabel,
                            command = routedCommand,
                            textureCache = textureCache,
                            sceneClearColor = sceneClear(),
                            dispatched = dispatched,
                            diagnostics = diagnostics,
                            colorFormat = texFormat,
                            recordResult = !clipSourceRoute,
                        )
                    }
                }
                return recordSourcePart(diagnostics.fatalCount == fatalBefore && plan !is GPUImageFilterPlan.Refused)
            }

            fun renderImageCommand(op: DisplayOp.DrawImage, cmdId: GPUDrawCommandID): Boolean =
                if (clipSourceRoute && clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    renderImageGeometryCoverage(op, cmdId)
                } else {
                    renderImageColorCommand(op.toImageRectCommand(cmdId, targets))
                }

            GPUClipUsePrepass.register(
                operations = ops,
                target = targets,
                config = config,
                maxTextureDimension2D = t.maxTextureDimension2D,
                cache = clipFrameCache,
            )

            /** Encodes the whole text operation into the current target; a clip source is always SrcOver. */
            fun renderTextCommand(op: DisplayOp.DrawText, cmdId: GPUDrawCommandID): Boolean {
                if (destinationReadSourceClipIsEmpty(op.clip)) return false
                val fatalBefore = diagnostics.fatalCount
                val rendered = when {
                    op.paint.shader != null && extractSolidShaderColor(op.paint.shader) == null ->
                        renderShaderText(op, cmdId)
                    hasColorGlyphs(op.blob) -> renderColorText(op, cmdId)
                    op.paint.isStroke() -> renderShaderText(op, cmdId)
                    else -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (!clipSourceRoute && cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawText:${cmdId.value}", "drawText", "unsupported_blend:advanced")
                            false
                        } else {
                            val ctmScale = ctmEffectiveScale(op.transform)
                            val rasterBlob = op.blob.scaledForRasterization(ctmScale)
                            var gpuBlob = TextBridge.rasterize(rasterBlob)
                            if (gpuBlob == null) {
                                diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "rasterize_failed")
                                false
                            } else {
                                gpuBlob = gpuBlob.normalizeGlyphRects(ctmScale)
                                t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                    drawTextAtlasPass(
                                        gpuBlob = gpuBlob,
                                        blendMode = if (clipSourceRoute) GPUBlendMode.SRC_OVER else cmd.blend.blendMode,
                                        dispatched = dispatched,
                                        diagnostics = diagnostics,
                                        textColor = if (clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                                            Color.WHITE
                                        } else {
                                            resolveTextColor(op.paint)
                                        },
                                        targetWidth = width,
                                        targetHeight = height,
                                        drawOriginX = op.x,
                                        drawOriginY = op.y,
                                        transform = op.transform,
                                        recordResult = !clipSourceRoute,
                                        scissor = destinationReadSourceScissor(op.clip),
                                        sourcePlane = clipSourcePlane,
                                    )
                                }
                                true
                            }
                        }
                    }
                }
                return recordSourcePart(rendered && diagnostics.fatalCount == fatalBefore)
            }

            /** Triangle coverage for textured vertices/meshes, independent of sampled texel alpha. */
            fun renderVerticesGeometryCoverage(
                vertices: org.graphiks.kanvas.types.Vertices,
                transform: Matrix33,
                clip: ClipStack,
                cmdId: GPUDrawCommandID,
                operationName: String,
            ): Boolean {
                val indices = vertices.indices?.toIntArray() ?: IntArray(vertices.positions.size) { it }
                if (indices.isEmpty() || indices.any { it !in vertices.positions.indices }) {
                    diagnostics.fatal(
                        "refuse:$operationName:${cmdId.value}",
                        operationName,
                        "unsupported.vertices.indices",
                    )
                    return false
                }
                val path = Path()
                fun devicePoint(index: Int): org.graphiks.kanvas.types.Point {
                    val point = vertices.positions[index]
                    return org.graphiks.kanvas.types.Point(
                        transform.scaleX * point.x + transform.skewX * point.y + transform.transX,
                        transform.skewY * point.x + transform.scaleY * point.y + transform.transY,
                    )
                }
                fun triangle(a: Int, b: Int, c: Int) {
                    devicePoint(a).let { path.moveTo(it.x, it.y) }
                    devicePoint(b).let { path.lineTo(it.x, it.y) }
                    devicePoint(c).let { path.lineTo(it.x, it.y) }
                    path.close()
                }
                when (vertices.mode) {
                    org.graphiks.kanvas.types.VertexMode.TRIANGLES -> {
                        if (indices.size % 3 != 0) return false
                        for (index in indices.indices step 3) triangle(indices[index], indices[index + 1], indices[index + 2])
                    }
                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_STRIP ->
                        for (index in 2 until indices.size) triangle(indices[index - 2], indices[index - 1], indices[index])
                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_FAN ->
                        for (index in 2 until indices.size) triangle(indices[0], indices[index - 1], indices[index])
                }
                val flattened = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                    .flattenWithContours(path.toPathTessellatorData())
                val points = flattened.points
                if (points.size < 3) return false
                return dispatchPathDirect(
                    DisplayOp.DrawPath(path, Paint.fill(Color.WHITE), Matrix33.identity(), clip).toNormalizedCommand(
                        cmdId,
                        targets,
                        points.flatMap { listOf(it.x, it.y) },
                        flattened.contourStarts.ifEmpty { listOf(0) },
                        points.size,
                    ),
                )
            }

            /** Encodes path-backed or textured vertices into the current target without reapplying the outer clip. */
            fun renderVerticesCommand(
                vertices: org.graphiks.kanvas.types.Vertices,
                paint: Paint,
                transform: Matrix33,
                clip: org.graphiks.kanvas.canvas.ClipStack,
                cmdId: GPUDrawCommandID,
                operationName: String,
            ): Boolean {
                if (destinationReadSourceClipIsEmpty(clip)) return false
                if (!transform.isAffine()) {
                    diagnostics.fatal(
                        "refuse:$operationName:${cmdId.value}",
                        operationName,
                        "unsupported.vertices.perspective_transform",
                    )
                    return false
                }
                if (clipSourceRoute && clipSourcePlane == GPUClipSourcePlane.GeometryCoverage) {
                    return renderVerticesGeometryCoverage(vertices, transform, clip, cmdId, operationName)
                }
                val texCoords = vertices.texCoords
                if (vertices.colors != null || (texCoords == null && vertices.indices != null)) {
                    diagnostics.fatal(
                        "refuse:$operationName:${cmdId.value}",
                        operationName,
                        "unsupported.vertices.colors_or_indices",
                    )
                    return false
                }
                if (texCoords != null) {
                    if (vertices.mode != org.graphiks.kanvas.types.VertexMode.TRIANGLES) {
                        diagnostics.fatal(
                            "refuse:$operationName:${cmdId.value}",
                            operationName,
                            "unsupported.vertices.textured_mode",
                        )
                        return false
                    }
                    val imageShader = paint.shader as? Shader.Image
                    if (imageShader == null) {
                        diagnostics.degrade(
                            "unimplemented:$operationName:textured:${cmdId.value}",
                            operationName,
                            "gpu_textured_vertices_no_image_shader",
                        )
                        return false
                    }
                    val image = imageShader.image
                    if (image.pixels == null) {
                        diagnostics.degrade(
                            "unimplemented:$operationName:textured:${cmdId.value}",
                            operationName,
                            "gpu_textured_vertices_no_pixels",
                        )
                        return false
                    }
                    val textureBytes = image.expandToRgbaForGpu().let { expanded ->
                        if (image.colorType != ColorType.BGRA_8888) {
                            expanded
                        } else {
                            expanded.copyOf().also { rgba ->
                                for (offset in rgba.indices step 4) {
                                    val blue = rgba[offset]
                                    rgba[offset] = rgba[offset + 2]
                                    rgba[offset + 2] = blue
                                }
                            }
                        }
                    }
                    val positions = FloatArray(vertices.positions.size * 2) { index ->
                        val point = vertices.positions[index / 2]
                        val deviceX = transform.scaleX * point.x + transform.skewX * point.y + transform.transX
                        val deviceY = transform.skewY * point.x + transform.scaleY * point.y + transform.transY
                        if (index % 2 == 0) {
                            deviceX / width * 2f - 1f
                        } else {
                            1f - deviceY / height * 2f
                        }
                    }
                    val uvs = FloatArray(texCoords.size * 2) { index ->
                        if (index % 2 == 0) texCoords[index / 2].x else texCoords[index / 2].y
                    }
                    val indices = vertices.indices?.toIntArray() ?: IntArray(vertices.positions.size) { it }
                    if (
                        indices.isEmpty() ||
                        indices.size % 3 != 0 ||
                        indices.any { it !in vertices.positions.indices }
                    ) {
                        diagnostics.fatal(
                            "refuse:$operationName:${cmdId.value}",
                            operationName,
                            "unsupported.vertices.indices",
                        )
                        return false
                    }
                    var encoded = false
                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                        encoded = dispatchTexturedVertices(
                            positions = positions,
                            uvs = uvs,
                            uvs2 = null,
                            indices = indices,
                            paint = if (clipSourceRoute) {
                                paint.copy(blendMode = org.graphiks.kanvas.paint.BlendMode.SRC_OVER)
                            } else {
                                paint
                            },
                            textureBytes = textureBytes,
                            textureWidth = image.width,
                            textureHeight = image.height,
                            textureSourceId = image.sourceId,
                            diagnostics = diagnostics,
                            surfaceWidth = width,
                            surfaceHeight = height,
                            config = config,
                            diagnosticName = operationName,
                            blendModeOverride = if (clipSourceRoute) GPUBlendMode.SRC_OVER else null,
                            scissor = destinationReadSourceScissor(clip),
                        )
                    }
                    return recordSourcePart(encoded)
                }

                if (vertices.positions.size < 3) return false
                val path = Path().also { path ->
                    when (vertices.mode) {
                        org.graphiks.kanvas.types.VertexMode.TRIANGLES -> {
                            var index = 0
                            while (index + 2 < vertices.positions.size) {
                                path.moveTo(vertices.positions[index].x, vertices.positions[index].y)
                                path.lineTo(vertices.positions[index + 1].x, vertices.positions[index + 1].y)
                                path.lineTo(vertices.positions[index + 2].x, vertices.positions[index + 2].y)
                                path.close()
                                index += 3
                            }
                        }
                        org.graphiks.kanvas.types.VertexMode.TRIANGLE_STRIP -> {
                            for (index in 2 until vertices.positions.size) {
                                path.moveTo(vertices.positions[index - 2].x, vertices.positions[index - 2].y)
                                path.lineTo(vertices.positions[index - 1].x, vertices.positions[index - 1].y)
                                path.lineTo(vertices.positions[index].x, vertices.positions[index].y)
                                path.close()
                            }
                        }
                        org.graphiks.kanvas.types.VertexMode.TRIANGLE_FAN -> {
                            val first = vertices.positions.first()
                            for (index in 2 until vertices.positions.size) {
                                path.moveTo(first.x, first.y)
                                path.lineTo(vertices.positions[index - 1].x, vertices.positions[index - 1].y)
                                path.lineTo(vertices.positions[index].x, vertices.positions[index].y)
                                path.close()
                            }
                        }
                    }
                }
                val flattened = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                    .flattenWithContours(path.toPathTessellatorData())
                val flat = flattened.points
                if (flat.size < 3) {
                    diagnostics.degrade(
                        "unimplemented:$operationName:insufficient:${cmdId.value}",
                        operationName,
                        "insufficient_vertices:${flat.size}",
                    )
                    return false
                }
                val command = DisplayOp.DrawPath(path, paint, transform, clip).toNormalizedCommand(
                    cmdId,
                    targets,
                    flat.flatMap { listOf(it.x, it.y) },
                    flattened.contourStarts.ifEmpty { listOf(0) },
                    flat.size,
                )
                return dispatchPathDirect(command)
            }

            fun coreRoutePreflightRefusalReason(op: DisplayOp): String? = op.coreRoutePreflightRefusalReason()

            fun coreRouteOperationName(op: DisplayOp): String = when (op) {
                is DisplayOp.DrawMesh -> "drawMesh"
                is DisplayOp.DrawPicture -> "drawPicture"
                else -> "gpu"
            }

            fun refuseCoreRoutePreflight(op: DisplayOp, cmdId: GPUDrawCommandID): Boolean {
                val reason = coreRoutePreflightRefusalReason(op) ?: return false
                val operation = coreRouteOperationName(op)
                diagnostics.fatal("refuse:$operation:${cmdId.value}", operation, reason)
                return true
            }

            fun sourcePlanCommand(
                command: NormalizedDrawCommand,
                source: Boolean,
            ): NormalizedDrawCommand = when {
                !source -> command
                // The mask source pass itself remains wide-open in renderMaskBlur, but an exact
                // captured device rect is required to bound its intermediate allocation plan.
                command.clip.kind == GPUClipKind.DeviceRect -> command
                clipSourcePreservesClip -> command.copyForDestinationReadSource()
                else -> command.copyForClipSource(width, height)
            }

            /** Executes the normalized core routes in either the scene or the transparent full-target source. */
            fun executeCoreClipRoute(
                op: DisplayOp,
                cmdId: GPUDrawCommandID,
                source: Boolean,
                preserveClipInSource: Boolean = false,
                sourcePlane: GPUClipSourcePlane? = null,
            ): Boolean {
                val savedSceneLabel = sceneLabel
                val savedSceneHasContent = sceneHasContent
                val savedSourceHasContent = sourceHasContent
                val savedClipSourceRoute = clipSourceRoute
                val savedClipSourcePreservesClip = clipSourcePreservesClip
                val savedClipSourcePlane = clipSourcePlane
                if (source) {
                    sceneLabel = when (sourcePlane) {
                        GPUClipSourcePlane.Color -> srcLabel
                        GPUClipSourcePlane.GeometryCoverage -> geometryCoverageLabel
                        null -> error("source plane required for a clip source")
                    }
                    sceneHasContent = false
                    clipSourceRoute = true
                    clipSourcePreservesClip = preserveClipInSource
                    clipSourcePlane = sourcePlane
                    sourceHasContent = false
                }
                val fatalBefore = diagnostics.fatalCount
                var rendered = false
                try {
                    rendered = when (op) {
                        is DisplayOp.DrawRect -> {
                            if (op.paint.isStroke()) {
                                val command = op.toStrokePathCommand(cmdId, targets)
                                if (command.maskFilter == null) {
                                    dispatchPathDirect(command)
                                } else when (val blurPlan = planMaskBlur(sourcePlanCommand(command, source))) {
                                    is MaskBlurPlan.Ready -> renderMaskBlur(command, blurPlan)
                                    is MaskBlurPlan.Refused -> {
                                        refuseMaskBlur(command, blurPlan)
                                        false
                                    }
                                    MaskBlurPlan.Identity -> dispatchPathDirect(command)
                                }
                            } else {
                                val command = op.toNormalizedCommand(cmdId, targets)
                                if (command.maskFilter == null) {
                                    dispatchRectDirect(command)
                                } else when (val blurPlan = planMaskBlur(sourcePlanCommand(command, source))) {
                                    is MaskBlurPlan.Ready -> renderMaskBlur(command, blurPlan)
                                    is MaskBlurPlan.Refused -> {
                                        refuseMaskBlur(command, blurPlan)
                                        false
                                    }
                                    MaskBlurPlan.Identity -> dispatchRectDirect(command)
                                }
                            }
                        }
                        is DisplayOp.DrawPath -> {
                            val paint = op.paint
                            val isStroke = paint.isStroke()
                            val pathRect = Rect(0f, 0f, 0f, 0f)
                            if (!isStroke &&
                                op.transform == Matrix33.identity() &&
                                op.path.fillType in setOf(FillType.WINDING, FillType.EVEN_ODD) &&
                                op.path.isRect(pathRect)
                            ) {
                                val command = DisplayOp.DrawRect(pathRect, paint, op.transform, op.clip)
                                    .toNormalizedCommand(cmdId, targets)
                                if (command.maskFilter == null) {
                                    dispatchRectDirect(command)
                                } else when (val blurPlan = planMaskBlur(sourcePlanCommand(command, source))) {
                                    is MaskBlurPlan.Ready -> renderMaskBlur(command, blurPlan)
                                    is MaskBlurPlan.Refused -> {
                                        refuseMaskBlur(command, blurPlan)
                                        false
                                    }
                                    MaskBlurPlan.Identity -> dispatchRectDirect(command)
                                }
                            } else {
                                val flattened = PathTessellator(
                                    tolerance = config.curveTolerance,
                                    maxVertices = config.maxPathVertices.toInt(),
                                ).flattenWithContours(op.path.toPathTessellatorData())
                                val points = flattened.points
                                val allowsDegenerateRoundStroke =
                                    isStroke && paint.strokeCap.name.lowercase() == "round"
                                val minimumVertices = if (isStroke) {
                                    if (allowsDegenerateRoundStroke) 1 else 2
                                } else {
                                    3
                                }
                                if (points.size < minimumVertices) {
                                    diagnostics.fatal(
                                        "refuse:${op.hashCode()}",
                                        "drawPath",
                                        "insufficient_vertices:${points.size}",
                                    )
                                    false
                                } else {
                                    val vertices = selectPathVerticesForCommand(
                                        isStroke = isStroke,
                                        flattened = points,
                                        triangulated = points,
                                    ).flatMap { listOf(it.x, it.y) }
                                    val command = op.toNormalizedCommand(
                                        cmdId,
                                        targets,
                                        vertices,
                                        flattened.contourStarts.ifEmpty { listOf(0) },
                                        points.size,
                                    )
                                    if (command.maskFilter == null) {
                                        dispatchPathDirect(command)
                                    } else when (val blurPlan = planMaskBlur(sourcePlanCommand(command, source))) {
                                        is MaskBlurPlan.Ready -> renderMaskBlur(command, blurPlan)
                                        is MaskBlurPlan.Refused -> {
                                            refuseMaskBlur(command, blurPlan)
                                            false
                                        }
                                        MaskBlurPlan.Identity -> dispatchPathDirect(command)
                                    }
                                }
                            }
                        }
                        is DisplayOp.DrawRRect -> {
                            if (op.paint.isStroke()) {
                                diagnostics.fatal(
                                    "refuse:drawRRect:${cmdId.value}",
                                    "drawRRect",
                                    "stroke_rrect_unimplemented",
                                )
                                false
                            } else {
                                val command = op.toNormalizedCommand(cmdId, targets)
                                if (command.maskFilter == null) {
                                    dispatchRRectDirect(command)
                                    } else when (val blurPlan = planMaskBlur(sourcePlanCommand(command, source))) {
                                    is MaskBlurPlan.Ready -> renderMaskBlur(command, blurPlan)
                                    is MaskBlurPlan.Refused -> {
                                        refuseMaskBlur(command, blurPlan)
                                        false
                                    }
                                    MaskBlurPlan.Identity -> dispatchRRectDirect(command)
                                }
                            }
                        }
                        is DisplayOp.DrawImage -> renderImageCommand(op, cmdId)
                        is DisplayOp.DrawText -> renderTextCommand(op, cmdId)
                        is DisplayOp.DrawVertices -> renderVerticesCommand(
                            vertices = op.vertices,
                            paint = op.paint,
                            transform = op.transform,
                            clip = op.clip,
                            cmdId = cmdId,
                            operationName = "drawVertices",
                        )
                        is DisplayOp.DrawMesh -> {
                            if (op.mesh.program != null) {
                                diagnostics.fatal(
                                    "refuse:drawMesh:${cmdId.value}",
                                    "drawMesh",
                                    "unsupported.mesh.program",
                                )
                                false
                            } else {
                                renderVerticesCommand(
                                    vertices = op.mesh.vertices,
                                    paint = op.paint,
                                    transform = op.transform,
                                    clip = op.clip,
                                    cmdId = cmdId,
                                    operationName = "drawMesh",
                                )
                            }
                        }
                        is DisplayOp.DrawPicture -> {
                            val preflightRefusal = coreRoutePreflightRefusalReason(op)
                            if (preflightRefusal != null) {
                                diagnostics.fatal(
                                    "refuse:drawPicture:${cmdId.value}",
                                    "drawPicture",
                                    preflightRefusal,
                                )
                                false
                            } else {
                            val expanded = mutableListOf<DisplayOp>()
                            fun expand(
                                picture: org.graphiks.kanvas.picture.Picture,
                                outerTransform: Matrix33,
                            ) {
                                for (nested in picture.ops) {
                                    if (nested is DisplayOp.DrawPicture) {
                                        expand(nested.picture, outerTransform * nested.transform)
                                    } else {
                                        expanded += nested.withCombinedTransform(outerTransform)
                                    }
                                }
                            }
                            expand(op.picture, op.transform)
                            var renderedChild = false
                            var allRendered = true
                            for (nested in expanded) {
                                    val nestedCmdId = GPUDrawCommandID(dispatched.size)
                                    val perspectiveRefusal = nested.perspectiveCaptureRefusalReasonOrNull()
                                    if (perspectiveRefusal != null) {
                                        diagnostics.fatal(
                                            "refuse:gpu-perspective-clip:${nestedCmdId.value}",
                                            "gpu",
                                            perspectiveRefusal,
                                        )
                                        allRendered = false
                                        continue
                                    }
                                    val childRendered: Boolean? = when (nested) {
                                        is DisplayOp.DrawRect,
                                        is DisplayOp.DrawRRect,
                                        is DisplayOp.DrawPath,
                                        is DisplayOp.DrawImage,
                                        is DisplayOp.DrawText,
                                        is DisplayOp.DrawColor,
                                        is DisplayOp.Clear,
                                        is DisplayOp.DrawPoint,
                                        is DisplayOp.DrawPoints,
                                        is DisplayOp.DrawDRRect,
                                        is DisplayOp.DrawImageNine,
                                        is DisplayOp.DrawImageLattice,
                                        is DisplayOp.DrawVertices,
                                        is DisplayOp.DrawMesh,
                                        is DisplayOp.DrawAtlas,
                                        is DisplayOp.DrawPicture,
                                        -> executeCoreClipRoute(nested, nestedCmdId, source = false)
                                        is DisplayOp.SetTransform,
                                        is DisplayOp.SetClip,
                                        is DisplayOp.Annotation,
                                        is DisplayOp.FlushAndSnapshot,
                                        -> null
                                        is DisplayOp.BeginLayer -> beginLayer(nested.rec, nested.transform, nestedCmdId)
                                        DisplayOp.EndLayer -> endLayer(nestedCmdId)
                                    }
                                    if (childRendered != null) {
                                        renderedChild = childRendered || renderedChild
                                        allRendered = childRendered && allRendered
                                    }
                            }
                            renderedChild && allRendered
                            }
                        }
                        is DisplayOp.DrawColor -> dispatchRectDirect(op.toNormalizedCommand(cmdId, targets))
                        is DisplayOp.Clear -> dispatchRectDirect(op.toNormalizedCommand(cmdId, targets))
                        is DisplayOp.DrawPoint -> {
                            if (op.paint.isStroke()) {
                                diagnostics.fatal(
                                    "refuse:drawPoint:${cmdId.value}",
                                    "drawPoint",
                                    "stroke_point_unimplemented",
                                )
                                false
                            } else {
                                dispatchRectDirect(op.toNormalizedCommand(cmdId, targets))
                            }
                        }
                        is DisplayOp.DrawPoints -> when (op.mode) {
                            PointMode.POINTS -> {
                                var allRendered = op.points.isNotEmpty()
                                for (point in op.points) {
                                    val command = DisplayOp.DrawPoint(
                                        point.x,
                                        point.y,
                                        op.paint,
                                        op.transform,
                                        op.clip,
                                    ).toNormalizedCommand(GPUDrawCommandID(dispatched.size), targets)
                                    allRendered = dispatchRectDirect(command) && allRendered
                                }
                                allRendered
                            }
                            else -> {
                                val path = op.toPath()
                                val flattened = PathTessellator(
                                    tolerance = config.curveTolerance,
                                    maxVertices = config.maxPathVertices.toInt(),
                                ).flattenWithContours(path.toPathTessellatorData())
                                val points = flattened.points
                                if (points.size < 3) {
                                    diagnostics.fatal(
                                        "refuse:drawPoints:${cmdId.value}",
                                        "drawPoints",
                                        "insufficient_vertices:${points.size}",
                                    )
                                    false
                                } else {
                                    val command = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                        .toNormalizedCommand(
                                            cmdId,
                                            targets,
                                            points.flatMap { listOf(it.x, it.y) },
                                            flattened.contourStarts.ifEmpty { listOf(0) },
                                            points.size,
                                        )
                                        .copy(stroke = op.mode == PointMode.LINES)
                                    dispatchPathDirect(command)
                                }
                            }
                        }
                        is DisplayOp.DrawDRRect -> {
                            if (op.paint.isStroke()) {
                                diagnostics.fatal(
                                    "refuse:drawDRRect:${cmdId.value}",
                                    "drawDRRect",
                                    "stroke_drrect_unimplemented",
                                )
                                false
                            } else {
                                val path = op.toPath()
                                val flattened = PathTessellator(
                                    tolerance = config.curveTolerance,
                                    maxVertices = config.maxPathVertices.toInt(),
                                ).flattenWithContours(path.toPathTessellatorData())
                                val points = flattened.points
                                if (points.size < 3) {
                                    diagnostics.fatal(
                                        "refuse:drawDRRect:${cmdId.value}",
                                        "drawDRRect",
                                        "insufficient_vertices:${points.size}",
                                    )
                                    false
                                } else {
                                    val command = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                        .toNormalizedCommand(
                                            cmdId,
                                            targets,
                                            points.flatMap { listOf(it.x, it.y) },
                                            flattened.contourStarts.ifEmpty { listOf(0) },
                                            points.size,
                                        )
                                    dispatchPathDirect(command)
                                }
                            }
                        }
                        is DisplayOp.DrawImageNine -> {
                            var allRendered = true
                            for (cell in op.decompose()) {
                                val imageCell = DisplayOp.DrawImage(
                                    op.image,
                                    cell.src,
                                    cell.dst,
                                    op.paint,
                                    op.transform,
                                    op.clip,
                                )
                                allRendered = renderImageCommand(imageCell, GPUDrawCommandID(dispatched.size)) && allRendered
                            }
                            allRendered
                        }
                        is DisplayOp.DrawImageLattice -> {
                            var allRendered = true
                            for (cell in op.decompose()) {
                                val paint = if (cell.color != null) {
                                    (op.paint ?: Paint()).copy(
                                        color = cell.color,
                                        blendMode = op.paint?.blendMode ?: org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                                    )
                                } else {
                                    op.paint
                                }
                                val imageCell = DisplayOp.DrawImage(
                                    op.image,
                                    cell.src,
                                    cell.dst,
                                    paint,
                                    op.transform,
                                    op.clip,
                                )
                                allRendered = renderImageCommand(imageCell, GPUDrawCommandID(dispatched.size)) && allRendered
                            }
                            allRendered
                        }
                        is DisplayOp.DrawAtlas -> {
                            var allRendered = true
                            for (index in 0 until minOf(op.transforms.size, op.texRects.size)) {
                                val tint = op.colors?.getOrNull(index)
                                val paint = when {
                                    tint != null && op.paint != null -> op.paint.copy(color = tint)
                                    tint != null -> Paint.fill(tint).copy(blendMode = op.blendMode)
                                    else -> op.paint ?: Paint().copy(blendMode = op.blendMode)
                                }
                                val imageCell = DisplayOp.DrawImage(
                                    op.atlas,
                                    op.texRects[index],
                                    computeAtlasDst(op.texRects[index], op.transform * op.transforms[index]),
                                    paint,
                                    Matrix33.identity(),
                                    op.clip,
                                )
                                allRendered = renderImageCommand(imageCell, GPUDrawCommandID(dispatched.size)) && allRendered
                            }
                            allRendered
                        }
                        else -> error("Core clip route received ${op.javaClass.simpleName}")
                    }
                    rendered = rendered && diagnostics.fatalCount == fatalBefore
                    if (rendered) {
                        if (clipSourceRoute) sourceHasContent = true else sceneHasContent = true
                    }
                } finally {
                    if (source) {
                        rendered = rendered && sourceHasContent
                        sceneLabel = savedSceneLabel
                        sceneHasContent = savedSceneHasContent
                        sourceHasContent = savedSourceHasContent
                        clipSourceRoute = savedClipSourceRoute
                        clipSourcePreservesClip = savedClipSourcePreservesClip
                        clipSourcePlane = savedClipSourcePlane
                    }
                }
                return rendered
            }

            fun refusePerspectiveCapture(op: DisplayOp, cmdId: GPUDrawCommandID): Boolean {
                val reason = op.perspectiveCaptureRefusalReasonOrNull() ?: return false
                diagnostics.fatal(
                    "refuse:gpu-perspective-clip:${cmdId.value}",
                    "gpu",
                    reason,
                )
                return true
            }
            val destinationReadComposer = GPUClipDestinationReadComposer {
                    context,
                    clipMaskLabel,
                    clipScissor,
                    blend,
                    composerDiagnostics,
                    encodeSource,
                ->
                val mode = blend.blendMode
                    ?: if (blend.kind == GPUBlendKind.SrcOver) GPUBlendMode.SRC_OVER else null
                if (mode == null) {
                    composerDiagnostics.fatal(
                        code = "refuse:destination-read:${context.sourceLabelForDiagnostics}",
                        operation = context.sourceLabelForDiagnostics,
                        reason = "unsupported.destination_read.gpu_formula:${blend.modeLabel}",
                        facts = listOf(
                            DiagnosticFact("destination-read.source", context.sourceLabel),
                            DiagnosticFact("destination-read.snapshot", snapLabel),
                            DiagnosticFact("destination-read.mode", blend.modeLabel),
                            DiagnosticFact(
                                "clip.strategy",
                                if (clipMaskLabel == null) "direct" else "alpha-mask",
                            ),
                            DiagnosticFact("destination-read.action", "refuse-before-source"),
                        ),
                    )
                    false
                } else if (!encodeSource()) {
                    false
                } else {
                    val rendered = t.renderDestinationReadBlend(
                        sceneLabel = context.sceneLabel,
                        sceneHasContent = sceneHasContent,
                        sourceSurface = context.sourceSurface,
                        snapshotLabel = snapLabel,
                        combinedCoverageLabel = combinedCoverageLabel,
                        clipMaskLabel = clipMaskLabel,
                        mode = mode,
                        colorFormat = context.colorFormat,
                        width = context.targetWidth,
                        height = context.targetHeight,
                    )
                    if (rendered && (blend.requiresDestinationRead || context.forceSourceComposition)) {
                        composerDiagnostics.degrade(
                            code = "route:destination-read:${context.sourceLabelForDiagnostics}",
                            operation = context.sourceLabelForDiagnostics,
                            reason = "gpu-copy-then-formula",
                            facts = listOf(
                                DiagnosticFact("destination-read.source", context.sourceLabel),
                                DiagnosticFact("destination-read.snapshot", snapLabel),
                                DiagnosticFact("destination-read.mode", blend.modeLabel),
                                DiagnosticFact(
                                    "clip.strategy",
                                    if (clipMaskLabel == null) "direct" else "alpha-mask",
                                ),
                                DiagnosticFact("destination-read.action", "copy-then-formula"),
                            ),
                        )
                    }
                    rendered
                }
            }
            for (op in ops) {
                val cmdId = GPUDrawCommandID(dispatched.size)
                maskBlurSourceBounds = null
                if (op is DisplayOp.BeginLayer) {
                    beginLayer(op.rec, op.transform, cmdId)
                    continue
                }
                if (op is DisplayOp.EndLayer) {
                    endLayer(cmdId)
                    continue
                }
                if (suppressedLayerDepth > 0) continue
                if (refusePerspectiveCapture(op, cmdId)) continue
                if (refuseCoreRoutePreflight(op, cmdId)) continue
                val requestedClipPlan = op.gpuClipCoveragePlanOrNull(targets, config, t.maxTextureDimension2D)
                if (requestedClipPlan is GPUClipCoveragePlan.Refused) {
                    diagnostics.fatal("refuse:${op.javaClass.simpleName}:${cmdId.value}", "clip", requestedClipPlan.code)
                    continue
                }
                val clipPlan = requestedClipPlan ?: GPUClipCoveragePlan.NoClip
                val blend = op.clipCompositeBlendFacts()
                val hasActiveMaskBlur = op.hasActiveMaskBlur()
                val requiresSgComposition =
                    blend.requiresDestinationRead ||
                        clipPlan is GPUClipCoveragePlan.Mask ||
                        (op.requiresSeparateGeometryCoverage() && !hasActiveMaskBlur)
                op.coveragePlaneTask4RefusalOrNull()?.takeIf { requiresSgComposition }?.let { refusal ->
                    diagnostics.fatal(
                        code = "refuse:coverage-plane:${op.javaClass.simpleName}:${cmdId.value}",
                        operation = op.javaClass.simpleName,
                        reason = refusal,
                    )
                    continue
                }
                val routeContext = GPUClipRouteContext(
                    sceneLabel = sceneLabel,
                    sourceSurface = GPUClipSourceSurface(
                        colorLabel = srcLabel,
                        geometryCoverageLabel = geometryCoverageLabel,
                    ),
                    sourceLabelForDiagnostics = "${op.javaClass.simpleName}:${cmdId.value}",
                    targetWidth = width,
                    targetHeight = height,
                    colorFormat = texFormat,
                    config = config,
                    frameCache = clipFrameCache,
                    destinationReadComposer = destinationReadComposer,
                    trace = routeTrace,
                    forceSourceComposition = hasActiveMaskBlur,
                    coverageCompositionRequired = op.requiresSeparateGeometryCoverage() || hasActiveMaskBlur,
                    sourceCompositeBounds = { maskBlurSourceBounds },
                )
                if (blend.requiresDestinationRead) {
                    val rendered = t.renderWithClip(
                        context = routeContext,
                        clipPlan = clipPlan,
                        blend = blend,
                        diagnostics = diagnostics,
                        encodeDirect = { false },
                        encodeSource = {
                            executeCoreClipRoute(
                                op,
                                cmdId,
                                source = true,
                                preserveClipInSource = clipPlan is GPUClipCoveragePlan.Scissor,
                                sourcePlane = GPUClipSourcePlane.Color,
                            ) && executeCoreClipRoute(
                                op,
                                cmdId,
                                source = true,
                                preserveClipInSource = clipPlan is GPUClipCoveragePlan.Scissor,
                                sourcePlane = GPUClipSourcePlane.GeometryCoverage,
                            )
                        },
                    )
                    if (rendered) {
                        sceneHasContent = true
                        dispatched.add(cmdId.toString())
                        diagnostics.degrade(
                            "dispatch:${op.javaClass.simpleName}:${cmdId.value}",
                            op.javaClass.simpleName,
                            "dispatched",
                        )
                    }
                    continue
                }
                if (
                    op is DisplayOp.DrawRect ||
                    op is DisplayOp.DrawPath ||
                    op is DisplayOp.DrawRRect ||
                    op is DisplayOp.DrawImage ||
                    op is DisplayOp.DrawText ||
                    op is DisplayOp.DrawColor ||
                    op is DisplayOp.Clear ||
                    op is DisplayOp.DrawPoint ||
                    op is DisplayOp.DrawPoints ||
                    op is DisplayOp.DrawDRRect ||
                    op is DisplayOp.DrawImageNine ||
                    op is DisplayOp.DrawImageLattice ||
                    op is DisplayOp.DrawPicture ||
                    op is DisplayOp.DrawVertices ||
                    op is DisplayOp.DrawMesh ||
                    op is DisplayOp.DrawAtlas
                ) {
                    val rendered = t.renderWithClip(
                        context = routeContext,
                        clipPlan = clipPlan,
                        blend = blend,
                        diagnostics = diagnostics,
                        encodeDirect = { executeCoreClipRoute(op, cmdId, source = false) },
                        encodeSource = {
                            executeCoreClipRoute(
                                op,
                                cmdId,
                                source = true,
                                preserveClipInSource = clipPlan is GPUClipCoveragePlan.Scissor,
                                sourcePlane = GPUClipSourcePlane.Color,
                            ) && executeCoreClipRoute(
                                op,
                                cmdId,
                                source = true,
                                preserveClipInSource = clipPlan is GPUClipCoveragePlan.Scissor,
                                sourcePlane = GPUClipSourcePlane.GeometryCoverage,
                            )
                        },
                    )
                    if (rendered &&
                        (
                            clipPlan is GPUClipCoveragePlan.Mask ||
                                routeContext.forceSourceComposition ||
                                routeContext.coverageCompositionRequired
                            )
                    ) {
                        sceneHasContent = true
                        dispatched.add(cmdId.toString())
                        if (blend.requiresDestinationRead || routeContext.forceSourceComposition) {
                            diagnostics.degrade(
                                "dispatch:${op.javaClass.simpleName}:${cmdId.value}",
                                op.javaClass.simpleName,
                                "dispatched",
                            )
                        }
                    }
                    continue
                }
                // Every visual DisplayOp above has one explicit clip-aware source route. Keep the
                // legacy block below unreachable so adding a new DisplayOp cannot silently regain
                // a direct complex-clip path before its source encoder and inventory test exist.
                when (op) {
                    is DisplayOp.SetTransform,
                    is DisplayOp.SetClip,
                    is DisplayOp.Annotation,
                    is DisplayOp.FlushAndSnapshot,
                    -> continue
                    is DisplayOp.BeginLayer,
                    DisplayOp.EndLayer,
                    -> continue
                }
                val sourceThenComposite = clipPlan is GPUClipCoveragePlan.Mask
                val destinationLabel = sceneLabel
                val destinationHasContent = sceneHasContent
                if (sourceThenComposite) {
                    sceneLabel = srcLabel
                    sceneHasContent = false
                    clipSourceRoute = true
                }
                try {
                    when (op) {
                    is DisplayOp.DrawRect -> {
                        val rendered = if (op.paint.isStroke()) {
                            val cmd = op.toStrokePathCommand(cmdId, targets)
                            if (cmd.maskFilter == null) {
                                if (cmd.blend.requiresDestinationRead) {
                                    diagnostics.fatal("refuse:drawRect:${cmdId.value}", "drawRect", "unsupported_blend:advanced")
                                    false
                                } else {
                                    dispatchPathDirect(cmd)
                                }
                            } else when (val plan = planMaskBlur(cmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(cmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> {
                                    if (cmd.blend.requiresDestinationRead) {
                                        diagnostics.fatal("refuse:drawRect:${cmdId.value}", "drawRect", "unsupported_blend:advanced")
                                        false
                                    } else {
                                        dispatchPathDirect(cmd)
                                    }
                                }
                            }
                        } else {
                            val cmd = op.toNormalizedCommand(cmdId, targets)
                            if (cmd.maskFilter == null) {
                                dispatchRectDirect(cmd)
                            } else when (val plan = planMaskBlur(cmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(cmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> dispatchRectDirect(cmd)
                            }
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawPath -> {
                        val paint = op.paint
                        val isStroke = paint.isStroke()
                        val pathRect = Rect(0f, 0f, 0f, 0f)
                        if (!isStroke &&
                            op.transform == Matrix33.identity() &&
                            op.path.fillType in setOf(FillType.WINDING, FillType.EVEN_ODD) &&
                            op.path.isRect(pathRect)
                        ) {
                            val rectCmd = DisplayOp.DrawRect(pathRect, paint, op.transform, op.clip)
                                .toNormalizedCommand(cmdId, targets)
                            val rendered = if (rectCmd.maskFilter == null) {
                                dispatchRectDirect(rectCmd)
                            } else when (val plan = planMaskBlur(rectCmd)) {
                                is MaskBlurPlan.Ready -> renderMaskBlur(rectCmd, plan)
                                is MaskBlurPlan.Refused -> {
                                    refuseMaskBlur(rectCmd, plan)
                                    false
                                }
                                MaskBlurPlan.Identity -> dispatchRectDirect(rectCmd)
                            }
                            sceneHasContent = sceneHasContent || rendered
                            continue
                        }
                        val pathData = op.path.toPathTessellatorData()
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        val allowsDegenerateRoundStroke = isStroke && paint.strokeCap.name.lowercase() == "round"
                        val minVertices = if (isStroke) {
                            if (allowsDegenerateRoundStroke) 1 else 2
                        } else {
                            3
                        }
                        if (flat.size < minVertices) {
                            diagnostics.fatal("refuse:${op.hashCode()}", "drawPath", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val vertices = selectPathVerticesForCommand(
                            isStroke = isStroke,
                            flattened = flat,
                            triangulated = flat,
                        ).flatMap { listOf(it.x, it.y) }
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                        val cmd = op.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                        val rendered = if (cmd.maskFilter == null) {
                            dispatchPathDirect(cmd)
                        } else when (val plan = planMaskBlur(cmd)) {
                            is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                            is MaskBlurPlan.Refused -> {
                                refuseMaskBlur(cmd, plan)
                                false
                            }
                            MaskBlurPlan.Identity -> dispatchPathDirect(cmd)
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawRRect -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawRRect:${cmdId.value}", "drawRRect", "stroke_rrect_unimplemented")
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        val rendered = if (cmd.maskFilter == null) {
                            dispatchRRectDirect(cmd)
                        } else when (val plan = planMaskBlur(cmd)) {
                            is MaskBlurPlan.Ready -> renderMaskBlur(cmd, plan)
                            is MaskBlurPlan.Refused -> {
                                refuseMaskBlur(cmd, plan)
                                false
                            }
                            MaskBlurPlan.Identity -> dispatchRRectDirect(cmd)
                        }
                        sceneHasContent = sceneHasContent || rendered
                    }
                    is DisplayOp.DrawImage -> {
                        val cmd = op.toImageRectCommand(cmdId, targets)
                        renderImageColorCommand(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawText -> {
                        if (op.paint.shader != null && extractSolidShaderColor(op.paint.shader) == null) {
                            renderShaderText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        if (hasColorGlyphs(op.blob)) {
                            renderColorText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        if (op.paint.isStroke()) {
                            renderShaderText(op, cmdId)
                            sceneHasContent = true
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawText:${cmdId.value}", "drawText", "unsupported_blend:advanced")
                            continue
                        }
                        val ctmScale = ctmEffectiveScale(op.transform)
                        val rasterBlob = op.blob.scaledForRasterization(ctmScale)
                        var gpuBlob = TextBridge.rasterize(rasterBlob)
                        if (gpuBlob != null) {
                            gpuBlob = gpuBlob.normalizeGlyphRects(ctmScale)
                            t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                drawTextAtlasPass(
                                    gpuBlob,
                                    cmd.blend.blendMode,
                                    dispatched,
                                    diagnostics,
                                    textColor = resolveTextColor(op.paint),
                                    targetWidth = width,
                                    targetHeight = height,
                                    drawOriginX = op.x,
                                    drawOriginY = op.y,
                                    transform = op.transform,
                                )
                            }
                            sceneHasContent = true
                        } else {
                            diagnostics.degrade("degrade:drawText:${cmdId.value}", "drawText", "rasterize_failed")
                        }
                    }
                    is DisplayOp.SetTransform,
                    is DisplayOp.SetClip -> { /* state ops */ }
                    is DisplayOp.DrawColor -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawColor:${cmdId.value}", "drawColor", "unsupported_blend:advanced")
                            continue
                        }
                        dispatchRectDirect(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.Clear -> {
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        dispatchRectDirect(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawPoint -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawPoint:${cmdId.value}", "drawPoint", "stroke_point_unimplemented")
                            continue
                        }
                        val cmd = op.toNormalizedCommand(cmdId, targets)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawPoint:${cmdId.value}", "drawPoint", "unsupported_blend:advanced")
                            continue
                        }
                        dispatchRectDirect(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawPoints -> {
                        when (op.mode) {
                            PointMode.POINTS -> {
                                for (pt in op.points) {
                                    val subCmdId = GPUDrawCommandID(dispatched.size)
                                    val subOp = DisplayOp.DrawPoint(pt.x, pt.y, op.paint, op.transform, op.clip)
                                    val cmd = subOp.toNormalizedCommand(subCmdId, targets)
                                    dispatchRectDirect(cmd)
                                    sceneHasContent = true
                                }
                            }
                            else -> {
                                val path = op.toPath()
                                val pathData = path.toPathTessellatorData()
                                val tessellator = PathTessellator(
                                    tolerance = config.curveTolerance,
                                    maxVertices = config.maxPathVertices.toInt(),
                                )
                                val flattened = tessellator.flattenWithContours(pathData)
                                val flat = flattened.points
                                if (flat.size < 3) {
                                    diagnostics.fatal("refuse:drawPoints:${cmdId.value}", "drawPoints", "insufficient_vertices:${flat.size}")
                                    continue
                                }
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                val isStroke = op.mode == PointMode.LINES
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(
                                    cmdId, targets, vertices, contourStarts, flat.size,
                                ).copy(stroke = isStroke)
                                if (cmd.blend.requiresDestinationRead) {
                                    diagnostics.fatal("refuse:drawPoints:${cmdId.value}", "drawPoints", "unsupported_blend:advanced")
                                    continue
                                }
                                dispatchPathDirect(cmd)
                                sceneHasContent = true
                            }
                        }
                    }
                    is DisplayOp.DrawDRRect -> {
                        if (op.paint.isStroke()) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "stroke_drrect_unimplemented")
                            continue
                        }
                        val path = op.toPath()
                        val pathData = path.toPathTessellatorData()
                        val tessellator = PathTessellator(
                            tolerance = config.curveTolerance,
                            maxVertices = config.maxPathVertices.toInt(),
                        )
                        val flattened = tessellator.flattenWithContours(pathData)
                        val flat = flattened.points
                        if (flat.size < 3) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "insufficient_vertices:${flat.size}")
                            continue
                        }
                        val vertices = flat.flatMap { listOf(it.x, it.y) }
                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                        val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                        val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                        if (cmd.blend.requiresDestinationRead) {
                            diagnostics.fatal("refuse:drawDRRect:${cmdId.value}", "drawDRRect", "unsupported_blend:advanced")
                            continue
                        }
                        dispatchPathDirect(cmd)
                        sceneHasContent = true
                    }
                    is DisplayOp.DrawImageNine -> {
                        val cells = op.decompose()
                        for (cell in cells) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val subOp = DisplayOp.DrawImage(
                                image = op.image,
                                src = cell.src,
                                dst = cell.dst,
                                paint = op.paint,
                                transform = op.transform,
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            renderImageColorCommand(cmd)
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.DrawImageLattice -> {
                        val cells = op.decompose()
                        for (cell in cells) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val subPaint = if (cell.color != null) {
                                val c = cell.color
                                (op.paint ?: org.graphiks.kanvas.paint.Paint()).copy(
                                    color = c,
                                    blendMode = op.paint?.blendMode ?: org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                                )
                            } else op.paint
                            val subOp = DisplayOp.DrawImage(
                                image = op.image,
                                src = cell.src,
                                dst = cell.dst,
                                paint = subPaint,
                                transform = op.transform,
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            renderImageColorCommand(cmd)
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.DrawPicture -> {
                        val expanded = mutableListOf<DisplayOp>()
                        fun expand(pic: org.graphiks.kanvas.picture.Picture, outerXform: Matrix33) {
                            for (nested in pic.ops) {
                                val combined = when (nested) {
                                    is DisplayOp.DrawPicture -> {
                                        expand(nested.picture, outerXform * nested.transform)
                                        continue
                                    }
                                    else -> nested.withCombinedTransform(outerXform)
                                }
                                expanded.add(combined)
                            }
                        }
                        expand(op.picture, op.transform)
                        if (expanded.any { it is DisplayOp.BeginLayer || it is DisplayOp.EndLayer }) {
                            diagnostics.fatal(
                                "refuse:drawPicture:${cmdId.value}",
                                "drawPicture",
                                "unsupported.picture.save_layer",
                            )
                            continue
                        }
                        for (nestedOp in expanded) {
                            val nestedCmdId = GPUDrawCommandID(dispatched.size)
                            if (refusePerspectiveCapture(nestedOp, nestedCmdId)) continue
                            when (nestedOp) {
                                is DisplayOp.DrawRect -> {
                                    if (nestedOp.paint.isStroke()) {
                                        val cmd = nestedOp.toStrokePathCommand(nestedCmdId, targets)
                                        dispatchPathDirect(cmd)
                                    } else {
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                        dispatchRectDirect(cmd)
                                    }
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawRRect -> {
                                    if (!nestedOp.paint.isStroke()) {
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                        dispatchRRectDirect(cmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawPath -> {
                                    val paint = nestedOp.paint
                                    val isStroke = paint.isStroke()
                                    val pd = nestedOp.path.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    val allowsDegenerateRoundStroke = isStroke && paint.strokeCap.name.lowercase() == "round"
                                    val minVertices = if (isStroke) {
                                        if (allowsDegenerateRoundStroke) 1 else 2
                                    } else {
                                        3
                                    }
                                    if (fl.size >= minVertices) {
                                        val verts = selectPathVerticesForCommand(
                                            isStroke = isStroke,
                                            flattened = fl,
                                            triangulated = fl,
                                        ).flatMap { listOf(it.x, it.y) }
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size)
                                        dispatchPathDirect(cmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawColor -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    dispatchRectDirect(cmd)
                                    sceneHasContent = true
                                }
                                is DisplayOp.Clear -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    dispatchRectDirect(cmd)
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawPoint -> {
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    dispatchRectDirect(cmd)
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawPoints -> {
                                    val p = nestedOp.toPath()
                                    val pd = p.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    if (fl.size >= 3) {
                                        val verts = fl.flatMap { listOf(it.x, it.y) }
                                        val isStroke = nestedOp.mode == PointMode.LINES
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size).copy(stroke = isStroke)
                                        dispatchPathDirect(cmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawDRRect -> {
                                    val p = nestedOp.toPath()
                                    val pd = p.toPathTessellatorData()
                                    val tess = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                                    val flattened = tess.flattenWithContours(pd)
                                    val fl = flattened.points
                                    if (fl.size >= 3) {
                                        val verts = fl.flatMap { listOf(it.x, it.y) }
                                        val dpOp = DisplayOp.DrawPath(p, nestedOp.paint, nestedOp.transform, nestedOp.clip)
                                        val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                        val cmd = dpOp.toNormalizedCommand(nestedCmdId, targets, verts, contourStarts, fl.size)
                                        dispatchPathDirect(cmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawImage -> {
                                    val imgCmd = nestedOp.toImageRectCommand(nestedCmdId, targets)
                                    renderImageColorCommand(imgCmd)
                                    sceneHasContent = true
                                }
                                is DisplayOp.DrawImageNine -> {
                                    val cells = nestedOp.decompose()
                                    for (cell in cells) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.image,
                                            src = cell.src,
                                            dst = cell.dst,
                                            paint = nestedOp.paint,
                                            transform = nestedOp.transform,
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        renderImageColorCommand(imgCmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawImageLattice -> {
                                    val cells = nestedOp.decompose()
                                    for (cell in cells) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val subPaint = if (cell.color != null) {
                                            val c = cell.color
                                            (nestedOp.paint ?: org.graphiks.kanvas.paint.Paint()).copy(
                                                color = c,
                                                blendMode = nestedOp.paint?.blendMode ?: org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                                            )
                                        } else nestedOp.paint
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.image,
                                            src = cell.src,
                                            dst = cell.dst,
                                            paint = subPaint,
                                            transform = nestedOp.transform,
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        renderImageColorCommand(imgCmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawAtlas -> {
                                    val numSprites = minOf(nestedOp.transforms.size, nestedOp.texRects.size)
                                    for (i in 0 until numSprites) {
                                        val subCmdId = GPUDrawCommandID(dispatched.size)
                                        val spriteXform = nestedOp.transform * nestedOp.transforms[i]
                                        val texRect = nestedOp.texRects[i]
                                        val tint = nestedOp.colors?.getOrNull(i)
                                        val subPaint = when {
                                            tint != null && nestedOp.paint != null -> nestedOp.paint.copy(color = tint)
                                            tint != null -> org.graphiks.kanvas.paint.Paint.fill(tint)
                                                .copy(blendMode = nestedOp.blendMode)
                                            else -> nestedOp.paint ?: org.graphiks.kanvas.paint.Paint()
                                                .copy(blendMode = nestedOp.blendMode)
                                        }
                                        val screenDst = computeAtlasDst(texRect, spriteXform)
                                        val subOp = DisplayOp.DrawImage(
                                            image = nestedOp.atlas,
                                            src = texRect,
                                            dst = screenDst,
                                            paint = subPaint,
                                            transform = Matrix33.identity(),
                                            clip = nestedOp.clip,
                                        )
                                        val imgCmd = subOp.toImageRectCommand(subCmdId, targets)
                                        renderImageColorCommand(imgCmd)
                                        sceneHasContent = true
                                    }
                                }
                                is DisplayOp.DrawVertices -> {
                                    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_vertices_unimplemented")
                                }
                                is DisplayOp.DrawMesh -> {
                                    diagnostics.degrade("unimplemented:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "gpu_nested_mesh_unimplemented")
                                }
                                is DisplayOp.DrawText -> {
                                    if (nestedOp.paint.shader != null && extractSolidShaderColor(nestedOp.paint.shader) == null) {
                                        renderShaderText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    if (hasColorGlyphs(nestedOp.blob)) {
                                        renderColorText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    if (nestedOp.paint.isStroke()) {
                                        renderShaderText(nestedOp, nestedCmdId)
                                        sceneHasContent = true
                                        continue
                                    }
                                    val cmd = nestedOp.toNormalizedCommand(nestedCmdId, targets)
                                    if (cmd.blend.requiresDestinationRead) {
                                        diagnostics.fatal("refuse:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "unsupported_blend:advanced")
                                        continue
                                    }
                                    val ctmScale = ctmEffectiveScale(nestedOp.transform)
                                    val rasterBlob = nestedOp.blob.scaledForRasterization(ctmScale)
                                    var gpuBlob = TextBridge.rasterize(rasterBlob)
                                    if (gpuBlob != null) {
                                        gpuBlob = gpuBlob.normalizeGlyphRects(ctmScale)
                                        t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                            drawTextAtlasPass(
                                                gpuBlob,
                                                cmd.blend.blendMode,
                                                dispatched,
                                                diagnostics,
                                                textColor = resolveTextColor(nestedOp.paint),
                                                targetWidth = width,
                                                targetHeight = height,
                                                drawOriginX = nestedOp.x,
                                                drawOriginY = nestedOp.y,
                                                transform = nestedOp.transform,
                                            )
                                        }
                                        sceneHasContent = true
                                    } else {
                                        diagnostics.degrade("degrade:drawPicture:nested:${nestedCmdId.value}", "drawPicture", "nested_text_rasterize_failed")
                                    }
                                }
                                is DisplayOp.SetTransform,
                                is DisplayOp.SetClip,
                                is DisplayOp.BeginLayer,
                                is DisplayOp.EndLayer,
                                is DisplayOp.Annotation,
                                is DisplayOp.FlushAndSnapshot -> { /* state / metadata ops */ }
                                is DisplayOp.DrawPicture -> {
                                    /* already flattened; should not occur */
                                }
                            }
                        }
                    }
                    is DisplayOp.DrawVertices -> {
                        val verts = op.vertices
                        if (verts.texCoords != null) {
                            val tex = verts.texCoords
                            val idx = verts.indices?.toIntArray()
                                ?: IntArray(verts.positions.size) { it }
                            val posFlat = FloatArray(verts.positions.size * 2) {
                                if (it % 2 == 0) verts.positions[it / 2].x else verts.positions[it / 2].y
                            }
                            val uvFlat = FloatArray(tex.size * 2) {
                                if (it % 2 == 0) tex[it / 2].x else tex[it / 2].y
                            }
                            val paint = op.paint

                            if (paint.shader is Shader.Image) {
                                val img = paint.shader.image
                                val texBytes = img.pixels
                                val texW = img.width
                                val texH = img.height
                                if (texBytes != null) {
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchTexturedVertices(
                                            positions = posFlat, uvs = uvFlat, uvs2 = null,
                                            indices = idx, paint = paint,
                                            textureBytes = texBytes,
                                            textureWidth = texW, textureHeight = texH,
                                            textureSourceId = img.sourceId,
                                            diagnostics = diagnostics,
                                            surfaceWidth = width, surfaceHeight = height,
                                            config = config, diagnosticName = op.paint.toString(),
                                        )
                                    }
                                    sceneHasContent = true
                                    continue
                                } else {
                                    diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_pixels")
                                }
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_image_shader")
                            }
                            continue
                        }
                        if (verts.positions.size >= 3) {
                            val path = Path().also { p ->
                                when (verts.mode) {
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLES -> {
                                        var i = 0
                                        while (i + 2 < verts.positions.size) {
                                            p.moveTo(verts.positions[i].x, verts.positions[i].y)
                                            p.lineTo(verts.positions[i + 1].x, verts.positions[i + 1].y)
                                            p.lineTo(verts.positions[i + 2].x, verts.positions[i + 2].y)
                                            p.close()
                                            i += 3
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_STRIP -> {
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(verts.positions[j - 2].x, verts.positions[j - 2].y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_FAN -> {
                                        val first = verts.positions.first()
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(first.x, first.y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                }
                            }
                            val pathData = path.toPathTessellatorData()
                            val tessellator = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                            val flattened = tessellator.flattenWithContours(pathData)
                            val flat = flattened.points
                            if (flat.size >= 3) {
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                                dispatchPathDirect(cmd)
                                sceneHasContent = true
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:insufficient:${cmdId.value}", "drawVertices", "insufficient_vertices:${flat.size}")
                            }
                        }
                    }
                    is DisplayOp.DrawMesh -> {
                        val verts = op.mesh.vertices
                        if (verts.texCoords != null) {
                            val tex = verts.texCoords
                            val idx = verts.indices?.toIntArray()
                                ?: IntArray(verts.positions.size) { it }
                            val posFlat = FloatArray(verts.positions.size * 2) {
                                if (it % 2 == 0) verts.positions[it / 2].x else verts.positions[it / 2].y
                            }
                            val uvFlat = FloatArray(tex.size * 2) {
                                if (it % 2 == 0) tex[it / 2].x else tex[it / 2].y
                            }
                            val paint = op.paint

                            if (paint.shader is Shader.Image) {
                                val img = paint.shader.image
                                val texBytes = img.pixels
                                val texW = img.width
                                val texH = img.height
                                if (texBytes != null) {
                                    t.encodeOffscreenTexture(sceneLabel, sceneClear()) {
                                        dispatchTexturedVertices(
                                            positions = posFlat, uvs = uvFlat, uvs2 = null,
                                            indices = idx, paint = paint,
                                            textureBytes = texBytes,
                                            textureWidth = texW, textureHeight = texH,
                                            textureSourceId = img.sourceId,
                                            diagnostics = diagnostics,
                                            surfaceWidth = width, surfaceHeight = height,
                                            config = config, diagnosticName = op.paint.toString(),
                                        )
                                    }
                                    sceneHasContent = true
                                    continue
                                } else {
                                    diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_pixels")
                                }
                            } else {
                                diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_no_image_shader")
                            }
                            continue
                        }
                        if (verts.positions.size >= 3) {
                            val path = Path().also { p ->
                                when (verts.mode) {
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLES -> {
                                        var i = 0
                                        while (i + 2 < verts.positions.size) {
                                            p.moveTo(verts.positions[i].x, verts.positions[i].y)
                                            p.lineTo(verts.positions[i + 1].x, verts.positions[i + 1].y)
                                            p.lineTo(verts.positions[i + 2].x, verts.positions[i + 2].y)
                                            p.close()
                                            i += 3
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_STRIP -> {
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(verts.positions[j - 2].x, verts.positions[j - 2].y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                    org.graphiks.kanvas.types.VertexMode.TRIANGLE_FAN -> {
                                        val first = verts.positions.first()
                                        for (j in 2 until verts.positions.size) {
                                            p.moveTo(first.x, first.y)
                                            p.lineTo(verts.positions[j - 1].x, verts.positions[j - 1].y)
                                            p.lineTo(verts.positions[j].x, verts.positions[j].y)
                                            p.close()
                                        }
                                    }
                                }
                            }
                            val pathData = path.toPathTessellatorData()
                            val tessellator = PathTessellator(config.curveTolerance, config.maxPathVertices.toInt())
                            val flattened = tessellator.flattenWithContours(pathData)
                            val flat = flattened.points
                            if (flat.size >= 3) {
                                val vertices = flat.flatMap { listOf(it.x, it.y) }
                                val contourStarts = flattened.contourStarts.ifEmpty { listOf(0) }
                                val drawPathOp = DisplayOp.DrawPath(path, op.paint, op.transform, op.clip)
                                val cmd = drawPathOp.toNormalizedCommand(cmdId, targets, vertices, contourStarts, flat.size)
                                dispatchPathDirect(cmd)
                                sceneHasContent = true
                            } else {
                                diagnostics.degrade("unimplemented:drawMesh:insufficient:${cmdId.value}", "drawMesh", "insufficient_vertices:${flat.size}")
                            }
                        }
                    }
                    is DisplayOp.DrawAtlas -> {
                        val numSprites = minOf(op.transforms.size, op.texRects.size)
                        for (i in 0 until numSprites) {
                            val subCmdId = GPUDrawCommandID(dispatched.size)
                            val spriteXform = op.transform * op.transforms[i]
                            val texRect = op.texRects[i]
                            val tint = op.colors?.getOrNull(i)
                            val subPaint = when {
                                tint != null && op.paint != null -> op.paint.copy(color = tint)
                                tint != null -> org.graphiks.kanvas.paint.Paint.fill(tint).copy(blendMode = op.blendMode)
                                else -> op.paint ?: org.graphiks.kanvas.paint.Paint().copy(blendMode = op.blendMode)
                            }
                            val screenDst = computeAtlasDst(texRect, spriteXform)
                            val subOp = DisplayOp.DrawImage(
                                image = op.atlas,
                                src = texRect,
                                dst = screenDst,
                                paint = subPaint,
                                transform = Matrix33.identity(),
                                clip = op.clip,
                            )
                            val cmd = subOp.toImageRectCommand(subCmdId, targets)
                            renderImageColorCommand(cmd)
                            sceneHasContent = true
                        }
                    }
                    is DisplayOp.Annotation -> { /* no visual output */ }
                    is DisplayOp.FlushAndSnapshot -> { /* deferred to render-backend; no-op in CPU path */ }
                    else -> error("unreachable legacy GPU route")
                    }
                } finally {
                    if (sourceThenComposite) {
                        val sourceHasContent = sceneHasContent
                        sceneLabel = destinationLabel
                        sceneHasContent = destinationHasContent
                        clipSourceRoute = false
                        if (sourceHasContent && t.renderWithClip(
                                context = routeContext,
                                clipPlan = clipPlan,
                                blend = blend,
                                diagnostics = diagnostics,
                                encodeDirect = { false },
                                encodeSource = { true },
                            )
                        ) {
                            sceneHasContent = true
                            dispatched.add(cmdId.toString())
                            diagnostics.degrade(
                                "dispatch:${op.javaClass.simpleName}:${cmdId.value}",
                                op.javaClass.simpleName,
                                "dispatched",
                            )
                        }
                    }
                }
            }

            if (suppressedLayerDepth > 0) {
                diagnostics.fatal(
                    "refuse:saveLayer:suppressed-unbalanced",
                    "saveLayer",
                    "unsupported.layer.unbalanced_begin",
                )
            }
            while (layerStack.isNotEmpty()) {
                diagnostics.fatal(
                    "refuse:saveLayer:unbalanced:${layerStack.size}",
                    "saveLayer",
                    "unsupported.layer.unbalanced_begin",
                )
                val parent = layerStack.removeLast()
                sceneLabel = parent.label
                sceneHasContent = parent.hasContent
            }

            // Composite final: scene -> main target for readback
            t.encode(clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                drawCompositePass(
                    wgsl = COPY_WGSL,
                    colorFormat = texFormat,
                    textureLabel = sceneLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = ByteArray(16),
                            scissorX = 0, scissorY = 0,
                            scissorWidth = width, scissorHeight = height,
                        ),
                    ),
                )
            }

            val rgba = t.readRgba()
            return RenderResult(
                pixels = rgba.toUByteArray(),
                width = width,
                height = height,
                format = format,
                diagnostics = diagnostics,
                stats = RenderStats(
                    opsDispatched = dispatched.size,
                    opsRefused = diagnostics.fatalCount,
                    pipelineCount = 1,
                    drawCallCount = dispatched.size,
                    coverage = if (dispatched.isNotEmpty()) 1f else 0f,
                ),
            )
        }
    }
}

/** Transform a source rectangle through an affine matrix to obtain screen-space destination bounds. */
private fun computeAtlasDst(texRect: Rect, xform: Matrix33): Rect {
    val x0 = xform.scaleX * texRect.left + xform.skewX * texRect.top + xform.transX
    val y0 = xform.skewY * texRect.left + xform.scaleY * texRect.top + xform.transY
    val x1 = xform.scaleX * texRect.right + xform.skewX * texRect.top + xform.transX
    val y1 = xform.skewY * texRect.right + xform.scaleY * texRect.top + xform.transY
    val x2 = xform.scaleX * texRect.right + xform.skewX * texRect.bottom + xform.transX
    val y2 = xform.skewY * texRect.right + xform.scaleY * texRect.bottom + xform.transY
    val x3 = xform.scaleX * texRect.left + xform.skewX * texRect.bottom + xform.transX
    val y3 = xform.skewY * texRect.left + xform.scaleY * texRect.bottom + xform.transY
    val l = minOf(x0, x1, x2, x3)
    val t = minOf(y0, y1, y2, y3)
    val r = maxOf(x0, x1, x2, x3)
    val b = maxOf(y0, y1, y2, y3)
    return Rect.fromLTRB(l, t, r, b)
}

internal fun hasColorGlyphs(blob: TextBlob): Boolean {
    val tf = blob.typeface as? FontTypeface ?: return false
    val scaler = tf.scaler ?: return false
    // Shortcut: check table presence instead of scaling every glyph
    if (!scaler.hasAnyColorTable) return false
    for (run in blob.glyphRuns) {
        for (gid in run.glyphs) {
            val rep = scaler.getGlyphRepresentation(gid.toInt(), blob.fontSize)
            if (rep is GlyphRepresentation.ColorLayers || rep is GlyphRepresentation.Bitmap || rep is GlyphRepresentation.ColorLayersV1) return true
        }
    }
    return false
}

internal data class TextAtlasMesh(
    val vertexData: FloatArray,
    val indexData: IntArray,
)

internal fun buildTextAtlasMesh(
    gpuBlob: GpuTextBlob,
    drawOriginX: Float = 0f,
    drawOriginY: Float = 0f,
    transform: Matrix33? = null,
): TextAtlasMesh {
    val uvs = gpuBlob.glyphUvs
    val vertexData = mutableListOf<Float>()
    val indexData = mutableListOf<Int>()
    var glyphIndex = 0
    var quadIndex = 0
    val hasXform = transform != null
    val sx = transform?.scaleX ?: 1f
    val kx = transform?.skewX ?: 0f
    val tx = transform?.transX ?: 0f
    val ky = transform?.skewY ?: 0f
    val sy = transform?.scaleY ?: 1f
    val ty = transform?.transY ?: 0f

    for (run in gpuBlob.textBlob.glyphRuns) {
        for (pos in run.positions) {
            val uv = uvs.getOrNull(glyphIndex) ?: Rect.fromLTRB(0f, 0f, 1f, 1f)
            val glyphRect = gpuBlob.glyphRects.getOrNull(glyphIndex) ?: Rect(0f, 0f, 10f, 10f)
            glyphIndex++

            val left = drawOriginX + pos.x + glyphRect.left
            val top = drawOriginY + pos.y + glyphRect.top
            val right = drawOriginX + pos.x + glyphRect.right
            val bottom = drawOriginY + pos.y + glyphRect.bottom
            val w = right - left
            val h = bottom - top
            if (w <= 0f || h <= 0f) continue

            if (hasXform) {
                val x0 = sx * left + kx * top + tx
                val y0 = ky * left + sy * top + ty
                val x1 = sx * right + kx * top + tx
                val y1 = ky * right + sy * top + ty
                val x2 = sx * right + kx * bottom + tx
                val y2 = ky * right + sy * bottom + ty
                val x3 = sx * left + kx * bottom + tx
                val y3 = ky * left + sy * bottom + ty
                vertexData.addAll(listOf(x0, y0, uv.left, uv.top))
                vertexData.addAll(listOf(x1, y1, uv.right, uv.top))
                vertexData.addAll(listOf(x2, y2, uv.right, uv.bottom))
                vertexData.addAll(listOf(x3, y3, uv.left, uv.bottom))
            } else {
                vertexData.addAll(listOf(left, top, uv.left, uv.top))
                vertexData.addAll(listOf(right, top, uv.right, uv.top))
                vertexData.addAll(listOf(right, bottom, uv.right, uv.bottom))
                vertexData.addAll(listOf(left, bottom, uv.left, uv.bottom))
            }
            val base = quadIndex * 4
            indexData.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
            quadIndex++
        }
    }

    return TextAtlasMesh(vertexData.toFloatArray(), indexData.toIntArray())
}

/** Dispatch text atlas pass from a rasterized [GpuTextBlob]. */
private fun GPUBackendRenderRecorder.drawTextAtlasPass(
    gpuBlob: GpuTextBlob,
    blendMode: GPUBlendMode?,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    textColor: Color = Color.BLACK,
    targetWidth: Int = 0,
    targetHeight: Int = 0,
    drawOriginX: Float = 0f,
    drawOriginY: Float = 0f,
    transform: Matrix33? = null,
    recordResult: Boolean = true,
    scissor: GPUCoverageScissor? = null,
    sourcePlane: GPUClipSourcePlane? = null,
) {
    val blob = gpuBlob.textBlob
    val mesh = buildTextAtlasMesh(gpuBlob, drawOriginX, drawOriginY, transform)
    val vertexData = mesh.vertexData
    val indexData = mesh.indexData

    if (vertexData.isEmpty() || indexData.isEmpty()) return
    if (gpuBlob.atlasRgba.isEmpty() || gpuBlob.atlasWidth == 0 || gpuBlob.atlasHeight == 0) {
        diagnostics.degrade("degrade:drawText:empty_atlas", "drawText", "empty_atlas")
        return
    }


    // Populate uniforms matching TEXT_ATLAS_A8_WGSL struct:
    //   struct Uniforms {
    //       targetWidth: f32,     // offset 0,  size 4
    //       targetHeight: f32,    // offset 4,  size 4
    //       color: vec4<f32>,     // offset 16, size 16 (vec4 requires 16-byte alignment)
    //       sourcePlane: u32,     // offset 32, S/G encoding selector
    //   };  // total size: 64 bytes after WGSL struct alignment padding
    val tw = if (targetWidth > 0) targetWidth.toFloat() else gpuBlob.atlasWidth.toFloat()
    val th = if (targetHeight > 0) targetHeight.toFloat() else gpuBlob.atlasHeight.toFloat()
    val uniformBytes = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    uniformBytes.putFloat(tw)           // targetWidth
    uniformBytes.putFloat(th)           // targetHeight
    uniformBytes.putFloat(0f)           // padding (vec4 alignment)
    uniformBytes.putFloat(0f)           // padding
    val cr = ((textColor.packed shr 16) and 0xFFu).toFloat() / 255f
    val cg = ((textColor.packed shr 8) and 0xFFu).toFloat() / 255f
    val cb = ((textColor.packed shr 0) and 0xFFu).toFloat() / 255f
    val ca = ((textColor.packed shr 24) and 0xFFu).toFloat() / 255f
    uniformBytes.putFloat(cr)           // color.r
    uniformBytes.putFloat(cg)           // color.g
    uniformBytes.putFloat(cb)           // color.b
    uniformBytes.putFloat(ca)           // color.a
    uniformBytes.putInt(
        when (sourcePlane) {
            GPUClipSourcePlane.Color -> 1
            GPUClipSourcePlane.GeometryCoverage -> 2
            null -> 0
        },
    )
    uniformBytes.putInt(0); uniformBytes.putInt(0); uniformBytes.putInt(0)

    drawTextAtlasPass(
        atlasRgba = gpuBlob.atlasRgba,
        atlasWidth = gpuBlob.atlasWidth,
        atlasHeight = gpuBlob.atlasHeight,
        atlasFormat = "a8unorm",
        vertexData = vertexData,
        indexData = indexData,
        draws = listOf(
            GPUBackendRawUniformDraw(
                uniformBytes = uniformBytes.array(),
                scissorX = scissor?.x ?: 0,
                scissorY = scissor?.y ?: 0,
                scissorWidth = scissor?.width ?: tw.toInt(),
                scissorHeight = scissor?.height ?: th.toInt(),
            ),
        ),
        blendMode = blendMode,
    )
    if (recordResult) dispatched.add("text:${blob.hashCode()}")
}

private fun resolveTextColor(paint: org.graphiks.kanvas.paint.Paint): Color {
    val shader = paint.shader ?: return paint.color
    return extractSolidShaderColor(shader) ?: paint.color
}

private fun extractSolidShaderColor(shader: Shader): Color? = when (shader) {
    is Shader.SolidColor -> shader.color
    is Shader.WithLocalMatrix -> extractSolidShaderColor(shader.shader)
    is Shader.WithColorFilter -> extractSolidShaderColor(shader.shader)
    is Shader.WithWorkingColorSpace -> extractSolidShaderColor(shader.shader)
    is Shader.CoordClamp -> extractSolidShaderColor(shader.shader)
    else -> null
}

/** Extract the effective scale for text rasterization from a CTM. */
private fun ctmEffectiveScale(transform: Matrix33): Float {
    return maxOf(abs(transform.scaleX), abs(transform.scaleY), 1f)
}

/** Create a [TextBlob] with fontSize scaled by [scale] for higher-resolution rasterization. */
private fun TextBlob.scaledForRasterization(scale: Float): TextBlob {
    if (scale <= 1f || fontSize <= 0f) return this
    val efSize = maxOf(fontSize * scale, 1f)
    return copy(
        fontSize = efSize,
        glyphRuns = glyphRuns.map { it.copy(fontSize = efSize) },
    )
}

/**
 * Rescale glyphRects back to design-space dimensions.
 *
 * When glyphs are rasterized at a CTM-scaled font size, the resulting bitmap rects
 * are proportionally larger. This function reverses that so [drawTextAtlasPass]
 * produces correct screen-space quads when it applies the CTM to the vertices.
 */
internal fun GpuTextBlob.normalizeGlyphRects(scale: Float): GpuTextBlob {
    if (scale <= 1f) return this
    return copy(
        glyphRects = glyphRects.map {
            Rect.fromLTRB(it.left / scale, it.top / scale, it.right / scale, it.bottom / scale)
        },
    )
}
