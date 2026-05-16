package org.skia.foundation


import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Phase R2.12 — covers [SkImage.readPixels] (buffer and pixmap overloads).
 *
 * The contract mirrors upstream's `SkImage::readPixels` (`SkImage.h:489-553`) :
 *  - dst pixels store the source's pixel block at `(srcX, srcY)` after
 *    colour-type / colour-space conversion ;
 *  - `false` is returned for invalid dst geometry (row-bytes too small,
 *    unknown colour type, empty info), an out-of-range `srcX`/`srcY`,
 *    or an empty source image.
 *  - colour-space conversion is performed when the source and dst tags
 *    differ.
 */
class SkImageReadPixelsTest {

    private fun gradientImage(w: Int, h: Int): SkImage {
        val bm = SkBitmap(w, h, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        for (y in 0 until h) for (x in 0 until w) {
            bm.pixels[y * w + x] = SkColorSetARGB(0xFF, x * 23 and 0xFF, y * 29 and 0xFF, (x + y) * 17 and 0xFF)
        }
        return bm.asImage()
    }

    private fun allocLE(byteSize: Int): ByteBuffer =
        ByteBuffer.allocate(byteSize).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun `readPixels into matching RGBA8888 buffer is identity`() {
        val src = gradientImage(4, 4)
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        val ok = src.readPixels(info, buf, info.minRowBytes())
        assertTrue(ok)
        // Decode the buffer into ARGB SkColor and compare per-pixel.
        for (y in 0 until 4) for (x in 0 until 4) {
            val off = y * info.minRowBytes() + x * 4
            val r = buf.get(off).toInt() and 0xFF
            val g = buf.get(off + 1).toInt() and 0xFF
            val b = buf.get(off + 2).toInt() and 0xFF
            val a = buf.get(off + 3).toInt() and 0xFF
            assertEquals(src.peekPixel(x, y), SkColorSetARGB(a, r, g, b))
        }
    }

    @Test
    fun `readPixels sub-rect at srcX srcY offsets the source`() {
        val src = gradientImage(8, 8)
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        val ok = src.readPixels(info, buf, info.minRowBytes(), srcX = 2, srcY = 3)
        assertTrue(ok)
        for (y in 0 until 4) for (x in 0 until 4) {
            val off = y * info.minRowBytes() + x * 4
            val r = buf.get(off).toInt() and 0xFF
            val g = buf.get(off + 1).toInt() and 0xFF
            val b = buf.get(off + 2).toInt() and 0xFF
            val a = buf.get(off + 3).toInt() and 0xFF
            assertEquals(src.peekPixel(2 + x, 3 + y), SkColorSetARGB(a, r, g, b))
        }
    }

    @Test
    fun `readPixels converts to BGRA8888 by swapping R and B`() {
        val src = gradientImage(2, 2)
        val info = SkImageInfo.Make(2, 2, SkColorType.kBGRA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        val ok = src.readPixels(info, buf, info.minRowBytes())
        assertTrue(ok)
        // BGRA layout : B, G, R, A.
        for (y in 0 until 2) for (x in 0 until 2) {
            val off = y * info.minRowBytes() + x * 4
            val b = buf.get(off).toInt() and 0xFF
            val g = buf.get(off + 1).toInt() and 0xFF
            val r = buf.get(off + 2).toInt() and 0xFF
            val a = buf.get(off + 3).toInt() and 0xFF
            assertEquals(src.peekPixel(x, y), SkColorSetARGB(a, r, g, b))
        }
    }

    @Test
    fun `readPixels into Alpha8 stores only the alpha channel`() {
        // Build an image where alpha varies per pixel.
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        bm.pixels[0] = SkColorSetARGB(0x10, 0xFF, 0xFF, 0xFF)
        bm.pixels[1] = SkColorSetARGB(0x80, 0xFF, 0xFF, 0xFF)
        bm.pixels[2] = SkColorSetARGB(0xA0, 0xFF, 0xFF, 0xFF)
        bm.pixels[3] = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
        val src = bm.asImage()
        val info = SkImageInfo.MakeA8(2, 2)
        val buf = allocLE(info.minRowBytes() * info.height)
        assertTrue(src.readPixels(info, buf, info.minRowBytes()))
        assertEquals(0x10.toByte(), buf.get(0))
        assertEquals(0x80.toByte(), buf.get(1))
        assertEquals(0xA0.toByte(), buf.get(info.minRowBytes()))
        assertEquals(0xFF.toByte(), buf.get(info.minRowBytes() + 1))
    }

    @Test
    fun `pixmap overload writes through provided buffer`() {
        val src = gradientImage(3, 3)
        val info = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        val pixmap = SkPixmap(info, buf, info.minRowBytes())
        assertTrue(src.readPixels(pixmap))
        for (y in 0 until 3) for (x in 0 until 3) {
            assertEquals(src.peekPixel(x, y), pixmap.getColor(x, y))
        }
    }

    @Test
    fun `readPixels with cross-colorspace conversion respects target tag`() {
        // A solid sRGB white image, read into a Rec.2020-tagged dst.
        val bm = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        bm.pixels.fill(SK_ColorWHITE)
        val src = bm.asImage()
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020)!!
        val info = SkImageInfo.Make(
            2, 2, SkColorType.kRGBA_8888,
            SkAlphaType.kUnpremul, rec2020,
        )
        val buf = allocLE(info.minRowBytes() * info.height)
        assertTrue(src.readPixels(info, buf, info.minRowBytes()))
        // White is a gamut-invariant — stays ≈ (255, 255, 255).
        for (y in 0 until 2) for (x in 0 until 2) {
            val off = y * info.minRowBytes() + x * 4
            val r = buf.get(off).toInt() and 0xFF
            val g = buf.get(off + 1).toInt() and 0xFF
            val b = buf.get(off + 2).toInt() and 0xFF
            assertTrue(abs(r - 255) <= 2)
            assertTrue(abs(g - 255) <= 2)
            assertTrue(abs(b - 255) <= 2)
        }
    }

    @Test
    fun `readPixels rejects too-small row bytes`() {
        val src = gradientImage(4, 4)
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        // 4 * 4 = 16 minRowBytes ; pass 8 to fail.
        assertFalse(src.readPixels(info, buf, dstRowBytes = 8))
    }

    @Test
    fun `readPixels rejects unknown colorType`() {
        val src = gradientImage(2, 2)
        val info = SkImageInfo.Make(2, 2, SkColorType.kUnknown, SkAlphaType.kUnknown)
        val buf = allocLE(64)
        assertFalse(src.readPixels(info, buf, 8))
    }

    @Test
    fun `readPixels rejects out-of-range srcX or srcY`() {
        val src = gradientImage(4, 4)
        val info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = allocLE(info.minRowBytes() * info.height)
        // |srcX| >= width(4).
        assertFalse(src.readPixels(info, buf, info.minRowBytes(), srcX = 4))
        assertFalse(src.readPixels(info, buf, info.minRowBytes(), srcX = -4))
        // |srcY| >= height(4).
        assertFalse(src.readPixels(info, buf, info.minRowBytes(), srcY = 4))
    }
}
