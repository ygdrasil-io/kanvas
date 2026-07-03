package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/stroketext.cpp::stroketext_native`
 * (`DEF_SIMPLE_GM_CAN_FAIL`, 650 × 420).
 *
 * Exercises native outline-stroking on three groups of typefaces:
 *
 * 1. **TTF** (`fonts/Stroking.ttf`) — TrueType contours with explicit
 *    points characterising every degenerate-stroke edge case: nothing,
 *    something, empty quads, implicit quads, explicit close, etc.
 * 2. **OTF** (`fonts/Stroking.otf`) — CFF/Type 2 equivalents (moveto,
 *    lineto, cubicto, flex sequences).
 * 3. **Overlap** — `fonts/Variable.ttf` loaded through
 *    `ToolUtils.TestFontMgr().makeFromStream(stream, args)` at a
 *    specific `wght = 721` axis value, to exercise the overlap-flag
 *    glyph codepath (`U+74 t` has it, `U+167 ŧ` does not).
 *
 * All three typefaces are drawn at 100 pt with a 10 px round-cap/join
 * stroke, cycling through degenerate-glyph codepoints from the Stroking
 * fonts' custom encoding (U+25CB / U+25C9 / super- and sub-script digits).
 *
 * **Blocker** : the variable-font path calls `ToolUtils.TestFontMgr()`,
 * which throws `TODO("STUB.LIBERATION_FM: …")` — a public [SkFontMgr]
 * backed by the Liberation TTFs with full `makeFromStream` + variation
 * axis support has not yet been wired up in `:kanvas-skia`. Additionally,
 * `fonts/Stroking.ttf`, `fonts/Stroking.otf`, and `fonts/Variable.ttf`
 * are not yet bundled as classpath resources in this module.
 *
 * [StrokeTextNativeTest] is therefore `@Disabled("STUB.LIBERATION_FM: …")`.
 *
 * See `API_FINALIZATION_PLAN.md` § STUB.LIBERATION_FM.
 */
public class StrokeTextNativeGM : GM() {

    override fun getName(): String = "stroketext_native"
    override fun getISize(): SkISize = SkISize.Make(650, 420)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeJoin = SkPaint.Join.kRound_Join
            setARGB(0xff, 0xbb, 0x00, 0x00)
        }

        // TTF branch — Stroking.ttf exercises TrueType degenerate contours.
        val ttf = ToolUtils.CreateTypefaceFromResource("fonts/Stroking.ttf")
        if (ttf != null) {
            val font = SkFont(ttf, 100f)
            c.drawString("○◉  ⁻₋⁺₊", 10f, 100f, font, p)
            c.drawString("⁰₀¹₁²₂³₃", 10f, 200f, font, p)
        }

        // OTF branch — Stroking.otf exercises CFF/Type2 degenerate paths.
        val otf = ToolUtils.CreateTypefaceFromResource("fonts/Stroking.otf")
        if (otf != null) {
            val font = SkFont(otf, 100f)
            c.drawString("○◉  ⁰¹³ᶠ", 10f, 300f, font, p)
        }

        // Overlap branch: pure Kotlin variable-font load + clone path.
        // If the bundled variable fixture is missing or invalid, skip only
        // this branch while keeping the TTF/OTF subset deterministic.
        val overlapStroke = p.copy().apply { strokeWidth = 1f }
        val overlapTypeface = StrokeTextNativeGM::class.java.classLoader
            .getResourceAsStream("fonts/Variable.ttf")
            ?.use { stream -> ToolUtils.TestFontMgr().makeFromStream(stream) }
            ?.makeClone(
                SkFontArguments().setVariationDesignPosition(
                    SkFontArguments.VariationPosition(
                        listOf(
                            SkFontArguments.VariationPosition.Coordinate.of(SkFontVariation.WEIGHT, 721f),
                        ),
                    ),
                ),
            )
            ?: ToolUtils.DefaultPortableTypeface()
        val overlapFont = SkFont(overlapTypeface, 100f)
        c.drawString("tŧ", 10f, 400f, overlapFont, overlapStroke)
    }
}
