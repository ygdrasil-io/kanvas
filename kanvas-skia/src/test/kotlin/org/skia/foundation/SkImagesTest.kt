package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.ImageCodecs
import org.graphiks.kanvas.codec.ImageGeneratorImages
import org.skia.foundation.SkEncodedImageFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phase R2 batch3-B (+ R-suivi.12 cleanup) — unit tests for [SkImages]
 * static factories.
 *
 * Coverage :
 *  - [SkImages.RasterFromBitmap] snapshots the source pixels and rejects
 *    empty bitmaps.
 *  - [SkImages.RasterFromData] reads pixels out of a `ByteBuffer` honouring
 *    `rowBytes` (including padding) and rejects mis-sized buffers /
 *    unsupported colour types.
 *  - [ImageCodecs.DeferredFromEncodedData] decodes a PNG-encoded byte stream
 *    via the registered codec family.
 *  - [SkImages.RasterFromPixmap] / [SkImages.RasterFromPixmapCopy] snapshot
 *    a pixmap into a fresh [SkImage] (kanvas-skia copies eagerly — see
 *    [SkImages.RasterFromPixmap] KDoc).
 *  - [ImageGeneratorImages.DeferredFromGenerator] decodes a generator into a fresh
 *    [SkImage], matching the dedicated
 *    [ImageGeneratorImages.DeferredFromGenerator] entry point.
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
        val image = ImageCodecs.DeferredFromEncodedData(ByteBuffer.wrap(pngBytes))
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
        assertNull(ImageCodecs.DeferredFromEncodedData(nonsense))
    }

    // ─── R-suivi.12 — RasterFromPixmap / RasterFromPixmapCopy / DeferredFromGenerator ───

    /**
     * Build a `width × height` RGBA_8888 [SkPixmap] whose pixels encode
     * `((10 + x * 10), (20 + y * 20), 0x30, 0xFF)` (R, G, B, A) — same
     * shape as the [RasterFromData] fixture so the assertions can match.
     */
    private fun buildSamplePixmap(width: Int, height: Int): SkPixmap {
        val info = SkImageInfo.Make(width, height, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val rowBytes = info.minRowBytes()
        val buf = ByteBuffer.allocate(rowBytes * height).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until height) for (x in 0 until width) {
            val off = y * rowBytes + x * 4
            buf.put(off, (10 + x * 10).toByte())
            buf.put(off + 1, (20 + y * 20).toByte())
            buf.put(off + 2, 0x30.toByte())
            buf.put(off + 3, 0xFF.toByte())
        }
        return SkPixmap(info, buf, rowBytes)
    }

    @Test
    fun `RasterFromPixmap snapshots pixmap pixels and invokes release proc`() {
        val pixmap = buildSamplePixmap(3, 2)
        var released = false
        val image = SkImages.RasterFromPixmap(pixmap) { released = true }
        assertNotNull(image)
        assertEquals(3, image!!.width)
        assertEquals(2, image.height)
        assertTrue(released, "releaseProc must fire after the eager copy")
        for (y in 0 until 2) for (x in 0 until 3) {
            val expected = (0xFF shl 24) or
                ((10 + x * 10) shl 16) or
                ((20 + y * 20) shl 8) or
                0x30
            assertEquals(expected, image.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `RasterFromPixmap returns null for an empty pixmap`() {
        val empty = SkPixmap()
        assertNull(SkImages.RasterFromPixmap(empty))
    }

    @Test
    fun `RasterFromPixmapCopy snapshots pixmap pixels without release proc`() {
        val pixmap = buildSamplePixmap(2, 2)
        val image = SkImages.RasterFromPixmapCopy(pixmap)
        assertNotNull(image)
        for (y in 0 until 2) for (x in 0 until 2) {
            val expected = (0xFF shl 24) or
                ((10 + x * 10) shl 16) or
                ((20 + y * 20) shl 8) or
                0x30
            assertEquals(expected, image!!.peekPixel(x, y), "($x, $y)")
        }
    }

    @Test
    fun `RasterFromPixmapCopy returns null for an empty pixmap`() {
        assertNull(SkImages.RasterFromPixmapCopy(SkPixmap()))
    }

    /** Tiny in-memory generator yielding a deterministic 8888 pattern. */
    private class CheckerGenerator(info: SkImageInfo) : SkImageGenerator(info) {
        override fun onGetPixels(info: SkImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean {
            for (y in 0 until info.height) {
                for (x in 0 until info.width) {
                    val off = y * rowBytes + x * 4
                    // Black/white checkerboard.
                    val on = ((x xor y) and 1) == 0
                    val v: Byte = if (on) 0xFF.toByte() else 0x00.toByte()
                    pixels.put(off, v)       // R
                    pixels.put(off + 1, v)   // G
                    pixels.put(off + 2, v)   // B
                    pixels.put(off + 3, 0xFF.toByte()) // A
                }
            }
            return true
        }
    }

    @Test
    fun `DeferredFromGenerator decodes generator pixels into an SkImage`() {
        val info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val gen = CheckerGenerator(info)
        val image = ImageGeneratorImages.DeferredFromGenerator(gen)
        assertNotNull(image)
        assertEquals(2, image!!.width)
        assertEquals(2, image.height)
        // Verify the checker pattern survived the round-trip.
        val white = (0xFF shl 24) or (0xFF shl 16) or (0xFF shl 8) or 0xFF
        val black = (0xFF shl 24)
        assertEquals(white, image.peekPixel(0, 0))
        assertEquals(black, image.peekPixel(1, 0))
        assertEquals(black, image.peekPixel(0, 1))
        assertEquals(white, image.peekPixel(1, 1))
    }

    @Test
    fun `DeferredFromGenerator matches ImageGeneratorImages factory`() {
        val info = SkImageInfo.Make(3, 3, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul)
        val genA = CheckerGenerator(info)
        val genB = CheckerGenerator(info)
        val viaImages = ImageGeneratorImages.DeferredFromGenerator(genA)
        val viaDedicated = ImageGeneratorImages.DeferredFromGenerator(genB)
        assertNotNull(viaImages); assertNotNull(viaDedicated)
        for (y in 0 until 3) for (x in 0 until 3) {
            assertEquals(viaDedicated!!.peekPixel(x, y), viaImages!!.peekPixel(x, y), "($x, $y)")
        }
    }
}
