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
                device.flush()
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
