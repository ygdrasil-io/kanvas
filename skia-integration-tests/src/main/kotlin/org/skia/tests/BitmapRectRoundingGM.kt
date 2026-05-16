package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`BitmapRectRounding`).
 *
 * Probes precision of `drawImageRect` under a sub-pixel `scale(0.9, 0.9)`
 * CTM. The destination rect lands on a 1/2 pixel boundary for its bottom
 * edge — a precision-loss bug used to leak the underlying red `drawRect`
 * along the bottom seam. The expected output is all blue (the bitmap)
 * fully covering the underlying red rect; if precision regresses we'd see
 * a 1-pixel red line at the bottom.
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("bitmaprect_rounding"); }
 * SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 * void onOnceBeforeDraw() override {
 *     fBM.allocN32Pixels(10, 10);
 *     fBM.eraseColor(SK_ColorBLUE);
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setColor(SK_ColorRED);
 *
 *     const SkRect r = SkRect::MakeXYWH(1, 1, 110, 114);
 *     canvas->scale(0.9f, 0.9f);
 *
 *     // the drawRect shows the same problem as clipRect(r) followed by drawcolor(red)
 *     canvas->drawRect(r, paint);
 *     canvas->drawImageRect(fBM.asImage(), r, SkSamplingOptions());
 * }
 * ```
 */
public class BitmapRectRoundingGM : GM() {

    private val fBM: SkBitmap = SkBitmap(10, 10).also { it.eraseColor(SK_ColorBLUE) }

    override fun getName(): String = "bitmaprect_rounding"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { color = SK_ColorRED }

        val r = SkRect.MakeXYWH(1f, 1f, 110f, 114f)
        c.scale(0.9f, 0.9f)

        // The drawRect shows the same problem as clipRect(r) + drawColor(red).
        c.drawRect(r, paint)
        c.drawImageRect(
            fBM.asImage(),
            SkRect.MakeWH(fBM.width.toFloat(), fBM.height.toFloat()),
            r,
            SkSamplingOptions.Default,
            paint = null,
            constraint = SrcRectConstraint.kStrict,
        )
    }
}
