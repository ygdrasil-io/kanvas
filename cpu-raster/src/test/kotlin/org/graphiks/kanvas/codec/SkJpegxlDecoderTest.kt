package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import java.io.ByteArrayInputStream

/**
 * R3.10 verification suite for [SkJpegxlDecoder] — the
 * [SkJpegxlDecoder.IsJpegxl] sniff must match both the raw codestream
 * (`FF 0A`) and the ISO-BMFF wrapper signatures, and reject other byte
 * streams. [SkJpegxlDecoder.Decode] is a stub : every overload returns
 * `null` until the libjxl back-end lands (R-suivi.28).
 */
class SkJpegxlDecoderTest {

    /** Raw JPEG-XL codestream signature : `FF 0A …`. */
    private fun rawCodestream(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0x0A, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05)

    /** ISO-BMFF JPEG-XL signature box : 12 bytes. */
    private fun isoBmffSignature(): ByteArray = byteArrayOf(
        0x00, 0x00, 0x00, 0x0C,
        0x4A, 0x58, 0x4C, 0x20,
        0x0D, 0x0A, 0x87.toByte(), 0x0A,
    )

    @Test
    fun `IsJpegxl matches the raw codestream FF 0A signature`() {
        assertTrue(SkJpegxlDecoder.IsJpegxl(rawCodestream()))
    }

    @Test
    fun `IsJpegxl matches the ISO-BMFF wrapper signature`() {
        assertTrue(SkJpegxlDecoder.IsJpegxl(isoBmffSignature()))
        // Extra trailing bytes still match.
        assertTrue(SkJpegxlDecoder.IsJpegxl(isoBmffSignature() + byteArrayOf(0x12, 0x34)))
    }

    @Test
    fun `IsJpegxl rejects unrelated byte streams`() {
        // PNG header.
        assertFalse(SkJpegxlDecoder.IsJpegxl(byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )))
        // JPEG SOI.
        assertFalse(SkJpegxlDecoder.IsJpegxl(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        // Random.
        assertFalse(SkJpegxlDecoder.IsJpegxl("garbage".toByteArray()))
        // Empty.
        assertFalse(SkJpegxlDecoder.IsJpegxl(ByteArray(0)))
        // A single FF byte is not enough.
        assertFalse(SkJpegxlDecoder.IsJpegxl(byteArrayOf(0xFF.toByte())))
    }

    @Test
    fun `IsJpegxl on stream rewinds the buffer`() {
        val data = isoBmffSignature() + ByteArray(20) { it.toByte() }
        val stream = ByteArrayInputStream(data)
        assertTrue(SkJpegxlDecoder.IsJpegxl(stream))
        assertTrue(stream.available() == data.size)
    }

    @Test
    fun `Decode is stubbed to null for every overload`() {
        val bytes = rawCodestream()
        assertNull(SkJpegxlDecoder.Decode(bytes))
        assertNull(SkJpegxlDecoder.Decode(SkData.MakeWithCopy(bytes)))
        assertNull(SkJpegxlDecoder.Decode(ByteArrayInputStream(bytes)))
        assertNull(SkJpegxlDecoder.Decode("garbage".toByteArray()))
    }
}
