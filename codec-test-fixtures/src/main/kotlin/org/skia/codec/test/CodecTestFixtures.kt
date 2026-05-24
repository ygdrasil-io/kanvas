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

    public fun simpleGrayscaleJpeg(width: Int = 8, height: Int = 8): ByteArray {
        require(width > 0 && height > 0) { "JPEG fixture dimensions must be positive" }
        val out = ByteArrayOutputStream()
        out.writeJpegMarker(0xD8)
        out.writeJpegSegment(0xDB) {
            write(0)
            repeat(64) { write(1) }
        }
        out.writeJpegSegment(0xC0) {
            write(8)
            writeU16BE(height)
            writeU16BE(width)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }
        out.writeJpegSegment(0xC4) {
            write(0x00)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeJpegSegment(0xC4) {
            write(0x10)
            write(1)
            repeat(15) { write(0) }
            write(0x00)
        }
        out.writeJpegSegment(0xDA) {
            write(1)
            write(1)
            write(0x00)
            write(0)
            write(63)
            write(0)
        }
        out.write(jpegZeroBlockEntropy(((width + 7) / 8) * ((height + 7) / 8)))
        out.writeJpegMarker(0xD9)
        return out.toByteArray()
    }

    public fun indexedGif(
        pixels: List<IntArray>,
        palette: IntArray = intArrayOf(RED, GREEN, BLUE, WHITE),
    ): ByteArray {
        require(pixels.isNotEmpty()) { "GIF fixture requires at least one row" }
        val width = pixels.first().size
        require(width > 0) { "GIF fixture requires at least one column" }
        require(pixels.all { it.size == width }) { "GIF fixture rows must have equal width" }
        require(palette.size in 2..256 && palette.size.countOneBits() == 1) {
            "GIF fixture palette size must be a power of two between 2 and 256"
        }

        val out = ArrayList<Byte>()
        out.addAscii("GIF89a")
        out.addU16LE(width)
        out.addU16LE(pixels.size)
        out += (0x80 or 0x70 or colorTableSizeBits(palette.size)).toByte()
        out += 0.toByte()
        out += 0.toByte()
        out.addColorTable(palette)
        out += 0x2C.toByte()
        out.addU16LE(0)
        out.addU16LE(0)
        out.addU16LE(width)
        out.addU16LE(pixels.size)
        out += 0.toByte()
        out += GIF_MIN_CODE_SIZE.toByte()
        out.addSubBlocks(gifLzwData(pixels.flatMap { row -> row.asIterable() }.toIntArray()))
        out += 0x3B.toByte()
        return out.toByteArray()
    }

    public fun rgbBmp(pixels: List<IntArray>): ByteArray {
        require(pixels.isNotEmpty()) { "BMP fixture requires at least one row" }
        val width = pixels.first().size
        require(width > 0) { "BMP fixture requires at least one column" }
        require(pixels.all { it.size == width }) { "BMP fixture rows must have equal width" }

        val height = pixels.size
        val rowBytes = bmpRowBytes(width, bitsPerPixel = 24)
        val pixelOffset = 14 + 40
        val out = ByteArray(pixelOffset + rowBytes * height)
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        out.writeI32LE(2, out.size)
        out.writeI32LE(10, pixelOffset)
        out.writeI32LE(14, 40)
        out.writeI32LE(18, width)
        out.writeI32LE(22, height)
        out.writeU16LE(26, 1)
        out.writeU16LE(28, 24)
        for (dy in 0 until height) {
            val row = pixelOffset + (height - 1 - dy) * rowBytes
            for (x in 0 until width) {
                val color = pixels[dy][x]
                val off = row + x * 3
                out[off] = (color and 0xFF).toByte()
                out[off + 1] = ((color ushr 8) and 0xFF).toByte()
                out[off + 2] = ((color ushr 16) and 0xFF).toByte()
            }
        }
        return out
    }

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

    private fun ByteArrayOutputStream.writeJpegMarker(marker: Int) {
        write(0xFF)
        write(marker)
    }

    private fun ByteArrayOutputStream.writeJpegSegment(marker: Int, writePayload: ByteArrayOutputStream.() -> Unit) {
        val payload = ByteArrayOutputStream().apply(writePayload).toByteArray()
        writeJpegMarker(marker)
        writeU16BE(payload.size + 2)
        write(payload)
    }

    private fun ByteArrayOutputStream.writeU16BE(value: Int) {
        write((value ushr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun jpegZeroBlockEntropy(blockCount: Int): ByteArray {
        val bitCount = blockCount * 2
        val byteCount = (bitCount + 7) / 8
        val out = ByteArray(byteCount)
        for (bit in bitCount until byteCount * 8) {
            val byte = bit / 8
            val shift = 7 - (bit and 7)
            out[byte] = (out[byte].toInt() or (1 shl shift)).toByte()
        }
        return out
    }

    private const val GIF_MIN_CODE_SIZE = 2

    private fun gifLzwData(indexes: IntArray): ByteArray {
        val clearCode = 1 shl GIF_MIN_CODE_SIZE
        val endCode = clearCode + 1
        val codes = buildList {
            var offset = 0
            while (offset < indexes.size) {
                add(clearCode)
                add(indexes[offset++])
                if (offset < indexes.size) add(indexes[offset++])
            }
            add(endCode)
        }
        val out = ArrayList<Byte>()
        var current = 0
        var bits = 0
        for (code in codes) {
            current = current or (code shl bits)
            bits += GIF_MIN_CODE_SIZE + 1
            while (bits >= 8) {
                out += (current and 0xFF).toByte()
                current = current ushr 8
                bits -= 8
            }
        }
        if (bits > 0) out += (current and 0xFF).toByte()
        return out.toByteArray()
    }

    private fun ArrayList<Byte>.addSubBlocks(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(255, bytes.size - offset)
            add(count.toByte())
            for (i in 0 until count) add(bytes[offset + i])
            offset += count
        }
        add(0)
    }

    private fun ArrayList<Byte>.addColorTable(colors: IntArray) {
        for (color in colors) {
            add(((color ushr 16) and 0xFF).toByte())
            add(((color ushr 8) and 0xFF).toByte())
            add((color and 0xFF).toByte())
        }
    }

    private fun ArrayList<Byte>.addAscii(value: String) {
        for (byte in value.toByteArray(Charsets.US_ASCII)) add(byte)
    }

    private fun ArrayList<Byte>.addU16LE(value: Int) {
        add((value and 0xFF).toByte())
        add(((value ushr 8) and 0xFF).toByte())
    }

    private fun colorTableSizeBits(size: Int): Int {
        var value = 2
        var bits = 0
        while (value < size) {
            value = value shl 1
            bits++
        }
        return bits
    }

    private fun bmpRowBytes(width: Int, bitsPerPixel: Int): Int = ((width * bitsPerPixel + 31) / 32) * 4

    private fun ByteArray.writeU16LE(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun ByteArray.writeI32LE(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
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
