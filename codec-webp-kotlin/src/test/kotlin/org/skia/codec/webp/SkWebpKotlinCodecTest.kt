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

    private fun ByteArrayOutputStream.writeU32LE(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
