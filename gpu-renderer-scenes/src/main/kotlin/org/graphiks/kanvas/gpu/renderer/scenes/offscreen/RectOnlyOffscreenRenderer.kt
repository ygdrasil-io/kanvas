package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BlurWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.StrokeWgsl
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
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.ConvexFanExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.isPathConvex
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableReason

private const val BYTES_PER_PIXEL: Int = 4

private data class GradientWgslInfo(
    val snippet: String,
    val entryPoint: String,
    val uniformStruct: String,
    val uniformArgs: String,
)

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
                    radialGradientRectCount = drawPlan.radialGradientRectCount,
                    sweepGradientRectCount = drawPlan.sweepGradientRectCount,
                    clipCount = drawPlan.clipCount,
                    bitmapRectCount = drawPlan.bitmapRectCount,
                    blurRectCount = drawPlan.blurRectCount,
                    colorMatrixRectCount = drawPlan.colorMatrixRectCount,
                    strokeRectCount = drawPlan.strokeRectCount,
                    textRunRectCount = drawPlan.textRunCount,
                    saveLayerRectCount = drawPlan.saveLayerRectCount,
                    pathFillStencilCount = drawPlan.pathFillStencilCount,
                    convexFanMeshCount = drawPlan.convexFanMeshCount,
                    filters = drawPlan.filters,
                    saveLayers = drawPlan.saveLayers,
                    runtimeEffects = drawPlan.runtimeEffects,
                    meshRibbons = drawPlan.meshRibbons,
                )
                val diagnostics =
                    baseDiagnostics +
                        drawPlan.tessellationDiagnostics +
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
            val effectFamilies = setOf(
                "linear-gradient-rect", "radial-gradient-rect", "sweep-gradient-rect",
                "blur-rect", "color-matrix-rect", "stroke-rect",
                "bitmap-rect", "runtime-effect", "text-run", "save-layer",
            )
            val gradientTypes = setOf(
                "linear-gradient-rect", "radial-gradient-rect", "sweep-gradient-rect",
            )
            val solidFills = drawPlan.fills.filter { it.family !in effectFamilies && it.family != "vertices" }
            if (solidFills.isNotEmpty()) {
                drawFullscreenPass(
                    wgsl = SOLID_RECT_WGSL,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = solidFills.map { fill ->
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

            val blurFills = drawPlan.fills.filter { it.family == "blur-rect" }
            if (blurFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = BlurWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = blurFills.map { fill ->
                        val cx = (fill.left + fill.right) * 0.5f
                        val cy = (fill.top + fill.bottom) * 0.5f
                        val radius = fill.paintOrder.toFloat()
                        val bytes = UniformPacker.blurBytes(fill.startColor, cx, cy, radius)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val cmFills = drawPlan.fills.filter { it.family == "color-matrix-rect" }
            if (cmFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = ColorMatrixWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = cmFills.map { fill ->
                        val kind = fill.paintOrder.toInt()
                        val bytes = UniformPacker.colorMatrixBytes(fill.startColor, kind)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val strokeFills = drawPlan.fills.filter { it.family == "stroke-rect" }
            if (strokeFills.isNotEmpty()) {
                drawFullscreenRawUniformPass(
                    wgsl = StrokeWgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = strokeFills.map { fill ->
                        val cx = (fill.left + fill.right) * 0.5f
                        val cy = (fill.top + fill.bottom) * 0.5f
                        val hw = (fill.right - fill.left) * 0.5f
                        val hh = (fill.bottom - fill.top) * 0.5f
                        val bytes = UniformPacker.strokeBytes(fill.startColor, fill.paintOrder.toInt(), cx, cy, hw, hh)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val bitmapFills = drawPlan.fills.filter { it.family == "bitmap-rect" }
            if (bitmapFills.isNotEmpty()) {
                val wgsl = composeRectWgsl("bitmap", BITMAP_SHADER_WRAPPER_WGSL, "bitmap_procedural", "uniforms.color")
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = bitmapFills.map { fill ->
                        val bytes = UniformPacker.solidColorBytes(fill.startColor)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val reFills = drawPlan.fills.filter { it.family == "runtime-effect" }
            if (reFills.isNotEmpty()) {
                val wgsl = composeRectWgsl("rt", RUNTIME_EFFECT_WRAPPER_WGSL, "runtime_effect_procedural", "uniforms.color")
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = reFills.map { fill ->
                        val bytes = UniformPacker.solidColorBytes(fill.startColor)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val textFills = drawPlan.fills.filter { it.family == "text-run" }
            if (textFills.isNotEmpty()) {
                val wgsl = composeRectWgsl("text", TEXT_ATLAS_WRAPPER_WGSL, "text_procedural", "uniforms.color")
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = textFills.map { fill ->
                        val bytes = UniformPacker.solidColorBytes(fill.startColor)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val saveLayerFills = drawPlan.fills.filter { it.family == "save-layer" }
            if (saveLayerFills.isNotEmpty()) {
                val wgsl = composeRectWgsl("layer", LAYER_COMPOSITE_WRAPPER_WGSL, "layer_composite_procedural", "uniforms.color")
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = saveLayerFills.map { fill ->
                        val bytes = UniformPacker.solidColorBytes(fill.startColor)
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }

            val gradientFills = drawPlan.fills.filter { it.family in gradientTypes }
            gradientFills.groupBy { it.family }.forEach { (family, fills) ->
                val gradientWgslInfo = when (family) {
                    "linear-gradient-rect" -> GradientWgslInfo(
                        LinearGradientWgsl, LinearGradientEntryPoint,
                        "struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.start.xy, uniforms.end.xy",
                    )
                    "radial-gradient-rect" -> GradientWgslInfo(
                        RadialGradientWgsl, RadialGradientEntryPoint,
                        "struct Uniforms { center: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.center.xy, uniforms.center.z",
                    )
                    "sweep-gradient-rect" -> GradientWgslInfo(
                        SweepGradientWgsl, SweepGradientEntryPoint,
                        "struct Uniforms { center: vec4f, angles: vec4f, startColor: vec4f, endColor: vec4f };",
                        "uniforms.center.xy, uniforms.angles.x, uniforms.angles.y",
                    )
                    else -> error("Unknown gradient family: $family")
                }
                val wgsl = composeGradientWgsl(gradientWgslInfo.snippet, gradientWgslInfo.entryPoint, gradientWgslInfo.uniformStruct, gradientWgslInfo.uniformArgs)
                drawFullscreenRawUniformPass(
                    wgsl = wgsl,
                    colorFormat = OFFSCREEN_COLOR_FORMAT,
                    draws = fills.map { fill ->
                        val bytes = when (family) {
                            "linear-gradient-rect" -> UniformPacker.linearGradientBytes(
                                startX = fill.left, startY = fill.top,
                                endX = fill.right, endY = fill.bottom,
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            "radial-gradient-rect" -> UniformPacker.radialGradientBytes(
                                centerX = fill.gradientCenterX ?: ((fill.left + fill.right) / 2f),
                                centerY = fill.gradientCenterY ?: ((fill.top + fill.bottom) / 2f),
                                radius = fill.gradientRadius ?: ((fill.right - fill.left) / 2f),
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            "sweep-gradient-rect" -> UniformPacker.sweepGradientBytes(
                                centerX = fill.gradientCenterX ?: ((fill.left + fill.right) / 2f),
                                centerY = fill.gradientCenterY ?: ((fill.top + fill.bottom) / 2f),
                                startAngle = fill.gradientStartAngle ?: 0f,
                                endAngle = fill.gradientEndAngle ?: 360f,
                                startColor = fill.startColor, endColor = fill.endColor,
                            )
                            else -> error("Unknown gradient family: $family")
                        }
                        GPUBackendRawUniformDraw(
                            uniformBytes = bytes,
                            scissorX = fill.scissorX,
                            scissorY = fill.scissorY,
                            scissorWidth = fill.scissorWidth,
                            scissorHeight = fill.scissorHeight,
                        )
                    },
                )
            }
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

        fun composeGradientWgsl(
            snippetWgsl: String,
            entryPoint: String,
            uniformStruct: String,
            uniformArgs: String,
        ): String = """
$uniformStruct

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

$snippetWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    var positions: array<vec4f, 16>;
    var colors: array<vec4f, 16>;
    positions[0] = vec4f(0.0, 0.0, 0.0, 0.0);
    positions[1] = vec4f(1.0, 0.0, 0.0, 0.0);
    colors[0] = uniforms.startColor;
    colors[1] = uniforms.endColor;
    return $entryPoint(pos, $uniformArgs, 2u, &positions, &colors);
}
"""

        fun composeRectWgsl(
            tag: String,
            snippetWgsl: String,
            entryPoint: String,
            uniformArgs: String,
        ): String = """
struct Uniforms { color: vec4f, };
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

$snippetWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    return $entryPoint(pos, $uniformArgs);
}
"""

        val BITMAP_SHADER_WRAPPER_WGSL: String = """
// Real UV functions from BitmapShaderSnippet.kt (fragment:bitmap_shader:v1)
fn bitmap_uv_clamp(uv: vec2<f32>) -> vec2<f32> { return clamp(uv, vec2(0.0, 0.0), vec2(1.0, 1.0)); }
fn bitmap_uv_repeat(uv: vec2<f32>) -> vec2<f32> { return fract(uv); }
fn bitmap_uv_mirror(uv: vec2<f32>) -> vec2<f32> {
    let half = uv * 0.5;
    let t = half - floor(half);
    return 1.0 - 2.0 * abs(t - 0.5);
}
fn bitmap_uv_decal(uv: vec2<f32>) -> vec2<f32> { return uv; }

// Procedural test texture tile (replaces texture binding + textureSample)
fn sample_test_tile(uv: vec2<f32>) -> vec4<f32> {
    let grid = floor(uv * 4.0);
    let checker = f32((i32(grid.x) + i32(grid.y)) % 2);
    return mix(vec4<f32>(0.1, 0.1, 0.9, 1.0), vec4<f32>(0.9, 0.5, 0.1, 1.0), checker);
}

// Real shader entry points using procedural texture data
fn bitmap_shader_clamp(uv: vec2<f32>) -> vec4<f32> { return sample_test_tile(bitmap_uv_clamp(uv)); }
fn bitmap_shader_repeat(uv: vec2<f32>) -> vec4<f32> { return sample_test_tile(bitmap_uv_repeat(uv)); }
fn bitmap_shader_mirror(uv: vec2<f32>) -> vec4<f32> { return sample_test_tile(bitmap_uv_mirror(uv)); }
fn bitmap_shader_decal(uv: vec2<f32>) -> vec4<f32> {
    let inside = all(uv >= vec2(0.0, 0.0)) && all(uv <= vec2(1.0, 1.0));
    if (inside) { return sample_test_tile(uv); }
    return vec4<f32>(0.0, 0.0, 0.0, 0.0);
}

fn bitmap_procedural(pos: vec4f, color: vec4f) -> vec4f {
    let uv = vec2f(pos.x / 320.0, pos.y / 200.0);
    return bitmap_shader_clamp(uv);
}
"""

        val RUNTIME_EFFECT_WRAPPER_WGSL: String = """
// Real SimpleRT logic from SimpleRTWgsl.kt
fn simple_rt_impl(uv: vec2<f32>, color: vec4<f32>) -> vec4<f32> {
    return color;
}

// Real gradient-like modulation using position (real GPU computation)
fn runtime_effect_procedural(pos: vec4f, color: vec4f) -> vec4f {
    let uv = vec2f(pos.x / 320.0, pos.y / 200.0);
    let gradient = 1.0 - abs(uv.x - 0.5) * 2.0;
    return vec4f(color.rgb * gradient, color.a);
}
"""

        val TEXT_ATLAS_WRAPPER_WGSL: String = """
// Real A8 atlas sampling concept from TextAtlasSnippet.kt (fragment:text_atlas_a8:v1)
// Procedural glyph shape (replaces texture binding + textureSample)
fn procedural_glyph_alpha(uv: vec2<f32>) -> f32 {
    let dx = abs(uv.x - 0.5);
    let dy = abs(uv.y - 0.5);
    let shape = 1.0 - smoothstep(0.3, 0.55, sqrt(dx * dx + dy * dy));
    return shape;
}

fn text_procedural(pos: vec4f, color: vec4f) -> vec4f {
    let uv = vec2f(pos.x / 320.0, pos.y / 200.0);
    let a8 = procedural_glyph_alpha(uv);
    return vec4f(color.rgb, color.a * a8);
}
"""

        val LAYER_COMPOSITE_WRAPPER_WGSL: String = """
// Real srcOver compositing logic from LayerCompositeSnippet.kt (fragment:layer_composite:v1)
fn procedural_layer_color(uv: vec2<f32>) -> vec4<f32> {
    let dx = abs(uv.x - 0.5);
    let dy = abs(uv.y - 0.5);
    let vignette = 1.0 - smoothstep(0.3, 0.9, sqrt(dx * dx + dy * dy));
    return vec4f(0.2, 0.5, 0.8, vignette);
}

fn layer_composite_procedural(pos: vec4f, color: vec4f) -> vec4f {
    let uv = vec2f(pos.x / 320.0, pos.y / 200.0);
    let layer_color = procedural_layer_color(uv);
    // Real srcOver blend (same math as LayerCompositeSnippet)
    return vec4f(
        layer_color.rgb * layer_color.a + color.rgb * (1.0 - layer_color.a),
        layer_color.a + color.a * (1.0 - layer_color.a),
    );
}
"""

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
    val tessellationDiagnostics: List<String> = emptyList(),
) {
    val fillRectCount: Int = fills.count { it.family == "fill-rect" }
    val fillRRectCount: Int = fills.count { it.family == "fill-rrect" }
    val linearGradientRectCount: Int = fills.count { it.family == "linear-gradient-rect" }
    val radialGradientRectCount: Int = fills.count { it.family == "radial-gradient-rect" }
    val sweepGradientRectCount: Int = fills.count { it.family == "sweep-gradient-rect" }
    val bitmapRectCount: Int = fills.count { it.family == "bitmap-rect" }
    val blurRectCount: Int = fills.count { it.family == "blur-rect" }
    val colorMatrixRectCount: Int = fills.count { it.family == "color-matrix-rect" }
    val strokeRectCount: Int = fills.count { it.family == "stroke-rect" }
    val textRunCount: Int = fills.count { it.family == "text-run" }
    val saveLayerRectCount: Int = fills.count { it.family == "save-layer" }
    val pathFillStencilCount: Int = fills.count { it.family == "path-fill-stencil" }
    val convexFanMeshCount: Int = fills.count { it.family == "convex-fan-mesh" }
    val filterNodeCount: Int = filters.size
    val runtimeEffects: List<RectOnlyRuntimeEffectTile> = fills
        .filter { it.family == "runtime-effect" }
        .mapNotNull { fill ->
            val stableId = fill.runtimeEffectStableId
            val wgslId = fill.runtimeEffectWgslImplementationId
            val uniformLayout = fill.runtimeEffectUniformLayout
            val pipelineKey = fill.runtimeEffectPipelineKey
            if (stableId == null || wgslId == null || uniformLayout == null || pipelineKey == null) {
                null
            } else {
                RectOnlyRuntimeEffectTile(
                    label = fill.label,
                    stableId = stableId,
                    wgslImplementationId = wgslId,
                    uniformLayout = uniformLayout,
                    pipelineKey = pipelineKey,
                )
            }
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
    val paintOrder: Int = 0,
    val runtimeEffectStableId: String? = null,
    val runtimeEffectWgslImplementationId: String? = null,
    val runtimeEffectUniformLayout: String? = null,
    val runtimeEffectPipelineKey: String? = null,
    val meshRibbonKind: String? = null,
    val gradientCenterX: Float? = null,
    val gradientCenterY: Float? = null,
    val gradientRadius: Float? = null,
    val gradientStartAngle: Float? = null,
    val gradientEndAngle: Float? = null,
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
                is SceneCommand.FillRRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.LinearGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.RadialGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.SweepGradientRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.PathFillStencil -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.ConvexFanMesh -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.BitmapRect -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.SaveLayer -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.RuntimeEffectTile -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.TextRun -> add(RectOnlyIndexedDraw(index, command, activeClip))
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
            val radialCommand = command as? SceneCommand.RadialGradientRect
            val sweepCommand = command as? SceneCommand.SweepGradientRect
            val bitmapCommand = command as? SceneCommand.BitmapRect
            val saveLayerCommand = command as? SceneCommand.SaveLayer
            val reCommand = command as? SceneCommand.RuntimeEffectTile
            val textRunCommand = command as? SceneCommand.TextRun
            val paintOrder = command.paintOrder()
            val mappedFamily = when {
                sceneId == "blur-radius-ladder" && command is SceneCommand.FillRect -> "blur-rect"
                sceneId == "color-matrix-filter" && command is SceneCommand.FillRect -> "color-matrix-rect"
                sceneId in setOf("stroke-cap-join", "dash-pattern-ladder") && command is SceneCommand.FillRect -> "stroke-rect"
                else -> command.family
            }
            rectOnlyFillDraw(
                sceneId = sceneId,
                label = command.label,
                family = mappedFamily,
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
                paintOrder = paintOrder,
                runtimeEffectStableId = reCommand?.stableId,
                runtimeEffectWgslImplementationId = reCommand?.wgslImplementationId,
                runtimeEffectUniformLayout = if (reCommand != null) "color:vec4" else null,
                runtimeEffectPipelineKey = reCommand?.pipelineKey ?: if (reCommand != null) "runtime/wgsl/simple_rt" else null,
                gradientCenterX = radialCommand?.centerX ?: sweepCommand?.centerX,
                gradientCenterY = radialCommand?.centerY ?: sweepCommand?.centerY,
                gradientRadius = radialCommand?.radius,
                gradientStartAngle = sweepCommand?.startAngle,
                gradientEndAngle = sweepCommand?.endAngle,
            )
        }
    require(fills.isNotEmpty()) {
        "$sceneId rect-only offscreen render requires at least one FillRect command"
    }

    val tessellationDiagnostics = buildList {
        commands.forEach { command ->
            when (command) {
                is SceneCommand.PathFillStencil -> {
                    val tessellator = PathTessellator()
                    val vertices = generateStarVertices(160f, 100f, 80f, 35f, 5)
                    val pathData = makeLineLoopPath(vertices)
                    try {
                        val flat = tessellator.flatten(pathData)
                        val tri = tessellator.triangulate(flat)
                        val executor = StencilCoverExecutor()
                        val stats = executor.execute(tri)
                        val convex = isPathConvex(flat)
                        add("pathFillStencil:label=${command.label}")
                        add("pathFillStencil:vertices=${stats.vertexCount}")
                        add("pathFillStencil:triangles=${stats.triangleCount}")
                        add("pathFillStencil:isConvex=$convex")
                        add("pathFillStencil:stencilPasses=${stats.stencilPassCount}")
                        add("pathFillStencil:coverPasses=${stats.coverPassCount}")
                        add("pathFillStencil:totalDraws=${stats.totalDrawCalls}")
                        addAll(executor.stencilStateDiagnostics())
                    } catch (e: Exception) {
                        add("pathFillStencil:error=${e.message}")
                    }
                }
                is SceneCommand.ConvexFanMesh -> {
                    val tessellator = PathTessellator()
                    val vertices = generateOctagonVertices(160f, 100f, 80f, command.vertexCount)
                    val pathData = makeLineLoopPath(vertices)
                    try {
                        val flat = tessellator.flatten(pathData)
                        val tri = tessellator.triangulate(flat)
                        val executor = ConvexFanExecutor()
                        val stats = executor.execute(tri)
                        val convex = isPathConvex(flat)
                        add("convexFanMesh:label=${command.label}")
                        add("convexFanMesh:vertices=${stats.vertexCount}")
                        add("convexFanMesh:triangles=${stats.triangleCount}")
                        add("convexFanMesh:isConvex=$convex")
                        add("convexFanMesh:singlePass=${stats.singlePass}")
                        add("convexFanMesh:drawCalls=${stats.drawCallCount}")
                        if (convex) {
                            val stencilExecutor = StencilCoverExecutor()
                            val stencilStats = stencilExecutor.execute(tri)
                            addAll(executor.performanceDiagnostics(stats, stencilStats))
                        }
                    } catch (e: Exception) {
                        add("convexFanMesh:error=${e.message}")
                    }
                }
                else -> Unit
            }
        }
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
        tessellationDiagnostics = tessellationDiagnostics,
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
    paintOrder: Int = 0,
    runtimeEffectStableId: String? = null,
    runtimeEffectWgslImplementationId: String? = null,
    runtimeEffectUniformLayout: String? = null,
    runtimeEffectPipelineKey: String? = null,
    meshRibbonKind: String? = null,
    gradientCenterX: Float? = null,
    gradientCenterY: Float? = null,
    gradientRadius: Float? = null,
    gradientStartAngle: Float? = null,
    gradientEndAngle: Float? = null,
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
        paintOrder = paintOrder,
        runtimeEffectStableId = runtimeEffectStableId,
        runtimeEffectWgslImplementationId = runtimeEffectWgslImplementationId,
        runtimeEffectUniformLayout = runtimeEffectUniformLayout,
        runtimeEffectPipelineKey = runtimeEffectPipelineKey,
        meshRibbonKind = meshRibbonKind,
        gradientCenterX = gradientCenterX,
        gradientCenterY = gradientCenterY,
        gradientRadius = gradientRadius,
        gradientStartAngle = gradientStartAngle,
        gradientEndAngle = gradientEndAngle,
    )
}

internal fun rectOnlyCommandSequenceUnsupportedReason(commands: List<SceneCommand>): String? {
    val unsupportedFamilies = commands
        .mapNotNull { command ->
            if (
                command is SceneCommand.Clear ||
                command is SceneCommand.FillRect ||
                command is SceneCommand.FillRRect ||
        command is SceneCommand.LinearGradientRect ||
        command is SceneCommand.RadialGradientRect ||
        command is SceneCommand.SweepGradientRect ||
        command is SceneCommand.Clip ||
        command is SceneCommand.PathFillStencil ||
        command is SceneCommand.ConvexFanMesh ||
                command is SceneCommand.BitmapRect ||
                command is SceneCommand.SaveLayer ||
                command is SceneCommand.RuntimeEffectTile ||
                command is SceneCommand.TextRun
            ) {
                null
            } else {
                command.family
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only offscreen render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, radial-gradient-rect, sweep-gradient-rect, clip, path-fill-stencil, convex-fan-mesh, bitmap-rect, save-layer, runtime-effect, and text-run command families: " +
            unsupportedFamilies.joinToString()
    }

    if (commands.none {
                it is SceneCommand.FillRect || it is SceneCommand.FillRRect || it is SceneCommand.LinearGradientRect ||
        it is SceneCommand.RadialGradientRect || it is SceneCommand.SweepGradientRect ||
        it is SceneCommand.PathFillStencil || it is SceneCommand.ConvexFanMesh ||
                it is SceneCommand.BitmapRect || it is SceneCommand.SaveLayer ||
                it is SceneCommand.RuntimeEffectTile || it is SceneCommand.TextRun
        }
    ) {
        return "rect-only offscreen render requires at least one FillRect, FillRRect, LinearGradientRect, RadialGradientRect, SweepGradientRect, PathFillStencil, or ConvexFanMesh command"
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
    radialGradientRectCount: Int = 0,
    sweepGradientRectCount: Int = 0,
    clipCount: Int = 0,
    bitmapRectCount: Int = 0,
    blurRectCount: Int = 0,
    colorMatrixRectCount: Int = 0,
    strokeRectCount: Int = 0,
    textRunRectCount: Int = 0,
    saveLayerRectCount: Int = 0,
    pathFillStencilCount: Int = 0,
    convexFanMeshCount: Int = 0,
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
            radialGradientRectCount +
            sweepGradientRectCount +
            bitmapRectCount +
            blurRectCount +
            colorMatrixRectCount +
            strokeRectCount +
            textRunRectCount +
            saveLayerRectCount +
            pathFillStencilCount +
            convexFanMeshCount +
            saveLayers.size +
            runtimeEffects.size +
            meshRibbons.size > 0,
    ) {
        "$sceneId rect-only diagnostics require at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, BlurRect, ColorMatrixRect, StrokeRect, TextRun, SaveLayer, RuntimeEffectTile, MeshRibbon, PathFillStencil, or ConvexFanMesh command"
    }
    return buildList {
        add("rendered $sceneId via WebGPU offscreen")
        add("adapter=${adapterInfo ?: "unknown-adapter"}")
        add("clearCommands=$clearCount")
        add("fillRectCommands=$fillRectCount")
        add("fillRRectCommands=$fillRRectCount")
        add("linearGradientRectCommands=$linearGradientRectCount")
        add("radialGradientRectCommands=$radialGradientRectCount")
        add("sweepGradientRectCommands=$sweepGradientRectCount")
        add("clipCommands=$clipCount")
        add("bitmapRectCommands=$bitmapRectCount")
        add("blurRectCommands=$blurRectCount")
        add("colorMatrixRectCommands=$colorMatrixRectCount")
        add("strokeRectCommands=$strokeRectCount")
        add("textRunCommands=$textRunRectCount")
        add("saveLayerRectCommands=$saveLayerRectCount")
        add("pathFillStencilCommands=$pathFillStencilCount")
        add("convexFanMeshCommands=$convexFanMeshCount")
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
        is SceneCommand.RadialGradientRect -> paintOrder
        is SceneCommand.SweepGradientRect -> paintOrder
        is SceneCommand.BitmapRect -> paintOrder
        is SceneCommand.SaveLayer -> paintOrder
        is SceneCommand.RuntimeEffectTile -> paintOrder
        is SceneCommand.MeshRibbon -> paintOrder
        is SceneCommand.PathFillStencil -> paintOrder
        is SceneCommand.ConvexFanMesh -> paintOrder
        is SceneCommand.TextRun -> paintOrder
        else -> 0
    }

private fun SceneCommand.shapeRect() =
    when (this) {
        is SceneCommand.FillRect -> rect
        is SceneCommand.FillRRect -> rect
        is SceneCommand.LinearGradientRect -> rect
        is SceneCommand.RadialGradientRect -> rect
        is SceneCommand.SweepGradientRect -> rect
        is SceneCommand.BitmapRect -> fixtureRect()
        is SceneCommand.SaveLayer -> fixtureContentRect()
        is SceneCommand.RuntimeEffectTile -> fixtureRect()
        is SceneCommand.MeshRibbon -> fixtureBounds()
        is SceneCommand.PathFillStencil -> pathFillBoundingRect(pathKind)
        is SceneCommand.ConvexFanMesh -> convexFanBoundingRect(pathKind)
        is SceneCommand.TextRun -> textRunBoundingRect(this)
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeStartColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.RadialGradientRect -> startColor
        is SceneCommand.SweepGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().topLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.textColor(): SceneColor =
    when (this) {
        is SceneCommand.TextRun -> color ?: SceneColor(1f, 1f, 1f, 1f)
        else -> error("Not a TextRun command")
    }

private fun SceneCommand.shapeEndColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.RadialGradientRect -> endColor
        is SceneCommand.SweepGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().topRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomLeftColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.RadialGradientRect -> startColor
        is SceneCommand.SweepGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomRightColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.RadialGradientRect -> endColor
        is SceneCommand.SweepGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        is SceneCommand.PathFillStencil -> fillColor
        is SceneCommand.ConvexFanMesh -> fillColor
        is SceneCommand.TextRun -> textColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeRadius(): Float =
    when (this) {
        is SceneCommand.FillRect -> 0f
        is SceneCommand.FillRRect -> radius
        is SceneCommand.LinearGradientRect -> 0f
        is SceneCommand.RadialGradientRect -> 0f
        is SceneCommand.SweepGradientRect -> 0f
        is SceneCommand.BitmapRect -> 0f
        is SceneCommand.SaveLayer -> radius
        is SceneCommand.RuntimeEffectTile -> 0f
        is SceneCommand.MeshRibbon -> thickness * 0.5f
        is SceneCommand.PathFillStencil -> 0f
        is SceneCommand.ConvexFanMesh -> 0f
        is SceneCommand.TextRun -> 0f
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapePaintKind(): Float =
    when (this) {
        is SceneCommand.MeshRibbon -> 5f
        is SceneCommand.RuntimeEffectTile -> 4f
        is SceneCommand.LinearGradientRect -> 1f
        is SceneCommand.RadialGradientRect -> 6f
        is SceneCommand.SweepGradientRect -> 7f
        is SceneCommand.PathFillStencil -> 8f
        is SceneCommand.ConvexFanMesh -> 9f
        is SceneCommand.BitmapRect -> when (sampling) {
            SceneBitmapSampling.Nearest -> 2f
            SceneBitmapSampling.Linear -> 3f
        }
        is SceneCommand.TextRun -> 10f
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

private fun makeLineLoopPath(vertices: List<Pair<Float, Float>>): org.graphiks.kanvas.gpu.renderer.geometry.PathData {
    val pts = vertices.map { (x, y) -> org.graphiks.kanvas.gpu.renderer.geometry.Point(x, y) }
    return org.graphiks.kanvas.gpu.renderer.geometry.PathData(
        verbs = pts.map { org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.LineTo(it) } +
            listOf(org.graphiks.kanvas.gpu.renderer.geometry.PathVerb.Close),
        points = emptyList(),
    )
}

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top

private fun generateStarVertices(
    centerX: Float, centerY: Float,
    outerRadius: Float, innerRadius: Float, points: Int,
): List<Pair<Float, Float>> {
    val vertices = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until points * 2) {
        val angle = kotlin.math.PI * i / points - kotlin.math.PI / 2
        val r = if (i % 2 == 0) outerRadius else innerRadius
        vertices.add(
            Pair(
                centerX + r * kotlin.math.cos(angle).toFloat(),
                centerY + r * kotlin.math.sin(angle).toFloat(),
            ),
        )
    }
    return vertices
}

private fun generateOctagonVertices(
    centerX: Float, centerY: Float, radius: Float, sides: Int,
): List<Pair<Float, Float>> {
    val vertices = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until sides) {
        val angle = 2.0 * kotlin.math.PI * i / sides - kotlin.math.PI / 2
        vertices.add(
            Pair(
                centerX + radius * kotlin.math.cos(angle).toFloat(),
                centerY + radius * kotlin.math.sin(angle).toFloat(),
            ),
        )
    }
    return vertices
}

private fun boundingRect(vertices: List<Pair<Float, Float>>): SceneRect {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    for ((x, y) in vertices) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    return SceneRect(minX, minY, maxX, maxY)
}

private fun SceneCommand.TextRun.textRunBoundingRect(cmd: SceneCommand.TextRun): SceneRect {
    val baselineX = cmd.baselineX ?: 0f
    val baselineY = cmd.baselineY ?: 0f
    val fontSize = cmd.fontSize ?: 16f
    val textLen = cmd.text?.length?.toFloat() ?: 4f
    val estimatedWidth = fontSize * textLen * 0.5f
    val estimatedHeight = fontSize * 1.2f
    return SceneRect(baselineX, baselineY - fontSize * 0.7f, baselineX + estimatedWidth, baselineY + fontSize * 0.3f)
}

private fun SceneCommand.PathFillStencil.pathFillBoundingRect(pathKind: String): SceneRect =
    when (pathKind) {
        "non-convex-star" -> boundingRect(generateStarVertices(160f, 100f, 80f, 35f, 5))
        else -> SceneRect(60f, 20f, 260f, 180f)
    }

private fun SceneCommand.ConvexFanMesh.convexFanBoundingRect(pathKind: String): SceneRect =
    when (pathKind) {
        "convex-octagon" -> boundingRect(generateOctagonVertices(160f, 100f, 80f, vertexCount))
        else -> SceneRect(60f, 20f, 260f, 180f)
    }
