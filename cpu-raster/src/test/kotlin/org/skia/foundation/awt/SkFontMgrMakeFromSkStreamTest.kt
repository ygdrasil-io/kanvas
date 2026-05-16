package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFontMgr
import org.skia.foundation.stream.SkMemoryStream

/**
 * R-suivi.45 — verifies the new
 * [SkFontMgr.makeFromStream] overload taking an
 * [org.skia.foundation.stream.SkStream] correctly drains the stream and
 * routes to [SkFontMgr.makeFromData].
 *
 * Uses the bundled Liberation Sans Regular TTF as a portable byte
 * source — same resource layout as [SkFontMgrFromDataTest].
 */
class SkFontMgrMakeFromSkStreamTest {

    private fun loadBundledTtfBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = SkFontMgrMakeFromSkStreamTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `makeFromStream(SkStream) loads a bundled TTF`() {
        val mgr = SkFontMgr.RefDefault()
        val mem = SkMemoryStream(loadBundledTtfBytes())
        val tf = mgr.makeFromStream(mem)
        assertNotNull(tf, "makeFromStream(SkStream) should load Liberation Sans Regular")
    }

    @Test
    fun `makeFromStream(SkStream) on empty stream returns null`() {
        val mgr = SkFontMgr.RefDefault()
        val mem = SkMemoryStream(ByteArray(0))
        assertNull(mgr.makeFromStream(mem))
    }

    @Test
    fun `makeFromStream(SkStream) with garbage bytes returns null`() {
        val mgr = SkFontMgr.RefDefault()
        val garbage = ByteArray(64) { it.toByte() }
        val mem = SkMemoryStream(garbage)
        assertNull(mgr.makeFromStream(mem))
    }

    @Test
    fun `makeFromStream(SkStream) drains the stream to end-of-data`() {
        val mgr = SkFontMgr.RefDefault()
        val bytes = loadBundledTtfBytes()
        val mem = SkMemoryStream(bytes)
        val tf = mgr.makeFromStream(mem)
        assertNotNull(tf)
        // After the call the stream must be at EOF — drainSkStream
        // reads until isAtEnd() returns true.
        org.junit.jupiter.api.Assertions.assertTrue(mem.isAtEnd())
    }
}
