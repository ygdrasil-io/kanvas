package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlurMask
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurrect.cpp::DEF_SIMPLE_GM(blurrect_gallery, …, 1200, 1024)`.
 *
 * Exercises `SkBlurMask::BlurRect` — the low-level internal helper that
 * analytically computes a Gaussian-blurred alpha mask for an
 * axis-aligned rectangle without going through the full paint / canvas
 * pipeline. The gallery shows a variety of rect sizes, blur radii, and
 * styles ([SkBlurStyle.kNormal], [SkBlurStyle.kInner],
 * [SkBlurStyle.kOuter]) laid out left-to-right with line-wrapping.
 *
 * C++ body:
 * ```cpp
 * DEF_SIMPLE_GM(blurrect_gallery, canvas, 1200, 1024) {
 *     const int widths[]  = {25, 5, 5, 100, 150, 25};
 *     const int heights[] = {100, 100, 5, 25, 150, 25};
 *     const SkBlurStyle styles[] = {kNormal, kInner, kOuter};
 *     const float radii[] = {20, 5, 10};
 *     canvas->translate(50, 20);
 *     int cur_x = 0, cur_y = 0, max_height = 0;
 *     for each (width, height) × radius × style:
 *         SkBlurMask::BlurRect(ConvertRadiusToSigma(radius), &mask, r, style)
 *         bm.installPixels(MakeA8(mask.fBounds.wh), mask.image(), mask.fRowBytes)
 *         canvas->drawImage(bm.asImage(), ...)
 * }
 * ```
 *
 * Uses [SkBlurMask.BlurRect], which is backed by the same separable
 * Gaussian/style implementation as [org.skia.foundation.SkBlurMaskFilter].
 */
public class BlurRectGalleryGM : GM() {

    override fun getName(): String = "blurrect_gallery"
    override fun getISize(): SkISize = SkISize.Make(1200, 1024)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val fGMWidth = 1200
        val fPadding = 10
        val fMargin = 100

        val widths  = intArrayOf(25, 5, 5, 100, 150, 25)
        val heights = intArrayOf(100, 100, 5, 25, 150, 25)
        val styles  = arrayOf(SkBlurStyle.kNormal, SkBlurStyle.kInner, SkBlurStyle.kOuter)
        val radii   = floatArrayOf(20f, 5f, 10f)

        c.translate(50f, 20f)

        var curX = 0
        var curY = 0
        var maxHeight = 0

        for (i in widths.indices) {
            val width  = widths[i]
            val height = heights[i]
            val r = SkRect.MakeWH(width.toFloat(), height.toFloat())

            c.save()

            for (radius in radii) {
                val sigma = SkBlurMask.ConvertRadiusToSigma(radius)

                for (style in styles) {
                    // BlurRect returns null if the combination is degenerate;
                    // the C++ original used a bool return and `continue`.
                    val mask = SkBlurMask.BlurRect(sigma, r, style) ?: continue

                    val bm = SkBitmap(
                        mask.fBounds.width(),
                        mask.fBounds.height(),
                        colorType = SkColorType.kAlpha_8,
                    )
                    // Copy the raw A8 pixels from the mask into the bitmap's backing store.
                    // mask.fRowBytes may be >= bm.width; copy row-by-row when stride differs.
                    val w = mask.fBounds.width()
                    val h = mask.fBounds.height()
                    for (row in 0 until h) {
                        System.arraycopy(mask.image, row * mask.fRowBytes, bm.pixelsA8, row * w, w)
                    }

                    if (curX + bm.width >= fGMWidth - fMargin) {
                        curX = 0
                        curY += maxHeight + fPadding
                        maxHeight = 0
                    }

                    c.save()
                    c.translate(curX.toFloat(), curY.toFloat())
                    // Centre the mask over the original rect (mask is larger due to blur spread).
                    c.translate(
                        -(bm.width  - r.width())  / 2f,
                        -(bm.height - r.height()) / 2f,
                    )
                    c.drawImage(bm.asImage(), 0f, 0f)
                    c.restore()

                    curX += bm.width + fPadding
                    if (bm.height > maxHeight) maxHeight = bm.height
                }
            }

            c.restore()
        }
    }
}
