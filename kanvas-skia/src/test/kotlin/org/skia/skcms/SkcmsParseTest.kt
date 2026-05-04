package org.skia.skcms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.util.zip.Inflater
import kotlin.math.abs

/**
 * Phase F2 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the ICC parser
 * on real-world inputs:
 *  - the Rec.2020 profile from the DM reference PNGs (extracted from the
 *    iCCP chunk of `bigrect.png`);
 *  - constructed bad inputs (truncated, wrong magic, non-D50 illuminant).
 */
class SkcmsParseTest {

    private val rec2020Profile: ByteArray by lazy { extractIccProfileFromPng("bigrect.png") }

    // -----------------------------------------------------------------------
    // Big-endian readers
    // -----------------------------------------------------------------------

    @Test
    fun `readBigU16 swaps bytes`() {
        val b = byteArrayOf(0x12, 0x34)
        assertEquals(0x1234, readBigU16(b, 0))
    }

    @Test
    fun `readBigU32 swaps bytes`() {
        val b = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        assertEquals(0x12345678, readBigU32(b, 0))
    }

    @Test
    fun `readBigFixed reads s15_16 as Float`() {
        // 1.0 in s15.16 = 0x00010000
        val one = byteArrayOf(0x00, 0x01, 0x00, 0x00)
        assertEquals(1.0f, readBigFixed(one, 0))

        // 0.5 in s15.16 = 0x00008000 = bytes 00 00 80 00 (big-endian).
        val half = byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x00)
        assertEquals(0.5f, readBigFixed(half, 0))
    }

    // -----------------------------------------------------------------------
    // Real Rec.2020 profile from bigrect.png
    // -----------------------------------------------------------------------

    @Test
    fun `parses the Rec_2020 profile from bigrect_png`() {
        val profile = skcmsParse(rec2020Profile)
        assertNotNull(profile, "Rec.2020 profile should parse")

        val p = profile!!
        assertEquals(SkcmsSignature.RGB.value, p.dataColorSpace)
        assertEquals(SkcmsSignature.XYZ.value, p.pcs)
        assertTrue(p.hasTrc)
        assertTrue(p.hasToXYZD50)
        assertEquals(9, p.tagCount, "expected 9 tags in DM unified Rec.2020")
    }

    @Test
    fun `Rec_2020 profile TRC matches kRec2020 within snap tolerance`() {
        val profile = skcmsParse(rec2020Profile)!!
        // All three TRCs share the same para tag in the DM profile.
        for (i in 0 until 3) {
            val curve = profile.trc[i]
            assertNotNull(curve, "trc[$i]")
            assertTrue(curve is SkcmsCurve.Parametric, "trc[$i] should be Parametric")
            val tf = (curve as SkcmsCurve.Parametric).parametric
            // Compare element-wise to kRec2020 within transfer-fn tolerance.
            assertTrue(abs(tf.g - SkNamedTransferFn.kRec2020.g) < 1e-3f, "g")
            assertTrue(abs(tf.a - SkNamedTransferFn.kRec2020.a) < 1e-3f, "a")
            assertTrue(abs(tf.b - SkNamedTransferFn.kRec2020.b) < 1e-3f, "b")
            assertTrue(abs(tf.c - SkNamedTransferFn.kRec2020.c) < 1e-3f, "c")
            assertTrue(abs(tf.d - SkNamedTransferFn.kRec2020.d) < 1e-3f, "d")
        }
    }

    @Test
    fun `Rec_2020 profile toXYZD50 matches kRec2020-gamut within tolerance`() {
        val profile = skcmsParse(rec2020Profile)!!
        for (r in 0 until 3) for (c in 0 until 3) {
            val want = SkNamedGamut.kRec2020.vals[r][c]
            val got = profile.toXYZD50.vals[r][c]
            assertTrue(abs(want - got) < 0.01f, "[$r][$c] want=$want got=$got")
        }
    }

    // -----------------------------------------------------------------------
    // Error paths
    // -----------------------------------------------------------------------

    @Test
    fun `truncated buffer returns null`() {
        assertNull(skcmsParse(rec2020Profile.copyOfRange(0, 100)))
    }

    @Test
    fun `wrong header signature returns null`() {
        val tampered = rec2020Profile.copyOf()
        // Header signature is at offset 36 ('acsp').
        tampered[36] = 'X'.code.toByte()
        assertNull(skcmsParse(tampered))
    }

    @Test
    fun `non-D50 illuminant returns null`() {
        val tampered = rec2020Profile.copyOf()
        // Illuminant_X at offset 68. Set to 2.0 (s15.16 = 0x00020000).
        tampered[68] = 0x00; tampered[69] = 0x02; tampered[70] = 0x00; tampered[71] = 0x00
        assertNull(skcmsParse(tampered))
    }

    @Test
    fun `version above 4 returns null`() {
        val tampered = rec2020Profile.copyOf()
        // Version major byte at offset 8. Set to 5.
        tampered[8] = 0x05
        assertNull(skcmsParse(tampered))
    }

    // -----------------------------------------------------------------------
    // Helpers — extract ICC profile from a PNG iCCP chunk.
    // -----------------------------------------------------------------------

    /**
     * Walk a PNG looking for the iCCP chunk and return its inflated profile
     * bytes. Used to feed [skcmsParse] real ICC bytes from a reference
     * PNG without needing a hand-rolled fixture.
     */
    private fun extractIccProfileFromPng(name: String): ByteArray {
        val pngBytes = SkcmsParseTest::class.java.classLoader
            .getResourceAsStream("original-888/$name")?.readBytes()
            ?: error("missing original-888/$name on classpath")

        require(pngBytes.size >= 8) { "PNG too short" }
        val sig = pngBytes.copyOfRange(0, 8)
        require(sig.contentEquals(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ))) { "bad PNG signature" }

        val dis = DataInputStream(pngBytes.inputStream())
        dis.skipBytes(8)
        while (dis.available() > 0) {
            val length = dis.readInt()
            val typeBytes = ByteArray(4).also { dis.readFully(it) }
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = ByteArray(length).also { dis.readFully(it) }
            dis.readInt() // CRC
            if (type == "iCCP") {
                // iCCP layout: profile name (NUL-terminated) + compression byte + zlib data.
                var nameEnd = 0
                while (nameEnd < data.size && data[nameEnd] != 0.toByte()) nameEnd++
                val compressed = data.copyOfRange(nameEnd + 2, data.size)
                val inflater = Inflater()
                inflater.setInput(compressed)
                val out = ByteArray(64 * 1024)
                val len = inflater.inflate(out)
                inflater.end()
                return out.copyOfRange(0, len)
            }
        }
        error("no iCCP chunk in $name")
    }
}
