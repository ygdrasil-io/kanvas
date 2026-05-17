package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkRect

/**
 * G4.3 acceptance test -- sweep gradient (kClamp) fill of a rect on GPU.
 *
 * Routed through SkCanvas.drawRect : because `paint.shader` is non-null,
 * the canvas falls through to drawPath, which hits the G4.3 fast path in
 * `SkWebGpuDevice.drawPath` (path.isRect + axis-aligned CTM gate + kClamp
 * tile mode).
 *
 * Verifies the gradient interpolates correctly around the angle from the
 * centre :
 *   - `t = 0` at the +X axis (right of centre, red here),
 *   - `t = 0.25` at +Y (down, since image-space Y is down -- green here),
 *   - `t = 0.5` at -X (left, blue here),
 *   - `t = 0.75` at -Y (up -- back to first stop in a 3-stop ramp).
 */
class SweepGradientRectTest {

    @Test
    fun `red-green-blue sweep gradient interpolates around the center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Centre at the rect's centre (32, 32). Default angle range
        // [0, 360]. Stops red -> green -> blue evenly spaced :
        //   t = 0.0 -> red, t = 0.5 -> green, t = 1.0 -> blue.
        // The 360-degree wrap closes (red == blue) which gives a hard
        // line across the negative-X axis -- expected with kClamp.
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE),
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

        // Pixel-center is at (x + 0.5, y + 0.5). The sample points below
        // are deliberately a few pixels away from the centre so the
        // (0, 0) singularity at the centre doesn't matter.
        // Just right of centre (column 50, row 32) : pixel-center
        // (50.5, 32.5), relative (18.5, 0.5) -> angle ~= 0.027 rad
        // -> t = 0.0043 -> firmly red.
        val right = pixels.rgbaAt(50, 32)
        assertTrue(right[0] in 200..255, "right.R mostly red, got ${right[0]}")
        assertTrue(right[1] < 80, "right.G near zero, got ${right[1]}")
        assertTrue(right[2] < 80, "right.B near zero, got ${right[2]}")
        assertTrue(right[3] >= 250, "right.A near opaque, got ${right[3]}")

        // Just below centre (column 32, row 50, image-space Y-down) :
        // relative (0.5, 18.5) -> angle ~= pi/2 - small -> t ~= 0.25
        // -> halfway between red and green -> roughly equal R + G.
        val down = pixels.rgbaAt(32, 50)
        assertTrue(down[0] in 80..200, "down.R balanced, got ${down[0]}")
        assertTrue(down[1] in 80..200, "down.G balanced, got ${down[1]}")
        assertTrue(down[2] < 80, "down.B near zero, got ${down[2]}")

        // Just below-and-down-and-right by a 45-degree axis (column 45,
        // row 45) : relative (13.5, 13.5) -> angle = pi/4 -> t = 0.125
        // -> first half of red->green band, ~75% red, ~25% green.
        val downRight = pixels.rgbaAt(45, 45)
        assertTrue(downRight[0] in 120..255, "downRight.R red-heavy, got ${downRight[0]}")
        assertTrue(downRight[1] < 180, "downRight.G low-mid, got ${downRight[1]}")
        assertTrue(downRight[2] < 80, "downRight.B near zero, got ${downRight[2]}")

        // Just below centre + small +x : (33, 60) : relative (1.5, 28.5)
        // -> angle ~= pi/2 + 0.05 -> t ~= 0.258 -> just past mid-green.
        // Sanity check : G must be strong.
        val downSlightlyRight = pixels.rgbaAt(33, 60)
        assertTrue(downSlightlyRight[1] >= 120, "downSlightlyRight.G strong, got ${downSlightlyRight[1]}")

        // Just left of centre (column 14, row 32) : relative
        // (-17.5, 0.5) -> angle ~= pi - small -> after CW normalisation
        // u ~= 0.5 -> t = 0.5 -> green ; with tiny y > 0, t is just
        // *under* 0.5, still very green (G == 255 at exactly t = 0.5,
        // dropping linearly on either side).
        val left = pixels.rgbaAt(14, 32)
        assertTrue(left[1] >= 200, "left.G mostly green, got ${left[1]}")
        assertTrue(left[0] < 80, "left.R near zero, got ${left[0]}")
        assertTrue(left[2] < 80, "left.B near zero, got ${left[2]}")

        // Above centre (column 32, row 14) : relative (0.5, -17.5) ->
        // angle ~= -pi/2 -> u (after CW normalisation) = 0.75 -> halfway
        // between green and blue (t = 0.75 splits stop 1 (green @ 0.5)
        // and stop 2 (blue @ 1.0) at 0.5) -> roughly equal G + B.
        val up = pixels.rgbaAt(32, 14)
        assertTrue(up[1] in 80..200, "up.G balanced, got ${up[1]}")
        assertTrue(up[2] in 80..200, "up.B balanced, got ${up[2]}")
        assertTrue(up[0] < 80, "up.R near zero, got ${up[0]}")

        // Outside the rect : white background untouched.
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
