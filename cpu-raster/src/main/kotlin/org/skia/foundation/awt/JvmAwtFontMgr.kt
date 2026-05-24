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
 * **NOTE D'IMPLÉMENTATION** — Optional JVM AWT font manager. Surfaces system
 * fonts to explicit `cpu-raster` AWT consumers via
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
 * Portable `kanvas-skia` font paths do not use this manager. They use the
 * bundled pure-Kotlin OpenType Liberation manager so tests and rendering do
 * not depend on host system fonts.
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
     * R-suivi.43 — codepoint→family fallback via a static script-bucket
     * table (see [AwtFontFallbackTable]). For each candidate family in
     * the bucket for [character], try [matchFamilyStyle] and check the
     * resulting typeface actually carries a glyph for the code point
     * via [AwtTypeface.canDisplayCodepoint]. The first hit wins. If no
     * candidate matches, fall back to "Liberation Sans" (always
     * present on a JVM that has loaded our bundled portable fonts).
     *
     * Notes :
     *  - [familyName] is currently ignored — the upstream contract is
     *    "you want a font that carries this codepoint, and you'd like
     *    it to match [familyName] if possible". Honoring [familyName]
     *    cleanly would require enumerating its style set and then
     *    falling back ; we keep the simpler "by script" path.
     *  - [bcp47] is also unused — upstream uses it to bias the script
     *    bucket (e.g. CJK Simplified Chinese vs. Traditional). Our
     *    table is coarse enough that the bias would be a no-op.
     */
    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: Array<String>?,
        character: Int,
    ): SkTypeface? {
        for (candidate in AwtFontFallbackTable.candidatesFor(character)) {
            val tf = matchFamilyStyle(candidate, style) as? AwtTypeface ?: continue
            if (tf.canDisplayCodepoint(character)) return tf
        }
        // Last-resort: Liberation Sans (always present in kanvas-skia's
        // bundled-font test fixture and on most Linux distros).
        return (matchFamilyStyle("Liberation Sans", style) as? AwtTypeface)
            ?.takeIf { it.canDisplayCodepoint(character) }
    }

    override fun makeFromData(data: SkData, ttcIndex: Int): SkTypeface? {
        if (data.size == 0) return null
        val bytes = data.toByteArray()
        // R-suivi.44 — TTC (TrueType Collection) face selection. AWT's
        // `Font.createFont(TRUETYPE_FONT, …)` always picks face 0 in a
        // TTC, so we slice the TTC down to the requested face's bytes
        // before handing it off. Header layout (big-endian, from the
        // OpenType spec):
        //   off 0..3  : "ttcf" tag (4 bytes)
        //   off 4..7  : TTC version (u32, ignored)
        //   off 8..11 : numFonts (u32)
        //   off 12..  : numFonts × u32 = offset table → byte offset of
        //               each face's TTF header within the TTC.
        // For non-TTC fonts (single face), `ttcIndex` must be 0 ; any
        // other value returns null to match upstream's "ttcIndex out of
        // range" semantics.
        val effectiveBytes = if (bytes.size >= 12 &&
            bytes[0] == 't'.code.toByte() && bytes[1] == 't'.code.toByte() &&
            bytes[2] == 'c'.code.toByte() && bytes[3] == 'f'.code.toByte()) {
            val numFonts = readU32BE(bytes, 8)
            if (ttcIndex < 0 || ttcIndex.toLong() >= numFonts) return null
            val offsetTableEnd = 12 + numFonts.toInt() * 4
            if (bytes.size < offsetTableEnd) return null
            val offset = readU32BE(bytes, 12 + ttcIndex * 4).toInt()
            if (offset < 0 || offset >= bytes.size) return null
            bytes.copyOfRange(offset, bytes.size)
        } else if (ttcIndex != 0) {
            return null
        } else {
            bytes
        }
        // After TTC slicing the selected face is at index 0 of the
        // resulting buffer — pass 0 downstream regardless of the
        // original ttcIndex.
        return makeFromStream(ByteArrayInputStream(effectiveBytes), 0)
    }

    private fun readU32BE(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

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
