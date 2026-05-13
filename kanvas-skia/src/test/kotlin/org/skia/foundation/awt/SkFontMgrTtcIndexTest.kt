package org.skia.foundation.awt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkFontMgr

/**
 * R-suivi.44 — exercises `ttcIndex` parsing in
 * [SkFontMgr.makeFromData]. We construct a **minimal synthetic TTC**
 * whose `offsets[]` table points at one or two copies of a real
 * Liberation Sans TTF embedded in the trailing bytes. The point is to
 * verify the parser (header detection, offset resolution,
 * out-of-range rejection), not to exercise an actual multi-face
 * collection — synthesising a true shared-table TTC requires a full
 * TrueType layout serializer we don't have.
 */
class SkFontMgrTtcIndexTest {

    private fun liberationSansBytes(): ByteArray =
        javaClass.getResourceAsStream("/fonts/liberation/LiberationSans-Regular.ttf")
            ?.use { it.readBytes() }
            ?: error("Liberation Sans Regular TTF must ship in test classpath")

    /**
     * Build a synthetic TTC blob :
     *
     * ```
     *   'ttcf' | 0x00010000 | numFonts | offsets[numFonts] | ttfBytes
     * ```
     *
     * `offsets[i]` is the absolute byte offset of `ttfBytes` within
     * the resulting blob — i.e. all entries point at the same single
     * embedded TTF. Sufficient to exercise the parser's index range
     * check.
     */
    private fun buildSyntheticTtc(numFonts: Int, ttfBytes: ByteArray): ByteArray {
        val headerSize = 12 + numFonts * 4
        val ttfOffset = headerSize
        val total = headerSize + ttfBytes.size
        val out = ByteArray(total)
        // 'ttcf'
        out[0] = 0x74; out[1] = 0x74; out[2] = 0x63; out[3] = 0x66
        // version 0x00010000
        writeUInt32BE(out, 4, 0x00010000)
        // numFonts
        writeUInt32BE(out, 8, numFonts)
        // offsets[]: all point at the same embedded TTF
        for (i in 0 until numFonts) {
            writeUInt32BE(out, 12 + i * 4, ttfOffset)
        }
        // payload
        System.arraycopy(ttfBytes, 0, out, ttfOffset, ttfBytes.size)
        return out
    }

    private fun writeUInt32BE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }

    @Test
    fun `synthetic TTC with two faces resolves ttcIndex=0 and ttcIndex=1`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val ttc = buildSyntheticTtc(numFonts = 2, ttfBytes = liberationSansBytes())

        val face0 = mgr.makeFromData(ttc, ttcIndex = 0)
        val face1 = mgr.makeFromData(ttc, ttcIndex = 1)
        assertNotNull(face0, "TTC face 0 must resolve")
        assertNotNull(face1, "TTC face 1 must resolve (same TTF payload, different index)")
    }

    @Test
    fun `synthetic TTC rejects out-of-range ttcIndex`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val ttc = buildSyntheticTtc(numFonts = 2, ttfBytes = liberationSansBytes())

        assertNull(mgr.makeFromData(ttc, ttcIndex = 2))
        assertNull(mgr.makeFromData(ttc, ttcIndex = 99))
        assertNull(mgr.makeFromData(ttc, ttcIndex = -1))
    }

    @Test
    fun `non-TTC TTF passes through unchanged at ttcIndex=0`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        val ttf = liberationSansBytes()
        val face = mgr.makeFromData(ttf, ttcIndex = 0)
        assertNotNull(face, "Single-face TTF must load with ttcIndex=0")
    }

    @Test
    fun `truncated TTC header returns null`() {
        val mgr: SkFontMgr = JvmAwtFontMgr()
        // 'ttcf' magic + truncated header (only 8 bytes).
        val truncated = byteArrayOf(0x74, 0x74, 0x63, 0x66, 0x00, 0x01, 0x00, 0x00)
        assertEquals(null, mgr.makeFromData(truncated))
    }
}
