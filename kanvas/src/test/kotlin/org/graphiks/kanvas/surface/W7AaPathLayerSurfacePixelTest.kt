@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.surface.gpu.GPUPlanSurfaceTerminalException
import org.junit.jupiter.api.Test

/** Public W7 witness for a resolved AA path colour source inside a W6 layer. */
class W7AaPathLayerSurfacePixelTest {
    @Test
    fun `aa concave stencil path preserves notch`() {
        // A concave winding L forces the stencil route.  These full-coverage samples and the
        // notch are literal public observations, fixed before Surface construction.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val concaveL = Path().apply {
            moveTo(1f, 1f); lineTo(6f, 1f); lineTo(6f, 3f)
            lineTo(3f, 3f); lineTo(3f, 6f); lineTo(1f, 6f); close()
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(concaveL, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 4, 4, 0, 0, 0, 0)
        assertPixel(result.pixels, 7, 0, 0, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa even odd stencil path preserves hole`() {
        // Same-winding contours select EVEN_ODD. The center is a hole, not transparent fallout.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val donut = Path().apply {
            addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f))
            addRect(RectF32.ofLTRB(2f, 2f, 5f, 5f))
            fillType = FillType.EVEN_ODD
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(donut, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 3, 3, 0, 0, 0, 0)
        assertPixel(result.pixels, 7, 0, 0, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa stencil children are isolated`() {
        // Both concave children require independent stencil state. The second's blue sample and
        // the exterior transparent sample detect a stale stencil/resolve from the first child.
        val green = ColorARGB.of(255, 45, 179, 97)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val first = Path().apply {
            moveTo(1f, 1f); lineTo(3f, 1f); lineTo(3f, 3f)
            lineTo(2f, 3f); lineTo(2f, 5f); lineTo(1f, 5f); close()
        }
        val second = Path().apply {
            moveTo(4f, 1f); lineTo(6f, 1f); lineTo(6f, 3f)
            lineTo(5f, 3f); lineTo(5f, 5f); lineTo(4f, 5f); close()
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(first, Paint(green, antiAlias = true))
            drawPath(second, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 45, 179, 97, 255)
        assertPixel(result.pixels, 7, 4, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 0, 6, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa direct triangle preserves painted parent`() {
        // These literal samples are fixed before either Surface exists.  The full-cover sample
        // must be blue, while both exterior samples prove the isolated resolve did not clear the
        // opaque red parent.  (4, 1) is centred on the diagonal AA edge in the transparent run.
        val red = ColorARGB.of(255, 239, 51, 73)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val triangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }

        val paintedParent = Surface(7, 7)
        paintedParent.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(red, antiAlias = false))
            saveLayer()
            drawPath(triangle, Paint(blue, antiAlias = true))
            restore()
        }
        val parentResult = try {
            paintedParent.render()
        } catch (refusal: GPUPlanSurfaceTerminalException) {
            // The runtime capability snapshot may refuse sRGB 4x. This does not establish
            // hardware incapability and cannot stand in for the positive pixel oracle below.
            assertTrue(refusal.message?.contains("w4d.general.texture-sample-support-unavailable") == true,
                refusal.message)
            assertTrue(refusal.message?.contains("W4d.2 four-sample color support is unavailable") == true,
                refusal.message)
            println("W7 capability refusal (no positive pixel witness): ${refusal.message}")
            return
        }
        assertPixel(parentResult.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(parentResult.pixels, 7, 0, 0, 239, 51, 73, 255)
        assertPixel(parentResult.pixels, 7, 5, 5, 239, 51, 73, 255)
        assertTrue(parentResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            parentResult.nativeEvidenceScopeKinds.toString())

        val transparentParent = Surface(7, 7)
        transparentParent.canvas {
            saveLayer()
            drawPath(triangle, Paint(blue, antiAlias = true))
            restore()
        }
        val transparentResult = transparentParent.render()
        val diagonalAlpha = transparentResult.pixels[((1 * 7 + 4) * 4) + 3].toInt()
        assertTrue(diagonalAlpha in 1..254, "expected intermediate AA alpha, got $diagonalAlpha")
        assertTrue(transparentResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            transparentResult.nativeEvidenceScopeKinds.toString())
    }

    private fun assertPixel(
        pixels: UByteArray,
        width: Int,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val offset = (y * width + x) * 4
        assertTrue(pixels[offset].toInt() == red && pixels[offset + 1].toInt() == green &&
            pixels[offset + 2].toInt() == blue && pixels[offset + 3].toInt() == alpha,
            "pixel ($x,$y) was ${pixels.copyOfRange(offset, offset + 4).toList()}")
    }

    private fun renderOrAcceptExactAaCapabilityRefusal(surface: Surface): RenderResult? = try {
        surface.render()
    } catch (refusal: GPUPlanSurfaceTerminalException) {
        // The runtime capability snapshot may refuse sRGB 4x. This does not establish hardware
        // incapability and cannot stand in for the positive pixel oracles above.
        assertTrue(refusal.message?.contains("w4d.general.texture-sample-support-unavailable") == true,
            refusal.message)
        assertTrue(refusal.message?.contains("W4d.2 four-sample color support is unavailable") == true,
            refusal.message)
        println("W7 capability refusal (no positive pixel witness): ${refusal.message}")
        null
    }
}
