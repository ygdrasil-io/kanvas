package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPixmap
import org.skia.foundation.stream.SkWStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * PNG encoder — D3.5 implementation of upstream's
 * [`SkPngEncoder`](https://github.com/google/skia/blob/main/include/encode/SkPngEncoder.h).
 *
 * Mirrors the upstream namespace as a Kotlin `object` carrying the
 * static `Encode` entry points, plus a Kotlin-idiomatic [Options]
 * data class that maps onto the upstream `SkPngEncoder::Options`
 * struct. The bitstream is written directly in Kotlin: PNG signature,
 * `IHDR`, optional `tEXt`, `IDAT`, `IEND`, zlib compression, and CRC.
 *
 * **Honoured options** : [Options.zLibLevel], [Options.comments], and
 * [Options.filterFlags]. When multiple filters are allowed, the encoder
 * chooses the row filter with the smallest absolute-byte score.
 *
 * **Colour space** : the caller's [SkBitmap.colorSpace] is **not**
 * embedded as an `iCCP` chunk. Pixel rows are materialized through
 * [SkBitmap.getPixelAsSrgb], so F16 non-sRGB bitmaps are converted to
 * untagged sRGB RGBA samples at the encoder boundary.
 */
public object SkPngEncoder {

    /**
     * Mirrors `SkPngEncoder::FilterFlag`. Each flag is a libpng
     * filter selector ; combining them tells libpng to pick the
     * smallest-encoded filter per row. [kAll] matches libpng's
     * default and also matches this encoder's multi-filter row
     * heuristic.
     */
    public enum class FilterFlag(public val mask: Int) {
        kZero(0x00),
        kNone(0x08),
        kSub(0x10),
        kUp(0x20),
        kAvg(0x40),
        kPaeth(0x80),
        kAll(0x08 or 0x10 or 0x20 or 0x40 or 0x80),
    }

    /**
     * Mirrors `SkPngEncoder::Options`.
     */
    public data class Options(
        /** Bitfield of [FilterFlag]s. */
        val filterFlags: Int = FilterFlag.kAll.mask,
        /** Must be in `[0, 9]` ; 9 = max compression. Advisory. */
        val zLibLevel: Int = 6,
        /**
         * `tEXt` keyword/value pairs. Even-indexed entries are
         * keywords ; odd-indexed entries are the corresponding text.
         * `tEXt` keyword/value pairs written before `IDAT`.
         */
        val comments: List<String> = emptyList(),
    ) {
        init {
            require(zLibLevel in 0..9) { "zLibLevel must be in [0, 9], got $zLibLevel" }
            require(comments.size % 2 == 0) {
                "comments must alternate keyword/text — got odd count ${comments.size}"
            }
        }
    }

    /** Default-options [Options] singleton — saves one allocation per call. */
    private val defaultOptions = Options()

    /**
     * Encode [src]'s pixels and return the PNG bytes, or `null` on
     * encoder failure. Mirrors `sk_sp<SkData>
     * SkPngEncoder::Encode(const SkPixmap&, const Options&)`.
     */
    public fun Encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (Encode(baos, src, options)) baos.toByteArray() else null
    }

    /**
     * Encode [src]'s pixels into [dst]. Returns `true` on success.
     * Mirrors `bool SkPngEncoder::Encode(SkWStream*, const SkPixmap&,
     * const Options&)`. The caller retains ownership of [dst].
     */
    public fun Encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            if (src.width <= 0 || src.height <= 0) return false
            writePng(dst, src, options)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ── R-final.6 overloads : SkPixmap + SkWStream ───────────────────────
    //
    // The plan calls for `SkPngEncoder.Encode(stream, pixmap, options)` to
    // mirror the upstream `SkWStream*` + `SkPixmap` signature. The Kotlin
    // implementation routes through the existing SkBitmap path : the pixmap
    // is materialised into a fresh 8888 bitmap via `getColor` (which honours
    // the source colour type), then the bitmap path writes the PNG directly.
    // Slow for huge images but correct for the GM-sized inputs that exercise
    // this overload.

    /**
     * Encode [src]'s pixels into [stream]. Returns `true` on success.
     * Mirrors upstream's `bool SkPngEncoder::Encode(SkWStream*, const
     * SkPixmap&, const Options&)`. The caller retains ownership of
     * [stream]. See class kdoc for the list of honoured options.
     */
    public fun Encode(stream: SkWStream, src: SkPixmap, options: Options = defaultOptions): Boolean {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return false
        return Encode(stream, bitmap, options)
    }

    /**
     * Encode [src]'s pixels into [bytes]. Returns `null` if encoding
     * fails. Convenience wrapper for the upstream `sk_sp<SkData>
     * SkPngEncoder::Encode(const SkPixmap&, const Options&)` shape.
     */
    public fun Encode(src: SkPixmap, options: Options = defaultOptions): ByteArray? {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return null
        return Encode(bitmap, options)
    }

    /**
     * Convenience overload — writes the encoded bytes into [stream] via
     * the [SkWStream.write] contract instead of an [OutputStream].
     * Useful for call sites already plumbing the foundation type.
     */
    public fun Encode(stream: SkWStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        val bytes = Encode(src, options) ?: return false
        return stream.write(bytes, bytes.size)
    }

    private fun writePng(dst: OutputStream, src: SkBitmap, options: Options) {
        dst.write(PNG_SIGNATURE)
        writeChunk(dst, TYPE_IHDR, ihdr(src.width, src.height))
        options.comments.chunked(2).forEach { (keyword, text) ->
            writeChunk(dst, TYPE_TEXT, textChunk(keyword, text))
        }
        writeChunk(dst, TYPE_IDAT, deflate(filteredRgbaRows(src, options.filterFlags), options.zLibLevel))
        writeChunk(dst, TYPE_IEND, ByteArray(0))
    }

    private fun ihdr(width: Int, height: Int): ByteArray =
        ByteArray(13).also { out ->
            writeU32BE(out, 0, width)
            writeU32BE(out, 4, height)
            out[8] = 8 // bit depth
            out[9] = 6 // colour type: RGBA
            out[10] = 0 // compression
            out[11] = 0 // filter
            out[12] = 0 // interlace
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
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count == 0 && deflater.needsInput()) break
                out.write(buffer, 0, count)
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
}
