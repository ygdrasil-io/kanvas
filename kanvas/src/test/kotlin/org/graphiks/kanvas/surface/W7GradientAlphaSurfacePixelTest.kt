@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.GradientAlphaMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class W7GradientAlphaSurfacePixelTest {
    companion object {
        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
        private val onePixel = RectF32.ofLTRB(0f, 0f, 1f, 1f)
    }

    @Test
    fun premultipliedTransparentEndpointPreservesSourceColor() {
        val left = ColorARGB.White
        val right = ColorARGB.Transparent
        val straight = expected(left, right, GradientAlphaMode.STRAIGHT)
        val premultiplied = expected(left, right, GradientAlphaMode.PREMULTIPLIED)
        W5fSurfacePixelFixtures.requireBounded(straight)
        W5fSurfacePixelFixtures.requireBounded(premultiplied)
        val straightResult = renderTwice(gradient(left, right, GradientAlphaMode.STRAIGHT))
        val premultipliedResult = renderTwice(gradient(left, right, GradientAlphaMode.PREMULTIPLIED))
        W5fSurfacePixelFixtures.assertNativePixels(straightResult, listOf(straight))
        W5fSurfacePixelFixtures.assertNativePixels(premultipliedResult, listOf(premultiplied))
        assertDisjoint(straight, premultiplied)
    }

    @Test
    fun premultipliedTwoNonzeroAlphasPreserveWeightedColor() {
        val left = ColorARGB.of(128, 255, 0, 0)
        val right = ColorARGB.of(64, 0, 0, 255)
        val straight = expected(left, right, GradientAlphaMode.STRAIGHT)
        val premultiplied = expected(left, right, GradientAlphaMode.PREMULTIPLIED)
        W5fSurfacePixelFixtures.requireBounded(straight)
        W5fSurfacePixelFixtures.requireBounded(premultiplied)
        val straightResult = renderTwice(gradient(left, right, GradientAlphaMode.STRAIGHT))
        val premultipliedResult = renderTwice(gradient(left, right, GradientAlphaMode.PREMULTIPLIED))
        W5fSurfacePixelFixtures.assertNativePixels(straightResult, listOf(straight))
        W5fSurfacePixelFixtures.assertNativePixels(premultipliedResult, listOf(premultiplied))
        assertDisjoint(straight, premultiplied)
    }

    @Test
    fun srcReplacesPrepopulatedDestinationWithV4SemiTransparentSource() {
        val left = ColorARGB.of(128, 31, 143, 219)
        val right = ColorARGB.of(128, 31, 143, 219)
        val expected = expected(left, right, GradientAlphaMode.PREMULTIPLIED)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val destination = ColorARGB.of(211, 203, 47, 89)
        val surface = Surface(1, 1).also { target -> target.canvas {
            drawRect(onePixel, Paint(destination, antiAlias = false))
            drawRect(onePixel, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, left), GradientStop(1f, right)), alphaMode = GradientAlphaMode.PREMULTIPLIED),
                antiAlias = false, blendMode = BlendMode.SRC))
        } }
        val result = renderTwice(surface)
        W5fSurfacePixelFixtures.assertNativePixels(result, listOf(expected))
        assertTrue(!result.pixels.contentEquals(ubyteArrayOf(destination.red.toUByte(), destination.green.toUByte(),
            destination.blue.toUByte(), destination.alpha.toUByte())))
    }

    @Test
    fun zeroAndMinimalAlphaEndpointsStayFinite() {
        val cases = listOf(
            ColorARGB.Transparent to ColorARGB.Transparent,
            ColorARGB.Transparent to ColorARGB.of(1, 255, 0, 0),
            ColorARGB.of(1, 0, 0, 255) to ColorARGB.Transparent,
            ColorARGB.of(128, 255, 0, 0) to ColorARGB.of(64, 0, 0, 255),
        )
        cases.forEach { (left, right) ->
            val expected = expected(left, right, GradientAlphaMode.PREMULTIPLIED)
            W5fSurfacePixelFixtures.requireBounded(expected)
            W5fSurfacePixelFixtures.assertNativePixels(renderTwice(gradient(left, right, GradientAlphaMode.PREMULTIPLIED)), listOf(expected))
        }
    }

    @Test fun pictureRoundTripPreservesAlphaMode() {
        val left = ColorARGB.White; val right = ColorARGB.Transparent
        val picture = PictureRecorder().also { recorder -> recorder.beginRecording(onePixel).drawRect(onePixel, Paint(
            shader = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),listOf(GradientStop(0f,left),GradientStop(1f,right)),
                alphaMode = GradientAlphaMode.PREMULTIPLIED), antiAlias=false, blendMode=BlendMode.SRC)) }.finishRecordingAsPicture()
        val decoded = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        val expected=expected(left,right,GradientAlphaMode.PREMULTIPLIED); W5fSurfacePixelFixtures.requireBounded(expected)
        listOf(picture,decoded).forEach { candidate ->
            val result=Surface(1,1).also { surface -> surface.canvas { candidate.playback(this) } }.let(::renderTwice)
            W5fSurfacePixelFixtures.assertNativePixels(result,listOf(expected))
        }
    }

    @Test fun historicalPicture15StraightGradientReplaysItsWhiteBackground() {
        val encoded = requireNotNull(javaClass.getResourceAsStream(
            "/picture/format-15-straight-alpha-gradient-c80e5b56d.base64")) {
            "Picture15 fixture resource missing"
        }.bufferedReader().use { it.readText().trim() }
        val archive = java.util.Base64.getDecoder().decode(encoded)
        val picture = requireNotNull(Picture.fromByteArray(archive)) { "Picture15 fixture did not decode" }
        val pixels = renderTwice(Surface(1, 1).also { surface -> surface.canvas { picture.playback(this) } }).pixels
        // Picture15 recorded opaque white followed by its historical straight half-alpha gradient.
        assertTrue(kotlin.math.abs(pixels[0].toInt() - 205) <= 1, "historical red=${pixels[0]}")
        assertTrue(kotlin.math.abs(pixels[1].toInt() - 205) <= 1, "historical green=${pixels[1]}")
        assertTrue(kotlin.math.abs(pixels[2].toInt() - 205) <= 1, "historical blue=${pixels[2]}")
        assertEquals(255, pixels[3].toInt())
    }

    @Test fun mixedModesKeepCaptureRangesAndOrder() {
        val specs = listOf(2, 16, 17).flatMap { count ->
            listOf(GradientAlphaMode.STRAIGHT, GradientAlphaMode.PREMULTIPLIED).map { mode -> count to mode }
        }
        fun frame(reverse: Boolean): Pair<Surface, List<WgslFloatEnvelopeV1Oracle.DrawResult>> {
            val surface = Surface(specs.size, 1)
            val mutableRanges = mutableListOf<MutableList<GradientStop>>()
            val expected = mutableListOf<WgslFloatEnvelopeV1Oracle.DrawResult>()
            surface.canvas {
                (if (reverse) specs.reversed() else specs).forEachIndexed { x, (count, mode) ->
                    val colors = List(count) { index -> when (index) {
                        0 -> ColorARGB.of(128, 255, 255, 255)
                        count - 1 -> ColorARGB.of(64, 0, 0, 255)
                        else -> ColorARGB.of(96, index * 13, 255 - index * 9, index * 7)
                    } }
                    val stops = colors.mapIndexed { index, color ->
                        GradientStop(index.toFloat() / (count - 1), color)
                    }.toMutableList()
                    mutableRanges += stops
                    val leaf = Shader.LinearGradient(Point2F32(x.toFloat(), 0f), Point2F32(x + 1f, 0f),
                        stops, alphaMode = mode)
                    val source = if (mode == GradientAlphaMode.STRAIGHT)
                        Shader.WithWorkingColorSpace(leaf, ColorSpaceInterpolation.SRGB) else leaf
                    val want = W5fColorCpuOracle.expectedShaderTree(source, finalBlend = BlendMode.SRC,
                        devicePointF32 = Point2F32(x + .5f, .5f))
                    W5fSurfacePixelFixtures.requireBounded(want)
                    expected += want
                    val bounds = RectF32.ofLTRB(x.toFloat(), 0f, x + 1f, 1f)
                    val paint = Paint(shader = source, antiAlias = false, blendMode = BlendMode.SRC)
                    if (count == 16 && mode == GradientAlphaMode.PREMULTIPLIED)
                        drawPath(Path().addRect(bounds), paint)
                    else drawRect(bounds, paint)
                }
            }
            mutableRanges.forEach { range -> range.indices.forEach { index ->
                range[index] = GradientStop(range[index].position, ColorARGB.Green)
            } }
            return surface to expected
        }
        listOf(false, true).forEach { reverse ->
            val (surface, expected) = frame(reverse)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(surface), expected) }
        }
    }

    @Test fun wrappersAndMixedAaRootPreserveAlphaMode() {
        val alphaBounds = RectF32.ofLTRB(8f, 0f, 9f, 1f)
        val alphaLeft = ColorARGB.White
        val alphaRight = ColorARGB.of(0, 204, 204, 204)
        val alphaModes = listOf(GradientAlphaMode.STRAIGHT, GradientAlphaMode.PREMULTIPLIED)
        // Establish that transparent-destination SrcOver separates the modes using only
        // the independent CPU oracle, before any native render in this test.
        val alphaExpectations = alphaModes.associateWith { mode ->
            W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB, alphaLeft, alphaRight,
                .5f, finalBlend = BlendMode.SRC_OVER, alphaMode = mode)
                .also(W5fSurfacePixelFixtures::requireBounded)
        }
        assertDisjoint(alphaExpectations.getValue(GradientAlphaMode.STRAIGHT),
            alphaExpectations.getValue(GradientAlphaMode.PREMULTIPLIED))

        val left = ColorARGB.of(128, 255, 255, 255)
        val right = ColorARGB.of(64, 0, 0, 255)
        val leaf = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(0f, left), GradientStop(1f, right)), alphaMode = GradientAlphaMode.PREMULTIPLIED)
        val wrapped = Shader.Opacity(Shader.CoordClamp(Shader.WithLocalMatrix(
            Shader.WithWorkingColorSpace(leaf, ColorSpaceInterpolation.SRGB), Matrix3x3F32.translation(-.25f, 0f)),
            RectF32.ofLTRB(0f, 0f, 1f, 1f)), .5f)
        val wrappedExpected = W5fColorCpuOracle.expectedShaderTree(wrapped, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(wrappedExpected)
        val wrappedSurface = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(onePixel, Paint(shader = wrapped, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(wrappedSurface), listOf(wrappedExpected)) }

        val ring = RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f)
        val full = RectF32.ofLTRB(0f, 0f, 8f, 8f)
        val rootGradient = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(8f, 0f),
            listOf(GradientStop(0f, ColorARGB.White), GradientStop(.5f, ColorARGB.White),
                GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue)),
            alphaMode = GradientAlphaMode.PREMULTIPLIED)
        alphaModes.forEach { mode ->
            val alphaShader = Shader.LinearGradient(Point2F32(8f, 0f), Point2F32(9f, 0f),
                listOf(GradientStop(0f, alphaLeft), GradientStop(1f, alphaRight)), alphaMode = mode)
            val mixed = Surface(9, 8)
            mixed.canvas {
                drawRect(full, Paint(shader = rootGradient, antiAlias = false))
                drawRect(alphaBounds, Paint(shader = alphaShader, antiAlias = false, blendMode = BlendMode.SRC_OVER))
                drawRect(ring, Paint(ColorARGB.Red, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 1f))
            }
            val pixels = renderTwice(mixed).pixels
            WgslFloatEnvelopeV1Oracle.assertAdmits(alphaExpectations.getValue(mode), pixels.copyOfRange(8 * 4, 9 * 4))
            assertPixel(pixels, 2, 2, 255, 0, 0, 255, 9)
            assertPixel(pixels, 3, 3, 255, 255, 255, 255, 9)
            assertPixel(pixels, 0, 0, 255, 255, 255, 255, 9)
        }
    }

    @Test fun unsupportedAlphaCombinationsRefuseBeforePublicationAndRecover() {
        val unsupported = listOf(
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, ColorARGB.White)), interpolation = ColorSpaceInterpolation.LINEAR,
                alphaMode = GradientAlphaMode.PREMULTIPLIED),
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, ColorARGB.White), GradientStop(1f, ColorARGB.Black)),
                tileMode = TileMode.REPEAT, alphaMode = GradientAlphaMode.PREMULTIPLIED),
            Shader.WithWorkingColorSpace(Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, ColorARGB.White), GradientStop(1f, ColorARGB.Black)),
                alphaMode = GradientAlphaMode.PREMULTIPLIED), ColorSpaceInterpolation.OKLAB),
        )
        unsupported.forEach(::assertRefusesAndRecovers)
    }

    @Test fun composedNonClampSingleStopRefusesBeforeCollapseAndRecovers() = assertRefusesAndRecovers(
        Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Transparent),
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, ColorARGB.White)), tileMode = TileMode.REPEAT,
                alphaMode = GradientAlphaMode.PREMULTIPLIED)),
    )

    @Test fun composedNonSrgbWorkingSpaceRefusesBeforePublicationAndRecovers() = assertRefusesAndRecovers(
        Shader.Blend(BlendMode.SRC_OVER, Shader.SolidColor(ColorARGB.Transparent),
            Shader.WithWorkingColorSpace(Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                listOf(GradientStop(0f, ColorARGB.White), GradientStop(1f, ColorARGB.Black)),
                alphaMode = GradientAlphaMode.PREMULTIPLIED), ColorSpaceInterpolation.OKLAB)),
    )

    private fun assertRefusesAndRecovers(source: Shader) {
        val surface = Surface(1, 1)
        surface.canvas { drawRect(onePixel, Paint(shader = source, antiAlias = false, blendMode = BlendMode.SRC)) }
        val sentinel = UByteArray(4) { 0x5au }
        val before = sentinel.copyOf()
        val refusal = assertFailsWith<IllegalStateException> { surface.readPixels(onePixel, sentinel) }
        assertTrue(refusal.message.orEmpty().startsWith("unsupported.material.gradient.alpha-mode:"),
            refusal.message)
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        val healthyColor = ColorARGB.Blue
        surface.canvas { drawRect(onePixel, Paint(healthyColor, antiAlias = false)) }
        repeat(2) { assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), renderTwice(surface).pixels) }
    }

    @Test fun premultipliedBudgetRefusesPreciselyAndRecovers() {
        // Static B = 25,220 bytes: W4a Rect target 4 + aligned readback 256 +
        // V/I/U capacities 16,384 + 4,096 + 4,096 (two Rects fit the existing
        // pool floors) + two V4 material uniforms (2 x (112-byte header +
        // 48-byte inverse-CTM coordinate op)) + one shared 2 x 32-byte stop slab.
        val budgetB = 25_220L
        val sharedStops = listOf(GradientStop(0f, ColorARGB.of(128, 255, 255, 255)),
            GradientStop(1f, ColorARGB.of(64, 0, 0, 255)))
        fun frame(budget: Long) = Surface(1, 1, config = RenderConfig(frameLocalBudgetBytes = budget)).also { surface ->
            surface.canvas {
                drawRect(onePixel, Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
                    sharedStops, alphaMode = GradientAlphaMode.PREMULTIPLIED), antiAlias = false, blendMode = BlendMode.SRC))
                drawRect(onePixel, Paint(shader = Shader.WithWorkingColorSpace(Shader.LinearGradient(
                    Point2F32(0f, 0f), Point2F32(1f, 0f), sharedStops, alphaMode = GradientAlphaMode.STRAIGHT),
                    ColorSpaceInterpolation.SRGB), antiAlias = false, blendMode = BlendMode.SRC))
            }
        }
        val expected = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,
            sharedStops[0].color, sharedStops[1].color, .5f, finalBlend = BlendMode.SRC,
            alphaMode = GradientAlphaMode.STRAIGHT)
        val accepted = frame(budgetB)
        W5fSurfacePixelFixtures.assertNativePixels(renderTwice(accepted), listOf(expected))
        val refused = frame(budgetB - 1L)
        val sentinel = UByteArray(4) { 0x5au }; val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { refused.readPixels(onePixel, sentinel) }
        assertEquals("resource.material.gradient.stop-budget", failure.message.orEmpty().substringBefore(':'), failure.message)
        assertContentEquals(before, sentinel)
        refused.discardRecordedOperations()
        refused.canvas { drawRect(onePixel, Paint(ColorARGB.Green, antiAlias = false)) }
        val healthy = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Green), finalBlend = BlendMode.SRC)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(refused), listOf(healthy)) }
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int, width: Int) {
        val offset = (y * width + x) * 4
        assertContentEquals(ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }

    @Test fun clampHardStopsAndDegenerateSourcesKeepAlphaMode() {
        val mode = GradientAlphaMode.PREMULTIPLIED
        val single = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(.3f, ColorARGB.of(128, 37, 151, 223))), alphaMode = mode)
        val singleExpected = W5fColorCpuOracle.expectedShaderTree(single, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(singleExpected)
        val singleSurface = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(onePixel, Paint(shader = single, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(singleSurface), listOf(singleExpected)) }

        val hardStops = listOf(GradientStop(0f, ColorARGB.of(128, 255, 0, 0)),
            GradientStop(.5f, ColorARGB.of(64, 0, 255, 0)),
            GradientStop(.5f, ColorARGB.of(128, 0, 0, 255)),
            GradientStop(1f, ColorARGB.of(64, 255, 255, 255)))
        val hard = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f), hardStops, alphaMode = mode)
        val hardExpected = W5fColorCpuOracle.expectedShaderTree(hard, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(hardExpected)
        val hardSurface = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(onePixel, Paint(shader = hard, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(hardSurface), listOf(hardExpected)) }

        val clampStops = listOf(GradientStop(0f, ColorARGB.of(128, 255, 0, 0)),
            GradientStop(1f, ColorARGB.of(64, 0, 0, 255)))
        val clamp = Shader.LinearGradient(Point2F32(1f, 0f), Point2F32(2f, 0f), clampStops, alphaMode = mode)
        val clampSurface = Surface(3, 1).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 1f), Paint(shader = clamp, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        val clampExpected = (0..2).map { x -> W5fColorCpuOracle.expectedShaderTree(clamp, finalBlend = BlendMode.SRC,
            devicePointF32 = Point2F32(x + .5f, .5f)).also(W5fSurfacePixelFixtures::requireBounded) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(clampSurface), clampExpected) }

        val opaque = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(1f, 0f),
            listOf(GradientStop(0f, ColorARGB.of(255, 255, 0, 0)), GradientStop(1f, ColorARGB.of(255, 0, 0, 255))),
            alphaMode = mode)
        val opaqueExpected = W5fColorCpuOracle.expectedShaderTree(opaque, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(opaqueExpected)
        val opaqueSurface = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(onePixel, Paint(shader = opaque, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(opaqueSurface), listOf(opaqueExpected)) }

        val last = ColorARGB.of(128, 0, 0, 255)
        val degenerate = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(0f, 0f),
            listOf(GradientStop(0f, ColorARGB.of(128, 255, 0, 0)), GradientStop(.5f, ColorARGB.Green),
                GradientStop(.5f, last), GradientStop(1f, last)), alphaMode = mode)
        val degenerateExpected = W5fColorCpuOracle.expectedShaderTree(degenerate, finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(degenerateExpected)
        val degenerateSurface = Surface(1, 1).also { surface -> surface.canvas {
            drawRect(onePixel, Paint(shader = degenerate, antiAlias = false, blendMode = BlendMode.SRC))
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(renderTwice(degenerateSurface), listOf(degenerateExpected)) }
    }

    private fun gradient(left: ColorARGB, right: ColorARGB, alphaMode: GradientAlphaMode): Surface = Surface(1, 1).also { surface ->
        surface.canvas {
            drawRect(onePixel, Paint(shader = Shader.LinearGradient(
                Point2F32(0f, 0f), Point2F32(1f, 0f), listOf(GradientStop(0f, left), GradientStop(1f, right)),
                alphaMode = alphaMode), antiAlias = false, blendMode = BlendMode.SRC))
        }
    }

    private fun expected(left: ColorARGB, right: ColorARGB, alphaMode: GradientAlphaMode) =
        W5fColorCpuOracle.expectedGradientPixel(org.graphiks.kanvas.paint.ColorSpaceInterpolation.SRGB,
            left, right, .5f, finalBlend = BlendMode.SRC, alphaMode = alphaMode)

    private fun assertDisjoint(left: WgslFloatEnvelopeV1Oracle.DrawResult,
        right: WgslFloatEnvelopeV1Oracle.DrawResult) {
        val l = left as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        val r = right as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(l.channels.take(3).zip(r.channels.take(3)).any { (a,b) -> a.intersect(b).isEmpty() })
    }

    private fun renderTwice(surface: Surface): RenderResult {
        val first = surface.render()
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        val second = surface.render()
        assertTrue(second.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), second.nativeEvidenceScopeKinds.toString())
        assertContentEquals(first.pixels, second.pixels)
        return first
    }
}
