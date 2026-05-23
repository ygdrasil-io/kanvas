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

class SkJpegKotlinCodecTest {

    @Test
    fun `rejects invalid signature and unsupported RGB JPEG`() {
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
