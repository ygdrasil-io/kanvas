package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.SkCodec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import org.skia.utils.SkPixmapUtils
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * First pure Kotlin JPEG decoder slice.
 *
 * This backend owns the baseline JPEG container path directly: marker parsing,
 * DQT/DHT/SOF0/SOS, entropy bit reading, Huffman decode, dequantization, and
 * IDCT. The decode surface is intentionally narrow: sequential 8-bit grayscale,
 * 3-component YCbCr JPEGs with 4:4:4, 4:2:2, or 4:2:0 sampling, and
 * baseline Adobe CMYK/YCCK JPEGs.
 */
public class SkJpegKotlinCodec private constructor(
    private val jpeg: ParsedJpeg,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = if (jpeg.origin.swapsWidthHeight()) jpeg.height else jpeg.width,
            height = if (jpeg.origin.swapsWidthHeight()) jpeg.width else jpeg.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = jpeg.iccProfile?.let { SkColorSpace.make(it) } ?: SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG

    override fun getICCProfile(): SkcmsICCProfile? = jpeg.iccProfile

    override fun getOrigin(): SkEncodedOrigin = jpeg.origin

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) return Result.kInvalidParameters
        if (dst.colorType != info.colorType) return Result.kInvalidParameters
        if (!canDecodeTo(info.colorType)) return Result.kInvalidConversion
        val pixels = try {
            if (jpeg.coding == JpegCoding.kProgressive) {
                decodeProgressive(jpeg) ?: return Result.kUnimplemented
            } else {
                decodeBaseline(jpeg)
            }
        } catch (_: IllegalArgumentException) {
            return Result.kErrorInInput
        }
        if (jpeg.origin == SkEncodedOrigin.kTopLeft) {
            return writeDecodedPixels(dst, pixels)
        }
        val raw = SkBitmap(
            width = jpeg.width,
            height = jpeg.height,
            colorSpace = info.colorSpace,
            colorType = info.colorType,
        )
        val copyResult = writeDecodedPixels(raw, pixels)
        if (copyResult != Result.kSuccess) return copyResult
        if (!SkPixmapUtils.Orient(dst, raw, jpeg.origin)) return Result.kInvalidParameters
        return Result.kSuccess
    }

    private fun canDecodeTo(colorType: SkColorType): Boolean =
        colorType == SkColorType.kRGBA_8888 || colorType == SkColorType.kRGBA_F16Norm

    private fun writeDecodedPixels(dst: SkBitmap, pixels: IntArray): Result {
        return when (dst.colorType) {
            SkColorType.kRGBA_8888 -> {
                System.arraycopy(pixels, 0, dst.pixels8888, 0, pixels.size)
                Result.kSuccess
            }
            SkColorType.kRGBA_F16Norm -> {
                for (y in 0 until dst.height) {
                    for (x in 0 until dst.width) {
                        val color = pixels[y * dst.width + x]
                        val a = ((color ushr 24) and 0xFF) / 255f
                        val r = ((color ushr 16) and 0xFF) / 255f
                        val g = ((color ushr 8) and 0xFF) / 255f
                        val b = (color and 0xFF) / 255f
                        dst.setPixelF16(x, y, r * a, g * a, b * a, a)
                    }
                }
                Result.kSuccess
            }
            else -> Result.kInvalidConversion
        }
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
    val restartInterval: Int,
    val iccProfile: SkcmsICCProfile?,
    val origin: SkEncodedOrigin,
    val coding: JpegCoding,
    val scanSpectralStart: Int,
    val scanSpectralEnd: Int,
    val scanSuccessiveApprox: Int,
    val scanCount: Int,
    val scans: List<EntropyScan>,
    val adobeTransform: Int?,
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

private data class Scan(
    val components: List<Component>,
    val spectralStart: Int,
    val spectralEnd: Int,
    val successiveApprox: Int,
)

private data class EntropyScan(
    val scan: Scan,
    val data: ByteArray,
)

private enum class JpegCoding {
    kBaseline,
    kProgressive,
}

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
    var scan: Scan? = null
    var scanData = ByteArray(0)
    var scanCount = 0
    val scans = ArrayList<EntropyScan>()
    var coding: JpegCoding? = null
    var restartInterval = 0
    var origin = SkEncodedOrigin.kTopLeft
    var adobeTransform: Int? = null
    val iccChunks = ArrayList<Pair<Int, ByteArray>>()
    var iccChunkCount = -1
    var offset = 2

    while (offset < data.size) {
        while (offset < data.size && data[offset] == 0xFF.toByte()) offset++
        if (offset >= data.size) return null
        val marker = data[offset++].toInt() and 0xFF
        when (marker) {
            MARKER_EOI -> break
            in 0xD0..0xD7 -> return null
            MARKER_TEM -> continue
            MARKER_SOS -> {
                if (offset + 2 > data.size) return null
                val length = readU16BE(data, offset)
                val payloadStart = offset + 2
                val payloadEnd = offset + length
                if (length < 2 || payloadEnd > data.size) return null
                val currentCoding = coding ?: return null
                val current = parseSos(
                    data,
                    payloadStart,
                    payloadEnd,
                    frameComponents,
                    currentCoding == JpegCoding.kProgressive,
                ) ?: return null
                scan = current
                scanCount++
                offset = payloadEnd
                val scanStart = offset
                while (offset + 1 < data.size) {
                    if (data[offset] == 0xFF.toByte()) {
                        val markerStart = offset
                        var markerOffset = offset + 1
                        while (markerOffset < data.size && data[markerOffset] == 0xFF.toByte()) markerOffset++
                        if (markerOffset >= data.size) return null
                        val next = data[markerOffset].toInt() and 0xFF
                        if (next in 0xD0..0xD7 && restartInterval == 0) return null
                        if (next != 0x00 && next !in 0xD0..0xD7) {
                            scanData = data.copyOfRange(scanStart, offset)
                            scans += EntropyScan(current, scanData)
                            if (next == MARKER_EOI) {
                                return buildParsed(
                                    width,
                                    height,
                                    quantTables,
                                    dcTables,
                                    acTables,
                                    if (currentCoding == JpegCoding.kProgressive) frameComponents else current.components,
                                    scanData,
                                    restartInterval,
                                    parseIccProfile(iccChunks, iccChunkCount),
                                    origin,
                                    currentCoding,
                                    current.spectralStart,
                                    current.spectralEnd,
                                    current.successiveApprox,
                                    scanCount,
                                    if (currentCoding == JpegCoding.kProgressive) scans.toList() else emptyList(),
                                    adobeTransform,
                                )
                            }
                            if (currentCoding == JpegCoding.kBaseline) return null
                            offset = markerStart
                            break
                        }
                        offset = markerOffset + 1
                    } else {
                        offset++
                    }
                }
                if (offset + 1 >= data.size) return null
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
                    MARKER_APP1 -> parseExifOrigin(data, payloadStart, payloadEnd)?.let { origin = it }
                    MARKER_APP14 -> parseAdobeTransform(data, payloadStart, payloadEnd)?.let { adobeTransform = it }
                    MARKER_APP2 -> parseIccChunk(data, payloadStart, payloadEnd)?.let { chunk ->
                        val (index, count, payload) = chunk
                        if (iccChunkCount == -1) {
                            iccChunkCount = count
                        }
                        if (count == iccChunkCount) {
                            iccChunks += index to payload
                        }
                    }
                    MARKER_SOF0, MARKER_SOF2 -> {
                        if (coding != null) return null
                        val frame = parseSof(data, payloadStart, payloadEnd) ?: return null
                        width = frame.width
                        height = frame.height
                        frameComponents = frame.components
                        coding = if (marker == MARKER_SOF0) JpegCoding.kBaseline else JpegCoding.kProgressive
                    }
                    MARKER_DRI -> {
                        if (payloadEnd - payloadStart != 2) return null
                        restartInterval = readU16BE(data, payloadStart)
                    }
                }
                offset = payloadEnd
            }
        }
    }
    val lastScan = scan
    return if (lastScan != null && coding != null) {
        buildParsed(
            width,
            height,
            quantTables,
            dcTables,
            acTables,
            if (coding == JpegCoding.kProgressive) frameComponents else lastScan.components,
            scanData,
            restartInterval,
            parseIccProfile(iccChunks, iccChunkCount),
            origin,
            coding,
            lastScan.spectralStart,
            lastScan.spectralEnd,
            lastScan.successiveApprox,
            scanCount,
            if (coding == JpegCoding.kProgressive) scans.toList() else emptyList(),
            adobeTransform,
        )
    } else {
        null
    }
}

private fun buildParsed(
    width: Int,
    height: Int,
    quantTables: Array<IntArray?>,
    dcTables: Array<HuffmanTable?>,
    acTables: Array<HuffmanTable?>,
    components: List<Component>?,
    scanData: ByteArray,
    restartInterval: Int,
    iccProfile: SkcmsICCProfile?,
    origin: SkEncodedOrigin,
    coding: JpegCoding,
    scanSpectralStart: Int,
    scanSpectralEnd: Int,
    scanSuccessiveApprox: Int,
    scanCount: Int,
    scans: List<EntropyScan>,
    adobeTransform: Int?,
): ParsedJpeg? {
    val cs = components ?: return null
    if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
    if (cs.size !in listOf(1, 3, 4)) return null
    if (coding == JpegCoding.kProgressive && cs.size == 4) return null
    if (coding == JpegCoding.kProgressive) {
        for (component in cs) {
            if (component.h !in 1..4 || component.v !in 1..4) return null
            if (component.quantTable !in quantTables.indices || quantTables[component.quantTable] == null) return null
        }
        if (scanData.isEmpty()) return null
        return ParsedJpeg(
            width,
            height,
            quantTables,
            dcTables,
            acTables,
            cs,
            scanData,
            restartInterval,
            iccProfile,
            origin,
            coding,
            scanSpectralStart,
            scanSpectralEnd,
            scanSuccessiveApprox,
            scanCount,
            scans,
            adobeTransform,
        )
    }
    for (component in cs) {
        if (component.h !in 1..2 || component.v !in 1..2) return null
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
    when (cs.size) {
        1 -> {
            if (cs.single().h != 1 || cs.single().v != 1) return null
        }
        3 -> {
            val y = cs.firstOrNull { it.frameIndex == 0 } ?: return null
            val cb = cs.firstOrNull { it.frameIndex == 1 } ?: return null
            val cr = cs.firstOrNull { it.frameIndex == 2 } ?: return null
            if (cb.h != 1 || cb.v != 1 || cr.h != 1 || cr.v != 1) return null
            if ((y.h != 1 && y.h != 2) || (y.v != 1 && y.v != 2)) return null
            if (y.h == 1 && y.v != 1) return null
        }
        4 -> {
            when (adobeTransform) {
                0 -> {
                    if (cs.any { it.h != 1 || it.v != 1 }) return null
                }
                2 -> {
                    val y = cs.firstOrNull { it.frameIndex == 0 } ?: return null
                    val cb = cs.firstOrNull { it.frameIndex == 1 } ?: return null
                    val cr = cs.firstOrNull { it.frameIndex == 2 } ?: return null
                    val k = cs.firstOrNull { it.frameIndex == 3 } ?: return null
                    if (cb.h != 1 || cb.v != 1 || cr.h != 1 || cr.v != 1 || k.h != 1 || k.v != 1) return null
                    if ((y.h != 1 && y.h != 2) || (y.v != 1 && y.v != 2)) return null
                    if (y.h == 1 && y.v != 1) return null
                }
                else -> return null
            }
        }
    }
    if (scanData.isEmpty()) return null
    return ParsedJpeg(
        width,
        height,
        quantTables,
        dcTables,
        acTables,
        cs,
        scanData,
        restartInterval,
        iccProfile,
        origin,
        coding,
        scanSpectralStart,
        scanSpectralEnd,
        scanSuccessiveApprox,
        scanCount,
        scans,
        adobeTransform,
    )
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

private fun parseSof(data: ByteArray, start: Int, end: Int): Frame? {
    if (end - start < 8) return null
    var p = start
    val precision = data[p++].toInt() and 0xFF
    val height = readU16BE(data, p).also { p += 2 }
    val width = readU16BE(data, p).also { p += 2 }
    val componentCount = data[p++].toInt() and 0xFF
    if (precision != 8 || componentCount !in listOf(1, 3, 4) || end - p != componentCount * 3) return null
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

private fun parseSos(
    data: ByteArray,
    start: Int,
    end: Int,
    frameComponents: List<Component>?,
    progressive: Boolean,
): Scan? {
    val frame = frameComponents ?: return null
    if (end - start < 6) return null
    var p = start
    val componentCount = data[p++].toInt() and 0xFF
    if (end - start != 4 + componentCount * 2) return null
    if (progressive) {
        if (componentCount !in 1..frame.size) return null
    } else if (componentCount != frame.size) {
        return null
    }
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
    if (progressive) {
        val successiveHigh = approx ushr 4
        val successiveLow = approx and 0x0F
        if (spectralStart !in 0..63 || spectralEnd !in spectralStart..63) return null
        if (successiveHigh > 13 || successiveLow > 13) return null
        if (spectralStart == 0 && spectralEnd != 0) return null
        if (spectralStart != 0 && componentCount != 1) return null
    } else if (spectralStart != 0 || spectralEnd != 63 || approx != 0) {
        return null
    }
    return Scan(components, spectralStart, spectralEnd, approx)
}

private fun decodeBaseline(jpeg: ParsedJpeg): IntArray {
    return when (jpeg.components.size) {
        1 -> decodeGrayscale(jpeg)
        3 -> decodeColor(jpeg)
        4 -> if (jpeg.adobeTransform == 2) decodeYcck(jpeg) else decodeCmyk(jpeg)
        else -> fail()
    }
}

private fun decodeProgressive(jpeg: ParsedJpeg): IntArray? {
    if (jpeg.components.size != 1) return null
    if (jpeg.scans.isEmpty()) return null
    if (jpeg.scans.size == 1) {
        val scan = jpeg.scans.single().scan
        if (scan.spectralStart != 0 || scan.spectralEnd != 0 || scan.successiveApprox != 0) return null
    }
    return decodeProgressiveGrayscaleInitial(jpeg)
}

private fun decodeProgressiveGrayscaleInitial(jpeg: ParsedJpeg): IntArray? {
    val component = jpeg.components.single()
    if (component.h != 1 || component.v != 1) fail()
    val quant = jpeg.quantTables[component.quantTable] ?: fail()
    val blocksX = (jpeg.width + 7) / 8
    val blocksY = (jpeg.height + 7) / 8
    val totalMcus = blocksX * blocksY
    val blockCoeffs = Array(totalMcus) { IntArray(64) }
    var sawDc = false
    var dcApproxLow = -1

    for (entropyScan in jpeg.scans) {
        val scan = entropyScan.scan
        if (scan.components.size != 1 || scan.components.single().id != component.id) return null
        val successiveHigh = scan.successiveApprox ushr 4
        val successiveLow = scan.successiveApprox and 0x0F
        if (scan.spectralStart == 0) {
            if (scan.spectralEnd != 0) return null
            if (successiveHigh == 0) {
                if (sawDc) return null
                decodeProgressiveGrayscaleDcScan(jpeg, entropyScan, blockCoeffs, quant, successiveLow)
                sawDc = true
                dcApproxLow = successiveLow
            } else {
                if (!sawDc || successiveHigh != dcApproxLow || successiveLow != successiveHigh - 1) return null
                decodeProgressiveGrayscaleDcRefinementScan(jpeg, entropyScan, blockCoeffs, quant, successiveLow)
                dcApproxLow = successiveLow
            }
        } else {
            if (!sawDc) return null
            if (successiveHigh == 0) {
                decodeProgressiveGrayscaleAcScan(jpeg, entropyScan, blockCoeffs, quant, successiveLow)
            } else {
                if (successiveLow != successiveHigh - 1) return null
                decodeProgressiveGrayscaleAcRefinementScan(jpeg, entropyScan, blockCoeffs, quant, successiveLow)
            }
        }
    }

    if (!sawDc) return null
    val pixels = IntArray(jpeg.width * jpeg.height)
    for (by in 0 until blocksY) {
        for (bx in 0 until blocksX) {
            val block = idct(blockCoeffs[by * blocksX + bx])
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

private fun decodeProgressiveGrayscaleDcScan(
    jpeg: ParsedJpeg,
    entropyScan: EntropyScan,
    blockCoeffs: Array<IntArray>,
    quant: IntArray,
    successiveLow: Int,
) {
    val component = entropyScan.scan.components.single()
    val dcTable = jpeg.dcTables[component.dcTable] ?: fail()
    val reader = EntropyBitReader(entropyScan.data)
    var previousDc = 0
    var nextRestartMarker = 0
    for (blockIndex in blockCoeffs.indices) {
        val dcCategory = dcTable.decode(reader)
        if (dcCategory !in 0..11) fail()
        previousDc += receiveAndExtend(reader, dcCategory)
        blockCoeffs[blockIndex][0] = previousDc * (1 shl successiveLow) * quant[0]
        if (jpeg.restartInterval > 0 && (blockIndex + 1) % jpeg.restartInterval == 0 && blockIndex + 1 < blockCoeffs.size) {
            reader.consumeRestart(nextRestartMarker)
            nextRestartMarker = (nextRestartMarker + 1) and 7
            previousDc = 0
        }
    }
}

private fun decodeProgressiveGrayscaleDcRefinementScan(
    jpeg: ParsedJpeg,
    entropyScan: EntropyScan,
    blockCoeffs: Array<IntArray>,
    quant: IntArray,
    successiveLow: Int,
) {
    val reader = EntropyBitReader(entropyScan.data)
    var nextRestartMarker = 0
    val refinement = (1 shl successiveLow) * quant[0]
    for (blockIndex in blockCoeffs.indices) {
        if (reader.readBit() != 0) {
            blockCoeffs[blockIndex][0] += refinement
        }
        if (jpeg.restartInterval > 0 && (blockIndex + 1) % jpeg.restartInterval == 0 && blockIndex + 1 < blockCoeffs.size) {
            reader.consumeRestart(nextRestartMarker)
            nextRestartMarker = (nextRestartMarker + 1) and 7
        }
    }
}

private fun decodeProgressiveGrayscaleAcScan(
    jpeg: ParsedJpeg,
    entropyScan: EntropyScan,
    blockCoeffs: Array<IntArray>,
    quant: IntArray,
    successiveLow: Int,
) {
    val scan = entropyScan.scan
    val component = scan.components.single()
    val acTable = jpeg.acTables[component.acTable] ?: fail()
    val reader = EntropyBitReader(entropyScan.data)
    var nextRestartMarker = 0
    val scale = 1 shl successiveLow
    var eobRun = 0
    for (blockIndex in blockCoeffs.indices) {
        if (eobRun > 0) {
            eobRun--
            if (jpeg.restartInterval > 0 && (blockIndex + 1) % jpeg.restartInterval == 0 && blockIndex + 1 < blockCoeffs.size) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                eobRun = 0
            }
            continue
        }
        var k = scan.spectralStart
        while (k <= scan.spectralEnd) {
            val rs = acTable.decode(reader)
            if (rs == 0x00) break
            val run = rs ushr 4
            val size = rs and 0x0F
            if (size == 0) {
                if (run == 15) {
                    k += 16
                    continue
                }
                eobRun = (1 shl run) + reader.readBits(run)
                eobRun--
                break
            }
            k += run
            if (k > scan.spectralEnd) fail()
            val coefficientIndex = ZIGZAG[k]
            blockCoeffs[blockIndex][coefficientIndex] =
                receiveAndExtend(reader, size) * scale * quant[coefficientIndex]
            k++
        }
        if (jpeg.restartInterval > 0 && (blockIndex + 1) % jpeg.restartInterval == 0 && blockIndex + 1 < blockCoeffs.size) {
            reader.consumeRestart(nextRestartMarker)
            nextRestartMarker = (nextRestartMarker + 1) and 7
            eobRun = 0
        }
    }
}

private fun decodeProgressiveGrayscaleAcRefinementScan(
    jpeg: ParsedJpeg,
    entropyScan: EntropyScan,
    blockCoeffs: Array<IntArray>,
    quant: IntArray,
    successiveLow: Int,
) {
    val scan = entropyScan.scan
    val component = scan.components.single()
    val acTable = jpeg.acTables[component.acTable] ?: fail()
    val reader = EntropyBitReader(entropyScan.data)
    var nextRestartMarker = 0
    val refinement = 1 shl successiveLow
    var eobRun = 0
    for (blockIndex in blockCoeffs.indices) {
        var k = scan.spectralStart
        if (eobRun > 0) {
            refineExistingAcCoefficients(blockCoeffs[blockIndex], quant, reader, refinement, k, scan.spectralEnd)
            eobRun--
        } else {
            while (k <= scan.spectralEnd) {
                val coefficientIndex = ZIGZAG[k]
                if (blockCoeffs[blockIndex][coefficientIndex] != 0) {
                    refineExistingAcCoefficient(blockCoeffs[blockIndex], quant, reader, refinement, coefficientIndex)
                    k++
                    continue
                }

                val rs = acTable.decode(reader)
                val run = rs ushr 4
                val size = rs and 0x0F
                if (size == 0) {
                    if (run == 15) {
                        k = skipZeroAcCoefficientsForRefinement(
                            blockCoeffs[blockIndex],
                            quant,
                            reader,
                            refinement,
                            k,
                            scan.spectralEnd,
                            zeroCount = 16,
                        )
                        continue
                    }
                    eobRun = (1 shl run) + reader.readBits(run)
                    refineExistingAcCoefficients(blockCoeffs[blockIndex], quant, reader, refinement, k, scan.spectralEnd)
                    eobRun--
                    break
                }
                if (size != 1) fail()
                k = skipZeroAcCoefficientsForRefinement(
                    blockCoeffs[blockIndex],
                    quant,
                    reader,
                    refinement,
                    k,
                    scan.spectralEnd,
                    zeroCount = run,
                )
                if (k > scan.spectralEnd) fail()
                val newCoefficientIndex = ZIGZAG[k]
                if (blockCoeffs[blockIndex][newCoefficientIndex] != 0) fail()
                val signBit = reader.readBit()
                blockCoeffs[blockIndex][newCoefficientIndex] =
                    if (signBit == 0) -refinement * quant[newCoefficientIndex] else refinement * quant[newCoefficientIndex]
                k++
            }
        }
        if (jpeg.restartInterval > 0 && (blockIndex + 1) % jpeg.restartInterval == 0 && blockIndex + 1 < blockCoeffs.size) {
            reader.consumeRestart(nextRestartMarker)
            nextRestartMarker = (nextRestartMarker + 1) and 7
            eobRun = 0
        }
    }
}

private fun skipZeroAcCoefficientsForRefinement(
    blockCoeffs: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    start: Int,
    end: Int,
    zeroCount: Int,
): Int {
    var k = start
    var remainingZeroes = zeroCount
    while (k <= end) {
        val coefficientIndex = ZIGZAG[k]
        if (blockCoeffs[coefficientIndex] != 0) {
            refineExistingAcCoefficient(blockCoeffs, quant, reader, refinement, coefficientIndex)
        } else {
            if (remainingZeroes == 0) return k
            remainingZeroes--
        }
        k++
    }
    if (remainingZeroes == 0) return k
    fail()
}

private fun refineExistingAcCoefficients(
    blockCoeffs: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    start: Int,
    end: Int,
) {
    for (k in start..end) {
        val coefficientIndex = ZIGZAG[k]
        if (blockCoeffs[coefficientIndex] != 0) {
            refineExistingAcCoefficient(blockCoeffs, quant, reader, refinement, coefficientIndex)
        }
    }
}

private fun refineExistingAcCoefficient(
    blockCoeffs: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    coefficientIndex: Int,
) {
    val coefficient = blockCoeffs[coefficientIndex]
    if (coefficient == 0 || reader.readBit() == 0) return
    blockCoeffs[coefficientIndex] +=
        if (coefficient > 0) {
            refinement * quant[coefficientIndex]
        } else {
            -refinement * quant[coefficientIndex]
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
    val totalMcus = blocksX * blocksY
    var mcu = 0
    var nextRestartMarker = 0

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
            mcu++
            if (jpeg.restartInterval > 0 && mcu % jpeg.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc = 0
            }
        }
    }
    return pixels
}

private fun decodeColor(jpeg: ParsedJpeg): IntArray {
    val components = jpeg.components
    val reader = EntropyBitReader(jpeg.scanData)
    val pixels = IntArray(jpeg.width * jpeg.height)
    val previousDc = IntArray(components.size)
    val maxH = components.maxOf { it.h }
    val maxV = components.maxOf { it.v }
    val mcuWidth = maxH * 8
    val mcuHeight = maxV * 8
    val frameComponents = Array(components.size) { frameIndex ->
        components.first { it.frameIndex == frameIndex }
    }
    val blocks = Array(frameComponents.size) { Array(frameComponents[it].h * frameComponents[it].v) { IntArray(64) } }
    val blocksX = (jpeg.width + mcuWidth - 1) / mcuWidth
    val blocksY = (jpeg.height + mcuHeight - 1) / mcuHeight
    val totalMcus = blocksX * blocksY
    var mcu = 0
    var nextRestartMarker = 0

    for (my in 0 until blocksY) {
        for (mx in 0 until blocksX) {
            for (scanIndex in components.indices) {
                val component = components[scanIndex]
                for (blockY in 0 until component.v) {
                    for (blockX in 0 until component.h) {
                        val blockIndex = blockY * component.h + blockX
                        blocks[component.frameIndex][blockIndex] =
                            decodeBlock(jpeg, component, reader, previousDc, component.frameIndex)
                    }
                }
            }

            for (y in 0 until mcuHeight) {
                val py = my * mcuHeight + y
                if (py >= jpeg.height) continue
                for (x in 0 until mcuWidth) {
                    val px = mx * mcuWidth + x
                    if (px >= jpeg.width) continue
                    val yy = sampleComponent(blocks[0], frameComponents[0], x, y, mcuWidth, mcuHeight)
                    val cb = sampleComponent(blocks[1], frameComponents[1], x, y, mcuWidth, mcuHeight)
                    val cr = sampleComponent(blocks[2], frameComponents[2], x, y, mcuWidth, mcuHeight)
                    pixels[py * jpeg.width + px] = yCbCrToArgb(yy, cb, cr)
                }
            }
            mcu++
            if (jpeg.restartInterval > 0 && mcu % jpeg.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    return pixels
}

private fun decodeCmyk(jpeg: ParsedJpeg): IntArray {
    val components = jpeg.components
    val reader = EntropyBitReader(jpeg.scanData)
    val pixels = IntArray(jpeg.width * jpeg.height)
    val previousDc = IntArray(components.size)
    val blocks = Array(components.size) { IntArray(64) }
    val blocksX = (jpeg.width + 7) / 8
    val blocksY = (jpeg.height + 7) / 8
    val totalMcus = blocksX * blocksY
    var mcu = 0
    var nextRestartMarker = 0

    for (my in 0 until blocksY) {
        for (mx in 0 until blocksX) {
            for (component in components) {
                blocks[component.frameIndex] = decodeBlock(jpeg, component, reader, previousDc, component.frameIndex)
            }

            for (y in 0 until 8) {
                val py = my * 8 + y
                if (py >= jpeg.height) continue
                for (x in 0 until 8) {
                    val px = mx * 8 + x
                    if (px >= jpeg.width) continue
                    val index = y * 8 + x
                    val c = (blocks[0][index] + 128).coerceIn(0, 255)
                    val m = (blocks[1][index] + 128).coerceIn(0, 255)
                    val yy = (blocks[2][index] + 128).coerceIn(0, 255)
                    val k = (blocks[3][index] + 128).coerceIn(0, 255)
                    pixels[py * jpeg.width + px] = invertedCmykToArgb(c, m, yy, k)
                }
            }

            mcu++
            if (jpeg.restartInterval > 0 && mcu % jpeg.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    return pixels
}

private fun decodeYcck(jpeg: ParsedJpeg): IntArray {
    val components = jpeg.components
    val reader = EntropyBitReader(jpeg.scanData)
    val pixels = IntArray(jpeg.width * jpeg.height)
    val previousDc = IntArray(components.size)
    val maxH = components.maxOf { it.h }
    val maxV = components.maxOf { it.v }
    val mcuWidth = maxH * 8
    val mcuHeight = maxV * 8
    val frameComponents = Array(components.size) { frameIndex ->
        components.first { it.frameIndex == frameIndex }
    }
    val blocks = Array(frameComponents.size) { Array(frameComponents[it].h * frameComponents[it].v) { IntArray(64) } }
    val blocksX = (jpeg.width + mcuWidth - 1) / mcuWidth
    val blocksY = (jpeg.height + mcuHeight - 1) / mcuHeight
    val totalMcus = blocksX * blocksY
    var mcu = 0
    var nextRestartMarker = 0

    for (my in 0 until blocksY) {
        for (mx in 0 until blocksX) {
            for (component in components) {
                for (blockY in 0 until component.v) {
                    for (blockX in 0 until component.h) {
                        val blockIndex = blockY * component.h + blockX
                        blocks[component.frameIndex][blockIndex] =
                            decodeBlock(jpeg, component, reader, previousDc, component.frameIndex)
                    }
                }
            }

            for (y in 0 until mcuHeight) {
                val py = my * mcuHeight + y
                if (py >= jpeg.height) continue
                for (x in 0 until mcuWidth) {
                    val px = mx * mcuWidth + x
                    if (px >= jpeg.width) continue
                    val yy = sampleComponent(blocks[0], frameComponents[0], x, y, mcuWidth, mcuHeight)
                    val cb = sampleComponent(blocks[1], frameComponents[1], x, y, mcuWidth, mcuHeight)
                    val cr = sampleComponent(blocks[2], frameComponents[2], x, y, mcuWidth, mcuHeight)
                    val k = sampleComponent(blocks[3], frameComponents[3], x, y, mcuWidth, mcuHeight)
                    pixels[py * jpeg.width + px] = ycckToArgb(yy, cb, cr, k)
                }
            }

            mcu++
            if (jpeg.restartInterval > 0 && mcu % jpeg.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    return pixels
}

private fun sampleComponent(
    blocks: Array<IntArray>,
    component: Component,
    mcuX: Int,
    mcuY: Int,
    mcuWidth: Int,
    mcuHeight: Int,
): Int {
    val componentWidth = component.h * 8
    val componentHeight = component.v * 8
    if (componentWidth != mcuWidth || componentHeight != mcuHeight) {
        val sourceX = ((mcuX + 0.5) * componentWidth / mcuWidth) - 0.5
        val sourceY = ((mcuY + 0.5) * componentHeight / mcuHeight) - 0.5
        val x0 = floor(sourceX).toInt().coerceIn(0, componentWidth - 1)
        val y0 = floor(sourceY).toInt().coerceIn(0, componentHeight - 1)
        val x1 = (x0 + 1).coerceAtMost(componentWidth - 1)
        val y1 = (y0 + 1).coerceAtMost(componentHeight - 1)
        val fx = (sourceX - x0).coerceIn(0.0, 1.0)
        val fy = (sourceY - y0).coerceIn(0.0, 1.0)
        val top = sampleComponentAt(blocks, component, x0, y0) * (1.0 - fx) +
            sampleComponentAt(blocks, component, x1, y0) * fx
        val bottom = sampleComponentAt(blocks, component, x0, y1) * (1.0 - fx) +
            sampleComponentAt(blocks, component, x1, y1) * fx
        return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, 255)
    }
    return sampleComponentAt(blocks, component, mcuX, mcuY)
}

private fun sampleComponentAt(
    blocks: Array<IntArray>,
    component: Component,
    componentX: Int,
    componentY: Int,
): Int {
    val blockX = componentX / 8
    val blockY = componentY / 8
    val block = blocks[blockY * component.h + blockX]
    return (block[(componentY and 7) * 8 + (componentX and 7)] + 128).coerceIn(0, 255)
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

    fun consumeRestart(expected: Int) {
        remaining = 0
        if (offset >= bytes.size) fail()
        if (bytes[offset] != 0xFF.toByte()) fail()
        while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) offset++
        if (offset >= bytes.size) fail()
        val marker = bytes[offset++].toInt() and 0xFF
        if (marker != 0xD0 + expected) fail()
    }
}

private data class IccChunk(val index: Int, val count: Int, val payload: ByteArray)

private fun parseIccChunk(data: ByteArray, start: Int, end: Int): IccChunk? {
    if (end - start < ICC_SIGNATURE.size + 2 || !matchesAt(data, start, ICC_SIGNATURE)) return null
    val index = data[start + ICC_SIGNATURE.size].toInt() and 0xFF
    val count = data[start + ICC_SIGNATURE.size + 1].toInt() and 0xFF
    if (index == 0 || count == 0 || index > count) return null
    return IccChunk(index, count, data.copyOfRange(start + ICC_SIGNATURE.size + 2, end))
}

private fun parseIccProfile(chunks: List<Pair<Int, ByteArray>>, expectedCount: Int): SkcmsICCProfile? {
    if (chunks.isEmpty() || expectedCount <= 0 || chunks.size != expectedCount) return null
    val sorted = chunks.sortedBy { it.first }
    for ((i, chunk) in sorted.withIndex()) {
        if (chunk.first != i + 1) return null
    }
    val bytes = ByteArray(sorted.sumOf { it.second.size })
    var offset = 0
    for ((_, chunk) in sorted) {
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return try {
        skcmsParse(bytes)
    } catch (_: Throwable) {
        null
    }
}

private fun parseExifOrigin(data: ByteArray, start: Int, end: Int): SkEncodedOrigin? {
    if (end - start < EXIF_SIGNATURE.size + 8 || !matchesAt(data, start, EXIF_SIGNATURE)) return null
    return parseExifTiffForOrigin(data, start + EXIF_SIGNATURE.size, end)
}

private fun parseExifTiffForOrigin(data: ByteArray, tiffStart: Int, end: Int): SkEncodedOrigin? {
    if (tiffStart + 8 > end) return null
    val littleEndian = when {
        data[tiffStart] == 0x49.toByte() && data[tiffStart + 1] == 0x49.toByte() -> true
        data[tiffStart] == 0x4D.toByte() && data[tiffStart + 1] == 0x4D.toByte() -> false
        else -> return null
    }
    if (readU16(data, tiffStart + 2, littleEndian) != 0x002A) return null
    val ifdOffset = readU32(data, tiffStart + 4, littleEndian)
    val ifdStart = tiffStart + ifdOffset
    if (ifdStart < tiffStart || ifdStart + 2 > end) return null
    val count = readU16(data, ifdStart, littleEndian)
    val entriesStart = ifdStart + 2
    if (count > (end - entriesStart) / 12) return null
    for (i in 0 until count) {
        val entry = entriesStart + i * 12
        if (readU16(data, entry, littleEndian) != EXIF_ORIENTATION_TAG) continue
        val type = readU16(data, entry + 2, littleEndian)
        val valueCount = readU32(data, entry + 4, littleEndian)
        if (type != TIFF_TYPE_SHORT || valueCount != 1) return null
        val raw = readU16(data, entry + 8, littleEndian)
        if (raw !in 1..8) return null
        return SkEncodedOrigin.fromExifValue(raw)
    }
    return null
}

private fun matchesAt(data: ByteArray, at: Int, signature: ByteArray): Boolean {
    if (at + signature.size > data.size) return false
    for (i in signature.indices) {
        if (data[at + i] != signature[i]) return false
    }
    return true
}

private fun readU16(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
}

private fun readU32(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    val b2 = bytes[offset + 2].toInt() and 0xFF
    val b3 = bytes[offset + 3].toInt() and 0xFF
    return if (littleEndian) {
        (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    } else {
        (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
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

private fun ycckToArgb(y: Int, cb: Int, cr: Int, k: Int): Int {
    val rgb = yCbCrToArgb(y, cb, cr)
    return argb(
        0xFF,
        (((rgb ushr 16) and 0xFF) * k + 127) / 255,
        (((rgb ushr 8) and 0xFF) * k + 127) / 255,
        ((rgb and 0xFF) * k + 127) / 255,
    )
}

private fun invertedCmykToArgb(c: Int, m: Int, y: Int, k: Int): Int =
    argb(
        0xFF,
        (c * k + 127) / 255,
        (m * k + 127) / 255,
        (y * k + 127) / 255,
    )

private fun parseAdobeTransform(data: ByteArray, start: Int, end: Int): Int? {
    if (end - start != 12 || !matchesAt(data, start, ADOBE_SIGNATURE)) return null
    val transform = data[start + 11].toInt() and 0xFF
    return if (transform in 0..2) transform else null
}

private fun fail(): Nothing = throw IllegalArgumentException("invalid JPEG")

private const val MARKER_SOF0 = 0xC0
private const val MARKER_SOF2 = 0xC2
private const val MARKER_DHT = 0xC4
private const val MARKER_SOS = 0xDA
private const val MARKER_DQT = 0xDB
private const val MARKER_DRI = 0xDD
private const val MARKER_APP1 = 0xE1
private const val MARKER_APP2 = 0xE2
private const val MARKER_APP14 = 0xEE
private const val MARKER_EOI = 0xD9
private const val MARKER_TEM = 0x01
private const val MAX_DIMENSION = 100_000
private const val EXIF_ORIENTATION_TAG = 0x0112
private const val TIFF_TYPE_SHORT = 3
private val INV_SQRT2 = 1.0 / sqrt(2.0)
private val ICC_SIGNATURE = byteArrayOf(
    0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00,
)
private val EXIF_SIGNATURE = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
private val ADOBE_SIGNATURE = byteArrayOf(0x41, 0x64, 0x6F, 0x62, 0x65)

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
