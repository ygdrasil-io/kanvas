package org.graphiks.kanvas.codec.gif

import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object GifEncoder {

    public data class Options(
        /** 0 = no loop, -1 = infinite, positive = play N times */
        val loopCount: Int = 0,
    )

    private val defaultOptions = Options()

    public fun encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (encode(baos, bitmap, options)) baos.toByteArray() else null
    }

    public fun encode(dst: OutputStream, bitmap: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return false
            writeGif(dst, bitmap, w, h, options)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeGif(out: OutputStream, bitmap: SkBitmap, w: Int, h: Int, options: Options) {
        val palette = buildPalette(bitmap, w, h)
        val colorDepth = colorTableBitDepth(palette.size)

        out.write("GIF87a".toByteArray())
        writeU16LE(out, w)
        writeU16LE(out, h)
        val packed = 0x80 or ((colorDepth - 1) and 0x07)
        out.write(packed)
        out.write(0)
        out.write(0)

        for (color in palette) {
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
        for (i in palette.size until (1 shl colorDepth)) {
            out.write(0); out.write(0); out.write(0)
        }

        if (options.loopCount != 0) {
            out.write(0x21)
            out.write(0xFF)
            out.write(11)
            out.write("NETSCAPE2.0".toByteArray())
            out.write(3)
            out.write(1)
            writeU16LE(out, if (options.loopCount < 0) 0 else options.loopCount)
            out.write(0)
        }

        out.write(0x2C)
        writeU16LE(out, 0)
        writeU16LE(out, 0)
        writeU16LE(out, w)
        writeU16LE(out, h)
        out.write(0)

        val indices = buildIndices(bitmap, w, h, palette)
        val codeSize = maxOf(2, colorDepth)
        out.write(codeSize)
        lzwEncode(out, indices, codeSize)

        out.write(0x3B)
    }

    private fun buildPalette(bitmap: SkBitmap, w: Int, h: Int): IntArray {
        val colorSet = linkedSetOf<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            colorSet.add(bitmap.getPixel(x, y) and 0x00FFFFFF)
            if (colorSet.size >= 256) break
        }
        return colorSet.toIntArray()
    }

    private fun buildIndices(bitmap: SkBitmap, w: Int, h: Int, palette: IntArray): ByteArray {
        val indexMap = mutableMapOf<Int, Int>()
        for (i in palette.indices) indexMap[palette[i]] = i
        val indices = ByteArray(w * h)
        var pos = 0
        for (y in 0 until h) for (x in 0 until w) {
            val rgb = bitmap.getPixel(x, y) and 0x00FFFFFF
            indices[pos++] = (indexMap[rgb] ?: 0).toByte()
        }
        return indices
    }

    private fun colorTableBitDepth(size: Int): Int {
        var depth = 1
        while ((1 shl depth) < size && depth < 8) depth++
        return depth
    }

    private fun lzwEncode(out: OutputStream, indices: ByteArray, minCodeSize: Int) {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var nextCode = eoiCode + 1
        var codeSize = minCodeSize + 1

        val table = linkedMapOf<String, Int>()
        for (i in 0 until (1 shl minCodeSize)) {
            table[i.toString()] = i
        }

        val bitBuffer = ByteArrayOutputStream()
        var bitPos = 0
        var currentByte = 0

        fun writeBits(code: Int, bits: Int) {
            var remaining = code
            var b = bits
            while (b > 0) {
                currentByte = currentByte or ((remaining and 1) shl bitPos)
                remaining = remaining ushr 1
                bitPos++
                b--
                if (bitPos == 8) {
                    bitBuffer.write(currentByte)
                    currentByte = 0
                    bitPos = 0
                }
            }
        }

        fun flushBits() {
            if (bitPos > 0) bitBuffer.write(currentByte)
            currentByte = 0
            bitPos = 0
        }

        // Simulate the decoder's dictionary size for code-size decisions.
        // The decoder adds one entry per data code after the first.
        var decoderDictSize = (1 shl minCodeSize) + 2
        var codesWrittenAfterClear = 0

        fun updateCodeSizeForDecoder() {
            if (codesWrittenAfterClear > 1) decoderDictSize++
            while (decoderDictSize >= (1 shl codeSize) && codeSize < 12) {
                codeSize++
            }
        }

        writeBits(clearCode, codeSize)
        codesWrittenAfterClear = 0

        var current: String? = null
        for (index in indices) {
            val next = index.toInt() and 0xFF
            val combined = if (current != null) "$current,$next" else next.toString()
            if (table.containsKey(combined)) {
                current = combined
            } else {
                writeBits(table[current!!]!!, codeSize)
                codesWrittenAfterClear++
                updateCodeSizeForDecoder()
                if (nextCode < 4096) {
                    table[combined] = nextCode
                    nextCode++
                } else {
                    writeBits(clearCode, codeSize)
                    codesWrittenAfterClear = 0
                    decoderDictSize = (1 shl minCodeSize) + 2
                    codeSize = minCodeSize + 1
                    table.clear()
                    for (i in 0 until (1 shl minCodeSize)) table[i.toString()] = i
                    nextCode = eoiCode + 1
                }
                current = next.toString()
            }
        }

        if (current != null) {
            writeBits(table[current]!!, codeSize)
            codesWrittenAfterClear++
            updateCodeSizeForDecoder()
        }
        writeBits(eoiCode, codeSize)
        flushBits()

        val compressed = bitBuffer.toByteArray()
        var offset = 0
        while (offset < compressed.size) {
            val blockSize = minOf(255, compressed.size - offset)
            out.write(blockSize)
            out.write(compressed, offset, blockSize)
            offset += blockSize
        }
        out.write(0)
    }

    private fun writeU16LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }
}
