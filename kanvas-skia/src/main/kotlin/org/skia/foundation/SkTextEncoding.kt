package org.skia.foundation

/**
 * Mirrors Skia's `SkTextEncoding` (`include/core/SkFontTypes.h`).
 *
 * Identifies the byte / word size of an input text buffer for
 * [SkFont.measureText] / [SkCanvas.drawSimpleText]. T1 only honours
 * [kUTF8] meaningfully (the only encoding actually exercised by
 * upstream `drawString` callers); the other variants are accepted
 * for signature compatibility but treated as UTF-8 by the AWT
 * backend (see `MIGRATION_PLAN_TEXT_ARCHIVED.md` §T1).
 */
public enum class SkTextEncoding {
    kUTF8,    // bytes representing UTF-8 (or ASCII)
    kUTF16,   // two-byte words representing most of Unicode
    kUTF32,   // four-byte words representing all of Unicode
    kGlyphID, // two-byte words representing glyph indices
}
