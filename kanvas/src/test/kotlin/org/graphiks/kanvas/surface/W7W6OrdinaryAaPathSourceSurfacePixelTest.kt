@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public Surface witnesses for ordinary PATH AA sources in a frame owned by a filtered sibling. */
class W7W6OrdinaryAaPathSourceSurfacePixelTest {
    @Test
    fun ordinaryStrokeSurvivesFilteredSibling() {
        val surface = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(line(), Paint(RED, style = PaintStyle.STROKE, strokeWidth = 2f, antiAlias = true))
        } }

        val result = surface.render()
        assertNative(result)
        assertPixel(result.pixels, 16, 16, 255, 0, 0, 255)
        assertPixel(result.pixels, 16, 13, 0, 0, 0, 0)
    }

    @Test
    fun ordinaryFillSurvivesFilteredSibling() {
        val surface = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(triangle(), Paint(RED, antiAlias = true))
        } }

        val result = surface.render()
        assertNative(result)
        assertPixel(result.pixels, 10, 10, 255, 0, 0, 255)
        assertPixel(result.pixels, 28, 28, 0, 0, 0, 0)
    }

    @Test
    fun isolatedSourcePreservesPremultipliedColorAndOrder() {
        // LINEAR composition stores sRGB-encoded linear-premultiplied readback. Half red
        // coverage is therefore (188,0,0,128), not bytewise RGB=alpha.
        val once = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(line(), Paint(RED, style = PaintStyle.STROKE, strokeWidth = 1f, antiAlias = true))
        } }.render()
        assertNative(once)
        assertPixelNear(once.pixels, 16, 15, 188, 0, 0, 128, 1)

        // Each occurrence resolves its own half-covered source before SrcOver. Two 1/2-alpha
        // sources yield 3/4 alpha (191/192), while a shared retained root sample stays 1/2.
        val twice = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(line(), Paint(RED, style = PaintStyle.STROKE, strokeWidth = 1f, antiAlias = true))
            drawPath(line(), Paint(RED, style = PaintStyle.STROKE, strokeWidth = 1f, antiAlias = true))
        } }.render()
        assertNative(twice)
        assertPixelNear(twice.pixels, 16, 15, 225, 0, 0, 191, 1)
        assertTrue(twice.pixels[(15 * SIZE + 16) * 4 + 3].toInt() in 191..192)

        // The blur is well inside its support at (12,12); full SrcOver coverage makes order
        // observable without estimating a halo pixel.
        fun overlap(filteredFirst: Boolean): RenderResult = Surface(SIZE, SIZE).also { target -> target.canvas {
            if (filteredFirst) {
                drawRect(OVERLAPPING_BLUR, Paint(BLUE, maskFilter = normalBlur(), antiAlias = false))
                drawPath(triangle(), Paint(RED, antiAlias = true))
            } else {
                drawPath(triangle(), Paint(RED, antiAlias = true))
                drawRect(OVERLAPPING_BLUR, Paint(BLUE, maskFilter = normalBlur(), antiAlias = false))
            }
        } }.render()

        val pathOnTop = overlap(filteredFirst = true)
        assertNative(pathOnTop)
        assertPixel(pathOnTop.pixels, 12, 12, 255, 0, 0, 255)
        val filteredOnTop = overlap(filteredFirst = false)
        assertNative(filteredOnTop)
        assertPixel(filteredOnTop.pixels, 12, 12, 0, 0, 255, 255)
    }

    @Test
    fun ordinarySourceDoesNotBorrowForeignAuthority() {
        // The already-supported filtered PATH fill remains on its existing AA-coverage/filter
        // lane when another root occurrence makes W6 the frame owner.
        val filteredPath = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(triangle(), Paint(RED, maskFilter = normalBlur(), antiAlias = true))
        } }.render()
        assertNative(filteredPath)
        assertPixel(filteredPath.pixels, 12, 12, 255, 0, 0, 255)

        // Foreign style and blend facts retain the existing precise refusal and a clean next
        // native frame. These guards prevent ordinary-source admission from laundering them.
        val strokeWithFilter = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(line(), Paint(RED, style = PaintStyle.STROKE, strokeWidth = 2f,
                maskFilter = normalBlur(), antiAlias = true))
        } }
        assertRefusesAndRecovers(strokeWithFilter)

        val plusBlend = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            drawPath(triangle(), Paint(RED, antiAlias = true, blendMode = org.graphiks.kanvas.paint.BlendMode.PLUS))
        } }
        assertRefusesAndRecovers(plusBlend)

        val nonSolid = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            val gradient = Shader.LinearGradient(
                org.graphiks.math.geometry.Point2F32(8f, 8f),
                org.graphiks.math.geometry.Point2F32(24f, 8f),
                listOf(GradientStop(0f, RED), GradientStop(1f, BLUE)),
            )
            drawPath(triangle(), Paint(shader = gradient, antiAlias = true))
        } }
        assertRefusesAndRecovers(nonSolid)

        val aaClip = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            save()
            clipRect(RectF32.ofLTRB(8f, 8f, 24f, 24f), antiAlias = true)
            drawPath(triangle(), Paint(RED, antiAlias = true))
            restore()
        } }
        assertRefusesAndRecovers(aaClip)
    }

    @Test
    fun ordinaryHairlineSurvivesFilteredSiblingAndKeepsDeviceWidth() {
        val standalone = Surface(SIZE, SIZE).also { target -> target.canvas {
            save()
            scale(2f, 3f)
            drawPath(
                Path().apply {
                    moveTo(4f, 5.5f)
                    lineTo(20f, 5.5f)
                },
                Paint(RED, style = PaintStyle.STROKE, strokeWidth = 0f,
                    strokeCap = StrokeCap.BUTT, antiAlias = true),
            )
            restore()
        } }.render()
        assertNative(standalone)
        assertPixel(standalone.pixels, 16, 16, 255, 0, 0, 255)
        assertPixel(standalone.pixels, 16, 15, 0, 0, 0, 0)
        assertPixel(standalone.pixels, 16, 17, 0, 0, 0, 0)
        assertPixel(standalone.pixels, 6, 16, 0, 0, 0, 0)
        assertPixel(standalone.pixels, 42, 16, 0, 0, 0, 0)

        val withFilteredSibling = Surface(SIZE, SIZE).also { target -> target.canvas {
            drawFilteredSibling()
            save()
            scale(2f, 3f)
            drawPath(
                Path().apply {
                    moveTo(4f, 5.5f)
                    lineTo(20f, 5.5f)
                },
                Paint(RED, style = PaintStyle.STROKE, strokeWidth = 0f,
                    strokeCap = StrokeCap.BUTT, antiAlias = true),
            )
            restore()
        } }.render()
        assertNative(withFilteredSibling)
        assertPixel(withFilteredSibling.pixels, 16, 16, 255, 0, 0, 255)
        assertPixel(withFilteredSibling.pixels, 16, 15, 0, 0, 0, 0)
        assertPixel(withFilteredSibling.pixels, 16, 17, 0, 0, 0, 0)
        assertPixel(withFilteredSibling.pixels, 6, 16, 0, 0, 0, 0)
        assertPixel(withFilteredSibling.pixels, 42, 16, 0, 0, 0, 0)
    }

    private fun Canvas.drawFilteredSibling() {
        drawRect(NON_OVERLAPPING_BLUR, Paint(BLUE, maskFilter = normalBlur(), antiAlias = false))
    }

    private fun normalBlur() = MaskFilter.Blur(BlurStyle.NORMAL, 1f)

    private fun line() = Path().apply {
        moveTo(8f, 16f)
        lineTo(40f, 16f)
    }

    private fun triangle() = Path().apply {
        moveTo(8f, 8f)
        lineTo(24f, 8f)
        lineTo(8f, 24f)
        close()
    }

    private fun assertRefusesAndRecovers(surface: Surface) {
        val bounds = RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        val sentinel = UByteArray(SIZE * SIZE * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6a.layer.unsupported_child:") == true, failure.message)
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint(BLUE, antiAlias = false)) }
        val recovered = surface.render()
        assertNative(recovered)
        assertPixel(recovered.pixels, 16, 16, 0, 0, 255, 255)
    }

    private fun assertNative(result: RenderResult) {
        val trace = "diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.isClean, trace)
        assertTrue(result.diagnostics.isEmpty, trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertTrue(result.stats.opsRefused == 0, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")), trace)
    }

    private fun assertPixel(pixels: UByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int) {
        val offset = (y * SIZE + x) * 4
        assertContentEquals(ubyteArrayOf(r.toUByte(), g.toUByte(), b.toUByte(), a.toUByte()),
            pixels.copyOfRange(offset, offset + 4), "pixel ($x,$y)")
    }

    private fun assertPixelNear(pixels: UByteArray, x: Int, y: Int, r: Int, g: Int, b: Int, a: Int, tolerance: Int) {
        val offset = (y * SIZE + x) * 4
        listOf(r, g, b, a).forEachIndexed { channel, expected ->
            assertTrue(kotlin.math.abs(pixels[offset + channel].toInt() - expected) <= tolerance,
                "pixel ($x,$y) channel $channel: expected $expected ±$tolerance, got ${pixels[offset + channel]}")
        }
    }

    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }

        private const val SIZE = 64
        private val NON_OVERLAPPING_BLUR = RectF32.ofLTRB(48f, 48f, 56f, 56f)
        private val OVERLAPPING_BLUR = RectF32.ofLTRB(4f, 4f, 36f, 36f)
        private val RED = ColorARGB.Red
        private val BLUE = ColorARGB.Blue
    }
}
