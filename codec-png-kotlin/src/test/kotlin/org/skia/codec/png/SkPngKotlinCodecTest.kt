package org.skia.codec.png

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
import java.util.zip.CRC32
import java.util.zip.Deflater

class SkPngKotlinCodecTest {

    @Test
    fun `rejects invalid signature`() {
        assertFalse(SkPngKotlinCodec.Decoder.matches(ByteArray(0)))
        assertFalse(SkPngKotlinCodec.Decoder.matches("not-a-png".toByteArray()))
        assertNull(SkPngKotlinCodec.Decoder.make("not-a-png".toByteArray()))
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
        val codec = SkPngKotlinCodec.Decoder.make(
            png(width = 3, height = 5, colorType = 6, rows = rows, filters = intArrayOf(0, 1, 2, 3, 4)),
        )

        assertNotNull(codec)
        assertTrue(codec is SkPngKotlinCodec)
        assertEquals(SkEncodedImageFormat.kPNG, codec!!.getEncodedFormat())
        assertEquals(3, codec.getInfo().width)
        assertEquals(5, codec.getInfo().height)
        assertEquals(SkColorType.kRGBA_8888, codec.getInfo().colorType)
        assertEquals(SkAlphaType.kUnpremul, codec.getInfo().alphaType)
        assertTrue(codec.getInfo().colorSpace.isSRGB())

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
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
        val codec = SkPngKotlinCodec.Decoder.make(
            png(width = 2, height = 2, colorType = 2, rows = rows, filters = intArrayOf(0, 1)),
        )!!

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(rows[0][0], bitmap!!.getPixel(0, 0))
        assertEquals(rows[0][1], bitmap.getPixel(1, 0))
        assertEquals(rows[1][0], bitmap.getPixel(0, 1))
        assertEquals(rows[1][1], bitmap.getPixel(1, 1))
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

        assertNull(SkPngKotlinCodec.Decoder.make(data))
    }

    private fun png(width: Int, height: Int, colorType: Int, rows: List<IntArray>, filters: IntArray): ByteArray {
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
