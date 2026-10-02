@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

class W7RootAaMsaaDomainSurfacePixelTest {
    companion object {
        private var adapterEvidencePrinted = false

        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()

        @Synchronized private fun printAdapterEvidenceOnce() {
            if (adapterEvidencePrinted) return
            adapterEvidencePrinted = true
            println("W7 root AA native adapter=${GPUBackendRuntimeFactory.createOrNull()?.adapterInfo}")
        }

        private val vertical = W7MsaaCompositionCpuOracle.Stroke(4f, 2f, 4f, 10f, 5f)
        private val horizontal = W7MsaaCompositionCpuOracle.Stroke(2f, 4f, 10f, 4f, 5f)
        private val red = ColorARGB.of(255, 255, 0, 0)
        private val black = ColorARGB.Black
        private val white = ColorARGB.White
    }

    @Test
    fun linearRootStrokeSeparatesCoverageFromStoredColor() {
        val domain = CompositionDomain.LINEAR
        val scenes = listOf(verticalScene(), horizontalScene())
        // Build every expected code set before the first native Surface.render call.
        val expected = scenes.flatMap { scene -> listOf(
            expectedStroke(scene, domain, background = null, source = red),
            expectedStroke(scene, domain, background = null, source = black),
            expectedStroke(scene, domain, background = white, source = red),
            expectedStroke(scene, domain, background = white, source = black),
        ) }
        expected.forEach { case -> assertStrokeAnchors(case, 188, 128) }
        printStrokeExpectations("linearRootStrokeSeparatesCoverageFromStoredColor", expected)

        for (case in expected) {
            val surface = strokeSurface(case.scene.stroke, case.domain, case.background, case.source)
            val first = surface.render()
            assertNativeBeforePixels(first, case.label)
            case.pixels.forEach { (point, envelope) -> assertPixelEnvelope(first, point, envelope, case.label) }
            val second = surface.render()
            assertNativeBeforePixels(second, "${case.label} repeat")
            assertContentEquals(first.pixels, second.pixels, case.label)
        }
    }

    @Test
    fun linearRootMasksStayCorrelatedUntilResolve() {
        val domain = CompositionDomain.LINEAR
        val sameMaskPixel = W7MsaaCompositionCpuOracle.Pixel(1, 5)
        val repeatedMask = W7MsaaCompositionCpuOracle.strokeMask(vertical, sameMaskPixel)
        assertExactMask(repeatedMask, 0b1010)
        val repeatedExpected = W7MsaaCompositionCpuOracle.pixel(
            sameMaskPixel, domain, white, listOf(repeatedMask to black, repeatedMask to black),
        )
        val repeatedAlternative = W7MsaaCompositionCpuOracle.scalarResolvePerDraw(
            domain, white, black, .5f, drawCount = 2,
        )
        W7MsaaCompositionCpuOracle.assertDisjoint(repeatedExpected, repeatedAlternative)
        assertCodeSetIncludes(repeatedExpected, 188, "repeated correlated half mask")
        assertCodeSetIncludes(repeatedAlternative, 137, "repeated scalar-resolve alternative")

        val complementPixel = W7MsaaCompositionCpuOracle.Pixel(2, 4)
        val left = W7MsaaCompositionCpuOracle.boxMask(
            W7MsaaCompositionCpuOracle.Box(0f, 0f, 2.5f, 8f), complementPixel,
        )
        val right = W7MsaaCompositionCpuOracle.boxMask(
            W7MsaaCompositionCpuOracle.Box(2.5f, 0f, 8f, 8f), complementPixel,
        )
        assertExactMask(left, 0b0101); assertExactMask(right, 0b1010)
        assertTrue(left.bits and right.bits == 0, "complement masks must not overlap")
        assertTrue((left.bits or right.bits) == 0b1111, "complement masks must cover every sample")
        val complementExpected = W7MsaaCompositionCpuOracle.pixel(
            complementPixel, domain, white, listOf(left to black, right to black),
        )
        val complementAlternative = W7MsaaCompositionCpuOracle.resolvePerDrawComplement(domain)
        W7MsaaCompositionCpuOracle.assertDisjoint(complementExpected, complementAlternative)
        assertCodeSetIncludes(complementExpected, 0, "complementary retained masks")
        assertCodeSetIncludes(complementAlternative, 137, "complementary scalar-resolve alternative")

        val repeatedSurface = repeatedStrokeSurface(domain)
        val complementSurface = complementFillSurface(domain)
        printEnvelopeExpectations("linearRootMasksStayCorrelatedUntilResolve", listOf(
            "retained-repeated" to repeatedExpected,
            "scalar-repeated" to repeatedAlternative,
            "retained-complementary" to complementExpected,
            "scalar-complementary" to complementAlternative,
        ))
        val repeatedFirst = repeatedSurface.render()
        assertNativeBeforePixels(repeatedFirst, "linear repeated root stroke")
        assertPixelEnvelope(repeatedFirst, sameMaskPixel, repeatedExpected, "linear repeated root stroke")
        val repeatedSecond = repeatedSurface.render()
        assertNativeBeforePixels(repeatedSecond, "linear repeated root stroke repeat")
        assertContentEquals(repeatedFirst.pixels, repeatedSecond.pixels)

        val complementFirst = complementSurface.render()
        assertNativeBeforePixels(complementFirst, "linear complementary root fills")
        assertPixelEnvelope(complementFirst, complementPixel, complementExpected, "linear complementary root fills")
        val complementSecond = complementSurface.render()
        assertNativeBeforePixels(complementSecond, "linear complementary root fills repeat")
        assertContentEquals(complementFirst.pixels, complementSecond.pixels)
    }

    @Test
    fun encodedRootStrokeUsesSelectedDomain() {
        val domain = CompositionDomain.SRGB_ENCODED
        val scenes = listOf(verticalScene(), horizontalScene())
        val expectedByDomain = scenes.map { scene ->
            val encoded = listOf(
                expectedStroke(scene, domain, background = null, source = red),
                expectedStroke(scene, domain, background = null, source = black),
                expectedStroke(scene, domain, background = white, source = red),
                expectedStroke(scene, domain, background = white, source = black),
            )
            val linear = listOf(
                expectedStroke(scene, CompositionDomain.LINEAR, background = null, source = red),
                expectedStroke(scene, CompositionDomain.LINEAR, background = null, source = black),
                expectedStroke(scene, CompositionDomain.LINEAR, background = white, source = red),
                expectedStroke(scene, CompositionDomain.LINEAR, background = white, source = black),
            )
            encoded.zip(linear).forEach { (encodedCase, linearCase) ->
                val channel = domainWitnessChannel(encodedCase)
                if (channel != null) {
                    assertChannelDisjoint(
                        encodedCase.pixels.getValue(scene.edge), linearCase.pixels.getValue(scene.edge), channel,
                    )
                    assertChannelCodeIncludes(encodedCase.pixels.getValue(scene.edge), channel, 128, encodedCase.label)
                    assertChannelCodeIncludes(linearCase.pixels.getValue(scene.edge), channel, 188, linearCase.label)
                }
                assertStrokeAnchors(encodedCase, 188, 128)
            }
            encoded
        }.flatten()
        printStrokeExpectations("encodedRootStrokeUsesSelectedDomain", expectedByDomain)

        for (case in expectedByDomain) {
            val surface = strokeSurface(case.scene.stroke, case.domain, case.background, case.source)
            val first = surface.render()
            assertNativeBeforePixels(first, case.label)
            case.pixels.forEach { (point, envelope) -> assertPixelEnvelope(first, point, envelope, case.label) }
            val second = surface.render()
            assertNativeBeforePixels(second, "${case.label} repeat")
            assertContentEquals(first.pixels, second.pixels, case.label)
        }
    }

    @Test
    fun encodedRootMasksStayCorrelatedUntilResolve() {
        val domain = CompositionDomain.SRGB_ENCODED
        val sameMaskPixel = W7MsaaCompositionCpuOracle.Pixel(1, 5)
        val repeatedMask = W7MsaaCompositionCpuOracle.strokeMask(vertical, sameMaskPixel)
        assertExactMask(repeatedMask, 0b1010)
        val repeatedExpected = W7MsaaCompositionCpuOracle.pixel(
            sameMaskPixel, domain, white, listOf(repeatedMask to black, repeatedMask to black),
        )
        val repeatedAlternative = W7MsaaCompositionCpuOracle.scalarResolvePerDraw(domain, white, black, .5f, 2)
        W7MsaaCompositionCpuOracle.assertDisjoint(repeatedExpected, repeatedAlternative)
        assertCodeSetIncludes(repeatedExpected, 128, "encoded repeated correlated half mask")
        assertCodeSetIncludes(repeatedAlternative, 64, "encoded repeated scalar-resolve alternative")

        val complementPixel = W7MsaaCompositionCpuOracle.Pixel(2, 4)
        val left = W7MsaaCompositionCpuOracle.boxMask(
            W7MsaaCompositionCpuOracle.Box(0f, 0f, 2.5f, 8f), complementPixel,
        )
        val right = W7MsaaCompositionCpuOracle.boxMask(
            W7MsaaCompositionCpuOracle.Box(2.5f, 0f, 8f, 8f), complementPixel,
        )
        assertExactMask(left, 0b0101); assertExactMask(right, 0b1010)
        assertTrue(left.bits and right.bits == 0, "complement masks must not overlap")
        assertTrue((left.bits or right.bits) == 0b1111, "complement masks must cover every sample")
        val complementExpected = W7MsaaCompositionCpuOracle.pixel(
            complementPixel, domain, white, listOf(left to black, right to black),
        )
        val complementAlternative = W7MsaaCompositionCpuOracle.resolvePerDrawComplement(domain)
        W7MsaaCompositionCpuOracle.assertDisjoint(complementExpected, complementAlternative)
        assertCodeSetIncludes(complementExpected, 0, "encoded complementary retained masks")
        assertCodeSetIncludes(complementAlternative, 64, "encoded complementary scalar-resolve alternative")

        val repeatedSurface = repeatedStrokeSurface(domain)
        val complementSurface = complementFillSurface(domain)
        printEnvelopeExpectations("encodedRootMasksStayCorrelatedUntilResolve", listOf(
            "retained-repeated" to repeatedExpected,
            "scalar-repeated" to repeatedAlternative,
            "retained-complementary" to complementExpected,
            "scalar-complementary" to complementAlternative,
        ))
        val repeatedFirst = repeatedSurface.render()
        assertNativeBeforePixels(repeatedFirst, "encoded repeated root stroke")
        assertPixelEnvelope(repeatedFirst, sameMaskPixel, repeatedExpected, "encoded repeated root stroke")
        val repeatedSecond = repeatedSurface.render()
        assertNativeBeforePixels(repeatedSecond, "encoded repeated root stroke repeat")
        assertContentEquals(repeatedFirst.pixels, repeatedSecond.pixels)

        val complementFirst = complementSurface.render()
        assertNativeBeforePixels(complementFirst, "encoded complementary root fills")
        assertPixelEnvelope(complementFirst, complementPixel, complementExpected, "encoded complementary root fills")
        val complementSecond = complementSurface.render()
        assertNativeBeforePixels(complementSecond, "encoded complementary root fills repeat")
        assertContentEquals(complementFirst.pixels, complementSecond.pixels)
    }

    @Test
    fun linearRootMixedColorQualifiesResolveModel() {
        val cases = mixedExpectations()
        assertMixedDomainWitnesses(cases, "linearRootMixedColorQualifiesResolveModel")
        printMixedExpectations("linearRootMixedColorQualifiesResolveModel", cases)

        for (case in cases) {
            val surface = mixedSurface(CompositionDomain.LINEAR, case.background)
            val first = surface.render()
            assertNativeBeforePixels(first, "linear mixed ${backgroundLabel(case.background)}")
            case.linear.forEach { (point, envelope) ->
                assertPixelEnvelope(first, point, envelope, "linear mixed ${backgroundLabel(case.background)}")
            }
            val second = surface.render()
            assertNativeBeforePixels(second, "linear mixed ${backgroundLabel(case.background)} repeat")
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    @Test
    fun encodedRootMixedColorUsesSelectedDomain() {
        val cases = mixedExpectations()
        assertMixedDomainWitnesses(cases, "encodedRootMixedColorUsesSelectedDomain")
        printMixedExpectations("encodedRootMixedColorUsesSelectedDomain", cases)

        for (case in cases) {
            val surface = mixedSurface(CompositionDomain.SRGB_ENCODED, case.background)
            val first = surface.render()
            assertNativeBeforePixels(first, "encoded mixed ${backgroundLabel(case.background)}")
            case.encoded.forEach { (point, envelope) ->
                assertPixelEnvelope(first, point, envelope, "encoded mixed ${backgroundLabel(case.background)}")
            }
            val second = surface.render()
            assertNativeBeforePixels(second, "encoded mixed ${backgroundLabel(case.background)} repeat")
            assertContentEquals(first.pixels, second.pixels)
        }
    }

    private data class MixedCase(
        val background: ColorARGB?,
        val linear: Map<W7MsaaCompositionCpuOracle.Pixel, W7CompositionCpuOracle.CompositionEnvelope>,
        val encoded: Map<W7MsaaCompositionCpuOracle.Pixel, W7CompositionCpuOracle.CompositionEnvelope>,
    )

    private fun mixedExpectations(): List<MixedCase> {
        val scene = verticalScene()
        assertSceneMasks(scene)
        val source = ColorARGB.of(128, 128, 64, 32)
        val points = listOf(scene.edge, scene.farEdge, scene.interior) + scene.outside
        return listOf<ColorARGB?>(null, white).map { background ->
            fun expected(domain: CompositionDomain) = points.associateWith { point ->
                val mask = W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, point)
                W7MsaaCompositionCpuOracle.pixel(point, domain, background, listOf(mask to source))
            }
            MixedCase(
                background = background,
                linear = expected(CompositionDomain.LINEAR),
                encoded = expected(CompositionDomain.SRGB_ENCODED),
            )
        }
    }

    private fun assertMixedDomainWitnesses(cases: List<MixedCase>, label: String) {
        val halfPixels = listOf(
            W7MsaaCompositionCpuOracle.Pixel(1, 5),
            W7MsaaCompositionCpuOracle.Pixel(6, 5),
        )
        for (case in cases) for (point in halfPixels) {
            val linear = case.linear.getValue(point)
            val encoded = case.encoded.getValue(point)
            assertTrue(
                (0..2).any { channel -> linear.channels[channel].intersect(encoded.channels[channel]).isEmpty() },
                "$label ${backgroundLabel(case.background)} pixel=$point has no disjoint RGB code set: " +
                    "linear=${linear.channels} encoded=${encoded.channels}",
            )
            assertTrue(
                linear.channels[3].intersect(encoded.channels[3]).isNotEmpty(),
                "$label ${backgroundLabel(case.background)} pixel=$point alpha should not discriminate the domain",
            )
        }
    }

    private fun printMixedExpectations(label: String, cases: List<MixedCase>) {
        for (case in cases) {
            case.linear.forEach { (point, envelope) ->
                printExpected("$label domain=LINEAR background=${backgroundLabel(case.background)} pixel=$point", envelope)
            }
            case.encoded.forEach { (point, envelope) ->
                printExpected("$label domain=SRGB_ENCODED background=${backgroundLabel(case.background)} pixel=$point", envelope)
            }
        }
    }

    private fun backgroundLabel(background: ColorARGB?) = if (background == null) "transparent" else "white"

    private fun mixedSurface(domain: CompositionDomain, background: ColorARGB?): Surface =
        Surface(12, 12, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas {
                if (background != null) drawPath(fullPath(12f, 12f), Paint(background, antiAlias = false))
                drawPath(path(vertical), Paint(
                    ColorARGB.of(128, 128, 64, 32), style = PaintStyle.STROKE, strokeWidth = 5f,
                    strokeCap = StrokeCap.BUTT, strokeJoin = StrokeJoin.MITER, strokeMiter = 4f,
                    antiAlias = true,
                ))
            }
        }

    private data class StrokeScene(
        val stroke: W7MsaaCompositionCpuOracle.Stroke,
        val edge: W7MsaaCompositionCpuOracle.Pixel,
        val farEdge: W7MsaaCompositionCpuOracle.Pixel,
        val interior: W7MsaaCompositionCpuOracle.Pixel,
        val outside: List<W7MsaaCompositionCpuOracle.Pixel>,
        val horizontal: Boolean,
    )

    private data class ExpectedStroke(
        val label: String,
        val scene: StrokeScene,
        val background: ColorARGB?,
        val source: ColorARGB,
        val domain: CompositionDomain,
        val pixels: Map<W7MsaaCompositionCpuOracle.Pixel, W7CompositionCpuOracle.CompositionEnvelope>,
    )

    private fun verticalScene() = StrokeScene(
        vertical, W7MsaaCompositionCpuOracle.Pixel(1, 5), W7MsaaCompositionCpuOracle.Pixel(6, 5),
        W7MsaaCompositionCpuOracle.Pixel(3, 5), listOf(
            W7MsaaCompositionCpuOracle.Pixel(0, 5), W7MsaaCompositionCpuOracle.Pixel(7, 5),
        ), horizontal = false,
    )

    private fun horizontalScene() = StrokeScene(
        horizontal, W7MsaaCompositionCpuOracle.Pixel(5, 1), W7MsaaCompositionCpuOracle.Pixel(5, 6),
        W7MsaaCompositionCpuOracle.Pixel(5, 3), listOf(
            W7MsaaCompositionCpuOracle.Pixel(5, 0), W7MsaaCompositionCpuOracle.Pixel(5, 7),
        ), horizontal = true,
    )

    private fun expectedStroke(
        scene: StrokeScene,
        domain: CompositionDomain,
        background: ColorARGB?,
        source: ColorARGB,
    ): ExpectedStroke {
        assertSceneMasks(scene)
        val points = listOf(scene.edge, scene.farEdge, scene.interior) + scene.outside
        val draws = points.associateWith { point ->
            listOf(W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, point) to source)
        }
        val expected = points.associateWith { point ->
            W7MsaaCompositionCpuOracle.pixel(point, domain, background, draws.getValue(point))
        }
        val label = "domain=$domain source=${if (source == red) "red" else "black"} " +
            "background=${if (background == null) "transparent" else "white"} " +
            "stroke=${if (scene.horizontal) "horizontal" else "vertical"}"
        return ExpectedStroke(label, scene, background, source, domain, expected)
    }

    private fun assertSceneMasks(scene: StrokeScene) {
        val expectedEdges = if (scene.horizontal) 0b1100 to 0b0011 else 0b1010 to 0b0101
        assertExactMask(W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, scene.edge), expectedEdges.first)
        assertExactMask(W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, scene.farEdge), expectedEdges.second)
        assertExactMask(W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, scene.interior), 0b1111)
        scene.outside.forEach { point ->
            assertExactMask(W7MsaaCompositionCpuOracle.strokeMask(scene.stroke, point), 0b0000)
        }
    }

    private fun domainWitnessChannel(case: ExpectedStroke): Int? = when {
        case.background == null && case.source == red -> 0
        case.background == null -> null
        case.source == red -> 1
        else -> 0
    }

    private fun assertChannelDisjoint(
        left: W7CompositionCpuOracle.CompositionEnvelope,
        right: W7CompositionCpuOracle.CompositionEnvelope,
        channel: Int,
    ) {
        assertTrue(
            left.channels[channel].intersect(right.channels[channel]).isEmpty(),
            "channel=$channel expected disjoint sets: ${left.channels[channel]} vs ${right.channels[channel]}",
        )
    }

    private fun assertStrokeAnchors(case: ExpectedStroke, linearHalf: Int, encodedHalf: Int) {
        val edge = case.pixels.getValue(case.scene.edge)
        val far = case.pixels.getValue(case.scene.farEdge)
        val interior = case.pixels.getValue(case.scene.interior)
        val outside = case.scene.outside.map(case.pixels::getValue)
        val expectedHalf = if (case.domain == CompositionDomain.LINEAR) linearHalf else encodedHalf
        if (case.background == null) {
            val expectedRed = if (case.source == red) expectedHalf else 0
            assertChannelCodeIncludes(edge, 0, expectedRed, case.label)
            assertChannelCodeIncludes(far, 0, expectedRed, case.label)
            assertChannelCodeIncludes(edge, 3, 128, "half alpha ${case.label}")
            val interiorRed = if (case.source == red) 255 else 0
            assertChannelCodeIncludes(interior, 0, interiorRed, "opaque stroke interior ${case.label}")
            assertChannelCodeIncludes(interior, 3, 255, "opaque alpha ${case.label}")
            outside.forEach {
                assertCodeSetIncludes(it, 0, "transparent exterior ${case.label}")
                assertChannelCodeIncludes(it, 3, 0, "transparent exterior alpha ${case.label}")
            }
        } else {
            val (redEdge, greenEdge, blueEdge) = if (case.source == black)
                Triple(expectedHalf, expectedHalf, expectedHalf)
            else Triple(255, expectedHalf, expectedHalf)
            assertChannelCodeIncludes(edge, 0, redEdge, case.label)
            assertChannelCodeIncludes(edge, 1, greenEdge, case.label)
            assertChannelCodeIncludes(edge, 2, blueEdge, case.label)
            assertChannelCodeIncludes(far, 0, redEdge, case.label)
            assertChannelCodeIncludes(far, 1, greenEdge, case.label)
            assertChannelCodeIncludes(far, 2, blueEdge, case.label)
            assertChannelCodeIncludes(edge, 3, 255, "opaque white edge ${case.label}")
            assertChannelCodeIncludes(far, 3, 255, "opaque white far edge ${case.label}")
            val interiorColor = if (case.source == red) listOf(255, 0, 0) else listOf(0, 0, 0)
            interiorColor.forEachIndexed { channel, code ->
                assertChannelCodeIncludes(interior, channel, code, "opaque stroke interior ${case.label}")
            }
            assertChannelCodeIncludes(interior, 3, 255, "opaque stroke alpha ${case.label}")
            assertCodeSetIncludes(outside, 255, "white background exterior ${case.label}")
            outside.forEach { assertChannelCodeIncludes(it, 3, 255, "white background alpha ${case.label}") }
        }
    }

    private fun strokeSurface(
        stroke: W7MsaaCompositionCpuOracle.Stroke,
        domain: CompositionDomain,
        background: ColorARGB?,
        source: ColorARGB,
    ): Surface = Surface(12, 12, config = RenderConfig(compositionDomain = domain)).also { surface ->
        surface.canvas {
            if (background != null) drawPath(fullPath(12f, 12f), Paint(background, antiAlias = false))
            drawPath(path(stroke), Paint(
                source, style = PaintStyle.STROKE, strokeWidth = stroke.width,
                strokeCap = StrokeCap.BUTT, strokeJoin = StrokeJoin.MITER, strokeMiter = 4f,
                antiAlias = true,
            ))
        }
    }

    private fun repeatedStrokeSurface(domain: CompositionDomain) =
        Surface(12, 12, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas {
                drawPath(fullPath(12f, 12f), Paint(white, antiAlias = false))
                val strokePath = path(vertical)
                val paint = Paint(black, style = PaintStyle.STROKE, strokeWidth = 5f,
                    strokeCap = StrokeCap.BUTT, strokeJoin = StrokeJoin.MITER, antiAlias = true)
                drawPath(strokePath, paint); drawPath(strokePath, paint)
            }
        }

    private fun complementFillSurface(domain: CompositionDomain) =
        Surface(8, 8, config = RenderConfig(compositionDomain = domain)).also { surface ->
            surface.canvas {
                drawPath(fullPath(8f, 8f), Paint(white, antiAlias = false))
                drawPath(rectPath(0f, 0f, 2.5f, 8f), Paint(black, antiAlias = true))
                drawPath(rectPath(2.5f, 0f, 8f, 8f), Paint(black, antiAlias = true))
            }
        }

    private fun path(stroke: W7MsaaCompositionCpuOracle.Stroke) = Path().apply {
        moveTo(stroke.x1, stroke.y1); lineTo(stroke.x2, stroke.y2)
    }

    private fun fullPath(width: Float, height: Float) = rectPath(0f, 0f, width, height)
    private fun rectPath(left: Float, top: Float, right: Float, bottom: Float) = Path().apply {
        addRect(RectF32.ofLTRB(left, top, right, bottom))
    }

    private fun assertNativeBeforePixels(result: RenderResult, label: String) {
        val trace = "$label diagnostics=${result.diagnostics.summary()} native=${result.nativeEvidenceScopeKinds} " +
            "dispatched=${result.stats.opsDispatched} refused=${result.stats.opsRefused}"
        assertTrue(result.isClean, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertTrue(result.stats.opsRefused == 0, trace)
        printAdapterEvidenceOnce()
    }

    private fun assertPixelEnvelope(
        result: RenderResult,
        point: W7MsaaCompositionCpuOracle.Pixel,
        expected: W7CompositionCpuOracle.CompositionEnvelope,
        label: String,
    ) {
        assertTrue(point.x in 0 until result.width && point.y in 0 until result.height,
            "$label pixel=$point outside ${result.width}x${result.height}")
        assertTrue(result.pixels.size == result.width * result.height * 4,
            "$label pixel extent=${result.pixels.size} for ${result.width}x${result.height}")
        val offset = (point.y * result.width + point.x) * 4
        W7CompositionCpuOracle.assertAdmits(expected, result.pixels.copyOfRange(offset, offset + 4))
    }

    private fun assertCodeSetIncludes(
        expected: W7CompositionCpuOracle.CompositionEnvelope,
        code: Int,
        label: String,
    ) {
        assertTrue(expected.channels.take(3).any { code in it }, "$label expected code $code in ${expected.channels}")
    }

    private fun assertChannelCodeIncludes(
        expected: W7CompositionCpuOracle.CompositionEnvelope,
        channel: Int,
        code: Int,
        label: String,
    ) {
        assertTrue(code in expected.channels[channel], "$label expected channel=$channel code=$code in ${expected.channels}")
    }

    private fun assertCodeSetIncludes(
        expected: List<W7CompositionCpuOracle.CompositionEnvelope>,
        code: Int,
        label: String,
    ) = expected.forEach { assertCodeSetIncludes(it, code, label) }

    private fun printStrokeExpectations(label: String, cases: List<ExpectedStroke>) {
        cases.forEach { case ->
            case.pixels.forEach { (point, envelope) -> printExpected("$label ${case.label} pixel=$point", envelope) }
        }
    }

    private fun printEnvelopeExpectations(
        label: String,
        envelopes: List<Pair<String, W7CompositionCpuOracle.CompositionEnvelope>>,
    ) {
        envelopes.forEach { (name, envelope) -> printExpected("$label $name", envelope) }
    }

    private fun printExpected(label: String, envelope: W7CompositionCpuOracle.CompositionEnvelope) {
        println("W7 expected[$label] channels=${envelope.channels} stores=${envelope.storeTrace}")
    }

    private fun assertExactMask(mask: W7MsaaCompositionCpuOracle.Mask, bits: Int) {
        assertTrue(mask.bits == bits, "mask=${mask.bits.toString(2)} != ${bits.toString(2)}")
    }
}
