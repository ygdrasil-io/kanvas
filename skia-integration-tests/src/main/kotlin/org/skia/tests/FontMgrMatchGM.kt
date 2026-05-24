package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkGraphics
import org.skia.foundation.awt.RefDefault
import org.skia.tools.ToolUtils
import org.graphiks.math.SkScalar

/**
 * Port of upstream Skia's
 * [`gm/fontmgr.cpp::FontMgrMatchGM`](https://github.com/google/skia/blob/main/gm/fontmgr.cpp)
 * (`DEF_GM(return new FontMgrMatchGM;)`, name `fontmgr_match`,
 * 640 × 1024).
 *
 * Two-column layout :
 *  - **left column (`exploreFamily`)** — sweeps the 25-cell
 *    `weight × width` grid (5 weights × 5 widths) with
 *    `SkFontStyle::kUpright_Slant`, calling
 *    [SkFontStyleSet.matchStyle] for each. Skips cells that return
 *    `null` (no matching face).
 *  - **right column (`iterateFamily`)** — walks the set's
 *    [SkFontStyleSet.count] styles, drawing each typeface's style
 *    label + the two CJK probe codepoints `U+5203` with `bcp47 =
 *    "zh"` / `"ja"`.
 *
 * The family is the first one of `Helvetica Neue / Arial / sans /
 * Roboto` that produces a non-empty style set ; on the JVM AWT
 * backend the lookup typically lands on `Arial` or `Helvetica`
 * depending on the JDK distribution.
 *
 * **Font-set divergence** : same caveat as [FontMgrGM] — the
 * default JVM font manager enumerates system fonts, not upstream's
 * Liberation portable set. The matching test is `@Disabled` for
 * cross-platform stability.
 *
 * **API gap vs upstream — `SkFontMgr::Request`** : same as
 * [FontMgrGM] — the third `drawCharacter` pass is elided.
 */
public class FontMgrMatchGM : GM() {

    override fun getName(): String = "fontmgr_match"

    override fun getISize(): SkISize = SkISize.Make(640, 1024)

    override fun onOnceBeforeDraw() {
        SkGraphics.SetFontCacheLimit(16L * 1024L * 1024L)
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        val font = SkFont(ToolUtils.DefaultPortableTypeface()).apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
            isSubpixel = true
            size = 17f
        }
        val fm: SkFontMgr = SkFontMgr.RefDefault()

        var fset: SkFontStyleSet? = null
        for (name in CANDIDATE_FAMILIES) {
            val candidate = fm.matchFamily(name)
            if (candidate.count() > 0) {
                fset = candidate
                break
            }
        }
        if (fset == null || fset.count() == 0) {
            // Upstream returns `DrawResult::kSkip` here ; we no-op.
            return
        }

        canvas.translate(20f, 40f)
        exploreFamily(canvas, font, fset, fm)
        canvas.translate(350f, 0f)
        iterateFamily(canvas, font, fset, fm)
    }

    /**
     * Mirrors upstream's `iterateFamily(canvas, font, fset)` — for
     * every style in [fset], draws the style label + the two CJK
     * probes (`zh` / `ja` codepoints) on a single row, then advances
     * the row baseline by 24 px.
     */
    private fun iterateFamily(
        canvas: SkCanvas,
        font: SkFont,
        fset: SkFontStyleSet,
        fm: SkFontMgr,
    ) {
        val f = SkFont(font)
        var y: SkScalar = 0f

        for (j in 0 until fset.count()) {
            val styleNameBuf = StringBuilder()
            val fs: SkFontStyle = fset.getStyle(j, null, styleNameBuf)
            val sname = "$styleNameBuf [${fs.weight} ${fs.width}]"

            val face = fset.createTypeface(j) ?: continue
            f.typeface = face
            var x: SkScalar = 0f
            x = FontMgrGM.drawString(canvas, sname, x, y, f) + 20f
            // CJK probes — anonymous family (upstream passes `nullptr`)
            // so the fallback chain is the only resolution path.
            x = FontMgrGM.drawCharacter(canvas, 0x5203, x, y, font, fm, "", FontMgrGM.ZH, fs)
            @Suppress("UNUSED_VALUE")
            x = FontMgrGM.drawCharacter(canvas, 0x5203, x, y, font, fm, "", FontMgrGM.JA, fs)
            y += 24f
        }
    }

    /**
     * Mirrors upstream's `exploreFamily(canvas, font, fset)` — sweeps
     * a `weight × width` grid (5 × 5 cells) with upright slant,
     * calling [SkFontStyleSet.matchStyle] to resolve the closest
     * face. Cells that resolve are rendered as
     * `"request [w wd]"` + the two CJK probes ; cells that miss are
     * skipped (no row emitted).
     */
    private fun exploreFamily(
        canvas: SkCanvas,
        font: SkFont,
        fset: SkFontStyleSet,
        fm: SkFontMgr,
    ) {
        val f = SkFont(font)
        var y: SkScalar = 0f
        var weight = 100
        while (weight <= 900) {
            var width = 1
            while (width <= 9) {
                val fs = SkFontStyle(weight, width, SkFontStyle.Slant.kUpright_Slant)
                val face = fset.matchStyle(fs)
                if (face != null) {
                    f.typeface = face
                    val label = "request [${fs.weight} ${fs.width}]"
                    var x: SkScalar = 0f
                    x = FontMgrGM.drawString(canvas, label, x, y, f) + 20f
                    x = FontMgrGM.drawCharacter(canvas, 0x5203, x, y, font, fm, "", FontMgrGM.ZH, fs)
                    @Suppress("UNUSED_VALUE")
                    x = FontMgrGM.drawCharacter(canvas, 0x5203, x, y, font, fm, "", FontMgrGM.JA, fs)
                    y += 24f
                }
                width += 2
            }
            weight += 200
        }
    }

    public companion object {
        /**
         * Upstream's hard-coded family probe list (`gNames[]`) —
         * tries Helvetica Neue / Arial / "sans" / Roboto in order ;
         * the first non-empty match wins. On JVM AWT the resolution
         * typically lands on Arial (Windows / Linux JDK) or
         * Helvetica (macOS JDK).
         */
        internal val CANDIDATE_FAMILIES: Array<String> = arrayOf(
            "Helvetica Neue", "Arial", "sans", "Roboto",
        )
    }
}
