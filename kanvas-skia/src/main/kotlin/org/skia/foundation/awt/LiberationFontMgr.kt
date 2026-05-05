package org.skia.foundation.awt

import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkTypeface
import java.awt.Font

/**
 * **NOTE D'IMPLÉMENTATION** — Ce fichier expose la surface API Skia
 * (`SkTypeface`) mais l'implémentation sous-jacente repose sur
 * **`java.awt.Font.createFont(TRUETYPE_FONT, …)`** + AWT, pas sur le
 * moteur de fontes natif Skia (FreeType + SkScalerContext).
 *
 * Conséquences :
 *  - Les outlines vectoriels sont identiques à ceux que Skia DM lit
 *    depuis `test_font_*.inc` (les `.inc` ont été générés depuis ces
 *    mêmes TTF Liberation par `tools/fonts/create_test_font.cpp`).
 *  - Le rasterizer AA / hinting / scaler est celui d'AWT, donc 1-2 ulp
 *    de drift sur les bords AA vs FreeType (cf. plan §T4 option A).
 *  - **Option B** (cf. MIGRATION_PLAN_TEXT_ARCHIVED.md §T4) consistera plus tard
 *    à porter directement les `.inc` en Kotlin via un nouveau
 *    `SkTestTypeface : SkTypeface` qui itère points/verbs sans passer
 *    par AWT pour la résolution outline. À ce moment-là, **seul
 *    LiberationFontMgr et AwtTypeface changeront** — l'API publique
 *    (`SkTypeface` / `SkFont` / `ToolUtils`) reste figée.
 *
 * Si on remplace AWT par FreeType+JNI, **seul ce fichier (et ses pairs
 * `Awt*.kt`) doit changer** — l'API publique reste figée sur la
 * signature Skia.
 *
 * Mirrors `tools/fonts/TestFontMgr.cpp::FontMgr` upstream (Skia DM's
 * portable font manager). The 12 sub-fonts come from the same
 * Liberation TTFs that upstream pre-extracts into
 * `test_font_{monospace,sans_serif,serif}.inc`. With
 * `gDefaultFontIndex = 4` upstream, the default typeface is
 * **Liberation Sans Regular** — which we mirror in [getDefault].
 */
internal object LiberationFontMgr {

    /**
     * The three Liberation families upstream Skia exposes via
     * `MakePortableFontMgr`. Names match upstream's family-name
     * matching convention (`onMatchFamily` in `TestFontMgr.cpp` uses
     * `strstr(name, "ono")` / `"ans"` / `"erif"`); we accept the same
     * shorthand strings + the explicit "Toy Liberation X" forms +
     * the bare "Liberation X" upstream sometimes uses.
     */
    private enum class Family(
        val canonical: String,
        val aliases: List<String>,
        val resourcePrefix: String,
    ) {
        MONO(
            canonical = "monospace",
            aliases = listOf("mono", "Toy Liberation Mono", "Liberation Mono", "Courier", "Courier New"),
            resourcePrefix = "LiberationMono",
        ),
        SANS(
            canonical = "sans-serif",
            aliases = listOf("sans", "Toy Liberation Sans", "Liberation Sans", "Arial", "Helvetica"),
            resourcePrefix = "LiberationSans",
        ),
        SERIF(
            canonical = "serif",
            aliases = listOf("Toy Liberation Serif", "Liberation Serif", "Times", "Times New Roman"),
            resourcePrefix = "LiberationSerif",
        );

        companion object {
            /** Mirrors upstream `onMatchFamily`'s case-insensitive substring match. */
            fun resolve(familyName: String?): Family {
                if (familyName == null) return SANS  // upstream's gDefaultFontIndex = 4 = Liberation Sans
                val lower = familyName.lowercase()
                if ("ono" in lower) return MONO
                if ("ans" in lower) return SANS
                if ("erif" in lower) return SERIF
                // Try alias exact-match
                for (f in entries) {
                    if (f.aliases.any { it.equals(familyName, ignoreCase = true) }) return f
                }
                return SANS  // fallback to default family, like upstream's `fDefaultFamily`
            }
        }
    }

    /**
     * Style suffixes appended to [Family.resourcePrefix] to form the
     * TTF resource name. Matches the file layout in
     * `kanvas-skia/src/main/resources/fonts/liberation/`.
     */
    private enum class StyleSuffix(val tag: String) {
        REGULAR("Regular"),
        BOLD("Bold"),
        ITALIC("Italic"),
        BOLD_ITALIC("BoldItalic");

        companion object {
            /**
             * Mirrors `SkFontStyle::matchStyleCSS3` — Liberation only
             * ships 4 styles per family, so the matching collapses
             * (weight ≥ 600 → Bold, slant != upright → Italic, etc.).
             */
            fun resolve(style: SkFontStyle): StyleSuffix {
                val isBold = style.weight >= SkFontStyle.kSemiBold_Weight
                val isItalic = style.slant != SkFontStyle.Slant.kUpright_Slant
                return when {
                    isBold && isItalic -> BOLD_ITALIC
                    isBold -> BOLD
                    isItalic -> ITALIC
                    else -> REGULAR
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Resource loading + cache
    // -----------------------------------------------------------------

    private val baseFontCache: MutableMap<Pair<Family, StyleSuffix>, Font> = HashMap()
    private val typefaceCache: MutableMap<Pair<Family, StyleSuffix>, AwtTypeface> = HashMap()

    private fun loadBaseFont(family: Family, style: StyleSuffix): Font {
        val cached = synchronized(baseFontCache) { baseFontCache[family to style] }
        if (cached != null) return cached

        val resource = "/fonts/liberation/${family.resourcePrefix}-${style.tag}.ttf"
        val stream = LiberationFontMgr::class.java.getResourceAsStream(resource)
            ?: error(
                "Liberation font resource not found: $resource. " +
                    "Expected in kanvas-skia/src/main/resources/fonts/liberation/. " +
                    "See MIGRATION_PLAN_TEXT_ARCHIVED.md §T4."
            )
        val font = stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }

        return synchronized(baseFontCache) {
            baseFontCache.getOrPut(family to style) { font }
        }
    }

    private fun makeStyleObject(style: StyleSuffix): SkFontStyle = when (style) {
        StyleSuffix.REGULAR -> SkFontStyle.Normal()
        StyleSuffix.BOLD -> SkFontStyle.Bold()
        StyleSuffix.ITALIC -> SkFontStyle.Italic()
        StyleSuffix.BOLD_ITALIC -> SkFontStyle.BoldItalic()
    }

    /**
     * Mirrors `SkFontMgr::matchFamilyStyle(family, style)`. Returns an
     * [AwtTypeface] backed by the Liberation TTF closest to the
     * requested `(family, style)` pair. Resolution is lossy in the
     * style dimension (4 styles total per family, vs. the 11 weights ×
     * 9 widths × 3 slants Skia models).
     *
     * `family = null` resolves to the default family
     * (`Liberation Sans`), matching upstream behaviour where
     * `legacyMakeTypeface(nullptr, ...)` returns the default family's
     * matched style.
     */
    fun matchFamilyStyle(family: String?, style: SkFontStyle): SkTypeface {
        val fam = Family.resolve(family)
        val sfx = StyleSuffix.resolve(style)
        val cached = synchronized(typefaceCache) { typefaceCache[fam to sfx] }
        if (cached != null) return cached

        val baseFont = loadBaseFont(fam, sfx)
        val tf = AwtTypeface(baseFont, makeStyleObject(sfx))
        return synchronized(typefaceCache) {
            typefaceCache.getOrPut(fam to sfx) { tf }
        }
    }

    /**
     * Mirrors `ToolUtils::DefaultPortableTypeface()` upstream — returns
     * the typeface backed by **Liberation Sans Regular** (upstream's
     * `gDefaultFontIndex = 4` in `test_font_index.inc`).
     */
    fun getDefault(): SkTypeface = matchFamilyStyle(null, SkFontStyle.Normal())
}
