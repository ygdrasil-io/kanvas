package org.graphiks.kanvas.codec.png

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkICC
import org.skia.foundation.SkPixmap
import org.skia.foundation.stream.SkWStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

public object PngEncoder {

    public enum class FilterFlag(public val mask: Int) {
        kZero(0x00),
        kNone(0x08),
        kSub(0x10),
        kUp(0x20),
        kAvg(0x40),
        kPaeth(0x80),
        kAll(0x08 or 0x10 or 0x20 or 0x40 or 0x80),
    }

    public data class Options(
        val filterFlags: Int = FilterFlag.kAll.mask,
        val zLibLevel: Int = 6,
        val comments: List<String> = emptyList(),
        val interlace: Boolean = false,
    ) {
        init {
            require(zLibLevel in 0..9) { "zLibLevel must be in [0, 9], got $zLibLevel" }
            require(comments.size % 2 == 0) {
                "comments must alternate keyword/text — got odd count ${comments.size}"
            }
        }
    }

    private val defaultOptions = Options()

    public fun encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (encode(baos, src, options)) baos.toByteArray() else null
    }

    public fun encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            if (src.width <= 0 || src.height <= 0) return false
            writePng(dst, src, options)
            true
        } catch (_: Throwable) {
            false
        }
    }

    public fun encode(stream: SkWStream, src: SkPixmap, options: Options = defaultOptions): Boolean {
        val bitmap = encoderSupport.pixmapToBitmap(src) ?: return false
        return encode(stream, bitmap, options)
    }

    public fun encode(src: SkPixmap, options: Options = defaultOptions): ByteArray? {
        val bitmap = encoderSupport.pixmapToBitmap(src) ?: return null
        return encode(bitmap, options)
    }

    public fun encode(stream: SkWStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        val bytes = encode(src, options) ?: return false
        return stream.write(bytes, bytes.size)
    }

    private fun writePng(dst: OutputStream, src: SkBitmap, options: Options) {
        dst.write(PNG_SIGNATURE)
        writeChunk(dst, TYPE_IHDR, ihdr(src.width, src.height, options))
        options.comments.chunked(2).forEach { (keyword, text) ->
            writeChunk(dst, TYPE_TEXT, textChunk(keyword, text))
        }
        if (src.colorSpace.isSRGB()) {
            writeChunk(dst, TYPE_SRGB, byteArrayOf(0))
            val gammaBytes = ByteArray(4)
            writeU32BE(gammaBytes, 0, 45455)
            writeChunk(dst, TYPE_GAMA, gammaBytes)
        } else {
            val iccProfileData = SkICC.WriteToICC(src.colorSpace.transferFn, src.colorSpace.toXYZD50)
            val nameBytes = "sRGB".toByteArray()
            val deflater = Deflater()
            try {
                deflater.setInput(iccProfileData)
                deflater.finish()
                val compressed = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!deflater.finished()) {
                    val n = deflater.deflate(buf)
                    if (n == 0 && deflater.needsInput()) break
                    compressed.write(buf, 0, n)
                }
                val iccpData = nameBytes + byteArrayOf(0, 0) + compressed.toByteArray()
                writeChunk(dst, TYPE_ICCP, iccpData)
            } finally {
                deflater.end()
            }
        }
        val pixelBytes = if (options.interlace) {
            adam7Rows(src, src.width, src.height, options.filterFlags)
        } else {
            filteredRgbaRows(src, options.filterFlags)
        }
        writeChunk(dst, TYPE_IDAT, deflate(pixelBytes, options.zLibLevel))
        writeChunk(dst, TYPE_IEND, ByteArray(0))
    }

    private fun ihdr(width: Int, height: Int, options: Options): ByteArray =
        ByteArray(13).also { out ->
            writeU32BE(out, 0, width)
            writeU32BE(out, 4, height)
            out[8] = 8 // bit depth
            out[9] = 6 // colour type: RGBA
            out[10] = 0 // compression
            out[11] = 0 // filter
            out[12] = if (options.interlace) 1 else 0 // interlace
        }

    private fun textChunk(keyword: String, text: String): ByteArray {
        require(keyword.isNotEmpty()) { "PNG tEXt keyword must not be empty" }
        require(keyword.length <= 79) { "PNG tEXt keyword must be at most 79 bytes" }
        val keyBytes = keyword.encodeToByteArray()
        val textBytes = text.encodeToByteArray()
        return keyBytes + byteArrayOf(0) + textBytes
    }

    private fun filteredRgbaRows(src: SkBitmap, filterFlags: Int): ByteArray {
        val allowedFilters = allowedFilters(filterFlags)
        val rowBytes = src.width * RGBA_BYTES_PER_PIXEL
        val out = ByteArray((rowBytes + 1) * src.height)
        var offset = 0
        var previous = ByteArray(rowBytes)
        for (y in 0 until src.height) {
            val current = rgbaRow(src, y)
            val filter = chooseFilter(current, previous, allowedFilters)
            out[offset++] = filter.toByte()
            writeFilteredRow(filter, current, previous, out, offset)
            offset += rowBytes
            previous = current
        }
        return out
    }

    private fun allowedFilters(filterFlags: Int): IntArray {
        val filters = ArrayList<Int>(5)
        fun addIf(mask: Int, filter: Int) {
            if ((filterFlags and mask) != 0) filters += filter
        }
        addIf(FilterFlag.kNone.mask, FILTER_NONE)
        addIf(FilterFlag.kSub.mask, FILTER_SUB)
        addIf(FilterFlag.kUp.mask, FILTER_UP)
        addIf(FilterFlag.kAvg.mask, FILTER_AVG)
        addIf(FilterFlag.kPaeth.mask, FILTER_PAETH)
        if (filters.isEmpty() || (filterFlags and FilterFlag.kZero.mask) != 0) {
            filters += FILTER_NONE
        }
        return filters.toIntArray()
    }

    private fun rgbaRow(src: SkBitmap, y: Int): ByteArray {
        val row = ByteArray(src.width * RGBA_BYTES_PER_PIXEL)
        var offset = 0
        for (x in 0 until src.width) {
            val argb = src.getPixelAsSrgb(x, y)
            row[offset++] = ((argb ushr 16) and 0xFF).toByte()
            row[offset++] = ((argb ushr 8) and 0xFF).toByte()
            row[offset++] = (argb and 0xFF).toByte()
            row[offset++] = ((argb ushr 24) and 0xFF).toByte()
        }
        return row
    }

    private fun chooseFilter(current: ByteArray, previous: ByteArray, allowed: IntArray): Int {
        var bestFilter = allowed.first()
        var bestScore = Long.MAX_VALUE
        for (filter in allowed) {
            val score = filterScore(filter, current, previous)
            if (score < bestScore) {
                bestScore = score
                bestFilter = filter
            }
        }
        return bestFilter
    }

    private fun filterScore(filter: Int, current: ByteArray, previous: ByteArray): Long {
        var score = 0L
        for (i in current.indices) {
            val raw = current[i].toInt() and 0xFF
            val left = if (i >= RGBA_BYTES_PER_PIXEL) current[i - RGBA_BYTES_PER_PIXEL].toInt() and 0xFF else 0
            val up = previous[i].toInt() and 0xFF
            val upLeft = if (i >= RGBA_BYTES_PER_PIXEL) previous[i - RGBA_BYTES_PER_PIXEL].toInt() and 0xFF else 0
            val predictor = predictor(filter, left, up, upLeft)
            val filtered = (raw - predictor) and 0xFF
            score += if (filtered < 128) filtered else 256 - filtered
        }
        return score
    }

    private fun writeFilteredRow(filter: Int, current: ByteArray, previous: ByteArray, out: ByteArray, offset: Int) {
        for (i in current.indices) {
            val raw = current[i].toInt() and 0xFF
            val left = if (i >= RGBA_BYTES_PER_PIXEL) current[i - RGBA_BYTES_PER_PIXEL].toInt() and 0xFF else 0
            val up = previous[i].toInt() and 0xFF
            val upLeft = if (i >= RGBA_BYTES_PER_PIXEL) previous[i - RGBA_BYTES_PER_PIXEL].toInt() and 0xFF else 0
            out[offset + i] = ((raw - predictor(filter, left, up, upLeft)) and 0xFF).toByte()
        }
    }

    private fun predictor(filter: Int, left: Int, up: Int, upLeft: Int): Int =
        when (filter) {
            FILTER_NONE -> 0
            FILTER_SUB -> left
            FILTER_UP -> up
            FILTER_AVG -> (left + up) / 2
            FILTER_PAETH -> paeth(left, up, upLeft)
            else -> 0
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

    private fun deflate(bytes: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        return try {
            deflater.setInput(bytes)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(bytes.size + 64)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer, 0, buffer.size)
                if (n > 0) out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun writeChunk(dst: OutputStream, type: Int, data: ByteArray) {
        writeU32BE(dst, data.size)
        val typeBytes = byteArrayOf(
            (type ushr 24).toByte(),
            (type ushr 16).toByte(),
            (type ushr 8).toByte(),
            type.toByte(),
        )
        dst.write(typeBytes)
        dst.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeU32BE(dst, crc.value.toInt())
    }

    private fun writeU32BE(dst: OutputStream, value: Int) {
        dst.write((value ushr 24) and 0xFF)
        dst.write((value ushr 16) and 0xFF)
        dst.write((value ushr 8) and 0xFF)
        dst.write(value and 0xFF)
    }

    private fun writeU32BE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value ushr 24).toByte()
        dst[offset + 1] = (value ushr 16).toByte()
        dst[offset + 2] = (value ushr 8).toByte()
        dst[offset + 3] = value.toByte()
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

    private const val RGBA_BYTES_PER_PIXEL = 4
    private const val FILTER_NONE = 0
    private const val FILTER_SUB = 1
    private const val FILTER_UP = 2
    private const val FILTER_AVG = 3
    private const val FILTER_PAETH = 4
    private const val TYPE_IHDR = 0x49484452
    private const val TYPE_IDAT = 0x49444154
    private const val TYPE_IEND = 0x49454E44
    private const val TYPE_TEXT = 0x74455874
    private const val TYPE_ICCP = 0x69434350
    private const val TYPE_SRGB = 0x73524742
    private const val TYPE_GAMA = 0x67414D41

    private val adam7Passes = arrayOf(
        intArrayOf(0, 0, 8, 8),
        intArrayOf(4, 0, 8, 8),
        intArrayOf(0, 4, 4, 8),
        intArrayOf(2, 0, 4, 4),
        intArrayOf(0, 2, 2, 4),
        intArrayOf(1, 0, 2, 2),
        intArrayOf(0, 1, 1, 2),
    )

    private fun adam7Rows(src: SkBitmap, w: Int, h: Int, filterFlags: Int): ByteArray {
        val allowedFilters = allowedFilters(filterFlags)
        val bpp = RGBA_BYTES_PER_PIXEL
        val allRows = ByteArrayOutputStream()
        for (pass in adam7Passes) {
            val xStart = pass[0]; val yStart = pass[1]
            val xStep = pass[2]; val yStep = pass[3]
            val passW = if (w > xStart) (w - xStart + xStep - 1) / xStep else 0
            val passH = if (h > yStart) (h - yStart + yStep - 1) / yStep else 0
            if (passW <= 0 || passH <= 0) continue
            val rowLen = passW * bpp
            var prevRow = ByteArray(rowLen)
            val filtered = ByteArray(rowLen)
            for (py in 0 until passH) {
                val y = yStart + py * yStep
                val current = ByteArray(rowLen)
                var off = 0
                for (px in 0 until passW) {
                    val x = xStart + px * xStep
                    val argb = src.getPixelAsSrgb(x, y)
                    current[off++] = ((argb ushr 16) and 0xFF).toByte()
                    current[off++] = ((argb ushr 8) and 0xFF).toByte()
                    current[off++] = (argb and 0xFF).toByte()
                    current[off++] = ((argb ushr 24) and 0xFF).toByte()
                }
                val filter = chooseFilter(current, prevRow, allowedFilters)
                allRows.write(filter)
                writeFilteredRow(filter, current, prevRow, filtered, 0)
                allRows.write(filtered, 0, rowLen)
                prevRow = current
            }
        }
        return allRows.toByteArray()
    }

    private object encoderSupport {
        fun pixmapToBitmap(src: SkPixmap): SkBitmap? {
            if (src.width() <= 0 || src.height() <= 0) return null
            val bitmap = SkBitmap(src.width(), src.height())
            for (y in 0 until src.height()) {
                for (x in 0 until src.width()) {
                    bitmap.pixels[y * src.width() + x] = src.getColor(x, y)
                }
            }
            return bitmap
        }
    }
}
