package org.graphiks.kanvas.surface.gpu

import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * End-to-end WebGPU support inventory for every visual Canvas API that has a deterministic
 * fixture. The CPU side is deliberately a small, pure-Kotlin pixel oracle: Surface has no public
 * CPU renderer, so re-rendering the command stream would only compare WebGPU with itself.
 *
 * Each fixture samples a fully covered interior pixel. That keeps this matrix focused on the
 * API-to-S/G adapter and blend route; AA edge coverage is covered by the dedicated surface tests.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class GPUAllApiBlendSurfaceTest {
    @TestFactory
    fun everyVisualApiSupportsEveryBlendModeInEveryRoute(): Stream<DynamicTest> =
        apiCases().flatMap { api ->
            BlendMode.entries.flatMap { mode ->
                BlendContext.entries.map { context ->
                    DynamicTest.dynamicTest("${api.name}/${mode.name}/${context.name}") {
                        val session = GPUBackendRuntimeFactory.createOrNull()
                        assumeTrue(session != null, "GPU backend unavailable in current environment")
                        val readbacksBefore = session!!.runtimeTelemetry.destinationReadbackSnapshots

                        val gpu = renderGpu(api, mode, context)
                        val cpu = renderCpu(api, mode, context)

                        assertPixelsNear(cpu.pixels, gpu.pixels, tolerance = 2)
                        assertEquals(0, gpu.result.diagnostics.fatalCount, gpu.result.diagnostics.entries.toString())
                        assertEquals(0, gpu.result.stats.opsRefused, gpu.result.diagnostics.entries.toString())
                        assertFalse(
                            gpu.result.diagnostics.entries.any { entry ->
                                entry.reason.contains("blend", ignoreCase = true) &&
                                    entry.reason.startsWith("unsupported")
                            },
                            gpu.result.diagnostics.entries.toString(),
                        )
                        if (mode.requiresDestinationRead()) {
                            assertEquals(
                                readbacksBefore,
                                session.runtimeTelemetry.destinationReadbackSnapshots,
                                "${api.name}/${mode.name}/${context.name} performed a GPU-to-CPU destination readback",
                            )
                        }
                    }
                }
            }
        }.stream()

    private fun renderGpu(api: BlendCase, mode: BlendMode, context: BlendContext): GpuPixel =
        Surface(SURFACE_SIZE, SURFACE_SIZE).run {
            canvas {
                drawRect(SURFACE_RECT, Paint.fill(DESTINATION).copy(antiAlias = false))
                when (context) {
                    BlendContext.UNCLIPPED -> api.draw(this, mode)
                    BlendContext.SCISSOR -> {
                        save()
                        clipRect(CLIP_RECT, ClipOp.INTERSECT, antiAlias = false)
                        api.draw(this, mode)
                        restore()
                    }
                    BlendContext.ALPHA_MASK -> {
                        save()
                        // Fractional AA bounds select the alpha-mask S/G route rather than a scissor.
                        clipRect(ALPHA_MASK_RECT, ClipOp.INTERSECT, antiAlias = true)
                        api.draw(this, mode)
                        restore()
                    }
                    BlendContext.SAVE_LAYER -> {
                        saveLayer()
                        api.draw(this, mode)
                        restore()
                    }
                }
            }
            val result = render()
            GpuPixel(readPixel(result, api.sample), result)
        }

    private fun renderCpu(api: BlendCase, mode: BlendMode, context: BlendContext): CpuPixel {
        val direct = when (api.composition) {
            Composition.BLEND -> blend(SOURCE, DESTINATION, mode)
            Composition.CLEAR -> SOURCE
        }
        val color = when {
            api.composition == Composition.CLEAR -> SOURCE
            context != BlendContext.SAVE_LAYER -> direct
            else -> sourceOver(blend(SOURCE, Color.TRANSPARENT, mode), DESTINATION)
        }
        return CpuPixel(color.toRgbaBytes())
    }

    private fun apiCases(): List<BlendCase> {
        val image = sourceImage()
        val shapePaint: (BlendMode) -> Paint = { mode ->
            Paint.fill(SOURCE).copy(antiAlias = false, blendMode = mode)
        }
        val imagePaint: (BlendMode) -> Paint = { mode ->
            Paint.fill(Color.WHITE).copy(antiAlias = false, blendMode = mode)
        }
        // Image texels and the text-atlas material are UNORM linear inputs. Shape paint is an
        // sRGB-facing API, so these fixture inputs use the inverse transfer to produce SOURCE.
        val textPaint: (BlendMode) -> Paint = { mode ->
            Paint.fill(sourceLinearColor()).copy(antiAlias = false, blendMode = mode)
        }
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point(6f, 6f), Point(26f, 6f), Point(16f, 26f)),
        )
        val textTypeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val textBlob = Font(textTypeface, 24f).toTextBlob("I", 14f, 24f)

        return listOf(
            BlendCase("DrawRect", Point(16f, 16f)) { mode ->
                drawRect(SOURCE_RECT, shapePaint(mode))
            },
            BlendCase("DrawRRect", Point(16f, 16f)) { mode ->
                drawRRect(RRect(SOURCE_RECT, radius = 3f), shapePaint(mode))
            },
            BlendCase("DrawPath", Point(16f, 16f)) { mode ->
                drawPath(
                    Path { moveTo(6f, 6f); lineTo(26f, 6f); lineTo(16f, 26f); close() },
                    shapePaint(mode),
                )
            },
            BlendCase("DrawImage", Point(16f, 16f)) { mode ->
                drawImage(image, SOURCE_RECT, imagePaint(mode))
            },
            BlendCase("DrawText", Point(17f, 14f)) { mode ->
                drawText(textBlob, 0f, 0f, textPaint(mode))
            },
            BlendCase("DrawColor", Point(16f, 16f)) { mode ->
                drawColor(SOURCE, mode)
            },
            // Canvas.clear has no BlendMode parameter. It is still instantiated for every mode so
            // the inventory cannot silently omit this visual API; the mode is intentionally ignored.
            BlendCase("Clear", Point(16f, 16f), Composition.CLEAR) { _ ->
                clear(SOURCE)
            },
            BlendCase("DrawPoint", Point(16f, 16f)) { mode ->
                drawPoint(16f, 16f, shapePaint(mode))
            },
            BlendCase("DrawPoints", Point(16f, 16f)) { mode ->
                drawPoints(PointMode.POINTS, listOf(Point(16f, 16f)), shapePaint(mode))
            },
            BlendCase("DrawDRRect", Point(6f, 16f)) { mode ->
                drawDRRect(
                    RRect(Rect(4f, 4f, 28f, 28f), radius = 2f),
                    RRect(Rect(10f, 10f, 22f, 22f), radius = 2f),
                    shapePaint(mode),
                )
            },
            BlendCase("DrawImageNine", Point(16f, 16f)) { mode ->
                drawImageNine(image, Rect(1f, 1f, 3f, 3f), SOURCE_RECT, imagePaint(mode))
            },
            BlendCase("DrawImageLattice", Point(16f, 16f)) { mode ->
                drawImageLattice(
                    image,
                    Lattice(xDivs = listOf(1, 3), yDivs = listOf(1, 3)),
                    SOURCE_RECT,
                    imagePaint(mode),
                )
            },
            BlendCase("DrawPicture", Point(16f, 16f)) { mode ->
                val recorder = PictureRecorder()
                recorder.beginRecording(SURFACE_RECT).drawRect(SOURCE_RECT, shapePaint(mode))
                drawPicture(recorder.finishRecordingAsPicture())
            },
            BlendCase("DrawVertices", Point(16f, 16f)) { mode ->
                drawVertices(triangle, shapePaint(mode))
            },
            BlendCase("DrawMesh(program=null)", Point(16f, 16f)) { mode ->
                drawMesh(Mesh(triangle, program = null, bounds = SOURCE_RECT), shapePaint(mode))
            },
            BlendCase("DrawAtlas", Point(15f, 15f)) { mode ->
                drawAtlas(
                    atlas = image,
                    transforms = listOf(Matrix33.translate(14f, 14f)),
                    texRects = listOf(Rect(0f, 0f, 4f, 4f)),
                    paint = imagePaint(mode),
                )
            },
        )
    }

    private fun sourceImage(): Image = Image.fromPixels(
        width = 4,
        height = 4,
        pixels = ByteArray(4 * 4 * 4) { index ->
            when (index % 4) {
                0 -> linearByte(SOURCE.redByte)
                1 -> linearByte(SOURCE.greenByte)
                2 -> linearByte(SOURCE.blueByte)
                else -> SOURCE.alphaByte.toByte()
            }
        },
        colorType = ColorType.RGBA_8888,
        sourceId = "all-api-blend-source",
    )

    private fun readPixel(result: RenderResult, sample: Point): UByteArray {
        val offset = (sample.y.toInt() * SURFACE_SIZE + sample.x.toInt()) * 4
        return result.pixels.copyOfRange(offset, offset + 4)
    }

    private fun Color.toRgbaBytes(): UByteArray = ubyteArrayOf(
        redByte.toUByte(),
        greenByte.toUByte(),
        blueByte.toUByte(),
        alphaByte.toUByte(),
    )

    private fun sourceLinearColor(): Color = Color.fromRGBA(
        srgbToLinear(SOURCE.redByte / 255f),
        srgbToLinear(SOURCE.greenByte / 255f),
        srgbToLinear(SOURCE.blueByte / 255f),
        SOURCE.alphaByte / 255f,
    )

    private fun linearByte(srgbByte: Int): Byte =
        (srgbToLinear(srgbByte / 255f) * 255f + .5f).toInt().coerceIn(0, 255).toByte()

    private fun assertPixelsNear(expected: UByteArray, actual: UByteArray, tolerance: Int) {
        expected.indices.forEach { channel ->
            assertTrue(
                abs(expected[channel].toInt() - actual[channel].toInt()) <= tolerance,
                "channel=$channel expected=${expected.toList()} actual=${actual.toList()} tolerance=$tolerance",
            )
        }
    }

    /** Pure Kotlin Porter-Duff and W3C blend oracle; no GPU/WGSL helper is used here. */
    private fun blend(source: Color, destination: Color, mode: BlendMode): Color {
        val s = source.toLinear()
        val d = destination.toLinear()
        val sa = source.alphaByte / 255f
        val da = destination.alphaByte / 255f
        if (mode in ARTISTIC_MODES) {
            val mixed = blendColor(s, d, mode)
            val alpha = sa + da * (1f - sa)
            return fromPremultipliedLinear(
                red = s[0] * sa * (1f - da) + d[0] * da * (1f - sa) + mixed[0] * sa * da,
                green = s[1] * sa * (1f - da) + d[1] * da * (1f - sa) + mixed[1] * sa * da,
                blue = s[2] * sa * (1f - da) + d[2] * da * (1f - sa) + mixed[2] * sa * da,
                alpha = alpha,
            )
        }
        if (mode == BlendMode.MODULATE) {
            return fromPremultipliedLinear(
                red = s[0] * sa * d[0] * da,
                green = s[1] * sa * d[1] * da,
                blue = s[2] * sa * d[2] * da,
                alpha = sa * da,
            )
        }
        val (fs, fd) = porterDuffFactors(mode, sa, da)
        return fromPremultipliedLinear(
            red = s[0] * sa * fs + d[0] * da * fd,
            green = s[1] * sa * fs + d[1] * da * fd,
            blue = s[2] * sa * fs + d[2] * da * fd,
            alpha = sa * fs + da * fd,
            clamp = mode == BlendMode.PLUS,
        )
    }

    private fun sourceOver(source: Color, destination: Color): Color = blend(source, destination, BlendMode.SRC_OVER)

    private fun porterDuffFactors(mode: BlendMode, sa: Float, da: Float): Pair<Float, Float> = when (mode) {
        BlendMode.CLEAR -> 0f to 0f
        BlendMode.SRC -> 1f to 0f
        BlendMode.DST -> 0f to 1f
        BlendMode.SRC_OVER -> 1f to 1f - sa
        BlendMode.DST_OVER -> 1f - da to 1f
        BlendMode.SRC_IN -> da to 0f
        BlendMode.DST_IN -> 0f to sa
        BlendMode.SRC_OUT -> 1f - da to 0f
        BlendMode.DST_OUT -> 0f to 1f - sa
        BlendMode.SRC_ATOP -> da to 1f - sa
        BlendMode.DST_ATOP -> 1f - da to sa
        BlendMode.XOR -> 1f - da to 1f - sa
        BlendMode.PLUS -> 1f to 1f
        else -> error("$mode is not a Porter-Duff mode")
    }

    private fun blendColor(source: FloatArray, destination: FloatArray, mode: BlendMode): FloatArray =
        FloatArray(3) { channel ->
            val s = source[channel]
            val d = destination[channel]
            when (mode) {
                BlendMode.MULTIPLY -> s * d
                BlendMode.SCREEN -> s + d - s * d
                BlendMode.OVERLAY -> if (d <= .5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)
                BlendMode.DARKEN -> min(s, d)
                BlendMode.LIGHTEN -> max(s, d)
                BlendMode.COLOR_DODGE -> if (d == 0f) 0f else if (s == 1f) 1f else min(1f, d / (1f - s))
                BlendMode.COLOR_BURN -> if (d == 1f) 1f else if (s == 0f) 0f else 1f - min(1f, (1f - d) / s)
                BlendMode.HARD_LIGHT -> if (s <= .5f) 2f * s * d else 1f - 2f * (1f - s) * (1f - d)
                BlendMode.SOFT_LIGHT -> softLight(d, s)
                BlendMode.DIFFERENCE -> abs(d - s)
                BlendMode.EXCLUSION -> s + d - 2f * s * d
                BlendMode.HUE,
                BlendMode.SATURATION,
                BlendMode.COLOR,
                BlendMode.LUMINOSITY,
                -> 0f
                else -> error("$mode is not an artistic blend mode")
            }
        }.let { channels ->
            when (mode) {
                BlendMode.HUE -> setLum(setSat(source, saturation(destination)), luminosity(destination))
                BlendMode.SATURATION -> setLum(setSat(destination, saturation(source)), luminosity(destination))
                BlendMode.COLOR -> setLum(source, luminosity(destination))
                BlendMode.LUMINOSITY -> setLum(destination, luminosity(source))
                else -> channels
            }
        }

    private fun softLight(backdrop: Float, source: Float): Float =
        if (source <= .5f) {
            backdrop - (1f - 2f * source) * backdrop * (1f - backdrop)
        } else {
            val d = if (backdrop <= .25f) ((16f * backdrop - 12f) * backdrop + 4f) * backdrop else sqrt(backdrop)
            backdrop + (2f * source - 1f) * (d - backdrop)
        }

    private fun luminosity(color: FloatArray): Float = color[0] * .3f + color[1] * .59f + color[2] * .11f

    private fun saturation(color: FloatArray): Float = color.maxOrNull()!! - color.minOrNull()!!

    private fun setSat(color: FloatArray, saturation: Float): FloatArray {
        val min = color.minOrNull()!!
        val max = color.maxOrNull()!!
        if (max == min) return FloatArray(3)
        return FloatArray(3) { channel -> (color[channel] - min) * saturation / (max - min) }
    }

    private fun setLum(color: FloatArray, luminosity: Float): FloatArray {
        val delta = luminosity - luminosity(color)
        return clipColor(FloatArray(3) { channel -> color[channel] + delta })
    }

    private fun clipColor(color: FloatArray): FloatArray {
        val luminosity = luminosity(color)
        val min = color.minOrNull()!!
        val max = color.maxOrNull()!!
        var clipped = color.copyOf()
        if (min < 0f) {
            clipped = FloatArray(3) { channel ->
                luminosity + (clipped[channel] - luminosity) * luminosity / (luminosity - min)
            }
        }
        if (max > 1f) {
            clipped = FloatArray(3) { channel ->
                luminosity + (clipped[channel] - luminosity) * (1f - luminosity) / (max - luminosity)
            }
        }
        return clipped
    }

    private fun Color.toLinear(): FloatArray = floatArrayOf(
        srgbToLinear(redByte / 255f),
        srgbToLinear(greenByte / 255f),
        srgbToLinear(blueByte / 255f),
    )

    private fun fromPremultipliedLinear(
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        clamp: Boolean = false,
    ): Color = Color.fromRGBA(
        linearToSrgb(if (clamp) red.coerceIn(0f, 1f) else red),
        linearToSrgb(if (clamp) green.coerceIn(0f, 1f) else green),
        linearToSrgb(if (clamp) blue.coerceIn(0f, 1f) else blue),
        alpha.coerceIn(0f, 1f),
    )

    private fun srgbToLinear(value: Float): Float =
        if (value <= .04045f) value / 12.92f else ((value + .055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= .0031308f) clamped * 12.92f else 1.055f * clamped.pow(1f / 2.4f) - .055f
    }

    private fun BlendMode.requiresDestinationRead(): Boolean = this in ARTISTIC_MODES

    private data class BlendCase(
        val name: String,
        val sample: Point,
        val composition: Composition = Composition.BLEND,
        val draw: Canvas.(BlendMode) -> Unit,
    )

    private data class CpuPixel(val pixels: UByteArray)

    private data class GpuPixel(val pixels: UByteArray, val result: RenderResult)

    private enum class Composition { BLEND, CLEAR }

    private enum class BlendContext { UNCLIPPED, SCISSOR, ALPHA_MASK, SAVE_LAYER }

    private companion object {
        const val SURFACE_SIZE = 32
        val SURFACE_RECT = Rect(0f, 0f, SURFACE_SIZE.toFloat(), SURFACE_SIZE.toFloat())
        val SOURCE_RECT = Rect(6f, 6f, 26f, 26f)
        val CLIP_RECT = Rect(2f, 2f, 30f, 30f)
        val ALPHA_MASK_RECT = Rect(2.5f, 2.5f, 29.5f, 29.5f)
        val SOURCE = Color.fromArgb(255, 208, 80, 32)
        val DESTINATION = Color.fromArgb(160, 40, 120, 208)
        val ARTISTIC_MODES = BlendMode.entries.filter { it.ordinal >= BlendMode.MULTIPLY.ordinal }.toSet()

        @AfterAll
        @JvmStatic
        fun disposeRuntime() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
