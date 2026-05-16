package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/hsl.cpp::DEF_SIMPLE_GM(HSL_duck, canvas, 1110, 620)`.
 *
 * Renders the `ducky.png` resource through the four non-separable HSL
 * blend modes (Hue / Saturation / Color / Luminosity) over a 6-stop
 * gradient destination, twice — at `alpha = 1.0` and `alpha = 0.5`.
 * The labels above each column and the alpha label on the left come
 * from `ToolUtils::DefaultPortableFont()`.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(HSL_duck, canvas, 1110, 620) {
 *   auto src = ToolUtils::GetResourceAsImage("images/ducky.png");
 *   auto dst = make_grad(src->width());
 *   SkRect r = SkRect::MakeIWH(src->width(), src->height());
 *   canvas->translate(10, 50);
 *   canvas->scale(0.5f, 0.5f);
 *   const struct { SkBlendMode fMode; const char* fName; } recs[] = {
 *     {SkBlendMode::kHue, "Hue"}, {SkBlendMode::kSaturation, "Saturation"},
 *     {SkBlendMode::kColor, "Color"}, {SkBlendMode::kLuminosity, "Luminosity"},
 *   };
 *   SkFont font = ToolUtils::DefaultPortableFont();
 *   font.setSize(40);
 *   font.setEdging(SkFont::Edging::kAntiAlias);
 *   canvas->save();
 *   for (auto [_, name] : recs) {
 *     canvas->drawSimpleText(name, strlen(name), SkTextEncoding::kUTF8,
 *                            150, -20, font, SkPaint());
 *     canvas->translate(r.width() + 10, 0);
 *   }
 *   canvas->restore();
 *   for (SkScalar src_a : {1.0f, 0.5f}) {
 *     canvas->save();
 *     for (auto [mode, _] : recs) {
 *       SkPaint p; p.setShader(dst); canvas->drawRect(r, p);
 *       p.setShader(nullptr); p.setBlendMode(mode); p.setAlphaf(src_a);
 *       canvas->drawImageRect(src, r, SkSamplingOptions(), &p);
 *       canvas->translate(r.width() + 10, 0);
 *     }
 *     SkString str; str.printf("alpha %g", src_a);
 *     canvas->drawSimpleText(str.c_str(), str.size(), SkTextEncoding::kUTF8,
 *                            10, r.height()/2, font, SkPaint());
 *     canvas->restore();
 *     canvas->translate(0, r.height() + 10);
 *   }
 * }
 * ```
 *
 * `make_grad(width)` builds a 6-stop horizontal linear gradient with
 * colours `{0xFF00CCCC, 0xFF0000CC, 0xFFCC00CC, 0xFFCC0000, 0xFFCCCC00,
 * 0xFF00CC00}`, kClamp tile mode.
 */
public class HslGM : GM() {

    override fun getName(): String = "HSL_duck"

    override fun getISize(): SkISize = SkISize.Make(1110, 620)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val src = ToolUtils.GetResourceAsImage("images/ducky.png") ?: return

        val dst = makeGrad(src.width.toFloat())
        val r = SkRect.MakeIWH(src.width, src.height)

        c.translate(10f, 50f)
        c.scale(0.5f, 0.5f)

        val recs = arrayOf(
            SkBlendMode.kHue to "Hue",
            SkBlendMode.kSaturation to "Saturation",
            SkBlendMode.kColor to "Color",
            SkBlendMode.kLuminosity to "Luminosity",
        )

        val font = ToolUtils.DefaultPortableFont(size = 40f)

        // Column labels.
        c.save()
        for ((_, name) in recs) {
            c.drawSimpleText(
                name, name.length, SkTextEncoding.kUTF8,
                150f, -20f, font, SkPaint(),
            )
            c.translate(r.width() + 10f, 0f)
        }
        c.restore()

        for (srcA in floatArrayOf(1.0f, 0.5f)) {
            c.save()
            for ((mode, _) in recs) {
                // Background gradient destination.
                val bgPaint = SkPaint().apply { shader = dst }
                c.drawRect(r, bgPaint)

                // HSL-blend the duck image on top at `srcA` alpha.
                val srcPaint = SkPaint().apply {
                    blendMode = mode
                    alphaf = srcA
                }
                c.drawImageRect(src, r, r, paint = srcPaint)
                c.translate(r.width() + 10f, 0f)
            }
            val str = "alpha ${"%g".format(srcA)}"
            c.drawSimpleText(
                str, str.length, SkTextEncoding.kUTF8,
                10f, r.height() / 2f, font, SkPaint(),
            )
            c.restore()
            c.translate(0f, r.height() + 10f)
        }
    }

    private fun makeGrad(width: Float): SkShader {
        val colors = intArrayOf(
            0xFF00CCCC.toInt(),
            0xFF0000CC.toInt(),
            0xFFCC00CC.toInt(),
            0xFFCC0000.toInt(),
            0xFFCCCC00.toInt(),
            0xFF00CC00.toInt(),
        )
        return SkLinearGradient.Make(
            SkPoint(0f, 0f),
            SkPoint(width, 0f),
            colors,
            null,
            SkTileMode.kClamp,
        )
    }
}
