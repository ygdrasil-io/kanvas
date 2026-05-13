package org.skia.foundation.awt

/**
 * Minimal hardcoded codepoint → font-family fallback chain, used by
 * [JvmAwtFontMgr.matchFamilyStyleCharacter] when no caller-supplied
 * family carries the requested glyph.
 *
 * Upstream Skia handles fallback via `fontconfig` on Linux,
 * `SkFontMgr_Mac`'s `CTFontCreateForString` on macOS, and the
 * `IDWriteFontFallback` API on Windows. Wiring any of these requires
 * a JNI surface we don't have yet (see API_REMEDIATION_PLAN.md §1.6).
 *
 * As a stopgap, this table hardcodes the most common per-script
 * fallback chains shipped on Linux/macOS/Windows. It's deliberately
 * tiny — covers Latin / CJK / Arabic / Devanagari / Emoji / Symbol —
 * and intentionally leans on commonly-installed family names rather
 * than dynamic discovery. If none of the families in the chain are
 * installed, the caller falls back to its platform default
 * (`Liberation Sans` in this module).
 *
 * R-suivi.43 partial — full fontconfig binding remains future work.
 */
internal object AwtFontFallbackTable {

    /**
     * Coarse Unicode-script bucket. Identified from the codepoint via
     * a single range check (see [scriptOf]).
     */
    internal enum class Script {
        LATIN,
        CJK,
        ARABIC,
        DEVANAGARI,
        EMOJI,
        SYMBOL,
    }

    /**
     * Per-script ordered list of family names to try. Returned to the
     * caller via [familiesFor]; resolved to typefaces one-by-one until
     * a `matchFamilyStyle` lookup succeeds.
     */
    private val table: Map<Script, List<String>> = mapOf(
        Script.LATIN to listOf("DejaVu Sans", "Liberation Sans", "Arial", "Helvetica"),
        Script.CJK to listOf("Noto Sans CJK SC", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB"),
        Script.ARABIC to listOf("Noto Sans Arabic", "Arial", "Tahoma"),
        Script.DEVANAGARI to listOf("Noto Sans Devanagari", "Mangal"),
        Script.EMOJI to listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
        Script.SYMBOL to listOf("Symbola", "DejaVu Sans", "Arial Unicode MS"),
    )

    /** Last-resort family — picks the only TTF we know ships in this module. */
    internal const val LAST_RESORT_FAMILY: String = "Liberation Sans"

    /**
     * Coarse codepoint → [Script] classifier. Uses Unicode block
     * boundaries (BMP-only — supplementary planes get classified as
     * EMOJI when in `[U+1F300, U+1FAFF]`, otherwise SYMBOL).
     *
     * The cut-offs match the upstream block tables in
     * `SkUnicode_libgrapheme.cpp` for the scripts we care about. They
     * are intentionally permissive — false positives only result in a
     * longer fallback chain walk.
     */
    internal fun scriptOf(codepoint: Int): Script {
        return when {
            // Latin (Basic Latin + Latin-1 + Latin Extended A/B + IPA).
            codepoint in 0x0000..0x024F -> Script.LATIN
            // Arabic + Arabic Supplement + Extended-A + Presentation Forms.
            codepoint in 0x0600..0x06FF ||
                codepoint in 0x0750..0x077F ||
                codepoint in 0x08A0..0x08FF ||
                codepoint in 0xFB50..0xFDFF ||
                codepoint in 0xFE70..0xFEFF -> Script.ARABIC
            // Devanagari + Vedic Extensions.
            codepoint in 0x0900..0x097F ||
                codepoint in 0x1CD0..0x1CFF -> Script.DEVANAGARI
            // CJK Unified Ideographs + Extensions A + Compatibility +
            // Hiragana + Katakana + Hangul.
            codepoint in 0x3040..0x309F ||
                codepoint in 0x30A0..0x30FF ||
                codepoint in 0x3400..0x4DBF ||
                codepoint in 0x4E00..0x9FFF ||
                codepoint in 0xAC00..0xD7AF ||
                codepoint in 0xF900..0xFAFF -> Script.CJK
            // Emoji blocks (mostly supplementary plane).
            codepoint in 0x1F300..0x1FAFF ||
                codepoint in 0x2600..0x27BF -> Script.EMOJI
            // Everything else → SYMBOL (math, dingbats, misc).
            else -> Script.SYMBOL
        }
    }

    /** Returns the ordered fallback list for the script of [codepoint]. */
    internal fun familiesFor(codepoint: Int): List<String> =
        table[scriptOf(codepoint)] ?: emptyList()
}
