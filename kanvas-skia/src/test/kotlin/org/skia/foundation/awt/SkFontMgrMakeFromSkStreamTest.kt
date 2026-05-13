package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkTypeface
import org.skia.foundation.stream.SkMemoryStream

/**
 * R-suivi.45 — exercises [SkFontMgr.makeFromStream] backed by an
 * [SkMemoryStream] and ensures the manager honours the
 * `:kanvas-skia` `SkStream` contract (rather than `java.io.InputStream`).
 */
class SkFontMgrMakeFromSkStreamTest {

    private fun liberationSansBytes(): ByteArray =
        javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")
            ?.use { it.readBytes() }
            ?: error("Liberation Sans Regular TTF must ship in test classpath")

    @Test
    fun `makeFromStream returns a typeface from a SkMemoryStream-backed TTF`() {
        val bytes = liberationSansBytes()
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val stream = SkMemoryStream(bytes)
        val tf: SkTypeface? = mgr.makeFromStream(stream, ttcIndex = 0)
        assertNotNull(tf, "Liberation Sans Regular must load via SkMemoryStream")
        assertEquals(SkFontStyle.Normal(), tf!!.fontStyle)
    }

    @Test
    fun `makeFromStream-loaded typeface produces a non-zero advance for ASCII`() {
        val bytes = liberationSansBytes()
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val tf = mgr.makeFromStream(SkMemoryStream(bytes))!!
        val font = SkFont(tf, 24f)
        val advance = font.measureText("Hello")
        assertTrue(advance > 0f, "ASCII text must have a positive advance, got $advance")
    }

    @Test
    fun `makeFromData with empty bytes returns null`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        assertNull(mgr.makeFromData(ByteArray(0)))
    }

    @Test
    fun `makeFromData with garbage bytes returns null instead of throwing`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val garbage = ByteArray(128) { (it and 0xFF).toByte() }
        // Should swallow the AWT FontFormatException and return null.
        assertNull(mgr.makeFromData(garbage))
    }

    @Test
    fun `makeFromStream on a non-TTC payload rejects ttcIndex != 0`() {
        val bytes = liberationSansBytes()
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val tf = mgr.makeFromStream(SkMemoryStream(bytes), ttcIndex = 1)
        assertNull(tf, "ttcIndex != 0 on a single-face TTF must return null")
    }
}
