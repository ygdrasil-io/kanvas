package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the default JVM AWT-backed [SkFontMgr] enumerates the JVM's
 * fonts and supports the basic discovery loop (`countFamilies` →
 * `getFamilyName` → `matchFamily` → `matchStyle`).
 *
 * The exact families available depend on the host JVM, so the assertions
 * here are intentionally generic — every JVM ships AWT's logical
 * families ("Dialog", "SansSerif", "Serif", "Monospaced") via
 * `GraphicsEnvironment.getAvailableFontFamilyNames()`, so we test on
 * those.
 */
class SkFontMgrTest {

    @Test
    fun `RefDefault returns non-empty family list on JVM`() {
        val mgr = SkFontMgr.RefDefault()
        assertTrue(mgr.countFamilies() > 0,
            "JVM AWT GraphicsEnvironment should expose at least 1 font family")
    }

    @Test
    fun `getFamilyName returns each enumerated family`() {
        val mgr = SkFontMgr.RefDefault()
        val n = mgr.countFamilies()
        for (i in 0 until n) {
            val name = mgr.getFamilyName(i)
            assertTrue(name.isNotEmpty(),
                "Family name at index $i should not be empty")
        }
    }

    @Test
    fun `matchFamily with unknown name returns empty set`() {
        val mgr = SkFontMgr.RefDefault()
        val set = mgr.matchFamily("ThisFamilyShouldNotExist__zzzz__99")
        assertEquals(0, set.count())
    }

    @Test
    fun `matchFamily with null returns default family set`() {
        val mgr = SkFontMgr.RefDefault()
        val set = mgr.matchFamily(null)
        // Dialog is always available on a JVM ; matchFamily(null) maps to
        // it per the JvmAwtFontMgr KDoc.
        assertTrue(set.count() > 0,
            "matchFamily(null) should resolve to a default family with ≥ 1 style")
    }

    @Test
    fun `matchFamilyStyle returns a typeface for a known family`() {
        val mgr = SkFontMgr.RefDefault()
        val tf = mgr.matchFamilyStyle("Dialog", SkFontStyle.Normal())
        assertNotNull(tf, "Dialog regular should resolve on every JVM")
    }

    @Test
    fun `matchFamilyStyleCharacter resolves Latin code points (R-suivi 43)`() {
        val mgr = SkFontMgr.RefDefault()
        val tf = mgr.matchFamilyStyleCharacter(
            null, SkFontStyle.Normal(), null, 'A'.code,
        )
        // R-suivi.43 — JvmAwtFontMgr now ships an
        // AwtFontFallbackTable script-bucketed fallback chain backed by
        // the bundled Liberation TTFs.
        assertNotNull(tf, "Latin 'A' must resolve to some typeface via fallback")
    }

    @Test
    fun `legacyMakeTypeface mirrors matchFamilyStyle`() {
        val mgr = SkFontMgr.RefDefault()
        val a = mgr.matchFamilyStyle("Dialog", SkFontStyle.Bold())
        val b = mgr.legacyMakeTypeface("Dialog", SkFontStyle.Bold())
        assertNotNull(a)
        assertNotNull(b)
    }

    @Test
    fun `matchFamily is case insensitive`() {
        val mgr = SkFontMgr.RefDefault()
        val lower = mgr.matchFamily("dialog")
        val upper = mgr.matchFamily("DIALOG")
        assertEquals(lower.count(), upper.count())
        assertTrue(lower.count() > 0)
    }

    @Test
    fun `createStyleSet by index returns 4 styles`() {
        val mgr = SkFontMgr.RefDefault()
        if (mgr.countFamilies() == 0) return
        val set = mgr.createStyleSet(0)
        // JvmAwtFontMgr maps every family to 4 AWT styles (PLAIN/BOLD/
        // ITALIC/BOLD|ITALIC). See class kdoc.
        assertEquals(4, set.count())
    }
}
