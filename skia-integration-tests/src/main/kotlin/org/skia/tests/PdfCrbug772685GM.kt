package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/pdf_never_embed.cpp::pdf_crbug_772685`
 * (`DEF_SIMPLE_GM`, 612 × 792).
 *
 * Regression test for https://crbug.com/772685 — a PDF backend bug
 * that produced an incorrect clip when a `translate(-571, 0)` was
 * followed by a `scale(0.75, 0.75)` and a second clip was applied.
 * The draw should produce a completely white canvas (the `drawRect`
 * is fully covered by the two intersected clips). Any non-white pixel
 * indicates the clip math regressed.
 *
 * C++ original (`gm/pdf_never_embed.cpp:75-82`):
 * ```cpp
 * DEF_SIMPLE_GM(pdf_crbug_772685, canvas, 612, 792) {
 *     canvas->clipRect({-1, -1, 613, 793}, false);
 *     canvas->translate(-571, 0);
 *     canvas->scale(0.75, 0.75);
 *     canvas->clipRect({-1, -1, 613, 793}, false);
 *     canvas->translate(0, -816);
 *     canvas->drawRect({0, 0, 1224, 1500}, SkPaint());
 * }
 * ```
 *
 * All operations used (`clipRect`, `translate`, `scale`, `drawRect`)
 * are fully supported in `:kanvas-skia`. No font or resource
 * dependencies.
 */
public class PdfCrbug772685GM : GM() {

    override fun getName(): String = "pdf_crbug_772685"
    override fun getISize(): SkISize = SkISize.Make(612, 792)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clipRect(SkRect.MakeLTRB(-1f, -1f, 613f, 793f), doAntiAlias = false)
        c.translate(-571f, 0f)
        c.scale(0.75f, 0.75f)
        c.clipRect(SkRect.MakeLTRB(-1f, -1f, 613f, 793f), doAntiAlias = false)
        c.translate(0f, -816f)
        c.drawRect(SkRect.MakeLTRB(0f, 0f, 1224f, 1500f), SkPaint())
    }
}
