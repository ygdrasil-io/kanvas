package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bigtext.cpp` (`BigTextGM`).
 *
 * **First textual GM port** — exercises the T1+T2+T3+T4 stack
 * end-to-end:
 *  - T1: `SkFont` ctor, `SkTextEncoding`.
 *  - T2: `SkFont.measureText` with `bounds` out-param to centre the glyph.
 *  - T3: `SkCanvas.drawSimpleText` rendering OpenType glyph outlines
 *        as `SkPath` data through the existing scanline-fill pipeline.
 *  - T4: `ToolUtils.DefaultPortableTypeface` resolves to Liberation
 *        Sans Regular (matching upstream's `gDefaultFontIndex = 4`).
 *
 * The GM is small (640 × 480) but exercises a stress-test condition:
 * **font size 1500pt**, which forces both the pure Kotlin OpenType
 * backend and upstream FreeType to take a path-fill route (no glyph
 * cache hit, no bitmap mask). The remaining divergence is scaler /
 * hinting details plus AA scanline-fill.
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("bigtext"); }
 * SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     SkFont font(ToolUtils::DefaultPortableTypeface(), 1500);
 *
 *     SkRect r;
 *     (void)font.measureText("/", 1, SkTextEncoding::kUTF8, &r);
 *     SkPoint pos = {
 *         this->width()/2 - r.centerX(),
 *         this->height()/2 - r.centerY()
 *     };
 *
 *     paint.setColor(SK_ColorRED);
 *     canvas->drawSimpleText("/", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
 *
 *     paint.setColor(SK_ColorBLUE);
 *     canvas->drawSimpleText("\\", 1, SkTextEncoding::kUTF8, pos.fX, pos.fY, font, paint);
 * }
 * ```
 */
public class BigTextGM : GM() {
    override fun getName(): String = "bigtext"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 1500f)

        // Visual-bounds out-param: gives us the glyph's bbox in source
        // coords so we can centre it on the canvas.
        val r = SkRect.MakeEmpty()
        font.measureText("/", 1, SkTextEncoding.kUTF8, r)

        val w = size().width
        val h = size().height
        val posX = w / 2f - r.centerX()
        val posY = h / 2f - r.centerY()

        paint.color = SK_ColorRED
        c.drawSimpleText("/", 1, SkTextEncoding.kUTF8, posX, posY, font, paint)

        paint.color = SK_ColorBLUE
        c.drawSimpleText("\\", 1, SkTextEncoding.kUTF8, posX, posY, font, paint)
    }
}
