package org.skia.codec.webp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader

class SkWebpKotlinCodecTest {

    @Test
    fun `sniffs RIFF WEBP signature only`() {
        assertFalse(SkWebpKotlinCodec.Decoder.matches(ByteArray(0)))
        assertFalse(SkWebpKotlinCodec.Decoder.matches("not-a-webp".toByteArray()))
        assertFalse(SkWebpKotlinCodec.Decoder.matches(riff("WAVE", chunk("fmt ", byteArrayOf(1, 2, 3, 4)))))
        assertTrue(SkWebpKotlinCodec.Decoder.matches(vp8xWebp(width = 1, height = 1, flags = 0)))
    }

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "webp" })
    }

    @Test
    fun `parses VP8X dimensions and flags`() {
        val codec = SkWebpKotlinCodec.Decoder.make(
            vp8xWebp(width = 321, height = 123, flags = 0x3E),
        )

        assertNotNull(codec)
        assertTrue(codec is SkWebpKotlinCodec)
        codec as SkWebpKotlinCodec
        assertEquals(SkEncodedImageFormat.kWEBP, codec.getEncodedFormat())
        assertEquals(321, codec.getInfo().width)
        assertEquals(123, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())
        assertTrue(codec.metadata.flags.icc)
        assertTrue(codec.metadata.flags.alpha)
        assertTrue(codec.metadata.flags.exif)
        assertTrue(codec.metadata.flags.xmp)
        assertTrue(codec.metadata.flags.animation)
        assertEquals(0x3E, codec.metadata.flags.raw)
    }

    @Test
    fun `parses VP8L dimensions`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lWebp(width = 4096, height = 2048)) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertEquals(WebpBitstreamFormat.VP8L, codec!!.metadata.format)
        assertEquals(4096, codec.getInfo().width)
        assertEquals(2048, codec.getInfo().height)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
    }

    @Test
    fun `parses VP8 keyframe dimensions`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8Webp(width = 640, height = 480)) as SkWebpKotlinCodec?

        assertNotNull(codec)
        assertEquals(WebpBitstreamFormat.VP8, codec!!.metadata.format)
        assertEquals(640, codec.getInfo().width)
        assertEquals(480, codec.getInfo().height)
        assertEquals(SkAlphaType.kOpaque, codec.getInfo().alphaType)
    }

    @Test
    fun `returns unimplemented for pixel decode after metadata parse`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8xWebp(width = 2, height = 2, flags = 0))!!
        val dst = SkBitmap(
            width = 2,
            height = 2,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `decodes VP8L simple literal pixels`() {
        val expected = intArrayOf(
            argb(0xFF, 0x11, 0x22, 0x33),
            argb(0x80, 0x44, 0x66, 0x77),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lLiteralWebp(width = 2, height = 1, expected))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L normal Huffman single-symbol literals`() {
        val expected = intArrayOf(
            argb(0x00, 0x00, 0x00, 0x00),
            argb(0x00, 0x00, 0x00, 0x00),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lNormalSingleSymbolWebp(width = 2, height = 1))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L LZ77 copy length`() {
        val expected = intArrayOf(
            argb(0xFF, 0x11, 0x25, 0x33),
            argb(0xFF, 0x11, 0x25, 0x33),
            argb(0xFF, 0x11, 0x25, 0x33),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lCopyLengthWebp(width = 3, height = 1))!!
        val dst = SkBitmap(
            width = 3,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L color cache references`() {
        val expected = intArrayOf(
            argb(0xFF, 0x31, 0x42, 0x53),
            argb(0xFF, 0x31, 0x42, 0x53),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lColorCacheWebp(width = 2, height = 1, expected[0]))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L subtract green transform`() {
        val expected = intArrayOf(
            argb(0xFF, 0x40, 0x25, 0x58),
            argb(0x80, 0x04, 0xFE, 0x13),
        )
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lSubtractGreenWebp(width = 2, height = 1, expected))!!
        val dst = SkBitmap(
            width = 2,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (x in expected.indices) {
            val actual = dst.getPixel(x, 0)
            assertEquals(alpha(expected[x]), alpha(actual))
            assertEquals(red(expected[x]), red(actual))
            assertEquals(green(expected[x]), green(actual))
            assertEquals(blue(expected[x]), blue(actual))
        }
    }

    @Test
    fun `decodes VP8L predictor transform modes`() {
        val width = 5
        val height = 3
        val residuals = IntArray(width * height) { argb(0x00, 0x03, 0x05, 0x07) }

        for (mode in 0..13) {
            val expected = applyPredictorFixture(width, height, mode, residuals)
            val codec = SkWebpKotlinCodec.Decoder.make(vp8lPredictorWebp(width, height, mode, residuals))!!

            assertWebpPixels(codec, width, height, expected)
        }
    }

    @Test
    fun `decodes VP8L predictor transform border rules`() {
        val width = 4
        val height = 3
        val residuals = IntArray(width * height) { argb(0x00, 0x01, 0x02, 0x03) }
        val expected = applyPredictorFixture(width, height, mode = 3, residuals)
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lPredictorWebp(width, height, mode = 3, residuals))!!

        assertEquals(argb(0xFF, 0x01, 0x02, 0x03), expected[0])
        assertEquals(argb(0xFF, 0x02, 0x04, 0x06), expected[1]) // top row uses L.
        assertEquals(argb(0xFF, 0x02, 0x04, 0x06), expected[width]) // left column uses T.
        assertEquals(argb(0xFF, 0x03, 0x06, 0x09), expected[width * 2 - 1]) // right edge TR wraps to row start.
        assertWebpPixels(codec, width, height, expected)
    }

    @Test
    fun `VP8L pixel decode rejects unsupported color transform`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lUnsupportedTransformWebp(width = 1, height = 1))!!
        val dst = SkBitmap(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kUnimplemented, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `VP8L pixel decode rejects invalid color cache size`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lInvalidColorCacheWebp(width = 1, height = 1))!!
        val dst = SkBitmap(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `VP8L pixel decode rejects truncated normal Huffman code`() {
        val codec = SkWebpKotlinCodec.Decoder.make(vp8lNormalHuffmanWebp(width = 1, height = 1))!!
        val dst = SkBitmap(
            width = 1,
            height = 1,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kErrorInInput, codec.getPixels(codec.getInfo(), dst))
    }

    @Test
    fun `rejects truncated and metadata-less RIFF WEBP`() {
        assertNull(SkWebpKotlinCodec.Decoder.make(byteArrayOf('R'.code.toByte(), 'I'.code.toByte())))
        assertNull(SkWebpKotlinCodec.Decoder.make(riff("WEBP", chunk("VP8X", ByteArray(9)))))
        assertNull(SkWebpKotlinCodec.Decoder.make(riff("WEBP", chunk("ALPH", byteArrayOf(1, 2, 3)))))
        val declaredTooLarge = riff("WEBP", chunk("VP8X", ByteArray(10))).copyOf(20)
        assertNull(SkWebpKotlinCodec.Decoder.make(declaredTooLarge))
    }

    private fun vp8xWebp(width: Int, height: Int, flags: Int): ByteArray {
        val payload = ByteArray(10)
        payload[0] = flags.toByte()
        write24LE(payload, 4, width - 1)
        write24LE(payload, 7, height - 1)
        return riff("WEBP", chunk("VP8X", payload))
    }

    private fun vp8lWebp(width: Int, height: Int): ByteArray {
        val bits = ((width - 1) and 0x3FFF) or (((height - 1) and 0x3FFF) shl 14)
        val payload = ByteArray(5)
        payload[0] = 0x2F
        payload[1] = (bits and 0xFF).toByte()
        payload[2] = ((bits ushr 8) and 0xFF).toByte()
        payload[3] = ((bits ushr 16) and 0xFF).toByte()
        payload[4] = ((bits ushr 24) and 0xFF).toByte()
        return riff("WEBP", chunk("VP8L", payload))
    }

    private fun vp8lLiteralWebp(width: Int, height: Int, argb: IntArray): ByteArray {
        require(argb.size == width * height)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = argb.uniqueChannel { (it ushr 8) and 0xFF }
        val red = argb.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = argb.uniqueChannel { it and 0xFF }
        val alpha = argb.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0)) // distance alphabet is unused by literal-only pixels.
        for (pixel in argb) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lUnsupportedTransformWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1)
        writer.writeBits(1, 2) // color transform is not supported yet.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lNormalHuffmanWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        writer.writeBits(0, 1) // normal Huffman code with no complete code description.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lNormalSingleSymbolWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        repeat(5) {
            writeNormalSingleSymbolCode(writer, symbol = 0)
        }
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lCopyLengthWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        writeNormalTwoSymbolCode(writer, first = 0x25, second = 257)
        writeSimpleCode(writer, intArrayOf(0x11))
        writeSimpleCode(writer, intArrayOf(0x33))
        writeSimpleCode(writer, intArrayOf(0xFF))
        writeSimpleCode(writer, intArrayOf(1)) // distance prefix 1 maps to the left pixel.
        writer.writeBits(0, 1) // literal green 0x25.
        writer.writeBits(1, 1) // length prefix 1 => copy two pixels.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lColorCacheWebp(width: Int, height: Int, pixel: Int): ByteArray {
        require(width * height == 2)
        val colorCacheBits = 3
        val colorCacheIndex = colorCacheIndex(pixel, colorCacheBits)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(1, 1) // color_cache_present
        writer.writeBits(colorCacheBits, 4)
        writer.writeBits(0, 1) // meta_prefix_present
        val greenSymbols = intArrayOf(green(pixel), 256 + 24 + colorCacheIndex)
        writeNormalTwoSymbolCode(writer, first = greenSymbols[0], second = greenSymbols[1])
        writeSimpleCode(writer, intArrayOf(red(pixel)))
        writeSimpleCode(writer, intArrayOf(blue(pixel)))
        writeSimpleCode(writer, intArrayOf(alpha(pixel)))
        writeSimpleCode(writer, intArrayOf(0)) // distance alphabet is unused.
        writer.writeBits(0, 1) // literal pixel.
        writer.writeBits(1, 1) // color cache reference.
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lSubtractGreenWebp(width: Int, height: Int, argb: IntArray): ByteArray {
        require(argb.size == width * height)
        val transformed = IntArray(argb.size) { i ->
            val pixel = argb[i]
            val green = green(pixel)
            argb(
                alpha = alpha(pixel),
                red = (red(pixel) - green) and 0xFF,
                green = green,
                blue = (blue(pixel) - green) and 0xFF,
            )
        }
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(2, 2) // subtract green transform.
        writer.writeBits(0, 1) // transform_present terminator
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = transformed.uniqueChannel { (it ushr 8) and 0xFF }
        val red = transformed.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = transformed.uniqueChannel { it and 0xFF }
        val alpha = transformed.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0))
        for (pixel in transformed) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lPredictorWebp(width: Int, height: Int, mode: Int, residuals: IntArray): ByteArray {
        require(residuals.size == width * height)
        require(mode in 0..13)
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(1, 1) // transform_present
        writer.writeBits(0, 2) // predictor transform.
        writer.writeBits(0, 3) // size_bits = 2, so this tiny fixture has one predictor block.
        writeVp8lLiteralImageData(writer, intArrayOf(argb(0xFF, 0, mode, 0)))
        writer.writeBits(0, 1) // transform_present terminator
        writeVp8lLiteralImageData(writer, residuals)
        return vp8lWebpFromBits(writer)
    }

    private fun vp8lInvalidColorCacheWebp(width: Int, height: Int): ByteArray {
        val writer = Vp8lTestBitWriter()
        writeVp8lHeaderBits(writer, width, height)
        writer.writeBits(0, 1) // transform_present
        writer.writeBits(1, 1) // color_cache_present
        writer.writeBits(0, 4) // invalid: valid color cache bits are 1..11.
        return vp8lWebpFromBits(writer)
    }

    private fun writeVp8lLiteralImageData(writer: Vp8lTestBitWriter, argb: IntArray) {
        writer.writeBits(0, 1) // color_cache_present
        writer.writeBits(0, 1) // meta_prefix_present
        val green = argb.uniqueChannel { (it ushr 8) and 0xFF }
        val red = argb.uniqueChannel { (it ushr 16) and 0xFF }
        val blue = argb.uniqueChannel { it and 0xFF }
        val alpha = argb.uniqueChannel { (it ushr 24) and 0xFF }
        writeSimpleCode(writer, green)
        writeSimpleCode(writer, red)
        writeSimpleCode(writer, blue)
        writeSimpleCode(writer, alpha)
        writeSimpleCode(writer, intArrayOf(0))
        for (pixel in argb) {
            writer.writeSymbol(green, (pixel ushr 8) and 0xFF)
            writer.writeSymbol(red, (pixel ushr 16) and 0xFF)
            writer.writeSymbol(blue, pixel and 0xFF)
            writer.writeSymbol(alpha, (pixel ushr 24) and 0xFF)
        }
    }

    private fun writeVp8lHeaderBits(writer: Vp8lTestBitWriter, width: Int, height: Int) {
        writer.writeBits(width - 1, 14)
        writer.writeBits(height - 1, 14)
        writer.writeBits(1, 1) // alpha_is_used
        writer.writeBits(0, 3) // version
    }

    private fun writeSimpleCode(writer: Vp8lTestBitWriter, symbols: IntArray) {
        require(symbols.size in 1..2)
        writer.writeBits(1, 1) // simple code
        writer.writeBits(symbols.size - 1, 1)
        writer.writeBits(1, 1) // first symbol uses 8 bits.
        writer.writeBits(symbols[0], 8)
        if (symbols.size == 2) writer.writeBits(symbols[1], 8)
    }

    private fun writeNormalSingleSymbolCode(writer: Vp8lTestBitWriter, symbol: Int) {
        require(symbol in 0..1)
        writer.writeBits(0, 1) // normal code
        writer.writeBits(0, 4) // four code length code lengths.
        writer.writeBits(0, 3) // symbol 17 length.
        writer.writeBits(0, 3) // symbol 18 length.
        writer.writeBits(1, 3) // symbol 0 length.
        writer.writeBits(1, 3) // symbol 1 length.
        writer.writeBits(1, 1) // custom max_symbol.
        writer.writeBits(0, 3) // two bits encode max_symbol.
        writer.writeBits(0, 2) // max_symbol = 2.
        writer.writeBits(if (symbol == 0) 1 else 0, 1)
        writer.writeBits(if (symbol == 0) 0 else 1, 1)
    }

    private fun writeNormalTwoSymbolCode(writer: Vp8lTestBitWriter, first: Int, second: Int) {
        require(first < second)
        writer.writeBits(0, 1) // normal code
        writer.writeBits(0, 4) // four code length code lengths.
        writer.writeBits(0, 3) // symbol 17 length.
        writer.writeBits(1, 3) // symbol 18 length.
        writer.writeBits(0, 3) // symbol 0 length.
        writer.writeBits(1, 3) // symbol 1 length.
        writer.writeBits(1, 1) // custom max_symbol.
        writer.writeBits(4, 3) // ten bits encode max_symbol.
        writer.writeBits(second + 1 - 2, 10)
        writeCodeLengthZeroRun(writer, first)
        writeCodeLengthOne(writer)
        writeCodeLengthZeroRun(writer, second - first - 1)
        writeCodeLengthOne(writer)
    }

    private fun writeCodeLengthOne(writer: Vp8lTestBitWriter) {
        writer.writeBits(0, 1)
    }

    private fun writeCodeLengthZeroRun(writer: Vp8lTestBitWriter, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val repeat = minOf(remaining, 138)
            require(repeat >= 11)
            writer.writeBits(1, 1) // code length code symbol 18.
            writer.writeBits(repeat - 11, 7)
            remaining -= repeat
        }
    }

    private fun vp8lWebpFromBits(writer: Vp8lTestBitWriter): ByteArray =
        riff("WEBP", chunk("VP8L", byteArrayOf(0x2F) + writer.toByteArray()))

    private fun IntArray.uniqueChannel(component: (Int) -> Int): IntArray =
        map(component).distinct().sorted().also { require(it.size <= 2) }.toIntArray()

    private fun vp8Webp(width: Int, height: Int): ByteArray {
        val payload = ByteArray(10)
        payload[3] = 0x9D.toByte()
        payload[4] = 0x01
        payload[5] = 0x2A
        writeU16LE(payload, 6, width)
        writeU16LE(payload, 8, height)
        return riff("WEBP", chunk("VP8 ", payload))
    }

    private fun riff(type: String, vararg chunks: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(type.toByteArray(Charsets.US_ASCII))
        for (chunk in chunks) payload.write(chunk)
        val payloadBytes = payload.toByteArray()
        return ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeU32LE(payloadBytes.size)
            write(payloadBytes)
        }.toByteArray()
    }

    private fun chunk(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(type.toByteArray(Charsets.US_ASCII))
            writeU32LE(payload.size)
            write(payload)
            if ((payload.size and 1) != 0) write(0)
        }.toByteArray()

    private fun write24LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    }

    private fun writeU16LE(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun alpha(pixel: Int): Int = (pixel ushr 24) and 0xFF
    private fun red(pixel: Int): Int = (pixel ushr 16) and 0xFF
    private fun green(pixel: Int): Int = (pixel ushr 8) and 0xFF
    private fun blue(pixel: Int): Int = pixel and 0xFF

    private fun assertWebpPixels(codec: SkCodec, width: Int, height: Int, expected: IntArray) {
        val dst = SkBitmap(
            width = width,
            height = height,
            colorType = SkColorType.kRGBA_8888,
            colorSpace = SkColorSpace.makeSRGB(),
        )

        assertEquals(SkCodec.Result.kSuccess, codec.getPixels(codec.getInfo(), dst))
        for (y in 0 until height) {
            for (x in 0 until width) {
                val expectedPixel = expected[y * width + x]
                val actual = dst.getPixel(x, y)
                assertEquals(alpha(expectedPixel), alpha(actual))
                assertEquals(red(expectedPixel), red(actual))
                assertEquals(green(expectedPixel), green(actual))
                assertEquals(blue(expectedPixel), blue(actual))
            }
        }
    }

    private fun applyPredictorFixture(width: Int, height: Int, mode: Int, residuals: IntArray): IntArray {
        require(residuals.size == width * height)
        val pixels = IntArray(residuals.size)
        for (i in residuals.indices) {
            val x = i % width
            val y = i / width
            val predictor = when {
                x == 0 && y == 0 -> argb(0xFF, 0, 0, 0)
                y == 0 -> pixels[i - 1]
                x == 0 -> pixels[i - width]
                else -> predictorFixturePixel(pixels, width, x, y, mode)
            }
            pixels[i] = addPixels(residuals[i], predictor)
        }
        return pixels
    }

    private fun predictorFixturePixel(pixels: IntArray, width: Int, x: Int, y: Int, mode: Int): Int {
        val left = pixels[y * width + x - 1]
        val top = pixels[(y - 1) * width + x]
        val topLeft = pixels[(y - 1) * width + x - 1]
        val topRight = if (x == width - 1) pixels[y * width] else pixels[(y - 1) * width + x + 1]
        return when (mode) {
            0 -> argb(0xFF, 0, 0, 0)
            1 -> left
            2 -> top
            3 -> topRight
            4 -> topLeft
            5 -> averagePixels(averagePixels(left, topRight), top)
            6 -> averagePixels(left, topLeft)
            7 -> averagePixels(left, top)
            8 -> averagePixels(topLeft, top)
            9 -> averagePixels(top, topRight)
            10 -> averagePixels(averagePixels(left, topLeft), averagePixels(top, topRight))
            11 -> selectPredictor(left, top, topLeft)
            12 -> clampAddSubtractFull(left, top, topLeft)
            else -> clampAddSubtractHalf(averagePixels(left, top), topLeft)
        }
    }

    private fun addPixels(residual: Int, predictor: Int): Int =
        argb(
            alpha = (alpha(residual) + alpha(predictor)) and 0xFF,
            red = (red(residual) + red(predictor)) and 0xFF,
            green = (green(residual) + green(predictor)) and 0xFF,
            blue = (blue(residual) + blue(predictor)) and 0xFF,
        )

    private fun averagePixels(a: Int, b: Int): Int =
        argb(
            alpha = (alpha(a) + alpha(b)) ushr 1,
            red = (red(a) + red(b)) ushr 1,
            green = (green(a) + green(b)) ushr 1,
            blue = (blue(a) + blue(b)) ushr 1,
        )

    private fun selectPredictor(left: Int, top: Int, topLeft: Int): Int {
        val pa = alpha(left) + alpha(top) - alpha(topLeft)
        val pr = red(left) + red(top) - red(topLeft)
        val pg = green(left) + green(top) - green(topLeft)
        val pb = blue(left) + blue(top) - blue(topLeft)
        val leftDistance = kotlin.math.abs(pa - alpha(left)) +
            kotlin.math.abs(pr - red(left)) +
            kotlin.math.abs(pg - green(left)) +
            kotlin.math.abs(pb - blue(left))
        val topDistance = kotlin.math.abs(pa - alpha(top)) +
            kotlin.math.abs(pr - red(top)) +
            kotlin.math.abs(pg - green(top)) +
            kotlin.math.abs(pb - blue(top))
        return if (leftDistance < topDistance) left else top
    }

    private fun clampAddSubtractFull(left: Int, top: Int, topLeft: Int): Int =
        argb(
            alpha = clampByte(alpha(left) + alpha(top) - alpha(topLeft)),
            red = clampByte(red(left) + red(top) - red(topLeft)),
            green = clampByte(green(left) + green(top) - green(topLeft)),
            blue = clampByte(blue(left) + blue(top) - blue(topLeft)),
        )

    private fun clampAddSubtractHalf(average: Int, topLeft: Int): Int =
        argb(
            alpha = clampByte(alpha(average) + (alpha(average) - alpha(topLeft)) / 2),
            red = clampByte(red(average) + (red(average) - red(topLeft)) / 2),
            green = clampByte(green(average) + (green(average) - green(topLeft)) / 2),
            blue = clampByte(blue(average) + (blue(average) - blue(topLeft)) / 2),
        )

    private fun clampByte(value: Int): Int = value.coerceIn(0, 255)

    private fun colorCacheIndex(pixel: Int, bits: Int): Int =
        (pixel * 0x1e35a7bd) ushr (32 - bits)

    private fun ByteArrayOutputStream.writeU32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    private class Vp8lTestBitWriter {
        private val bytes = ArrayList<Int>()
        private var bitOffset = 0

        fun writeBits(value: Int, count: Int) {
            for (i in 0 until count) {
                if ((bitOffset and 7) == 0) bytes.add(0)
                val bit = (value ushr i) and 1
                val index = bytes.lastIndex
                bytes[index] = bytes[index] or (bit shl (bitOffset and 7))
                bitOffset++
            }
        }

        fun writeSymbol(symbols: IntArray, value: Int) {
            if (symbols.size == 1) {
                require(symbols[0] == value)
                return
            }
            writeBits(symbols.indexOf(value), 1)
        }

        fun toByteArray(): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }
    }
}
