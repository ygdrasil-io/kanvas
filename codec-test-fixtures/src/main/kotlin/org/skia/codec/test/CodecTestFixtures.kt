package org.skia.codec.test

import org.skia.codec.SkCodec
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

public object CodecTestFixtures {
    public const val RED: Int = 0xFFFF0000.toInt()
    public const val GREEN: Int = 0xFF00FF00.toInt()
    public const val BLUE: Int = 0xFF0000FF.toInt()
    public const val WHITE: Int = 0xFFFFFFFF.toInt()

    public val SIMPLE_RGBA_PIXELS: List<IntArray> = listOf(
        intArrayOf(RED, GREEN),
        intArrayOf(BLUE, WHITE),
    )

    public fun simpleRgbaPng(): ByteArray = rgbaPng(SIMPLE_RGBA_PIXELS)

    public fun rgbaPng(pixels: List<IntArray>): ByteArray {
        require(pixels.isNotEmpty()) { "PNG fixture requires at least one row" }
        val width = pixels.first().size
        require(width > 0) { "PNG fixture requires at least one column" }
        require(pixels.all { it.size == width }) { "PNG fixture rows must have equal width" }

        val raw = ByteArrayOutputStream()
        for (row in pixels) {
            raw.write(0)
            for (color in row) {
                raw.write((color ushr 16) and 0xFF)
                raw.write((color ushr 8) and 0xFF)
                raw.write(color and 0xFF)
                raw.write((color ushr 24) and 0xFF)
            }
        }

        return ByteArrayOutputStream().apply {
            write(PNG_SIGNATURE)
            writeChunk("IHDR", ByteArrayOutputStream().apply {
                writeI32BE(width)
                writeI32BE(pixels.size)
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

    public fun decodePixels(codec: SkCodec): List<IntArray> {
        val (bitmap, result) = codec.getImage()
        require(result == SkCodec.Result.kSuccess) { "Expected successful decode, got $result" }
        require(bitmap != null) { "Successful decode did not return a bitmap" }
        return bitmap.toArgbRows()
    }

    public fun SkBitmap.toArgbRows(): List<IntArray> =
        List(height) { y ->
            IntArray(width) { x -> getPixel(x, y) }
        }

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(1024)
        val out = ByteArrayOutputStream()
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

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
}

public object CodecNegativeFixtures {
    public data class NegativeCase(
        public val name: String,
        public val data: ByteArray,
    )

    public fun invalidMagic(name: String, data: ByteArray): NegativeCase =
        NegativeCase(name, data)

    public fun invalidMagic(name: String, ascii: String): NegativeCase =
        invalidMagic(name, ascii.toByteArray(Charsets.US_ASCII))

    public fun truncated(name: String, data: ByteArray, size: Int): NegativeCase {
        require(size in 0..data.size) { "truncated fixture size must be within the input" }
        return NegativeCase(name, data.copyOf(size))
    }

    public fun truncatedTail(name: String, data: ByteArray, droppedBytes: Int): NegativeCase {
        require(droppedBytes in 0..data.size) { "dropped byte count must be within the input" }
        return truncated(name, data, data.size - droppedBytes)
    }

    public fun mutatedByte(name: String, data: ByteArray, index: Int, value: Int): NegativeCase {
        require(index in data.indices) { "mutation index must be within the input" }
        val copy = data.copyOf()
        copy[index] = value.toByte()
        return NegativeCase(name, copy)
    }

    public fun invalidSize(name: String, data: ByteArray): NegativeCase =
        NegativeCase(name, data)

    public fun duplicateMetadata(name: String, data: ByteArray): NegativeCase =
        NegativeCase(name, data)

    public fun misplacedMetadata(name: String, data: ByteArray): NegativeCase =
        NegativeCase(name, data)
}
