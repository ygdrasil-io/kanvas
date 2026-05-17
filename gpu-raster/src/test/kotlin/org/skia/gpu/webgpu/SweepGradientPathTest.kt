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
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode

/**
 * G4.3.2 acceptance test -- sweep gradient fill of a non-rect path
 * (circle) on GPU, exercising all 4 SkTileMode values. Mirrors
 * [SweepGradientRectTest] but the geometry routes through the AA
 * stencil-and-cover sweep-gradient pipeline instead of the rect-only
 * fast path. Mirrors [LinearGradientPathTest] / [RadialGradientPathTest]
 * in shape -- one test per tile mode, sampling at known angular
 * positions relative to the sweep centre.
 *
 * The path is a circle large enough that the sampled points sit well
 * inside the AA boundary (coverage = 1.0 at interior pixels, AA blend
 * only along the perimeter).
 *
 * Sweep direction convention : image-space Y points down so angles
 * increase clockwise from the +X axis (matches `SkSweepGradient`).
 */
class SweepGradientPathTest {

    @Test
    fun `kClamp sweep gradient on a circle path interpolates around the center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Disk centred at (32, 32) radius 28 covers most of the 64x64
        // surface. Default angle range [0, 360] with stops red -> green
        // -> blue evenly spaced (t = 0 red, t = 0.5 green, t = 1 blue).
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Just right of centre (50, 32) -> angle ~ 0 -> t ~ 0 -> red.
        val right = pixels.rgbaAt(50, 32)
        assertTrue(right[0] in 200..255, "right.R mostly red, got ${right[0]}")
        assertTrue(right[1] < 80, "right.G near zero, got ${right[1]}")
        assertTrue(right[2] < 80, "right.B near zero, got ${right[2]}")
        assertTrue(right[3] >= 240, "right.A near opaque, got ${right[3]}")

        // Just below centre (32, 50) -> angle ~ pi/2 -> t ~ 0.25 ->
        // halfway between red and green.
        val down = pixels.rgbaAt(32, 50)
        assertTrue(down[0] in 80..200, "down.R balanced, got ${down[0]}")
        assertTrue(down[1] in 80..200, "down.G balanced, got ${down[1]}")
        assertTrue(down[2] < 80, "down.B near zero, got ${down[2]}")

        // Just left of centre (14, 32) -> angle ~ pi -> t ~ 0.5 -> green.
        val left = pixels.rgbaAt(14, 32)
        assertTrue(left[0] < 80, "left.R near zero, got ${left[0]}")
        assertTrue(left[1] in 200..255, "left.G mostly green, got ${left[1]}")
        assertTrue(left[2] < 80, "left.B near zero, got ${left[2]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
    }

    @Test
    fun `kRepeat sweep gradient on a circle path wraps the constrained sweep`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Constrain the gradient range to [0, 90] degrees so kRepeat has
        // something non-trivial to wrap (a full [0, 360] sweep collapses
        // t_raw to [0, 1) and the tile mode would be a no-op).
        // Stops red -> blue ; tBias = 0, tScale = 4. So :
        //   angle 0    -> t_raw 0.000 -> repeat 0.000 -> red
        //   angle 45   -> t_raw 0.500 -> repeat 0.500 -> balanced
        //   angle 90   -> t_raw 1.000 -> repeat 0.000 -> red
        //   angle 180  -> t_raw 2.000 -> repeat 0.000 -> red
        //   angle 225  -> t_raw 2.500 -> repeat 0.500 -> balanced
        // Pixel centres at (x + 0.5, y + 0.5) shift sample angles slightly.
        // The +X / +Y / -X / -Y samples should all land near the start of
        // a period (mostly red), and the diagonal samples in the middle of
        // periods 0 and 2 should land near t = 0.5 (balanced).
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 90f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kRepeat,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Just right of centre, angle ~ 0 -> t_raw ~ 0 -> repeat -> red.
        val right = pixels.rgbaAt(50, 32)
        assertTrue(right[0] in 200..255, "right.R mostly red, got ${right[0]}")
        assertTrue(right[2] < 64, "right.B near zero, got ${right[2]}")

        // Down-right diagonal at ~45 deg (period 0 middle, t = 0.5) ->
        // balanced red + blue.
        val period0Mid = pixels.rgbaAt(46, 46)
        assertTrue(period0Mid[0] in 60..200, "period0Mid.R balanced, got ${period0Mid[0]}")
        assertTrue(period0Mid[2] in 60..200, "period0Mid.B balanced, got ${period0Mid[2]}")

        // Down-left diagonal at ~135 deg (period 1 middle, t_raw ~ 1.5,
        // repeat -> 0.5) -> balanced red + blue (same colour as period 0
        // middle thanks to wrap).
        val period1Mid = pixels.rgbaAt(18, 46)
        assertTrue(period1Mid[0] in 60..200, "period1Mid.R balanced, got ${period1Mid[0]}")
        assertTrue(period1Mid[2] in 60..200, "period1Mid.B balanced, got ${period1Mid[2]}")

        // Up-left diagonal at ~225 deg (period 2 middle, t_raw ~ 2.5,
        // repeat -> 0.5) -> balanced.
        val period2Mid = pixels.rgbaAt(18, 18)
        assertTrue(period2Mid[0] in 60..200, "period2Mid.R balanced, got ${period2Mid[0]}")
        assertTrue(period2Mid[2] in 60..200, "period2Mid.B balanced, got ${period2Mid[2]}")
    }

    @Test
    fun `kMirror sweep gradient on a circle path reflects the constrained sweep`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Constrain to [0, 90] degrees ; tScale = 4, tBias = 0. Stops
        // red -> blue.
        //   angle 0    -> t_raw 0   -> mirror -> 0   -> red
        //   angle 90   -> t_raw 1.0 -> mirror -> 0   -> red (end of mirror)
        //   angle 45   -> t_raw 0.5 -> mirror -> 0.5 -> half red half blue
        //   angle 135  -> t_raw 1.5 -> mirror -> 0.5 -> half red half blue
        //   angle 180  -> t_raw 2.0 -> mirror -> 0   -> red
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 90f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Right of centre, angle ~ 0, mirrored -> 0 -> red.
        val right = pixels.rgbaAt(50, 32)
        assertTrue(right[0] in 200..255, "right.R mostly red, got ${right[0]}")
        assertTrue(right[2] < 64, "right.B near zero, got ${right[2]}")

        // Down-right diagonal at ~45 deg, mirrored -> 0.5 -> balanced.
        val diag = pixels.rgbaAt(46, 46)
        assertTrue(diag[0] in 60..200, "diag.R balanced, got ${diag[0]}")
        assertTrue(diag[2] in 60..200, "diag.B balanced, got ${diag[2]}")

        // Left of centre, angle ~ pi, mirrored -> 0 -> red.
        val left = pixels.rgbaAt(14, 32)
        assertTrue(left[0] in 200..255, "left.R mostly red, got ${left[0]}")
        assertTrue(left[2] < 64, "left.B near zero, got ${left[2]}")
    }

    @Test
    fun `kDecal sweep gradient on a circle path punches transparent outside the sweep`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Constrain to [0, 90] degrees ; tScale = 4, tBias = 0. Stops
        // red -> blue. With kDecal :
        //   t_raw in [0, 1] -> sample (angle in [0, 90]).
        //   t_raw outside  [0, 1] -> transparent.
        // Inside the disk but outside the [0, 90] arc, fragments are
        // transparent -> white background visible through premul SrcOver.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkSweepGradient.Make(
            center = SkPoint(32f, 32f),
            startAngle = 0f,
            endAngle = 90f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kDecal,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Right of centre (angle ~ 0) -> inside sweep, t ~ 0 -> red.
        val right = pixels.rgbaAt(50, 32)
        assertTrue(right[0] in 200..255, "right.R mostly red, got ${right[0]}")
        assertTrue(right[2] < 64, "right.B near zero, got ${right[2]}")

        // Down-right at ~45 deg (angle in [0, 90]) -> sweep range,
        // t ~ 0.5 -> balanced.
        val diag = pixels.rgbaAt(46, 46)
        assertTrue(diag[0] in 60..200, "diag.R balanced, got ${diag[0]}")
        assertTrue(diag[2] in 60..200, "diag.B balanced, got ${diag[2]}")

        // Just below centre (angle ~ 90 deg) -> at end of sweep,
        // t ~ 1 -> blue.
        val down = pixels.rgbaAt(32, 50)
        assertTrue(down[0] < 80, "down.R near zero, got ${down[0]}")
        assertTrue(down[2] >= 180, "down.B mostly blue, got ${down[2]}")

        // Left of centre (angle ~ pi = 180 deg) -> t_raw ~ 2.0,
        // outside sweep -> kDecal transparent -> white background.
        val left = pixels.rgbaAt(14, 32)
        assertTrue(left[0] >= 230, "left.R near white, got ${left[0]}")
        assertTrue(left[1] >= 230, "left.G near white, got ${left[1]}")
        assertTrue(left[2] >= 230, "left.B near white, got ${left[2]}")

        // Up (angle ~ -pi/2 = 270 deg, but unitAngle wraps to ~ 0.75 ->
        // t_raw 3.0) -> outside sweep -> transparent.
        val up = pixels.rgbaAt(32, 14)
        assertTrue(up[0] >= 230, "up.R near white, got ${up[0]}")
        assertTrue(up[1] >= 230, "up.G near white, got ${up[1]}")
        assertTrue(up[2] >= 230, "up.B near white, got ${up[2]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
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
