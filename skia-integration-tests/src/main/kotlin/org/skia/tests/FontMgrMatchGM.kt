package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/fontmgr.cpp::FontMgrMatchGM` (640 × 1024).
 *
 * Stress-tests `SkFontMgr::matchFamilyStyle(name, style)` and
 * `matchFamilyStyleCharacter(...)` — walks every family × every
 * `SkFontStyle` combination and lays out the matched typeface
 * rendering "Hamburgefons" at a fixed size. Failures show up as
 * missing rows (no match) or wrong-style hits.
 *
 * **API gap** : same as [FontMgrGM] — depends on the upstream
 * `LiberationFontMgr` family layout for byte-stable comparison.
 * Also, `matchFamilyStyleCharacter` returns `null` on the default
 * `JvmAwtFontMgr` (no public AWT fallback API).
 */
public class FontMgrMatchGM : GM() {
    override fun getName(): String = "fontmgr_match"
    override fun getISize(): SkISize = SkISize.Make(640, 1024)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once portable Liberation SkFontMgr +
        //   fallback character matching land.
    }
}
