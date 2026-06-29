package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkData
import java.io.ByteArrayInputStream

/**
 * R3.10 verification suite for [AvifDecoder] — the [AvifDecoder.IsAvif]
 * sniff must match real `ftypavif` / `ftypavis` brand headers and reject
 * other byte streams. [AvifDecoder.Decode] is a stub : every overload
 * returns `null` until the libavif back-end lands (R-suivi.28).
 */
class AvifDecoderTest {

    /** Minimal 12-byte AVIF prefix : `00 00 00 20 'f' 't' 'y' 'p' 'a' 'v' 'i' 'f'`. */
    private fun avifHeader(brand: String = "avif"): ByteArray {
        require(brand.length == 4) { "brand must be 4 bytes" }
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            brand[0].code.toByte(), brand[1].code.toByte(),
            brand[2].code.toByte(), brand[3].code.toByte(),
        )
    }

    @Test
    fun `IsAvif matches the avif brand`() {
        assertTrue(AvifDecoder.IsAvif(avifHeader("avif")))
    }

    @Test
    fun `IsAvif matches the avis sequence brand`() {
        assertTrue(AvifDecoder.IsAvif(avifHeader("avis")))
    }

    @Test
    fun `IsAvif rejects HEIF and unrelated byte streams`() {
        // Plain HEIF brand should NOT match (HEIF is routed to a separate
        // decoder slot upstream).
        assertFalse(AvifDecoder.IsAvif(avifHeader("heic")))
        assertFalse(AvifDecoder.IsAvif(avifHeader("mif1")))
        // Random bytes.
        assertFalse(AvifDecoder.IsAvif("not-an-avif-file-at-all".toByteArray()))
        // Empty.
        assertFalse(AvifDecoder.IsAvif(ByteArray(0)))
        // Truncated below the 12-byte sniff window.
        assertFalse(AvifDecoder.IsAvif(byteArrayOf(0, 0, 0, 0x20, 'f'.code.toByte())))
    }

    @Test
    fun `IsAvif honours the length argument`() {
        // Full buffer is AVIF, but a length-limited sniff should refuse
        // when the brand falls outside the window.
        val data = avifHeader("avif")
        assertTrue(AvifDecoder.IsAvif(data, length = 12))
        assertFalse(AvifDecoder.IsAvif(data, length = 8))
    }

    @Test
    fun `IsAvif on stream rewinds the buffer`() {
        val data = avifHeader("avif") + ByteArray(8) { it.toByte() }
        val stream = ByteArrayInputStream(data)
        assertTrue(AvifDecoder.IsAvif(stream))
        // After IsAvif, the stream must still expose all bytes from offset 0.
        assertTrue(stream.available() == data.size)
    }

    @Test
    fun `Decode is stubbed to null for every overload`() {
        val bytes = avifHeader("avif")
        assertNull(AvifDecoder.Decode(bytes))
        assertNull(AvifDecoder.Decode(SkData.MakeWithCopy(bytes)))
        assertNull(AvifDecoder.Decode(ByteArrayInputStream(bytes)))
        // Even non-AVIF bytes return null — the contract is stubbed
        // unconditionally for R3.10.
        assertNull(AvifDecoder.Decode("garbage".toByteArray()))
    }
}
