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
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode

/**
 * G4.2.2 acceptance test -- radial gradient fill of a non-rect path
 * (circle) on GPU, exercising all 4 SkTileMode values. Mirrors
 * [RadialGradientRectTest] but the geometry routes through the AA
 * stencil-and-cover radial-gradient pipeline instead of the rect-only
 * fast path. Mirrors [LinearGradientPathTest] in shape -- one test per
 * tile mode, sampling on-axis at known distances from the gradient
 * centre.
 *
 * The path is a circle large enough to cover most of the surface so the
 * sampled columns sit well inside the AA boundary (coverage = 1.0 at the
 * interior pixels, AA blend only along the perimeter).
 */
class RadialGradientPathTest {

    @Test
    fun `kClamp radial gradient on a circle path interpolates along distance from center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Disk centred at (32, 32) radius 28 covers most of the 64x64
        // surface. Gradient centre at the same point with radius 20 -> the
        // last stop (blue) is reached well before the disk perimeter, so
        // beyond ~20 px out the kClamp tile mode pins to blue.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 20f,
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

        // Centre pixel : t ~ 0 -> mostly red.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")
        assertTrue(center[3] >= 240, "center.A near opaque, got ${center[3]}")

        // ~halfway along +x : x = 42, t ~ 0.475 -> balanced.
        val mid = pixels.rgbaAt(42, 32)
        assertTrue(mid[0] in 60..200, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..200, "mid.B balanced, got ${mid[2]}")

        // Near the gradient radius edge but inside the disk : x = 52,
        // distance ~ 19.5 -> t ~ 0.975 -> mostly blue.
        val nearEdge = pixels.rgbaAt(52, 32)
        assertTrue(nearEdge[0] < 64, "nearEdge.R near zero, got ${nearEdge[0]}")
        assertTrue(nearEdge[2] >= 200, "nearEdge.B mostly blue, got ${nearEdge[2]}")

        // Past the gradient radius but still inside the disk : x = 58,
        // distance ~ 25.5, kClamp -> blue (last stop).
        val pastEdge = pixels.rgbaAt(58, 32)
        assertTrue(pastEdge[0] < 64, "pastEdge.R near zero, got ${pastEdge[0]}")
        assertTrue(pastEdge[2] >= 200, "pastEdge.B mostly blue, got ${pastEdge[2]}")

        // Outside the disk -> white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertTrue(outside[0] >= 230, "outside.R near white, got ${outside[0]}")
        assertTrue(outside[1] >= 230, "outside.G near white, got ${outside[1]}")
        assertTrue(outside[2] >= 230, "outside.B near white, got ${outside[2]}")
    }

    @Test
    fun `kRepeat radial gradient on a circle path tiles outward from the center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient radius = 10 px. Disk radius = 28 px. Sample points
        // chosen so distance / radius produces predictable t_raw -- the
        // kRepeat mode wraps via `t = t_raw - floor(t_raw)`.
        //
        //   x = 33 -> distance ~ 1 -> t_raw ~ 0.1 -> t ~ 0.1 -> red.
        //   x = 53 -> distance ~ 21 -> t_raw ~ 2.1 -> t ~ 0.1 -> red.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Period 0 near start (x = 33, t_raw ~ 0.1) : red.
        val p0Start = pixels.rgbaAt(33, 32)
        assertTrue(p0Start[0] in 200..255, "p0Start.R mostly red, got ${p0Start[0]}")
        assertTrue(p0Start[2] < 64, "p0Start.B near zero, got ${p0Start[2]}")

        // Period 2 near start (x = 53, t_raw ~ 2.1) : red again.
        val p2Start = pixels.rgbaAt(53, 32)
        assertTrue(p2Start[0] in 200..255, "p2Start.R mostly red, got ${p2Start[0]}")
        assertTrue(p2Start[2] < 64, "p2Start.B near zero, got ${p2Start[2]}")
    }

    @Test
    fun `kMirror radial gradient on a circle path reflects outward from the center`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient radius = 10 px. Disk radius = 28 px.
        //   x = 43 -> distance ~ 11 -> t_raw ~ 1.1 -> mirror -> t ~ 0.9
        //             (= 2 - 1.1) -> mostly blue.
        //   x = 52 -> distance ~ 20 -> t_raw ~ 2.0 -> mirror -> t ~ 0.0
        //             -> red.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Mirror period 1 (t_raw ~ 1.1, mirror -> t ~ 0.9) : blue.
        val p1Start = pixels.rgbaAt(43, 32)
        assertTrue(p1Start[0] < 64, "p1Start.R near zero, got ${p1Start[0]}")
        assertTrue(p1Start[2] >= 200, "p1Start.B mostly blue, got ${p1Start[2]}")

        // End of mirror period 1 (x = 52, t_raw ~ 2.0, mirror -> t ~ 0) : red.
        val p1End = pixels.rgbaAt(52, 32)
        assertTrue(p1End[0] in 200..255, "p1End.R mostly red, got ${p1End[0]}")
        assertTrue(p1End[2] < 64, "p1End.B near zero, got ${p1End[2]}")
    }

    @Test
    fun `kDecal radial gradient on a circle path punches transparent outside the gradient disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient radius = 10 px. Disk radius = 28 px. Pixels inside the
        // disk but outside the gradient disk (distance > 10) are transparent
        // (premul (0,0,0,0)) -- they composite over the white background to
        // leave white visible.
        val path = SkPath.Circle(32f, 32f, 28f, SkPathDirection.kCW)
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Inside the gradient disk near the centre (x = 33, t ~ 0.1) : red.
        val center = pixels.rgbaAt(33, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")

        // Inside the gradient disk near the radius (x = 41, t ~ 0.85) : blue.
        val edge = pixels.rgbaAt(41, 32)
        assertTrue(edge[0] < 80, "edge.R near zero, got ${edge[0]}")
        assertTrue(edge[2] >= 180, "edge.B mostly blue, got ${edge[2]}")

        // Outside the gradient disk but inside the path disk (x = 50,
        // distance ~ 18 > 10) -> transparent -> white background visible.
        val outsideGradient = pixels.rgbaAt(50, 32)
        assertTrue(
            outsideGradient[0] >= 230,
            "outsideGradient.R near white, got ${outsideGradient[0]}",
        )
        assertTrue(
            outsideGradient[1] >= 230,
            "outsideGradient.G near white, got ${outsideGradient[1]}",
        )
        assertTrue(
            outsideGradient[2] >= 230,
            "outsideGradient.B near white, got ${outsideGradient[2]}",
        )

        // Outside the path disk -> white background untouched.
        val outsidePath = pixels.rgbaAt(0, 0)
        assertTrue(outsidePath[0] >= 230, "outsidePath.R near white, got ${outsidePath[0]}")
        assertTrue(outsidePath[1] >= 230, "outsidePath.G near white, got ${outsidePath[1]}")
        assertTrue(outsidePath[2] >= 230, "outsidePath.B near white, got ${outsidePath[2]}")
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
