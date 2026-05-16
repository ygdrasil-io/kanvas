package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * R-suivi.21 — verify the tag-table emitted by [SkICC.WriteToICC] is
 * parseable as a valid ICC v4 RGB display profile.
 *
 * Pins, by reading the bytes back :
 *  - tag count at offset 128 is > 0 and matches the table length,
 *  - every tag-table entry's offset + size lies within the buffer,
 *  - the four ICC tag types referenced (`mluc`, `XYZ `, `para`) are
 *    correctly stamped on the data blocks,
 *  - the mandatory v4 RGB display tags are all present (`desc`,
 *    `wtpt`, `rXYZ`, `gXYZ`, `bXYZ`, `rTRC`, `gTRC`, `bTRC`, `cprt`),
 *  - the white point equals the sum of the RGB primary XYZ tags
 *    (within the s15Fixed16 quantisation tolerance).
 */
class SkICCWriteToICCTagTableTest {

    private fun u32be(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    private fun s15Fixed16(b: ByteArray, off: Int): Float {
        // Treat the 32 bits as a signed two's complement value, then /65536.
        val raw = u32be(b, off).toInt()
        return raw.toFloat() / 65536f
    }

    private fun tagSig(b: ByteArray, off: Int): String =
        String(b, off, 4)

    @Test
    fun `WriteToICC emits a valid v4 header with non-zero tag count`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)

        // Profile size at offset 0 matches buffer length.
        assertEquals(bytes.size.toLong(), u32be(bytes, 0))
        // Profile version v4.3 at offset 8.
        assertEquals(0x04300000L, u32be(bytes, 8))
        // 'acsp' magic at offset 36.
        assertEquals("acsp", tagSig(bytes, 36))
        // Tag count immediately after the 128-byte header.
        val tagCount = u32be(bytes, SkICC.HEADER_SIZE).toInt()
        assertTrue(tagCount > 0, "tag count must be > 0")
        // Tag count is sane (we know there are 9 mandatory tags here).
        assertEquals(9, tagCount)
    }

    @Test
    fun `every tag-table entry's offset plus size lies within the buffer`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val tagCount = u32be(bytes, SkICC.HEADER_SIZE).toInt()
        val tableStart = SkICC.HEADER_SIZE + 4

        for (i in 0 until tagCount) {
            val entry = tableStart + i * SkICC.TAG_TABLE_ENTRY_SIZE
            val sig = tagSig(bytes, entry)
            val offset = u32be(bytes, entry + 4).toInt()
            val size = u32be(bytes, entry + 8).toInt()
            assertTrue(
                offset >= tableStart + tagCount * SkICC.TAG_TABLE_ENTRY_SIZE,
                "tag $sig offset $offset overlaps the tag table",
            )
            assertTrue(
                offset + size <= bytes.size,
                "tag $sig overruns buffer (offset=$offset size=$size, buf=${bytes.size})",
            )
        }
    }

    @Test
    fun `mandatory v4 RGB display tags are all present`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val signatures = collectTagSignatures(bytes)
        for (required in listOf("desc", "wtpt", "rXYZ", "gXYZ", "bXYZ", "rTRC", "gTRC", "bTRC", "cprt")) {
            assertTrue(required in signatures, "missing required tag $required (got $signatures)")
        }
    }

    @Test
    fun `XYZ tags carry the XYZ type signature and three s15Fixed16 values`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        for (sig in listOf("rXYZ", "gXYZ", "bXYZ", "wtpt")) {
            val (offset, size) = lookupTag(bytes, sig)
            assertEquals(20, size, "$sig data block must be 20 bytes")
            assertEquals("XYZ ", tagSig(bytes, offset), "$sig must carry an 'XYZ ' type sig")
            // Reserved 4 bytes at offset+4.
            assertEquals(0L, u32be(bytes, offset + 4), "$sig reserved word must be zero")
            // XYZ values readable as s15Fixed16.
            val x = s15Fixed16(bytes, offset + 8)
            val y = s15Fixed16(bytes, offset + 12)
            val z = s15Fixed16(bytes, offset + 16)
            assertTrue(x.isFinite() && y.isFinite() && z.isFinite())
        }
    }

    @Test
    fun `white point equals the sum of the RGB primary XYZ tags`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)

        val (rOff, _) = lookupTag(bytes, "rXYZ")
        val (gOff, _) = lookupTag(bytes, "gXYZ")
        val (bOff, _) = lookupTag(bytes, "bXYZ")
        val (wOff, _) = lookupTag(bytes, "wtpt")

        // Compute expected white point from the matrix sum, encode through
        // the same s15Fixed16 quantisation, then read back the wtpt bytes.
        val expectedWX = s15Fixed16(bytes, rOff + 8) + s15Fixed16(bytes, gOff + 8) + s15Fixed16(bytes, bOff + 8)
        val expectedWY = s15Fixed16(bytes, rOff + 12) + s15Fixed16(bytes, gOff + 12) + s15Fixed16(bytes, bOff + 12)
        val expectedWZ = s15Fixed16(bytes, rOff + 16) + s15Fixed16(bytes, gOff + 16) + s15Fixed16(bytes, bOff + 16)

        val actualWX = s15Fixed16(bytes, wOff + 8)
        val actualWY = s15Fixed16(bytes, wOff + 12)
        val actualWZ = s15Fixed16(bytes, wOff + 16)

        // Tolerance covers two s15Fixed16 quantisation steps (≈ 3e-5 each).
        val tol = 1e-4f
        assertTrue(Math.abs(expectedWX - actualWX) < tol, "wtpt X: $actualWX vs $expectedWX")
        assertTrue(Math.abs(expectedWY - actualWY) < tol, "wtpt Y: $actualWY vs $expectedWY")
        assertTrue(Math.abs(expectedWZ - actualWZ) < tol, "wtpt Z: $actualWZ vs $expectedWZ")
    }

    @Test
    fun `TRC tags carry the para type signature and encode the transfer fn`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        for (sig in listOf("rTRC", "gTRC", "bTRC")) {
            val (offset, size) = lookupTag(bytes, sig)
            assertEquals(40, size, "$sig data block must be 40 bytes (para type-4 curve)")
            assertEquals("para", tagSig(bytes, offset), "$sig must carry a 'para' type sig")
            // Function type field at offset+8..9 — value 4 (GABCDEF).
            assertEquals(0.toByte(), bytes[offset + 8])
            assertEquals(4.toByte(), bytes[offset + 9])
            // g (gamma) read back must match the sRGB gamma (2.4) within s15Fixed16 quantisation.
            val gamma = s15Fixed16(bytes, offset + 12)
            assertTrue(Math.abs(gamma - 2.4f) < 1e-3f, "TRC gamma: $gamma")
        }
    }

    @Test
    fun `desc and cprt tags carry the mluc type signature`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        for (sig in listOf("desc", "cprt")) {
            val (offset, _) = lookupTag(bytes, sig)
            assertEquals("mluc", tagSig(bytes, offset), "$sig must carry an 'mluc' type sig")
        }
    }

    @Test
    fun `different inputs produce different bytes`() {
        val srgb = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB)
        val p3 = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val linear = SkICC.WriteToICC(SkNamedTransferFn.kLinear, SkNamedGamut.kSRGB)
        // The matrix change must alter the XYZ tags ; the TF change must
        // alter the TRC tags.
        assertNotEquals(srgb.toList(), p3.toList())
        assertNotEquals(srgb.toList(), linear.toList())
    }

    /** Read all tag-table signatures into a set. */
    private fun collectTagSignatures(bytes: ByteArray): Set<String> {
        val tagCount = u32be(bytes, SkICC.HEADER_SIZE).toInt()
        val tableStart = SkICC.HEADER_SIZE + 4
        val out = HashSet<String>(tagCount * 2)
        for (i in 0 until tagCount) {
            out += tagSig(bytes, tableStart + i * SkICC.TAG_TABLE_ENTRY_SIZE)
        }
        return out
    }

    /** Find the tag-table entry for [sig] and return (offset, size). */
    private fun lookupTag(bytes: ByteArray, sig: String): Pair<Int, Int> {
        val tagCount = u32be(bytes, SkICC.HEADER_SIZE).toInt()
        val tableStart = SkICC.HEADER_SIZE + 4
        for (i in 0 until tagCount) {
            val entry = tableStart + i * SkICC.TAG_TABLE_ENTRY_SIZE
            if (tagSig(bytes, entry) == sig) {
                return Pair(u32be(bytes, entry + 4).toInt(), u32be(bytes, entry + 8).toInt())
            }
        }
        error("Tag $sig not found in tag table")
    }
}
