package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream

/**
 * R-suivi.23 verification suite for [SkWebpEncoder].
 *
 * Covers :
 *  - the [SkWebpEncoder.Compression.kLossless] path produces a real
 *    VP8L bitstream wrapped in a RIFF/WEBP container, and round-trips
 *    pixel-identical through the pure Kotlin WebP decoder ;
 *  - the RIFF header carries the correct magic bytes and the VP8L
 *    signature byte sits at offset 20 ;
 *  - the [SkWebpEncoder.Custom] hook short-circuits the built-in
 *    encoder when registered and falls back to it after `Custom(null)` ;
 *  - the [SkWebpEncoder.Compression.kLossy] path returns `null`
 *    as the documented unsupported portable encode path ;
 *  - the legacy [SkWebpEncoder.Options] invariants are still enforced.
 */
class SkWebpEncoderTest {

    @AfterEach
    fun clearCustomEncoder() {
        // Tests register Custom callbacks ; restore the built-in
        // before the next test runs so cross-test state can't leak.
        SkWebpEncoder.Custom(null)
    }

    @Test
    fun `lossless encode produces a valid RIFF WEBP VP8L header`() {
        val bitmap = makeGradient(16, 16)
        val bytes = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless, 100f),
        )
        assertNotNull(bytes)
        assertTrue(bytes!!.size > 20, "encoder must produce ≥ 21 bytes (RIFF + chunk hdr)")
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals('W'.code.toByte(), bytes[8])
        assertEquals('E'.code.toByte(), bytes[9])
        assertEquals('B'.code.toByte(), bytes[10])
        assertEquals('P'.code.toByte(), bytes[11])
        assertEquals('V'.code.toByte(), bytes[12])
        assertEquals('P'.code.toByte(), bytes[13])
        assertEquals('8'.code.toByte(), bytes[14])
        assertEquals('L'.code.toByte(), bytes[15])
        // VP8L signature byte sits right after the 4-byte chunk size.
        assertEquals(0x2F.toByte(), bytes[20])
    }

    @Test
    fun `lossless encode round-trips pixel-identical through pure Kotlin WebP codec`() {
        val src = makeGradient(16, 16)
        val bytes = SkWebpEncoder.Encode(
            src,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless, 100f),
        )
        assertNotNull(bytes, "lossless encode must succeed")
        val decoded = decodeWebp(bytes!!)
        assertEquals(src.width, decoded.width)
        assertEquals(src.height, decoded.height)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val expected = src.getPixel(x, y)
                val actual = decoded.getPixel(x, y)
                assertEquals(
                    expected,
                    actual,
                    "lossless WebP must round-trip ($x,$y) byte-identical : " +
                        "expected 0x${expected.toUInt().toString(16)}, got 0x${actual.toUInt().toString(16)}",
                )
            }
        }
    }

    @Test
    fun `lossless encode round-trips a single-color image`() {
        // 1-color image exercises the single-symbol Huffman path —
        // the green / red / blue / alpha codes each have only one
        // used symbol, so the simple-code branch with num_symbols=1
        // is taken. The decoder must still read the right pixel
        // values back even though no bits are emitted per pixel.
        val bitmap = SkBitmap(8, 8, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        val color = 0x80112233.toInt()  // alpha=0x80, R=0x11, G=0x22, B=0x33
        for (i in bitmap.pixels.indices) bitmap.pixels[i] = color
        val bytes = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertNotNull(bytes)
        val decoded = decodeWebp(bytes!!)
        for (y in 0 until 8) for (x in 0 until 8) {
            assertEquals(color, decoded.getPixel(x, y))
        }
    }

    @Test
    fun `lossless encode round-trips a 1x1 image`() {
        val bitmap = SkBitmap(1, 1, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        bitmap.pixels[0] = 0xFF7F00FF.toInt()
        val bytes = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertNotNull(bytes)
        val decoded = decodeWebp(bytes!!)
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
        assertEquals(0xFF7F00FF.toInt(), decoded.getPixel(0, 0))
    }

    @Test
    fun `lossless encode via SkImage matches encode via SkBitmap`() {
        val bitmap = makeGradient(8, 8)
        val image = SkImage.Make(bitmap)
        val viaBitmap = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        val viaImage = SkWebpEncoder.Encode(
            image,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertNotNull(viaBitmap)
        assertNotNull(viaImage)
        assertEquals(
            viaBitmap!!.toList(),
            viaImage!!.toList(),
            "bitmap and image paths must produce identical bytes",
        )
    }

    @Test
    fun `lossless encode to OutputStream agrees with encode to ByteArray`() {
        val bitmap = makeGradient(4, 4)
        val viaBytes = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )!!
        val baos = ByteArrayOutputStream()
        assertTrue(
            SkWebpEncoder.Encode(
                baos,
                bitmap,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
            ),
        )
        assertEquals(viaBytes.toList(), baos.toByteArray().toList())
    }

    @Test
    fun `lossy encode returns null without a Custom override`() {
        // VP8 lossy is out of scope for the pure-Kotlin port — see
        // the encoder kdoc. The kLossy path is reserved for future
        // work or a downstream Custom encoder.
        val bitmap = makeGradient(4, 4)
        assertNull(
            SkWebpEncoder.Encode(
                bitmap,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossy, 50f),
            ),
        )
    }

    @Test
    fun `Custom callback overrides the built-in encoder for every compression mode`() {
        // The Custom hook takes precedence over the built-in for any
        // requested compression, including kLossless — consumers may
        // want to plug in libwebp's optimized lossless encoder.
        val sentinel = byteArrayOf(1, 2, 3, 4, 5)
        var captured: Pair<Int, SkWebpEncoder.Compression>? = null
        SkWebpEncoder.Custom { bm, opts ->
            captured = bm.width to opts.compression
            sentinel
        }
        val bitmap = makeGradient(7, 11)
        val lossy = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossy),
        )
        assertEquals(sentinel.toList(), lossy!!.toList())
        assertEquals(7, captured!!.first)
        assertEquals(SkWebpEncoder.Compression.kLossy, captured!!.second)

        val lossless = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertEquals(sentinel.toList(), lossless!!.toList())
        assertEquals(SkWebpEncoder.Compression.kLossless, captured!!.second)
    }

    @Test
    fun `Custom null falls back to the built-in lossless encoder`() {
        SkWebpEncoder.Custom { _, _ -> byteArrayOf(0xCA.toByte(), 0xFE.toByte()) }
        val bitmap = makeGradient(4, 4)
        val withCustom = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertEquals(listOf(0xCA.toByte(), 0xFE.toByte()), withCustom!!.toList())

        SkWebpEncoder.Custom(null)
        val builtin = SkWebpEncoder.Encode(
            bitmap,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertNotNull(builtin)
        // Built-in must be a real WebP, not the 2-byte sentinel.
        assertTrue(builtin!!.size > 20)
        assertEquals('R'.code.toByte(), builtin[0])
    }

    @Test
    fun `Custom callback returning null surfaces as Encode null`() {
        // A soft failure from the Custom callback must propagate up to
        // the caller — the built-in is NOT used as a fallback (the
        // callback is authoritative once registered).
        SkWebpEncoder.Custom { _, _ -> null }
        val bitmap = makeGradient(4, 4)
        assertNull(
            SkWebpEncoder.Encode(
                bitmap,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
            ),
        )
    }

    @Test
    fun `EncodeAsData wraps the byte array in SkData`() {
        val bitmap = makeGradient(8, 8)
        val image = SkImage.Make(bitmap)
        val data = SkWebpEncoder.EncodeAsData(
            image,
            SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless),
        )
        assertNotNull(data)
        // Sanity : the wrapped bytes start with the RIFF header.
        assertEquals('R'.code.toByte(), data!!.byteAt(0))
        assertEquals('I'.code.toByte(), data.byteAt(1))
    }

    @Test
    fun `EncodeAsData returns null when the encoder returns null`() {
        val bitmap = makeGradient(4, 4)
        val image = SkImage.Make(bitmap)
        // Default options are kLossy → returns null without a Custom
        // hook installed.
        assertNull(SkWebpEncoder.EncodeAsData(image))
    }

    @Test
    fun `Encode to OutputStream returns false when underlying encode returns null`() {
        // No Custom hook + kLossy → null bytes → no write, false return.
        val bitmap = makeGradient(2, 2)
        val baos = ByteArrayOutputStream()
        assertFalse(
            SkWebpEncoder.Encode(
                baos,
                bitmap,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossy),
            ),
        )
        assertEquals(0, baos.size(), "no bytes must be written when encode fails")
    }

    @Test
    fun `Options defaults match the upstream struct`() {
        val opts = SkWebpEncoder.Options()
        assertEquals(SkWebpEncoder.Compression.kLossy, opts.compression)
        assertEquals(100f, opts.quality)
    }

    @Test
    fun `Options enforces quality in 0 to 100 inclusive`() {
        assertThrows(IllegalArgumentException::class.java) {
            SkWebpEncoder.Options(quality = -1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SkWebpEncoder.Options(quality = 100.01f)
        }
        SkWebpEncoder.Options(quality = 0f)
        SkWebpEncoder.Options(quality = 100f)
    }

    @Test
    fun `Compression enum carries both lossy and lossless`() {
        assertEquals(2, SkWebpEncoder.Compression.entries.size)
        assertEquals(
            SkWebpEncoder.Compression.kLossy,
            SkWebpEncoder.Compression.valueOf("kLossy"),
        )
        assertEquals(
            SkWebpEncoder.Compression.kLossless,
            SkWebpEncoder.Compression.valueOf("kLossless"),
        )
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

    private fun decodeWebp(bytes: ByteArray): SkBitmap {
        val codec = Codec.MakeFromData(bytes)
        assertNotNull(codec, "pure Kotlin WebP codec must decode the produced WebP")
        val (bitmap, result) = codec!!.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        return bitmap!!
    }
}
