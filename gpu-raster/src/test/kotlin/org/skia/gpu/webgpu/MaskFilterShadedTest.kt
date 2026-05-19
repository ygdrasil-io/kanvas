package org.skia.gpu.webgpu

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkImage
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode

/**
 * Phase MaskFilter-blur-shaded acceptance tests -- `paint.maskFilter =
 * SkBlurMaskFilter(...)` combined with a non-null `paint.shader`
 * (linear gradient, bitmap shader). Routes through the dedicated RGBA
 * pipeline in [SkWebGpuDevice.drawPathWithShadedBlurMaskFilter] :
 *  1. Rasterise the path with the FULL paint (shader + colourFilter)
 *     onto a child layer device, producing RGBA premul layer pixels.
 *  2. Allocate a per-draw scratch H-pass texture.
 *  3. Enqueue a [SkWebGpuDevice.BlurredShadedPathDraw] : the parent's
 *     flush replays it as two render passes (H Gaussian blur on the
 *     RGBA scratch ; V Gaussian blur + per-style RGBA combine +
 *     composite onto the parent's intermediate).
 *
 * The V-pass shader (`blur_mask_filter_shaded.wgsl`) combines the
 * blurred RGBA B with the original shaded layer A (and M = A.a) per
 * [SkBlurStyle] :
 *  - kNormal : B
 *  - kSolid  : A + B * (1 - A.a)   (SrcOver A over B)
 *  - kOuter  : B * (1 - M)
 *  - kInner  : B * M
 *
 * Coverage in this suite :
 *  - drawRect + linear gradient + kNormal : blurred gradient (red at
 *    left of the rect, blue at the right, soft edges).
 *  - drawRect + bitmap shader + kNormal : blurred tiled bitmap.
 *  - drawPath (circle) + gradient + kSolid : sharp gradient shape with
 *    soft halo on the outside.
 *  - kOuter halo only (gradient interior masked away).
 *  - kInner blur clipped inside (exterior fully transparent).
 *  - Regression : solid-paint blur path still produces the same
 *    pixels as before (the shaded path doesn't intercept).
 */
class MaskFilterShadedTest {

    @Test
    fun `drawRect with linear gradient and kNormal blur softens the gradient edges`() {
        // Rect [16, 24, 48, 40] (32x16) blurred with sigma = 3. The
        // rect is wide enough that the gradient is clearly visible
        // (red on the left, blue on the right) ; the soft edge band
        // (~9 px on each side) sits inside the 64x64 device.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 3f
        val rect = SkRect.MakeLTRB(16f, 24f, 48f, 40f)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(16f, 32f),
            p1 = SkPoint(48f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Sampling well within the interior of a blurred gradient on a
        // 32x16 rect with sigma 3 (radius 9) : the kernel mostly
        // straddles columns where the gradient has a similar t, so
        // the pixel reads close to the gradient's instantaneous
        // colour but smoothed. We pick samples that are far enough
        // from both ends to avoid the soft-edge halo while still
        // having an unambiguous "more red than blue" / "more blue
        // than red" classification.
        //
        // Sample (24, 32) -- 8 px into the rect from the left edge,
        // gradient t ~ 0.25 -> dominant red, some purple bleed from
        // the blur kernel reaching mid-gradient. R should still
        // outweigh B noticeably.
        val leftInside = pixels.rgbaAt(24, 32)
        assertTrue(leftInside[0] > leftInside[2] + 60,
            "left-quarter pixel should have R >> B (red-leaning), " +
                "got R = ${leftInside[0]}, B = ${leftInside[2]}")

        // Sample (40, 32) -- 8 px into the rect from the right edge,
        // gradient t ~ 0.75 -> dominant blue.
        val rightInside = pixels.rgbaAt(40, 32)
        assertTrue(rightInside[2] > rightInside[0] + 60,
            "right-quarter pixel should have B >> R (blue-leaning), " +
                "got R = ${rightInside[0]}, B = ${rightInside[2]}")

        // Far above the rect (y = 0, 24 px above the top edge ; the
        // blur radius is 9 px so this is well past the blur extent) :
        // background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(32, 0),
            "above the blur extent : background")

        // Edge soft band at the rect's right edge (x = 48) : the
        // gradient end is blue ; the soft-edge pixels should be
        // partial-coverage blue (some background bleed-through).
        // Look for at least one pixel in x = 48..56 (the band) with
        // intermediate blue alpha -- i.e. partial coverage.
        val rightBand = (48..56).map { x ->
            pixels.rgbaAt(x, 32)
        }
        val partials = rightBand.count { rgba ->
            // Partial-coverage pixel : R near 0 (no red bleed at this
            // end of the gradient) and B somewhere between background
            // (255) and full blue (well below 255) -- the SrcOver-on-
            // white shows blue tinted toward white.
            rgba[0] < 220 && rgba[2] > 80 && rgba[2] < 255
        }
        assertTrue(partials >= 2,
            "expected at least 2 partial-coverage pixels in the right " +
                "soft-edge band, got ${partials} (band : $rightBand)")
    }

    @Test
    fun `drawPath with gradient and kSolid blur preserves sharp shape with outer halo`() {
        // Circle of radius 14 centred at (32, 32), shaded with a
        // horizontal red->blue gradient, blurred with sigma = 3 and
        // kSolid style. kSolid : output = A + B * (1 - A.a). Inside
        // the disk (A.a = 1) the output reduces to A -- the sharp
        // gradient stays. Outside the disk (A.a = 0) the output
        // reduces to B -- the blurred halo only.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 3f
        val cx = 32f; val cy = 32f; val r = 14f
        val circle = SkPath.Circle(cx, cy, r)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(cx - r, cy),
            p1 = SkPoint(cx + r, cy),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(circle, paint)
                device.flush()
            }
        }

        // Disk centre (32, 32) : gradient midpoint, fully opaque (A.a = 1
        // at the interior, kSolid preserves A). The midpoint between
        // red and blue produces a saturated purple-ish premul colour ;
        // both R and B should be moderate (around half) and G near 0.
        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] in 60..200,
            "kSolid centre R should be moderate (~half gradient), got ${centre[0]}")
        assertTrue(centre[2] in 60..200,
            "kSolid centre B should be moderate (~half gradient), got ${centre[2]}")
        assertTrue(centre[1] < 100,
            "kSolid centre G should be low (no green in the gradient), got ${centre[1]}")
        // Alpha stays opaque -- the sharp gradient interior covers the
        // background.
        assertTrue(centre[3] >= 240,
            "kSolid centre alpha should be ~opaque, got ${centre[3]}")

        // Just inside the right edge of the circle (x = cx + r - 2 = 44,
        // y = 32) : near-blue (gradient end), still opaque (kSolid
        // pins A back to fully solid on interior pixels).
        val justInside = pixels.rgbaAt(44, 32)
        assertTrue(justInside[2] > 150,
            "kSolid just-inside R-edge B should be near blue, got ${justInside[2]}")
        // Alpha approaches opaque (some AA at the boundary keeps it
        // slightly under 255, but kSolid forces the inside-of-the-
        // shape to read A directly, so the coverage is whatever the
        // sharp AA produced).
        assertTrue(justInside[3] >= 230,
            "kSolid just-inside-edge alpha should be near opaque, got ${justInside[3]}")

        // Halo on the OUTSIDE of the disk : at x = 48 (2 px past the
        // boundary at the right end of the disk where the gradient is
        // blue), the kSolid output = B (since A.a = 0 there). The
        // blurred B is mostly blue (the kernel mostly straddles the
        // blue end of the disk). The halo is the SrcOver-on-white
        // result : light blue tint.
        val halo = pixels.rgbaAt(48, 32)
        // The halo IS blue-tinted (B contributes blue) and clearly
        // distinguishable from a pure-background pixel.
        assertTrue(halo[2] > halo[0],
            "kSolid halo at x = 48 should be more blue than red, " +
                "got ${halo}")
        assertTrue(halo[0] < 240 || halo[1] < 240,
            "kSolid halo at x = 48 should be tinted (not pure " +
                "background), got ${halo}")

        // Far outside (x = 60, 14 px past the boundary, well past the
        // blur extent of 9 px) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kSolid far-outside must be background (no blur reach)")
    }

    @Test
    fun `drawRect with gradient and kOuter blur shows halo only outside the rect`() {
        // kOuter : output = B * (1 - M). Inside the rect M = 1 and B
        // is high -- the multiplier zeroes it out, so the interior
        // shows background. Outside the rect M = 0 -- only B remains.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 3f
        val rect = SkRect.MakeLTRB(20f, 24f, 44f, 40f)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(20f, 32f),
            p1 = SkPoint(44f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kOuter, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Centre of the rect : kOuter subtracts the M = 1 silhouette,
        // so the interior shows background untouched.
        val centre = pixels.rgbaAt(32, 32)
        assertEquals(listOf(255, 255, 255, 255), centre,
            "kOuter rect interior must be background (M = 1 suppresses B)")

        // Just outside the right edge (x = 44, 1 px past the boundary) :
        // halo present. Should be partial coverage of blue-ish.
        val halo = pixels.rgbaAt(45, 32)
        assertTrue(halo[2] < 255 && halo[0] < halo[2] + 30,
            "kOuter halo at x = 45 should be partial blue, got $halo")

        // Far outside (x = 56, 12 px past the right edge ; blur reach
        // is 9 px) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(56, 32),
            "kOuter far-outside must be background")
    }

    @Test
    fun `drawRect with gradient and kInner blur clips the blur to inside the rect`() {
        // kInner : output = B * M. Inside the rect M = 1 so the output
        // is the blurred gradient ; outside M = 0 so the output is
        // transparent (background bleeds through).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 3f
        val rect = SkRect.MakeLTRB(20f, 24f, 44f, 40f)
        val grad = SkLinearGradient.Make(
            p0 = SkPoint(20f, 32f),
            p1 = SkPoint(44f, 32f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        val paint = SkPaint().apply {
            shader = grad
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kInner, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        // Deep inside the rect (32, 32) : the gradient is shown, B is
        // near-saturated for the centre column of a 24x16 interior.
        // Output should look like the gradient midpoint.
        val centre = pixels.rgbaAt(32, 32)
        // Centre is the gradient midpoint -- partial-saturated R and
        // B from the SrcOver-on-white premul math.
        assertTrue(centre[0] in 40..220,
            "kInner centre R should be moderate, got ${centre[0]}")
        assertTrue(centre[2] in 40..220,
            "kInner centre B should be moderate, got ${centre[2]}")

        // Just outside the rect's right edge (x = 44 -- the first
        // pixel past the right boundary) : kInner has M = 0 there,
        // so background bleeds through fully.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(44, 32),
            "kInner just-outside must be background (M = 0)")

        // Far outside : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "kInner far-outside must be background")
    }

    @Test
    fun `drawRect with bitmap shader and kNormal blur softens the tiled bitmap`() {
        // Bitmap shader on a rect with kNormal blur. The bitmap is a
        // 4x4 quadrant pattern (R/G/B/Bk). The rect covers a region
        // larger than 4x4 with kRepeat to ensure the shader produces
        // a tiled, non-trivial RGBA layer. The blur should soften
        // the colour transitions across the bitmap tile boundaries.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 3f
        val rect = SkRect.MakeLTRB(16f, 16f, 48f, 48f)  // 32x32
        val image = makeQuadrantImage(SIDE)
        val paint = SkPaint().apply {
            shader = image.makeShader(
                tileX = SkTileMode.kRepeat,
                tileY = SkTileMode.kRepeat,
                sampling = SkSamplingOptions.nearest(),
            )
            isAntiAlias = true
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                // Translate first so the image origin lands at (16, 16).
                val canvas = SkCanvas(device)
                canvas.translate(16f, 16f)
                canvas.drawRect(
                    SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    paint,
                )
                device.flush()
            }
        }

        // Above the rect's top edge by more than the blur reach (9 px) :
        // background untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(32, 4),
            "well above the blur extent : background")

        // Deep inside the rect, far from any edge : the bitmap shader
        // produces colourful pixels (the blur softens across the 4x4
        // quadrant boundaries but doesn't fully wash them out). At
        // device pixel (32, 32) the local image coord is (16, 16)
        // modulo 4 = (0, 0) -> red quadrant ; the blur kernel averages
        // surrounding red/green/blue/black tiles. Expect non-trivial
        // coverage : at least one channel meaningfully present, and
        // alpha approaches opaque (the interior is far from the rect
        // boundary so the kernel sees full-coverage source).
        val deep = pixels.rgbaAt(32, 32)
        assertTrue(deep[3] >= 220,
            "deep interior alpha should be near opaque, got ${deep[3]}")
        // The four-quadrant average is not pure white -- at least one
        // RGB channel should be well below 255 (showing the bitmap
        // colours bled through).
        assertTrue(deep[0] < 240 || deep[1] < 240 || deep[2] < 240,
            "deep interior must be coloured (non-background), got $deep")

        // Far past the rect's right edge (x = 60, 12 px past the
        // right side ; > blur reach of 9 px) : background.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(60, 32),
            "far past the right edge : background")
    }

    @Test
    fun `solid paint blur path unchanged when shaded path coexists`() {
        // Regression : verify the solid-paint MaskFilter blur path still
        // produces its expected pixels when the shaded variant exists.
        // Same rect as the kNormal canonical solid case from
        // MaskFilterBlurTest -- if the dispatch gate routed it through
        // the shaded path, the centre pixel would still be near-black
        // (paint.color = SK_ColorBLACK -> A.rgb = (0, 0, 0, A.a)), so
        // this test only catches a structural regression (compile-
        // time or null-paint propagation), not a math regression.
        // Pixel-iso compares are in MaskFilterBlurTest.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sigma = 4f
        val rect = SkRect.MakeLTRB(20f, 20f, 44f, 44f)
        val paint = SkPaint().apply {
            color = SK_ColorBLACK
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, sigma)
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(rect, paint)
                device.flush()
            }
        }

        val centre = pixels.rgbaAt(32, 32)
        assertTrue(centre[0] < 20,
            "regression : solid-paint blur centre R should be near 0, " +
                "got ${centre[0]}")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(5, 5),
            "regression : solid-paint blur far-outside is background")
    }

    /**
     * 4x4 image split into 4 quadrants : R / G / B / Bk. Mirrors
     * [BitmapShaderPaintRectTest.makeQuadrantImage].
     */
    private fun makeQuadrantImage(side: Int): SkImage {
        val bitmap = SkBitmap(side, side)
        val half = side / 2
        for (y in 0 until side) {
            for (x in 0 until side) {
                val color = when {
                    x < half && y < half -> SK_ColorRED
                    x >= half && y < half -> SK_ColorGREEN
                    x < half && y >= half -> SK_ColorBLUE
                    else -> SK_ColorBLACK
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return SkImage.Make(bitmap)
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
        const val SIDE: Int = 4
    }
}
