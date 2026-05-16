package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/skbug_8955.cpp`
 * ([`DEF_SIMPLE_GM(skbug_8955, canvas, 100, 100)`](https://github.com/google/skia/blob/main/gm/skbug_8955.cpp)).
 *
 * Regression test for an upstream blob-cache bug : drawing the *same*
 * [SkTextBlob] twice while the first draw is under a degenerate
 * `scale(0, 0)` transform (which generates no glyphs) used to leave
 * the blob marked as "no bitmap runs", preventing the second draw at
 * the identity matrix from rendering. The expected output is a single
 * `+` glyph at `(30, 60)` from the second `drawTextBlob` ; the first
 * draw collapses to nothing under the zero-scale CTM.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(skbug_8955, canvas, 100, 100) {
 *     SkPaint p;
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setSize(50);
 *     auto blob = SkTextBlob::MakeFromText("+", 1, font);
 *
 *     canvas->save();
 *     canvas->scale(0, 0);
 *     canvas->drawTextBlob(blob, 30, 60, p);
 *     canvas->restore();
 *     canvas->drawTextBlob(blob, 30, 60, p);
 * }
 * ```
 *
 * `:kanvas-skia` doesn't carry a `SkTextBlob::MakeFromText` factory ;
 * we reproduce its single-`allocRun` build via [ToolUtils.addToTextBlob],
 * which mirrors upstream's `add_to_text_blob` helper and is the same
 * pattern used by the other text-blob GM ports.
 */
public class Skbug8955GM : GM() {
    override fun getName(): String = "skbug_8955"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint()
        val font = ToolUtils.DefaultPortableFont().apply { size = 50f }

        // Mirror `SkTextBlob::MakeFromText("+", 1, font)`. The text is
        // a single ASCII codepoint so byteLength == codepoint count.
        val builder = SkTextBlobBuilder()
        ToolUtils.addToTextBlob(builder, "+", font, 0f, 0f)
        val blob: SkTextBlob = builder.make() ?: return

        // First draw under scale(0,0) — produces nothing visible but
        // is the buggy state that prevented the second draw upstream.
        c.save()
        c.scale(0f, 0f)
        c.drawTextBlob(blob, 30f, 60f, paint)
        c.restore()

        // Second draw at the identity CTM — should render "+" at (30, 60).
        c.drawTextBlob(blob, 30f, 60f, paint)
    }
}
