@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.surface.gpu.GPUPlanSurfaceTerminalException
import org.junit.jupiter.api.Test

/** Public W7 witness for a resolved AA path colour source inside a W6 layer. */
class W7AaPathLayerSurfacePixelTest {
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
}
