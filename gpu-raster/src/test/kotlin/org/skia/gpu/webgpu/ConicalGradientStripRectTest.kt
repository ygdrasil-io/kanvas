package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkCanvas
import org.skia.core.SkColorSpaceXformSteps
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkColorSpace
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
 *   `disc = stripP0 - y_mapped^2`            with
 *     `stripP0 = (r0 / centerX1)^2 = (0.5 / 32)^2 = 0.000244140625`
 *   `t    = x_mapped + sqrt(disc)`           iff `disc >= 0`
 * The half-width of the strip in *device* pixels is therefore
 *   `sqrt(stripP0) * centerX1 = r0 = 0.5` -- i.e. the strip is 1
 * device-pixel tall, centred on `py = 32`. Pixels with `disc < 0` fall
 * outside the strip and render as premul-transparent black (matches
 * CPU's `mask_2pt_conical_nan` -> `apply_vector_mask` post-pass).
 *
 * NB : the GPU kStrip uniform value `stripP0` is sourced from
 * [SkConicalGradient.getStripP0], so both the CPU and the GPU stay in
 * lock-step with upstream Skia's `scaledR0 = fRadius1 / getCenterX1()`
 * (see SkConicalGradient.cpp::appendGradientStages). Cross-test GMs
 * that explicitly exercise the strip case are not in scope -- this
 * test class is the primary verification surface.
 */
class ConicalGradientStripRectTest {

    private val identityXform: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = SkColorSpace.makeSRGB(), dstAT = SkAlphaType.kUnpremul,
    )

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
        // (r0 / centerX1)^2 = (0.5 / 32)^2 = 1/4096 = 0.000244140625
        assertEquals(0.000244140625f, grad.getStripP0(), 1e-9f)
    }

    /**
     * Cross-check the `stripP0` scaling for a case where the answer
     * differs sharply from the un-scaled `r0^2`. With `r0 = 0.5` and
     * `centerX1 = 2.0`, upstream Skia stores `(0.5 / 2.0)^2 = 0.0625`,
     * whereas the pre-fix Kotlin port stored `0.5^2 = 0.25`. Either
     * value classifies as kStrip ; only the rendered output disambiguates.
     *
     * Mirrors `SkConicalGradient::appendGradientStages` (kStrip branch).
     */
    @Test
    fun `stripP0 applies centerX1 scaling for non-unit centre distance`() {
        val grad = SkConicalGradient.Make(
            start = SkPoint(0f, 0f), startRadius = 0.5f,
            end = SkPoint(2f, 0f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kStrip, grad.getType())
        // (r0 / centerX1)^2 = (0.5 / 2.0)^2 = 0.0625, NOT r0^2 = 0.25.
        assertEquals(0.0625f, grad.getStripP0(), 1e-7f)
    }

    /**
     * Verify the CPU raster `t` matches the upstream spec formula for a
     * sample of in-strip device pixels. The spec : map the device pixel
     * through `gradientMatrix * (CTM * localMatrix)^-1` to `(x, y)`,
     * then `t = x + sqrt(fP0 - y*y)` with `fP0 = (r0/centerX1)^2`. Here
     * CTM and localMatrix are identity, so the chain reduces to just
     * `gradientMatrix`.
     */
    @Test
    fun `CPU shadeRow matches the spec strip formula on the axis`() {
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!

        // Setup with identity CTM (xform doesn't affect strip math).
        grad.setupForDraw(SkMatrix.Identity, identityXform)

        // Sample px=32, py=32 -- on-axis, in-strip.
        //   x_mapped = (32.5 - 16) / 32 = 0.515625
        //   y_mapped = (32.5 - 32) / 32 = 0.015625
        //   fP0 = (0.5/32)^2 = 0.000244140625
        //   disc = fP0 - y_mapped^2 = 0 exactly -- in-strip boundary
        //   t = x_mapped + 0 = 0.515625 -- mid-balanced lerp red <-> blue.
        val dst = IntArray(1)
        grad.shadeRow(devX = 32, devY = 32, count = 1, dst = dst)

        // Decode premul ARGB color : at t = 0.515625, the lerp between
        // SK_ColorRED (0xFFFF0000) and SK_ColorBLUE (0xFF0000FF) yields
        // approximately (R, G, B, A) = (123, 0, 132, 255) post-premul.
        val pixel = dst[0]
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        assertEquals(255, a, "alpha should be opaque")
        assertEquals(0, g, "green should be zero")
        // Both R and B should be near the lerp midpoint (~123, ~132).
        assertTrue(r in 100..160, "R near midpoint, got $r")
        assertTrue(b in 100..160, "B near midpoint, got $b")
        // Sum should be ~255 (since lerp(red, blue) preserves saturation).
        assertTrue((r + b) in 230..270, "R+B near 255, got ${r + b}")
    }

    /**
     * Verify the strip mask kicks in at the right y-distance. With the
     * fix, the strip is `2 * r0 = 1` device-pixel tall around `py = 32`.
     * Rows `py = 33` and beyond must produce NaN-masked transparent
     * pixels.
     */
    @Test
    fun `CPU shadeRow masks pixels outside the strip half-width`() {
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0.5f,
            end = SkPoint(48f, 32f), endRadius = 0.5f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        grad.setupForDraw(SkMatrix.Identity, identityXform)

        // py=34 -> y_mapped = (34.5 - 32) / 32 = 0.078125, y_mapped^2 ~ 6.1e-3,
        // fP0 = 2.44e-4 -> disc < 0 -> NaN -> 0 (transparent black).
        val dst = IntArray(1)
        grad.shadeRow(devX = 32, devY = 34, count = 1, dst = dst)
        assertEquals(0, dst[0], "outside-strip pixel should be premul transparent black")
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

        // On the strip axis (py = 32, y_mapped = 0.015625) :
        //   disc = fP0 - y_mapped^2 = 0 exactly -- in-strip boundary ;
        //   t = x_mapped + 0 = (px + 0.5 - 16) / 32.
        // px=2 -> t = -0.421875 -- clamp -> red.
        val nearStart = pixels.rgbaAt(2, 32)
        assertTrue(nearStart[0] >= 180, "nearStart.R mostly red, got ${nearStart[0]}")
        assertTrue(nearStart[2] < 96, "nearStart.B near zero, got ${nearStart[2]}")
        assertTrue(nearStart[3] >= 250, "nearStart.A near opaque, got ${nearStart[3]}")

        // px=16 -> t = 0.015625 -- still mostly red (very close to t=0).
        val nearStartInside = pixels.rgbaAt(16, 32)
        assertTrue(nearStartInside[0] >= 180, "nearStartInside.R mostly red, got ${nearStartInside[0]}")
        assertTrue(nearStartInside[2] < 96, "nearStartInside.B near zero, got ${nearStartInside[2]}")

        // px=32 -> t = 0.515625 -- mid lerp.
        val mid = pixels.rgbaAt(32, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // px=60 -> t = 1.390625 -- clamp to 1 -> blue.
        val beyondEnd = pixels.rgbaAt(60, 32)
        assertTrue(beyondEnd[0] < 96, "beyondEnd.R near zero, got ${beyondEnd[0]}")
        assertTrue(beyondEnd[2] >= 180, "beyondEnd.B mostly blue, got ${beyondEnd[2]}")

        // Off-axis only 1 row away (py=34 -> y_mapped = 0.078125,
        // disc = 2.44e-4 - 6.10e-3 < 0) : transparent -> background white.
        // The strip is now only 1 device-pixel tall (rows py=31, 32), so
        // py=34 is well outside.
        val outsideStripNear = pixels.rgbaAt(30, 34)
        assertNear(outsideStripNear[0], 255, "outsideStripNear.R", tol = 16)
        assertNear(outsideStripNear[1], 255, "outsideStripNear.G", tol = 16)
        assertNear(outsideStripNear[2], 255, "outsideStripNear.B", tol = 16)

        // Far from the strip (py=58) : also disc < 0, transparent.
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

        // Same strip, kRepeat. At py=32, disc=0, t = x_mapped.
        //   px=2  -> t = -0.421875 -> repeat -> 0.578125 -> blue-leaning.
        //   px=60 -> t =  1.390625 -> repeat -> 0.390625 -> red-leaning.
        // This discriminates kRepeat from kClamp -- under kClamp, px=2 would
        // be pure red and px=60 would be pure blue.
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

        // px=2, py=32 -> t = -0.421875 -> repeat -> 0.578125 -> blue-leaning
        // (would be pure red under kClamp -- discriminator).
        val nearStart = pixels.rgbaAt(2, 32)
        assertTrue(nearStart[0] < 192, "nearStart.R not pure red (repeat active), got ${nearStart[0]}")
        assertTrue(nearStart[2] >= 96, "nearStart.B non-zero (repeat active), got ${nearStart[2]}")

        // px=60, py=32 -> t = 1.390625 -> repeat -> 0.390625 -> red-leaning
        // (would be pure blue under kClamp -- discriminator).
        val period1 = pixels.rgbaAt(60, 32)
        assertTrue(period1[0] >= 96, "period1.R non-zero (repeat active), got ${period1[0]}")
        assertTrue(period1[2] < 192, "period1.B not pure blue (repeat active), got ${period1[2]}")
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

        // At py=32, disc = 0 -> in-strip, t = x_mapped.
        // px=32, py=32 -> t = 0.515625 -- inside [0, 1] -- balanced lerp.
        val mid = pixels.rgbaAt(32, 32)
        assertTrue(mid[0] in 60..220, "mid.R balanced, got ${mid[0]}")
        assertTrue(mid[2] in 60..220, "mid.B balanced, got ${mid[2]}")

        // px=60, py=32 -> t = 1.390625 -- outside [0, 1] -- transparent ->
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
