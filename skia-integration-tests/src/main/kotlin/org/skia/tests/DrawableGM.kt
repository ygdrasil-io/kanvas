package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkDrawable
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/drawable.cpp::drawable` (180 × 275).
 *
 * Exercises the [SkDrawable] extension slot through [SkCanvas.drawDrawable]
 * under four CTM variants : a plain translate, an `(x, y)` translate
 * overload, an arbitrary [SkMatrix] (scale + post-translate), and a
 * second matrix with an additional post-translate. The drawable's
 * `onDraw` paints a blue control-bbox of a single-conic path, then
 * the anti-aliased white-filled conic path on top, so each instance
 * renders a "blue rect with a white curved cap inside it".
 *
 * C++ original:
 * ```cpp
 * struct MyDrawable : public SkDrawable {
 *     SkRect onGetBounds() override { return SkRect::MakeWH(50, 100);  }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPath path = SkPathBuilder().moveTo(10, 10)
 *                                      .conicTo(10, 90, 50, 90, 0.9f)
 *                                      .detach();
 *
 *        SkPaint paint;
 *        paint.setColor(SK_ColorBLUE);
 *        canvas->drawRect(path.getBounds(), paint);
 *
 *        paint.setAntiAlias(true);
 *        paint.setColor(SK_ColorWHITE);
 *        canvas->drawPath(path, paint);
 *     }
 * };
 *
 * DEF_SIMPLE_GM(drawable, canvas, 180, 275) {
 *     sk_sp<SkDrawable> drawable(new MyDrawable);
 *
 *     canvas->translate(10, 10);
 *     canvas->drawDrawable(drawable.get());
 *     canvas->drawDrawable(drawable.get(), 0, 150);
 *
 *     SkMatrix m = SkMatrix::Scale(1.5f, 0.8f);
 *     m.postTranslate(70, 0);
 *     canvas->drawDrawable(drawable.get(), &m);
 *
 *     m.postTranslate(0, 150);
 *     canvas->drawDrawable(drawable.get(), &m);
 * }
 * ```
 */
public class DrawableGM : GM() {
    override fun getName(): String = "drawable"
    override fun getISize(): SkISize = SkISize.Make(180, 275)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val drawable: SkDrawable = MyDrawable()

        c.translate(10f, 10f)
        c.drawDrawable(drawable)
        c.drawDrawable(drawable, 0f, 150f)

        var m = SkMatrix.MakeScale(1.5f, 0.8f).postTranslate(70f, 0f)
        c.drawDrawable(drawable, m)

        m = m.postTranslate(0f, 150f)
        c.drawDrawable(drawable, m)
    }

    /**
     * Mirrors the `MyDrawable` inner struct in `drawable.cpp`. The path
     * is a single conic `(10,10) → (50,90)` with control point `(10,90)`
     * and weight `0.9` ; its **control-bbox** `getBounds()` is the rect
     * `(10, 10, 50, 90)`.
     */
    private class MyDrawable : SkDrawable() {
        override fun onGetBounds(): SkRect = SkRect.MakeWH(50f, 100f)

        override fun onDraw(canvas: SkCanvas) {
            val path: SkPath = SkPathBuilder()
                .moveTo(10f, 10f)
                .conicTo(10f, 90f, 50f, 90f, 0.9f)
                .detach()

            val paint = SkPaint()
            paint.color = SK_ColorBLUE
            // Upstream `SkPath::getBounds()` returns the control-point
            // bbox (== kanvas-skia's `computeBounds`).
            canvas.drawRect(path.computeBounds(), paint)

            paint.isAntiAlias = true
            paint.color = SK_ColorWHITE
            canvas.drawPath(path, paint)
        }
    }
}
