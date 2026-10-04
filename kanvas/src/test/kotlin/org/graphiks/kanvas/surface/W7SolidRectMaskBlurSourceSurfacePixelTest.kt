@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public native RED fixture for SolidRect mask blur source bounds and ordinary clipping. */
class W7SolidRectMaskBlurSourceSurfacePixelTest {
    @Test
    fun interiorClipPreservesOpaqueBlurSourceAndReplay() {
        val expected = transparentBuffer().also { pixels ->
            fillRect(pixels, 8, 8, 12, 12, BLUE_PIXEL)
            fillRect(pixels, 8, 15, 24, 17, RED_PIXEL)
        }
        verifyFourRenders("interior-filtered", expected) { canvas ->
            canvas.save()
            canvas.clipRect(INTERIOR_CLIP, antiAlias = false)
            canvas.drawRect(
                LARGE_RECT,
                Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false),
            )
            canvas.restore()
            canvas.drawWidthTwoStroke()
        }
    }

    @Test
    fun ordinaryClippedRectKeepsIndependentFullBufferAndReplay() {
        listOf("edge" to EDGE_CLIP, "interior" to INTERIOR_CLIP).forEach { (label, clip) ->
            val expected = transparentBuffer().also { pixels ->
                val left = clip.left.toInt()
                val top = clip.top.toInt()
                val right = clip.right.toInt()
                val bottom = clip.bottom.toInt()
                fillRect(pixels, left, top, right, bottom, BLUE_PIXEL)
            }
            verifyFourRenders("ordinary-$label", expected) { canvas ->
                canvas.save()
                canvas.clipRect(clip, antiAlias = false)
                canvas.drawRect(LARGE_RECT, Paint(BLUE, antiAlias = false))
                canvas.restore()
            }
        }
    }

    @Test
    fun trueGeometryEdgeBlurRetainsFalloffOverOpaqueBackground() {
        val expected = geometryEdgeOracle()
        verifyFourRenders("geometry-edge", expected) { canvas ->
            canvas.drawColor(RED)
            canvas.save()
            canvas.clipRect(INTERIOR_CLIP, antiAlias = false)
            canvas.drawRect(
                SOLID_RECT_GEOMETRY,
                Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false),
            )
            canvas.restore()
        }
    }

    @Test
    fun enlargedSourceBudgetAcceptsExactBoundaryAndRecoversAfterRefusal() {
        val filteredExpected = transparentBuffer().also { pixels ->
            fillRect(pixels, 8, 8, 12, 12, BLUE_PIXEL)
        }
        verifyFourRenders(
            "budget-exact-boundary",
            filteredExpected,
            config = RenderConfig(frameLocalBudgetBytes = SOURCE_BUDGET_BYTES),
        ) { canvas ->
            canvas.save()
            canvas.clipRect(INTERIOR_CLIP, antiAlias = false)
            canvas.drawRect(
                LARGE_RECT,
                Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false),
            )
            canvas.restore()
        }

        val refused = Surface(SIZE, SIZE, config = RenderConfig(frameLocalBudgetBytes = SOURCE_BUDGET_BYTES - 1L))
        refused.canvas {
            save()
            clipRect(INTERIOR_CLIP, antiAlias = false)
            drawRect(LARGE_RECT, Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false))
            restore()
        }
        val sentinel = UByteArray(FULL_RGBA_BYTES) { 0x5au }
        val sentinelBefore = sentinel.copyOf()
        val refusal = assertFailsWith<IllegalStateException> {
            refused.readPixels(RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), sentinel)
        }
        assertTrue(
            refusal.message?.startsWith("w6b.filter.frame_budget_exceeded:") == true,
            refusal.message ?: "missing W6b frame-budget diagnostic",
        )
        assertContentEquals(sentinelBefore, sentinel, "B-1 refusal must preserve the caller's readback sentinel")

        refused.discardRecordedOperations()
        refused.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint(BLUE, antiAlias = false))
        }
        val recoveryCaptures = listOf("recovery-first", "recovery-replay").map { replay ->
            refused.render().also { printEvidence("budget-recovery", replay, it) }
        }
        recoveryCaptures.forEachIndexed { index, result ->
            val replay = if (index == 0) "recovery-first" else "recovery-replay"
            assertNativeCompletion(result, "budget-recovery", replay)
            assertFullBufferOracle(result, solidBuffer(BLUE_PIXEL), "budget-recovery", replay)
        }
        recoveryCaptures.drop(1).forEach { result ->
            assertContentEquals(recoveryCaptures.first().pixels, result.pixels, "same-Surface recovery replay")
        }
    }

    @Test
    fun disjointNestedMaskSourceRefusesWithoutWideningAndSameSurfaceRecovers() {
        val refused = Surface(SIZE, SIZE)
        refused.canvas {
            save()
            clipRect(EDGE_CLIP, antiAlias = false)
            saveLayer(paint = null)
            drawRect(
                RectF32.ofLTRB(16f, 16f, 24f, 24f),
                Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false),
            )
            restore()
            restore()
        }

        val sentinel = UByteArray(FULL_RGBA_BYTES) { 0x5au }
        val sentinelBefore = sentinel.copyOf()
        var readbackResult: Boolean? = null
        var readbackFailure: Exception? = null
        try {
            readbackResult = refused.readPixels(
                RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), sentinel,
            )
        } catch (failure: Exception) {
            readbackFailure = failure
        }
        println(
            "W7_SOLID_RECT_MASK scene=disjoint-nested-source " +
                "readPixels=$readbackResult exception=${readbackFailure?.javaClass?.name}:" +
                "${readbackFailure?.message} sentinelUnchanged=${sentinel.contentEquals(sentinelBefore)}",
        )

        refused.discardRecordedOperations()
        refused.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint(BLUE, antiAlias = false))
        }
        val recoveryCaptures = listOf("recovery-first", "recovery-replay").map { replay ->
            refused.render().also { printEvidence("disjoint-nested-recovery", replay, it) }
        }
        recoveryCaptures.forEachIndexed { index, result ->
            val replay = if (index == 0) "recovery-first" else "recovery-replay"
            assertNativeCompletion(result, "disjoint-nested-recovery", replay)
            assertFullBufferOracle(result, solidBuffer(BLUE_PIXEL), "disjoint-nested-recovery", replay)
        }
        recoveryCaptures.drop(1).forEach { result ->
            assertContentEquals(recoveryCaptures.first().pixels, result.pixels, "same-Surface disjoint recovery replay")
        }

        assertTrue(
            readbackFailure is IllegalStateException,
            "expected InvalidBounds refusal after two recovery renders; " +
                "readPixels=$readbackResult exception=${readbackFailure?.javaClass?.name}:" +
                "${readbackFailure?.message}",
        )
        assertTrue(
            readbackFailure.message?.startsWith("w6b.filter.invalid_bounds:") == true,
            readbackFailure.message ?: "missing W6b invalid-bounds diagnostic",
        )
        assertContentEquals(sentinelBefore, sentinel, "InvalidBounds refusal must preserve the readback sentinel")
    }

    private fun verifyFourRenders(
        scene: String,
        expected: UByteArray,
        config: RenderConfig = RenderConfig(),
        record: (Canvas) -> Unit,
    ) {
        data class CapturedRender(val label: String, val result: RenderResult)

        val refusals = mutableListOf<String>()
        val captures = mutableListOf<CapturedRender>()
        fun attempt(surface: Surface, replay: String) {
            val result = try {
                surface.render()
            } catch (failure: IllegalStateException) {
                val detail = failure.message.orEmpty()
                refusals += "$replay: $detail"
                println(
                    "W7_SOLID_RECT_MASK scene=$scene replay=$replay nativeCompletion=false " +
                        "diagnostics=$detail firstClipCorner=unavailable",
                )
                return
            }
            printEvidence(scene, replay, result)
            captures += CapturedRender(replay, result)
        }

        val sameSurface = Surface(SIZE, SIZE, config = config).also { record(it.canvas()) }
        attempt(sameSurface, "same-surface-first")
        attempt(sameSurface, "same-surface-replay")

        val freshOneSurface = Surface(SIZE, SIZE, config = config).also { record(it.canvas()) }
        attempt(freshOneSurface, "fresh-surface-1")

        val freshTwoSurface = Surface(SIZE, SIZE, config = config).also { record(it.canvas()) }
        attempt(freshTwoSurface, "fresh-surface-2")

        assertTrue(refusals.isEmpty(), "$scene native render refusals=$refusals")
        captures.forEach { (label, result) -> assertNativeCompletion(result, scene, label) }
        captures.forEach { (label, result) -> assertFullBufferOracle(result, expected, scene, label) }
        val first = captures.first().result
        captures.drop(1).forEach { (label, result) ->
            assertContentEquals(first.pixels, result.pixels, "$scene $label replay consistency")
        }
    }

    private fun Canvas.drawWidthTwoStroke() {
        drawPath(
            Path().apply { moveTo(8f, 16f); lineTo(24f, 16f) },
            Paint(
                color = RED,
                style = PaintStyle.STROKE,
                strokeWidth = 2f,
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                antiAlias = true,
            ),
        )
    }

    private fun assertFullBufferOracle(
        result: RenderResult,
        expected: UByteArray,
        scene: String,
        replay: String,
    ) {
        assertEquals(SIZE, result.width, "$scene $replay width")
        assertEquals(SIZE, result.height, "$scene $replay height")
        assertEquals(PixelFormat.RGBA8, result.format, "$scene $replay format")
        assertEquals(FULL_RGBA_BYTES, result.pixels.size, "$scene $replay full RGBA8 buffer")
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val offset = (y * SIZE + x) * 4
                val isFractionalGeometryEdge = scene == "geometry-edge" &&
                    x in 8..11 && y in 8..11 && expected[offset + 3].toInt() == 255
                if (isFractionalGeometryEdge) {
                    assertPixelNearFractional(result.pixels, expected, offset, "$scene $replay ($x,$y)")
                } else {
                    assertContentEquals(
                        expected.copyOfRange(offset, offset + 4),
                        result.pixels.copyOfRange(offset, offset + 4),
                        "$scene $replay ($x,$y)",
                    )
                }
            }
        }
    }

    private fun assertPixelNearFractional(actual: UByteArray, expected: UByteArray, offset: Int, label: String) {
        for (channel in 0 until 4) {
            val expectedByte = expected[offset + channel].toInt()
            val actualByte = actual[offset + channel].toInt()
            if (expectedByte == 0 || expectedByte == 255) {
                assertEquals(expectedByte, actualByte, "$label channel=$channel")
            } else {
                assertTrue(
                    abs(actualByte - expectedByte) <= 1,
                    "$label channel=$channel expected=$expectedByte ±1 actual=$actualByte",
                )
            }
        }
    }

    private fun assertNativeCompletion(result: RenderResult, scene: String, replay: String) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = result.structuralSteps.indexOf("CompletionSucceeded")
        val evidence = "scene=$scene replay=$replay diagnostics=${result.diagnostics.summary()} " +
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

    private fun printEvidence(scene: String, replay: String, result: RenderResult) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = submitted >= 0 && result.structuralSteps.indexOf("CompletionSucceeded") > submitted
        val diagnostics = result.diagnostics.entries.joinToString { "${it.code}:${it.operation}:${it.reason}" }
        val cornerX = if (scene == "ordinary-edge") 0 else 8
        val cornerY = if (scene == "ordinary-edge") 0 else 8
        val corner = if (result.width == SIZE && result.height == SIZE && result.pixels.size == FULL_RGBA_BYTES) {
            rgbaAt(result.pixels, cornerX, cornerY)
        } else {
            "unavailable(width=${result.width},height=${result.height},bytes=${result.pixels.size})"
        }
        println(
            "W7_SOLID_RECT_MASK scene=$scene replay=$replay nativeCompletion=$completed " +
                "diagnostics=$diagnostics scopes=${result.nativeEvidenceScopeKinds} " +
                "stats=${result.stats} counters=${result.nativeEvidenceCounters} " +
                "firstClipCorner=($cornerX,$cornerY):$corner",
        )
    }

    private fun geometryEdgeOracle(): UByteArray = UByteArray(FULL_RGBA_BYTES).also { pixels ->
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val offset = (y * SIZE + x) * 4
                pixels[offset] = 255u
                pixels[offset + 1] = 0u
                pixels[offset + 2] = 0u
                pixels[offset + 3] = 255u
                if (x in 8..11 && y in 8..11) {
                    val horizontal = (255.0 * q(x - 8)).roundByte()
                    val alpha = (horizontal * q(y - 8)).roundByte()
                    val blueLinear = alpha / 255.0
                    val redLinear = 1.0 - blueLinear
                    pixels[offset] = (linearToSrgb(redLinear) * 255.0).roundByte().toUByte()
                    pixels[offset + 2] = (linearToSrgb(blueLinear) * 255.0).roundByte().toUByte()
                }
            }
        }
    }

    private fun q(distanceFromLeftEdge: Int): Double {
        val weights = (-3..3).map { k -> exp(-(k * k) / 2.0) }
        val denominator = weights.sum()
        val numerator = (-3..3).filter { k -> k >= -distanceFromLeftEdge }
            .sumOf { k -> exp(-(k * k) / 2.0) }
        return numerator / denominator
    }

    private fun linearToSrgb(value: Double): Double =
        if (value <= 0.0031308) 12.92 * value else 1.055 * value.pow(1.0 / 2.4) - 0.055

    private fun Double.roundByte(): Int = kotlin.math.floor(this + 0.5).toInt().coerceIn(0, 255)

    private fun transparentBuffer(): UByteArray = UByteArray(FULL_RGBA_BYTES)

    private fun solidBuffer(color: UByteArray): UByteArray = transparentBuffer().also { pixels ->
        fillRect(pixels, 0, 0, SIZE, SIZE, color)
    }

    private fun fillRect(pixels: UByteArray, left: Int, top: Int, right: Int, bottom: Int, color: UByteArray) {
        for (y in top until bottom) {
            for (x in left until right) {
                color.copyInto(pixels, (y * SIZE + x) * 4)
            }
        }
    }

    private fun rgbaAt(pixels: UByteArray, x: Int, y: Int): List<Int> {
        val offset = (y * SIZE + x) * 4
        return (offset until offset + 4).map { pixels[it].toInt() }
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }

        private const val SIZE = 32
        private const val FULL_RGBA_BYTES = SIZE * SIZE * 4
        // Root4096 + source textures5136 + staging8192 + W6 row16 + BLUE row16.
        private const val SOURCE_BUDGET_BYTES = 17_456L
        private val RED = ColorARGB.Red
        private val BLUE = ColorARGB.Blue
        private val BLUE_PIXEL = ubyteArrayOf(0u, 0u, 255u, 255u)
        private val RED_PIXEL = ubyteArrayOf(255u, 0u, 0u, 255u)
        private val EDGE_CLIP = RectF32.ofLTRB(0f, 0f, 4f, 4f)
        private val INTERIOR_CLIP = RectF32.ofLTRB(8f, 8f, 12f, 12f)
        private val LARGE_RECT = RectF32.ofLTRB(-16f, -16f, 48f, 48f)
        private val SOLID_RECT_GEOMETRY = RectF32.ofLTRB(8f, 8f, 24f, 24f)
    }
}
