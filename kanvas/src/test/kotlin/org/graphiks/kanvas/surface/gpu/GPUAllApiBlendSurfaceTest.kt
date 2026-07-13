package org.graphiks.kanvas.surface.gpu

import java.util.stream.Stream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * End-to-end WebGPU support inventory for every visual Canvas API that has a deterministic
 * fixture. The CPU side is deliberately a small, pure-Kotlin pixel oracle: Surface has no public
 * CPU renderer, so re-rendering the command stream would only compare WebGPU with itself.
 *
 * Each fixture samples a fully covered interior pixel, a source pixel excluded by a clip, and a
 * half-covered alpha-mask edge when that geometry can supply one. This keeps the matrix focused
 * on the API-to-S/G adapter and blend route while retaining dedicated tests for complex AA edges.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class GPUAllApiBlendSurfaceTest {
    /**
     * Proves that [ALPHA_MASK_EDGE] is a real half-covered pixel for the fixture rather than an
     * assumed coordinate. The 0.5 F used by the independent blend oracle remains geometric; this
     * GPU read only validates the sample location and the alpha-mask route's fixture setup.
     */
    @Test
    fun alphaMaskFixtureHasMeasuredHalfCoverage() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        assertEquals(.5f, axisAlignedRectCoverage(ALPHA_MASK_RECT, ALPHA_MASK_EDGE), 1e-6f)

        val result = Surface(SURFACE_SIZE, SURFACE_SIZE).run {
            canvas {
                save()
                clipRect(ALPHA_MASK_RECT, ClipOp.INTERSECT, antiAlias = true)
                drawRect(SURFACE_RECT, Paint.fill(Color.WHITE).copy(antiAlias = false))
                restore()
            }
            render()
        }
        val actualAlpha = readPixel(result, ALPHA_MASK_EDGE)[3].toInt()
        assertTrue(
            abs(actualAlpha - (.5f * 255f).roundToInt()) <= 2,
            "alpha-mask fixture coverage at $ALPHA_MASK_EDGE must be 0.5, actual alpha=$actualAlpha",
        )
    }

    /**
     * Regression for an outer DrawPicture paint under an AA clip. The temporary picture layer
     * contributes its geometric coverage G = 1; only the outer clip contributes F = 0.5 at
     * [ALPHA_MASK_EDGE]. In particular, the excluded sample must preserve the destination rather
     * than compositing a transparent temporary layer over it.
     */
    @Test
    fun paintedPictureRestoresThroughItsOuterAlphaClipExactlyOnce() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")

        val api = drawPictureCase()
        val gpu = renderGpu(api, BlendMode.SRC, BlendContext.ALPHA_MASK)
        val cpu = renderCpu(api, BlendMode.SRC, BlendContext.ALPHA_MASK)

        assertPixelsNear(cpu.pixels, gpu.pixels, tolerance = 2)
        assertEquals(0, gpu.result.diagnostics.fatalCount, gpu.result.diagnostics.entries.toString())
        assertEquals(0, gpu.result.stats.opsRefused, gpu.result.diagnostics.entries.toString())
    }

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
                        // Clear has no public BlendMode argument. Its repeated rows document that
                        // inventory exception, but they do not request the selected matrix mode.
                        if (api.composition != Composition.CLEAR && mode.requiresDestinationRead()) {
                            assertEquals(
                                readbacksBefore,
                                session.runtimeTelemetry.destinationReadbackSnapshots,
                                "${api.name}/${mode.name}/${context.name} performed a GPU-to-CPU destination readback",
                            )
                            assertTrue(
                                gpu.result.diagnostics.entries.any { entry ->
                                    api.destinationReadRouteOperations.any { routeOperation ->
                                        entry.code.startsWith("route:destination-read:$routeOperation:")
                                    } &&
                                        entry.reason == "gpu-copy-then-formula"
                                },
                                "${api.name}/${mode.name}/${context.name} did not emit the GPU destination-read formula route " +
                                    "${api.destinationReadRouteOperations.joinToString()}: " +
                                    gpu.result.diagnostics.entries,
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
            GpuPixel(readPixels(result, api, context), result)
        }

    private fun renderCpu(api: BlendCase, mode: BlendMode, context: BlendContext): CpuPixel {
        val sourceOnTransparent = blendPremultiplied(SOURCE, Color.TRANSPARENT, BlendMode.SRC)
        val direct = if (api.composition == Composition.CLEAR) sourceOnTransparent.toColor() else blend(SOURCE, DESTINATION, mode)
        val color = when (context) {
            BlendContext.SAVE_LAYER -> when (api.composition) {
                Composition.BLEND -> sourceOver(
                    blendPremultiplied(SOURCE, Color.TRANSPARENT, mode),
                    DESTINATION.toPremultipliedLinear(),
                ).toColor()
                Composition.CLEAR -> sourceOver(
                    sourceOnTransparent,
                    DESTINATION.toPremultipliedLinear(),
                ).toColor()
            }
            else -> direct
        }
        val clipExcludedColor = when (context) {
            BlendContext.SCISSOR,
            BlendContext.ALPHA_MASK,
            -> destinationAfterInitialDraw()
            BlendContext.UNCLIPPED,
            BlendContext.SAVE_LAYER,
            -> color
        }
        val alphaEdgeColor = when {
            context == BlendContext.ALPHA_MASK && api.alphaMaskEdgeSample != null && api.composition == Composition.BLEND ->
                blendWithCoverage(SOURCE, DESTINATION, mode, coverage = .5f)
            else -> color
        }
        return CpuPixel(
            buildList {
                addAll(color.toRgbaBytes().toList())
                api.clipExcludedSample?.let { addAll(clipExcludedColor.toRgbaBytes().toList()) }
                if (context == BlendContext.ALPHA_MASK) {
                    api.alphaMaskEdgeSample?.let { addAll(alphaEdgeColor.toRgbaBytes().toList()) }
                }
            }.toUByteArray(),
        )
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
            BlendCase("DrawRect", Point(16f, 16f), Point(7f, 16f), Point(12f, 16f)) { mode ->
                drawRect(SOURCE_RECT, shapePaint(mode))
            },
            BlendCase("DrawRRect", Point(16f, 16f), Point(7f, 16f), Point(12f, 16f)) { mode ->
                drawRRect(RRect(SOURCE_RECT, radius = 3f), shapePaint(mode))
            },
            BlendCase("DrawPath", Point(16f, 16f), Point(10f, 10f), Point(16f, 12f)) { mode ->
                drawPath(
                    Path { moveTo(6f, 6f); lineTo(26f, 6f); lineTo(16f, 26f); close() },
                    shapePaint(mode),
                )
            },
            BlendCase("DrawImage", Point(16f, 16f), Point(7f, 16f), Point(12f, 16f)) { mode ->
                drawImage(image, SOURCE_RECT, imagePaint(mode))
            },
            BlendCase("DrawText", Point(17f, 14f), Point(17f, 10f), Point(17f, 12f)) { mode ->
                drawText(textBlob, 0f, 0f, textPaint(mode))
            },
            BlendCase("DrawColor", Point(16f, 16f), Point(4f, 16f), Point(12f, 16f)) { mode ->
                drawColor(SOURCE, mode)
            },
            // Canvas.clear has no BlendMode parameter. It is still instantiated for every mode so
            // the inventory cannot silently omit this visual API; the mode is intentionally ignored.
            BlendCase("Clear", Point(16f, 16f), composition = Composition.CLEAR) { _ ->
                clear(SOURCE)
            },
            BlendCase("DrawPoint", Point(16f, 16f), Point(10f, 16f), Point(12f, 16f)) { mode ->
                drawPoint(16f, 16f, shapePaint(mode))
                drawPoint(10f, 16f, shapePaint(mode))
                drawPoint(12f, 16f, shapePaint(mode))
            },
            BlendCase("DrawPoints", Point(16f, 16f), Point(10f, 16f), Point(12f, 16f)) { mode ->
                drawPoints(
                    PointMode.POINTS,
                    listOf(Point(16f, 16f), Point(10f, 16f), Point(12f, 16f)),
                    shapePaint(mode),
                )
            },
            BlendCase("DrawDRRect", Point(22f, 16f), Point(7f, 16f), Point(22f, 12f)) { mode ->
                drawDRRect(
                    RRect(Rect(4f, 4f, 28f, 28f), radius = 2f),
                    RRect(Rect(10f, 10f, 22f, 22f), radius = 2f),
                    shapePaint(mode),
                )
            },
            BlendCase("DrawImageNine", Point(16f, 16f), Point(7f, 16f), Point(16f, 12f)) { mode ->
                drawImageNine(image, Rect(1f, 1f, 3f, 3f), SOURCE_RECT, imagePaint(mode))
            },
            BlendCase("DrawImageLattice", Point(16f, 16f), Point(7f, 16f), Point(16f, 12f)) { mode ->
                drawImageLattice(
                    image,
                    Lattice(xDivs = listOf(1, 3), yDivs = listOf(1, 3)),
                    SOURCE_RECT,
                    imagePaint(mode),
                )
            },
            drawPictureCase(),
            BlendCase("DrawVertices", Point(16f, 16f), Point(10f, 10f), Point(16f, 12f)) { mode ->
                drawVertices(triangle, shapePaint(mode))
            },
            BlendCase(
                name = "DrawMesh(program=null)",
                sample = Point(16f, 16f),
                clipExcludedSample = Point(10f, 10f),
                alphaMaskEdgeSample = Point(16f, 12f),
                // The public no-program form is intentionally normalized to the DrawVertices adapter.
                destinationReadRouteOperations = setOf("DrawVertices"),
            ) { mode ->
                drawMesh(
                    Mesh(triangle, program = null, bounds = SOURCE_RECT),
                    Paint.fill(SOURCE).copy(antiAlias = false),
                    blendMode = mode,
                )
            },
            BlendCase("DrawAtlas", Point(17f, 17f), Point(9f, 17f), Point(17f, 12f)) { mode ->
                drawAtlas(
                    atlas = image,
                    transforms = listOf(
                        Matrix33.translate(14f, 14f),
                        Matrix33.translate(8f, 14f),
                        Matrix33.translate(14f, 12f),
                    ),
                    texRects = listOf(
                        Rect(0f, 0f, 4f, 4f),
                        Rect(0f, 0f, 4f, 4f),
                        Rect(0f, 0f, 4f, 4f),
                    ),
                    blendMode = mode,
                )
            },
        )
    }

    private fun drawPictureCase(): BlendCase =
        BlendCase(
            name = "DrawPicture",
            sample = Point(16f, 16f),
            clipExcludedSample = Point(10f, 16f),
            alphaMaskEdgeSample = ALPHA_MASK_EDGE,
            // The public DrawPicture paint is rendered atomically at synthetic-layer restore.
            destinationReadRouteOperations = setOf("saveLayer"),
        ) { mode ->
            val recorder = PictureRecorder()
            recorder.beginRecording(SURFACE_RECT).drawRect(
                SOURCE_RECT,
                Paint.fill(Color.fromArgb(255, SOURCE.redByte, SOURCE.greenByte, SOURCE.blueByte))
                    .copy(antiAlias = false),
            )
            drawPicture(
                recorder.finishRecordingAsPicture(),
                Paint.fill(Color.fromArgb(SOURCE.alphaByte, 255, 255, 255)).copy(blendMode = mode),
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

    private fun readPixels(result: RenderResult, api: BlendCase, context: BlendContext): UByteArray = buildList {
        addAll(readPixel(result, api.sample).toList())
        api.clipExcludedSample?.let { addAll(readPixel(result, it).toList()) }
        if (context == BlendContext.ALPHA_MASK) {
            api.alphaMaskEdgeSample?.let { addAll(readPixel(result, it).toList()) }
        }
    }.toUByteArray()

    private fun readPixel(result: RenderResult, sample: Point): UByteArray {
        val offset = (sample.y.toInt() * SURFACE_SIZE + sample.x.toInt()) * 4
        return result.pixels.copyOfRange(offset, offset + 4)
    }

    private fun axisAlignedRectCoverage(rect: Rect, pixel: Point): Float {
        val overlapWidth = (min(rect.right, pixel.x + 1f) - max(rect.left, pixel.x)).coerceAtLeast(0f)
        val overlapHeight = (min(rect.bottom, pixel.y + 1f) - max(rect.top, pixel.y)).coerceAtLeast(0f)
        return overlapWidth * overlapHeight
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
        (srgbToLinear(srgbByte / 255f) * SOURCE.alphaByte / 255f * 255f + .5f)
            .toInt()
            .coerceIn(0, 255)
            .toByte()

    private fun destinationAfterInitialDraw(): Color = blend(Color.TRANSPARENT, DESTINATION, BlendMode.DST)

    private fun assertPixelsNear(expected: UByteArray, actual: UByteArray, tolerance: Int) {
        expected.indices.forEach { channel ->
            assertTrue(
                abs(expected[channel].toInt() - actual[channel].toInt()) <= tolerance,
                "channel=$channel expected=${expected.toList()} actual=${actual.toList()} tolerance=$tolerance",
            )
        }
    }

    /** Pure Kotlin Porter-Duff and W3C blend oracle; no GPU/WGSL helper is used here. */
    private fun blend(source: Color, destination: Color, mode: BlendMode): Color =
        blendPremultiplied(source, destination, mode).toColor()

    /** Implements `D + F * (blend(S, D) - D)` for an independently generated coverage F. */
    private fun blendWithCoverage(source: Color, destination: Color, mode: BlendMode, coverage: Float): Color {
        val blended = blendPremultiplied(source, destination, mode)
        val destinationPremul = destination.toPremultipliedLinear()
        return PremultipliedLinear(
            red = destinationPremul.red + coverage * (blended.red - destinationPremul.red),
            green = destinationPremul.green + coverage * (blended.green - destinationPremul.green),
            blue = destinationPremul.blue + coverage * (blended.blue - destinationPremul.blue),
            alpha = destinationPremul.alpha + coverage * (blended.alpha - destinationPremul.alpha),
        ).toColor()
    }

    private fun blendPremultiplied(source: Color, destination: Color, mode: BlendMode): PremultipliedLinear {
        val s = source.toLinear()
        val d = destination.toLinear()
        val sa = source.alphaByte / 255f
        val da = destination.alphaByte / 255f
        if (mode in ARTISTIC_MODES) {
            val mixed = blendColor(s, d, mode)
            val alpha = sa + da * (1f - sa)
            return PremultipliedLinear(
                red = s[0] * sa * (1f - da) + d[0] * da * (1f - sa) + mixed[0] * sa * da,
                green = s[1] * sa * (1f - da) + d[1] * da * (1f - sa) + mixed[1] * sa * da,
                blue = s[2] * sa * (1f - da) + d[2] * da * (1f - sa) + mixed[2] * sa * da,
                alpha = alpha,
            )
        }
        if (mode == BlendMode.MODULATE) {
            return PremultipliedLinear(
                red = s[0] * sa * d[0] * da,
                green = s[1] * sa * d[1] * da,
                blue = s[2] * sa * d[2] * da,
                alpha = sa * da,
            )
        }
        val (fs, fd) = porterDuffFactors(mode, sa, da)
        return PremultipliedLinear(
            red = s[0] * sa * fs + d[0] * da * fd,
            green = s[1] * sa * fs + d[1] * da * fd,
            blue = s[2] * sa * fs + d[2] * da * fd,
            alpha = sa * fs + da * fd,
        ).let { if (mode == BlendMode.PLUS) it.clamped() else it }
    }

    private fun sourceOver(source: Color, destination: Color): Color = blend(source, destination, BlendMode.SRC_OVER)

    private fun sourceOver(source: PremultipliedLinear, destination: PremultipliedLinear): PremultipliedLinear =
        PremultipliedLinear(
            red = source.red + destination.red * (1f - source.alpha),
            green = source.green + destination.green * (1f - source.alpha),
            blue = source.blue + destination.blue * (1f - source.alpha),
            alpha = source.alpha + destination.alpha * (1f - source.alpha),
        )

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

    private fun Color.toPremultipliedLinear(): PremultipliedLinear {
        val alpha = alphaByte / 255f
        val rgb = toLinear()
        return PremultipliedLinear(rgb[0] * alpha, rgb[1] * alpha, rgb[2] * alpha, alpha)
    }

    private fun PremultipliedLinear.toColor(): Color = Color.fromRGBA(
        linearToSrgb(red),
        linearToSrgb(green),
        linearToSrgb(blue),
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
        val clipExcludedSample: Point? = null,
        val alphaMaskEdgeSample: Point? = null,
        val composition: Composition = Composition.BLEND,
        /** Exact route operations allowed to emit this API's advanced-blend formula diagnostic. */
        val destinationReadRouteOperations: Set<String> = setOf(name),
        val draw: Canvas.(BlendMode) -> Unit,
    )

    private data class PremultipliedLinear(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        fun clamped(): PremultipliedLinear = PremultipliedLinear(
            red.coerceIn(0f, 1f),
            green.coerceIn(0f, 1f),
            blue.coerceIn(0f, 1f),
            alpha.coerceIn(0f, 1f),
        )

    }

    private data class CpuPixel(val pixels: UByteArray)

    private data class GpuPixel(val pixels: UByteArray, val result: RenderResult)

    private enum class Composition { BLEND, CLEAR }

    private enum class BlendContext { UNCLIPPED, SCISSOR, ALPHA_MASK, SAVE_LAYER }

    private companion object {
        const val SURFACE_SIZE = 32
        val SURFACE_RECT = Rect(0f, 0f, SURFACE_SIZE.toFloat(), SURFACE_SIZE.toFloat())
        val SOURCE_RECT = Rect(6f, 6f, 26f, 26f)
        val CLIP_RECT = Rect(12f, 12f, 24f, 24f)
        val ALPHA_MASK_RECT = Rect(12.5f, 12.5f, 23.5f, 23.5f)
        val ALPHA_MASK_EDGE = Point(12f, 16f)
        val SOURCE = Color.fromArgb(192, 208, 80, 32)
        val DESTINATION = Color.fromArgb(160, 40, 120, 208)
        val ARTISTIC_MODES = BlendMode.entries.filter { it.ordinal >= BlendMode.MULTIPLY.ordinal }.toSet()

        @AfterAll
        @JvmStatic
        fun disposeRuntime() {
            GPUBackendRuntimeFactory.dispose()
        }
    }
}
