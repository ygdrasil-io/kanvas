package org.skia.encode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream

/**
 * R2.20 verification suite for [SkWebpEncoder].
 *
 * The encoder body is a stub today — the JVM has no built-in WebP
 * encoder and TwelveMonkeys' `imageio-webp` ships only a decoder.
 * These tests pin :
 *   - the stub returns `null` / `false` for every overload,
 *   - the surface accepts every well-formed input without throwing,
 *   - the [SkWebpEncoder.Options] `quality` invariant is enforced,
 *   - the [SkWebpEncoder.Compression] enum keeps both lossy / lossless
 *     spellings for future-proofing call sites.
 *
 * A future "WebP encoder body" slice will turn the null returns into
 * real bytes — the tests above will then be tightened to require a
 * VP8 magic header.
 */
class SkWebpEncoderTest {

    @Test
    fun `Encode(SkImage) returns null on the stub`() {
        val bitmap = makeGradient(4, 4)
        val image = SkImage.Make(bitmap)
        assertNull(SkWebpEncoder.Encode(image))
        assertNull(
            SkWebpEncoder.Encode(
                image,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossless, 80f),
            ),
        )
    }

    @Test
    fun `Encode(SkBitmap) returns null on the stub`() {
        val bitmap = makeGradient(4, 4)
        assertNull(SkWebpEncoder.Encode(bitmap))
        assertNull(
            SkWebpEncoder.Encode(
                bitmap,
                SkWebpEncoder.Options(SkWebpEncoder.Compression.kLossy, 50f),
            ),
        )
    }

    @Test
    fun `Encode to OutputStream returns false on the stub and leaves the stream untouched`() {
        val bitmap = makeGradient(2, 2)
        val baos = ByteArrayOutputStream()
        assertFalse(SkWebpEncoder.Encode(baos, bitmap))
        assertEquals(0, baos.size(), "stub must not write any bytes")
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
        // Both endpoints are valid.
        SkWebpEncoder.Options(quality = 0f)
        SkWebpEncoder.Options(quality = 100f)
    }

    @Test
    fun `Compression enum carries both lossy and lossless`() {
        assertEquals(2, SkWebpEncoder.Compression.entries.size)
        // Both spellings must round-trip valueOf.
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
}
