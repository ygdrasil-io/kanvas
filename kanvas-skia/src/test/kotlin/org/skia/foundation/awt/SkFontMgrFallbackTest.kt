package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle

/**
 * R-suivi.43 — exercises [SkFontMgr.matchFamilyStyleCharacter] and the
 * hardcoded [AwtFontFallbackTable] backing it. The test relies on the
 * fact that any JVM ships at least one Latin-capable font (the AWT
 * logical `SANS_SERIF` is always present), so a Latin codepoint must
 * always resolve to a non-null typeface — even with a deliberately
 * unknown family name.
 */
class SkFontMgrFallbackTest {

    @Test
    fun `matchFamilyStyleCharacter returns non-null for Latin A`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val tf = mgr.matchFamilyStyleCharacter(
            familyName = "ThisFamilyDoesNotExist__zzz",
            style = SkFontStyle.Normal(),
            bcp47 = emptyList(),
            character = 'A'.code, // U+0041 Latin
        )
        assertNotNull(tf, "Latin 'A' must resolve to some typeface via fallback")
    }

    @Test
    fun `matchFamilyStyleCharacter with null family still resolves Latin A`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val tf = mgr.matchFamilyStyleCharacter(
            familyName = null,
            style = SkFontStyle.Normal(),
            bcp47 = emptyList(),
            character = 0x0041,
        )
        assertNotNull(tf)
    }

    @Test
    fun `scriptOf classifies common codepoints into the expected buckets`() {
        // Spot-check a handful of codepoints against the script buckets.
        assertEquals(AwtFontFallbackTable.Script.LATIN, AwtFontFallbackTable.scriptOf(0x0041))
        assertEquals(AwtFontFallbackTable.Script.LATIN, AwtFontFallbackTable.scriptOf(0x00E9)) // 'é'
        assertEquals(AwtFontFallbackTable.Script.ARABIC, AwtFontFallbackTable.scriptOf(0x0627))
        assertEquals(AwtFontFallbackTable.Script.DEVANAGARI, AwtFontFallbackTable.scriptOf(0x0928))
        assertEquals(AwtFontFallbackTable.Script.CJK, AwtFontFallbackTable.scriptOf(0x4E2D)) // 中
        assertEquals(AwtFontFallbackTable.Script.CJK, AwtFontFallbackTable.scriptOf(0x3042)) // あ
        assertEquals(AwtFontFallbackTable.Script.EMOJI, AwtFontFallbackTable.scriptOf(0x1F600))
    }

    @Test
    fun `familiesFor returns a non-empty chain for every covered script`() {
        // Latin, Arabic, Devanagari, CJK, Emoji, Symbol all populated.
        val codepoints = intArrayOf(0x0041, 0x0627, 0x0928, 0x4E2D, 0x1F600, 0x2600)
        for (cp in codepoints) {
            val chain = AwtFontFallbackTable.familiesFor(cp)
            assertEquals(true, chain.isNotEmpty(), "fallback chain for cp=0x${cp.toString(16)} must not be empty")
        }
    }

    @Test
    fun `RefDefault produces a working SkFontMgr instance`() {
        val mgr = SkFontMgr.RefDefault()
        // RefDefault is allowed to fall back to RefEmpty on platforms
        // without AWT, but on the JVM where this test runs it must
        // load the JvmAwtFontMgr.
        val tf = mgr.matchFamilyStyleCharacter(
            familyName = null,
            style = SkFontStyle.Normal(),
            bcp47 = emptyList(),
            character = 'A'.code,
        )
        assertNotNull(tf)
    }
}
