package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.a8GlyphAtlasGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.legacyRetirementBlockerDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pathStencilCoverGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pmReadinessFreezeDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.runtimeEffectRefusalGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.textResourceBindingGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableReason

private const val BYTES_PER_PIXEL: Int = 4

class RectOnlyOffscreenRenderer {
    fun render(scene: GPURendererScene<SceneCommand>, outputDir: Path): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        outputDir.createDirectories()
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = sceneId,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )

        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")

        runtime.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                ),
            ).use { target ->
                val pixels = renderToPixels(target, drawPlan)
                val nonTransparentPixels = pixels.countNonTransparentPixels()
                val imagePath = outputDir.resolve(RENDER_FILE_NAME)
                val width = target.target.descriptor.width
                val height = target.target.descriptor.height
                writePng(pixels, width, height, imagePath)
                val baseDiagnostics = rectOnlyRenderedDiagnostics(
                    sceneId = sceneId,
                    adapterInfo = session.adapterInfo?.summary,
                    clearCount = drawPlan.clearCount,
                    fillRectCount = drawPlan.fillRectCount,
                    fillRRectCount = drawPlan.fillRRectCount,
                    linearGradientRectCount = drawPlan.linearGradientRectCount,
                    clipCount = drawPlan.clipCount,
                    bitmapRectCount = drawPlan.bitmapRectCount,
                    filters = drawPlan.filters,
                    saveLayers = drawPlan.saveLayers,
                    runtimeEffects = drawPlan.runtimeEffects,
                    meshRibbons = drawPlan.meshRibbons,
                )
                val diagnostics =
                    baseDiagnostics +
                        scene.runtimeEffectRefusalGateDiagnostics() +
                        scene.a8GlyphAtlasGateDiagnostics() +
                        scene.textResourceBindingGateDiagnostics() +
                        scene.pmReadinessFreezeDiagnostics() +
                        scene.legacyRetirementBlockerDiagnostics() +
                        scene.pathStencilCoverGateDiagnostics()
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = width,
                    height = height,
                    byteCount = rectOnlyRawRgbaByteCount(pixels, width, height),
                    nonTransparentPixels = nonTransparentPixels,
                    diagnostics = diagnostics,
                )
            }
        }
    }

    internal fun renderToPixels(
        target: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
    ): ByteArray {
        target.encode(clearColor = drawPlan.clearColor.toGpuClearColor()) {
            drawFullscreenPass(
                wgsl = SOLID_RECT_WGSL,
                colorFormat = OFFSCREEN_COLOR_FORMAT,
                draws = drawPlan.fills.map { fill ->
                    GPUBackendRectDraw(
                        rgbaPremul = fill.toPremulColorArray(),
                        scissorX = fill.scissorX,
                        scissorY = fill.scissorY,
                        scissorWidth = fill.scissorWidth,
                        scissorHeight = fill.scissorHeight,
                    )
                },
            )
        }
        return target.readRgba()
    }

    private fun SceneColor.toGpuClearColor(): GPUClearColor =
        GPUClearColor(
            red = (r * a).toDouble(),
            green = (g * a).toDouble(),
            blue = (b * a).toDouble(),
            alpha = a.toDouble(),
        )

    private fun RectOnlyFillDraw.toPremulColorArray(): FloatArray =
        floatArrayOf(
            startColor.r * startColor.a,
            startColor.g * startColor.a,
            startColor.b * startColor.a,
            startColor.a,
        )

    private fun writePng(pixels: ByteArray, width: Int, height: Int, path: Path) {
        require(pixels.size == width * height * BYTES_PER_PIXEL) {
            "RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${pixels.size}"
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (pixelIndex in 0 until width * height) {
            val base = pixelIndex * BYTES_PER_PIXEL
            val r = pixels[base].toInt() and 0xFF
            val g = pixels[base + 1].toInt() and 0xFF
            val b = pixels[base + 2].toInt() and 0xFF
            val a = pixels[base + 3].toInt() and 0xFF
            image.setRGB(pixelIndex % width, pixelIndex / width, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
        require(ImageIO.write(image, "png", path.toFile())) {
            "No PNG writer available for $RENDER_FILE_NAME"
        }
    }

    private companion object {
        const val RENDER_FILE_NAME: String = "render.png"
        const val OFFSCREEN_COLOR_FORMAT: String = "rgba8unorm"

        val SOLID_RECT_WGSL: String = """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return uniforms.color;
            }
        """.trimIndent()

        fun ByteArray.countNonTransparentPixels(): Int {
            require(size % BYTES_PER_PIXEL == 0) { "RGBA buffer size must be a multiple of $BYTES_PER_PIXEL" }
            var count = 0
            for (base in 3 until size step BYTES_PER_PIXEL) {
                if ((this[base].toInt() and 0xFF) > 0) count += 1
            }
            return count
        }
    }
}

internal data class RectOnlyDrawPlan(
    val sceneId: String,
    val clearColor: SceneColor,
    val clearCount: Int,
    val fills: List<RectOnlyFillDraw>,
    val clipCount: Int = 0,
    val filters: List<RectOnlyFilterNode> = emptyList(),
    val saveLayers: List<RectOnlySaveLayer> = emptyList(),
) {
    val fillRectCount: Int = fills.count { it.family == "fill-rect" }
    val fillRRectCount: Int = fills.count { it.family == "fill-rrect" }
    val linearGradientRectCount: Int = fills.count { it.family == "linear-gradient-rect" }
    val bitmapRectCount: Int = fills.count { it.family == "bitmap-rect" }
    val filterNodeCount: Int = filters.size
    val runtimeEffects: List<RectOnlyRuntimeEffectTile> = fills
        .filter { it.family == "runtime-effect" }
        .map { fill ->
            RectOnlyRuntimeEffectTile(
                label = fill.label,
                stableId = fill.runtimeEffectStableId
                    ?: error("RuntimeEffectTile draw requires stableId: ${fill.label}"),
                wgslImplementationId = fill.runtimeEffectWgslImplementationId
                    ?: error("RuntimeEffectTile draw requires wgslImplementationId: ${fill.label}"),
                uniformLayout = fill.runtimeEffectUniformLayout
                    ?: error("RuntimeEffectTile draw requires uniform layout: ${fill.label}"),
                pipelineKey = fill.runtimeEffectPipelineKey
                    ?: error("RuntimeEffectTile draw requires pipeline key: ${fill.label}"),
            )
        }
    val runtimeEffectCount: Int = runtimeEffects.size
    val meshRibbons: List<RectOnlyMeshRibbon> = fills
        .filter { it.family == "vertices" }
        .map { fill ->
            RectOnlyMeshRibbon(
                label = fill.label,
                meshKind = fill.meshRibbonKind
                    ?: error("MeshRibbon draw requires mesh kind: ${fill.label}"),
            )
        }
    val meshRibbonCount: Int = meshRibbons.size
}

internal data class RectOnlyFilterNode(
    val label: String,
    val inputLabel: String,
    val kind: SceneFilterKind,
    val strength: Float,
)

internal data class RectOnlyRuntimeEffectTile(
    val label: String,
    val stableId: String,
    val wgslImplementationId: String,
    val uniformLayout: String,
    val pipelineKey: String,
)

internal data class RectOnlySaveLayer(
    val label: String,
    val layerKind: String,
    val filterLabel: String,
    val filterKind: SceneFilterKind,
    val filterStrength: Float,
)

internal data class RectOnlyMeshRibbon(
    val label: String,
    val meshKind: String,
)

internal data class RectOnlyFillDraw(
    val label: String,
    val family: String,
    val startColor: SceneColor,
    val endColor: SceneColor,
    val bottomLeftColor: SceneColor,
    val bottomRightColor: SceneColor,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    val paintKind: Float,
    val filterKind: Float,
    val filterStrength: Float,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
    val runtimeEffectStableId: String? = null,
    val runtimeEffectWgslImplementationId: String? = null,
    val runtimeEffectUniformLayout: String? = null,
    val runtimeEffectPipelineKey: String? = null,
    val meshRibbonKind: String? = null,
)

private data class RectOnlyIndexedDraw(
    val index: Int,
    val command: SceneCommand,
    val clip: SceneCommand.Clip?,
)

internal fun rectOnlyRawRgbaByteCount(pixels: ByteArray, width: Int, height: Int): Long {
    require(width > 0) { "rect-only raw byte count width must be positive" }
    require(height > 0) { "rect-only raw byte count height must be positive" }
    val expectedByteCount = width.toLong() * height.toLong() * BYTES_PER_PIXEL.toLong()
    require(pixels.size.toLong() == expectedByteCount) {
        "RGBA buffer size mismatch: expected $expectedByteCount, got ${pixels.size}"
    }
    return pixels.size.toLong()
}

internal fun prepareRectOnlyDrawPlan(
    sceneId: String,
    commands: List<SceneCommand>,
    width: Int,
    height: Int,
): RectOnlyDrawPlan {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(width > 0) { "$sceneId rect-only target width must be positive" }
    require(height > 0) { "$sceneId rect-only target height must be positive" }
    val unsupportedReason = rectOnlyCommandSequenceUnsupportedReason(commands)
    require(unsupportedReason == null) { "$sceneId $unsupportedReason" }

    var activeClip: SceneCommand.Clip? = null
    val indexedDraws = buildList {
        commands.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Clip -> activeClip = command
                is SceneCommand.FillRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                else -> Unit
            }
        }
    }

    val fills = indexedDraws
        .sortedWith(
            compareBy<RectOnlyIndexedDraw> { (_, command) ->
                command.paintOrder()
            }.thenBy { it.index },
        )
        .map { (_, command, clip) ->
            rectOnlyFillDraw(
                sceneId = sceneId,
                label = command.label,
                family = command.family,
                rect = command.shapeRect(),
                radius = command.shapeRadius(),
                startColor = command.shapeStartColor(),
                endColor = command.shapeEndColor(),
                bottomLeftColor = command.shapeBottomLeftColor(),
                bottomRightColor = command.shapeBottomRightColor(),
                paintKind = command.shapePaintKind(),
                filterKind = 0f,
                filterStrength = 0f,
                clip = clip,
                width = width,
                height = height,
            )
        }
    require(fills.isNotEmpty()) {
        "$sceneId rect-only offscreen render requires at least one FillRect command"
    }

    return RectOnlyDrawPlan(
        sceneId = sceneId,
        clearColor = commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f),
        clearCount = commands.count { it is SceneCommand.Clear },
        fills = fills,
        clipCount = commands.count { it is SceneCommand.Clip },
        filters = emptyList(),
        saveLayers = emptyList(),
    )
}

private fun rectOnlyFillDraw(
    sceneId: String,
    label: String,
    family: String,
    rect: SceneRect,
    radius: Float,
    startColor: SceneColor,
    endColor: SceneColor,
    bottomLeftColor: SceneColor,
    bottomRightColor: SceneColor,
    paintKind: Float,
    filterKind: Float,
    filterStrength: Float,
    clip: SceneCommand.Clip?,
    width: Int,
    height: Int,
    runtimeEffectStableId: String? = null,
    runtimeEffectWgslImplementationId: String? = null,
    runtimeEffectUniformLayout: String? = null,
    runtimeEffectPipelineKey: String? = null,
    meshRibbonKind: String? = null,
): RectOnlyFillDraw {
    requireInsideTarget(sceneId, label, rect, width, height, "fill shape")
    clip?.let { requireInsideTarget(sceneId, it.label, it.rect, width, height, "clip") }
    val scissorRect = rect.intersect(clip?.rect)
    require(scissorRect != null) {
        "$sceneId rect-only fill shape must intersect active clip: $label"
    }
    val left = floor(scissorRect.left).toInt()
    val top = floor(scissorRect.top).toInt()
    val right = ceil(scissorRect.right).toInt()
    val bottom = ceil(scissorRect.bottom).toInt()
    val widthPx = rect.right - rect.left
    val heightPx = rect.bottom - rect.top
    return RectOnlyFillDraw(
        label = label,
        family = family,
        startColor = startColor,
        endColor = endColor,
        bottomLeftColor = bottomLeftColor,
        bottomRightColor = bottomRightColor,
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
        radius = minOf(radius, widthPx * 0.5f, heightPx * 0.5f),
        paintKind = paintKind,
        filterKind = filterKind,
        filterStrength = filterStrength,
        scissorX = left,
        scissorY = top,
        scissorWidth = right - left,
        scissorHeight = bottom - top,
        runtimeEffectStableId = runtimeEffectStableId,
        runtimeEffectWgslImplementationId = runtimeEffectWgslImplementationId,
        runtimeEffectUniformLayout = runtimeEffectUniformLayout,
        runtimeEffectPipelineKey = runtimeEffectPipelineKey,
        meshRibbonKind = meshRibbonKind,
    )
}

internal fun rectOnlyCommandSequenceUnsupportedReason(commands: List<SceneCommand>): String? {
    commands.textRunRouteUnavailableReason()?.let { return it }

    val unsupportedFamilies = commands
        .mapNotNull { command ->
            if (
                command is SceneCommand.Clear ||
                command is SceneCommand.FillRect ||
                command is SceneCommand.Clip
            ) {
                null
            } else {
                command.family
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only offscreen render supports only clear, fill-rect, and clip command families: " +
            unsupportedFamilies.joinToString()
    }

    if (commands.none {
                it is SceneCommand.FillRect
        }
    ) {
        return "rect-only offscreen render requires at least one FillRect command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only offscreen render supports zero or one initial Clear before drawable commands"
    }

    return null
}

internal fun rectOnlyRenderedDiagnostics(
    sceneId: String,
    adapterInfo: String?,
    clearCount: Int,
    fillRectCount: Int,
    fillRRectCount: Int,
    linearGradientRectCount: Int = 0,
    clipCount: Int = 0,
    bitmapRectCount: Int = 0,
    filters: List<RectOnlyFilterNode> = emptyList(),
    saveLayers: List<RectOnlySaveLayer> = emptyList(),
    runtimeEffects: List<RectOnlyRuntimeEffectTile> = emptyList(),
    meshRibbons: List<RectOnlyMeshRibbon> = emptyList(),
): List<String> {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(
        fillRectCount +
            fillRRectCount +
            linearGradientRectCount +
            bitmapRectCount +
            saveLayers.size +
            runtimeEffects.size +
            meshRibbons.size > 0,
    ) {
        "$sceneId rect-only diagnostics require at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command"
    }
    return buildList {
        add("rendered $sceneId via WebGPU offscreen")
        add("adapter=${adapterInfo ?: "unknown-adapter"}")
        add("clearCommands=$clearCount")
        add("fillRectCommands=$fillRectCount")
        add("fillRRectCommands=$fillRRectCount")
        add("linearGradientRectCommands=$linearGradientRectCount")
        add("clipCommands=$clipCount")
        add("bitmapRectCommands=$bitmapRectCount")
        if (saveLayers.isNotEmpty()) {
            add("saveLayerCommands=${saveLayers.size}")
            add("saveLayerKinds=${saveLayers.joinToString { it.layerKind }}")
            add("saveLayerRoute=scene-fixture.bounded-shadow-card")
            add("saveLayerMaterializedDraws=${saveLayers.size * 2}")
            add("saveLayerFilterKinds=${saveLayers.joinToString { it.filterKind.wireName }}")
            add("saveLayerFallbackReason=none")
            add("filterRoutes=scene-fixture.bounded-drop-shadow")
            add("generalSaveLayerSupport=false")
            add("imageFilterDagSupport=false")
        }
        if (filters.isNotEmpty()) {
            add("filterNodeCommands=${filters.size}")
            add("filterKinds=${filters.joinToString { it.kind.wireName }}")
            add("filterInputs=${filters.joinToString { it.inputLabel }}")
        }
        if (runtimeEffects.isNotEmpty()) {
            add("runtimeEffectCommands=${runtimeEffects.size}")
            add("runtimeEffectStableIds=${runtimeEffects.joinToString { it.stableId }}")
            add("runtimeEffectWgslImplementationIds=${runtimeEffects.joinToString { it.wgslImplementationId }}")
            add("runtimeEffectUniformLayout=${runtimeEffects.joinToString { it.uniformLayout }}")
            add("runtimeEffectPipelineKey=${runtimeEffects.joinToString { it.pipelineKey }}")
            add("runtimeEffectDescriptorEvidence=reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json")
            add(
                "runtimeEffectParserEvidence=" +
                    "RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms",
            )
            add("fallbackReason=none")
        }
        if (meshRibbons.isNotEmpty()) {
            add("meshRibbonCommands=${meshRibbons.size}")
            add("meshRibbonKinds=${meshRibbons.joinToString { it.meshKind }}")
            add("meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
            add("meshRibbonFallbackReason=none")
            add("generalVerticesSupport=false")
            add("vertexIndexBufferSupport=false")
        }
    }
}

private fun SceneCommand.paintOrder(): Int =
    when (this) {
        is SceneCommand.FillRect -> paintOrder
        is SceneCommand.FillRRect -> paintOrder
        is SceneCommand.LinearGradientRect -> paintOrder
        is SceneCommand.BitmapRect -> paintOrder
        is SceneCommand.SaveLayer -> paintOrder
        is SceneCommand.RuntimeEffectTile -> paintOrder
        is SceneCommand.MeshRibbon -> paintOrder
        else -> 0
    }

private fun SceneCommand.shapeRect() =
    when (this) {
        is SceneCommand.FillRect -> rect
        is SceneCommand.FillRRect -> rect
        is SceneCommand.LinearGradientRect -> rect
        is SceneCommand.BitmapRect -> fixtureRect()
        is SceneCommand.SaveLayer -> fixtureContentRect()
        is SceneCommand.RuntimeEffectTile -> fixtureRect()
        is SceneCommand.MeshRibbon -> fixtureBounds()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeStartColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().topLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeEndColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().topRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomLeftColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomRightColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeRadius(): Float =
    when (this) {
        is SceneCommand.FillRect -> 0f
        is SceneCommand.FillRRect -> radius
        is SceneCommand.LinearGradientRect -> 0f
        is SceneCommand.BitmapRect -> 0f
        is SceneCommand.SaveLayer -> radius
        is SceneCommand.RuntimeEffectTile -> 0f
        is SceneCommand.MeshRibbon -> thickness * 0.5f
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapePaintKind(): Float =
    when (this) {
        is SceneCommand.MeshRibbon -> 5f
        is SceneCommand.RuntimeEffectTile -> 4f
        is SceneCommand.LinearGradientRect -> 1f
        is SceneCommand.BitmapRect -> when (sampling) {
            SceneBitmapSampling.Nearest -> 2f
            SceneBitmapSampling.Linear -> 3f
        }
        else -> 0f
    }

private fun SceneFilterKind.filterPaintKind(): Float =
    when (this) {
        SceneFilterKind.LumaTint -> 1f
        SceneFilterKind.DropShadow -> 0f
    }

private fun SceneCommand.BitmapRect.fixtureRect(): SceneRect =
    rect ?: error("BitmapRect requires rect fixture payload: $label")

private fun SceneCommand.BitmapRect.fixtureSource(): SceneBitmapSource =
    source ?: error("BitmapRect requires source fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentRect(): SceneRect =
    contentRect ?: error("SaveLayer requires contentRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowRect(): SceneRect =
    shadowRect ?: error("SaveLayer requires shadowRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentColor(): SceneColor =
    contentColor ?: error("SaveLayer requires contentColor fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowColor(): SceneColor =
    shadowColor ?: error("SaveLayer requires shadowColor fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureRect(): SceneRect =
    rect ?: error("RuntimeEffectTile requires rect fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureUniformColor(): SceneColor =
    uniformColor ?: error("RuntimeEffectTile requires uniform color fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureBounds(): SceneRect =
    bounds ?: error("MeshRibbon requires bounds fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureStartColor(): SceneColor =
    startColor ?: error("MeshRibbon requires startColor fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureEndColor(): SceneColor =
    endColor ?: error("MeshRibbon requires endColor fixture payload: $label")

private fun SceneColor.withAlpha(alpha: Float): SceneColor =
    SceneColor(r = r, g = g, b = b, a = alpha.coerceIn(0f, 1f))

private fun requireInsideTarget(
    sceneId: String,
    label: String,
    rect: SceneRect,
    width: Int,
    height: Int,
    kind: String,
) {
    val left = floor(rect.left).toInt()
    val top = floor(rect.top).toInt()
    val right = ceil(rect.right).toInt()
    val bottom = ceil(rect.bottom).toInt()
    require(
        left >= 0 &&
            top >= 0 &&
            right <= width &&
            bottom <= height &&
            right > left &&
            bottom > top,
    ) {
        "$sceneId rect-only $kind must be inside positive bounds: $label"
    }
}

private fun SceneRect.intersect(other: SceneRect?): SceneRect? {
    if (other == null) return this
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right > left && bottom > top) SceneRect(left, top, right, bottom) else null
}

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top
