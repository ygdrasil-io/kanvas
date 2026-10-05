@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

/** Public native RED gate for the shared W7 ordinary AA PATH colour source. */
class W7CommonAaPathSourceSurfacePixelTest {
    @Test
    fun rootStrokeHasIndependentFullBufferAndReplay() {
        for (width in STROKE_WIDTHS) {
            val surface = Surface(SIZE, SIZE)
            surface.canvas { drawStroke(width) }
            val series = renderFourTimes(surface, "root", width, hasFilteredSibling = false)
            assertTrue(series.refusals.isEmpty(), "root width=$width terminal failures=${series.refusals}")
        }
    }

    @Test
    fun rootStrokeWithFilteredSiblingHasIndependentFullBufferAndReplay() {
        for (width in STROKE_WIDTHS) {
            val surface = Surface(SIZE, SIZE)
            surface.canvas {
                drawFilteredSibling()
                drawStroke(width)
            }
            val series = renderFourTimes(surface, "root-filtered-sibling", width, hasFilteredSibling = true)
            assertTrue(series.refusals.isEmpty(), "filtered sibling width=$width terminal failures=${series.refusals}")
        }
    }

    @Test
    fun plainLayerStrokeHasIndependentFullBufferAndReplay() {
        val layerRefusals = mutableListOf<String>()
        for (width in STROKE_WIDTHS) {
            val root = Surface(SIZE, SIZE)
            root.canvas { drawStroke(width) }
            val rootResult = renderFourTimes(root, "root-layer-control", width, hasFilteredSibling = false)
            assertTrue(rootResult.refusals.isEmpty(), "root control width=$width terminal failures=${rootResult.refusals}")

            val layer = Surface(SIZE, SIZE)
            layer.canvas {
                saveLayer()
                drawStroke(width)
                restore()
            }
            val layerResult = renderFourTimes(layer, "plain-layer", width, hasFilteredSibling = false)
            layerRefusals += layerResult.refusals.map { "width=$width $it" }
            if (layerResult.first != null) {
                assertContentEquals(rootResult.first!!.pixels, layerResult.first.pixels,
                    "root/layer complete RGBA width=$width")
            }
        }
        val unexpectedRefusals = layerRefusals.filterNot { it.contains("w6a.layer.unsupported_child") }
        assertTrue(unexpectedRefusals.isEmpty(), "plain layer had a non-gate failure: $unexpectedRefusals")
        assertTrue(
            layerRefusals.isEmpty(),
            "plain layer should render the root's independently pinned full buffer; refusals=$layerRefusals",
        )
    }

    private data class RenderSeries(val first: RenderResult?, val refusals: List<String>)

    private fun renderFourTimes(
        surface: Surface,
        label: String,
        width: Int,
        hasFilteredSibling: Boolean,
    ): RenderSeries {
        val refusals = mutableListOf<String>()
        fun renderAttempt(target: Surface, renderLabel: String): RenderResult? {
            val result = try {
                target.render()
            } catch (failure: IllegalStateException) {
                val diagnostic = failure.message.orEmpty()
                refusals += "$renderLabel: $diagnostic"
                println(
                    "W7_COMMON_AA_PATH label=$label width=$width render=$renderLabel " +
                        "nativeCompletion=false diagnostics=$diagnostic",
                )
                return null
            }
            printEvidence(label, width, renderLabel, result)
            assertFullBufferOracle(result, "$renderLabel ($label)", width, hasFilteredSibling)
            assertNativeCompletion(result, renderLabel, width)
            return result
        }

        val first = renderAttempt(surface, "same-surface-first")
        val replay = renderAttempt(surface, "same-surface-replay")
        if (first != null && replay != null) {
            assertContentEquals(first.pixels, replay.pixels, "$label same-Surface replay width=$width")
        }

        val freshOneSurface = Surface(SIZE, SIZE).also { fresh ->
            fresh.canvas { recordScene(label, width) }
        }
        val freshOne = renderAttempt(freshOneSurface, "fresh-surface-1")
        if (first != null && freshOne != null) {
            assertContentEquals(first.pixels, freshOne.pixels, "$label fresh Surface 1 width=$width")
        }

        val freshTwoSurface = Surface(SIZE, SIZE).also { fresh ->
            fresh.canvas { recordScene(label, width) }
        }
        val freshTwo = renderAttempt(freshTwoSurface, "fresh-surface-2")
        if (first != null && freshTwo != null) {
            assertContentEquals(first.pixels, freshTwo.pixels, "$label fresh Surface 2 width=$width")
        }
        return RenderSeries(first, refusals)
    }

    private fun Canvas.recordScene(label: String, width: Int) {
        if (label == "root-filtered-sibling") drawFilteredSibling()
        if (label == "plain-layer") saveLayer()
        drawStroke(width)
        if (label == "plain-layer") restore()
    }

    private fun Canvas.drawStroke(width: Int) {
        save()
        clipRect(SURFACE_BOUNDS, antiAlias = false)
        drawPath(
            Path().apply { moveTo(8f, 16f); lineTo(24f, 16f) },
            Paint(
                color = RED,
                style = PaintStyle.STROKE,
                strokeWidth = width.toFloat(),
                strokeCap = StrokeCap.BUTT,
                strokeJoin = StrokeJoin.MITER,
                antiAlias = true,
            ),
        )
        restore()
    }

    private fun Canvas.drawFilteredSibling() {
        save()
        clipRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), antiAlias = false)
        drawRect(
            RectF32.ofLTRB(-16f, -16f, 48f, 48f),
            Paint(BLUE, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = false),
        )
        restore()
    }

    private fun assertFullBufferOracle(result: RenderResult, label: String, width: Int, hasFilteredSibling: Boolean) {
        assertEquals(SIZE, result.width, "$label width")
        assertEquals(SIZE, result.height, "$label height")
        assertEquals(PixelFormat.RGBA8, result.format, "$label format")
        assertEquals(SIZE * SIZE * 4, result.pixels.size, "$label full RGBA8 buffer")

        // Independent cell oracle: a width-2 butt stroke covers rows 15–16 fully;
        // the width-1 stroke contributes half linear red and alpha to those rows.
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val offset = (y * SIZE + x) * 4
                val filteredBlue = hasFilteredSibling && x in 0..3 && y in 0..3
                val stroke = x in 8..23 && y in 15..16
                when {
                    filteredBlue -> assertPixel(result.pixels, offset, 0, 0, 255, 255, "$label ($x,$y)")
                    stroke && width == 2 -> assertPixel(result.pixels, offset, 255, 0, 0, 255, "$label ($x,$y)")
                    stroke -> assertPixelNear(result.pixels, offset, 188, 0, 0, 128, "$label ($x,$y)")
                    else -> assertPixel(result.pixels, offset, 0, 0, 0, 0, "$label ($x,$y)")
                }
            }
        }
    }

    private fun assertNativeCompletion(result: RenderResult, label: String, width: Int) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = result.structuralSteps.indexOf("CompletionSucceeded")
        val evidence = "label=$label width=$width diagnostics=${result.diagnostics.summary()} " +
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

    private fun printEvidence(label: String, width: Int, render: String, result: RenderResult) {
        val submitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completed = result.structuralSteps.indexOf("CompletionSucceeded") > submitted && submitted >= 0
        val diagnostics = result.diagnostics.entries.joinToString { "${it.code}:${it.operation}:${it.reason}" }
        println(
            "W7_COMMON_AA_PATH label=$label width=$width render=$render " +
                "nativeCompletion=$completed diagnostics=$diagnostics scopes=${result.nativeEvidenceScopeKinds} " +
                "counters=${result.nativeEvidenceCounters}",
        )
    }

    private fun assertPixel(pixels: UByteArray, offset: Int, r: Int, g: Int, b: Int, a: Int, label: String) {
        assertContentEquals(
            ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4),
            label,
        )
    }

    private fun assertPixelNear(pixels: UByteArray, offset: Int, r: Int, g: Int, b: Int, a: Int, label: String) {
        listOf(r, g, b, a).forEachIndexed { channel, expected ->
            val actual = pixels[offset + channel].toInt()
            assertTrue(abs(actual - expected) <= 1, "$label channel=$channel expected=$expected ±1 actual=$actual")
        }
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }

        private const val SIZE = 32
        private val STROKE_WIDTHS = listOf(2, 1)
        private val SURFACE_BOUNDS = RectF32.ofLTRB(0f, 0f, 32f, 32f)
        private val RED = ColorARGB.Red
        private val BLUE = ColorARGB.Blue
    }
}
