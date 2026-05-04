package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorCYAN
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorLTGRAY
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/colorwheel.cpp::colorwheelnative`.
 *
 * Smaller and more diverse than [BigTextGM] — exercises:
 *  - `ToolUtils.CreatePortableTypeface("sans-serif", Bold)` → Liberation
 *    Sans Bold via [org.skia.foundation.awt.LiberationFontMgr.matchFamilyStyle].
 *  - `font.setEdging(SkFont.Edging.kAlias)` → **non-AA text**, a code
 *    path different from the AA bigtext path.
 *  - 7 distinct colours via 7 paints (R, G, B, C, M, Y, K) on a single
 *    canvas with a non-white background (`SK_ColorLTGRAY`) — exercises
 *    `paint.color` propagation through `drawString` → `drawPath`.
 *  - Small canvas (128 × 28 = 3584 px) → tight pixel budget, low
 *    headroom for AA-edge slack.
 *  - 18 pt font size (typical UI label scale) — different glyph cache
 *    bucket from bigtext's 1500 pt extreme.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(colorwheelnative, canvas, 128, 28) {
 *     SkFont font(ToolUtils::CreatePortableTypeface("sans-serif", SkFontStyle::Bold()), 18);
 *     font.setEdging(SkFont::Edging::kAlias);
 *
 *     canvas->clear(SK_ColorLTGRAY);
 *     canvas->drawString("R", 8.0f,   20.0f, font, SkPaint(SkColors::kRed));
 *     canvas->drawString("G", 24.0f,  20.0f, font, SkPaint(SkColors::kGreen));
 *     canvas->drawString("B", 40.0f,  20.0f, font, SkPaint(SkColors::kBlue));
 *     canvas->drawString("C", 56.0f,  20.0f, font, SkPaint(SkColors::kCyan));
 *     canvas->drawString("M", 72.0f,  20.0f, font, SkPaint(SkColors::kMagenta));
 *     canvas->drawString("Y", 88.0f,  20.0f, font, SkPaint(SkColors::kYellow));
 *     canvas->drawString("K", 104.0f, 20.0f, font, SkPaint(SkColors::kBlack));
 * }
 * ```
 */
public class ColorWheelNativeGM : GM() {

    override fun getName(): String = "colorwheelnative"
    override fun getISize(): SkISize = SkISize.Make(128, 28)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val font = SkFont(
            ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold()),
            18f,
        ).apply { edging = SkFont.Edging.kAlias }

        c.clear(SK_ColorLTGRAY)
        c.drawString("R",   8.0f, 20.0f, font, SkPaint(SK_ColorRED))
        c.drawString("G",  24.0f, 20.0f, font, SkPaint(SK_ColorGREEN))
        c.drawString("B",  40.0f, 20.0f, font, SkPaint(SK_ColorBLUE))
        c.drawString("C",  56.0f, 20.0f, font, SkPaint(SK_ColorCYAN))
        c.drawString("M",  72.0f, 20.0f, font, SkPaint(SK_ColorMAGENTA))
        c.drawString("Y",  88.0f, 20.0f, font, SkPaint(SK_ColorYELLOW))
        c.drawString("K", 104.0f, 20.0f, font, SkPaint(SK_ColorBLACK))
    }
}
