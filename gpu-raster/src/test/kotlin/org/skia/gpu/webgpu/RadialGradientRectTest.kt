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

    // G4.2.1 -- kRepeat / kMirror / kDecal exercise one new fragment
    // entry point each. Pixel-center is at (x + 0.5, y + 0.5), so the
    // distance values quoted below add a ~0.5 px offset.

    @Test
    fun `kRepeat tiles the radial gradient outside the first ring`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Centre (32, 32), radius = 10 px : 3 full periods fit between
        // the centre and the rect edge (x = 62 -> distance 30 -> 3
        // periods). Red at the start of each period (t = 0) ; blue at
        // the end (t ~= 1) ; back to red at the next ring boundary.
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Centre (pixel-center 32.5, 32.5 -- distance ~0.707, t = 0.07)
        // : still mostly red.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")

        // End of period 0 along +x : x = 41, pixel-center 41.5 ->
        // distance 9.5 -> t = 0.95, mostly blue.
        val p0End = pixels.rgbaAt(41, 32)
        assertTrue(p0End[0] < 64, "p0End.R near zero, got ${p0End[0]}")
        assertTrue(p0End[2] >= 200, "p0End.B mostly blue, got ${p0End[2]}")

        // Just past the period 0 / 1 boundary along +x : x = 43, pixel
        // center 43.5 -> distance 11.5 -> t_raw = 1.15 -> t = 0.15,
        // back to mostly red. This is the kRepeat-vs-kMirror discriminator
        // : with kMirror the same x would be mostly blue (t = 0.85).
        val p1Start = pixels.rgbaAt(43, 32)
        assertTrue(p1Start[0] in 180..255, "p1Start.R mostly red, got ${p1Start[0]}")
        assertTrue(p1Start[2] < 80, "p1Start.B mostly zero, got ${p1Start[2]}")
    }

    @Test
    fun `kMirror reflects the radial gradient at each ring boundary`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Same setup as kRepeat above : centre (32, 32), radius = 10 px.
        // Mirror : red->blue in ring 0, blue->red in ring 1, etc. Just
        // past the ring 0/1 boundary the value reflects -- t_raw = 1.15
        // -> mirror -> t = 1 - 0.15 = 0.85, still mostly blue.
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Centre : still mostly red.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")

        // End of ring 0 (x = 41, distance ~9.5, t = 0.95) : mostly blue.
        val r0End = pixels.rgbaAt(41, 32)
        assertTrue(r0End[0] < 64, "r0End.R near zero, got ${r0End[0]}")
        assertTrue(r0End[2] >= 200, "r0End.B mostly blue, got ${r0End[2]}")

        // Just past ring 0/1 boundary along +x : x = 43, distance ~11.5,
        // t_raw = 1.15 -> mirror -> t = 0.85, still mostly blue.
        // (kRepeat would give mostly red here -- this is the
        // kMirror-vs-kRepeat discriminator.)
        val r1Start = pixels.rgbaAt(43, 32)
        assertTrue(r1Start[0] < 80, "r1Start.R near zero, got ${r1Start[0]}")
        assertTrue(r1Start[2] in 180..255, "r1Start.B mostly blue, got ${r1Start[2]}")
    }

    @Test
    fun `kDecal punches transparent outside the radial gradient`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Centre (32, 32), radius = 10 px. Rect is much larger : x and
        // y in [2, 62]. Pixels with distance > radius are outside the
        // gradient line and must be fully transparent (premul (0, 0, 0,
        // 0)) -- compositing over white leaves white untouched.
        val grad = SkRadialGradient.Make(
            center = SkPoint(32f, 32f),
            radius = 10f,
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

        // Inside the gradient : centre is mostly red.
        val center = pixels.rgbaAt(32, 32)
        assertTrue(center[0] in 200..255, "center.R mostly red, got ${center[0]}")
        assertTrue(center[2] < 64, "center.B near zero, got ${center[2]}")

        // Inside near radius : end of ring 0 along +x (x = 41,
        // distance ~9.5, t = 0.95) : mostly blue.
        val edge = pixels.rgbaAt(41, 32)
        assertTrue(edge[0] < 64, "edge.R near zero, got ${edge[0]}")
        assertTrue(edge[2] >= 200, "edge.B mostly blue, got ${edge[2]}")

        // Outside the radius along +x : x = 50, distance ~17.5 -> t_raw
        // > 1 -> transparent -> background white untouched.
        val outside = pixels.rgbaAt(50, 32)
        assertNear(outside[0], 255, "outside.R", tol = 16)
        assertNear(outside[1], 255, "outside.G", tol = 16)
        assertNear(outside[2], 255, "outside.B", tol = 16)

        // Outside along -y, +x diagonal : (45, 18), distance ~19 ->
        // transparent.
        val outsideDiag = pixels.rgbaAt(45, 18)
        assertNear(outsideDiag[0], 255, "outsideDiag.R", tol = 16)
        assertNear(outsideDiag[1], 255, "outsideDiag.G", tol = 16)
        assertNear(outsideDiag[2], 255, "outsideDiag.B", tol = 16)
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
