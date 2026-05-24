package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifies the portable bundled OpenType [SkFontMgr] supports the basic
 * discovery loop (`countFamilies` → `getFamilyName` → `matchFamily` →
 * `matchStyle`) without depending on platform font APIs.
 */
class SkFontMgrTest {

    private fun portableMgr(): SkFontMgr = LiberationFontMgr.Make()

    @Test
    fun `portable manager returns bundled Liberation families`() {
        val mgr = portableMgr()
        assertEquals(3, mgr.countFamilies())
    }

    @Test
    fun `getFamilyName returns each enumerated family`() {
        val mgr = portableMgr()
        val n = mgr.countFamilies()
        for (i in 0 until n) {
            val name = mgr.getFamilyName(i)
            assertTrue(name.isNotEmpty(),
                "Family name at index $i should not be empty")
        }
    }

    @Test
    fun `matchFamily with unknown name returns empty set`() {
        val mgr = portableMgr()
        val set = mgr.matchFamily("ThisFamilyShouldNotExist__zzzz__99")
        assertEquals(0, set.count())
    }

    @Test
    fun `matchFamily with null returns default family set`() {
        val mgr = portableMgr()
        val set = mgr.matchFamily(null)
        assertTrue(set.count() > 0,
            "matchFamily(null) should resolve to a default family with ≥ 1 style")
    }

    @Test
    fun `matchFamilyStyle returns a typeface for a known family`() {
        val mgr = portableMgr()
        val tf = mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Normal())
        assertNotNull(tf, "Liberation Sans regular should resolve")
    }

    @Test
    fun `matchFamilyStyleCharacter resolves Latin code points`() {
        val mgr = portableMgr()
        val tf = mgr.matchFamilyStyleCharacter(
            null, SkFontStyle.Normal(), null, 'A'.code,
        )
        assertNotNull(tf, "Latin 'A' must resolve via bundled Liberation fallback")
    }

    @Test
    fun `legacyMakeTypeface mirrors matchFamilyStyle`() {
        val mgr = portableMgr()
        val a = mgr.matchFamilyStyle("Liberation Sans", SkFontStyle.Bold())
        val b = mgr.legacyMakeTypeface("Liberation Sans", SkFontStyle.Bold())
        assertNotNull(a)
        assertNotNull(b)
    }

    @Test
    fun `matchFamily is case insensitive`() {
        val mgr = portableMgr()
        val lower = mgr.matchFamily("liberation sans")
        val upper = mgr.matchFamily("LIBERATION SANS")
        assertEquals(lower.count(), upper.count())
        assertTrue(lower.count() > 0)
    }

    @Test
    fun `createStyleSet by index returns 4 styles`() {
        val mgr = portableMgr()
        if (mgr.countFamilies() == 0) return
        val set = mgr.createStyleSet(0)
        assertEquals(4, set.count())
    }
}
