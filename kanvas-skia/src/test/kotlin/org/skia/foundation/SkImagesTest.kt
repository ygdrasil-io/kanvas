package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.skia.codec.SkEncodedImageFormat
import java.nio.ByteBuffer

/**
 * Phase R2 batch3-B — unit tests for [SkImages] static factories.
 *
 * Coverage :
 *  - [SkImages.RasterFromBitmap] snapshots the source pixels and rejects
 *    empty bitmaps.
 *  - [SkImages.RasterFromData] reads pixels out of a `ByteBuffer` honouring
 *    `rowBytes` (including padding) and rejects mis-sized buffers /
 *    unsupported colour types.
 *  - [SkImages.DeferredFromEncodedData] decodes a PNG-encoded byte stream
 *    via the registered codec family.
 *  - The pixmap / generator stubs throw `NotImplementedError` (`TODO`)
 *    pending the parallel R2 batch3-A merge.
 */
class SkImagesTest {

    @Test
    fun `RasterFromBitmap returns an SkImage matching the source pixels`() {
        val bitmap = SkBitmap(4, 4)
        for (i in bitmap.pixels.indices) bitmap.pixels[i] = (0xFF shl 24) or (i * 16)
        val image = SkImages.RasterFromBitmap(bitmap)
        assertNotNull(image)
        assertEquals(4, image!!.width)
        assertEquals(4, image.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(bitmap.pixels[y * 4 + x], image.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `RasterFromBitmap rejects an empty (zero-sized) bitmap`() {
        val bitmap = SkBitmap(0, 4)
        assertNull(SkImages.RasterFromBitmap(bitmap))
    }

    @Test
    fun `RasterFromData reads pixels from a ByteBuffer at the supplied rowBytes`() {
        // Build a 3×2 RGBA_8888 buffer with extra padding per row to
        // exercise the rowBytes path. Per-row stride : 16 bytes
        // (12 bytes pixel data + 4 bytes padding).
        val width = 3
        val height = 2
        val rowBytes = 16
        val buf = ByteBuffer.allocate(rowBytes * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val off = y * rowBytes + x * 4
                // RGBA bytes : low → high = (R, G, B, A).
                buf.put(off, (10 + x * 10).toByte()) // R
                buf.put(off + 1, (20 + y * 20).toByte()) // G
                buf.put(off + 2, 0x30.toByte()) // B
                buf.put(off + 3, 0xFF.toByte()) // A
            }
            // Padding bytes intentionally garbage — must be ignored.
            buf.put(y * rowBytes + 12, 0xAB.toByte())
            buf.put(y * rowBytes + 13, 0xCD.toByte())
            buf.put(y * rowBytes + 14, 0xEF.toByte())
            buf.put(y * rowBytes + 15, 0x12.toByte())
        }
        val info = SkImageInfo.Make(width, height, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val image = SkImages.RasterFromData(info, buf, rowBytes)
        assertNotNull(image)
        for (y in 0 until height) for (x in 0 until width) {
            val expected = (0xFF shl 24) or
                ((10 + x * 10) shl 16) or
                ((20 + y * 20) shl 8) or
                0x30
            assertEquals(expected, image!!.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `RasterFromData rejects rowBytes smaller than one row`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = ByteBuffer.allocate(4 * 16)
        // 4 pixels × 4 bytes = 16 bytes/row required ; 8 is invalid.
        assertNull(SkImages.RasterFromData(info, buf, rowBytes = 8))
    }

    @Test
    fun `RasterFromData rejects an undersized ByteBuffer`() {
        val info = SkImageInfo.Make(4, 4, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val buf = ByteBuffer.allocate(8) // way too small
        assertNull(SkImages.RasterFromData(info, buf, rowBytes = 16))
    }

    @Test
    fun `RasterFromData rejects unsupported colour types`() {
        // F16 is unsupported by the byte-buffer raster path.
        val info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_F16Norm, SkAlphaType.kPremul)
        val buf = ByteBuffer.allocate(2 * 2 * 8)
        assertNull(SkImages.RasterFromData(info, buf, rowBytes = 16))
    }

    @Test
    fun `DeferredFromEncodedData decodes a PNG byte stream into a raster SkImage`() {
        // Encode a known 4×4 image to PNG, then round-trip through the
        // factory and verify pixel-equivalence.
        val source = SkBitmap(4, 4, colorType = SkColorType.kRGBA_8888)
        for (y in 0 until 4) for (x in 0 until 4) {
            source.pixels[y * 4 + x] =
                (0xFF shl 24) or ((x * 50) shl 16) or ((y * 60) shl 8) or 0x40
        }
        val pngBytes = source.asImage()
            .encodeToData(SkEncodedImageFormat.kPNG, quality = 100)!!
            .toByteArray()
        val image = SkImages.DeferredFromEncodedData(ByteBuffer.wrap(pngBytes))
        assertNotNull(image)
        assertEquals(4, image!!.width)
        assertEquals(4, image.height)
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(source.pixels[y * 4 + x], image.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `DeferredFromEncodedData returns null for non-image bytes`() {
        val nonsense = ByteBuffer.wrap("not an image".toByteArray())
        assertNull(SkImages.DeferredFromEncodedData(nonsense))
    }

    @Test
    fun `RasterFromPixmap stub throws TODO until SkPixmap lands`() {
        // R2-B : SkPixmap is being added by parallel batch3-A.
        assertThrows(NotImplementedError::class.java) {
            SkImages.RasterFromPixmap(Any())
        }
    }

    @Test
    fun `RasterFromPixmapCopy stub throws TODO until SkPixmap lands`() {
        assertThrows(NotImplementedError::class.java) {
            SkImages.RasterFromPixmapCopy(Any())
        }
    }

    @Test
    fun `DeferredFromGenerator stub throws TODO until SkImageGenerator lands`() {
        assertThrows(NotImplementedError::class.java) {
            SkImages.DeferredFromGenerator(Any())
        }
    }
}
