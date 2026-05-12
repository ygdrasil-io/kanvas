package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontHinting
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/fontscaler.cpp::FontScalerGM` (1450 × 750).
 *
 * Sweeps "Hamburgefons ooo mmm" at sizes 6..22 in five rotated /
 * translated columns, twice (the second pass enables subpixel,
 * linear metrics, and disables baseline-snap). On the GPU pipeline
 * this exercised path-vs-mask scaler selection across many size
 * buckets ; the raster pipeline simply scales each glyph's outline
 * through `drawPath` so the second pass differs by sub-pixel
 * baseline positioning only.
 *
 * C++ original:
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setEdging(SkFont::Edging::kSubpixelAntiAlias);
 *     //With freetype the default (normal hinting) can be really ugly.
 *     //Most distros now set slight (vertical hinting only) in any event.
 *     font.setHinting(SkFontHinting::kSlight);
 *
 *     const char* text = "Hamburgefons ooo mmm";
 *     const size_t textLen = strlen(text);
 *
 *     for (int j = 0; j < 2; ++j) {
 *         // This used to do 6 iterations but it causes the N4 to crash in the MSAA4 config.
 *         for (int i = 0; i < 5; ++i) {
 *             SkScalar x = 10;
 *             SkScalar y = 20;
 *
 *             SkAutoCanvasRestore acr(canvas, true);
 *             canvas->translate(SkIntToScalar(50 + i * 230),
 *                               SkIntToScalar(20));
 *             canvas->rotate(SkIntToScalar(i * 5), x, y * 10);
 *
 *             {
 *                 SkPaint p;
 *                 p.setAntiAlias(true);
 *                 SkRect r;
 *                 r.setLTRB(x - 3, 15, x - 1, 280);
 *                 canvas->drawRect(r, p);
 *             }
 *
 *             for (int ps = 6; ps <= 22; ps++) {
 *                 font.setSize(SkIntToScalar(ps));
 *                 canvas->drawSimpleText(text, textLen, SkTextEncoding::kUTF8, x, y, font, SkPaint());
 *                 y += font.getMetrics(nullptr);
 *             }
 *         }
 *         canvas->translate(0, SkIntToScalar(360));
 *         font.setSubpixel(true);
 *         font.setLinearMetrics(true);
 *         font.setBaselineSnap(false);
 *     }
 * }
 * ```
 *
 * **kanvas-skia notes** :
 *  - `font.getMetrics(nullptr)` upstream returns the recommended line
 *    spacing ; we use [SkFont.getSpacing] which is the same value.
 *  - `kSubpixelAntiAlias` is silently downgraded to `kAntiAlias`
 *    (no LCD pixel pipeline on raster).
 */
public class FontScalerGM : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "fontscaler"
    override fun getISize(): SkISize = SkISize.Make(1450, 750)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
            hinting = SkFontHinting.kSlight
        }

        val text = "Hamburgefons ooo mmm"
        val textLen = text.length

        val metrics = SkFontMetrics()

        for (j in 0 until 2) {
            for (i in 0 until 5) {
                val x = 10f
                var y = 20f

                c.save()
                c.translate((50 + i * 230).toFloat(), 20f)
                c.rotate((i * 5).toFloat(), x, y * 10f)

                run {
                    val p = SkPaint().apply { isAntiAlias = true }
                    val r = SkRect.MakeLTRB(x - 3f, 15f, x - 1f, 280f)
                    c.drawRect(r, p)
                }

                var ps = 6
                while (ps <= 22) {
                    font.size = ps.toFloat()
                    c.drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, SkPaint())
                    // `font.getMetrics(nullptr)` returns the spacing.
                    y += font.getMetrics(metrics)
                    ps++
                }

                c.restore()
            }
            c.translate(0f, 360f)
            font.isSubpixel = true
            font.isLinearMetrics = true
            font.isBaselineSnap = false
        }
    }
}
