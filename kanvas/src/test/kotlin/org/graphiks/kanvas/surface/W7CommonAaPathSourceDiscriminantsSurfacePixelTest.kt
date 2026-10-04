@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public pixel/refusal discriminants kept separate from the causal W7 gate. */
class W7CommonAaPathSourceDiscriminantsSurfacePixelTest {
    @Test
    fun layerStrokePreservesSourceAlphaAndDistinctOccurrences() {
        val single = { layerStrokeSurface(width = 1, strokePaint = strokePaint()) }
        val singleRegions = listOf(region(8, 15, 24, 17, pixel(188, 0, 0, 128, tolerance = 1)))
        assertPositiveSeries(single(), "isolated-half-source", 1, SIZE, SIZE, singleRegions, single)

        val duplicate = {
            layerStrokeSurface(width = 1, strokePaint = strokePaint(), occurrences = 2)
        }
        val duplicateRegions = listOf(region(8, 15, 24, 17, pixel(225, 0, 0, 191, tolerance = 1)))
        assertPositiveSeries(duplicate(), "two-distinct-half-sources", 1, SIZE, SIZE,
            duplicateRegions, duplicate)

        val alphaDrawPaint = strokePaint().copy(color = ColorARGB.of(128, 255, 0, 0))
        val widthTwoAlpha = { layerStrokeSurface(width = 2, strokePaint = alphaDrawPaint) }
        assertPositiveSeries(widthTwoAlpha(), "source-alpha128-width2", 2, SIZE, SIZE,
            listOf(region(8, 15, 24, 17, pixel(188, 0, 0, 128, tolerance = 1))), widthTwoAlpha)

        val widthOneAlpha = { layerStrokeSurface(width = 1, strokePaint = alphaDrawPaint) }
        assertPositiveSeries(widthOneAlpha(), "source-alpha128-width1", 1, SIZE, SIZE,
            listOf(region(8, 15, 24, 17, pixel(137, 0, 0, 64, tolerance = 1))), widthOneAlpha)
    }

    @Test
    fun layerRestorePreservesAlphaAndOrder() {
        val restorePaint = Paint(color = ColorARGB.of(128, 0, 0, 0))
        for (width in listOf(2, 1)) {
            // Width 2 resolves opaque red, then restore alpha 1/2 yields encoded linear-premul
            // red 188/alpha 128. Width 1 has 1/2 coverage before restore, yielding 1/4 red 137/64.
            val alphaScene = {
                layerStrokeSurface(width, strokePaint(), restorePaint = restorePaint)
            }
            val alphaPixel = if (width == 2) pixel(188, 0, 0, 128, tolerance = 1)
            else pixel(137, 0, 0, 64, tolerance = 1)
            assertPositiveSeries(alphaScene(), "restore-alpha-$width", width, SIZE, SIZE,
                listOf(region(8, 15, 24, 17, alphaPixel)), alphaScene)

            for (blueFirst in listOf(true, false)) {
                val orderedScene = { orderedLayerScene(width, restorePaint, blueFirst) }
                val sourceOverBlue = if (width == 2) pixel(188, 0, 188, 255, tolerance = 1)
                else pixel(137, 0, 225, 255, tolerance = 1)
                val strokeOutsideBlue = if (width == 2) pixel(188, 0, 0, 128, tolerance = 1)
                else pixel(137, 0, 0, 64, tolerance = 1)
                val regions = if (blueFirst) listOf(
                    region(12, 14, 20, 18, BLUE_PIXEL),
                    region(8, 15, 12, 17, strokeOutsideBlue),
                    region(20, 15, 24, 17, strokeOutsideBlue),
                    region(12, 15, 20, 17, sourceOverBlue),
                ) else listOf(
                    region(8, 15, 24, 17, strokeOutsideBlue),
                    region(12, 14, 20, 18, BLUE_PIXEL),
                )
                assertPositiveSeries(orderedScene(), "restore-order-${if (blueFirst) "blue-first" else "blue-last"}-$width",
                    width, SIZE, SIZE, regions, orderedScene)
            }
        }
    }

    @Test
    fun layerSourcePreservesDeviceCoordinatesAndHardClip() {
        val hinted = { translatedHintScene(hardClip = false) }
        assertPositiveSeries(
            hinted(), "translated-hint-does-not-clip", 2, SIZE, SIZE,
            listOf(region(11, 17, 27, 19, OPAQUE_RED)), hinted,
        )

        val clipped = { translatedHintScene(hardClip = true) }
        assertPositiveSeries(
            clipped(), "independent-device-hard-clip", 2, SIZE, SIZE,
            listOf(region(12, 17, 20, 19, OPAQUE_RED)), clipped,
        )
    }

    @Test
    fun foreignAxesRefuseWithoutPublishingAndRecover() {
        val pathShader = Shader.LinearGradient(
            Point2F32(0f, 8f), Point2F32(32f, 8f),
            listOf(GradientStop(0f, RED), GradientStop(1f, BLUE)),
        )
        val cases = listOf(
            ForeignCase("own-mask-filter", "w6a.layer.unsupported_spatial_filter") {
                saveLayer()
                drawPath(filterTriangle(), fillPaint().copy(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f)))
                restore()
            },
            ForeignCase("own-image-filter", "w6a.layer.unsupported_spatial_filter") {
                saveLayer()
                drawPath(filterTriangle(), fillPaint().copy(imageFilter = ImageFilter.Blur(1f, 1f)))
                restore()
            },
            ForeignCase("own-mask-filter-path-stroke", "w6a.layer.unsupported_spatial_filter") {
                saveLayer()
                drawPath(line(), strokePaint().copy(maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f)))
                restore()
            },
            ForeignCase("own-image-filter-path-stroke", "w6a.layer.unsupported_spatial_filter") {
                saveLayer()
                drawPath(line(), strokePaint().copy(imageFilter = ImageFilter.Blur(1f, 1f)))
                restore()
            },
            // These cases stay PATH STROKE; the established shader/PLUS AA FILL deferred lane
            // remains a positive contract and is deliberately not asserted to refuse here.
            ForeignCase("shader-stroke", "w6a.layer.unsupported_child") {
                saveLayer()
                drawPath(line(), strokePaint().copy(shader = pathShader))
                restore()
            },
            ForeignCase("plus-stroke", "w6a.layer.unsupported_child") {
                saveLayer()
                drawPath(line(), strokePaint().copy(blendMode = BlendMode.PLUS))
                restore()
            },
            ForeignCase("aa-child-clip", "w6a.layer.unsupported_child") {
                saveLayer()
                save()
                clipRect(RectF32.ofLTRB(8f, 0f, 24f, 32f), antiAlias = true)
                drawPath(line(), strokePaint())
                restore()
                restore()
            },
            ForeignCase("fractional-hard-child-clip", "w6a.layer.unsupported_child") {
                saveLayer()
                save()
                clipRect(RectF32.ofLTRB(8.25f, 0f, 23.75f, 32f), antiAlias = false)
                drawPath(line(), strokePaint())
                restore()
                restore()
            },
        )

        for (case in cases) {
            val surface = surface32(record = case.record)
            assertRefusesWithoutPublishing(surface, case.label, case.expectedCode, SIZE, SIZE)
            surface.discardRecordedOperations()
            recordBlueRecovery(surface, SIZE, SIZE)
            val recovered = surface.render()
            printEvidence("${case.label}-recovery", 2, recovered)
            assertBuffer(recovered, "${case.label} blue recovery", SIZE, SIZE,
                listOf(region(0, 0, SIZE, SIZE, BLUE_PIXEL)))
            assertNativeCompletion(recovered, "${case.label} blue recovery", SIZE, SIZE)
            val replay = surface.render()
            printEvidence("${case.label}-recovery-replay", 2, replay)
            assertBuffer(replay, "${case.label} blue recovery replay", SIZE, SIZE,
                listOf(region(0, 0, SIZE, SIZE, BLUE_PIXEL)))
            assertNativeCompletion(replay, "${case.label} blue recovery replay", SIZE, SIZE)
            assertContentEquals(recovered.pixels, replay.pixels, "${case.label} same-Surface recovery replay")
        }
    }

    @Test
    fun encodedLayerRefusesWhileEncodedRootRemainsNative() {
        val encoded = RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED)
        val layer = surface32(config = encoded) {
            saveLayer()
            drawStroke(width = 1, paint = strokePaint(width = 1f))
            restore()
        }
        val sentinel = UByteArray(SIZE * SIZE * 4) { 0x5au }
        val before = sentinel.copyOf()
        val layerFailure = runCatching { layer.readPixels(FULL_BOUNDS, sentinel) }.exceptionOrNull()
        if (layerFailure is IllegalStateException) {
            assertContentEquals(before, sentinel, "encoded layer refusal sentinel")
            println("W7_COMMON_AA_PATH_DISCRIMINANT label=encoded-layer nativeCompletion=false diagnostics=${layerFailure.message}")
            layer.discardRecordedOperations()
            recordBlueRecovery(layer, SIZE, SIZE)
            val recovered = layer.render()
            printEvidence("encoded-layer-blue-recovery", 2, recovered)
            assertBuffer(recovered, "encoded layer recovery", SIZE, SIZE,
                listOf(region(0, 0, SIZE, SIZE, BLUE_PIXEL)))
            assertNativeCompletion(recovered, "encoded layer recovery", SIZE, SIZE)
            val replay = layer.render()
            printEvidence("encoded-layer-blue-recovery-replay", 2, replay)
            assertBuffer(replay, "encoded layer recovery replay", SIZE, SIZE,
                listOf(region(0, 0, SIZE, SIZE, BLUE_PIXEL)))
            assertNativeCompletion(replay, "encoded layer recovery replay", SIZE, SIZE)
            assertContentEquals(recovered.pixels, replay.pixels, "encoded layer recovery replay")
        }

        val root = { encodedRootStroke() }
        val encodedHalfRed = listOf(region(8, 15, 24, 17, pixel(128, 0, 0, 128, tolerance = 1)))
        assertPositiveSeries(root(), "encoded-root-control", 1, SIZE, SIZE, encodedHalfRed, root)
        assertTrue(
            layerFailure is IllegalStateException &&
                layerFailure.message.orEmpty().startsWith("unsupported.surface.composition.layer:"),
            "expected encoded layer admission refusal, got ${layerFailure?.javaClass?.name}: ${layerFailure?.message}",
        )
    }

    @Test
    fun layerStrokeBudgetBoundaryRefusesAndRecovers() {
        // B = root RGBA16 + aligned readback512 + layer RGBA16 + AA4 color64 + AA4 D24S8 64 +
        // resolve RGBA16 + W4d V/I/U pools 16384/4096/4096 + W6 uniform16 + composed Solid16
        // + composed tail-alpha16 (a distinct ABI field, neutral value 1).
        // The width-1 quad uses StencilCover. No cache or lifetime alias discount is taken.
        val budgetB = listOf(16L, 512L, 16L, 64L, 64L, 16L, 16_384L, 4_096L, 4_096L, 16L, 16L, 16L)
            .fold(0L, Math::addExact)
        assertEquals(25_312L, budgetB, "independent stroke layer boundary derivation")

        val admitted = budgetStrokeSurface(budgetB)
        val expected = listOf(region(0, 0, 2, 2, pixel(188, 0, 0, 128, tolerance = 1)))
        assertPositiveSeries(admitted, "layer-stroke-budget-B", 1, 2, 2, expected) {
            budgetStrokeSurface(budgetB)
        }

        val refused = budgetStrokeSurface(budgetB - 1L)
        assertRefusesWithoutPublishing(refused, "layer-stroke-budget-B-minus-1",
            "budget.w5g.composed-uniform", 2, 2)
        refused.discardRecordedOperations()
        recordBlueRecovery(refused, 2, 2)
        val recovered = refused.render()
        printEvidence("layer-stroke-budget-B-minus-1-blue-recovery", 1, recovered)
        assertBuffer(recovered, "B-1 blue recovery", 2, 2, listOf(region(0, 0, 2, 2, BLUE_PIXEL)))
        assertNativeCompletion(recovered, "B-1 blue recovery", 2, 2)
        val replay = refused.render()
        printEvidence("layer-stroke-budget-B-minus-1-blue-recovery-replay", 1, replay)
        assertBuffer(replay, "B-1 blue recovery replay", 2, 2, listOf(region(0, 0, 2, 2, BLUE_PIXEL)))
        assertNativeCompletion(replay, "B-1 blue recovery replay", 2, 2)
        assertContentEquals(recovered.pixels, replay.pixels, "B-1 same-Surface recovery replay")
    }

    private data class ForeignCase(
        val label: String,
        val expectedCode: String,
        val record: Canvas.() -> Unit,
    )

    private data class ExpectedPixel(val red: Int, val green: Int, val blue: Int, val alpha: Int, val tolerance: Int)
    private data class ExpectedRegion(val bounds: RectI32, val pixel: ExpectedPixel)

    private fun assertPositiveSeries(
        surface: Surface,
        label: String,
        strokeWidth: Int,
        width: Int,
        height: Int,
        expected: List<ExpectedRegion>,
        freshSurface: () -> Surface,
    ): RenderResult {
        val first = renderAndAssert(surface, "$label first", strokeWidth, width, height, expected)
        val replay = renderAndAssert(surface, "$label same-Surface replay", strokeWidth, width, height, expected)
        assertContentEquals(first.pixels, replay.pixels, "$label same-Surface replay")
        for (freshIndex in 1..2) {
            val fresh = renderAndAssert(freshSurface(), "$label fresh Surface $freshIndex", strokeWidth,
                width, height, expected)
            assertContentEquals(first.pixels, fresh.pixels, "$label fresh Surface $freshIndex")
        }
        return first
    }

    private fun renderAndAssert(
        surface: Surface,
        label: String,
        strokeWidth: Int,
        width: Int,
        height: Int,
        expected: List<ExpectedRegion>,
    ): RenderResult {
        val result = surface.render()
        printEvidence("$label width=$strokeWidth", strokeWidth, result)
        assertBuffer(result, label, width, height, expected)
        assertNativeCompletion(result, label, width, height)
        return result
    }

    private fun assertBuffer(
        result: RenderResult,
        label: String,
        width: Int,
        height: Int,
        expected: List<ExpectedRegion>,
    ) {
        assertEquals(width, result.width, "$label width")
        assertEquals(height, result.height, "$label height")
        assertEquals(PixelFormat.RGBA8, result.format, "$label RGBA8 format")
        assertEquals(width * height * 4, result.pixels.size, "$label complete RGBA8 buffer")
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = expected.lastOrNull {
                x in it.bounds.left until it.bounds.right && y in it.bounds.top until it.bounds.bottom
            }?.pixel
                ?: TRANSPARENT_PIXEL
            val offset = (y * width + x) * 4
            if (pixel.tolerance == 0) assertPixel(result.pixels, offset, pixel, "$label ($x,$y)")
            else assertPixelNear(result.pixels, offset, pixel, "$label ($x,$y)")
        }
    }

    private fun assertNativeCompletion(result: RenderResult, label: String, width: Int, height: Int) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = result.structuralSteps.indexOf("CompletionSucceeded")
        val evidence = "label=$label size=${width}x$height diagnostics=${result.diagnostics.summary()} " +
            "stats=${result.stats} scopes=${result.nativeEvidenceScopeKinds} " +
            "counters=${result.nativeEvidenceCounters} structuralSteps=${result.structuralSteps}"
        assertTrue(result.isClean, evidence)
        assertTrue(result.diagnostics.isEmpty, evidence)
        assertTrue(result.stats.opsDispatched > 0, evidence)
        assertEquals(0, result.stats.opsRefused, evidence)
        assertTrue(result.stats.drawCallCount > 0, evidence)
        assertTrue(result.stats.pipelineCount > 0, evidence)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), evidence)
        assertTrue(submitted >= 0 && completed > submitted, evidence)
        assertEquals(1L, result.nativeEvidenceCounters["submits"], evidence)
        assertEquals(1L, result.nativeEvidenceCounters["readbackCopies"], evidence)
        val draws = requireNotNull(result.nativeEvidenceCounters["draws"]) { evidence }
        val indexedDraws = requireNotNull(result.nativeEvidenceCounters["drawIndexed"]) { evidence }
        assertTrue(draws + indexedDraws > 0L, evidence)
        assertTrue(requireNotNull(result.nativeEvidenceCounters["pipelineBinds"]) { evidence } > 0L, evidence)
    }

    private fun assertRefusesWithoutPublishing(surface: Surface, label: String, code: String, width: Int, height: Int) {
        val bounds = RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat())
        val sentinel = UByteArray(width * height * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = runCatching { surface.readPixels(bounds, sentinel) }.exceptionOrNull()
        println("W7_COMMON_AA_PATH_DISCRIMINANT label=$label size=${width}x$height " +
            "nativeCompletion=false diagnostics=${failure?.message ?: "none"}")
        assertTrue(failure is IllegalStateException && failure.message.orEmpty().startsWith("$code:"),
            "$label expected $code terminal refusal, got ${failure?.javaClass?.name}: ${failure?.message}")
        assertContentEquals(before, sentinel, "$label refusal must not publish into caller buffer")
    }

    private fun printEvidence(label: String, strokeWidth: Int, result: RenderResult) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = submitted >= 0 && result.structuralSteps.indexOf("CompletionSucceeded") > submitted
        val diagnostics = result.diagnostics.entries.joinToString { "${it.code}:${it.operation}:${it.reason}" }
        println("W7_COMMON_AA_PATH_DISCRIMINANT label=$label width=$strokeWidth " +
            "nativeCompletion=$completed diagnostics=$diagnostics scopes=${result.nativeEvidenceScopeKinds} " +
            "counters=${result.nativeEvidenceCounters}")
    }

    private fun surface32(config: RenderConfig = RenderConfig.DEFAULT, record: Canvas.() -> Unit): Surface =
        Surface(SIZE, SIZE, config = config).also { surface -> surface.canvas(record) }

    private fun layerStrokeSurface(
        width: Int,
        strokePaint: Paint,
        occurrences: Int = 1,
        restorePaint: Paint? = null,
    ): Surface = surface32 {
        saveLayer(paint = restorePaint)
        repeat(occurrences) { drawStroke(width, strokePaint) }
        restore()
    }

    private fun orderedLayerScene(width: Int, restorePaint: Paint, blueFirst: Boolean): Surface = surface32 {
        if (blueFirst) drawRect(ORDER_RECT, Paint(BLUE, antiAlias = false))
        saveLayer(paint = restorePaint)
        drawStroke(width, strokePaint())
        restore()
        if (!blueFirst) drawRect(ORDER_RECT, Paint(BLUE, antiAlias = false))
    }

    private fun translatedHintScene(hardClip: Boolean): Surface = surface32 {
        if (hardClip) clipRect(RectF32.ofLTRB(12f, 0f, 20f, 32f), antiAlias = false)
        translate(3f, 2f)
        saveLayer(RectF32.ofLTRB(12f, 15f, 20f, 17f))
        drawStroke(2, strokePaint())
        restore()
    }

    private fun encodedRootStroke(): Surface = surface32(RenderConfig(compositionDomain = CompositionDomain.SRGB_ENCODED)) {
        drawStroke(1, strokePaint())
    }

    private fun budgetStrokeSurface(budget: Long): Surface = Surface(
        2, 2, config = RenderConfig(frameLocalBudgetBytes = budget),
    ).also { surface ->
        surface.canvas {
            saveLayer()
            drawPath(
                Path().apply { moveTo(0f, 1f); lineTo(2f, 1f) },
                strokePaint(width = 1f),
            )
            restore()
        }
    }

    private fun Canvas.drawStroke(width: Int, paint: Paint) {
        save()
        clipRect(FULL_BOUNDS, antiAlias = false)
        drawPath(line(), paint.copy(strokeWidth = width.toFloat()))
        restore()
    }

    private fun line() = Path().apply { moveTo(8f, 16f); lineTo(24f, 16f) }

    private fun filterTriangle() = Path().apply {
        moveTo(8f, 8f)
        lineTo(24f, 8f)
        lineTo(16f, 24f)
        close()
    }

    private fun strokePaint(width: Float = 2f): Paint = Paint(
        RED,
        style = PaintStyle.STROKE,
        strokeWidth = width,
        strokeCap = StrokeCap.BUTT,
        strokeJoin = StrokeJoin.MITER,
        antiAlias = true,
    )

    private fun fillPaint(): Paint = Paint(RED, style = PaintStyle.FILL, antiAlias = true)

    private fun recordBlueRecovery(surface: Surface, width: Int, height: Int) {
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, width.toFloat(), height.toFloat()), Paint(BLUE, antiAlias = false))
        }
    }

    private fun assertPixel(pixels: UByteArray, offset: Int, expected: ExpectedPixel, label: String) {
        assertContentEquals(
            ubyteArrayOf(expected.red.toUByte(), expected.green.toUByte(), expected.blue.toUByte(), expected.alpha.toUByte()),
            pixels.copyOfRange(offset, offset + 4), label,
        )
    }

    private fun assertPixelNear(pixels: UByteArray, offset: Int, expected: ExpectedPixel, label: String) {
        listOf(expected.red, expected.green, expected.blue, expected.alpha).forEachIndexed { channel, value ->
            val actual = pixels[offset + channel].toInt()
            assertTrue(abs(actual - value) <= expected.tolerance,
                "$label channel=$channel expected=$value ±${expected.tolerance} actual=$actual")
        }
    }

    private fun region(left: Int, top: Int, right: Int, bottom: Int, pixel: ExpectedPixel) =
        ExpectedRegion(RectI32.ofLTRB(left, top, right, bottom), pixel)

    private fun pixel(red: Int, green: Int, blue: Int, alpha: Int, tolerance: Int = 0) =
        ExpectedPixel(red, green, blue, alpha, tolerance)

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }

        private const val SIZE = 32
        private val RED = ColorARGB.Red
        private val BLUE = ColorARGB.Blue
        private val FULL_BOUNDS = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        private val ORDER_RECT = RectF32.ofLTRB(12f, 14f, 20f, 18f)
        private val TRANSPARENT_PIXEL = ExpectedPixel(0, 0, 0, 0, 0)
        private val OPAQUE_RED = ExpectedPixel(255, 0, 0, 255, 0)
        private val BLUE_PIXEL = ExpectedPixel(0, 0, 255, 255, 0)
    }
}
