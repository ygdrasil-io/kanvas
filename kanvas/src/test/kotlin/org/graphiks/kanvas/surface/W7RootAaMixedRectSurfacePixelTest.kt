@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Frozen public Surface witnesses for encoded hard Rect + root Path AA composition. */
class W7RootAaMixedRectSurfacePixelTest {
    companion object {
        private const val SIZE = 12
        private val full = RectF32.ofLTRB(0f, 0f, 12f, 12f)
        private val vertical = W7MsaaCompositionCpuOracle.Stroke(4f, 2f, 4f, 10f, 5f)
        private val black = ColorARGB.Black
        private val white = ColorARGB.White
        private val mixed = ColorARGB.of(128, 64, 128, 192)
        private val edge = listOf(Pixel(1, 5), Pixel(6, 5), Pixel(3, 5), Pixel(0, 5), Pixel(7, 5))

        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    private data class Pixel(val x: Int, val y: Int)

    @Test fun linearMixedRectPathDomainControl() {
        val expectations = wholeExpected(CompositionDomain.LINEAR, white) { pathMask(it) to black }
        assertMaskAnchors()
        val surface = scene(CompositionDomain.LINEAR) { drawRect(full, hard(white)); drawPath(line(), stroke()) }
        assertExpectedRepeated(surface, expectations, "linear mixed Rect/Path")
    }

    @Test fun encodedMixedRectBackgroundKeepsAllPixels() {
        assertMaskAnchors()
        val expectedByFormat = listOf(PixelFormat.RGBA8, PixelFormat.BGRA8).associateWith { format ->
            wholeExpected(CompositionDomain.SRGB_ENCODED, white, format) { pathMask(it) to black }
        }
        assertTrue(expectedByFormat.values.flattenEnvelopes().any { it.channels[0].contains(127) || it.channels[0].contains(128) })
        for ((format, expectations) in expectedByFormat) {
            val surface = Surface(SIZE, SIZE, format, RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED))
            surface.canvas { drawRect(full, hard(white)); drawPath(line(), stroke()) }
            assertExpectedRepeated(surface, expectations, "encoded background $format")
        }
    }

    @Test fun encodedMixedRectOrderPreservesFullReplacement() {
        val whiteExpected = solidImage(white)
        val blackExpected = solidImage(black)
        val whiteSurface = scene(CompositionDomain.SRGB_ENCODED) { drawPath(line(), stroke()); drawRect(full, hard(white)) }
        val blackSurface = scene(CompositionDomain.SRGB_ENCODED) { drawPath(line(), stroke()); drawRect(full, hard(black)) }
        assertEveryPixelRepeated(whiteSurface, whiteExpected, "Path then full white Rect")
        assertEveryPixelRepeated(blackSurface, blackExpected, "old full black Rect exclusion")
    }

    @Test fun encodedMixedRectRetainsCorrelatedSamples() {
        val points = listOf(Pixel(1, 5), Pixel(6, 5), Pixel(3, 5), Pixel(0, 5), Pixel(7, 5))
        val domains = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)
        val expectedByDomain = domains.associateWith { domain ->
            points.associateWith { point ->
                val draws = listOf(pathMask(point) to black, boxMask(point, 4f, 0f, 12f, 12f) to white,
                    pathMask(point) to black)
                expected(point, domain, white, draws)
            }
        }
        for (domain in domains) {
            val correlated = expected(Pixel(1, 5), domain, white,
                listOf(pathMask(Pixel(1, 5)) to black, boxMask(Pixel(1, 5), 4f, 0f, 12f, 12f) to white, pathMask(Pixel(1, 5)) to black))
            val scalar = W7MsaaCompositionCpuOracle.scalarResolvePerDraw(domain, white, black, .5f, 2)
            W7MsaaCompositionCpuOracle.assertDisjoint(correlated, scalar)
        }
        assertMaskAnchors()
        for (domain in domains) {
            val surface = scene(domain) {
                drawRect(full, hard(white)); drawPath(line(), stroke())
                drawRect(RectF32.ofLTRB(4f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke())
            }
            assertExpectedRepeated(surface, expectedByDomain.getValue(domain), "correlated repeated path $domain")
        }
    }

    @Test fun encodedMixedRectAlphaAndDomainStayDistinct() {
        val points = listOf(Pixel(0, 5), Pixel(1, 5), Pixel(3, 5), Pixel(6, 5), Pixel(7, 5))
        // Freeze the complete target-format expectation matrix before rendering.
        val domainSequence = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED, CompositionDomain.LINEAR)
        val expectations = listOf(PixelFormat.RGBA8, PixelFormat.BGRA8).associateWith { format ->
            domainSequence.map { domain -> domain to points.associateWith { point -> expected(point, domain, null,
                listOf(W7MsaaCompositionCpuOracle.Mask(0b1111) to mixed, pathMask(point) to black), format) } }
        }
        for (domain in listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED)) {
            val source = W7CompositionCpuOracle.solid(mixed, domain)
            val once = W7CompositionCpuOracle.drawOnClear(source, domain)
            val twiceAppliedAlpha = W7CompositionCpuOracle.drawOnClear(W7CompositionCpuOracle.opacity(source, 128), domain)
            W7MsaaCompositionCpuOracle.assertDisjoint(once, twiceAppliedAlpha)
        }
        for (format in expectations.keys) {
            for ((index, entry) in expectations.getValue(format).withIndex()) {
                val (domain, expectedPixels) = entry
                val surface = Surface(SIZE, SIZE, format, RenderConfig(compositionDomain = domain))
                surface.canvas { drawRect(full, hard(mixed)); drawPath(line(), stroke()) }
                assertExpectedRepeated(surface, expectedPixels, "$domain alpha $format pass=$index")
            }
        }
    }

    @Test fun encodedMixedRectTranslationAndClipPreserveDeviceMasks() {
        val points = listOf(Pixel(2, 6), Pixel(4, 6), Pixel(1, 6), Pixel(7, 6), Pixel(0, 6))
        val expectedByDomain = listOf(CompositionDomain.LINEAR, CompositionDomain.SRGB_ENCODED).associateWith { domain ->
            points.associateWith { point ->
                val clipMask = boxMask(point, 0f, 0f, 7f, 12f)
                val translatedRectMask = boxMask(point, 1f, 1f, 13f, 13f)
                val rectMaskInDeviceClip = W7MsaaCompositionCpuOracle.Mask(translatedRectMask.bits and clipMask.bits)
                val translatedPath = W7MsaaCompositionCpuOracle.strokeMask(
                    W7MsaaCompositionCpuOracle.Stroke(5f, 3f, 5f, 11f, 5f),
                    W7MsaaCompositionCpuOracle.Pixel(point.x, point.y),
                )
                val mask = W7MsaaCompositionCpuOracle.Mask(translatedPath.bits and clipMask.bits)
                expected(point, domain, null, listOf(rectMaskInDeviceClip to white, mask to black))
            }
        }
        assertEquals(0b1010, W7MsaaCompositionCpuOracle.strokeMask(
            W7MsaaCompositionCpuOracle.Stroke(5f, 3f, 5f, 11f, 5f), W7MsaaCompositionCpuOracle.Pixel(2, 6)).bits)
        for (domain in expectedByDomain.keys) {
            val surface = scene(domain) {
                clipRect(RectF32.ofLTRB(0f, 0f, 7f, 12f), antiAlias = false); save(); translate(1f, 1f)
                drawRect(RectF32.ofLTRB(0f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke()); restore()
            }
            assertExpectedRepeated(surface, expectedByDomain.getValue(domain), "clip translation $domain")
        }
    }

    @Test fun encodedMixedRectPictureReplaysIndependentPixels() {
        val points = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> Pixel(x, y) } }
        val expectations = points.associateWith { point -> expected(point, CompositionDomain.SRGB_ENCODED, white,
            listOf(pathMask(point) to black, boxMask(point, 4f, 0f, 12f, 12f) to white, pathMask(point) to black)) }
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(full).apply {
                drawRect(full, hard(white)); drawPath(line(), stroke())
                drawRect(RectF32.ofLTRB(4f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke())
            }
        }.finishRecordingAsPicture()
        val archived = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        val direct = scene(CompositionDomain.SRGB_ENCODED) {
            drawRect(full, hard(white)); drawPath(line(), stroke())
            drawRect(RectF32.ofLTRB(4f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke())
        }
        val fromPicture = scene(CompositionDomain.SRGB_ENCODED) { picture.playback(this) }
        val fromArchive = scene(CompositionDomain.SRGB_ENCODED) { archived.playback(this) }
        for ((label, surface) in listOf("direct" to direct, "picture" to fromPicture, "archive" to fromArchive)) {
            assertExpectedRepeated(surface, expectations, "encoded $label")
        }
        assertContentEquals(direct.render().pixels, fromPicture.render().pixels)
        assertContentEquals(direct.render().pixels, fromArchive.render().pixels)
    }

    @Test fun encodedMixedRectPictureWrapperRemainsTransactional() {
        val points = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> Pixel(x, y) } }
        val expectations = points.associateWith { point -> expected(point, CompositionDomain.SRGB_ENCODED, white,
            listOf(pathMask(point) to black, boxMask(point, 4f, 0f, 12f, 12f) to white, pathMask(point) to black)) }
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(full).apply {
                drawRect(full, hard(white)); drawPath(line(), stroke())
                drawRect(RectF32.ofLTRB(4f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke())
            }
        }.finishRecordingAsPicture()
        val archived = requireNotNull(Picture.fromByteArray(picture.toByteArray()))
        for ((label, wrapper) in listOf("memory" to picture, "archive" to archived)) {
            val surface = scene(CompositionDomain.SRGB_ENCODED) { drawPicture(wrapper) }
            val sentinel = UByteArray(SIZE * SIZE * 4) { 0x5au }
            val failure = assertFailsWith<IllegalStateException>(label) {
                surface.readPixels(full, sentinel)
            }
            assertEquals("unsupported.surface.composition.geometry", failure.message.orEmpty().substringBefore(':'), label)
            assertContentEquals(UByteArray(SIZE * SIZE * 4) { 0x5au }, sentinel, label)
            surface.discardRecordedOperations()
            surface.canvas {
                drawRect(full, hard(white)); drawPath(line(), stroke())
                drawRect(RectF32.ofLTRB(4f, 0f, 12f, 12f), hard(white)); drawPath(line(), stroke())
            }
            assertExpectedRepeated(surface, expectations, "$label wrapper direct-C recovery")
        }
    }

    @Test fun encodedMixedRectBudgetBoundaryIsTransactional() {
        val budget = 27_392L
        val points = listOf(Pixel(1, 4), Pixel(6, 4), Pixel(3, 4), Pixel(0, 4), Pixel(7, 4))
        val expectations = points.associateWith { point -> expected(point, CompositionDomain.SRGB_ENCODED, white,
            listOf(pathMask(point) to black)) }
        fun fixture(limit: Long) = Surface(8, 8, config = RenderConfig(
            compositionDomain = CompositionDomain.SRGB_ENCODED, frameLocalBudgetBytes = limit,
        )).also { it.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 8f, 8f), hard(white)); drawPath(
            Path().apply { moveTo(4f, 2f); lineTo(4f, 6f) }, stroke()) } }
        assertEquals(27_392L, 256L + 24_576L + 1_024L + 1_024L + 256L + 256L)
        assertExpectedRepeated(fixture(budget), expectations, "budget admitted")
        val refused = fixture(budget - 1)
        val sentinel = UByteArray(8 * 8 * 4) { 0x5au }
        val failure = assertFailsWith<IllegalStateException> {
            refused.readPixels(RectF32.ofLTRB(0f, 0f, 8f, 8f), sentinel)
        }
        assertEquals("w4d.general.budget.frame-local-exceeded", failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(UByteArray(8 * 8 * 4) { 0x5au }, sentinel)
        refused.discardRecordedOperations()
        repeat(2) {
            val recovered = refused.render()
            assertNative(recovered, "budget recovery")
            assertEveryPixel(recovered, solidImage(ColorARGB.of(0, 0, 0, 0)), "budget recovery")
        }
    }

    @Test fun encodedMixedRectExcludedSiblingsAreTransactional() {
        val fractional = RectF32.ofLTRB(.5f, 0f, 12f, 12f)
        val overI32 = RectF32.ofLTRB(0f, 0f, 2_147_483_648f, 12f)
        val gradient = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(12f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        val cases: List<Triple<String, String, Canvas.() -> Unit>> = listOf(
            Triple("fractional-bounds", "geometry") { drawRect(fractional, hard(white)) },
            Triple("rect-aa", "geometry") { drawRect(full, Paint(white, antiAlias = true)) },
            Triple("rect-stroke-zero", "geometry") { drawRect(full, Paint(white, style = PaintStyle.STROKE, strokeWidth = 0f)) },
            Triple("rect-scale", "geometry") { scale(2f, 2f); drawRect(full, hard(white)) },
            Triple("rect-fractional-translation", "geometry") { translate(.5f, 0f); drawRect(full, hard(white)) },
            Triple("rect-i32-overflow", "geometry") { drawRect(overI32, hard(white)) },
            Triple("empty-rect", "geometry") { drawRect(RectF32.ofLTRB(2f, 2f, 2f, 8f), hard(white)) },
            Triple("inverted-rect", "geometry") { drawRect(RectF32.ofLTRB(8f, 2f, 2f, 8f), hard(white)) },
            Triple("rect-gradient", "geometry") { drawRect(full, Paint(shader = gradient, antiAlias = false)) },
            Triple("rect-image-filter", "geometry") { drawRect(full, Paint(white, imageFilter = ImageFilter.Offset(1f, 0f), antiAlias = false)) },
            Triple("rect-src", "geometry") { drawRect(full, Paint(white, blendMode = BlendMode.SRC, antiAlias = false)) },
            Triple("layer", "layer") { saveLayer(); restore() },
            Triple("aa-clip", "geometry") { clipRect(full, antiAlias = true) },
        )
        val recovery = edge.associateWith { point -> expected(point, CompositionDomain.SRGB_ENCODED, null,
            listOf(pathMask(point) to black)) }
        for ((name, category, append) in cases) {
            val surface = scene(CompositionDomain.SRGB_ENCODED) { drawPath(line(), stroke()); save(); append(); restore() }
            val sentinel = UByteArray(SIZE * SIZE * 4) { 0x5au }
            val failure = assertFailsWith<IllegalStateException>(name) {
                surface.readPixels(full, sentinel)
            }
            assertEquals("unsupported.surface.composition.$category", failure.message.orEmpty().substringBefore(':'), name)
            assertContentEquals(UByteArray(SIZE * SIZE * 4) { 0x5au }, sentinel, name)
            surface.discardRecordedOperations()
            surface.canvas { resetMatrix(); drawPath(line(), stroke()) }
            repeat(2) { assertExpected(surface.render(), recovery, "$name recovery") }
        }
    }

    private fun scene(domain: CompositionDomain, block: Canvas.() -> Unit) =
        Surface(SIZE, SIZE, config = RenderConfig(compositionDomain = domain)).also { it.canvas(block) }

    private fun line() = Path().apply { moveTo(4f, 2f); lineTo(4f, 10f) }
    private fun hard(color: ColorARGB) = Paint(color, antiAlias = false)
    private fun stroke() = Paint(black, style = PaintStyle.STROKE, strokeWidth = 5f,
        strokeCap = org.graphiks.kanvas.paint.StrokeCap.BUTT,
        strokeJoin = org.graphiks.kanvas.paint.StrokeJoin.MITER, strokeMiter = 4f, antiAlias = true)
    private fun pathMask(point: Pixel) = W7MsaaCompositionCpuOracle.strokeMask(vertical,
        W7MsaaCompositionCpuOracle.Pixel(point.x, point.y))
    private fun boxMask(point: Pixel, l: Float, t: Float, r: Float, b: Float) = W7MsaaCompositionCpuOracle.boxMask(
        W7MsaaCompositionCpuOracle.Box(l, t, r, b), W7MsaaCompositionCpuOracle.Pixel(point.x, point.y))
    private fun expected(point: Pixel, domain: CompositionDomain, background: ColorARGB?, draws: List<Pair<W7MsaaCompositionCpuOracle.Mask, ColorARGB>>, format: PixelFormat = PixelFormat.RGBA8) =
        W7CompositionCpuOracle.swizzle(W7MsaaCompositionCpuOracle.pixel(
            W7MsaaCompositionCpuOracle.Pixel(point.x, point.y), domain, background, draws), format)

    private fun wholeExpected(domain: CompositionDomain, background: ColorARGB?, format: PixelFormat = PixelFormat.RGBA8,
        draws: (Pixel) -> Pair<W7MsaaCompositionCpuOracle.Mask, ColorARGB>) =
        (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> Pixel(x, y) } }
            .associateWith { point -> expected(point, domain, background, listOf(draws(point)), format) }

    private fun assertMaskAnchors() {
        assertEquals(0b1010, pathMask(Pixel(1, 5)).bits)
        assertEquals(0b0101, pathMask(Pixel(6, 5)).bits)
        assertEquals(0b1111, pathMask(Pixel(3, 5)).bits)
        assertEquals(0, pathMask(Pixel(0, 5)).bits)
        assertEquals(0, pathMask(Pixel(7, 5)).bits)
    }

    private fun assertExpectedRepeated(surface: Surface, expected: Map<Pixel, W7CompositionCpuOracle.CompositionEnvelope>, label: String) {
        val first = surface.render(); assertNative(first, label); assertExpected(first, expected, label)
        val second = surface.render(); assertNative(second, "$label repeat"); assertExpected(second, expected, "$label repeat")
        assertContentEquals(first.pixels, second.pixels, label)
    }
    private fun assertExpected(result: RenderResult, expected: Map<Pixel, W7CompositionCpuOracle.CompositionEnvelope>, label: String) {
        expected.forEach { (point, envelope) ->
            val offset = (point.y * result.width + point.x) * 4
            envelope.channels.forEachIndexed { channel, codes -> assertTrue(result.pixels[offset + channel].toInt() in codes,
                "$label pixel=${point.x},${point.y} channel=$channel observed=${result.pixels[offset + channel]} expected=$codes") }
        }
    }
    private fun assertEveryPixelRepeated(surface: Surface, pixel: W7CompositionCpuOracle.CompositionEnvelope, label: String) {
        val expected = (0 until SIZE).flatMap { y -> (0 until SIZE).map { x -> Pixel(x, y) }}.associateWith { pixel }
        assertExpectedRepeated(surface, expected, label)
    }
    private fun assertEveryPixel(result: RenderResult, pixel: W7CompositionCpuOracle.CompositionEnvelope, label: String) =
        assertExpected(result, (0 until result.height).flatMap { y -> (0 until result.width).map { x -> Pixel(x, y) }}.associateWith { pixel }, label)
    private fun solidImage(color: ColorARGB) = W7CompositionCpuOracle.store(W7CompositionCpuOracle.solid(color,
        CompositionDomain.SRGB_ENCODED), CompositionDomain.SRGB_ENCODED)
    private fun assertNative(result: RenderResult, label: String) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), "$label scopes=${result.nativeEvidenceScopeKinds}")
        assertTrue(result.diagnostics.isEmpty, "$label ${result.diagnostics}")
        assertEquals(0, result.stats.opsRefused, "$label ${result.diagnostics}")
    }
    private fun Iterable<Map<Pixel, W7CompositionCpuOracle.CompositionEnvelope>>.flattenEnvelopes() = flatMap { it.values }
}
