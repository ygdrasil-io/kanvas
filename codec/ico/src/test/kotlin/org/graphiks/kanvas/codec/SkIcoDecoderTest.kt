package org.graphiks.kanvas.codec

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat
import java.io.ByteArrayOutputStream
import java.util.ServiceLoader
import java.util.zip.CRC32
import java.util.zip.Deflater

class SkIcoDecoderTest {

    @Test
    fun `is registered through ServiceLoader`() {
        val decoders = ServiceLoader.load(CodecDecoderProvider::class.java)
            .flatMap { it.decoders() }
        assertTrue(decoders.any { it.name == "ico" })
    }

    @Test
    fun `decodes embedded PNG through codec registry`() {
        val pixels = listOf(intArrayOf(RED, GREEN))
        val codec = SkCodec.MakeFromData(ico(entry(width = 2, height = 1, payload = png(2, 1, pixels))))!!

        assertEquals(SkEncodedImageFormat.kPNG, codec.getEncodedFormat())
        assertEquals(2, codec.getInfo().width)
        assertEquals(1, codec.getInfo().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
    }

    @Test
    fun `decodes embedded DIB through codec registry`() {
        val pixels = listOf(
            listOf(RED, GREEN),
            listOf(BLUE, WHITE),
        )
        val codec = SkCodec.MakeFromData(ico(entry(width = 2, height = 2, payload = dib32(2, 2, pixels))))!!

        assertEquals(SkEncodedImageFormat.kBMP, codec.getEncodedFormat())
        assertEquals(2, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(GREEN, bitmap.getPixel(1, 0))
        assertEquals(BLUE, bitmap.getPixel(0, 1))
        assertEquals(WHITE, bitmap.getPixel(1, 1))
    }

    @Test
    fun `selects the largest directory entry`() {
        val small = entry(width = 1, height = 1, payload = png(1, 1, listOf(intArrayOf(RED))))
        val large = entry(
            width = 3,
            height = 2,
            payload = png(
                3,
                2,
                listOf(
                    intArrayOf(GREEN, GREEN, GREEN),
                    intArrayOf(GREEN, GREEN, GREEN),
                ),
            ),
        )
        val codec = SkCodec.MakeFromData(ico(small, large))!!

        assertEquals(3, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(GREEN, bitmap!!.getPixel(2, 1))
    }

    @Test
    fun `prefers PNG over DIB when directory sizes tie`() {
        val dib = entry(
            width = 2,
            height = 2,
            bitDepth = 32,
            payload = dib32(2, 2, listOf(listOf(BLUE, BLUE), listOf(BLUE, BLUE))),
        )
        val embeddedPng = entry(
            width = 2,
            height = 2,
            bitDepth = 1,
            payload = png(2, 2, listOf(intArrayOf(RED, RED), intArrayOf(RED, RED))),
        )
        val codec = SkCodec.MakeFromData(ico(dib, embeddedPng))!!

        assertEquals(SkEncodedImageFormat.kPNG, codec.getEncodedFormat())
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
    }

    @Test
    fun `selects larger DIB over smaller PNG even when directory bit depth is lower`() {
        val smallerPng = entry(
            width = 2,
            height = 2,
            bitDepth = 32,
            payload = png(2, 2, listOf(intArrayOf(RED, RED), intArrayOf(RED, RED))),
        )
        val largerDib = entry(
            width = 3,
            height = 2,
            bitDepth = 24,
            payload = dib24WithMask(
                3,
                2,
                listOf(
                    listOf(GREEN, GREEN, GREEN),
                    listOf(GREEN, GREEN, GREEN),
                ),
                listOf(
                    booleanArrayOf(false, false, false),
                    booleanArrayOf(false, false, false),
                ),
            ),
        )
        val codec = SkCodec.MakeFromData(ico(smallerPng, largerDib))!!

        assertEquals(SkEncodedImageFormat.kBMP, codec.getEncodedFormat())
        assertEquals(3, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(GREEN, bitmap!!.getPixel(2, 1))
    }

    @Test
    fun `treats zero directory dimension as 256 when ranking entries`() {
        val row256 = listOf(IntArray(256) { GREEN })
        val encodedAsZero = entry(width = 256, height = 1, payload = png(256, 1, row256))
        val smallerSquare = entry(
            width = 15,
            height = 15,
            payload = png(15, 15, List(15) { IntArray(15) { RED } }),
        )
        val codec = SkCodec.MakeFromData(ico(smallerSquare, encodedAsZero))!!

        assertEquals(SkEncodedImageFormat.kPNG, codec.getEncodedFormat())
        assertEquals(256, codec.getInfo().width)
        assertEquals(1, codec.getInfo().height)
        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(GREEN, bitmap!!.getPixel(255, 0))
    }

    @Test
    fun `applies legacy DIB AND mask as alpha`() {
        val pixels = listOf(
            listOf(RED, GREEN),
            listOf(BLUE, WHITE),
        )
        val mask = listOf(
            booleanArrayOf(false, true),
            booleanArrayOf(true, false),
        )
        val codec = SkCodec.MakeFromData(ico(entry(width = 2, height = 2, payload = dib24WithMask(2, 2, pixels, mask))))!!

        assertEquals(SkEncodedImageFormat.kBMP, codec.getEncodedFormat())
        assertEquals(2, codec.getInfo().width)
        assertEquals(2, codec.getInfo().height)

        val (bitmap, result) = codec.getImage()
        assertEquals(SkCodec.Result.kSuccess, result)
        assertNotNull(bitmap)
        assertEquals(RED, bitmap!!.getPixel(0, 0))
        assertEquals(TRANSPARENT_GREEN, bitmap.getPixel(1, 0))
        assertEquals(TRANSPARENT_BLUE, bitmap.getPixel(0, 1))
        assertEquals(WHITE, bitmap.getPixel(1, 1))
    }

    private data class Entry(val width: Int, val height: Int, val bitDepth: Int, val payload: ByteArray)

    private fun entry(width: Int, height: Int, bitDepth: Int = 32, payload: ByteArray): Entry =
        Entry(width = width, height = height, bitDepth = bitDepth, payload = payload)

    private fun ico(vararg entries: Entry): ByteArray {
        val headerBytes = 6 + entries.size * 16
        val payloadBytes = entries.sumOf { it.payload.size }
        val out = ByteArray(headerBytes + payloadBytes)
        writeU16LE(out, 2, 1)
        writeU16LE(out, 4, entries.size)
        var payloadOffset = headerBytes
        for ((index, image) in entries.withIndex()) {
            val entryOffset = 6 + index * 16
            out[entryOffset] = if (image.width == 256) 0 else image.width.toByte()
            out[entryOffset + 1] = if (image.height == 256) 0 else image.height.toByte()
            writeU16LE(out, entryOffset + 4, 1)
            writeU16LE(out, entryOffset + 6, image.bitDepth)
            writeI32LE(out, entryOffset + 8, image.payload.size)
            writeI32LE(out, entryOffset + 12, payloadOffset)
            System.arraycopy(image.payload, 0, out, payloadOffset, image.payload.size)
            payloadOffset += image.payload.size
        }
        return out
    }

    private fun png(width: Int, height: Int, pixels: List<IntArray>): ByteArray {
        val raw = ByteArrayOutputStream()
        for (y in 0 until height) {
            raw.write(0)
            for (x in 0 until width) {
                val color = pixels[y][x]
                raw.write(r(color))
                raw.write(g(color))
                raw.write(b(color))
                raw.write(a(color))
            }
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(height)
                write(8)
                write(6)
                write(0)
                write(0)
                write(0)
            }.toByteArray())
            writeChunk("IDAT", deflate(raw.toByteArray()))
            writeChunk("IEND", ByteArray(0))
        }.toByteArray()
    }

    private fun dib32(width: Int, height: Int, pixels: List<List<Int>>): ByteArray {
        val rowBytes = width * 4
        val maskRowBytes = (((width + 31) / 32) * 4)
        val out = ByteArray(40 + rowBytes * height + maskRowBytes * height)
        writeI32LE(out, 0, 40)
        writeI32LE(out, 4, width)
        writeI32LE(out, 8, height * 2)
        writeU16LE(out, 12, 1)
        writeU16LE(out, 14, 32)
        writeI32LE(out, 20, rowBytes * height)
        for (dy in 0 until height) {
            val fileRow = height - 1 - dy
            val rowOffset = 40 + fileRow * rowBytes
            for (x in 0 until width) {
                val color = pixels[dy][x]
                val offset = rowOffset + x * 4
                out[offset] = b(color).toByte()
                out[offset + 1] = g(color).toByte()
                out[offset + 2] = r(color).toByte()
                out[offset + 3] = a(color).toByte()
            }
        }
        return out
    }

    private fun dib24WithMask(
        width: Int,
        height: Int,
        pixels: List<List<Int>>,
        mask: List<BooleanArray>,
    ): ByteArray {
        val rowBytes = (((width * 24 + 31) / 32) * 4)
        val maskRowBytes = (((width + 31) / 32) * 4)
        val maskOffset = 40 + rowBytes * height
        val out = ByteArray(maskOffset + maskRowBytes * height)
        writeI32LE(out, 0, 40)
        writeI32LE(out, 4, width)
        writeI32LE(out, 8, height * 2)
        writeU16LE(out, 12, 1)
        writeU16LE(out, 14, 24)
        writeI32LE(out, 20, rowBytes * height)
        for (dy in 0 until height) {
            val fileRow = height - 1 - dy
            val rowOffset = 40 + fileRow * rowBytes
            val maskRowOffset = maskOffset + fileRow * maskRowBytes
            for (x in 0 until width) {
                val color = pixels[dy][x]
                val offset = rowOffset + x * 3
                out[offset] = b(color).toByte()
                out[offset + 1] = g(color).toByte()
                out[offset + 2] = r(color).toByte()
                if (mask[dy][x]) {
                    out[maskRowOffset + x / 8] = (
                        out[maskRowOffset + x / 8].toInt() or (1 shl (7 - (x and 7)))
                    ).toByte()
                }
            }
        }
        return out
    }
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

private const val RED: Int = -0x10000
private const val GREEN: Int = -0xff0100
private const val BLUE: Int = -0xffff01
private const val WHITE: Int = -0x1
private const val TRANSPARENT_GREEN: Int = 0x0000FF00
private const val TRANSPARENT_BLUE: Int = 0x000000FF

private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    writeI32BE(data.size)
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
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
    val buffer = ByteArray(256)
    val out = ByteArrayOutputStream()
    while (!deflater.finished()) {
        out.write(buffer, 0, deflater.deflate(buffer))
    }
    deflater.end()
    return out.toByteArray()
}

private fun writeU16LE(out: ByteArray, offset: Int, value: Int) {
    out[offset] = value.toByte()
    out[offset + 1] = (value ushr 8).toByte()
}

private fun writeI32LE(out: ByteArray, offset: Int, value: Int) {
    out[offset] = value.toByte()
    out[offset + 1] = (value ushr 8).toByte()
    out[offset + 2] = (value ushr 16).toByte()
    out[offset + 3] = (value ushr 24).toByte()
}

private fun a(c: Int): Int = (c ushr 24) and 0xFF
private fun r(c: Int): Int = (c ushr 16) and 0xFF
private fun g(c: Int): Int = (c ushr 8) and 0xFF
private fun b(c: Int): Int = c and 0xFF
