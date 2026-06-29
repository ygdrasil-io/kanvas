package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import java.io.ByteArrayInputStream

/**
 * R3.10 + S7-A verification suite for [IcoDecoder] — the
 * [IcoDecoder.IsIco] sniff must match the 6-byte ICONDIR header for
 * both ICO (type=1) and CUR (type=2) variants and reject other byte
 * streams. [IcoDecoder.Decode] returns `null` for malformed inputs ;
 * the real-decode path is exercised by [IcoDecoderRealTest].
 */
class IcoDecoderTest {

    /** ICONDIR header : `00 00 01 00 count_lo count_hi` (ICO variant). */
    private fun icoHeader(count: Int = 1): ByteArray = byteArrayOf(
        0x00, 0x00, 0x01, 0x00,
        (count and 0xFF).toByte(), ((count ushr 8) and 0xFF).toByte(),
    )

    /** CURSORDIR header : `00 00 02 00 count_lo count_hi` (CUR variant). */
    private fun curHeader(count: Int = 1): ByteArray = byteArrayOf(
        0x00, 0x00, 0x02, 0x00,
        (count and 0xFF).toByte(), ((count ushr 8) and 0xFF).toByte(),
    )

    @Test
    fun `IsIco matches the ICO type-1 header`() {
        assertTrue(IcoDecoder.IsIco(icoHeader(count = 1)))
        assertTrue(IcoDecoder.IsIco(icoHeader(count = 3)))
    }

    @Test
    fun `IsIco matches the CUR type-2 header`() {
        assertTrue(IcoDecoder.IsIco(curHeader(count = 1)))
    }

    @Test
    fun `IsIco rejects unrelated byte streams`() {
        // PNG.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )))
        // JPEG.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        // Wrong type field : 03 is neither ICO nor CUR.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(0x00, 0x00, 0x03, 0x00, 0x01, 0x00)))
        // Zero-entry directory : malformed, must reject.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x00, 0x00)))
        // Non-zero reserved field.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(0x01, 0x00, 0x01, 0x00, 0x01, 0x00)))
        // Random.
        assertFalse(IcoDecoder.IsIco("garbage".toByteArray()))
        // Empty.
        assertFalse(IcoDecoder.IsIco(ByteArray(0)))
        // Truncated below 6 bytes.
        assertFalse(IcoDecoder.IsIco(byteArrayOf(0x00, 0x00, 0x01, 0x00, 0x01)))
    }

    @Test
    fun `IsIco on stream rewinds the buffer`() {
        val data = icoHeader(count = 1) + ByteArray(8) { it.toByte() }
        val stream = ByteArrayInputStream(data)
        assertTrue(IcoDecoder.IsIco(stream))
        assertTrue(stream.available() == data.size)
    }

    @Test
    fun `Decode returns null for malformed inputs`() {
        // Header-only ICO (no directory entries actually present) — the
        // sniff passes (`count >= 1`) but the directory walk fails.
        val headerOnly = icoHeader(count = 1)
        assertNull(IcoDecoder.Decode(headerOnly))
        // Also fails for non-ICO byte streams.
        assertNull(IcoDecoder.Decode("garbage".toByteArray()))
        assertNull(IcoDecoder.Decode(ByteArrayInputStream("garbage".toByteArray())))
        assertNull(IcoDecoder.Decode(SkData.MakeWithCopy("garbage".toByteArray())))
    }
}
