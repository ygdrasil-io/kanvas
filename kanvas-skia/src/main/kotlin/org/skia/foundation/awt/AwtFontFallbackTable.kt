package org.skia.foundation.awt

/**
 * R-suivi.43 — static codepoint→family fallback chain used by
 * [JvmAwtFontMgr.matchFamilyStyleCharacter].
 *
 * Upstream Skia delegates this to the native font configuration backend
 * (fontconfig on Linux, CoreText on macOS, DirectWrite on Windows). Each
 * of those backends ships its own per-script preferred-family list. We
 * don't bind to any native font config in the JVM AWT backend (see
 * [JvmAwtFontMgr] R-suivi note), so the codepoint→script bucketing and
 * the family chain are baked in here. The lists are intentionally short
 * — a few well-known families per script — and ordered by approximate
 * platform availability.
 *
 * The classification is coarse (Latin / CJK / Arabic / Devanagari /
 * Emoji / Symbol) — enough for the GMs in scope and as a "best effort"
 * for downstream callers. A native fontconfig integration would replace
 * this entirely.
 */
internal object AwtFontFallbackTable {

    private val table: Map<String, List<String>> = mapOf(
        "Latin" to listOf("DejaVu Sans", "Liberation Sans", "Arial", "Helvetica"),
        "CJK" to listOf("Noto Sans CJK SC", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB"),
        "Arabic" to listOf("Noto Sans Arabic", "Arial", "Tahoma"),
        "Devanagari" to listOf("Noto Sans Devanagari", "Mangal"),
        "Emoji" to listOf("Noto Color Emoji", "Apple Color Emoji", "Segoe UI Emoji"),
        "Symbol" to listOf("Symbola", "DejaVu Sans", "Arial Unicode MS"),
    )

    /**
     * Classify [cp] into one of the buckets above. Uses
     * [Character.UnicodeBlock] as the authoritative source — falls back
     * to `Latin` when the block is unknown.
     */
    fun scriptFor(cp: Int): String {
        val block = Character.UnicodeBlock.of(cp)?.toString() ?: return "Latin"
        return when {
            block.contains("CJK") ||
                block.contains("HIRAGANA") ||
                block.contains("KATAKANA") ||
                block.contains("HANGUL") -> "CJK"

            block.contains("ARABIC") -> "Arabic"
            block.contains("DEVANAGARI") -> "Devanagari"
            block.contains("EMOTICONS") ||
                block.contains("MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS") -> "Emoji"

            block.contains("SYMBOLS") || block.contains("DINGBATS") -> "Symbol"
            else -> "Latin"
        }
    }

    /** Ordered candidate family names for [cp]'s script bucket. */
    fun candidatesFor(cp: Int): List<String> = table[scriptFor(cp)] ?: emptyList()
}
