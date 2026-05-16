package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/bigrect.cpp` (`BigRectGM`).
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("bigrect"); }
 * SkISize getISize() override { return SkISize::Make(325, 125); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkScalar sizes[] = { 100, 5e10f, 1e6f };
 *     for (int i = 0; i < 8; ++i) {
 *         for (size_t j = 0; j < std::size(sizes); ++j) {
 *             canvas->save();
 *             canvas->translate(SkIntToScalar(i*40+5), SkIntToScalar(j*40+5));
 *             SkPaint paint;
 *             paint.setColor(SK_ColorBLUE);
 *             paint.setStyle(i & 1 ? SkPaint::kFill_Style : SkPaint::kStroke_Style);
 *             paint.setStrokeWidth(i & 2 ? 1 : 0);
 *             paint.setAntiAlias(SkToBool(i & 4));
 *             this->drawBigRect(canvas, sizes[j], paint);
 *             canvas->restore();
 *         }
 *     }
 * }
 *
 * void drawBigRect(SkCanvas* canvas, SkScalar big, const SkPaint& rectPaint) {
 *     canvas->clipRect(SkRect::MakeLTRB(0, 0, 35, 35));
 *     canvas->translate(SK_ScalarHalf, SK_ScalarHalf);
 *     // 10 axis-aligned rects + outOfBoundsPaint frame.
 *     ...
 * }
 * ```
 */
public class BigRectGM : GM() {
    override fun getName(): String = "bigrect"
    override fun getISize(): SkISize = SkISize.Make(325, 125)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val sizes = floatArrayOf(100f, 5e10f, 1e6f)
        for (i in 0 until 8) {
            for (j in sizes.indices) {
                c.save()
                c.translate((i * 40 + 5).toFloat(), (j * 40 + 5).toFloat())
                val paint = SkPaint().apply {
                    color = SK_ColorBLUE
                    style = if (i and 1 != 0) SkPaint.Style.kFill_Style else SkPaint.Style.kStroke_Style
                    strokeWidth = if (i and 2 != 0) 1f else 0f
                    isAntiAlias = (i and 4) != 0
                }
                drawBigRect(c, sizes[j], paint)
                c.restore()
            }
        }
    }

    private fun drawBigRect(canvas: SkCanvas, big: Float, rectPaint: SkPaint) {
        canvas.clipRect(SkRect.MakeLTRB(0f, 0f, 35f, 35f))
        canvas.translate(0.5f, 0.5f)

        canvas.drawRect(SkRect.MakeLTRB(-big, 5f, big, 10f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(5f, -big, 10f, big), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(-big, 20f, 17f, 25f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(20f, -big, 25f, 17f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(28f, 20f, big, 25f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(20f, 28f, 25f, big), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(-2f, -1f, 0f, 35f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(-1f, -2f, 35f, 0f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(34f, -1f, 36f, 35f), rectPaint)
        canvas.drawRect(SkRect.MakeLTRB(-1f, 34f, 35f, 36f), rectPaint)

        val outOfBoundsPaint = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }
        canvas.drawRect(SkRect.MakeLTRB(-1f, -1f, 35f, 35f), outOfBoundsPaint)
    }
}
