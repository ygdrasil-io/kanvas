package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/draw_bitmap_rect_skbug4374.cpp::draw_bitmap_rect_skbug4734`
 * (the GM is registered as `draw_bitmap_rect_skbug4734` — the filename's `4374`
 * is a typo against the upstream `skbug.com/40035524` referenced in the source).
 *
 * Loads `images/randPixels.png` (an 8×8 grid of saturated random pixels), insets
 * the source rect by `(0.5, 1.5)` and maps it through `SkMatrix::Scale(8, 8)` to
 * produce the destination rect. The point of the GM is to exercise sub-pixel
 * `src` insets — a regression check for `drawImageRect` precision under
 * fractional `src` coordinates.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(draw_bitmap_rect_skbug4734, canvas, 64, 64) {
 *     auto img = ToolUtils::MakeTextureImage(canvas,
 *                                            ToolUtils::GetResourceAsImage("images/randPixels.png"));
 *     if (img) {
 *         SkRect rect = SkRect::Make(img->bounds());
 *         rect.inset(0.5, 1.5);
 *         SkRect dst;
 *         SkMatrix::Scale(8, 8).mapRect(&dst, rect);
 *         canvas->drawImageRect(img, rect, dst, SkSamplingOptions(), nullptr,
 *                               SkCanvas::kStrict_SrcRectConstraint);
 *     }
 * }
 * ```
 */
public class DrawBitmapRectSkbug4734GM : GM() {
    override fun getName(): String = "draw_bitmap_rect_skbug4734"
    override fun getISize(): SkISize = SkISize.Make(64, 64)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.MakeTextureImage(c, ToolUtils.GetResourceAsImage("images/randPixels.png"))
            ?: return
        val rect = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
        rect.inset(0.5f, 1.5f)
        val dst = SkMatrix.MakeScale(8f, 8f).mapRect(rect)
        c.drawImageRect(
            image,
            src = rect,
            dst = dst,
            sampling = SkSamplingOptions.Default,
            paint = null,
            constraint = SrcRectConstraint.kStrict,
        )
    }
}
