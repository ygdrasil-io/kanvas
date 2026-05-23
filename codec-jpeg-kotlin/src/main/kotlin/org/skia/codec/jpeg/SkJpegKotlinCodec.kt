package org.skia.codec.jpeg

import org.skia.codec.CodecDecoderProvider
import org.skia.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * First pure Kotlin JPEG decoder slice.
 *
 * This backend owns the baseline JPEG container path directly: marker parsing,
 * DQT/DHT/SOF0/SOS, entropy bit reading, Huffman decode, dequantization, and
 * IDCT. The decode surface is intentionally narrow: sequential 8-bit grayscale
 * JPEGs and 3-component YCbCr JPEGs with 1x1 sampling.
 */
public class SkJpegKotlinCodec private constructor(
    private val jpeg: ParsedJpeg,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = jpeg.width,
            height = jpeg.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG

    override fun getICCProfile(): SkcmsICCProfile? = null

    override fun getOrigin(): SkEncodedOrigin = SkEncodedOrigin.kTopLeft

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) return Result.kInvalidParameters
        if (dst.colorType != info.colorType) return Result.kInvalidParameters
        if (info.colorType != SkColorType.kRGBA_8888) return Result.kInvalidConversion

        val pixels = try {
            decodeBaseline(jpeg)
        } catch (_: IllegalArgumentException) {
            return Result.kErrorInInput
        }
        System.arraycopy(pixels, 0, dst.pixels8888, 0, pixels.size)
        return Result.kSuccess
    }

    internal companion object Decoder : SkCodec.Decoder {
        override val name: String = "jpeg"

        override fun matches(data: ByteArray): Boolean =
            data.size >= 3 &&
                data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()

        override fun make(data: ByteArray): SkCodec? {
            if (!matches(data)) return null
            val parsed = try {
                parseJpeg(data)
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
            return SkJpegKotlinCodec(parsed)
        }
    }
}

public class JpegKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<SkCodec.Decoder> = listOf(SkJpegKotlinCodec.Decoder)
}

private data class ParsedJpeg(
    val width: Int,
    val height: Int,
    val quantTables: Array<IntArray?>,
    val dcTables: Array<HuffmanTable?>,
    val acTables: Array<HuffmanTable?>,
    val components: List<Component>,
    val scanData: ByteArray,
)

private data class Component(
    val id: Int,
    val h: Int,
    val v: Int,
    val quantTable: Int,
    val frameIndex: Int,
    val dcTable: Int,
    val acTable: Int,
)

private data class Frame(
    val width: Int,
    val height: Int,
    val components: List<Component>,
)

private class HuffmanTable(lengths: IntArray, symbols: IntArray) {
    private val entries: List<Entry>

    init {
        val out = ArrayList<Entry>()
        var code = 0
        var symbolIndex = 0
        for (length in 1..16) {
            val count = lengths[length - 1]
            for (i in 0 until count) {
                if (symbolIndex >= symbols.size) fail()
                out += Entry(code, length, symbols[symbolIndex++])
                code++
            }
            code = code shl 1
        }
        if (symbolIndex != symbols.size) fail()
        entries = out
    }

    fun decode(reader: EntropyBitReader): Int {
        var code = 0
        for (length in 1..16) {
            code = (code shl 1) or reader.readBit()
            val match = entries.firstOrNull { it.length == length && it.code == code }
            if (match != null) return match.symbol
        }
        fail()
    }

    private data class Entry(val code: Int, val length: Int, val symbol: Int)
}

private fun parseJpeg(data: ByteArray): ParsedJpeg? {
    if (data.size < 4 || data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) return null
    val quantTables = arrayOfNulls<IntArray>(4)
    val dcTables = arrayOfNulls<HuffmanTable>(4)
    val acTables = arrayOfNulls<HuffmanTable>(4)
    var width = 0
    var height = 0
    var frameComponents: List<Component>? = null
    var scanComponents: List<Component>? = null
    var offset = 2

    while (offset < data.size) {
        while (offset < data.size && data[offset] == 0xFF.toByte()) offset++
        if (offset >= data.size) return null
        val marker = data[offset++].toInt() and 0xFF
        when (marker) {
            MARKER_EOI -> break
            in 0xD0..0xD7, MARKER_TEM -> continue
            MARKER_SOS -> {
                if (offset + 2 > data.size) return null
                val length = readU16BE(data, offset)
                val payloadStart = offset + 2
                val payloadEnd = offset + length
                if (length < 2 || payloadEnd > data.size) return null
                val current = parseSos(data, payloadStart, payloadEnd, frameComponents) ?: return null
                scanComponents = current
                offset = payloadEnd
                val scanStart = offset
                while (offset + 1 < data.size) {
                    if (data[offset] == 0xFF.toByte()) {
                        var markerOffset = offset + 1
                        while (markerOffset < data.size && data[markerOffset] == 0xFF.toByte()) markerOffset++
                        if (markerOffset >= data.size) return null
                        val next = data[markerOffset].toInt() and 0xFF
                        if (next != 0x00) {
                            val scan = data.copyOfRange(scanStart, offset)
                            if (next != MARKER_EOI) return null
                            return buildParsed(width, height, quantTables, dcTables, acTables, scanComponents, scan)
                        }
                        offset = markerOffset + 1
                    } else {
                        offset++
                    }
                }
                return null
            }
            else -> {
                if (offset + 2 > data.size) return null
                val length = readU16BE(data, offset)
                val payloadStart = offset + 2
                val payloadEnd = offset + length
                if (length < 2 || payloadEnd > data.size) return null
                when (marker) {
                    MARKER_DQT -> parseDqt(data, payloadStart, payloadEnd, quantTables)
                    MARKER_DHT -> parseDht(data, payloadStart, payloadEnd, dcTables, acTables)
                    MARKER_SOF0 -> {
                        val frame = parseSof0(data, payloadStart, payloadEnd) ?: return null
                        width = frame.width
                        height = frame.height
                        frameComponents = frame.components
                    }
                    MARKER_SOF2 -> return null
                    MARKER_DRI -> return null
                }
                offset = payloadEnd
            }
        }
    }
    return null
}

private fun buildParsed(
    width: Int,
    height: Int,
    quantTables: Array<IntArray?>,
    dcTables: Array<HuffmanTable?>,
    acTables: Array<HuffmanTable?>,
    components: List<Component>?,
    scanData: ByteArray,
): ParsedJpeg? {
    val cs = components ?: return null
    if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
    if (cs.size != 1 && cs.size != 3) return null
    for (component in cs) {
        if (component.h != 1 || component.v != 1) return null
        if (
            component.quantTable !in quantTables.indices ||
            component.dcTable !in dcTables.indices ||
            component.acTable !in acTables.indices
        ) {
            return null
        }
        if (
            quantTables[component.quantTable] == null ||
            dcTables[component.dcTable] == null ||
            acTables[component.acTable] == null
        ) {
            return null
        }
    }
    if (scanData.isEmpty()) return null
    return ParsedJpeg(width, height, quantTables, dcTables, acTables, cs, scanData)
}

private fun parseDqt(data: ByteArray, start: Int, end: Int, tables: Array<IntArray?>) {
    var p = start
    while (p < end) {
        val spec = data[p++].toInt() and 0xFF
        val precision = spec ushr 4
        val tableId = spec and 0x0F
        if (precision != 0 || tableId !in tables.indices || p + 64 > end) fail()
        val table = IntArray(64)
        for (i in 0 until 64) table[ZIGZAG[i]] = data[p++].toInt() and 0xFF
        tables[tableId] = table
    }
    if (p != end) fail()
}

private fun parseDht(
    data: ByteArray,
    start: Int,
    end: Int,
    dcTables: Array<HuffmanTable?>,
    acTables: Array<HuffmanTable?>,
) {
    var p = start
    while (p < end) {
        val spec = data[p++].toInt() and 0xFF
        val tableClass = spec ushr 4
        val tableId = spec and 0x0F
        if (tableClass !in 0..1 || tableId !in 0..3 || p + 16 > end) fail()
        val lengths = IntArray(16)
        var symbolCount = 0
        for (i in 0 until 16) {
            lengths[i] = data[p++].toInt() and 0xFF
            symbolCount += lengths[i]
        }
        if (p + symbolCount > end) fail()
        val symbols = IntArray(symbolCount)
        for (i in 0 until symbolCount) symbols[i] = data[p++].toInt() and 0xFF
        val table = HuffmanTable(lengths, symbols)
        if (tableClass == 0) dcTables[tableId] = table else acTables[tableId] = table
    }
    if (p != end) fail()
}

private fun parseSof0(data: ByteArray, start: Int, end: Int): Frame? {
    if (end - start < 8) return null
    var p = start
    val precision = data[p++].toInt() and 0xFF
    val height = readU16BE(data, p).also { p += 2 }
    val width = readU16BE(data, p).also { p += 2 }
    val componentCount = data[p++].toInt() and 0xFF
    if (precision != 8 || componentCount !in listOf(1, 3) || end - p != componentCount * 3) return null
    val components = ArrayList<Component>(componentCount)
    val ids = HashSet<Int>()
    for (index in 0 until componentCount) {
        val id = data[p++].toInt() and 0xFF
        val sampling = data[p++].toInt() and 0xFF
        val quant = data[p++].toInt() and 0xFF
        if (!ids.add(id)) return null
        components += Component(id, sampling ushr 4, sampling and 0x0F, quant, index, 0, 0)
    }
    return Frame(width, height, components)
}

private fun parseSos(data: ByteArray, start: Int, end: Int, frameComponents: List<Component>?): List<Component>? {
    val frame = frameComponents ?: return null
    if (end - start < 6) return null
    var p = start
    val componentCount = data[p++].toInt() and 0xFF
    if (componentCount != frame.size || end - start != 4 + componentCount * 2) return null
    val components = ArrayList<Component>(componentCount)
    val seen = HashSet<Int>()
    for (i in 0 until componentCount) {
        val id = data[p++].toInt() and 0xFF
        if (!seen.add(id)) return null
        val component = frame.firstOrNull { it.id == id } ?: return null
        val tables = data[p++].toInt() and 0xFF
        components += component.copy(dcTable = tables ushr 4, acTable = tables and 0x0F)
    }
    val spectralStart = data[p++].toInt() and 0xFF
    val spectralEnd = data[p++].toInt() and 0xFF
    val approx = data[p++].toInt() and 0xFF
    if (spectralStart != 0 || spectralEnd != 63 || approx != 0) return null
    return components
}

private fun decodeBaseline(jpeg: ParsedJpeg): IntArray {
    return when (jpeg.components.size) {
        1 -> decodeGrayscale(jpeg)
        3 -> decodeColor444(jpeg)
        else -> fail()
    }
}

private fun decodeGrayscale(jpeg: ParsedJpeg): IntArray {
    val component = jpeg.components.single()
    val quant = jpeg.quantTables[component.quantTable] ?: fail()
    val dcTable = jpeg.dcTables[component.dcTable] ?: fail()
    val acTable = jpeg.acTables[component.acTable] ?: fail()
    val reader = EntropyBitReader(jpeg.scanData)
    val pixels = IntArray(jpeg.width * jpeg.height)
    var previousDc = 0
    val blocksX = (jpeg.width + 7) / 8
    val blocksY = (jpeg.height + 7) / 8

    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            val coeffs = IntArray(64)
            val dcCategory = dcTable.decode(reader)
            if (dcCategory !in 0..11) fail()
            val dcDiff = receiveAndExtend(reader, dcCategory)
            previousDc += dcDiff
            coeffs[0] = previousDc * quant[0]

            var k = 1
            while (k < 64) {
                val rs = acTable.decode(reader)
                if (rs == 0x00) break
                val run = rs ushr 4
                val size = rs and 0x0F
                if (size == 0) {
                    if (run == 15) {
                        k += 16
                        continue
                    }
                    fail()
                }
                k += run
                if (k >= 64) fail()
                coeffs[ZIGZAG[k]] = receiveAndExtend(reader, size) * quant[ZIGZAG[k]]
                k++
            }

            val block = idct(coeffs)
            for (y in 0 until 8) {
                val py = by * 8 + y
                if (py >= jpeg.height) continue
                for (x in 0 until 8) {
                    val px = bx * 8 + x
                    if (px >= jpeg.width) continue
                    val v = (block[y * 8 + x] + 128).coerceIn(0, 255)
                    pixels[py * jpeg.width + px] = argb(0xFF, v, v, v)
                }
            }
        }
    }
    return pixels
}

private fun decodeColor444(jpeg: ParsedJpeg): IntArray {
    val components = jpeg.components
    val reader = EntropyBitReader(jpeg.scanData)
    val pixels = IntArray(jpeg.width * jpeg.height)
    val previousDc = IntArray(components.size)
    val blocks = Array(components.size) { IntArray(64) }
    val blocksX = (jpeg.width + 7) / 8
    val blocksY = (jpeg.height + 7) / 8

    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            for (scanIndex in components.indices) {
                val component = components[scanIndex]
                blocks[component.frameIndex] = decodeBlock(jpeg, component, reader, previousDc, component.frameIndex)
            }

            val yBlock = blocks[0]
            val cbBlock = blocks[1]
            val crBlock = blocks[2]
            for (y in 0 until 8) {
                val py = by * 8 + y
                if (py >= jpeg.height) continue
                for (x in 0 until 8) {
                    val px = bx * 8 + x
                    if (px >= jpeg.width) continue
                    val offset = y * 8 + x
                    val yy = (yBlock[offset] + 128).coerceIn(0, 255)
                    val cb = (cbBlock[offset] + 128).coerceIn(0, 255)
                    val cr = (crBlock[offset] + 128).coerceIn(0, 255)
                    pixels[py * jpeg.width + px] = yCbCrToArgb(yy, cb, cr)
                }
            }
        }
    }
    return pixels
}

private fun decodeBlock(
    jpeg: ParsedJpeg,
    component: Component,
    reader: EntropyBitReader,
    previousDc: IntArray,
    previousDcIndex: Int,
): IntArray {
    val quant = jpeg.quantTables[component.quantTable] ?: fail()
    val dcTable = jpeg.dcTables[component.dcTable] ?: fail()
    val acTable = jpeg.acTables[component.acTable] ?: fail()
    val coeffs = IntArray(64)
    val dcCategory = dcTable.decode(reader)
    if (dcCategory !in 0..11) fail()
    val dcDiff = receiveAndExtend(reader, dcCategory)
    previousDc[previousDcIndex] += dcDiff
    coeffs[0] = previousDc[previousDcIndex] * quant[0]

    var k = 1
    while (k < 64) {
        val rs = acTable.decode(reader)
        if (rs == 0x00) break
        val run = rs ushr 4
        val size = rs and 0x0F
        if (size == 0) {
            if (run == 15) {
                k += 16
                continue
            }
            fail()
        }
        k += run
        if (k >= 64) fail()
        coeffs[ZIGZAG[k]] = receiveAndExtend(reader, size) * quant[ZIGZAG[k]]
        k++
    }

    return idct(coeffs)
}

private class EntropyBitReader(private val bytes: ByteArray) {
    private var offset = 0
    private var current = 0
    private var remaining = 0

    fun readBit(): Int {
        if (remaining == 0) {
            if (offset >= bytes.size) fail()
            current = bytes[offset++].toInt() and 0xFF
            if (current == 0xFF) {
                if (offset >= bytes.size || bytes[offset] != 0x00.toByte()) fail()
                offset++
            }
            remaining = 8
        }
        remaining--
        return (current ushr remaining) and 1
    }

    fun readBits(count: Int): Int {
        var out = 0
        for (i in 0 until count) out = (out shl 1) or readBit()
        return out
    }
}

private fun receiveAndExtend(reader: EntropyBitReader, size: Int): Int {
    if (size == 0) return 0
    val value = reader.readBits(size)
    val threshold = 1 shl (size - 1)
    return if (value < threshold) value - ((1 shl size) - 1) else value
}

private fun idct(coeffs: IntArray): IntArray {
    val out = IntArray(64)
    for (y in 0 until 8) {
        for (x in 0 until 8) {
            var sum = 0.0
            for (v in 0 until 8) {
                for (u in 0 until 8) {
                    val cu = if (u == 0) INV_SQRT2 else 1.0
                    val cv = if (v == 0) INV_SQRT2 else 1.0
                    sum += cu * cv * coeffs[v * 8 + u] *
                        cos(((2 * x + 1) * u * Math.PI) / 16.0) *
                        cos(((2 * y + 1) * v * Math.PI) / 16.0)
                }
            }
            out[y * 8 + x] = (sum / 4.0).roundToInt()
        }
    }
    return out
}

private fun readU16BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)

private fun yCbCrToArgb(y: Int, cb: Int, cr: Int): Int {
    val cbShifted = cb - 128
    val crShifted = cr - 128
    val r = (y + 1.402 * crShifted).roundToInt().coerceIn(0, 255)
    val g = (y - 0.344136 * cbShifted - 0.714136 * crShifted).roundToInt().coerceIn(0, 255)
    val b = (y + 1.772 * cbShifted).roundToInt().coerceIn(0, 255)
    return argb(0xFF, r, g, b)
}

private fun fail(): Nothing = throw IllegalArgumentException("invalid JPEG")

private const val MARKER_SOF0 = 0xC0
private const val MARKER_SOF2 = 0xC2
private const val MARKER_DHT = 0xC4
private const val MARKER_SOS = 0xDA
private const val MARKER_DQT = 0xDB
private const val MARKER_DRI = 0xDD
private const val MARKER_EOI = 0xD9
private const val MARKER_TEM = 0x01
private const val MAX_DIMENSION = 100_000
private val INV_SQRT2 = 1.0 / sqrt(2.0)

private val ZIGZAG = intArrayOf(
    0, 1, 8, 16, 9, 2, 3, 10,
    17, 24, 32, 25, 18, 11, 4, 5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13, 6, 7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63,
)
