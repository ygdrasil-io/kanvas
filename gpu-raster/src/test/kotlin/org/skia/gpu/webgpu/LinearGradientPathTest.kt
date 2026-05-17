package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkRect

/**
 * G4.1.2 acceptance test -- linear gradient fill of a non-rect path
 * (circle) on GPU, exercising all 4 SkTileMode values. Mirrors
 * [LinearGradientRectTest] but the geometry routes through the AA
 * stencil-and-cover gradient pipeline instead of the rect-only fast
 * path.
 *
 * The path is a circle large enough to cover most of the surface so
 * the sampled columns sit well inside the AA boundary (coverage = 1.0
 * at the interior pixels, AA blend only along the perimeter).
 */
class LinearGradientPathTest {

    @Test
    fun `kClamp linear gradient on a circle path interpolates across the disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Circle centred at (32, 32) radius 28 -> covers most of the 64x64
        // surface, leaving a few pixels of background visible at the
        // corners. Horizontal gradient over x in [4, 60] (the circle's
        // horizontal extent) so the gradient line matches the disk's
        // diameter ; on-axis pixels at y = 32 land deep inside the disk.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(4f, 32f),
            p1 = SkPoint(60f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
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

        // Sample column 6 (well inside the disk, t ~ 0.04) -> mostly red.
        val left = pixels.rgbaAt(6, 32)
        val mid32 = pixels.rgbaAt(32, 32)
        val right = pixels.rgbaAt(57, 32)
        assertTrue(left[0] in 200..255, "left.R mostly red, got ${left[0]}")
        assertTrue(left[2] < 64, "left.B near zero, got ${left[2]}")
        assertTrue(left[3] >= 240, "left.A near opaque, got ${left[3]}")

        // Sample column 57 (deep inside the disk, t ~ 0.96) -> mostly blue.
        assertTrue(right[0] < 64, "right.R near zero, got ${right[0]}")
        assertTrue(right[2] in 200..255, "right.B mostly blue, got ${right[2]}")
        assertTrue(right[3] >= 240, "right.A near opaque, got ${right[3]}")

        // Mid column -> roughly purple, no channel dominates.
        assertTrue(mid32[0] in 60..200, "mid.R balanced, got ${mid32[0]}")
        assertTrue(mid32[2] in 60..200, "mid.B balanced, got ${mid32[2]}")
        assertTrue(mid32[3] >= 240, "mid.A near opaque, got ${mid32[3]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
    }

    @Test
    fun `kRepeat linear gradient on a circle path tiles inside the disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span = 20 px (x in [10, 30]). Circle covers x in [4, 60].
        // The disk interior crosses ~2.5 periods of the gradient. Sample
        // at x = 11 (period 0, near red) and x = 51 (period 2, near red).
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(10f, 32f),
            p1 = SkPoint(30f, 32f),
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

        // Period 0 start (x = 11, t ~ 0.075) : red.
        val p0Start = pixels.rgbaAt(11, 32)
        assertTrue(p0Start[0] in 200..255, "p0Start.R mostly red, got ${p0Start[0]}")
        assertTrue(p0Start[2] < 64, "p0Start.B near zero, got ${p0Start[2]}")

        // Period 2 start (x = 51, t_raw = 2.075 -> t = 0.075) : red again.
        val p2Start = pixels.rgbaAt(51, 32)
        assertTrue(p2Start[0] in 200..255, "p2Start.R mostly red, got ${p2Start[0]}")
        assertTrue(p2Start[2] < 64, "p2Start.B near zero, got ${p2Start[2]}")
    }

    @Test
    fun `kMirror linear gradient on a circle path reflects inside the disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span = 20 px (x in [10, 30]). At x = 31 (t_raw = 1.075,
        // mirror -> t = 0.925) we expect mostly blue, not red (which is
        // what kRepeat would produce). At x = 49 (t_raw = 1.975, mirror
        // -> t = 0.025) we expect red.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(10f, 32f),
            p1 = SkPoint(30f, 32f),
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

        // Period 1 (reflected) : x = 31 -> mostly blue.
        val p1Start = pixels.rgbaAt(31, 32)
        assertTrue(p1Start[0] < 64, "p1Start.R near zero, got ${p1Start[0]}")
        assertTrue(p1Start[2] in 200..255, "p1Start.B mostly blue, got ${p1Start[2]}")

        // End of period 1 (x = 49) : red.
        val p1End = pixels.rgbaAt(49, 32)
        assertTrue(p1End[0] in 200..255, "p1End.R mostly red, got ${p1End[0]}")
        assertTrue(p1End[2] < 64, "p1End.B near zero, got ${p1End[2]}")
    }

    @Test
    fun `kDecal linear gradient on a circle path punches transparent outside the gradient line`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span x in [20, 40] ; circle covers x in [4, 60]. The
        // inside-disk strip x in [4, 20) and x in (40, 60] is outside the
        // gradient line, so the fragment shader returns premul (0, 0, 0, 0)
        // -- composites over white background to leave white visible.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(20f, 32f),
            p1 = SkPoint(40f, 32f),
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

        // Inside the disk but left of the gradient line (x = 10) -> white
        // (background punches through the transparent decal output).
        val leftOutside = pixels.rgbaAt(10, 32)
        assertTrue(leftOutside[0] >= 230, "leftOutside.R near white, got ${leftOutside[0]}")
        assertTrue(leftOutside[1] >= 230, "leftOutside.G near white, got ${leftOutside[1]}")
        assertTrue(leftOutside[2] >= 230, "leftOutside.B near white, got ${leftOutside[2]}")

        // Inside the gradient (x = 21, t ~ 0.075) -> red.
        val gradStart = pixels.rgbaAt(21, 32)
        assertTrue(gradStart[0] in 200..255, "gradStart.R mostly red, got ${gradStart[0]}")
        assertTrue(gradStart[2] < 64, "gradStart.B near zero, got ${gradStart[2]}")

        // Inside the gradient near the end (x = 39, t ~ 0.975) -> blue.
        val gradEnd = pixels.rgbaAt(39, 32)
        assertTrue(gradEnd[0] < 64, "gradEnd.R near zero, got ${gradEnd[0]}")
        assertTrue(gradEnd[2] in 200..255, "gradEnd.B mostly blue, got ${gradEnd[2]}")

        // Inside the disk but right of the gradient line (x = 50) -> white.
        val rightOutside = pixels.rgbaAt(50, 32)
        assertTrue(rightOutside[0] >= 230, "rightOutside.R near white, got ${rightOutside[0]}")
        assertTrue(rightOutside[1] >= 230, "rightOutside.G near white, got ${rightOutside[1]}")
        assertTrue(rightOutside[2] >= 230, "rightOutside.B near white, got ${rightOutside[2]}")
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
