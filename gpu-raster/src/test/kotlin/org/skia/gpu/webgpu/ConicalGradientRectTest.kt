package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode

/**
 * G4.4 acceptance test -- conical (two-point) gradient (kClamp) fill of
 * a rect on GPU, kRadial sub-case (concentric circles `c0 == c1`).
 *
 * Routed through SkCanvas.drawRect : because `paint.shader` is non-null,
 * the canvas falls through to drawPath, which hits the G4.4 fast path
 * in `SkWebGpuDevice.drawPath` (path.isRect + axis-aligned CTM gate +
 * kClamp tile mode + kRadial sub-case).
 *
 * Verifies the gradient interpolates between the two concentric circles
 * `(c, r0)` and `(c, r1)` per the formula
 *   t = (length(p - c) - r0) / (r1 - r0)
 * with clamp tile mode : pixels at distance < r0 sample the first stop
 * (red), pixels at distance > r1 the last stop (blue), in between a lerp.
 */
class ConicalGradientRectTest {

    @Test
    fun `concentric red-to-blue conical interpolates between inner and outer radii`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Centre (32, 32), startRadius = 5, endRadius = 20.
        // - Pixels within 5 px of centre : clamped to t = 0 -> red.
        // - Pixels at distance 20 px : t = 1 -> blue.
        // - Halfway between (distance 12.5) : t = 0.5 -> balanced.
        // - Beyond 20 px : clamped to t = 1 -> blue.
        val grad = SkConicalGradient.Make(
            start = SkPoint(32f, 32f), startRadius = 5f,
            end = SkPoint(32f, 32f), endRadius = 20f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        // Confirm we exercise the kRadial sub-case the GPU pipeline supports.
        assertTrue(
            grad.getType() == SkConicalGradient.Type.kRadial,
            "test fixture must be the kRadial sub-case, got ${grad.getType()}",
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

        // Inside the inner radius (pixel-center (32.5, 32.5), distance
        // ~0.7) : clamped to t = 0 -> red. The G6.1 sRGB -> Rec.2020
        // present pass pulls pure red from 255 to ~246, hence the band.
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] in 200..255, "centre.R mostly red, got ${centre[0]}")
        assertTrue(centre[2] < 64, "centre.B near zero, got ${centre[2]}")
        assertTrue(centre[3] >= 250, "centre.A near opaque, got ${centre[3]}")

        // Just inside the inner radius (x = 35, distance ~3) : clamped
        // to t = 0 -> still red.
        val nearCentre = pixels.rgbaAt(35, 32)
        assertTrue(nearCentre[0] in 200..255, "nearCentre.R mostly red, got ${nearCentre[0]}")
        assertTrue(nearCentre[2] < 64, "nearCentre.B near zero, got ${nearCentre[2]}")

        // Right edge of the gradient (x = 52, distance ~19.5, t ~ 0.97)
        // : mostly blue.
        val edgeRight = pixels.rgbaAt(52, 32)
        assertTrue(edgeRight[0] < 64, "edgeRight.R mostly zero, got ${edgeRight[0]}")
        assertTrue(edgeRight[2] >= 200, "edgeRight.B mostly blue, got ${edgeRight[2]}")

        // Halfway along +x : x = 44, distance ~12, t ~ 0.47 -> balanced.
        // The present-pass colorspace transform can shift channels by a
        // few dozen LSBs in the mid lerp region, hence the wide band.
        val mid = pixels.rgbaAt(44, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // Beyond the outer radius : x = 60, distance ~28 -> clamped to
        // t = 1 -> blue. (Same band as edgeRight.)
        val beyond = pixels.rgbaAt(60, 32)
        assertTrue(beyond[0] < 64, "beyond.R mostly zero, got ${beyond[0]}")
        assertTrue(beyond[2] >= 200, "beyond.B mostly blue, got ${beyond[2]}")

        // Symmetry check : pixel at (12, 32) is also ~20 px out -> blue.
        // (Concentric kRadial conical is rotationally symmetric.)
        val edgeLeft = pixels.rgbaAt(12, 32)
        assertTrue(edgeLeft[0] < 64, "edgeLeft.R mostly zero, got ${edgeLeft[0]}")
        assertTrue(edgeLeft[2] >= 200, "edgeLeft.B mostly blue, got ${edgeLeft[2]}")

        // Outside the rect : background white untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertNear(outside[0], 255, "outside.R", tol = 16)
        assertNear(outside[1], 255, "outside.G", tol = 16)
        assertNear(outside[2], 255, "outside.B", tol = 16)
    }

    @Test
    fun `shallow grey-to-grey conical matches the cross-test fixture`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Mirror of ShallowGradientConicalGM at small size : centre
        // (32, 32), inner radius = 1, outer radius = 32 ; near-identical
        // greys. Verifies the kRadial dispatch produces something close
        // to the reference shape (a near-uniform grey fill ; the
        // gradient between 0x55 and 0x44 is barely visible).
        val grad = SkConicalGradient.Make(
            start = SkPoint(32f, 32f), startRadius = 1f,
            end = SkPoint(32f, 32f), endRadius = 32f,
            colors = intArrayOf(0xFF555555.toInt(), 0xFF444444.toInt()),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
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

        // Centre : closer to 0x55 (85) -> RGB ~85, A 255. The present
        // pass colorspace transform shifts grey by a small amount.
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] in 50..100, "centre.R near 0x55, got ${centre[0]}")
        assertTrue(centre[1] in 50..100, "centre.G near 0x55, got ${centre[1]}")
        assertTrue(centre[2] in 50..100, "centre.B near 0x55, got ${centre[2]}")
        assertTrue(centre[3] >= 250, "centre.A near opaque, got ${centre[3]}")

        // Edge : closer to 0x44 (68).
        val edge = pixels.rgbaAt(60, 32)
        assertTrue(edge[0] in 40..90, "edge.R near 0x44, got ${edge[0]}")
        assertTrue(edge[1] in 40..90, "edge.G near 0x44, got ${edge[1]}")
        assertTrue(edge[2] in 40..90, "edge.B near 0x44, got ${edge[2]}")
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
