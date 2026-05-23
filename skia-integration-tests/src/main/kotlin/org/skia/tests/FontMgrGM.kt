package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkGraphics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTypeface
import org.skia.foundation.awt.RefDefault
import org.skia.tools.ToolUtils
import org.graphiks.math.SkScalar

/**
 * Port of upstream Skia's
 * [`gm/fontmgr.cpp::FontMgrGM`](https://github.com/google/skia/blob/main/gm/fontmgr.cpp)
 * (`DEF_GM(return new FontMgrGM;)`, name `fontmgr_iter`, 1536 × 768).
 *
 * Renders one row per font family discovered by the default
 * [SkFontMgr], then one column per style within each family. For
 * each style, the GM also probes
 * [SkFontMgr.matchFamilyStyleCharacter] with three sentinel
 * codepoints :
 *  - U+5203 with `bcp47 = "zh"` (Chinese variant)
 *  - U+5203 with `bcp47 = "ja"` (Japanese variant — same codepoint,
 *    distinct glyph in CJK fonts)
 *  - U+1F601 with no `bcp47` (Emoji fallback)
 *
 * **API gap vs upstream — `SkFontMgr::Request`** : upstream's
 * `drawCharacter` runs a third pass through `SkFontMgr::fallback`,
 * the variable-font-aware fallback API that takes an
 * `SkFontMgr::Request` carrying the requested variation axes.
 * Kanvas-skia's [SkFontMgr] surface exposes
 * [SkFontMgr.matchFamilyStyleCharacter] but not the `Request`-based
 * fallback ; we elide that third pass and rely on the first two
 * (which already exercise the same character-fallback codepath).
 *
 * **Font-set divergence** : the default JVM AWT font manager
 * enumerates system fonts (the JDK ships Dialog / Liberation Sans /
 * SansSerif / Serif on every platform we test on), not the
 * Liberation portable set upstream's `LiberationFontMgr` uses. The
 * matching `FontMgrTest` is therefore `@Disabled` — a pixel diff
 * against `fontmgr_iter.png` would fail at the family-enumeration
 * step regardless of glyph-level fidelity.
 */
public class FontMgrGM : GM() {

    override fun getName(): String = "fontmgr_iter"

    override fun getISize(): SkISize = SkISize.Make(1536, 768)

    override fun onOnceBeforeDraw() {
        // Upstream sets a 16 MiB font cache limit before draw. Our
        // SkGraphics implementation accepts the call and round-trips
        // the value (font cache is JVM-managed) — the call is kept
        // for API parity / direct-port reproducibility.
        SkGraphics.SetFontCacheLimit(16L * 1024L * 1024L)
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        var y: SkScalar = 20f
        val font = SkFont(ToolUtils.DefaultPortableTypeface()).apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
            isSubpixel = true
            size = 17f
        }

        val fm: SkFontMgr = SkFontMgr.RefDefault()
        val count = minOf(fm.countFamilies(), MAX_FAMILIES)
        if (count == 0) {
            // Upstream returns `DrawResult::kSkip` here ; we just no-op
            // (the empty canvas is fine — the cross-test will still
            // run and compare against the reference PNG).
            return
        }

        for (i in 0 until count) {
            val familyName = fm.getFamilyName(i)
            font.typeface = ToolUtils.DefaultPortableTypeface()
            drawString(canvas, familyName, 20f, y, font)

            var x: SkScalar = 220f

            val set: SkFontStyleSet = fm.createStyleSet(i)
            for (j in 0 until set.count()) {
                val styleNameBuf = StringBuilder()
                val fs: SkFontStyle = set.getStyle(j, null, styleNameBuf)
                val sname = "$styleNameBuf [${fs.weight} ${fs.width} ${fs.slant.ordinal}]"

                val styleTypeface = set.createTypeface(j) ?: continue
                font.typeface = styleTypeface
                x = drawString(canvas, sname, x, y, font) + 20f

                // Check that we get different glyphs in Chinese vs Japanese
                // and that emoji codepoints resolve through fallback.
                x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName, ZH, fs)
                x = drawCharacter(canvas, 0x5203, x, y, font, fm, familyName, JA, fs)
                x = drawCharacter(canvas, 0x1F601, x, y, font, fm, familyName, null, fs)
            }
            y += 24f
        }
    }

    public companion object {
        // Upstream `#define MAX_FAMILIES 30` — bound the draw time on
        // platforms with thousands of fonts.
        internal const val MAX_FAMILIES: Int = 30

        internal val ZH: Array<String> = arrayOf("zh")
        internal val JA: Array<String> = arrayOf("ja")

        /**
         * Mirrors upstream's `drawString(SkCanvas*, const SkString&,
         * SkScalar, SkScalar, const SkFont&)` helper — draws [text] at
         * `(x, y)` using [font] and returns the advance-relative
         * post-string x coordinate (`x + advance`).
         */
        internal fun drawString(
            canvas: SkCanvas, text: String, x: SkScalar, y: SkScalar, font: SkFont,
        ): SkScalar {
            canvas.drawString(text, x, y, font, SkPaint())
            return x + font.measureText(text)
        }

        /**
         * Mirrors upstream's
         * `drawCharacter(canvas, character, x, y, origFont, fm, fontName,
         *  bcp47, bcp47Count, fontStyle)`.
         *
         * Two passes :
         *  1. Resolve a typeface for [character] via
         *     [SkFontMgr.matchFamilyStyleCharacter], then render the
         *     single code-point string with it.
         *  2. If the first pass found a typeface, query its family
         *     name and re-resolve via
         *     [SkFontMgr.legacyMakeTypeface] — emulating the Blink
         *     pattern where the matched family name is re-used by
         *     subsequent shaping calls.
         *
         * The upstream third pass (`SkFontMgr::Request::fallback`) is
         * elided — kanvas-skia does not expose the `Request` API
         * (variable-font-aware fallback), and the first two passes
         * already exercise the same character-fallback codepath.
         */
        internal fun drawCharacter(
            canvas: SkCanvas,
            character: Int,
            xIn: SkScalar,
            y: SkScalar,
            origFont: SkFont,
            fm: SkFontMgr,
            fontName: String,
            bcp47: Array<String>?,
            fontStyle: SkFontStyle,
        ): SkScalar {
            var x = xIn
            val font = SkFont(origFont)
            // Single-codepoint UTF-16 string (Kotlin String is UTF-16
            // internally) — collapses to one upstream `SkUnichar`.
            val ch = String(Character.toChars(character))

            // Pass 1 — matchFamilyStyleCharacter.
            val tf1: SkTypeface? = fm.matchFamilyStyleCharacter(fontName, fontStyle, bcp47, character)
            font.typeface = tf1 ?: SkTypeface.MakeEmpty()
            x = drawString(canvas, ch, x, y, font) + 20f

            // Pass 2 — re-resolve by family name (Blink emulation).
            if (tf1 != null) {
                val familyName = tf1.getFamilyName()
                val tf2 = fm.legacyMakeTypeface(familyName, tf1.fontStyle)
                font.typeface = tf2 ?: SkTypeface.MakeEmpty()
                x = drawString(canvas, ch, x, y, font) + 20f
            }

            // Upstream pass 3 (SkFontMgr::Request::fallback) elided —
            // see class KDoc for rationale.
            return x
        }
    }
}
