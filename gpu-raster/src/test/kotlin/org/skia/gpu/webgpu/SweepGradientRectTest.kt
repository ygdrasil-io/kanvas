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
 * G4.3 / G4.3.1 acceptance test -- sweep gradient fill of a rect on GPU.
 *
 * Routed through SkCanvas.drawRect : because `paint.shader` is non-null,
 * the canvas falls through to drawPath, which hits the G4.3 fast path in
 * `SkWebGpuDevice.drawPath` (path.isRect + axis-aligned CTM gate). G4.3.1
 * widens the dispatch to all 4 tile modes.
 *
 * Verifies the gradient interpolates correctly around the angle from the
 * centre :
 *   - `t = 0` at the +X axis (right of centre, red here),
 *   - `t = 0.25` at +Y (down, since image-space Y is down -- green here),
 *   - `t = 0.5` at -X (left, blue here),
 *   - `t = 0.75` at -Y (up -- back to first stop in a 3-stop ramp).
 */
class SweepGradientRectTest {

    // G4.3.1 -- the kRepeat / kMirror / kDecal tests below exercise one
    // shader entry point each. Sweep gradients only produce a sensible
    // t_raw outside [0, 1] when the start/end angle range is narrower
    // than the full 360 turn (otherwise t_raw is always in [0, 1] and
    // tile mode is a no-op). We pass startAngle / endAngle through
    // SkSweepGradient.Make's primary overload to constrain the range.

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

    @Test
    fun `kRepeat tiles the sweep gradient outside the start-end range`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 180-degree sweep starting at the +X axis (centre 32, 32) :
        //   - unitAngle in [0, 0.5] -> t_raw in [0, 1]   (bottom half, Y-down)
        //   - unitAngle in [0.5, 1) -> t_raw in [1, 2)   (top half)
        // kRepeat : the top half wraps back so t = t_raw - 1, giving the
        // SAME red->blue ramp across the upper half as the lower half.
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 180f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kRepeat,
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

        // Just below centre (x = 32, y = 50) : angle ~= pi/2 -> unitAngle
        // ~= 0.25 -> t_raw ~= 0.5 -> mid red+blue purple.
        val downMid = pixels.rgbaAt(32, 50)
        assertTrue(downMid[0] in 80..200, "downMid.R balanced, got ${downMid[0]}")
        assertTrue(downMid[2] in 80..200, "downMid.B balanced, got ${downMid[2]}")

        // Just above centre (x = 32, y = 14) : angle ~= -pi/2 -> CW
        // unitAngle ~= 0.75 -> t_raw ~= 1.5. kRepeat wraps to t = 0.5
        // -> mid red+blue purple (SAME as the lower half).
        // This is the kRepeat-vs-kMirror discriminator : kMirror would
        // reflect to t = 0.5 too (same here), but the kRepeat-vs-kClamp
        // discriminator is solid : kClamp would give the last stop (blue).
        val upMid = pixels.rgbaAt(32, 14)
        assertTrue(upMid[0] in 80..200, "upMid.R balanced (kRepeat wrap), got ${upMid[0]}")
        assertTrue(upMid[2] in 80..200, "upMid.B balanced (kRepeat wrap), got ${upMid[2]}")

        // Just above and right of centre (x = 50, y = 14) : relative
        // (18.5, -17.5) -> angle ~= -0.76 rad -> u ~= -0.121 -> CW u
        // ~= 0.879 -> t_raw ~= 1.758. kRepeat -> t = 0.758 -> mostly
        // blue. kClamp would give pure blue (last stop). The
        // discriminator vs kClamp is the angle just before the +X axis
        // wrap, but the cleanest discriminator vs kMirror is below.
        val upRight = pixels.rgbaAt(50, 14)
        assertTrue(upRight[2] in 100..255, "upRight.B blue-heavy, got ${upRight[2]}")
        assertTrue(upRight[0] in 0..200, "upRight.R lower than blue, got ${upRight[0]}")

        // Just above and slight-right of centre (x = 34, y = 14) :
        // relative (2.5, -17.5) -> angle ~= -1.43 rad -> CW u ~= 0.728
        // -> t_raw ~= 1.456. kRepeat -> t = 0.456 -> roughly mid
        // red+blue. kMirror would give t = 1 - 0.456 = 0.544 -- also
        // roughly mid, so this isn't a discriminator. The clean kRepeat
        // signal here is "upMid behaves like downMid" (asserted above).
        val upSlightRight = pixels.rgbaAt(34, 14)
        assertTrue(upSlightRight[0] in 80..220, "upSlightRight.R balanced, got ${upSlightRight[0]}")
    }

    @Test
    fun `kMirror reflects the sweep gradient at the end-angle boundary`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Same 180-degree sweep setup as kRepeat above.
        // kMirror : the top half (t_raw in [1, 2)) reflects to t = 2 - t_raw.
        // At unitAngle = 0.75 (top, -Y), t_raw = 1.5 -> mirror -> t = 0.5.
        // The interesting discriminator vs kRepeat is just past the end
        // boundary : unitAngle just over 0.5 -> t_raw just over 1 -> mirror
        // gives t just under 1 (mostly blue, NOT red).
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 180f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kMirror,
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

        // Just below centre (x = 32, y = 50) : t_raw ~= 0.5 -> in [0, 1],
        // no mirror needed -> mid purple (same as kRepeat).
        val downMid = pixels.rgbaAt(32, 50)
        assertTrue(downMid[0] in 80..200, "downMid.R balanced, got ${downMid[0]}")
        assertTrue(downMid[2] in 80..200, "downMid.B balanced, got ${downMid[2]}")

        // Just left of centre and slightly below (x = 14, y = 33) :
        // relative (-17.5, 0.5) -> angle ~= pi (very slightly under) ->
        // CW u ~= 0.4955 -> t_raw ~= 0.991. Just under 1 (still in
        // first period). Mostly blue.
        val leftMidBlue = pixels.rgbaAt(14, 33)
        assertTrue(leftMidBlue[2] >= 180, "leftMidBlue.B mostly blue, got ${leftMidBlue[2]}")
        assertTrue(leftMidBlue[0] < 80, "leftMidBlue.R low, got ${leftMidBlue[0]}")

        // Just LEFT of centre but slightly ABOVE (x = 14, y = 31) :
        // relative (-17.5, -1.5) -> angle ~= -pi + small -> CW u ~= 0.5136
        // -> t_raw ~= 1.027. kMirror -> t = 2 - 1.027 = 0.973 -> still
        // mostly blue. This is the key kMirror-vs-kRepeat discriminator :
        // with kRepeat the same x would give t = 0.027 -> mostly RED.
        val leftMidMirror = pixels.rgbaAt(14, 31)
        assertTrue(leftMidMirror[2] >= 180, "leftMidMirror.B mostly blue, got ${leftMidMirror[2]}")
        assertTrue(leftMidMirror[0] < 80, "leftMidMirror.R low (mirror, NOT repeat), got ${leftMidMirror[0]}")

        // Just above centre (x = 32, y = 14) : angle ~= -pi/2 -> CW u
        // ~= 0.75 -> t_raw = 1.5 -> mirror -> t = 0.5 -> mid purple.
        val upMid = pixels.rgbaAt(32, 14)
        assertTrue(upMid[0] in 80..200, "upMid.R balanced, got ${upMid[0]}")
        assertTrue(upMid[2] in 80..200, "upMid.B balanced, got ${upMid[2]}")
    }

    @Test
    fun `kDecal punches transparent outside the start-end range`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // 180-degree sweep again : kDecal -> the top half (t_raw outside
        // [0, 1]) renders fully transparent (premul (0, 0, 0, 0)) over
        // the white background, so the upper half of the rect stays
        // WHITE while the lower half shows the red->blue ramp.
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 180f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kDecal,
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

        // Just below centre (x = 32, y = 50) : t_raw ~= 0.5 (inside)
        // -> mid red+blue purple.
        val downMid = pixels.rgbaAt(32, 50)
        assertTrue(downMid[0] in 80..200, "downMid.R balanced, got ${downMid[0]}")
        assertTrue(downMid[2] in 80..200, "downMid.B balanced, got ${downMid[2]}")
        assertTrue(downMid[3] >= 250, "downMid.A near opaque, got ${downMid[3]}")

        // Below + right (x = 50, y = 50) : relative (18.5, 18.5) ->
        // angle = pi/4 -> u = 0.125 -> t_raw = 0.25 -> roughly 3/4 red,
        // 1/4 blue.
        val downRight = pixels.rgbaAt(50, 50)
        assertTrue(downRight[0] >= 120, "downRight.R red-heavy, got ${downRight[0]}")
        assertTrue(downRight[2] < 150, "downRight.B low-mid, got ${downRight[2]}")

        // Just ABOVE centre (x = 32, y = 14) : t_raw ~= 1.5 (outside
        // [0, 1]) -> kDecal renders transparent -> background WHITE.
        // This is the key kDecal discriminator vs the other tile modes.
        val upMid = pixels.rgbaAt(32, 14)
        assertNear(upMid[0], 255, "upMid.R white (decal punch-through)", tol = 16)
        assertNear(upMid[1], 255, "upMid.G white (decal punch-through)", tol = 16)
        assertNear(upMid[2], 255, "upMid.B white (decal punch-through)", tol = 16)

        // Above + left (x = 14, y = 14) : also in the top half -> decal
        // punch-through -> white.
        val upLeft = pixels.rgbaAt(14, 14)
        assertNear(upLeft[0], 255, "upLeft.R white (decal punch-through)", tol = 16)
        assertNear(upLeft[1], 255, "upLeft.G white (decal punch-through)", tol = 16)
        assertNear(upLeft[2], 255, "upLeft.B white (decal punch-through)", tol = 16)
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
