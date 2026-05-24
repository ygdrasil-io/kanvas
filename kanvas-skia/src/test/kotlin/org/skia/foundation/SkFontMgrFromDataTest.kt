package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Verifies [SkFontMgr.makeFromData] / [SkFontMgr.makeFromStream] /
 * [SkFontMgr.makeFromFile] can load a real TTF.
 *
 * Uses the bundled Liberation Sans Regular at
 * `/fonts/liberation/LiberationSans-Regular.ttf` — same resource layout
 * as `LiberationFontMgr`. The bundled TTFs are valid TrueType and should
 * be accepted by the pure Kotlin OpenType manager.
 */
class SkFontMgrFromDataTest {
    private fun portableMgr(): SkFontMgr = LiberationFontMgr.Make()


    private fun loadBundledTtfBytes(): ByteArray {
        val resource = "/fonts/liberation/LiberationSans-Regular.ttf"
        val stream = SkFontMgrFromDataTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    @Test
    fun `makeFromData loads a bundled TTF`() {
        val mgr = portableMgr()
        val data = SkData.MakeWithCopy(loadBundledTtfBytes())
        val tf = mgr.makeFromData(data)
        assertNotNull(tf, "makeFromData should load Liberation Sans Regular")
    }

    @Test
    fun `makeFromData with empty data returns null`() {
        val mgr = portableMgr()
        val tf = mgr.makeFromData(SkData.EMPTY)
        assertNull(tf)
    }

    @Test
    fun `makeFromData with garbage bytes returns null`() {
        val mgr = portableMgr()
        // Random bytes that don't look like a TrueType file should be
        // caught and surfaced as null.
        val garbage = ByteArray(64) { it.toByte() }
        val tf = mgr.makeFromData(SkData.MakeWithCopy(garbage))
        assertNull(tf)
    }

    @Test
    fun `makeFromStream loads a bundled TTF`() {
        val mgr = portableMgr()
        val tf = ByteArrayInputStream(loadBundledTtfBytes()).use { mgr.makeFromStream(it) }
        assertNotNull(tf)
    }

    @Test
    fun `makeFromFile returns null for missing path`() {
        val mgr = portableMgr()
        val tf = mgr.makeFromFile("/this/path/should/not/exist.ttf")
        assertNull(tf)
    }

    @Test
    fun `makeFromFile loads a real TTF on disk`() {
        // Extract the bundled TTF to a temp file and load via makeFromFile.
        val bytes = loadBundledTtfBytes()
        val tmp = java.io.File.createTempFile("LiberationSans-Regular", ".ttf")
        tmp.deleteOnExit()
        tmp.writeBytes(bytes)
        val mgr = portableMgr()
        val tf = mgr.makeFromFile(tmp.absolutePath)
        assertNotNull(tf, "makeFromFile should load Liberation Sans Regular from disk")
        assertTrue(tmp.delete() || !tmp.exists())
    }
}
