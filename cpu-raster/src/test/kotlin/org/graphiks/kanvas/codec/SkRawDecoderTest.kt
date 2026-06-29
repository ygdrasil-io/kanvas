package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import java.io.ByteArrayInputStream

/**
 * R3.10 verification suite for [RawDecoder] — the [RawDecoder.IsRaw]
 * sniff narrows upstream's "accept everything" predicate to TIFF-like
 * signatures (`II*\0` LE / `MM\0*` BE) so the stub doesn't blanket-claim
 * every byte buffer. [RawDecoder.Decode] is a stub : every overload
 * returns `null` until the libraw / dng_sdk back-end lands (R-suivi.28).
 */
class RawDecoderTest {

    /** Little-endian TIFF header : `'I' 'I' 2A 00`. */
    private fun tiffLE(): ByteArray =
        byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00)

    /** Big-endian TIFF header : `'M' 'M' 00 2A`. */
    private fun tiffBE(): ByteArray =
        byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08)

    @Test
    fun `IsRaw matches little-endian TIFF header`() {
        assertTrue(RawDecoder.IsRaw(tiffLE()))
    }

    @Test
    fun `IsRaw matches big-endian TIFF header`() {
        assertTrue(RawDecoder.IsRaw(tiffBE()))
    }

    @Test
    fun `IsRaw rejects unrelated byte streams`() {
        // PNG.
        assertFalse(RawDecoder.IsRaw(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )))
        // JPEG.
        assertFalse(RawDecoder.IsRaw(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        // BMP.
        assertFalse(RawDecoder.IsRaw(byteArrayOf(0x42, 0x4D, 0x00, 0x00)))
        // ICO.
        assertFalse(RawDecoder.IsRaw(byteArrayOf(0x00, 0x00, 0x01, 0x00)))
        // Random.
        assertFalse(RawDecoder.IsRaw("garbage".toByteArray()))
        // Empty.
        assertFalse(RawDecoder.IsRaw(ByteArray(0)))
        // Truncated below the 4-byte sniff.
        assertFalse(RawDecoder.IsRaw(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 0x2A)))
        // 'I' 'I' but wrong magic.
        assertFalse(RawDecoder.IsRaw(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 0x00, 0x00)))
    }

    @Test
    fun `IsRaw on stream rewinds the buffer`() {
        val data = tiffLE() + ByteArray(16) { it.toByte() }
        val stream = ByteArrayInputStream(data)
        assertTrue(RawDecoder.IsRaw(stream))
        assertTrue(stream.available() == data.size)
    }

    @Test
    fun `Decode is stubbed to null for every overload`() {
        val bytes = tiffLE()
        assertNull(RawDecoder.Decode(bytes))
        assertNull(RawDecoder.Decode(SkData.MakeWithCopy(bytes)))
        assertNull(RawDecoder.Decode(ByteArrayInputStream(bytes)))
        assertNull(RawDecoder.Decode("garbage".toByteArray()))
    }
}
