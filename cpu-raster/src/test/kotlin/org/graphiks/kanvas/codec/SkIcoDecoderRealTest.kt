package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkBitmap

/**
 * S7-A verification suite for the real (non-stub) [IcoDecoder.Decode]
 * implementation. We hand-build a 16x16 single-image ICO whose payload
 * is a freshly-encoded PNG and verify the decoder routes through the
 * registered PNG decoder and surfaces the encoded image back via
 * [Codec.getImage].
 */
class IcoDecoderRealTest {

    private val side = 16

    /** Build a 16x16 RGBA bitmap with a deterministic gradient. */
    private fun makeReferenceBitmap(): SkBitmap {
        val bm = SkBitmap(side, side)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val r = (x * 16).coerceAtMost(255)
                val g = (y * 16).coerceAtMost(255)
                val b = ((x + y) * 8).coerceAtMost(255)
                val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                bm.setPixel(x, y, color)
            }
        }
        return bm
    }

    /**
     * Wrap the supplied PNG payload in a single-entry ICO container :
     * 6-byte ICONDIR header + 16-byte ICONDIRENTRY + payload bytes.
     */
    private fun makeIcoWithPng(png: ByteArray): ByteArray {
        val header = byteArrayOf(
            0x00, 0x00, 0x01, 0x00,            // reserved=0, type=ICO
            0x01, 0x00,                         // count=1
        )
        val entry = ByteArray(16)
        entry[0] = side.toByte()                // width (16)
        entry[1] = side.toByte()                // height (16)
        entry[2] = 0                            // colorCount (0 = no palette)
        entry[3] = 0                            // reserved
        entry[4] = 1; entry[5] = 0              // planes = 1
        entry[6] = 32; entry[7] = 0             // bpp = 32
        // sizeInBytes (LE) :
        val size = png.size
        entry[8]  = (size and 0xFF).toByte()
        entry[9]  = ((size ushr 8) and 0xFF).toByte()
        entry[10] = ((size ushr 16) and 0xFF).toByte()
        entry[11] = ((size ushr 24) and 0xFF).toByte()
        // offset (LE) — header(6) + entry(16) = 22.
        val off = 22
        entry[12] = (off and 0xFF).toByte()
        entry[13] = ((off ushr 8) and 0xFF).toByte()
        entry[14] = 0
        entry[15] = 0

        return header + entry + png
    }

    @Test
    fun `Decode picks the embedded PNG payload and decodes it through the PNG codec`() {
        val source = makeReferenceBitmap()
        val png: ByteArray? = PngEncoder.encode(source)
        assertNotNull(png, "test prelude — PNG encoder must produce bytes")
        val ico = makeIcoWithPng(png!!)

        // Round-trip through the dispatcher to mirror real-world calls.
        val codec = IcoDecoder.Decode(ico)
        assertNotNull(codec, "ICO + embedded PNG must produce a codec")
        codec as Codec
        assertEquals(side, codec.getInfo().width)
        assertEquals(side, codec.getInfo().height)

        val (decoded, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(decoded)
        decoded as SkBitmap
        assertEquals(side, decoded.width)
        assertEquals(side, decoded.height)

        // PNG round-trip is exact for opaque 8888 pixels — every
        // pixel in the decoded bitmap should match the source. We
        // sample every fourth pixel to keep the assertion budget
        // bounded while still covering the gradient.
        for (y in 0 until side step 4) {
            for (x in 0 until side step 4) {
                assertEquals(
                    source.getPixel(x, y),
                    decoded.getPixel(x, y),
                    "pixel ($x, $y)",
                )
            }
        }
    }

    @Test
    fun `Decode picks the largest entry when multiple are present`() {
        val small = makeReferenceBitmap().let { src ->
            // Half-size 8x8 derived bitmap.
            val out = SkBitmap(8, 8)
            for (y in 0 until 8) {
                for (x in 0 until 8) out.setPixel(x, y, src.getPixel(x * 2, y * 2))
            }
            out
        }
        val big = makeReferenceBitmap()
val pngSmall: ByteArray = PngEncoder.encode(small)!!
val pngBig: ByteArray = PngEncoder.encode(big)!!

        // Header (6) + 2 entries (16 each) = 38 bytes.
        val header = byteArrayOf(
            0x00, 0x00, 0x01, 0x00,
            0x02, 0x00,
        )
        val entrySmall = makeEntry(8, 8, pngSmall.size, offset = 38)
        val entryBig = makeEntry(side, side, pngBig.size, offset = 38 + pngSmall.size)
        val ico = header + entrySmall + entryBig + pngSmall + pngBig

        val codec = IcoDecoder.Decode(ico)
        assertNotNull(codec)
        codec as Codec
        // Largest by area wins → 16x16, not 8x8.
        assertEquals(side, codec.getInfo().width)
        assertEquals(side, codec.getInfo().height)
    }

    private fun makeEntry(w: Int, h: Int, sizeInBytes: Int, offset: Int): ByteArray {
        val e = ByteArray(16)
        e[0] = w.toByte()
        e[1] = h.toByte()
        e[2] = 0
        e[3] = 0
        e[4] = 1; e[5] = 0
        e[6] = 32; e[7] = 0
        e[8]  = (sizeInBytes and 0xFF).toByte()
        e[9]  = ((sizeInBytes ushr 8) and 0xFF).toByte()
        e[10] = ((sizeInBytes ushr 16) and 0xFF).toByte()
        e[11] = ((sizeInBytes ushr 24) and 0xFF).toByte()
        e[12] = (offset and 0xFF).toByte()
        e[13] = ((offset ushr 8) and 0xFF).toByte()
        e[14] = ((offset ushr 16) and 0xFF).toByte()
        e[15] = ((offset ushr 24) and 0xFF).toByte()
        return e
    }
}
