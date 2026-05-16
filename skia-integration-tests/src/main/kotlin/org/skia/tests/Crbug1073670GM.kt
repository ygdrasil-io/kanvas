package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/crbug_1073670.cpp::crbug_1073670`.
 *
 * **Validates an end-to-end interaction not previously tested**: a
 * paint with a [SkLinearGradient] shader applied to text (via
 * [SkCanvas.drawString]). T3 wired text rendering through `drawPath`,
 * Phase 5 wired shader sampling through the rect/path rasteriser —
 * but until this GM, no test exercised the full chain
 * `drawString → drawPath → fillPath → shader.shadeRow` on glyph
 * outline pixels. This GM closes that gap.
 *
 * 250 × 250 canvas, single huge "Gradient" string (size 325) painted
 * with a vertical red→blue linear gradient. The glyph fill should
 * smoothly transition from red at the top of each letter to blue at
 * the bottom.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_1073670, canvas, 250, 250) {
 *     const SkPoint pts[] = {{0, 0}, {0, 250}};
 *     const SkColor4f colors[] = {{1,0,0,1}, {0,0,1,1}};
 *     auto sh = SkShaders::LinearGradient(pts, {{colors, {}, SkTileMode::kClamp}, {}});
 *     SkPaint p;
 *     p.setShader(sh);
 *
 *     SkFont f = ToolUtils::DefaultPortableFont();
 *     f.setSize(325);
 *     f.setEdging(SkFont::Edging::kAntiAlias);
 *
 *     canvas->drawString("Gradient", 10, 250, f, p);
 * }
 * ```
 */
public class Crbug1073670GM : GM() {
    override fun getName(): String = "crbug_1073670"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Vertical gradient: red at top (y=0), blue at bottom (y=250).
        // Upstream uses SkColor4f {1,0,0,1} / {0,0,1,1} — packed ARGB
        // 0xFFFF0000 / 0xFF0000FF respectively. Our SkLinearGradient.Make
        // takes IntArray packed ARGB.
        val gradient = SkLinearGradient.Make(
            p0 = SkPoint.Make(0f, 0f),
            p1 = SkPoint.Make(0f, 250f),
            colors = intArrayOf(SK_ColorRED, SK_ColorBLUE),
            positions = null,                // evenly spaced [0, 1]
            tileMode = SkTileMode.kClamp,
        )

        val paint = SkPaint().apply { shader = gradient }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 325f).apply {
            edging = SkFont.Edging.kAntiAlias
        }

        c.drawString("Gradient", 10f, 250f, font, paint)
    }
}
