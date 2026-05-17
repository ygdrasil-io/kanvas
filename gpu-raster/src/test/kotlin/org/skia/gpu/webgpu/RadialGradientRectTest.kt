package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkRect

/**
 * G4.2 acceptance test -- radial gradient (kClamp) fill of a rect on GPU.
 *
 * Routed through SkCanvas.drawRect : because `paint.shader` is non-null,
 * the canvas falls through to drawPath, which hits the G4.2 fast path
 * in `SkWebGpuDevice.drawPath` (path.isRect + axis-aligned CTM gate +
 * kClamp tile mode).
 *
 * Verifies the gradient interpolates correctly along the radial distance
 * from the centre : red at t = 0 (centre), blue at t = 1 (radius), the
 * mid lerp at half-radius. Clamp tile mode means pixels beyond the
 * radius all sample the last stop (blue here).
 */
class RadialGradientRectTest {

    @Test
    fun `radial red-to-blue gradient interpolates along distance from center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Centre at the rect's centre (32, 32) ; radius = 20 px so the
        // last stop (blue) is hit ~20 px out and stays clamped beyond.
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 20f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = false
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 2f, 62f, 62f), paint)
                device.flush()
            }
        }

        // At the centre (32, 32) : t = 0 -> red. pixel-center is at
        // (32.5, 32.5) so distance ~= 0.707 -> t = 0.035 -> still red.
        // The G6.1 sRGB -> Rec.2020 present-pass transform pulls pure
        // red from 255 to ~246, hence the band rather than tight tol.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")
        assertTrue(center[3] >= 250, "center.A near opaque, got ${center[3]}")

        // Edge of the gradient ~20 px out along +x : x = 52, y = 32.
        // distance = ~19.5 -> t ~= 0.975 -> mostly blue.
        val edgeRight = pixels.rgbaAt(52, 32)
        assertTrue(edgeRight[0] < 64, "edgeRight.R mostly zero, got ${edgeRight[0]}")
        assertTrue(edgeRight[2] >= 200, "edgeRight.B mostly blue, got ${edgeRight[2]}")

        // Halfway along +x : x = 42, y = 32. distance ~= 9.5 -> t = 0.475
        // -> balanced red and blue. The present-pass colorspace transform
        // can shift channels by a few dozen LSBs in the mid lerp region,
        // hence the generous band assertions.
        val mid = pixels.rgbaAt(42, 32)
        assertTrue(mid[0] in 80..200, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 80..200, "mid.B balanced, got ${mid[2]}")

        // Beyond the radius : pixel at (60, 32) is ~28 px out -> clamped
        // to t = 1 -> blue. Same band as edgeRight.
        val beyond = pixels.rgbaAt(60, 32)
        assertTrue(beyond[0] < 64, "beyond.R mostly zero, got ${beyond[0]}")
        assertTrue(beyond[2] >= 200, "beyond.B mostly blue, got ${beyond[2]}")

        // Symmetry check : pixel at (12, 32) is also ~20 px out -> blue.
        // (radial gradient must be rotationally symmetric.)
        val edgeLeft = pixels.rgbaAt(12, 32)
        assertTrue(edgeLeft[0] < 64, "edgeLeft.R mostly zero, got ${edgeLeft[0]}")
        assertTrue(edgeLeft[2] >= 200, "edgeLeft.B mostly blue, got ${edgeLeft[2]}")

        // Outside the rect : background white untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertNear(outside[0], 255, "outside.R", tol = 16)
        assertNear(outside[1], 255, "outside.G", tol = 16)
        assertNear(outside[2], 255, "outside.B", tol = 16)
    }

    private fun assertNear(actual: Int, expected: Int, label: String, tol: Int) {
        val diff = kotlin.math.abs(actual - expected)
        assertTrue(diff <= tol, "$label : expected ~$expected (tol $tol), got $actual")
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
