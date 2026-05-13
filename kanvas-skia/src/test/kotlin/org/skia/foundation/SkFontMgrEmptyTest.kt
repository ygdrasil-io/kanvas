package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Verifies [SkFontMgr.RefEmpty] returns a font manager with 0 families
 * and every accessor short-circuits to null/empty per upstream parity.
 */
class SkFontMgrEmptyTest {

    @Test
    fun `RefEmpty has 0 families`() {
        val mgr = SkFontMgr.RefEmpty()
        assertEquals(0, mgr.countFamilies())
    }

    @Test
    fun `RefEmpty getFamilyName throws IndexOutOfBounds`() {
        val mgr = SkFontMgr.RefEmpty()
        assertThrows(IndexOutOfBoundsException::class.java) {
            mgr.getFamilyName(0)
        }
    }

    @Test
    fun `RefEmpty matchFamily returns empty set`() {
        val mgr = SkFontMgr.RefEmpty()
        assertEquals(0, mgr.matchFamily("anything").count())
        assertEquals(0, mgr.matchFamily(null).count())
    }

    @Test
    fun `RefEmpty matchFamilyStyle returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.matchFamilyStyle("anything", SkFontStyle.Normal()))
    }

    @Test
    fun `RefEmpty matchFamilyStyleCharacter returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.matchFamilyStyleCharacter(null, SkFontStyle.Normal(), null, 'a'.code))
    }

    @Test
    fun `RefEmpty makeFromData returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.makeFromData(SkData.MakeWithCopy(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `RefEmpty makeFromStream returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.makeFromStream(ByteArrayInputStream(byteArrayOf())))
    }

    @Test
    fun `RefEmpty makeFromFile returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.makeFromFile("/does/not/exist.ttf"))
    }

    @Test
    fun `RefEmpty legacyMakeTypeface returns null`() {
        val mgr = SkFontMgr.RefEmpty()
        assertNull(mgr.legacyMakeTypeface("anything", SkFontStyle.Normal()))
    }
}
