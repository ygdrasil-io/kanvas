package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFontMgr
import org.skia.foundation.opentype.OpenTypeSystemFontMgr

/**
 * R-suivi.43 — verifies that
 * [SkFontMgr.matchFamilyStyleCharacter] resolves a Unicode code point
 * to a typeface that actually carries the glyph.
 *
 * Verifies the legacy cpu-raster `RefDefault()` extension no longer routes
 * through AWT. Host font contents are intentionally not asserted here because
 * minimal CI hosts may have no TrueType system fonts parseable by the current
 * OpenType backend.
 */
class SkFontMgrFallbackTest {

    @Test
    fun `RefDefault uses the pure Kotlin OpenType system manager`() {
        assertTrue(SkFontMgr.RefDefault() is OpenTypeSystemFontMgr)
    }

    @Test
    fun `AwtFontFallbackTable scriptFor classifies common codepoints`() {
        assertEquals("Latin", AwtFontFallbackTable.scriptFor(0x0041)) // 'A'
        assertEquals("Latin", AwtFontFallbackTable.scriptFor(0x00E9)) // 'é'
        assertEquals("CJK", AwtFontFallbackTable.scriptFor(0x4E2D))   // 中
        assertEquals("CJK", AwtFontFallbackTable.scriptFor(0x3042))   // あ (Hiragana)
        assertEquals("CJK", AwtFontFallbackTable.scriptFor(0xAC00))   // 가 (Hangul)
        assertEquals("Arabic", AwtFontFallbackTable.scriptFor(0x0627)) // ا
        assertEquals("Devanagari", AwtFontFallbackTable.scriptFor(0x0905)) // अ
    }

    @Test
    fun `candidatesFor returns ordered list for Latin codepoints`() {
        val candidates = AwtFontFallbackTable.candidatesFor(0x0041)
        org.junit.jupiter.api.Assertions.assertTrue(candidates.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertTrue(
            candidates.contains("Liberation Sans") || candidates.contains("DejaVu Sans"),
            "Latin candidates should include a common cross-platform sans family",
        )
    }
}
