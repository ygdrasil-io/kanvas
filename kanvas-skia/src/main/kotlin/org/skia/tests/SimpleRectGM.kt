package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.colorToRGB565
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/simplerect.cpp` (`SimpleRectGM`).
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("simplerect"); }
 * SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->translate(1, 1);
 *     const SkScalar min = -20, max = 800, size = 20;
 *     SkRandom rand;
 *     SkPaint paint;
 *     for (int i = 0; i < 10000; i++) {
 *         paint.setColor(ToolUtils::color_to_565(rand.nextU() | (0xFF << 24)));
 *         SkScalar x = rand.nextRangeScalar(min, max);
 *         SkScalar y = rand.nextRangeScalar(min, max);
 *         SkScalar w = rand.nextRangeScalar(0, size);
 *         SkScalar h = rand.nextRangeScalar(0, size);
 *         canvas->drawRect(SkRect::MakeXYWH(x, y, w, h), paint);
 *     }
 * }
 * ```
 *
 * Note: SkRandom is not bit-compatible with Skia's, so the 10 000 random
 * rects fall in different positions / colours than the reference. Test
 * threshold is therefore 95% rather than 99%.
 */
public class SimpleRectGM : GM() {
    override fun getName(): String = "simplerect"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(1f, 1f)
        val min = -20f
        val max = 800f
        val size = 20f
        val rand = SkRandom()
        val paint = SkPaint()
        repeat(10_000) {
            paint.color = colorToRGB565(rand.nextU() or (0xFF shl 24))
            val x = rand.nextRangeScalar(min, max)
            val y = rand.nextRangeScalar(min, max)
            val w = rand.nextRangeScalar(0f, size)
            val h = rand.nextRangeScalar(0f, size)
            c.drawRect(SkRect.MakeXYWH(x, y, w, h), paint)
        }
    }
}
