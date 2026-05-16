package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.utils.SkParsePath

/**
 * Port of Skia's `gm/crbug_691386.cpp::crbug_691386`.
 *
 * Originally a `DEF_SIMPLE_GM_CAN_FAIL` because `SkParsePath::FromSVGString`
 * can return `nullptr` on parse failure. Our [SkParsePath.FromSVGString]
 * mirrors the same behaviour (returns `null` on parse failure) — but the
 * fixture path here is a trivial `M / A / Z` arc which always parses.
 *
 * **Repro of [crbug.com/691386](https://crbug.com/691386)** : a tiny
 * unit-radius half-arc stroked at sub-pixel width, scaled up 96× and
 * centred. Used to crash a stroker assert under specific path / scale /
 * stroke combinations. Visually, the result is a thin black "smile"
 * arc traced along the top half of a 192×192 disc inside a 256×256
 * canvas.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_CAN_FAIL(crbug_691386, canvas, errorMsg, 256, 256) {
 *     if (auto path = SkParsePath::FromSVGString("M -1 0 A 1 1 0 0 0 1 0 Z")) {
 *         SkPaint p;
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setStrokeWidth(0.025f);
 *         canvas->scale(96.0f, 96.0f);
 *         canvas->translate(1.25f, 1.25f);
 *         canvas->drawPath(*path, p);
 *         return skiagm::DrawResult::kOk;
 *     } else {
 *         *errorMsg = "Failed to parse path.";
 *         return skiagm::DrawResult::kFail;
 *     }
 * }
 * ```
 */
public class Crbug691386GM : GM() {
    override fun getName(): String = "crbug_691386"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkParsePath.FromSVGString("M -1 0 A 1 1 0 0 0 1 0 Z") ?: return
        val p = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0.025f
        }
        c.scale(96.0f, 96.0f)
        c.translate(1.25f, 1.25f)
        c.drawPath(path, p)
    }
}
