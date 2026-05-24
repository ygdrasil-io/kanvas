package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.LiberationFontMgr
import org.skia.foundation.SkFontMgr
import org.skia.foundation.stream.SkMemoryStream

class OpenTypeFontMgrMakeFromSkStreamTest {

    private fun loadBundledTtfBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = OpenTypeFontMgrMakeFromSkStreamTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `makeFromStream(SkStream) loads a bundled TTF`() {
        val mgr = portableMgr()
        val mem = SkMemoryStream(loadBundledTtfBytes())
        val tf = mgr.makeFromStream(mem)
        assertNotNull(tf, "makeFromStream(SkStream) should load Liberation Sans Regular")
    }

    @Test
    fun `makeFromStream(SkStream) on empty stream returns null`() {
        val mgr = portableMgr()
        val mem = SkMemoryStream(ByteArray(0))
        assertNull(mgr.makeFromStream(mem))
    }

    @Test
    fun `makeFromStream(SkStream) with garbage bytes returns null`() {
        val mgr = portableMgr()
        val garbage = ByteArray(64) { it.toByte() }
        val mem = SkMemoryStream(garbage)
        assertNull(mgr.makeFromStream(mem))
    }

    @Test
    fun `makeFromStream(SkStream) drains the stream to end-of-data`() {
        val mgr = portableMgr()
        val bytes = loadBundledTtfBytes()
        val mem = SkMemoryStream(bytes)
        val tf = mgr.makeFromStream(mem)
        assertNotNull(tf)
        assertTrue(mem.isAtEnd())
    }

    private fun portableMgr(): SkFontMgr = LiberationFontMgr.Make()
}
