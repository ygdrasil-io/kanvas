package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr

/**
 * R-suivi.44 — verifies that
 * [SkFontMgr.makeFromData] honours the `ttcIndex` parameter when the
 * input is a TrueType Collection.
 *
 * The test synthesises a minimal 2-face TTC by concatenating two real
 * Liberation TTFs (Sans-Regular as face 0, Serif-Regular as face 1)
 * behind a 20-byte TTC header (`ttcf` tag + version + numFonts + 2 ×
 * u32 offset). The two faces have visibly different metrics so the
 * resulting typefaces can be cross-checked.
 */
class SkFontMgrTtcIndexTest {

    private fun loadTtf(name: String): ByteArray {
        val resource = "/fonts/liberation/$name"
        val stream = SkFontMgrTtcIndexTest::class.java.getResourceAsStream(resource)
            ?: error("Missing bundled resource: $resource")
        return stream.use { it.readBytes() }
    }

    private fun writeU32BE(buf: ByteArray, off: Int, value: Long) {
        buf[off] = ((value ushr 24) and 0xFF).toByte()
        buf[off + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[off + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[off + 3] = (value and 0xFF).toByte()
    }

    /**
     * Build a 2-face TTC :
     *
     *   ttcf [4] | version=00010000 [4] | numFonts=2 [4]
     *           | offset[0] [4]         | offset[1] [4]
     *           | <face0 TTF bytes>     | <face1 TTF bytes>
     */
    private fun buildTtc(face0: ByteArray, face1: ByteArray): ByteArray {
        val headerSize = 12 + 2 * 4              // ttcf+ver+numFonts + 2 offsets
        val out = ByteArray(headerSize + face0.size + face1.size)
        // Tag
        out[0] = 't'.code.toByte()
        out[1] = 't'.code.toByte()
        out[2] = 'c'.code.toByte()
        out[3] = 'f'.code.toByte()
        // Version 1.0
        writeU32BE(out, 4, 0x00010000L)
        // numFonts = 2
        writeU32BE(out, 8, 2L)
        // Offset table
        val face0Off = headerSize
        val face1Off = headerSize + face0.size
        writeU32BE(out, 12, face0Off.toLong())
        writeU32BE(out, 16, face1Off.toLong())
        // Payloads
        System.arraycopy(face0, 0, out, face0Off, face0.size)
        System.arraycopy(face1, 0, out, face1Off, face1.size)
        return out
    }

    @Test
    fun `makeFromData index 0 and 1 produce different faces`() {
        val mgr = SkFontMgr.RefDefault()
        val face0Bytes = loadTtf("LiberationSans-Regular.ttf")
        val face1Bytes = loadTtf("LiberationSerif-Regular.ttf")
        val ttc = SkData.MakeWithCopy(buildTtc(face0Bytes, face1Bytes))

        val tf0 = mgr.makeFromData(ttc, ttcIndex = 0)
        val tf1 = mgr.makeFromData(ttc, ttcIndex = 1)
        assertNotNull(tf0, "TTC face 0 should load")
        assertNotNull(tf1, "TTC face 1 should load")

        // Sans and Serif glyph advances for "Hello" must differ at the
        // same size — proof we got two different faces.
        val sizeF = 32f
        val fSans = SkFont(tf0!!, sizeF)
        val fSerif = SkFont(tf1!!, sizeF)
        val wSans = fSans.measureText("Hello")
        val wSerif = fSerif.measureText("Hello")
        assertNotEquals(wSans, wSerif, "Sans vs Serif must yield different string advances")
    }

    @Test
    fun `makeFromData with ttcIndex out of range returns null`() {
        val mgr = SkFontMgr.RefDefault()
        val face0Bytes = loadTtf("LiberationSans-Regular.ttf")
        val face1Bytes = loadTtf("LiberationSerif-Regular.ttf")
        val ttc = SkData.MakeWithCopy(buildTtc(face0Bytes, face1Bytes))

        assertNull(mgr.makeFromData(ttc, ttcIndex = 2))
        assertNull(mgr.makeFromData(ttc, ttcIndex = -1))
    }

    @Test
    fun `makeFromData with non-TTC and ttcIndex non-zero returns null`() {
        val mgr = SkFontMgr.RefDefault()
        val singleFace = SkData.MakeWithCopy(loadTtf("LiberationSans-Regular.ttf"))
        // Single-face TTF — only ttcIndex=0 is valid.
        assertNotNull(mgr.makeFromData(singleFace, ttcIndex = 0))
        assertNull(mgr.makeFromData(singleFace, ttcIndex = 1))
    }

    @Test
    fun `makeFromData index 0 matches direct TTF load`() {
        // Sanity check: TTC face 0 should yield the same metrics as
        // loading the underlying TTF directly.
        val mgr = SkFontMgr.RefDefault()
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
}
