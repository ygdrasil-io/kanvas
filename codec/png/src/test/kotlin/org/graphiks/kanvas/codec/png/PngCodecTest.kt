package org.graphiks.kanvas.codec.png

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.test.CodecNegativeFixtures
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkICC
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader
import java.util.zip.CRC32
import java.util.zip.Deflater

class PngCodecTest {

    @Test
    fun `rejects invalid signature`() {
        val cases = listOf(
            CodecNegativeFixtures.invalidMagic("empty PNG input", ByteArray(0)),
            CodecNegativeFixtures.invalidMagic("ASCII non-PNG payload", "not-a-png"),
        )

        for (case in cases) {
            assertFalse(PngCodec.Decoder.matches(case.data), case.name)
            assertNull(PngCodec.Decoder.make(case.data), case.name)
        }
    }

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "png" })
    }

    @Test
    fun `decodes RGBA 8-bit pixels with all PNG filters`() {
        val rows = listOf(
            intArrayOf(argb(0xFF, 0x10, 0x20, 0x30), argb(0x80, 0x40, 0x50, 0x60), argb(0x00, 0x70, 0x80, 0x90)),
            intArrayOf(argb(0xFE, 0x11, 0x22, 0x33), argb(0x7F, 0x44, 0x55, 0x66), argb(0x01, 0x77, 0x88, 0x99)),
            intArrayOf(argb(0xCC, 0x13, 0x26, 0x39), argb(0x99, 0x4C, 0x5F, 0x62), argb(0x66, 0x71, 0x82, 0x93)),
            intArrayOf(argb(0xAA, 0x15, 0x2A, 0x3F), argb(0x55, 0x48, 0x5B, 0x6E), argb(0x33, 0x79, 0x8A, 0x9B)),
            intArrayOf(argb(0x12, 0x17, 0x2C, 0x41), argb(0x34, 0x4A, 0x5D, 0x70), argb(0x56, 0x7B, 0x8C, 0x9D)),
        )
        val codec = PngCodec.Decoder.make(
            png(width = 3, height = 5, colorType = 6, rows = rows, filters = intArrayOf(0, 1, 2, 3, 4)),
        )

        assertNotNull(codec)
        assertTrue(codec is PngCodec)
        assertEquals(SkEncodedImageFormat.kPNG, codec!!.getEncodedFormat())
        assertEquals(3, codec.getInfo().width)
        assertEquals(5, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                assertEquals(rows[y][x], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes RGB 8-bit pixels as opaque RGBA`() {
        val rows = listOf(
            intArrayOf(argb(0xFF, 0xFF, 0x00, 0x00), argb(0xFF, 0x00, 0xFF, 0x00)),
            intArrayOf(argb(0xFF, 0x00, 0x00, 0xFF), argb(0xFF, 0xFF, 0xFF, 0xFF)),
        )
        val codec = PngCodec.Decoder.make(
            png(width = 2, height = 2, colorType = 2, rows = rows, filters = intArrayOf(0, 1)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(rows[0][0], bitmap!!.getPixel(0, 0))
        assertEquals(rows[0][1], bitmap.getPixel(1, 0))
        assertEquals(rows[1][0], bitmap.getPixel(0, 1))
        assertEquals(rows[1][1], bitmap.getPixel(1, 1))
    }

    @Test
    fun `decodes RGB tRNS color as transparent`() {
        val rows = listOf(
            intArrayOf(argb(0xFF, 0x10, 0x20, 0x30), argb(0xFF, 0x40, 0x50, 0x60)),
        )
        val codec = PngCodec.Decoder.make(
            png(
                width = 2,
                height = 1,
                colorType = 2,
                rows = rows,
                filters = intArrayOf(0),
                transparency = u16Row(0x0010, 0x0020, 0x0030),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(argb(0x00, 0x10, 0x20, 0x30), bitmap!!.getPixel(0, 0))
        assertEquals(argb(0xFF, 0x40, 0x50, 0x60), bitmap.getPixel(1, 0))
    }

    @Test
    fun `decodes Adam7 interlaced RGBA 8-bit pixels`() {
        val rows = List(9) { y ->
            IntArray(9) { x ->
                argb(
                    a = (0x40 + x * 7 + y * 5) and 0xFF,
                    r = (0x10 + x * 17) and 0xFF,
                    g = (0x20 + y * 19) and 0xFF,
                    b = (0x30 + x * 11 + y * 13) and 0xFF,
                )
            }
        }
        val codec = PngCodec.Decoder.make(
            adam7Png(width = 9, height = 9, colorType = 6, rows = rows, filters = intArrayOf(0, 1, 2, 3, 4)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                assertEquals(rows[y][x], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes indexed 8-bit palette pixels as opaque RGBA`() {
        val palette = intArrayOf(
            argb(0xFF, 0x10, 0x20, 0x30),
            argb(0xFF, 0x40, 0x50, 0x60),
            argb(0xFF, 0x70, 0x80, 0x90),
        )
        val indexes = listOf(
            byteArrayOf(0, 1, 2),
            byteArrayOf(2, 1, 0),
        )
        val codec = PngCodec.Decoder.make(
            indexedPng(width = 3, height = 2, palette = palette, indexes = indexes, filters = intArrayOf(0, 1)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(palette[0], bitmap!!.getPixel(0, 0))
        assertEquals(palette[1], bitmap.getPixel(1, 0))
        assertEquals(palette[2], bitmap.getPixel(2, 0))
        assertEquals(palette[2], bitmap.getPixel(0, 1))
        assertEquals(palette[1], bitmap.getPixel(1, 1))
        assertEquals(palette[0], bitmap.getPixel(2, 1))
    }

    @Test
    fun `decodes indexed palette tRNS alpha`() {
        val palette = intArrayOf(
            argb(0xFF, 0xFF, 0x00, 0x00),
            argb(0xFF, 0x00, 0xFF, 0x00),
            argb(0xFF, 0x00, 0x00, 0xFF),
        )
        val codec = PngCodec.Decoder.make(
            indexedPng(
                width = 3,
                height = 1,
                palette = palette,
                transparency = byteArrayOf(0xFF.toByte(), 0x00, 0x80.toByte()),
                indexes = listOf(byteArrayOf(0, 1, 2)),
                filters = intArrayOf(0),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(argb(0xFF, 0xFF, 0x00, 0x00), bitmap!!.getPixel(0, 0))
        assertEquals(argb(0x00, 0x00, 0xFF, 0x00), bitmap.getPixel(1, 0))
        assertEquals(argb(0x80, 0x00, 0x00, 0xFF), bitmap.getPixel(2, 0))
    }

    @Test
    fun `decodes indexed packed palette bit depths`() {
        val palette = intArrayOf(
            argb(0xFF, 0x00, 0x00, 0x00),
            argb(0xFF, 0x40, 0x50, 0x60),
            argb(0xFF, 0x80, 0x90, 0xA0),
            argb(0xFF, 0xC0, 0xD0, 0xE0),
            argb(0xFF, 0x11, 0x22, 0x33),
            argb(0xFF, 0x44, 0x55, 0x66),
            argb(0xFF, 0x77, 0x88, 0x99),
            argb(0xFF, 0xAA, 0xBB, 0xCC),
            argb(0xFF, 0x12, 0x34, 0x56),
            argb(0xFF, 0x23, 0x45, 0x67),
            argb(0xFF, 0x34, 0x56, 0x78),
            argb(0xFF, 0x45, 0x67, 0x89),
            argb(0xFF, 0x56, 0x78, 0x9A),
            argb(0xFF, 0x67, 0x89, 0xAB),
            argb(0xFF, 0x78, 0x9A, 0xBC),
            argb(0xFF, 0x89, 0xAB, 0xCD),
        )

        for (bitDepth in intArrayOf(1, 2, 4)) {
            val indexes = listOf(
                intArrayOf(0, (1 shl bitDepth) - 1, 1, 0, 1),
                intArrayOf(1, 0, (1 shl bitDepth) - 1, 1, 0),
            )
            val codec = PngCodec.Decoder.make(
                indexedPng(
                    width = 5,
                    height = 2,
                    palette = palette.copyOf(1 shl bitDepth),
                    indexes = indexes.map { packSamples(it, bitDepth) },
                    filters = intArrayOf(0, 1),
                    bitDepth = bitDepth,
                ),
            )!!

            val (bitmap, result) = codec.getImage()
            assertEquals(Codec.Result.kSuccess, result, "bitDepth=$bitDepth")
            assertNotNull(bitmap)
            for (y in indexes.indices) {
                for (x in indexes[y].indices) {
                    assertEquals(palette[indexes[y][x]], bitmap!!.getPixel(x, y), "bitDepth=$bitDepth x=$x y=$y")
                }
            }
        }
    }

    @Test
    fun `decodes Adam7 interlaced packed palette pixels`() {
        val palette = intArrayOf(
            argb(0xFF, 0x00, 0x00, 0x00),
            argb(0x80, 0x40, 0x50, 0x60),
            argb(0x40, 0x80, 0x90, 0xA0),
            argb(0x00, 0xC0, 0xD0, 0xE0),
            argb(0xFF, 0x11, 0x22, 0x33),
            argb(0xE0, 0x22, 0x33, 0x44),
            argb(0xC0, 0x33, 0x44, 0x55),
            argb(0xA0, 0x44, 0x55, 0x66),
            argb(0x80, 0x55, 0x66, 0x77),
            argb(0x60, 0x66, 0x77, 0x88),
            argb(0x40, 0x77, 0x88, 0x99),
            argb(0x20, 0x88, 0x99, 0xAA),
            argb(0xFF, 0x99, 0xAA, 0xBB),
            argb(0xCC, 0xAA, 0xBB, 0xCC),
            argb(0x99, 0xBB, 0xCC, 0xDD),
            argb(0x66, 0xCC, 0xDD, 0xEE),
        )
        for (bitDepth in intArrayOf(1, 2, 4)) {
            val colorCount = 1 shl bitDepth
            val indexes = List(7) { y ->
                IntArray(10) { x -> (x + y * 2) % colorCount }
            }
            val codec = PngCodec.Decoder.make(
                adam7IndexedPng(
                    width = 10,
                    height = 7,
                    palette = palette.copyOf(colorCount),
                    indexes = indexes,
                    filters = intArrayOf(0, 1, 2, 3, 4),
                    bitDepth = bitDepth,
                ),
            )!!

            val (bitmap, result) = codec.getImage()
            assertEquals(Codec.Result.kSuccess, result, "bitDepth=$bitDepth")
            assertNotNull(bitmap)
            for (y in indexes.indices) {
                for (x in indexes[y].indices) {
                    assertEquals(palette[indexes[y][x]], bitmap!!.getPixel(x, y), "bitDepth=$bitDepth x=$x y=$y")
                }
            }
        }
    }

    @Test
    fun `decodes Adam7 interlaced grayscale bit depths`() {
        for (bitDepth in intArrayOf(1, 2, 4, 8)) {
            val max = (1 shl bitDepth) - 1
            val samples = List(9) { y ->
                IntArray(11) { x -> (x * 3 + y * 5) % (max + 1) }
            }
            val codec = PngCodec.Decoder.make(
                adam7GrayscalePng(
                    width = 11,
                    height = 9,
                    samples = samples,
                    filters = intArrayOf(0, 1, 2, 3, 4),
                    bitDepth = bitDepth,
                ),
            )!!

            val (bitmap, result) = codec.getImage()
            assertEquals(Codec.Result.kSuccess, result, "bitDepth=$bitDepth")
            assertNotNull(bitmap)
            for (y in samples.indices) {
                for (x in samples[y].indices) {
                    val gray = samples[y][x] * 255 / max
                    assertEquals(argb(0xFF, gray, gray, gray), bitmap!!.getPixel(x, y), "bitDepth=$bitDepth x=$x y=$y")
                }
            }
        }
    }

    @Test
    fun `decodes Adam7 interlaced RGB 8-bit pixels`() {
        val rows = List(8) { y ->
            IntArray(10) { x ->
                argb(
                    a = 0xFF,
                    r = (0x10 + x * 13 + y * 3) and 0xFF,
                    g = (0x20 + x * 5 + y * 17) and 0xFF,
                    b = (0x30 + x * 11 + y * 7) and 0xFF,
                )
            }
        }
        val codec = PngCodec.Decoder.make(
            adam7Png(
                width = 10,
                height = 8,
                colorType = 2,
                rows = rows,
                filters = intArrayOf(0, 1, 2, 3, 4),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                assertEquals(rows[y][x], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes Adam7 interlaced grayscale alpha 8-bit pixels`() {
        val rows = List(8) { y ->
            IntArray(10) { x ->
                val gray = (0x10 + x * 19 + y * 7) and 0xFF
                val alpha = (0x30 + x * 11 + y * 13) and 0xFF
                argb(alpha, gray, gray, gray)
            }
        }
        val codec = PngCodec.Decoder.make(
            adam7GrayscaleAlphaPng(
                width = 10,
                height = 8,
                rows = rows,
                filters = intArrayOf(0, 1, 2, 3, 4),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in rows.indices) {
            for (x in rows[y].indices) {
                assertEquals(rows[y][x], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes grayscale 8-bit pixels as opaque RGBA`() {
        val rows = listOf(
            byteArrayOf(0x00, 0x40, 0x80.toByte()),
            byteArrayOf(0xC0.toByte(), 0xFF.toByte(), 0x20),
        )
        val codec = PngCodec.Decoder.make(
            grayscalePng(width = 3, height = 2, rows = rows, filters = intArrayOf(0, 1), bitDepth = 8),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(argb(0xFF, 0x00, 0x00, 0x00), bitmap!!.getPixel(0, 0))
        assertEquals(argb(0xFF, 0x40, 0x40, 0x40), bitmap.getPixel(1, 0))
        assertEquals(argb(0xFF, 0x80, 0x80, 0x80), bitmap.getPixel(2, 0))
        assertEquals(argb(0xFF, 0xC0, 0xC0, 0xC0), bitmap.getPixel(0, 1))
        assertEquals(argb(0xFF, 0xFF, 0xFF, 0xFF), bitmap.getPixel(1, 1))
        assertEquals(argb(0xFF, 0x20, 0x20, 0x20), bitmap.getPixel(2, 1))
    }

    @Test
    fun `decodes grayscale tRNS sample as transparent`() {
        val rows = listOf(
            byteArrayOf(0x00, 0x40, 0x80.toByte()),
        )
        val codec = PngCodec.Decoder.make(
            grayscalePng(
                width = 3,
                height = 1,
                rows = rows,
                filters = intArrayOf(0),
                bitDepth = 8,
                transparency = u16Row(0x0040),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(argb(0xFF, 0x00, 0x00, 0x00), bitmap!!.getPixel(0, 0))
        assertEquals(argb(0x00, 0x40, 0x40, 0x40), bitmap.getPixel(1, 0))
        assertEquals(argb(0xFF, 0x80, 0x80, 0x80), bitmap.getPixel(2, 0))
    }

    @Test
    fun `decodes packed grayscale bit depths`() {
        for (bitDepth in intArrayOf(1, 2, 4)) {
            val max = (1 shl bitDepth) - 1
            val samples = listOf(
                intArrayOf(0, max, 1, 0, max),
                intArrayOf(max, 0, max / 2, 1, 0),
            )
            val codec = PngCodec.Decoder.make(
                grayscalePng(
                    width = 5,
                    height = 2,
                    rows = samples.map { packSamples(it, bitDepth) },
                    filters = intArrayOf(0, 1),
                    bitDepth = bitDepth,
                ),
            )!!

            val (bitmap, result) = codec.getImage()
            assertEquals(Codec.Result.kSuccess, result, "bitDepth=$bitDepth")
            assertNotNull(bitmap)
            for (y in samples.indices) {
                for (x in samples[y].indices) {
                    val gray = samples[y][x] * 255 / max
                    assertEquals(argb(0xFF, gray, gray, gray), bitmap!!.getPixel(x, y), "bitDepth=$bitDepth x=$x y=$y")
                }
            }
        }
    }

    @Test
    fun `decodes grayscale alpha 8-bit pixels`() {
        val pixels = listOf(
            intArrayOf(argb(0xFF, 0x10, 0x10, 0x10), argb(0x80, 0x40, 0x40, 0x40)),
            intArrayOf(argb(0x00, 0x70, 0x70, 0x70), argb(0x20, 0xA0, 0xA0, 0xA0)),
        )
        val codec = PngCodec.Decoder.make(
            grayscaleAlphaPng(width = 2, height = 2, rows = pixels, filters = intArrayOf(0, 1)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in pixels.indices) {
            for (x in pixels[y].indices) {
                assertEquals(pixels[y][x], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes grayscale 16-bit pixels into premul F16`() {
        val rows = listOf(
            u16Row(0x0000, 0x8000),
            u16Row(0xFFFF, 0x4000),
        )
        val codec = PngCodec.Decoder.make(
            grayscalePng(width = 2, height = 2, rows = rows, filters = intArrayOf(0, 1), bitDepth = 16),
        )!!

        assertEquals(SkColorType.kRGBA_F16Norm, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kPremul, codec.getInfo().alphaType)
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertF16(bitmap!!, 0, 0, 0f, 0f, 0f, 1f)
        assertF16(bitmap, 1, 0, 0x8000 / 65535f, 0x8000 / 65535f, 0x8000 / 65535f, 1f)
        assertF16(bitmap, 0, 1, 1f, 1f, 1f, 1f)
        assertF16(bitmap, 1, 1, 0x4000 / 65535f, 0x4000 / 65535f, 0x4000 / 65535f, 1f)
    }

    @Test
    fun `decodes grayscale 16-bit tRNS sample into transparent F16`() {
        val codec = PngCodec.Decoder.make(
            grayscalePng(
                width = 2,
                height = 1,
                rows = listOf(u16Row(0x1234, 0x8000)),
                filters = intArrayOf(0),
                bitDepth = 16,
                transparency = u16Row(0x1234),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertF16(bitmap!!, 0, 0, 0f, 0f, 0f, 0f)
        assertF16(bitmap, 1, 0, 0x8000 / 65535f, 0x8000 / 65535f, 0x8000 / 65535f, 1f)
    }

    @Test
    fun `decodes RGBA 16-bit pixels into premul F16`() {
        val rows = listOf(
            longArrayOf(rgba64(0xFFFF, 0x8000, 0x0000, 0x8000), rgba64(0x0000, 0x4000, 0xFFFF, 0xFFFF)),
            longArrayOf(rgba64(0x2000, 0x6000, 0xA000, 0x0000), rgba64(0x1111, 0x2222, 0x3333, 0x4000)),
        )
        val codec = PngCodec.Decoder.make(
            truecolor16Png(width = 2, height = 2, colorType = 6, rows = rows, filters = intArrayOf(0, 1)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(SkColorType.kRGBA_F16Norm, bitmap!!.colorType)
        assertF16(
            bitmap,
            0,
            0,
            (0xFFFF / 65535f) * (0x8000 / 65535f),
            (0x8000 / 65535f) * (0x8000 / 65535f),
            0f,
            0x8000 / 65535f,
        )
        assertF16(bitmap, 1, 0, 0f, 0x4000 / 65535f, 1f, 1f)
        assertF16(bitmap, 0, 1, 0f, 0f, 0f, 0f)
        assertF16(
            bitmap,
            1,
            1,
            (0x1111 / 65535f) * (0x4000 / 65535f),
            (0x2222 / 65535f) * (0x4000 / 65535f),
            (0x3333 / 65535f) * (0x4000 / 65535f),
            0x4000 / 65535f,
        )
    }

    @Test
    fun `decodes RGB and grayscale alpha 16-bit pixels into premul F16`() {
        val rgbCodec = PngCodec.Decoder.make(
            truecolor16Png(
                width = 1,
                height = 1,
                colorType = 2,
                rows = listOf(longArrayOf(rgb48(0x1111, 0x8000, 0xFFFF))),
                filters = intArrayOf(0),
            ),
        )!!
        val (rgbBitmap, rgbResult) = rgbCodec.getImage()
        assertEquals(Codec.Result.kSuccess, rgbResult)
        assertF16(rgbBitmap!!, 0, 0, 0x1111 / 65535f, 0x8000 / 65535f, 1f, 1f)

        val grayAlphaCodec = PngCodec.Decoder.make(
            grayscaleAlpha16Png(
                width = 1,
                height = 1,
                rows = listOf(u16Row(0x8000, 0x4000)),
                filters = intArrayOf(0),
            ),
        )!!
        val (grayAlphaBitmap, grayAlphaResult) = grayAlphaCodec.getImage()
        val gray = 0x8000 / 65535f
        val alpha = 0x4000 / 65535f
        assertEquals(Codec.Result.kSuccess, grayAlphaResult)
        assertF16(grayAlphaBitmap!!, 0, 0, gray * alpha, gray * alpha, gray * alpha, alpha)
    }

    @Test
    fun `decodes RGB 16-bit tRNS color into transparent F16`() {
        val codec = PngCodec.Decoder.make(
            truecolor16Png(
                width = 2,
                height = 1,
                colorType = 2,
                rows = listOf(
                    longArrayOf(rgb48(0x1111, 0x2222, 0x3333), rgb48(0x8000, 0x4000, 0xFFFF)),
                ),
                filters = intArrayOf(0),
                transparency = u16Row(0x1111, 0x2222, 0x3333),
            ),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertF16(bitmap!!, 0, 0, 0f, 0f, 0f, 0f)
        assertF16(bitmap, 1, 0, 0x8000 / 65535f, 0x4000 / 65535f, 1f, 1f)
    }

    @Test
    fun `converts 16-bit RGBA natural F16 decode into requested RGBA 8888`() {
        val codec = PngCodec.Decoder.make(
            truecolor16Png(
                width = 2,
                height = 1,
                colorType = 6,
                rows = listOf(
                    longArrayOf(rgba64(0xFFFF, 0x8000, 0x0000, 0x8000), rgba64(0x0000, 0x4000, 0xFFFF, 0xFFFF)),
                ),
                filters = intArrayOf(0),
            ),
        )!!
        val requested = SkImageInfo.Make(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = codec.getInfo().colorSpace,
        )
        val dst = SkBitmap(2, 1, requested.colorSpace, requested.colorType)

        assertEquals(Codec.Result.kSuccess, codec.getPixels(requested, dst))
        assertEquals(argb(0x80, 0xFF, 0x80, 0x00), dst.getPixel(0, 0))
        assertEquals(argb(0xFF, 0x00, 0x40, 0xFF), dst.getPixel(1, 0))
    }

    @Test
    fun `converts 8-bit RGBA natural 8888 decode into requested F16`() {
        val codec = PngCodec.Decoder.make(
            png(
                width = 2,
                height = 1,
                colorType = 6,
                rows = listOf(intArrayOf(argb(0x80, 0x40, 0x80, 0xC0), argb(0x00, 0x11, 0x22, 0x33))),
                filters = intArrayOf(0),
            ),
        )!!
        val requested = SkImageInfo.Make(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_F16Norm,
            alphaType = SkAlphaType.kPremul,
            colorSpace = codec.getInfo().colorSpace,
        )
        val dst = SkBitmap(2, 1, requested.colorSpace, requested.colorType)

        assertEquals(Codec.Result.kSuccess, codec.getPixels(requested, dst))
        assertF16(dst, 0, 0, (0x40 / 255f) * (0x80 / 255f), (0x80 / 255f) * (0x80 / 255f), (0xC0 / 255f) * (0x80 / 255f), 0x80 / 255f)
        assertF16(dst, 1, 0, 0f, 0f, 0f, 0f)
    }

    @Test
    fun `converts 8-bit PNG into additional bitmap backed color types`() {
        val codec = PngCodec.Decoder.make(
            png(
                width = 1,
                height = 1,
                colorType = 6,
                rows = listOf(intArrayOf(argb(0xFF, 0xFF, 0x00, 0x00))),
                filters = intArrayOf(0),
            ),
        )!!
        val cases = listOf(
            Triple(SkColorType.kBGRA_8888, SkAlphaType.kUnpremul, argb(0xFF, 0xFF, 0x00, 0x00)),
            Triple(SkColorType.kARGB_4444, SkAlphaType.kPremul, argb(0xFF, 0xFF, 0x00, 0x00)),
            Triple(SkColorType.kAlpha_8, SkAlphaType.kPremul, argb(0xFF, 0x00, 0x00, 0x00)),
            Triple(SkColorType.kRGB_565, SkAlphaType.kOpaque, argb(0xFF, 0xFF, 0x00, 0x00)),
            Triple(SkColorType.kGray_8, SkAlphaType.kOpaque, argb(0xFF, 0x4C, 0x4C, 0x4C)),
        )

        for ((colorType, alphaType, expected) in cases) {
            val requested = SkImageInfo.Make(
                width = 1,
                height = 1,
                colorType = colorType,
                alphaType = alphaType,
                colorSpace = codec.getInfo().colorSpace,
            )
            val dst = SkBitmap(1, 1, requested.colorSpace, requested.colorType)

            assertEquals(Codec.Result.kSuccess, codec.getPixels(requested, dst), colorType.name)
            assertEquals(expected, dst.getPixel(0, 0), colorType.name)
        }
    }

    @Test
    fun `keeps 16-bit PNG extra color conversions unsupported`() {
        val codec = PngCodec.Decoder.make(
            truecolor16Png(
                width = 1,
                height = 1,
                colorType = 6,
                rows = listOf(longArrayOf(rgba64(0xFFFF, 0x0000, 0x0000, 0xFFFF))),
                filters = intArrayOf(0),
            ),
        )!!
        val requested = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGB_565,
            alphaType = SkAlphaType.kOpaque,
            colorSpace = codec.getInfo().colorSpace,
        )
        val dst = SkBitmap(1, 1, requested.colorSpace, requested.colorType)

        assertEquals(Codec.Result.kInvalidConversion, codec.getPixels(requested, dst))
    }

    @Test
    fun `rejects unsupported requested PNG color conversion`() {
        val codec = PngCodec.Decoder.make(
            png(
                width = 1,
                height = 1,
                colorType = 6,
                rows = listOf(intArrayOf(argb(0xFF, 0x01, 0x02, 0x03))),
                filters = intArrayOf(0),
            ),
        )!!
        val requested = SkImageInfo.Make(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_F32,
            alphaType = SkAlphaType.kPremul,
            colorSpace = codec.getInfo().colorSpace,
        )
        val dst = SkBitmap(1, 1, requested.colorSpace, requested.colorType)

        assertEquals(Codec.Result.kInvalidConversion, codec.getPixels(requested, dst))
    }

    @Test
    fun `iCCP with unsupported synthetic profile falls back to sRGB`() {
        val codec = PngCodec.Decoder.make(
            grayscalePng(
                width = 1,
                height = 1,
                rows = listOf(byteArrayOf(0x40)),
                filters = intArrayOf(0),
                bitDepth = 8,
                iccp = iccpChunkData("synthetic", profileBytes = "not an icc profile".toByteArray()),
            ),
        )

        assertNotNull(codec)
        assertNull(codec!!.getICCProfile())
        assertTrue(codec.getInfo().colorSpace.isSRGB())
    }

    @Test
    fun `iCCP with valid RGB profile exposes parsed ICC profile`() {
        val iccBytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val codec = PngCodec.Decoder.make(
            grayscalePng(
                width = 1,
                height = 1,
                rows = listOf(byteArrayOf(0x40)),
                filters = intArrayOf(0),
                bitDepth = 8,
                iccp = iccpChunkData("display-p3", profileBytes = iccBytes),
            ),
        )

        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile)
        assertEquals(0x52474220, profile!!.dataColorSpace)
        assertEquals(0x58595A20, profile.pcs)
        assertEquals(9, profile.tagCount)
        assertTrue(profile.hasTrc)
        assertTrue(profile.hasToXYZD50)
        assertNotNull(profile.buffer)
        assertEquals(profile.size, profile.buffer!!.size)
        assertEquals(iccBytes.size, profile.size)
        assertFalse(codec.getInfo().colorSpace.isSRGB())
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(
                SkNamedGamut.kDisplayP3[row, column],
                codec.getInfo().colorSpace.toXYZD50[row, column],
                1f / 65_536f,
            )
        }
    }

    @Test
    fun `sRGB and gAMA chunks synthesize ICC when sRGB is present`() {
        val data = pngFromChunks(
            "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
            "sRGB" to byteArrayOf(0),
            "IDAT" to deflate(byteArrayOf(0, 0x40)),
            "IEND" to ByteArray(0),
        )

        val codec = PngCodec.Decoder.make(data)

        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile, "sRGB chunk must synthesize ICC when no iCCP is present")
        assertTrue(codec.getInfo().colorSpace.isSRGB())
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(argb(0xFF, 0x40, 0x40, 0x40), bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `sRGB and gAMA chunks synthesize ICC when both are present`() {
        val data = pngFromChunks(
            "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
            "gAMA" to u32Chunk(45_455),
            "sRGB" to byteArrayOf(0),
            "IDAT" to deflate(byteArrayOf(0, 0x40)),
            "IEND" to ByteArray(0),
        )

        val codec = PngCodec.Decoder.make(data)

        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile, "sRGB+gAMA must synthesize ICC when no iCCP is present")
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(argb(0xFF, 0x40, 0x40, 0x40), bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `gAMA chunk alone synthesizes ICC profile`() {
        val data = pngFromChunks(
            "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
            "gAMA" to u32Chunk(45_455),
            "IDAT" to deflate(byteArrayOf(0, 0x40)),
            "IEND" to ByteArray(0),
        )

        val codec = PngCodec.Decoder.make(data)

        assertNotNull(codec)
        val profile = codec!!.getICCProfile()
        assertNotNull(profile, "gAMA chunk must synthesize ICC when no iCCP is present")
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(argb(0xFF, 0x40, 0x40, 0x40), bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `cHRM chunk without sRGB or gAMA does not synthesize ICC`() {
        val data = pngFromChunks(
            "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
            "cHRM" to chrmChunk(
                whiteX = 31_270,
                whiteY = 32_900,
                redX = 64_000,
                redY = 33_000,
                greenX = 30_000,
                greenY = 60_000,
                blueX = 15_000,
                blueY = 6_000,
            ),
            "IDAT" to deflate(byteArrayOf(0, 0x40)),
            "IEND" to ByteArray(0),
        )

        val codec = PngCodec.Decoder.make(data)

        assertNotNull(codec)
        assertNull(codec!!.getICCProfile())
        assertTrue(codec.getInfo().colorSpace.isSRGB())
        val (bitmap, result) = codec.getImage()
        assertEquals(Codec.Result.kSuccess, result)
        assertEquals(argb(0xFF, 0x40, 0x40, 0x40), bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `rejects malformed gAMA cHRM and sRGB chunks`() {
        assertNull(
            PngCodec.Decoder.make(
                pngFromChunks(
                    "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
                    "gAMA" to byteArrayOf(0, 0, 0),
                    "IDAT" to deflate(byteArrayOf(0, 0x40)),
                    "IEND" to ByteArray(0),
                ),
            ),
        )
        assertNull(
            PngCodec.Decoder.make(
                pngFromChunks(
                    "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
                    "cHRM" to ByteArray(31),
                    "IDAT" to deflate(byteArrayOf(0, 0x40)),
                    "IEND" to ByteArray(0),
                ),
            ),
        )
        assertNull(
            PngCodec.Decoder.make(
                pngFromChunks(
                    "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
                    "sRGB" to byteArrayOf(4),
                    "IDAT" to deflate(byteArrayOf(0, 0x40)),
                    "IEND" to ByteArray(0),
                ),
            ),
        )
    }

    @Test
    fun `rejects malformed iCCP chunk`() {
        assertNull(
            PngCodec.Decoder.make(
                grayscalePng(
                    width = 1,
                    height = 1,
                    rows = listOf(byteArrayOf(0x40)),
                    filters = intArrayOf(0),
                    bitDepth = 8,
                    iccp = iccpChunkData("synthetic", compressionMethod = 1, profileBytes = byteArrayOf(1, 2, 3)),
                ),
            ),
        )
    }

    @Test
    fun `rejects malformed tRNS chunks`() {
        assertNull(
            PngCodec.Decoder.make(
                grayscalePng(
                    width = 1,
                    height = 1,
                    rows = listOf(byteArrayOf(0x40)),
                    filters = intArrayOf(0),
                    bitDepth = 8,
                    transparency = byteArrayOf(0x40),
                ),
            ),
        )
        assertNull(
            PngCodec.Decoder.make(
                png(
                    width = 1,
                    height = 1,
                    colorType = 2,
                    rows = listOf(intArrayOf(argb(0xFF, 0x01, 0x02, 0x03))),
                    filters = intArrayOf(0),
                    transparency = byteArrayOf(0x00, 0x01),
                ),
            ),
        )
    }

    @Test
    fun `rejects indexed PNG without palette`() {
        assertNull(
            PngCodec.Decoder.make(
                indexedPng(
                    width = 1,
                    height = 1,
                    palette = intArrayOf(argb(0xFF, 0x01, 0x02, 0x03)),
                    indexes = listOf(byteArrayOf(0)),
                    filters = intArrayOf(0),
                    includePalette = false,
                ),
            ),
        )
    }

    @Test
    fun `rejects invalid PLTE chunk`() {
        assertNull(
            PngCodec.Decoder.make(
                indexedPng(
                    width = 1,
                    height = 1,
                    palette = intArrayOf(argb(0xFF, 0x01, 0x02, 0x03)),
                    indexes = listOf(byteArrayOf(0)),
                    filters = intArrayOf(0),
                    paletteBytes = byteArrayOf(0x01, 0x02),
                ),
            ),
        )
    }

    @Test
    fun `rejects palette index outside PLTE`() {
        val codec = PngCodec.Decoder.make(
            indexedPng(
                width = 1,
                height = 1,
                palette = intArrayOf(argb(0xFF, 0x01, 0x02, 0x03)),
                indexes = listOf(byteArrayOf(1)),
                filters = intArrayOf(0),
            ),
        )

        assertNotNull(codec)
        val (_, result) = codec!!.getImage()
        assertEquals(Codec.Result.kErrorInInput, result)
    }

    @Test
    fun `rejects corrupted chunk CRC`() {
        val data = png(
            width = 1,
            height = 1,
            colorType = 6,
            rows = listOf(intArrayOf(argb(0xFF, 0x01, 0x02, 0x03))),
            filters = intArrayOf(0),
        )
        data[data.lastIndex] = (data.last().toInt() xor 0x01).toByte()

        assertNull(PngCodec.Decoder.make(data))
    }

    @Test
    fun `rejects trailing data after IEND`() {
        val data = grayscalePng(
            width = 1,
            height = 1,
            rows = listOf(byteArrayOf(0x40)),
            filters = intArrayOf(0),
            bitDepth = 8,
        ) + byteArrayOf(0x00)

        assertNull(PngCodec.Decoder.make(data))
    }

    @Test
    fun `rejects non contiguous IDAT chunks`() {
        val idat = deflate(byteArrayOf(0x00, 0x40))
        val split = idat.size / 2
        val case = CodecNegativeFixtures.misplacedMetadata(
            "ancillary chunk between IDAT chunks",
            pngFromChunks(
                "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
                "IDAT" to idat.copyOfRange(0, split),
                "tEXt" to "note\u0000between-idats".toByteArray(Charsets.ISO_8859_1),
                "IDAT" to idat.copyOfRange(split, idat.size),
                "IEND" to ByteArray(0),
            ),
        )

        assertNull(PngCodec.Decoder.make(case.data), case.name)
    }

    @Test
    fun `rejects PLTE on grayscale images`() {
        val data = pngFromChunks(
            "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 0),
            "PLTE" to byteArrayOf(0x00, 0x00, 0x00),
            "IDAT" to deflate(byteArrayOf(0x00, 0x40)),
            "IEND" to ByteArray(0),
        )

        assertNull(PngCodec.Decoder.make(data))
    }

    @Test
    fun `rejects duplicate PLTE chunks`() {
        val case = CodecNegativeFixtures.duplicateMetadata(
            "duplicate PLTE chunks",
            pngFromChunks(
                "IHDR" to ihdr(width = 1, height = 1, bitDepth = 8, colorType = 3),
                "PLTE" to byteArrayOf(0x10, 0x20, 0x30),
                "PLTE" to byteArrayOf(0x40, 0x50, 0x60),
                "IDAT" to deflate(byteArrayOf(0x00, 0x00)),
                "IEND" to ByteArray(0),
            ),
        )

        assertNull(PngCodec.Decoder.make(case.data), case.name)
    }

    private fun png(
        width: Int,
        height: Int,
        colorType: Int,
        rows: List<IntArray>,
        filters: IntArray,
        transparency: ByteArray? = null,
    ): ByteArray {
        val bpp = if (colorType == 6) 4 else 3
        val raw = ByteArrayOutputStream()
        var previous = ByteArray(width * bpp)
        for (y in 0 until height) {
            val row = encodeRow(rows[y], colorType)
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(8)
                write(colorType)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            if (transparency != null) writeChunk("tRNS", transparency)
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun pngFromChunks(vararg chunks: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            for ((type, payload) in chunks) writeChunk(type, payload)
        }.toByteArray()

    private fun ihdr(width: Int, height: Int, bitDepth: Int, colorType: Int, interlace: Int = 0): ByteArray =
        ByteArrayOutputStream().apply {
            writeI32BE(width)
            writeI32BE(height)
            write(bitDepth)
            write(colorType)
            write(0)
            write(0)
            write(interlace)
        }.toByteArray()

    private fun u32Chunk(value: Int): ByteArray =
        ByteArrayOutputStream().apply {
            writeI32BE(value)
        }.toByteArray()

    private fun chrmChunk(
        whiteX: Int,
        whiteY: Int,
        redX: Int,
        redY: Int,
        greenX: Int,
        greenY: Int,
        blueX: Int,
        blueY: Int,
    ): ByteArray =
        ByteArrayOutputStream().apply {
            writeI32BE(whiteX)
            writeI32BE(whiteY)
            writeI32BE(redX)
            writeI32BE(redY)
            writeI32BE(greenX)
            writeI32BE(greenY)
            writeI32BE(blueX)
            writeI32BE(blueY)
        }.toByteArray()

    private fun adam7Png(
        width: Int,
        height: Int,
        colorType: Int,
        rows: List<IntArray>,
        filters: IntArray,
    ): ByteArray {
        val bpp = if (colorType == 6) 4 else 3
        var filterIndex = 0
        val raw = ByteArrayOutputStream()
        for (pass in ADAM7_PASSES) {
            val passWidth = adam7Size(width, pass.xStart, pass.xStep)
            val passHeight = adam7Size(height, pass.yStart, pass.yStep)
            if (passWidth == 0 || passHeight == 0) continue
            var previous = ByteArray(passWidth * bpp)
            for (passY in 0 until passHeight) {
                val y = pass.yStart + passY * pass.yStep
                val row = encodeRow(
                    IntArray(passWidth) { passX ->
                        rows[y][pass.xStart + passX * pass.xStep]
                    },
                    colorType,
                )
                val filter = filters[filterIndex++ % filters.size]
                raw.write(filter)
                raw.write(filterRow(filter, row, previous, bpp))
                previous = row
            }
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(8)
                write(colorType)
                write(0)
                write(0)
                write(1)
            }.toByteArray())
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun indexedPng(
        width: Int,
        height: Int,
        palette: IntArray,
        indexes: List<ByteArray>,
        filters: IntArray,
        transparency: ByteArray? = null,
        includePalette: Boolean = true,
        paletteBytes: ByteArray = encodePalette(palette),
        bitDepth: Int = 8,
    ): ByteArray {
        val raw = ByteArrayOutputStream()
        var previous = ByteArray((width * bitDepth + 7) / 8)
        for (y in 0 until height) {
            val row = indexes[y]
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp = 1))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(bitDepth)
                write(3)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            if (includePalette) writeChunk("PLTE", paletteBytes)
            if (transparency != null) writeChunk("tRNS", transparency)
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun adam7IndexedPng(
        width: Int,
        height: Int,
        palette: IntArray,
        indexes: List<IntArray>,
        filters: IntArray,
        bitDepth: Int,
    ): ByteArray {
        var filterIndex = 0
        val raw = ByteArrayOutputStream()
        for (pass in ADAM7_PASSES) {
            val passWidth = adam7Size(width, pass.xStart, pass.xStep)
            val passHeight = adam7Size(height, pass.yStart, pass.yStep)
            if (passWidth == 0 || passHeight == 0) continue
            var previous = ByteArray((passWidth * bitDepth + 7) / 8)
            for (passY in 0 until passHeight) {
                val y = pass.yStart + passY * pass.yStep
                val row = packSamples(
                    IntArray(passWidth) { passX ->
                        indexes[y][pass.xStart + passX * pass.xStep]
                    },
                    bitDepth,
                )
                val filter = filters[filterIndex++ % filters.size]
                raw.write(filter)
                raw.write(filterRow(filter, row, previous, bpp = 1))
                previous = row
            }
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(bitDepth)
                write(3)
                write(0)
                write(0)
                write(1)
            }.toByteArray())
            writeChunk("PLTE", encodePalette(palette))
            if (palette.any { a(it) != 0xFF }) {
                writeChunk("tRNS", ByteArray(palette.size) { a(palette[it]).toByte() })
            }
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun adam7GrayscalePng(
        width: Int,
        height: Int,
        samples: List<IntArray>,
        filters: IntArray,
        bitDepth: Int,
    ): ByteArray =
        adam7RawPng(
            width = width,
            height = height,
            bitDepth = bitDepth,
            colorType = 0,
            filters = filters,
            bpp = 1,
        ) { pass, passWidth, passY ->
            val y = pass.yStart + passY * pass.yStep
            val passSamples = IntArray(passWidth) { passX ->
                samples[y][pass.xStart + passX * pass.xStep]
            }
            if (bitDepth == 8) {
                ByteArray(passWidth) { passSamples[it].toByte() }
            } else {
                packSamples(passSamples, bitDepth)
            }
        }

    private fun adam7GrayscaleAlphaPng(width: Int, height: Int, rows: List<IntArray>, filters: IntArray): ByteArray =
        adam7RawPng(
            width = width,
            height = height,
            bitDepth = 8,
            colorType = 4,
            filters = filters,
            bpp = 2,
        ) { pass, passWidth, passY ->
            val y = pass.yStart + passY * pass.yStep
            encodeGrayscaleAlphaRow(
                IntArray(passWidth) { passX ->
                    rows[y][pass.xStart + passX * pass.xStep]
                },
            )
        }

    private fun adam7RawPng(
        width: Int,
        height: Int,
        bitDepth: Int,
        colorType: Int,
        filters: IntArray,
        bpp: Int,
        rowForPass: (Adam7Pass, Int, Int) -> ByteArray,
    ): ByteArray {
        var filterIndex = 0
        val raw = ByteArrayOutputStream()
        for (pass in ADAM7_PASSES) {
            val passWidth = adam7Size(width, pass.xStart, pass.xStep)
            val passHeight = adam7Size(height, pass.yStart, pass.yStep)
            if (passWidth == 0 || passHeight == 0) continue
            var previous = ByteArray((passWidth * bitDepth * bpp + 7) / 8)
            for (passY in 0 until passHeight) {
                val row = rowForPass(pass, passWidth, passY)
                val filter = filters[filterIndex++ % filters.size]
                raw.write(filter)
                raw.write(filterRow(filter, row, previous, bpp))
                previous = row
            }
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk(
                "IHDR",
                ihdr(width = width, height = height, bitDepth = bitDepth, colorType = colorType, interlace = 1),
            )
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun encodePalette(palette: IntArray): ByteArray {
        val out = ByteArray(palette.size * 3)
        var offset = 0
        for (color in palette) {
            out[offset++] = r(color).toByte()
            out[offset++] = g(color).toByte()
            out[offset++] = b(color).toByte()
        }
        return out
    }

    private fun grayscalePng(
        width: Int,
        height: Int,
        rows: List<ByteArray>,
        filters: IntArray,
        bitDepth: Int,
        iccp: ByteArray? = null,
        transparency: ByteArray? = null,
    ): ByteArray {
        val raw = ByteArrayOutputStream()
        var previous = ByteArray((width * bitDepth + 7) / 8)
        for (y in 0 until height) {
            val row = rows[y]
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp = if (bitDepth == 16) 2 else 1))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(bitDepth)
                write(0)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            if (iccp != null) writeChunk("iCCP", iccp)
            if (transparency != null) writeChunk("tRNS", transparency)
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun truecolor16Png(
        width: Int,
        height: Int,
        colorType: Int,
        rows: List<LongArray>,
        filters: IntArray,
        transparency: ByteArray? = null,
    ): ByteArray {
        val bpp = if (colorType == 6) 8 else 6
        val raw = ByteArrayOutputStream()
        var previous = ByteArray(width * bpp)
        for (y in 0 until height) {
            val row = encodeTruecolor16Row(rows[y], colorType)
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(16)
                write(colorType)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            if (transparency != null) writeChunk("tRNS", transparency)
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun grayscaleAlpha16Png(width: Int, height: Int, rows: List<ByteArray>, filters: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        var previous = ByteArray(width * 4)
        for (y in 0 until height) {
            val row = rows[y]
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp = 4))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(16)
                write(4)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }


    private fun grayscaleAlphaPng(width: Int, height: Int, rows: List<IntArray>, filters: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        var previous = ByteArray(width * 2)
        for (y in 0 until height) {
            val row = encodeGrayscaleAlphaRow(rows[y])
            raw.write(filters[y])
            raw.write(filterRow(filters[y], row, previous, bpp = 2))
            previous = row
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(8)
                write(4)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun encodeRow(colors: IntArray, colorType: Int): ByteArray {
        val bpp = if (colorType == 6) 4 else 3
        val row = ByteArray(colors.size * bpp)
        var offset = 0
        for (c in colors) {
            row[offset++] = r(c).toByte()
            row[offset++] = g(c).toByte()
            row[offset++] = b(c).toByte()
            if (colorType == 6) row[offset++] = a(c).toByte()
        }
        return row
    }

    private fun encodeGrayscaleAlphaRow(colors: IntArray): ByteArray {
        val row = ByteArray(colors.size * 2)
        var offset = 0
        for (c in colors) {
            row[offset++] = r(c).toByte()
            row[offset++] = a(c).toByte()
        }
        return row
    }

    private fun encodeTruecolor16Row(colors: LongArray, colorType: Int): ByteArray {
        val channels = if (colorType == 6) 4 else 3
        val row = ByteArray(colors.size * channels * 2)
        var offset = 0
        for (c in colors) {
            writeU16BE(row, offset, ((c ushr 48) and 0xFFFF).toInt())
            offset += 2
            writeU16BE(row, offset, ((c ushr 32) and 0xFFFF).toInt())
            offset += 2
            writeU16BE(row, offset, ((c ushr 16) and 0xFFFF).toInt())
            offset += 2
            if (colorType == 6) {
                writeU16BE(row, offset, (c and 0xFFFF).toInt())
                offset += 2
            }
        }
        return row
    }

    private fun packSamples(samples: IntArray, bitDepth: Int): ByteArray {
        val out = ByteArray((samples.size * bitDepth + 7) / 8)
        val mask = (1 shl bitDepth) - 1
        for (x in samples.indices) {
            val bitOffset = x * bitDepth
            val shift = 8 - bitDepth - (bitOffset % 8)
            out[bitOffset / 8] = (out[bitOffset / 8].toInt() or ((samples[x] and mask) shl shift)).toByte()
        }
        return out
    }

    private fun filterRow(filter: Int, row: ByteArray, previous: ByteArray, bpp: Int): ByteArray {
        val out = ByteArray(row.size)
        for (i in row.indices) {
            val value = row[i].toInt() and 0xFF
            val left = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
            val up = previous[i].toInt() and 0xFF
            val upLeft = if (i >= bpp) previous[i - bpp].toInt() and 0xFF else 0
            val predictor = when (filter) {
                0 -> 0
                1 -> left
                2 -> up
                3 -> (left + up) / 2
                4 -> paeth(left, up, upLeft)
                else -> error("unexpected filter")
            }
            out[i] = ((value - predictor) and 0xFF).toByte()
        }
        return out
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        writeI32BE(data.size)
        write(typeBytes)
        write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeI32BE(crc.value.toInt())
    }

    private fun ByteArrayOutputStream.writeI32BE(value: Int) {
        write((value ushr 24) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun iccpChunkData(
        name: String,
        compressionMethod: Int = 0,
        profileBytes: ByteArray,
    ): ByteArray = ByteArrayOutputStream().apply {
        write(name.toByteArray(Charsets.ISO_8859_1))
        write(0)
        write(compressionMethod)
        write(deflate(profileBytes))
    }.toByteArray()

    private fun u16Row(vararg samples: Int): ByteArray = ByteArray(samples.size * 2).also { row ->
        var offset = 0
        for (sample in samples) {
            writeU16BE(row, offset, sample)
            offset += 2
        }
    }

    private fun writeU16BE(row: ByteArray, offset: Int, value: Int) {
        row[offset] = ((value ushr 8) and 0xFF).toByte()
        row[offset + 1] = (value and 0xFF).toByte()
    }

    private fun rgba64(r: Int, g: Int, b: Int, a: Int): Long =
        ((r and 0xFFFF).toLong() shl 48) or
            ((g and 0xFFFF).toLong() shl 32) or
            ((b and 0xFFFF).toLong() shl 16) or
            (a and 0xFFFF).toLong()

    private fun rgb48(r: Int, g: Int, b: Int): Long =
        ((r and 0xFFFF).toLong() shl 48) or
            ((g and 0xFFFF).toLong() shl 32) or
            ((b and 0xFFFF).toLong() shl 16)

    private fun assertF16(
        bitmap: org.skia.foundation.SkBitmap,
        x: Int,
        y: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        val pixel = FloatArray(4)
        bitmap.getPixelF16(x, y, pixel)
        assertEquals(r, pixel[0], 0.000001f, "r x=$x y=$y")
        assertEquals(g, pixel[1], 0.000001f, "g x=$x y=$y")
        assertEquals(b, pixel[2], 0.000001f, "b x=$x y=$y")
        assertEquals(a, pixel[3], 0.000001f, "a x=$x y=$y")
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private data class Adam7Pass(
        val xStart: Int,
        val yStart: Int,
        val xStep: Int,
        val yStep: Int,
    )

    private fun adam7Size(size: Int, start: Int, step: Int): Int =
        if (size <= start) 0 else (size - start + step - 1) / step

    private val ADAM7_PASSES = arrayOf(
        Adam7Pass(0, 0, 8, 8),
        Adam7Pass(4, 0, 8, 8),
        Adam7Pass(0, 4, 4, 8),
        Adam7Pass(2, 0, 4, 4),
        Adam7Pass(0, 2, 2, 4),
        Adam7Pass(1, 0, 2, 2),
        Adam7Pass(0, 1, 1, 2),
    )

    private fun a(c: Int): Int = (c ushr 24) and 0xFF
    private fun r(c: Int): Int = (c ushr 16) and 0xFF
    private fun g(c: Int): Int = (c ushr 8) and 0xFF
    private fun b(c: Int): Int = c and 0xFF
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
