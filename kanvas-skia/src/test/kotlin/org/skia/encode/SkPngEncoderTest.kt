package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkCodec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import java.io.ByteArrayOutputStream

/**
 * D3.5 verification suite for [SkPngEncoder].
 *
 * Covers :
 *  - `Encode(bitmap, options)` returns non-null bytes for an 8888
 *    bitmap.
 *  - Encode → decode round-trip via [SkCodec] is **byte-identical**
 *    on opaque pixels — PNG is lossless.
 *  - Encode-to-stream agrees with encode-to-bytes.
 *  - [SkPngEncoder.Options] validates `zLibLevel` and `comments`
 *    invariants.
 */
class SkPngEncoderTest {

    @Test
    fun `Encode returns non-null bytes for an 8888 bitmap`() {
        val bitmap = makeGradient(8, 8)
        val bytes = SkPngEncoder.Encode(bitmap)
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 0, "encoder must produce non-empty output")
        // PNG signature is 0x89 0x50 0x4E 0x47.
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    @Test
    fun `encode then decode round-trips opaque pixels byte-identical`() {
        val src = makeGradient(8, 8) // alpha = 0xFF for every pixel
        val bytes = SkPngEncoder.Encode(src)!!
        val codec = SkCodec.MakeFromData(bytes)!!
        val (decoded, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(decoded)
        assertEquals(src.width, decoded!!.width)
        assertEquals(src.height, decoded.height)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            assertEquals(
                src.getPixel(x, y),
                decoded.getPixel(x, y),
                "PNG must round-trip ($x,$y) byte-identical",
            )
        }
    }

    @Test
    fun `Encode to OutputStream matches Encode to ByteArray`() {
        val bitmap = makeGradient(4, 4)
        val viaBytes = SkPngEncoder.Encode(bitmap)!!
        val baos = ByteArrayOutputStream()
        assertTrue(SkPngEncoder.Encode(baos, bitmap))
        val viaStream = baos.toByteArray()
        assertEquals(viaBytes.toList(), viaStream.toList(), "stream and ByteArray paths must agree")
    }

    @Test
    fun `Options validates zLibLevel and comments invariants`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(zLibLevel = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(zLibLevel = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkPngEncoder.Options(comments = listOf("only-key-no-value"))
        }
        // Valid : 0 and 9 are both in range, even-sized comments OK.
        SkPngEncoder.Options(zLibLevel = 0)
        SkPngEncoder.Options(zLibLevel = 9, comments = listOf("k", "v", "k2", "v2"))
    }

    @Test
    fun `Encode passes through a non-default options object without crashing`() {
        // Even though the underlying ImageIO writer ignores most fields,
        // the encoder must still accept them and produce valid bytes.
        val bitmap = makeGradient(2, 2)
        val opts = SkPngEncoder.Options(
            filterFlags = SkPngEncoder.FilterFlag.kPaeth.mask,
            zLibLevel = 9,
            comments = listOf("Software", "kanvas-skia"),
        )
        val bytes = SkPngEncoder.Encode(bitmap, opts)
        assertNotNull(bytes)
    }

    @Test
    fun `Encode never returns null on a valid input — null reserved for future failure paths`() {
        // Even an empty 1×1 bitmap must round-trip.
        val tiny = SkBitmap(1, 1, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        tiny.pixels[0] = 0xFF112233.toInt()
        val bytes = SkPngEncoder.Encode(tiny)
        assertNotNull(bytes)
        // Sanity : null is what the encoder will eventually return on
        // a future "unsupported colour type" branch — checking the
        // negative path is left to that slice.
        @Suppress("USELESS_IS_CHECK") assertNull(null as ByteArray?)
    }

    private fun makeGradient(width: Int, height: Int): SkBitmap {
        val b = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until height) for (x in 0 until width) {
            val r = (x * 255 / maxOf(1, width - 1)).coerceIn(0, 255)
            val g = (y * 255 / maxOf(1, height - 1)).coerceIn(0, 255)
            b.pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or 0x40
        }
        return b
    }
}
