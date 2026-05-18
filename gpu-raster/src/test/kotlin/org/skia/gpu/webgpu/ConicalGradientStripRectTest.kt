package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
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
 * G4.4.4 acceptance test -- conical (two-point) gradient fill of a rect
 * on GPU, **kStrip** sub-case (`r0 == r1`, `c0 != c1`).
 *
 * Fixture : `start=(16, 32)`, `end=(48, 32)`, `r0 = r1 = 0.5`. This
 * routes through `SkConicalGradient.Type.kStrip`. With `dCenter = 32`
 * and the `MapToUnitX` gradient matrix translating by `-start` then
 * scaling by `1/dCenter`, the per-pixel mapped coords are
 *   `x_mapped = (px + 0.5 - 16) / 32`
 *   `y_mapped = (py + 0.5 - 32) / 32`
 * and the kStrip formula yields
 *   `disc = stripP0 - y_mapped^2`            with `stripP0 = r0^2 = 0.25`
 *   `t    = x_mapped + sqrt(disc)`           iff `disc >= 0`
 * Pixels with `disc < 0` fall outside the strip and render as
 * premul-transparent black (matches CPU's `mask_2pt_conical_nan` ->
 * `apply_vector_mask` post-pass).
 *
 * NB : the GPU kStrip uniform value `stripP0` is sourced from
 * [SkConicalGradient.getStripP0], so both the CPU and the GPU stay in
 * lock-step with the same (un-scaled) `r0^2` cached on the shader.
 * Cross-test GMs that explicitly exercise the strip case are not in
 * scope -- this test class is the primary verification surface.
 */
class ConicalGradientStripRectTest {

    @Test
    fun `r0 equals r1 classifies as kStrip`() {
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kStrip, grad.getType())
        assertEquals(0.25f, grad.getStripP0(), 1e-6f)
    }

    @Test
    fun `kStrip kClamp red-to-blue interpolates along the strip axis on GPU`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kStrip, grad.getType())
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

        // On the strip axis (py = 32, y_mapped ~ 0.015) :
        //   disc ~ 0.25 -- in-strip ;
        //   t ~ (px+0.5-16)/32 + 0.5.
        // px=2 -> t ~ 0.078 -- mostly red.
        val nearStart = pixels.rgbaAt(2, 32)
        assertTrue(nearStart[0] >= 180, "nearStart.R mostly red, got ${nearStart[0]}")
        assertTrue(nearStart[2] < 96, "nearStart.B near zero, got ${nearStart[2]}")
        assertTrue(nearStart[3] >= 250, "nearStart.A near opaque, got ${nearStart[3]}")

        // px=16 -> t ~ 0.516 -- mid lerp.
        val mid = pixels.rgbaAt(16, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // px=32 -> t ~ 1.016 -- clamped to 1 -> blue.
        val nearEnd = pixels.rgbaAt(32, 32)
        assertTrue(nearEnd[0] < 96, "nearEnd.R near zero, got ${nearEnd[0]}")
        assertTrue(nearEnd[2] >= 180, "nearEnd.B mostly blue, got ${nearEnd[2]}")

        // Far past end (px=60) : t ~ 1.89 -- clamped -> blue.
        val beyondEnd = pixels.rgbaAt(60, 32)
        assertTrue(beyondEnd[0] < 96, "beyondEnd.R near zero, got ${beyondEnd[0]}")
        assertTrue(beyondEnd[2] >= 180, "beyondEnd.B mostly blue, got ${beyondEnd[2]}")

        // Off-axis far from the strip (py=58 -> y_mapped ~ 0.83, disc < 0)
        // : transparent -> background white untouched.
        val outsideStrip = pixels.rgbaAt(30, 58)
        assertNear(outsideStrip[0], 255, "outsideStrip.R", tol = 16)
        assertNear(outsideStrip[1], 255, "outsideStrip.G", tol = 16)
        assertNear(outsideStrip[2], 255, "outsideStrip.B", tol = 16)

        // Outside the rect : background white untouched.
        val outsideRect = pixels.rgbaAt(0, 0)
        assertNear(outsideRect[0], 255, "outsideRect.R", tol = 16)
        assertNear(outsideRect[1], 255, "outsideRect.G", tol = 16)
        assertNear(outsideRect[2], 255, "outsideRect.B", tol = 16)
    }

    @Test
    fun `kStrip kRepeat tiles t outside the unit range along the axis`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Same strip, kRepeat. px=60 -> t ~ 1.89 -> repeat -> 0.89 -> mostly blue.
        // px=0 -> t ~ 0.02 -> red.
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kRepeat,
        )!!
        assertEquals(SkConicalGradient.Type.kStrip, grad.getType())
        val paint = SkPaint().apply { shader = grad; isAntiAlias = false }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 2f, 62f, 62f), paint)
                device.flush()
            }
        }

        // px=2, py=32 -> t ~ 0.078 -> red.
        val nearStart = pixels.rgbaAt(2, 32)
        assertTrue(nearStart[0] >= 180, "nearStart.R mostly red, got ${nearStart[0]}")
        assertTrue(nearStart[2] < 96, "nearStart.B near zero, got ${nearStart[2]}")

        // px=60, py=32 -> t ~ 1.89 -> repeat 0.89 -> mostly blue.
        // (kClamp would also give blue here ; the discriminator is the
        // next period -- but the rect cuts off before then. Still a
        // sanity check that the kRepeat entry point is wired.)
        val period1 = pixels.rgbaAt(60, 32)
        assertTrue(period1[0] < 128, "period1.R smaller, got ${period1[0]}")
        assertTrue(period1[2] >= 128, "period1.B blue-leaning, got ${period1[2]}")
    }

    @Test
    fun `kStrip kDecal punches transparent outside the unit range along the axis`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kDecal,
        )!!
        assertEquals(SkConicalGradient.Type.kStrip, grad.getType())
        val paint = SkPaint().apply { shader = grad; isAntiAlias = false }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(2f, 2f, 62f, 62f), paint)
                device.flush()
            }
        }

        // px=16, py=32 -> t ~ 0.516 -- inside [0, 1] -- balanced lerp.
        val mid = pixels.rgbaAt(16, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // px=60, py=32 -> t ~ 1.89 -- outside [0, 1] -- transparent ->
        // background white untouched.
        val pastEnd = pixels.rgbaAt(60, 32)
        assertNear(pastEnd[0], 255, "pastEnd.R", tol = 16)
        assertNear(pastEnd[1], 255, "pastEnd.G", tol = 16)
        assertNear(pastEnd[2], 255, "pastEnd.B", tol = 16)

        // py=58 -> disc < 0 -> in_strip = 0 -- transparent.
        val outsideStrip = pixels.rgbaAt(30, 58)
        assertNear(outsideStrip[0], 255, "outsideStrip.R", tol = 16)
        assertNear(outsideStrip[1], 255, "outsideStrip.G", tol = 16)
        assertNear(outsideStrip[2], 255, "outsideStrip.B", tol = 16)
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
