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
 * G4.4.1 acceptance test -- conical (two-point) gradient (kClamp) fill of
 * a rect on GPU, **focal-inside well-behaved** sub-case (focal point
 * inside the end circle, `focalData.fR1 > 1`).
 *
 * The test fixture uses `start = (16, 32)`, `startRadius = 0`,
 * `end = (32, 32)`, `endRadius = 25`. With `r0 = 0`, the focal point
 * coincides with `start`, and since `dist(start, end) = 16 < 25 = r1`,
 * the focal point is inside the end circle (well-behaved kFocal).
 *
 * Sample-point expectations (CPU reference) :
 *  - Pixel at the focal point `(16, 32)` -- distance to focal = 0 ->
 *    `t = 0` (after the well-behaved formula collapses to 0 when both
 *    transformed coords are zero) -> first stop (red).
 *  - Pixel far enough from the focal in any direction so the geometric
 *    interpolated circle has `t = 1` -> last stop (blue).
 *  - Pixel along the line through `start` and `end`, far past `end`,
 *    `t` clamped to 1 -> blue.
 *
 * Most assertions are wide bands because the focal-frame transform
 * folds in a normalize-by-dCenter scale and then applies
 * `MakePolyToPoly({focal, (1,0)}, {(0,0), (1,0)})` plus the
 * acceleration scales -- exact pixel correspondence is verified by the
 * cross-test GM, not by hand-computed coordinates here.
 */
class ConicalGradientFocalRectTest {

    @Test
    fun `r0 zero focal-inside is correctly classified as kFocal well-behaved`() {
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(32f, 32f), endRadius = 25f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        val fd = grad.getFocalData()
        assertTrue(fd != null, "focalData must be present for kFocal")
        assertTrue(fd!!.isWellBehaved(), "fixture must be focal-inside well-behaved")
    }

    @Test
    fun `red-to-blue focal-inside conical interpolates correctly on GPU`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Focal at (16, 32), end circle centre (32, 32), end radius 25.
        // - Focal point is well inside the end circle (1 in focal frame
        //   maps to distance 16 between focal and end ; end radius 25 ->
        //   fR1 = 25 / |1 - 0| / 16 (normalized) = 25 / 16 > 1).
        // - t = 0 at the focal point ; t increases towards the end circle ;
        //   t = 1 on the end circle.
        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(32f, 32f), endRadius = 25f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
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

        // At the focal point pixel (16, 32) : t ~ 0 -> red.
        val focal = pixels.rgbaAt(16, 32)
        assertTrue(focal[0] >= 180, "focal.R mostly red, got ${focal[0]}")
        assertTrue(focal[2] < 96, "focal.B near zero, got ${focal[2]}")
        assertTrue(focal[3] >= 250, "focal.A near opaque, got ${focal[3]}")

        // On the end circle along +x : the boundary intersection on the
        // far side is at (32 + 25, 32) = (57, 32). But our rect only
        // spans up to x=62, so (57, 32) is inside the rect.
        // t = 1 there -> blue.
        val endRight = pixels.rgbaAt(57, 32)
        assertTrue(endRight[0] < 96, "endRight.R near zero, got ${endRight[0]}")
        assertTrue(endRight[2] >= 180, "endRight.B mostly blue, got ${endRight[2]}")

        // Beyond the end circle along +x : (60, 32). Clamped to t = 1 -> blue.
        val beyond = pixels.rgbaAt(60, 32)
        assertTrue(beyond[0] < 96, "beyond.R near zero, got ${beyond[0]}")
        assertTrue(beyond[2] >= 180, "beyond.B mostly blue, got ${beyond[2]}")

        // Outside the rect : background white untouched.
        val outside = pixels.rgbaAt(0, 0)
        assertNear(outside[0], 255, "outside.R", tol = 16)
        assertNear(outside[1], 255, "outside.G", tol = 16)
        assertNear(outside[2], 255, "outside.B", tol = 16)
    }

    // G4.4.2 -- kRepeat / kMirror / kDecal exercise the new fragment
    // entry points on the focal-inside well-behaved pipeline.
    //
    // Fixture : start=(16, 32), startRadius=0, end=(20, 32), endRadius=8.
    // dCenter=4, fR1 = 8/4 = 2 > 1 -> well-behaved focal-inside.
    // The tight end disk means most pixels on the rect have t_raw > 1,
    // discriminating between the 4 tile modes :
    //   pixel (24, 32) -> t_raw ~ 0.71  (inside both rings, blue-ish)
    //   pixel (32, 32) -> t_raw ~ 1.38  (clamp=1, repeat=0.38, mirror=0.62,
    //                                    decal=OFF)
    //   pixel (40, 32) -> t_raw ~ 2.04  (clamp=1, repeat=0.04 ~red,
    //                                    mirror=0.04 ~red, decal=OFF)
    //   pixel (60, 32) -> t_raw ~ 3.71  (clamp=1, repeat=0.71 blue,
    //                                    mirror=0.29 red, decal=OFF)
    // pixel (60, 32) is the cleanest kRepeat-vs-kMirror discriminator.

    @Test
    fun `kRepeat tiles the focal-inside conical gradient outside the end disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(20f, 32f), endRadius = 8f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kRepeat,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        assertTrue(grad.getFocalData()?.isWellBehaved() == true)
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

        // Focal (16, 32) -- t_raw ~ 0.07 -- mostly red.
        val focal = pixels.rgbaAt(16, 32)
        assertTrue(focal[0] >= 180, "focal.R mostly red, got ${focal[0]}")
        assertTrue(focal[2] < 96, "focal.B near zero, got ${focal[2]}")

        // (40, 32) -- t_raw ~ 2.04 -- repeat -> 0.04 -- mostly red.
        val period2 = pixels.rgbaAt(40, 32)
        assertTrue(period2[0] >= 180, "period2.R mostly red, got ${period2[0]}")
        assertTrue(period2[2] < 96, "period2.B near zero, got ${period2[2]}")

        // (60, 32) -- t_raw ~ 3.71 -- repeat -> 0.71 -- mostly blue.
        // This is the kRepeat-vs-kMirror discriminator (kMirror would
        // give t ~ 0.29 -- mostly red).
        val period4 = pixels.rgbaAt(60, 32)
        assertTrue(period4[0] < 96, "period4.R near zero, got ${period4[0]}")
        assertTrue(period4[2] >= 180, "period4.B mostly blue, got ${period4[2]}")
    }

    @Test
    fun `kMirror reflects the focal-inside conical gradient outside the end disk`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(20f, 32f), endRadius = 8f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        assertTrue(grad.getFocalData()?.isWellBehaved() == true)
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

        // Focal (16, 32) -- t_raw ~ 0.07 -- mostly red.
        val focal = pixels.rgbaAt(16, 32)
        assertTrue(focal[0] >= 180, "focal.R mostly red, got ${focal[0]}")
        assertTrue(focal[2] < 96, "focal.B near zero, got ${focal[2]}")

        // (50, 32) -- t_raw ~ 2.88 -- mirror -> 0.88 -- mostly blue.
        val ring2Blue = pixels.rgbaAt(50, 32)
        assertTrue(ring2Blue[0] < 96, "ring2Blue.R near zero, got ${ring2Blue[0]}")
        assertTrue(ring2Blue[2] >= 160, "ring2Blue.B mostly blue, got ${ring2Blue[2]}")

        // (60, 32) -- t_raw ~ 3.71 -- mirror -> 0.29 -- mostly red.
        // kRepeat-vs-kMirror discriminator (kRepeat would give blue).
        val period4 = pixels.rgbaAt(60, 32)
        assertTrue(period4[0] >= 160, "period4.R mostly red, got ${period4[0]}")
        assertTrue(period4[2] < 96, "period4.B near zero, got ${period4[2]}")
    }

    @Test
    fun `kDecal punches transparent outside the focal-inside conical gradient`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val grad = SkConicalGradient.Make(
            start = SkPoint(16f, 32f), startRadius = 0f,
            end = SkPoint(20f, 32f), endRadius = 8f,
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kDecal,
        )!!
        assertEquals(SkConicalGradient.Type.kFocal, grad.getType())
        assertTrue(grad.getFocalData()?.isWellBehaved() == true)
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

        // Focal (16, 32) -- t_raw ~ 0.07 -- inside [0, 1] -- mostly red.
        val focal = pixels.rgbaAt(16, 32)
        assertTrue(focal[0] >= 180, "focal.R mostly red, got ${focal[0]}")
        assertTrue(focal[2] < 96, "focal.B near zero, got ${focal[2]}")

        // (24, 32) -- t_raw ~ 0.71 -- inside [0, 1] -- mostly blue.
        val inside = pixels.rgbaAt(24, 32)
        assertTrue(inside[0] < 128, "inside.R smaller, got ${inside[0]}")
        assertTrue(inside[2] >= 100, "inside.B blue-leaning, got ${inside[2]}")

        // (32, 32) -- t_raw ~ 1.38 -- outside [0, 1] -- transparent --
        // background white untouched.
        val outside1 = pixels.rgbaAt(32, 32)
        assertNear(outside1[0], 255, "outside1.R", tol = 16)
        assertNear(outside1[1], 255, "outside1.G", tol = 16)
        assertNear(outside1[2], 255, "outside1.B", tol = 16)

        // (60, 32) -- t_raw ~ 3.71 -- outside -- transparent.
        val outside2 = pixels.rgbaAt(60, 32)
        assertNear(outside2[0], 255, "outside2.R", tol = 16)
        assertNear(outside2[1], 255, "outside2.G", tol = 16)
        assertNear(outside2[2], 255, "outside2.B", tol = 16)
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
