package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkColor
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM_BG(blurs, …, 700, 500, 0xFFDDDDDD)`.
 *
 * Exercises all four named [SkBlurStyle] values plus the "NONE" sentinel (no
 * mask filter) on circles drawn at the compass positions of each style:
 *
 *  - NONE (no blur)  — origin
 *  - kInner          — left
 *  - kNormal         — down
 *  - kSolid          — up
 *  - kOuter          — right
 *
 * Then draws two copies of "Hamburgefons Style" in blurred black, with a
 * crisp white copy offset by (-2, -2) to produce a drop-shadow effect.
 *
 * The canvas starts with `translate(-40, 0)` matching the upstream shift.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG(blurs, canvas, 700, 500, 0xFFDDDDDD) {
 *     SkBlurStyle NONE = SkBlurStyle(-999);
 *     const struct { SkBlurStyle fStyle; int fCx, fCy; } gRecs[] = {
 *         { NONE,                0,  0 },
 *         { kInner_SkBlurStyle, -1,  0 },
 *         { kNormal_SkBlurStyle, 0,  1 },
 *         { kSolid_SkBlurStyle,  0, -1 },
 *         { kOuter_SkBlurStyle,  1,  0 },
 *     };
 *
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorBLUE);
 *
 *     canvas->translate(SkIntToScalar(-40), SkIntToScalar(0));
 *
 *     for (size_t i = 0; i < std::size(gRecs); i++) {
 *         if (gRecs[i].fStyle != NONE) {
 *             paint.setMaskFilter(SkMaskFilter::MakeBlur(gRecs[i].fStyle,
 *                                    SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(20))));
 *         } else {
 *             paint.setMaskFilter(nullptr);
 *         }
 *         canvas->drawCircle(SkIntToScalar(200 + gRecs[i].fCx*100),
 *                            SkIntToScalar(200 + gRecs[i].fCy*100),
 *                            SkIntToScalar(50), paint);
 *     }
 *     {
 *         SkFont font(ToolUtils::DefaultPortableTypeface(), 25);
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
 *                                    SkBlurMask::ConvertRadiusToSigma(SkIntToScalar(4))));
 *         SkScalar x = SkIntToScalar(70);
 *         SkScalar y = SkIntToScalar(400);
 *         paint.setColor(SK_ColorBLACK);
 *         canvas->drawString("Hamburgefons Style", x, y, font, paint);
 *         canvas->drawString("Hamburgefons Style", x, y + SkIntToScalar(50), font, paint);
 *         paint.setMaskFilter(nullptr);
 *         paint.setColor(SK_ColorWHITE);
 *         x -= SkIntToScalar(2);
 *         y -= SkIntToScalar(2);
 *         canvas->drawString("Hamburgefons Style", x, y, font, paint);
 *     }
 * }
 * ```
 */
public class BlursGM : GM() {

    init {
        setBGColor(0xFFDDDDDDu.toInt())
    }

    override fun getName(): String = "blurs"
    override fun getISize(): SkISize = SkISize.Make(700, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Matches SkBlurStyle(-999): sentinel "no blur"
        data class Rec(val style: SkBlurStyle?, val cx: Int, val cy: Int)

        val gRecs = listOf(
            Rec(null,                  0,  0),
            Rec(SkBlurStyle.kInner,   -1,  0),
            Rec(SkBlurStyle.kNormal,   0,  1),
            Rec(SkBlurStyle.kSolid,    0, -1),
            Rec(SkBlurStyle.kOuter,    1,  0),
        )

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
        }

        c.translate(-40f, 0f)

        for (rec in gRecs) {
            paint.maskFilter = if (rec.style != null) {
                SkBlurMaskFilter.Make(rec.style, convertRadiusToSigma(20f))
            } else {
                null
            }
            c.drawCircle(
                (200 + rec.cx * 100).toFloat(),
                (200 + rec.cy * 100).toFloat(),
                50f,
                paint,
            )
        }

        // Text section — blurred black + crisp white drop shadow.
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 25f)
        paint.maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, convertRadiusToSigma(4f))
        var x = 70f
        var y = 400f
        paint.color = SK_ColorBLACK
        c.drawString("Hamburgefons Style", x, y, font, paint)
        c.drawString("Hamburgefons Style", x, y + 50f, font, paint)

        paint.maskFilter = null
        paint.color = SK_ColorWHITE
        x -= 2f
        y -= 2f
        c.drawString("Hamburgefons Style", x, y, font, paint)
    }

    private companion object {
        /** Mirrors `SkBlurMask::ConvertRadiusToSigma`. */
        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
