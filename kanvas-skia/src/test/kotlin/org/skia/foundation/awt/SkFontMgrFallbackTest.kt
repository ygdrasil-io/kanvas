package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle

/**
 * R-suivi.43 — verifies that
 * [SkFontMgr.matchFamilyStyleCharacter] resolves a Unicode code point
 * to a typeface that actually carries the glyph.
 *
 * The test relies on the [AwtFontFallbackTable] candidate chain plus
 * the bundled `"Liberation Sans"` last-resort fallback (always present
 * because the kanvas-skia main resources bundle Liberation TTFs). On
 * any platform where `GraphicsEnvironment` exposes Liberation Sans
 * (which the JVM resolves via its own font discovery), Latin code
 * points must resolve to a non-null typeface.
 */
class SkFontMgrFallbackTest {

    @Test
    fun `matchFamilyStyleCharacter resolves U+0041 'A' to a non-null typeface`() {
        val mgr = SkFontMgr.RefDefault()
        val tf = mgr.matchFamilyStyleCharacter(
            familyName = null,
            style = SkFontStyle.Normal(),
            bcp47 = null,
            character = 0x0041, // 'A'
        )
        assertNotNull(tf, "Latin 'A' must resolve to some typeface via fallback")
    }

    @Test
    fun `matchFamilyStyleCharacter resolves U+0061 'a' to a non-null typeface`() {
        val mgr = SkFontMgr.RefDefault()
        val tf = mgr.matchFamilyStyleCharacter(
            familyName = null,
            style = SkFontStyle.Normal(),
            bcp47 = null,
            character = 0x0061, // 'a'
        )
        assertNotNull(tf)
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
