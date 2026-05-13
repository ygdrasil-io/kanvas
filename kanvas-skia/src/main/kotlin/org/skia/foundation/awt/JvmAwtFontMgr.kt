package org.skia.foundation.awt

import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkTypeface
import org.skia.foundation.stream.SkStream
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * JVM-AWT concrete [SkFontMgr] — wraps `java.awt.Font.createFont` for
 * stream/file/data inputs and routes family-name lookups through
 * `GraphicsEnvironment.getAvailableFontFamilyNames`. The internal
 * [AwtTypeface] class is the concrete typeface implementation used by
 * the rest of `:kanvas-skia`.
 *
 * **TTC support** — `Font.createFont(TRUETYPE_FONT, ...)` always
 * picks face 0 of a TrueType Collection. To honour the
 * `ttcIndex` argument upstream Skia carries, [makeFromData]
 * detects the `ttcf` magic and slices out the bytes for the
 * requested face before handing them to AWT (see [extractTtcFace]).
 *
 * **Codepoint fallback** — [matchFamilyStyleCharacter] consults the
 * hardcoded [AwtFontFallbackTable] to walk a per-script family chain
 * via [matchFamilyStyle], falling back to `Liberation Sans` as a
 * last resort. This is a stopgap until a full fontconfig binding
 * lands (R-suivi.43).
 */
public class JvmAwtFontMgr : SkFontMgr() {

    // -----------------------------------------------------------------
    // matchFamilyStyle — resolve by family name via AWT.
    // -----------------------------------------------------------------

    override fun matchFamilyStyle(familyName: String?, style: SkFontStyle): SkTypeface? {
        if (familyName == null) {
            // Mirror upstream `matchFamilyStyle(nullptr, …)` semantics —
            // returns the default family (here: Liberation Sans).
            return defaultTypeface(style)
        }
        val canonical = resolveFamilyName(familyName) ?: return null
        val awtStyle = toAwtStyle(style)
        val base = Font(canonical, awtStyle, 1)
        return AwtTypeface(base, style)
    }

    /**
     * Returns the AWT family name that matches [requested] case-
     * insensitively, or `null` if no such family is installed. The
     * lookup is cached for the lifetime of this manager — AWT's
     * `getAvailableFontFamilyNames` is expensive.
     */
    private fun resolveFamilyName(requested: String): String? {
        val cached = familyCache[requested.lowercase()]
        if (cached != null) return cached.takeIf { it.isNotEmpty() }
        val available = availableFamilies
        val match = available.firstOrNull { it.equals(requested, ignoreCase = true) }
        familyCache[requested.lowercase()] = match ?: ""
        return match
    }

    private val familyCache: MutableMap<String, String> = HashMap()

    private val availableFamilies: List<String> by lazy {
        try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun toAwtStyle(style: SkFontStyle): Int {
        val isBold = style.weight >= SkFontStyle.kSemiBold_Weight
        val isItalic = style.slant != SkFontStyle.Slant.kUpright_Slant
        return when {
            isBold && isItalic -> Font.BOLD or Font.ITALIC
            isBold -> Font.BOLD
            isItalic -> Font.ITALIC
            else -> Font.PLAIN
        }
    }

    private fun defaultTypeface(style: SkFontStyle): SkTypeface =
        matchFamilyStyle(AwtFontFallbackTable.LAST_RESORT_FAMILY, style)
            ?: AwtTypeface(Font(Font.SANS_SERIF, toAwtStyle(style), 1), style)

    // -----------------------------------------------------------------
    // matchFamilyStyleCharacter — codepoint-aware fallback chain.
    // -----------------------------------------------------------------

    override fun matchFamilyStyleCharacter(
        familyName: String?,
        style: SkFontStyle,
        bcp47: List<String>,
        character: Int,
    ): SkTypeface? {
        // 1. Try the caller-supplied family first.
        if (familyName != null) {
            val tf = matchFamilyStyle(familyName, style)
            if (tf != null && fontContainsCodepoint(tf, character)) return tf
        }
        // 2. Walk the hardcoded script-specific fallback list.
        for (candidate in AwtFontFallbackTable.familiesFor(character)) {
            val tf = matchFamilyStyle(candidate, style) ?: continue
            if (fontContainsCodepoint(tf, character)) return tf
        }
        // 3. Last-resort family. Returns it whether or not it carries
        //    the glyph — matches upstream Skia's behaviour where
        //    `matchFamilyStyleCharacter` falls back to the platform
        //    default rather than returning null when a glyph is
        //    rare/missing.
        return matchFamilyStyle(AwtFontFallbackTable.LAST_RESORT_FAMILY, style)
    }

    /**
     * Returns true if the AWT `Font` backing [typeface] reports a glyph
     * for [codepoint] (i.e. `canDisplay(codepoint) == true`). Always
     * returns true for non-AWT-backed typefaces — we can't peek into
     * them without a custom hook.
     */
    private fun fontContainsCodepoint(typeface: SkTypeface, codepoint: Int): Boolean {
        if (typeface !is AwtTypeface) return true
        return try {
            typeface.canDisplayCodepoint(codepoint)
        } catch (_: Throwable) {
            true
        }
    }

    // -----------------------------------------------------------------
    // makeFromData / makeFromStream / makeFromFile.
    // -----------------------------------------------------------------

    override fun makeFromData(data: ByteArray, ttcIndex: Int): SkTypeface? {
        if (data.isEmpty()) return null
        val payload = if (isTtc(data)) {
            extractTtcFace(data, ttcIndex) ?: return null
        } else {
            if (ttcIndex != 0) return null
            data
        }
        return try {
            val font = Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(payload))
            AwtTypeface(font, SkFontStyle.Normal())
        } catch (_: Throwable) {
            null
        }
    }

    override fun makeFromStream(stream: SkStream, ttcIndex: Int): SkTypeface? {
        val bytes = readAllFromSkStream(stream)
        return makeFromData(bytes, ttcIndex)
    }

    override fun makeFromFile(path: String, ttcIndex: Int): SkTypeface? {
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        val bytes = try {
            file.readBytes()
        } catch (_: Throwable) {
            return null
        }
        return makeFromData(bytes, ttcIndex)
    }

    override fun legacyMakeTypeface(familyName: String?, style: SkFontStyle): SkTypeface =
        matchFamilyStyle(familyName, style) ?: defaultTypeface(style)

    // -----------------------------------------------------------------
    // SkStream → byte[] reader.
    // -----------------------------------------------------------------

    private fun readAllFromSkStream(stream: SkStream): ByteArray {
        val sink = ByteArrayOutputStream(
            if (stream.hasLength()) stream.getLength().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 1024,
        )
        val buf = ByteArray(READ_BUFFER_SIZE)
        while (!stream.isAtEnd()) {
            val n = stream.read(buf, buf.size)
            if (n <= 0) break
            sink.write(buf, 0, n)
        }
        return sink.toByteArray()
    }

    // -----------------------------------------------------------------
    // TTC parsing.
    // -----------------------------------------------------------------

    /**
     * Returns true if [data] starts with the `ttcf` (0x74 0x74 0x63
     * 0x66) magic identifying a TrueType Collection.
     */
    private fun isTtc(data: ByteArray): Boolean =
        data.size >= TTC_HEADER_MIN_SIZE &&
            data[0] == 0x74.toByte() &&
            data[1] == 0x74.toByte() &&
            data[2] == 0x63.toByte() &&
            data[3] == 0x66.toByte()

    /**
     * Extracts the [ttcIndex]-th face from a TTC byte buffer. The
     * upstream TTC header layout is :
     *
     * ```
     *   uint32  tag        // 'ttcf'
     *   uint32  version    // 0x00010000 or 0x00020000
     *   uint32  numFonts
     *   uint32  offsets[numFonts]
     *   ... (version-2 trailer not consulted here)
     * ```
     *
     * The bytes from `offsets[ttcIndex]` to either
     * `offsets[ttcIndex + 1]` (if any) or end-of-buffer are extracted
     * verbatim. AWT's `Font.createFont(TRUETYPE_FONT, …)` accepts a
     * single-face TrueType blob, so the slice is suitable input.
     */
    private fun extractTtcFace(data: ByteArray, ttcIndex: Int): ByteArray? {
        if (ttcIndex < 0) return null
        if (data.size < TTC_HEADER_MIN_SIZE) return null
        val numFonts = readUInt32BE(data, 8)
        if (numFonts <= 0 || numFonts > MAX_TTC_FACES) return null
        if (ttcIndex >= numFonts) return null

        val offsetTableStart = 12
        val offsetTableEnd = offsetTableStart + numFonts * 4
        if (data.size < offsetTableEnd) return null

        val faceStart = readUInt32BE(data, offsetTableStart + ttcIndex * 4)
        if (faceStart <= 0 || faceStart >= data.size) return null

        // We extract from faceStart to either the next face offset or
        // end-of-buffer. Including extra trailing bytes is harmless —
        // a TrueType parser walks `cmap` / `glyf` / etc. via the
        // table directory; bytes past the last referenced table are
        // ignored.
        val faceEnd = data.size
        return data.copyOfRange(faceStart, faceEnd)
    }

    /** Reads a big-endian uint32 at [offset]. */
    private fun readUInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private companion object {
        private const val READ_BUFFER_SIZE = 8192
        private const val TTC_HEADER_MIN_SIZE = 12
        // Sanity cap: any TTC with > 4096 faces is almost certainly a
        // corrupted file we shouldn't try to parse.
        private const val MAX_TTC_FACES = 4096
    }
}
