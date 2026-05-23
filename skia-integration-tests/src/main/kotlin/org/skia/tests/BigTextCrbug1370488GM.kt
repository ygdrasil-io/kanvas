package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bigtext.cpp::DEF_SIMPLE_GM(bigtext_crbug_1370488, canvas, 512, 512)`.
 *
 * Regression test for crbug.com/1370488: when the glyph is large enough that
 * Skia/DirectWrite should draw it from a path, DirectWrite sometimes reported
 * empty bounds — causing the glyph to be discarded rather than rendered.  The
 * fix ensures the path-from-outline route is taken for any glyph whose
 * `DirectWrite` scaler returns empty bounds.
 *
 * Upstream draws a private-use Unicode character `U+F021` (0xEF 0x80 0xA1 in
 * UTF-8) from `fonts/SpiderSymbol.ttf` at a nominal 12 pt but scaled by
 * `437.5×437.5` — an effective ~5250 pt, well beyond the path-rasterisation
 * threshold — to trigger the exact bug.
 *
 * `:kanvas-skia` does **not** ship `SpiderSymbol.ttf` (it is a specialised
 * test font for a Windows-only DirectWrite code path). When the resource is
 * absent [ToolUtils.CreateTypefaceFromResource] returns `null` and the code
 * mirrors upstream's explicit fallback:
 *
 * ```cpp
 * if (!typeface) {
 *     typeface = ToolUtils::DefaultPortableTypeface();
 *     text = "H";
 * }
 * ```
 *
 * The fallback renders an `"H"` glyph from Liberation Sans at the same
 * extreme scale, which still exercises the large-glyph path-rendering
 * pipeline.  Pixel-level output differs from the upstream reference (which
 * uses SpiderSymbol) so this test is classified **LAZY_PORT**: the GM body
 * is fully ported, similarity tracking is active, but the floor is set
 * conservatively because we render a different glyph.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(bigtext_crbug_1370488, canvas, 512, 512) {
 *     auto typeface = ToolUtils::CreateTypefaceFromResource("fonts/SpiderSymbol.ttf");
 *     const char* text = "\xEF\x80\xA1";
 *     if (!typeface) {
 *         typeface = ToolUtils::DefaultPortableTypeface();
 *         text = "H";
 *     }
 *
 *     SkFont font(typeface, 12.f);
 *     canvas->translate(-1800.f, 1800.f);
 *     canvas->scale(437.5f, 437.5f);
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     canvas->drawString(text, 0.f, 0.f, font, paint);
 * }
 * ```
 */
public class BigTextCrbug1370488GM : GM() {

    override fun getName(): String = "bigtext_crbug_1370488"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Try to load the specialised SpiderSymbol font used in the upstream
        // repro.  When the resource is absent (which it is in :kanvas-skia),
        // fall back to Liberation Sans + "H" — identical to upstream's own
        // null-typeface guard.
        val typeface = ToolUtils.CreateTypefaceFromResource("fonts/SpiderSymbol.ttf")
        val text: String = if (typeface != null) "" else "H"
        val resolvedTypeface = typeface ?: ToolUtils.DefaultPortableTypeface()

        val font = SkFont(resolvedTypeface, 12f)
        c.translate(-1800f, 1800f)
        c.scale(437.5f, 437.5f)
        val paint = SkPaint().apply { isAntiAlias = true }
        c.drawString(text, 0f, 0f, font, paint)
    }
}
