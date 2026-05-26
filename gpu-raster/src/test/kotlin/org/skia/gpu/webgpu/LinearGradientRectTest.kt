package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgsl
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkRect

/**
 * G4.1 acceptance test — linear gradient (kClamp) fill of a rect on GPU.
 *
 * Routed through SkCanvas.drawRect : because `paint.shader` is non-null,
 * the canvas falls through to drawPath, which hits the G4.1 fast path
 * in `SkWebGpuDevice.drawPath` (path.isRect + axis-aligned CTM gate).
 *
 * Verifies the gradient interpolates correctly across the rect by
 * sampling a few key pixels of a horizontal red->blue gradient covering
 * the full rect bounds : red at the start, blue at the end, ~purple
 * (50/50 lerp) at the midpoint.
 */
class LinearGradientRectTest {

    // G4.1.1 -- the kRepeat / kMirror / kDecal tests below exercise one
    // shader entry point each. Pixel-center is at (x + 0.5, y + 0.5),
    // so the t_raw values quoted in the assertions are slightly offset
    // from "x - start" / "end - start" : column x has center at x + 0.5.

    @Test
    fun `horizontal red-to-blue gradient interpolates across the rect`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Horizontal linear gradient over a 60-px-wide rect at x in [2, 62].
        // start at the left edge (red), end at the right edge (blue) so
        // the gradient line maps 1:1 with the rect's horizontal extent.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(2f, 32f),
            p1 = SkPoint(62f, 32f),
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
                SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 10f, 62f, 50f), paint)
                val out = device.flush()
                assertNull(device.generatedLinearGradientFallbackReasonForDiagnostics())
                out
            }
        }

        // Sample column 2 (t = 0) : expect mostly red.
        val left = pixels.rgbaAt(2, 30)
        assertNear(left[0], 255, "left.R", tol = 4)
        assertNear(left[2], 0, "left.B", tol = 8)
        assertTrue(left[3] >= 250, "left.A near opaque, got ${left[3]}")

        // Sample column 61 (t = 1) : expect mostly blue.
        val right = pixels.rgbaAt(61, 30)
        assertNear(right[0], 0, "right.R", tol = 8)
        assertNear(right[2], 255, "right.B", tol = 4)
        assertTrue(right[3] >= 250, "right.A near opaque, got ${right[3]}")

        // Sample column 32 (t = 0.5) : expect ~purple, balanced R and B.
        // The gradient lerps in premul space ; with both endpoints fully
        // opaque (alpha = 1) the lerp degenerates to straight RGB lerp,
        // so mid-point R == mid-point B == 127. The G6.1 present pass
        // applies an sRGB -> Rec.2020 colorspace transform that shifts
        // the channels, so we leave a generous tolerance on the
        // individual channel values.
        val mid = pixels.rgbaAt(32, 30)
        assertTrue(mid[0] in 80..200, "mid.R in [80, 200], got ${mid[0]}")
        assertTrue(mid[2] in 80..200, "mid.B in [80, 200], got ${mid[2]}")
        // The mid pixel should NOT look anything like the endpoints.
        assertTrue(mid[0] < 250, "mid is not pure red (R=${mid[0]})")
        assertTrue(mid[2] < 250, "mid is not pure blue (B=${mid[2]})")
        assertTrue(mid[3] >= 250, "mid.A near opaque, got ${mid[3]}")

        // Outside the rect : white background untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertNear(outside[0], 255, "outside.R", tol = 16)
        assertNear(outside[1], 255, "outside.G", tol = 16)
        assertNear(outside[2], 255, "outside.B", tol = 16)
    }

    @Test
    fun `kClamp gradient falls back to handwritten shader when generated path is disabled`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val previous = System.getProperty(GeneratedLinearGradientWgsl.FEATURE_FLAG)
        System.setProperty(GeneratedLinearGradientWgsl.FEATURE_FLAG, "false")
        try {
            val grad = SkLinearGradient.Make(
                p0 = SkPoint(2f, 32f),
                p1 = SkPoint(62f, 32f),
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
                    SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 10f, 62f, 50f), paint)
                    val out = device.flush()
                    assertEquals(
                        "generated linear gradient disabled via -D${GeneratedLinearGradientWgsl.FEATURE_FLAG}=false",
                        device.generatedLinearGradientFallbackReasonForDiagnostics(),
                    )
                    out
                }
            }

            val mid = pixels.rgbaAt(32, 30)
            assertTrue(mid[0] in 80..200, "mid.R balanced, got ${mid[0]}")
            assertTrue(mid[2] in 80..200, "mid.B balanced, got ${mid[2]}")
        } finally {
            if (previous == null) {
                System.clearProperty(GeneratedLinearGradientWgsl.FEATURE_FLAG)
            } else {
                System.setProperty(GeneratedLinearGradientWgsl.FEATURE_FLAG, previous)
            }
        }
    }

    @Test
    fun `kRepeat tiles the gradient across the rect`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span = 20 px (x in [10, 30]). Rect spans x in [10, 60]
        // (the surface is 64 px wide), so we cover 2.5 periods of the
        // gradient. Red at the start of each period (t = 0) ; blue at
        // the end (t ~= 1).
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(10f, 32f),
            p1 = SkPoint(30f, 32f),
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
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 60f, 50f), paint)
                val out = device.flush()
                assertEquals(
                    "generated linear gradient currently supports only kClamp tile mode",
                    device.generatedLinearGradientFallbackReasonForDiagnostics(),
                )
                out
            }
        }

        // Period 0 : red at x = 10, blue at x = 29.
        val p0Start = pixels.rgbaAt(10, 30)
        assertNear(p0Start[0], 255, "p0Start.R", tol = 6)
        assertNear(p0Start[2], 0, "p0Start.B", tol = 12)
        val p0End = pixels.rgbaAt(29, 30)
        assertNear(p0End[0], 0, "p0End.R", tol = 12)
        assertNear(p0End[2], 255, "p0End.B", tol = 6)

        // Period 1 starts at x = 30 : back to red, NOT blue (this is the
        // kRepeat-vs-kMirror discriminator). Sample at x = 31 to be safely
        // past the wrap boundary (pixel-center 31.5 -> t_raw = 1.075 ->
        // t = 0.075, mostly red). The G6.1 sRGB -> Rec.2020 transform
        // pulls R down ~20 from 255 at the edge, hence the generous tol.
        val p1Start = pixels.rgbaAt(31, 30)
        assertTrue(p1Start[0] in 200..255, "p1Start.R mostly red, got ${p1Start[0]}")
        assertTrue(p1Start[2] < 64, "p1Start.B mostly zero, got ${p1Start[2]}")

        // Period 2 starts at x = 50 : red again.
        val p2Start = pixels.rgbaAt(51, 30)
        assertTrue(p2Start[0] in 200..255, "p2Start.R mostly red, got ${p2Start[0]}")
        assertTrue(p2Start[2] < 64, "p2Start.B mostly zero, got ${p2Start[2]}")
        // ...and halfway through period 2 (x = 59, pixel-center 59.5,
        // t_raw = 2.475 -> t = 0.475) we're back near the mid lerp.
        val p2Mid = pixels.rgbaAt(59, 30)
        assertTrue(p2Mid[0] in 80..200, "p2Mid.R balanced, got ${p2Mid[0]}")
        assertTrue(p2Mid[2] in 80..200, "p2Mid.B balanced, got ${p2Mid[2]}")
    }

    @Test
    fun `kMirror reflects the gradient across the rect`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span = 20 px (x in [10, 30]). Rect spans x in [10, 50]
        // (2 periods). Mirror : red->blue in period 0, blue->red in
        // period 1 (the reflected half). At x = 30 we should be blue
        // (the reflected boundary), at x = 49 red.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(10f, 32f),
            p1 = SkPoint(30f, 32f),
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
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 50f, 50f), paint)
                device.flush()
            }
        }

        // Period 0 : red->blue (same as kClamp/kRepeat inside [0, 1]).
        val p0Start = pixels.rgbaAt(10, 30)
        assertNear(p0Start[0], 255, "p0Start.R", tol = 6)
        assertNear(p0Start[2], 0, "p0Start.B", tol = 12)
        val p0End = pixels.rgbaAt(29, 30)
        assertNear(p0End[0], 0, "p0End.R", tol = 12)
        assertNear(p0End[2], 255, "p0End.B", tol = 6)

        // Period 1 (reflected) : at x = 31 (just past the boundary)
        // t_raw = 1.075 -> mirror -> t = 1 - 0.075 = 0.925, still mostly
        // blue. This is the key kMirror-vs-kRepeat discriminator : with
        // kRepeat the same x would be mostly red. The G6.1 sRGB ->
        // Rec.2020 transform tints the near-pure-blue edge by up to ~20,
        // hence the band assertions instead of tight tolerances.
        val p1Start = pixels.rgbaAt(31, 30)
        assertTrue(p1Start[0] < 64, "p1Start.R near zero, got ${p1Start[0]}")
        assertTrue(p1Start[2] in 200..255, "p1Start.B mostly blue, got ${p1Start[2]}")

        // End of period 1 (= rect end at x = 49) : reflected back to red.
        val p1End = pixels.rgbaAt(49, 30)
        assertTrue(p1End[0] in 200..255, "p1End.R mostly red, got ${p1End[0]}")
        assertTrue(p1End[2] < 64, "p1End.B near zero, got ${p1End[2]}")
    }

    @Test
    fun `kDecal punches transparent outside the gradient line`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Gradient span x in [20, 40]. Rect is wider, x in [10, 50],
        // so the strip x in [10, 20) and x in (40, 50] is outside the
        // gradient line and must be fully transparent (premul (0, 0, 0,
        // 0)) -- which composites over the white background to leave
        // white untouched.
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(20f, 32f),
            p1 = SkPoint(40f, 32f),
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
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 50f, 50f), paint)
                device.flush()
            }
        }

        // Outside the gradient on the left : background white, alpha 255.
        val leftOutside = pixels.rgbaAt(12, 30)
        assertNear(leftOutside[0], 255, "leftOutside.R", tol = 16)
        assertNear(leftOutside[1], 255, "leftOutside.G", tol = 16)
        assertNear(leftOutside[2], 255, "leftOutside.B", tol = 16)

        // Inside the gradient : red at x = 20, blue at x = 39.
        val gradStart = pixels.rgbaAt(20, 30)
        assertNear(gradStart[0], 255, "gradStart.R", tol = 6)
        assertNear(gradStart[2], 0, "gradStart.B", tol = 12)
        val gradEnd = pixels.rgbaAt(39, 30)
        assertNear(gradEnd[0], 0, "gradEnd.R", tol = 12)
        assertNear(gradEnd[2], 255, "gradEnd.B", tol = 6)

        // Outside the gradient on the right : background white again.
        val rightOutside = pixels.rgbaAt(45, 30)
        assertNear(rightOutside[0], 255, "rightOutside.R", tol = 16)
        assertNear(rightOutside[1], 255, "rightOutside.G", tol = 16)
        assertNear(rightOutside[2], 255, "rightOutside.B", tol = 16)
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
