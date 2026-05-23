package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkTypeface_Fontations
import org.skia.foundation.stream.SkMemoryStream
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/fontations.cpp` (`FontationsTypefaceGM`).
 *
 * Upstream registers **three** GM instances via `DEF_GM`, each exercising
 * a different combination of font file and `fvar` variation position:
 *
 *  1. `typeface_fontations_roboto` — Roboto-Regular, no variation axes.
 *  2. `typeface_fontations_distortable_light` — Distortable.ttf,
 *     `wght = 0.5`, constructed via `SkTypeface_Make_Fontations` with
 *     the variation baked into the `SkFontArguments` at load time
 *     (`TypefaceConstruction::kMakeWithFontArguments`).
 *  3. `typeface_fontations_distortable_bold` — Distortable.ttf,
 *     `wght = 2.0`, loaded at default variation then cloned with
 *     `SkTypeface::makeClone(args)` (`TypefaceConstruction::kCloneWithFontArguments`).
 *
 * Each GM draws the string `"abc"` at four sizes (12 / 18 / 30 / 120 pt)
 * with a green 2 × 2 px origin marker, then dumps metadata (glyph
 * count, localised family name(s), family / PostScript names) via a
 * report typeface (Roboto-Regular at 20 pt) below the text column.
 *
 * ## Port status — **INTRACTABLE** (bucket: INTRACTABLE)
 *
 * Two stacked blockers prevent this GM from running today:
 *
 *  1. **`STUB.FONTATIONS`** — [SkTypeface_Fontations.MakeFromStream] always
 *     throws [NotImplementedError]. Rendering requires the Fontations Rust
 *     crate wired via UniFFI or JNI, both out of scope for the pure-JVM
 *     `:kanvas-skia` module.
 *  2. **`STUB.FONTATIONS`** — [SkTypeface.getPostScriptName] and
 *     [SkTypeface.createFamilyNameIterator] also throw, because they
 *     require raw OpenType name-table access unavailable in the AWT backend.
 *
 * The [onDraw] body calls these stubs so the class stays compile-pinned
 * to the full [SkTypeface_Fontations] and [SkTypeface] API surface.
 *
 * All three companion factory GMs map to `@Disabled` test methods in
 * [FontationsTest]. See [SkTypeface_Fontations] KDoc and
 * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md)
 * § STUB.FONTATIONS.
 */
public open class FontationsGM(
    private val testName: String,
    private val testFontFilename: String,
    private val specifiedVariations: List<SkFontArguments.VariationPosition.Coordinate>,
    private val construction: TypefaceConstruction = TypefaceConstruction.kMakeWithFontArguments,
) : GM() {

    public enum class TypefaceConstruction {
        kMakeWithFontArguments,
        kCloneWithFontArguments,
    }

    private var testTypeface: SkTypeface? = null
    private var reportTypeface: SkTypeface? = null

    init {
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "typeface_fontations_$testName"
    override fun getISize(): SkISize = SkISize.Make(400, 200)

    override fun onOnceBeforeDraw() {
        val pos = SkFontArguments.VariationPosition(specifiedVariations)
        val args = SkFontArguments().setVariationDesignPosition(pos)

        // Touch STUB.FONTATIONS — both branches throw at runtime.
        testTypeface = when (construction) {
            TypefaceConstruction.kMakeWithFontArguments -> {
                val bytes = ToolUtils.GetResourceAsData(testFontFilename)?.toByteArray()
                    ?: ByteArray(0)
                SkTypeface_Fontations.MakeFromStream(SkMemoryStream(bytes), args)
            }
            TypefaceConstruction.kCloneWithFontArguments -> {
                val bytes = ToolUtils.GetResourceAsData(testFontFilename)?.toByteArray()
                    ?: ByteArray(0)
                SkTypeface_Fontations.MakeFromStream(SkMemoryStream(bytes), SkFontArguments())
                    .makeClone(args)
            }
        }

        val reportBytes = ToolUtils.GetResourceAsData(REPORT_FONT_NAME)?.toByteArray()
            ?: ByteArray(0)
        reportTypeface = SkTypeface_Fontations.MakeFromStream(
            SkMemoryStream(reportBytes), SkFontArguments(),
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val tf = testTypeface ?: return

        val paint = SkPaint().apply { color = SK_ColorBLACK }
        val font = SkFont(tf)
        val metrics = SkFontMetrics()
        val testText = "abc"
        val x = 100f
        var y = 150f

        for (textSize in TEXT_SIZES) {
            font.size = textSize
            y += font.getSpacing()

            // Green 2 × 2 origin marker, mirroring upstream's
            // `canvas->drawRect(SkRect::MakeXYWH(x, y, 2, 2), greenPaint)`.
            paint.color = SK_ColorGREEN
            c.drawRect(SkRect.MakeXYWH(x, y, 2f, 2f), paint)
            paint.color = SK_ColorBLACK

            c.drawSimpleText(testText, testText.length, SkTextEncoding.kUTF8, x, y, font, paint)
        }

        c.translate(100f, 470f)
        dumpGlyphCount(c, tf, reportTypeface)
        c.translate(0f, DUMP_FONT_SIZE * 1.2f)
        dumpLocalizedStrings(c, tf, reportTypeface)
        c.translate(0f, DUMP_FONT_SIZE * 1.2f)
        dumpFamilyAndPostscriptName(c, tf, reportTypeface)
    }

    // ─── dump helpers — mirror upstream's anonymous-namespace helpers ──────

    private fun dumpToCanvas(canvas: SkCanvas, text: String, reportTf: SkTypeface?) {
        if (reportTf == null) return
        canvas.drawSimpleText(
            text, text.length, SkTextEncoding.kUTF8,
            0f, 0f,
            SkFont(reportTf, DUMP_FONT_SIZE),
            SkPaint(),
        )
    }

    private fun dumpGlyphCount(canvas: SkCanvas, typeface: SkTypeface, reportTf: SkTypeface?) {
        // Mirrors upstream's `dumpGlyphCount` helper.
        dumpToCanvas(canvas, "Num glyphs: ${typeface.countGlyphs()}\n", reportTf)
    }

    private fun dumpLocalizedStrings(canvas: SkCanvas, typeface: SkTypeface, reportTf: SkTypeface?) {
        // Mirrors upstream's `dumpLocalizedStrings`: iterates all
        // localised family names from the OpenType name table.
        // Throws STUB.FONTATIONS at runtime.
        val iter = typeface.createFamilyNameIterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            dumpToCanvas(
                canvas,
                "Name: ${entry.fString} Language: ${entry.fLanguage}\n",
                reportTf,
            )
            canvas.translate(0f, DUMP_FONT_SIZE * 1.2f)
        }
    }

    private fun dumpFamilyAndPostscriptName(
        canvas: SkCanvas,
        typeface: SkTypeface,
        reportTf: SkTypeface?,
    ) {
        // Mirrors upstream's `dumpFamilyAndPostscriptName`.
        val familyName = typeface.getFamilyName()
        dumpToCanvas(canvas, "Family name: $familyName\n", reportTf)

        canvas.translate(0f, DUMP_FONT_SIZE * 1.2f)
        // getPostScriptName() throws STUB.FONTATIONS at runtime.
        val psName = typeface.getPostScriptName()
        if (psName != null) {
            dumpToCanvas(canvas, "PS Name: $psName\n", reportTf)
        } else {
            dumpToCanvas(canvas, "No Postscript name.", reportTf)
        }
    }

    public companion object {
        private val TEXT_SIZES = floatArrayOf(12f, 18f, 30f, 120f)
        private const val DUMP_FONT_SIZE = 20f
        private const val REPORT_FONT_NAME = "fonts/Roboto-Regular.ttf"

        // ─── DEF_GM equivalents (mirrors the three DEF_GM registrations) ──

        /** `typeface_fontations_roboto` — Roboto-Regular, no variation. */
        public fun makeRoboto(): FontationsGM =
            FontationsGM("roboto", "fonts/Roboto-Regular.ttf", emptyList())

        /** `typeface_fontations_distortable_light` — wght=0.5, kMakeWithFontArguments. */
        public fun makeDistortableLight(): FontationsGM =
            FontationsGM(
                "distortable_light",
                "fonts/Distortable.ttf",
                listOf(
                    SkFontArguments.VariationPosition.Coordinate.of(
                        SkFontVariation.Tag.of("wght"), 0.5f,
                    ),
                ),
                TypefaceConstruction.kMakeWithFontArguments,
            )

        /** `typeface_fontations_distortable_bold` — wght=2.0, kCloneWithFontArguments. */
        public fun makeDistortableBold(): FontationsGM =
            FontationsGM(
                "distortable_bold",
                "fonts/Distortable.ttf",
                listOf(
                    SkFontArguments.VariationPosition.Coordinate.of(
                        SkFontVariation.Tag.of("wght"), 2.0f,
                    ),
                ),
                TypefaceConstruction.kCloneWithFontArguments,
            )
    }
}
