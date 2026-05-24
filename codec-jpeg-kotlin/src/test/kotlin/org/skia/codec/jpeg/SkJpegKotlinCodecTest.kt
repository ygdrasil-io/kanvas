package org.skia.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader
import kotlin.math.roundToInt

class SkJpegKotlinCodecTest {

    @Test
    fun `rejects invalid signature and mismatched component scan`() {
        assertFalse(SkJpegKotlinCodec.Decoder.matches(ByteArray(0)))
        assertFalse(SkJpegKotlinCodec.Decoder.matches("not-a-jpeg".toByteArray()))
        assertNull(SkJpegKotlinCodec.Decoder.make("not-a-jpeg".toByteArray()))
        assertNull(SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 8, height = 8, componentCount = 3)))
    }

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "jpeg" })
    }

    @Test
    fun `decodes baseline grayscale 8x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 8, height = 8))

        assertNotNull(codec)
        assertTrue(codec is SkJpegKotlinCodec)
        assertEquals(SkEncodedImageFormat.kJPEG, codec!!.getEncodedFormat())
        assertEquals(8, codec.getInfo().width)
        assertEquals(8, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(0xFF808080.toInt(), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes partial edge blocks`() {
        val codec = SkJpegKotlinCodec.Decoder.make(grayscaleJpeg(width = 13, height = 9))!!
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(13, bitmap!!.width)
        assertEquals(9, bitmap.height)
        assertEquals(0xFF808080.toInt(), bitmap.getPixel(12, 8))
    }

    @Test
    fun `decodes baseline color 444 8x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(color444Jpeg())!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(0xFFF16937.toInt(), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes baseline color 422 16x8 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 8, ySampling = 0x21))!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(yCbCrToArgb(140, 80, 200), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
            for (x in 8 until 16) {
                assertEquals(yCbCrToArgb(152, 80, 200), bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `decodes baseline color 420 16x16 jpeg`() {
        val codec = SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 16, ySampling = 0x22))!!
        val (bitmap, result) = codec.getImage()

        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        val expected = intArrayOf(
            yCbCrToArgb(140, 80, 200),
            yCbCrToArgb(152, 80, 200),
            yCbCrToArgb(164, 80, 200),
            yCbCrToArgb(176, 80, 200),
        )
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val quadrant = (if (y < 8) 0 else 2) + if (x < 8) 0 else 1
                assertEquals(expected[quadrant], bitmap!!.getPixel(x, y), "x=$x y=$y")
            }
        }
    }

    @Test
    fun `rejects exotic color sampling`() {
        assertNull(SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 8, height = 16, ySampling = 0x12)))
        assertNull(SkJpegKotlinCodec.Decoder.make(colorJpeg(width = 16, height = 8, ySampling = 0x21, cbSampling = 0x21)))
    }

    private fun grayscaleJpeg(width: Int, height: Int, componentCount: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC0) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(componentCount)
            for (id in 1..componentCount) {
                write(id)
                write(0x11)
                write(0)
            }
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xDA) {
            write(1)
            write(1)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        out.write(entropyForZeroBlocks(((width + 7) / 8) * ((height + 7) / 8)))
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun color444Jpeg(ySampling: Int = 0x11): ByteArray {
        return colorJpeg(width = 8, height = 8, ySampling = ySampling)
    }

    private fun colorJpeg(
        width: Int,
        height: Int,
        ySampling: Int,
        cbSampling: Int = 0x11,
        crSampling: Int = 0x11,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeMarker(0xD8)
        out.writeSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeSegment(0xC0) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(3)
            write(1)
            write(ySampling)
            write(0)
            write(2)
            write(cbSampling)
            write(0)
            write(3)
            write(crSampling)
            write(0)
        }
        out.writeSegment(0xC4) {
            write(0x00)
            write(0)
            write(3)
            repeat(14) { write(0) }
            write(0x07)
            write(0x09)
            write(0x0A)
        }
        out.writeSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeSegment(0xDA) {
            write(3)
            write(1)
            write(0x00)
            write(2)
            write(0x00)
            write(3)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        val yBlocksPerMcu = (ySampling ushr 4) * (ySampling and 0x0F)
        val mcuWidth = (ySampling ushr 4) * 8
        val mcuHeight = (ySampling and 0x0F) * 8
        val mcuCount = ((width + mcuWidth - 1) / mcuWidth) * ((height + mcuHeight - 1) / mcuHeight)
        val bits = buildString {
            repeat(mcuCount) {
                repeat(yBlocksPerMcu) {
                    append("00")
                    append("1100000")
                    append("0")
                }
                append("01")
                append("001111111")
                append("0")
                append("10")
                append("1001000000")
                append("0")
            }
        }
        out.write(
            entropyBits(bits),
        )
        out.writeMarker(0xD9)
        return out.toByteArray()
    }

    private fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
        val cbShifted = cb - 128
        val crShifted = cr - 128
        val r = (y + 1.402 * crShifted).roundToInt().coerceIn(0, 255)
        val g = (y - 0.344136 * cbShifted - 0.714136 * crShifted).roundToInt().coerceIn(0, 255)
        val b = (y + 1.772 * cbShifted).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun entropyForZeroBlocks(blockCount: Int): ByteArray {
        val bitCount = blockCount * 2
        val byteCount = (bitCount + 7) / 8
        val out = ByteArray(byteCount)
        for (bit in 0 until bitCount) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() and (1 shl shift).inv()).toByte()
        }
        for (bit in bitCount until byteCount * 8) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
        }
        return out
    }

    private fun entropyBits(bits: String): ByteArray {
        val byteCount = (bits.length + 7) / 8
        val out = ByteArray(byteCount)
        for (bit in bits.indices) {
            if (bits[bit] == '1') {
                val byte = bit / 8
                val shift = 7 - (bit and 7)
                out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
            }
        }
        for (bit in bits.length until byteCount * 8) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
        }
        return out
    }

    private fun ByteArrayOutputStream.writeMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeSegment(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit) {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        writeMarker(marker)
        writeU16BE(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16BE(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }
}
