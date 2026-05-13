package org.skia.foundation.awt

import org.skia.foundation.SkData
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkTypeface
import java.awt.Font
import java.awt.FontFormatException
import java.awt.GraphicsEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * **NOTE D'IMPLÉMENTATION** — Default JVM font manager. Surfaces system
 * fonts to [SkFontMgr] consumers via
 * `java.awt.GraphicsEnvironment.getAvailableFontFamilyNames()`. Each
 * family enumerates the 4 classical AWT styles (regular / bold /
 * italic / bold-italic) — that's an approximation versus upstream's
 * fontconfig which exposes every concrete face the OS knows about
 * (multiple weights, variable widths, …).
 *
 * **R3.2 trade-offs / R-suivi notes :**
 *  - **No real codepoint-based fallback** : [matchFamilyStyleCharacter]
 *    returns `null`. AWT does not expose a "find any font that carries
 *    this codepoint" API. To get parity with upstream's fontconfig
 *    fallback, a native fontconfig binding is needed (deferred to R-suivi).
 *  - **4 styles per family** : every family is represented by 4 typefaces,
 *    regardless of how many concrete faces the underlying font file
 *    ships. This is consistent with how AWT models `Font.PLAIN /
 *    Font.BOLD / Font.ITALIC / Font.BOLD|Font.ITALIC`.
 *  - **TTC index ignored** : `Font.createFont(TRUETYPE_FONT, …)` always
 *    picks face 0 in a TrueType Collection.
 *
 * If/when a real fontconfig (or freetype) backend lands, only this file
 * (and its peers `Awt*.kt`) needs to change — the [SkFontMgr] API
 * surface stays pinned to the upstream signature.
 */
internal class JvmAwtFontMgr private constructor(
    private val families: List<String>,
) : SkFontMgr() {

    override fun countFamilies(): Int = families.size

    override fun getFamilyName(index: Int): String = families[index]

    override fun createStyleSet(index: Int): SkFontStyleSet =
        AwtFontStyleSet(families[index])

    override fun matchFamily(familyName: String?): SkFontStyleSet {
        // Upstream contract: returns the default system family when
        // familyName is null. AWT's "default" sans-serif is reached via
        // Font.SANS_SERIF — that's the logical name AWT remaps to the
        // platform-best sans-serif. We use the literal family name when
        // it shows up in `families` (which is always the case on a JVM
        // with an enumerable GraphicsEnvironment).
        if (familyName == null) {
            val fallback = "Dialog"  // AWT's "default font family" logical name
            return matchFamilyByName(fallback) ?: SkFontStyleSet.CreateEmpty()
        }
        return matchFamilyByName(familyName) ?: SkFontStyleSet.CreateEmpty()
    }

    private fun matchFamilyByName(familyName: String): SkFontStyleSet? {
        // Case-insensitive lookup — upstream's fontconfig does the same.
        val canonical = families.firstOrNull { it.equals(familyName, ignoreCase = true) }
            ?: return null
        return AwtFontStyleSet(canonical)
    }

    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? {
        return matchFamily(familyName).matchStyle(style)
    }

    /**
     * AWT has no codepoint→font fallback API — see class KDoc for the
     * R-suivi deferral. Returning `null` here mirrors upstream's
     * "no good match" return ; callers either propagate the null
     * (drawing the .notdef glyph) or carry their own fallback table.
     */
    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface? = null

    override fun makeFromData(data: SkData, ttcIndex: Int): SkTypeface? {
        if (data.size == 0) return null
        return makeFromStream(ByteArrayInputStream(data.toByteArray()), ttcIndex)
    }

    override fun makeFromStream(stream: InputStream, ttcIndex: Int): SkTypeface? {
        // Font.createFont consumes the stream up to EOF on success but
        // does not close it — symmetric with our InputStream-doesn't-close
        // contract on SkCodec.MakeFromStream.
        val awtFont: Font = try {
            Font.createFont(Font.TRUETYPE_FONT, stream)
        } catch (e: FontFormatException) {
            return null
        } catch (e: IOException) {
            return null
        }
        return AwtTypeface(awtFont, SkFontStyle.Normal())
    }

    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            file.inputStream().use { makeFromStream(it, ttcIndex) }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface? =
        matchFamilyStyle(familyName, style)

    internal companion object {
        /**
         * Lazy-initialised default font manager — first call enumerates
         * the JVM's font family names (an O(n_fonts) call that may hit
         * the filesystem on first use ; the JVM caches internally so
         * subsequent calls are cheap).
         *
         * `Locale.ROOT` ensures we don't get locale-translated family
         * names (the alternative is the deprecated zero-arg overload
         * which uses the default locale).
         */
        internal val SINGLETON: JvmAwtFontMgr by lazy {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            val raw = ge.availableFontFamilyNames
            JvmAwtFontMgr(raw.toList())
        }
    }
}

/**
 * AWT-backed [SkFontStyleSet] — one [familyName], 4 styles.
 *
 * The 4 styles are materialised lazily : `createTypeface(i)` builds the
 * AWT `Font` via `Font(familyName, style, 1)` (1pt — derived to the
 * requested size at draw time, per the [AwtTypeface] convention) the
 * first time it's asked for, then caches it.
 *
 * **Style index convention** : `0=Regular, 1=Bold, 2=Italic, 3=BoldItalic`.
 * This matches AWT's `Font.PLAIN / BOLD / ITALIC / BOLD|ITALIC` ordering.
 */
internal class AwtFontStyleSet(private val familyName: String) : SkFontStyleSet() {

    private data class StyleEntry(
        val skStyle: SkFontStyle,
        val awtStyle: Int,
        val name: String,
    )

    private val styles: List<StyleEntry> = listOf(
        StyleEntry(SkFontStyle.Normal(), Font.PLAIN, "Regular"),
        StyleEntry(SkFontStyle.Bold(), Font.BOLD, "Bold"),
        StyleEntry(SkFontStyle.Italic(), Font.ITALIC, "Italic"),
        StyleEntry(SkFontStyle.BoldItalic(), Font.BOLD or Font.ITALIC, "BoldItalic"),
    )

    override fun count(): Int = styles.size

    override fun getStyle(index: Int, style: SkFontStyle?, name: StringBuilder?): SkFontStyle {
        val entry = styles[index]
        name?.append(entry.name)
        return entry.skStyle
        // NOTE: upstream signature mutates `style` in place because
        // SkFontStyle has a copy-assignment operator. SkFontStyle in
        // Kotlin is immutable — we return the value and let callers
        // assign. The `style` out-param is intentionally accepted but
        // ignored (signature parity).
    }

    override fun createTypeface(index: Int): SkTypeface {
        val entry = styles[index]
        // Family name is mapped through AWT's `Font(name, style, size)`
        // constructor — when the name doesn't match a real family AWT
        // silently falls back to Dialog. We don't try to detect that
        // case here ; the caller has already passed [familyName] from
        // [JvmAwtFontMgr.families], which is by construction a name AWT
        // can resolve.
        val awt = Font(familyName, entry.awtStyle, 1)
        return AwtTypeface(awt, entry.skStyle)
    }

    /**
     * CSS-3 style match across the 4 AWT entries. Delegates to
     * [matchStyleCSS3] (in turn computing the [css3Distance] scoring) —
     * the small set size (4) makes the linear scan trivially cheap.
     */
    override fun matchStyle(pattern: SkFontStyle): SkTypeface? = matchStyleCSS3(pattern)
}
