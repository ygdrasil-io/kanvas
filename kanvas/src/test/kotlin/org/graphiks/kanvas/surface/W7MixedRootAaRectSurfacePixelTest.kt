@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public W7 witnesses for the bounded root mixture: a linear-gradient Rect sibling and an AA Rect stroke. */
class W7MixedRootAaRectSurfacePixelTest {
    companion object {
        @AfterAll @JvmStatic fun cleanupGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `mixed root gradient and aa ring preserve pixels and order`() {
        val surface = mixedBase()
        val first = renderTwice(surface)
        assertRing(first, red = RED, blue = BLUE)

        val reversedSurface = Surface(8, 8).also { target -> target.canvas {
            drawRect(RING, ringPaint(RED))
            drawRect(FULL, gradientPaint())
        } }
        val reversed = renderTwice(reversedSurface)
        assertAll(reversed, BLUE)
    }

    @Test
    fun `mixed root ring alpha is composed once`() {
        val opaqueBackground = renderTwice(mixedBase(ring = ColorARGB.of(128, RED.red, RED.green, RED.blue)))
        assertPixel(opaqueBackground, 2, 2, 188, 0, 187, 255)
        assertPixel(opaqueBackground, 3, 3, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)
        assertPixel(opaqueBackground, 0, 0, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)

        val halfCoverageSurface = Surface(8, 8).also { target -> target.canvas {
            drawRect(FULL, gradientPaint())
            drawRect(RectF32.ofLTRB(2f, 2f, 6f, 6f), ringPaint(ColorARGB.of(128, RED.red, RED.green, RED.blue)))
        } }
        val halfCoverage = renderTwice(halfCoverageSurface)
        assertPixelNear(halfCoverage, 1, 3, 137, 0, 224, 255, tolerance = 1)
    }

    @Test
    fun `mixed root two rings retain sibling chronology`() {
        val yellow = ColorARGB.of(255, 255, 235, 59)
        val surface = Surface(8, 8)
        surface.canvas {
            drawRect(FULL, gradientPaint())
            drawRect(RING, ringPaint(RED))
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(GREEN, antiAlias = false))
            drawRect(RectF32.ofLTRB(4.5f, 2.5f, 7.5f, 5.5f), ringPaint(yellow))
        }
        val first = renderTwice(surface)
        assertPixel(first, 2, 2, GREEN.red.toInt(), GREEN.green.toInt(), GREEN.blue.toInt(), 255)
        assertPixel(first, 2, 4, RED.red.toInt(), RED.green.toInt(), RED.blue.toInt(), 255)
        assertPixel(first, 4, 2, yellow.red.toInt(), yellow.green.toInt(), yellow.blue.toInt(), 255)
        assertPixel(first, 5, 3, RED.red.toInt(), RED.green.toInt(), RED.blue.toInt(), 255)
        assertPixel(first, 6, 3, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)
        assertPixel(first, 0, 0, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)
    }

    @Test
    fun `mixed root scaled hairline respects device clip`() {
        val clipped = Surface(8, 8)
        clipped.canvas {
            drawRect(FULL, gradientPaint())
            save(); clipRect(RectF32.ofLTRB(2f, 2f, 5f, 6f), antiAlias = false); scale(2f, 2f)
            drawRect(RectF32.ofLTRB(1.25f, 1.25f, 2.75f, 2.75f), ringPaint(RED, width = 0f)); restore()
        }
        val first = renderTwice(clipped)
        assertPixel(first, 2, 2, RED.red.toInt(), RED.green.toInt(), RED.blue.toInt(), 255)
        assertPixel(first, 4, 2, RED.red.toInt(), RED.green.toInt(), RED.blue.toInt(), 255)
        assertPixel(first, 2, 4, RED.red.toInt(), RED.green.toInt(), RED.blue.toInt(), 255)
        assertPixel(first, 5, 2, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)
        assertPixel(first, 3, 3, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)
        assertPixel(first, 0, 0, BLUE.red.toInt(), BLUE.green.toInt(), BLUE.blue.toInt(), 255)

        val translatedSurface = Surface(8, 8).also { target -> target.canvas {
            drawRect(FULL, gradientPaint()); save(); translate(1f, 1f)
            drawRect(RectF32.ofLTRB(1.5f, 1.5f, 4.5f, 4.5f), ringPaint(RED)); restore()
        } }
        val translated = renderTwice(translatedSurface)
        assertRing(translated, RED, BLUE)
    }

    @Test
    fun `mixed root invalid siblings refuse without publication`() {
        val picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(FULL).drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(RED, antiAlias = false))
        }.finishRecordingAsPicture()
        data class InvalidCase(val label: String, val expectedPrefix: String, val record: (Surface) -> Unit)
        val invalid = listOf(
            InvalidCase("stroke Shader.SolidColor", "unsupported.stroke.rect_anti_alias") { it.canvas { drawRect(FULL, gradientPaint()); drawRect(RING, Paint(shader = Shader.SolidColor(RED), antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 1f)) } },
            InvalidCase("stroke non-SrcOver", "unsupported.stroke.rect_anti_alias") { it.canvas { drawRect(FULL, gradientPaint()); drawRect(RING, Paint(RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 1f, blendMode = BlendMode.PLUS)) } },
            InvalidCase("AA clip", "unsupported.stroke.rect_anti_alias") { it.canvas { drawRect(FULL, gradientPaint()); save(); clipRect(RectF32.ofLTRB(1f, 1f, 7f, 7f), antiAlias = true); drawRect(RING, ringPaint(RED)); restore() } },
            InvalidCase("Picture sibling", "unsupported.composite.paint") { it.canvas { drawRect(FULL, gradientPaint()); drawPicture(picture); drawRect(RING, ringPaint(RED)) } },
            InvalidCase("stroke image filter", "w6a.layer.unsupported_child") { it.canvas { drawRect(FULL, gradientPaint()); drawRect(RING, Paint(RED, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 1f, imageFilter = ImageFilter.Blur(1f, 1f))) } },
            InvalidCase("transparent offscreen stroke", "w4d.general.path-resource-limit") { it.canvas { drawRect(FULL, gradientPaint()); drawRect(RectF32.ofLTRB(20f, 20f, 22f, 22f), ringPaint(ColorARGB.of(0, RED.red, RED.green, RED.blue))) } },
        )
        invalid.forEach { (label, expected, record) ->
            val surface = Surface(8, 8); record(surface)
            assertRefusesAndRecovers(surface, expected, label)
        }
    }

    @Test
    fun `mixed root exact budget refuses before publication and recovers`() {
        // B=29408: root RGBA8 256 + readback 2048 + W6 uniform 16 + AA4 colour 1024 + resolve 256 +
        // AA4 D24S8 1024 + the ring W4d V/I/U floors (16384+4096+4096) +
        // ring solid 16 + W3 LinearGradientV1 uniform (16+112) + two-stop GradientStopData slab 64.
        val budgetB = 29_408L
        val accepted = Surface(8, 8, config = RenderConfig(frameLocalBudgetBytes = budgetB)); recordMixed(accepted)
        assertRing(renderTwice(accepted), RED, BLUE)
        val refused = Surface(8, 8, config = RenderConfig(frameLocalBudgetBytes = budgetB - 1L)); recordMixed(refused)
        assertRefusesAndRecovers(refused, "w6a.layer.frame_budget_exceeded")
    }

    private fun mixedBase(ring: ColorARGB = RED): Surface = Surface(8, 8).also { recordMixed(it, ring) }
    private fun recordMixed(surface: Surface, ring: ColorARGB = RED) = surface.canvas { drawRect(FULL, gradientPaint()); drawRect(RING, ringPaint(ring)) }
    private fun gradientPaint() = Paint(shader = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(8f, 0f),
        listOf(org.graphiks.kanvas.paint.GradientStop(0f, BLUE), org.graphiks.kanvas.paint.GradientStop(1f, BLUE))), antiAlias = false)
    private fun ringPaint(color: ColorARGB, width: Float = 1f) = Paint(color, antiAlias = true, style = PaintStyle.STROKE, strokeWidth = width)

    private fun assertRing(result: RenderResult, red: ColorARGB, blue: ColorARGB) {
        for (y in 0 until 8) for (x in 0 until 8) {
            val onRing = x in 2..5 && y in 2..5 && (x !in 3..4 || y !in 3..4)
            val expected = if (onRing) red else blue
            assertPixel(result, x, y, expected.red.toInt(), expected.green.toInt(), expected.blue.toInt(), expected.alpha.toInt())
        }
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), result.nativeEvidenceScopeKinds.toString())
    }
    private fun assertAll(result: RenderResult, color: ColorARGB) { for (y in 0 until 8) for (x in 0 until 8)
        assertPixel(result, x, y, color.red.toInt(), color.green.toInt(), color.blue.toInt(), color.alpha.toInt()) }
    private fun renderTwice(surface: Surface): RenderResult {
        val first = surface.render()
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), first.nativeEvidenceScopeKinds.toString())
        assertContentEquals(first.pixels, surface.render().pixels)
        return first
    }
    private fun assertRefusesAndRecovers(surface: Surface, expectedPrefix: String? = null, context: String = "") {
        val sentinel = UByteArray(8 * 8 * 4) { 0x5au }; val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(FULL, sentinel) }
        expectedPrefix?.let { assertTrue(failure.message?.startsWith("$it:") == true, "$context: ${failure.message}") }
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations(); surface.canvas { drawRect(FULL, Paint(BLUE, antiAlias = false)) }
        assertAll(renderTwice(surface), BLUE)
    }
    private fun assertPixel(result: RenderResult, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * 8 + x) * 4
        assertContentEquals(ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()), result.pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }
    private fun assertPixelNear(result: RenderResult, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int, tolerance: Int) {
        val offset = (y * 8 + x) * 4
        listOf(r, g, b).forEachIndexed { index, expected ->
            assertTrue(kotlin.math.abs(result.pixels[offset + index].toInt() - expected) <= tolerance, "pixel ($x,$y)")
        }
        assertTrue(result.pixels[offset + 3].toInt() == a, "pixel ($x,$y) alpha")
    }

    private val FULL = RectF32.ofLTRB(0f, 0f, 8f, 8f)
    private val RING = RectF32.ofLTRB(2.5f, 2.5f, 5.5f, 5.5f)
    private val BLUE = ColorARGB.Blue
    private val RED = ColorARGB.Red
    private val GREEN = ColorARGB.of(255, 45, 179, 97)
}
