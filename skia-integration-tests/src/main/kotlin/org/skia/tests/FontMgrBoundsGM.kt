package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/fontmgr.cpp::FontMgrBoundsGM` (1024 × 850).
 *
 * Walks every typeface in the default `SkFontMgr`, drawing each at a
 * range of sizes with both `font.getMetrics()` bounds and per-glyph
 * `font.getBounds(glyphs)` rectangles overlaid. The intent is to
 * audit the font scaler's reported bounds against the actually-drawn
 * glyph extents.
 *
 * **API gap** : `:kanvas-skia`'s default JVM AWT font manager
 * (`JvmAwtFontMgr`) does not enumerate the same font set as Skia's
 * upstream `LiberationFontMgr` (the reference was rendered with
 * Liberation fonts). Even with the same glyph metrics, the family
 * indexing differs, so a direct PNG diff would fail at the
 * font-selection step. Stub keeps the class registered ; the
 * associated test is `@Ignore`'d.
 */
public class FontMgrBoundsGM : GM() {
    override fun getName(): String = "fontmgr_bounds"
    override fun getISize(): SkISize = SkISize.Make(1024, 850)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once a portable Liberation-backed SkFontMgr is
        //   exposed (deferred R-suivi, see SkFontMgr KDoc).
    }
}
