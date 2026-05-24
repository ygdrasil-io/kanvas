package org.skia.foundation.opentype

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.LiberationFontMgr
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr

class OpenTypeFontMgrTtcIndexTest {

    private fun loadTtf(name: String): ByteArray {
        val resource = "/fonts/liberation/$name"
        val stream = OpenTypeFontMgrTtcIndexTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    private fun writeU32BE(buf: ByteArray, off: Int, value: Long) {
        buf[off] = ((value ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 3] = (value and 0xFF).toByte()
    }

    private fun buildTtc(face0: ByteArray, face1: ByteArray): ByteArray {
        val headerSize = 12 + 2 * 4
        val out = ByteArray(headerSize + face0.size + face1.size)
        out[0] = 't'.code.toByte()
        out[1] = 't'.code.toByte()
        out[2] = 'c'.code.toByte()
        out[3] = 'f'.code.toByte()
        writeU32BE(out, 4, 0x00010000L)
        writeU32BE(out, 8, 2L)
        val face0Off = headerSize
        val face1Off = headerSize + face0.size
        writeU32BE(out, 12, face0Off.toLong())
        writeU32BE(out, 16, face1Off.toLong())
        System.arraycopy(face0, 0, out, face0Off, face0.size)
        System.arraycopy(face1, 0, out, face1Off, face1.size)
        return out
    }

    @Test
    fun `makeFromData index 0 and 1 produce different faces`() {
        val mgr = portableMgr()
        val face0Bytes = loadTtf("LiberationSans-Regular.ttf")
        val face1Bytes = loadTtf("LiberationSerif-Regular.ttf")
        val ttc = SkData.MakeWithCopy(buildTtc(face0Bytes, face1Bytes))

        val tf0 = mgr.makeFromData(ttc, ttcIndex = 0)
        val tf1 = mgr.makeFromData(ttc, ttcIndex = 1)
        assertNotNull(tf0, "TTC face 0 should load")
        assertNotNull(tf1, "TTC face 1 should load")
        assertTrue(tf0 is OpenTypeTypeface)
        assertTrue(tf1 is OpenTypeTypeface)

        val sizeF = 32f
        val wSans = SkFont(tf0!!, sizeF).measureText("Hello")
        val wSerif = SkFont(tf1!!, sizeF).measureText("Hello")
        assertNotEquals(wSans, wSerif, "Sans vs Serif must yield different string advances")
    }

    @Test
    fun `makeFromData with ttcIndex out of range returns null`() {
        val mgr = portableMgr()
        val face0Bytes = loadTtf("LiberationSans-Regular.ttf")
        val face1Bytes = loadTtf("LiberationSerif-Regular.ttf")
        val ttc = SkData.MakeWithCopy(buildTtc(face0Bytes, face1Bytes))

        assertNull(mgr.makeFromData(ttc, ttcIndex = 2))
        assertNull(mgr.makeFromData(ttc, ttcIndex = -1))
    }

    @Test
    fun `makeFromData with non-TTC and ttcIndex non-zero returns null`() {
        val mgr = portableMgr()
        val singleFace = SkData.MakeWithCopy(loadTtf("LiberationSans-Regular.ttf"))

        assertNotNull(mgr.makeFromData(singleFace, ttcIndex = 0))
        assertNull(mgr.makeFromData(singleFace, ttcIndex = 1))
    }

    @Test
    fun `makeFromData index 0 matches direct TTF load`() {
        val mgr = portableMgr()
        val face0Bytes = loadTtf("LiberationSans-Regular.ttf")
        val face1Bytes = loadTtf("LiberationSerif-Regular.ttf")
        val ttc = SkData.MakeWithCopy(buildTtc(face0Bytes, face1Bytes))
        val direct = SkData.MakeWithCopy(face0Bytes)

        val viaTtc = mgr.makeFromData(ttc, ttcIndex = 0)
        val viaDirect = mgr.makeFromData(direct)
        assertNotNull(viaTtc)
        assertNotNull(viaDirect)
        val sizeF = 32f
        val wViaTtc = SkFont(viaTtc!!, sizeF).measureText("Hello")
        val wViaDirect = SkFont(viaDirect!!, sizeF).measureText("Hello")
        assertEquals(
            wViaDirect,
            wViaTtc,
            0.001f,
            "TTC face 0 should match direct TTF load",
        )
    }

    private fun portableMgr(): SkFontMgr = LiberationFontMgr.Make()
}
