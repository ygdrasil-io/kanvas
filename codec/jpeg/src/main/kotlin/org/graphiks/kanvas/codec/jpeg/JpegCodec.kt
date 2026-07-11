package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.utils.PixmapUtils
import kotlin.math.roundToInt

/**
 * First pure Kotlin JPEG decoder slice.
 *
 * This backend owns JPEG container parsing and pure Kotlin sequential Huffman
 * DCT decoding. Sequential SOF0/SOF1 frames support 8-bit and SOF1 12-bit
 * grayscale, YCbCr, RGB, CMYK, and YCCK component data.
 */
public class JpegCodec private constructor(
    private val jpeg: ParsedJpeg,
) : Codec() {

    private val cachedInfo: SkImageInfo by lazy {
        SkImageInfo.Make(
            width = if (jpeg.metadata.origin.swapsWidthHeight()) jpeg.height else jpeg.width,
            height = if (jpeg.metadata.origin.swapsWidthHeight()) jpeg.width else jpeg.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = jpeg.metadata.iccProfile?.let(SkColorSpace::makeProfileAware) ?: SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG

    override fun getICCProfile(): SkcmsICCProfile? = jpeg.metadata.iccProfile

    override fun getOrigin(): SkEncodedOrigin = jpeg.metadata.origin

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) return Result.kInvalidParameters
        if (dst.colorType != info.colorType) return Result.kInvalidParameters
        if (!canDecodeTo(info.colorType)) return Result.kInvalidConversion
        val pixels = try {
            if (jpeg.coding == JpegCoding.kProgressive) {
                DecodedPixels(decodeProgressive(jpeg) ?: return Result.kUnimplemented)
            } else {
                val scan = jpeg.scans.singleOrNull() ?: fail()
                val samples = decodeSequentialDct(jpeg, scan)
                DecodedPixels(
                    rgba8888 = composePixels(samples, jpeg.colorModel()),
                    rgbaF16 = if (info.colorType == SkColorType.kRGBA_F16Norm) {
                        composeF16Pixels(samples, jpeg.colorModel())
                    } else {
                        null
                    },
                )
            }
        } catch (_: IllegalArgumentException) {
            return Result.kErrorInInput
        }
        if (jpeg.metadata.origin == SkEncodedOrigin.kTopLeft) {
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
        if (!PixmapUtils.Orient(dst, raw, jpeg.metadata.origin)) return Result.kInvalidParameters
        return Result.kSuccess
    }

    private fun canDecodeTo(colorType: SkColorType): Boolean =
        colorType == SkColorType.kRGBA_8888 || colorType == SkColorType.kRGBA_F16Norm

    private fun writeDecodedPixels(dst: SkBitmap, pixels: DecodedPixels): Result {
        return when (dst.colorType) {
            SkColorType.kRGBA_8888 -> {
                System.arraycopy(pixels.rgba8888, 0, dst.pixels8888, 0, pixels.rgba8888.size)
                Result.kSuccess
            }
            SkColorType.kRGBA_F16Norm -> {
                for (y in 0 until dst.height) {
                    for (x in 0 until dst.width) {
                        val index = y * dst.width + x
                        val color = pixels.rgba8888[index]
                        val f16 = pixels.rgbaF16
                        val a = f16?.get(index * 4 + 3) ?: ((color ushr 24) and 0xFF) / 255f
                        val r = f16?.get(index * 4) ?: ((color ushr 16) and 0xFF) / 255f
                        val g = f16?.get(index * 4 + 1) ?: ((color ushr 8) and 0xFF) / 255f
                        val b = f16?.get(index * 4 + 2) ?: (color and 0xFF) / 255f
                        dst.setPixelF16(x, y, r * a, g * a, b * a, a)
                    }
                }
                Result.kSuccess
            }
            else -> Result.kInvalidConversion
        }
    }

    private data class DecodedPixels(
        val rgba8888: IntArray,
        val rgbaF16: FloatArray? = null,
    )

    internal companion object Decoder : Codec.Decoder {
        override val name: String = "jpeg"

        override fun matches(data: ByteArray): Boolean =
            data.size >= 3 &&
                data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()

        override fun make(data: ByteArray): Codec? {
            if (!matches(data)) return null
            val document = JpegDocument.open(data).document ?: return null
            return document.makeCodec()
        }

        internal fun decode(document: JpegDocument, request: JpegDecodeRequest): JpegDecodeResult =
            decode(document.makeCodec(), document.encodedSize, request)

        private fun decode(
            codec: JpegCodec?,
            encodedSize: Long,
            request: JpegDecodeRequest,
        ): JpegDecodeResult {
            codec ?: return JpegDecodeResult(
                bitmap = null,
                diagnostic = JpegDiagnostic(
                    code = "jpeg.decode.unsupported",
                    offset = encodedSize,
                    result = Codec.Result.kUnimplemented,
                ),
            )
            val sourceInfo = codec.getInfo()
            val info = SkImageInfo.Make(
                width = sourceInfo.width,
                height = sourceInfo.height,
                colorType = request.colorType,
                alphaType = sourceInfo.alphaType,
                colorSpace = request.colorSpace ?: sourceInfo.colorSpace,
            )
            val bitmap = SkBitmap(
                width = info.width,
                height = info.height,
                colorSpace = info.colorSpace,
                colorType = info.colorType,
            )
            val result = codec.getPixels(info, bitmap)
            return if (result == Codec.Result.kSuccess) {
                JpegDecodeResult(bitmap, null)
            } else {
                JpegDecodeResult(
                    bitmap = null,
                    diagnostic = JpegDiagnostic(
                        code = "jpeg.decode.${result.name}",
                        offset = encodedSize,
                        result = result,
                    ),
                )
            }
        }

        internal fun makeFromDocumentSource(data: ByteArray, metadata: JpegMetadata): JpegCodec? {
            val parsed = try {
                parseJpeg(data, metadata)
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
            return JpegCodec(parsed)
        }
    }
}

public class JpegKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(JpegCodec.Decoder)
}

internal data class ParsedJpeg(
    val width: Int,
    val height: Int,
    val precision: Int,
    val quantTables: Array<IntArray?>,
    val dcTables: Array<HuffmanTable?>,
    val acTables: Array<HuffmanTable?>,
    val components: List<Component>,
    val scanData: ByteArray,
    val restartInterval: Int,
    val metadata: JpegMetadata,
    val coding: JpegCoding,
    val scanSpectralStart: Int,
    val scanSpectralEnd: Int,
    val scanSuccessiveApprox: Int,
    val scanCount: Int,
    val scans: List<EntropyScan>,
)

internal data class Component(
    val id: Int,
    val h: Int,
    val v: Int,
    val quantTable: Int,
    val frameIndex: Int,
    val dcTable: Int,
    val acTable: Int,
)

internal data class Frame(
    val width: Int,
    val height: Int,
    val precision: Int,
    val components: List<Component>,
)

internal data class Scan(
    val components: List<Component>,
    val spectralStart: Int,
    val spectralEnd: Int,
    val successiveApprox: Int,
)

internal data class EntropyScan(
    val scan: Scan,
    val data: ByteArray,
)

internal enum class JpegCoding {
    kBaseline,
    kProgressive,
}

internal fun parseJpeg(data: ByteArray, metadata: JpegMetadata): ParsedJpeg? {
    if (data.size < 4 || data[0] != 0xFF.toByte() || data[1] != 0xD8.toByte()) return null
    val quantTables = arrayOfNulls<IntArray>(4)
    val dcTables = arrayOfNulls<HuffmanTable>(4)
    val acTables = arrayOfNulls<HuffmanTable>(4)
    var width = 0
    var height = 0
    var precision = 0
    var frameComponents: List<Component>? = null
    var scan: Scan? = null
    var scanData = ByteArray(0)
    var scanCount = 0
    val scans = ArrayList<EntropyScan>()
    var coding: JpegCoding? = null
    var restartInterval = 0
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
                                    precision,
                                    quantTables,
                                    dcTables,
                                    acTables,
                                    if (currentCoding == JpegCoding.kProgressive) frameComponents else current.components,
                                    scanData,
                                    restartInterval,
                                    metadata,
                                    currentCoding,
                                    current.spectralStart,
                                    current.spectralEnd,
                                    current.successiveApprox,
                                    scanCount,
                                    scans.toList(),
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
                    MARKER_SOF0, MARKER_SOF1, MARKER_SOF2 -> {
                        if (coding != null) return null
                        val frameSpec = JpegFrameSpec.fromSof(marker) ?: return null
                        if (frameSpec.entropyCoding != JpegEntropyCoding.HUFFMAN || frameSpec.differential) return null
                        val frame = parseSof(data, payloadStart, payloadEnd, frameSpec) ?: return null
                        width = frame.width
                        height = frame.height
                        precision = frame.precision
                        frameComponents = frame.components
                        coding = when (frameSpec.sampleCoding) {
                            JpegSampleCoding.DCT_SEQUENTIAL -> JpegCoding.kBaseline
                            JpegSampleCoding.DCT_PROGRESSIVE -> JpegCoding.kProgressive
                            JpegSampleCoding.LOSSLESS -> return null
                        }
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
            precision,
            quantTables,
            dcTables,
            acTables,
            if (coding == JpegCoding.kProgressive) frameComponents else lastScan.components,
            scanData,
            restartInterval,
            metadata,
            coding,
            lastScan.spectralStart,
            lastScan.spectralEnd,
            lastScan.successiveApprox,
            scanCount,
            scans.toList(),
        )
    } else {
        null
    }
}

private fun buildParsed(
    width: Int,
    height: Int,
    precision: Int,
    quantTables: Array<IntArray?>,
    dcTables: Array<HuffmanTable?>,
    acTables: Array<HuffmanTable?>,
    components: List<Component>?,
    scanData: ByteArray,
    restartInterval: Int,
    metadata: JpegMetadata,
    coding: JpegCoding,
    scanSpectralStart: Int,
    scanSpectralEnd: Int,
    scanSuccessiveApprox: Int,
    scanCount: Int,
    scans: List<EntropyScan>,
): ParsedJpeg? {
    val cs = components ?: return null
    if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || precision !in listOf(8, 12)) return null
    if (cs.size !in listOf(1, 3, 4)) return null
    if (coding == JpegCoding.kProgressive && cs.size == 4) return null
    if (coding == JpegCoding.kProgressive) {
        if (precision != 8) return null
        for (component in cs) {
            if (component.h !in 1..4 || component.v !in 1..4) return null
            if (component.quantTable !in quantTables.indices || quantTables[component.quantTable] == null) return null
        }
        if (scanData.isEmpty()) return null
        return ParsedJpeg(
            width,
            height,
            precision,
            quantTables,
            dcTables,
            acTables,
            cs,
            scanData,
            restartInterval,
            metadata,
            coding,
            scanSpectralStart,
            scanSpectralEnd,
            scanSuccessiveApprox,
            scanCount,
            scans,
        )
    }
    for (component in cs) {
        if (component.h !in 1..4 || component.v !in 1..4) return null
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
    if (colorModelFor(cs, metadata) == null) return null
    if (scanData.isEmpty()) return null
    return ParsedJpeg(
        width,
        height,
        precision,
        quantTables,
        dcTables,
        acTables,
        cs,
        scanData,
        restartInterval,
        metadata,
        coding,
        scanSpectralStart,
        scanSpectralEnd,
        scanSuccessiveApprox,
        scanCount,
        scans,
    )
}

private fun ParsedJpeg.colorModel(): JpegColorModel = colorModelFor(components, metadata) ?: fail()

private fun colorModelFor(components: List<Component>, metadata: JpegMetadata): JpegColorModel? = when (components.size) {
    1 -> JpegColorModel.GRAYSCALE
    3 -> if (metadata.adobeTransform == 0) JpegColorModel.RGB else JpegColorModel.YCBCR
    4 -> when (metadata.adobeTransform) {
        0 -> JpegColorModel.CMYK
        2 -> JpegColorModel.YCCK
        else -> null
    }
    else -> null
}

private fun parseDqt(data: ByteArray, start: Int, end: Int, tables: Array<IntArray?>) {
    var p = start
    while (p < end) {
        val spec = data[p++].toInt() and 0xFF
        val precision = spec ushr 4
        val tableId = spec and 0x0F
        val bytesPerValue = when (precision) {
            0 -> 1
            1 -> 2
            else -> fail()
        }
        if (tableId !in tables.indices || p + 64 * bytesPerValue > end) fail()
        val table = IntArray(64)
        for (i in 0 until 64) {
            table[JPEG_ZIGZAG[i]] = if (bytesPerValue == 1) {
                data[p++].toInt() and 0xFF
            } else {
                readU16BE(data, p).also { p += 2 }
            }
        }
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

private fun parseSof(data: ByteArray, start: Int, end: Int, spec: JpegFrameSpec): Frame? {
    if (end - start < 8) return null
    var p = start
    val precision = data[p++].toInt() and 0xFF
    val height = readU16BE(data, p).also { p += 2 }
    val width = readU16BE(data, p).also { p += 2 }
    val componentCount = data[p++].toInt() and 0xFF
    if (
        precision !in listOf(8, 12) ||
        (spec.marker == MARKER_SOF0 && precision != 8) ||
        componentCount !in listOf(1, 3, 4) ||
        end - p != componentCount * 3
    ) {
        return null
    }
    val components = ArrayList<Component>(componentCount)
    val ids = HashSet<Int>()
    for (index in 0 until componentCount) {
        val id = data[p++].toInt() and 0xFF
        val sampling = data[p++].toInt() and 0xFF
        val quant = data[p++].toInt() and 0xFF
        if (!ids.add(id)) return null
        components += Component(id, sampling ushr 4, sampling and 0x0F, quant, index, 0, 0)
    }
    return Frame(width, height, precision, components)
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
            val coefficientIndex = JPEG_ZIGZAG[k]
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
                val coefficientIndex = JPEG_ZIGZAG[k]
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
                val newCoefficientIndex = JPEG_ZIGZAG[k]
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
        val coefficientIndex = JPEG_ZIGZAG[k]
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
        val coefficientIndex = JPEG_ZIGZAG[k]
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

private fun readU16BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

internal fun fail(): Nothing = throw IllegalArgumentException("invalid JPEG")

private const val MARKER_SOF0 = 0xC0
private const val MARKER_SOF1 = 0xC1
private const val MARKER_SOF2 = 0xC2
private const val MARKER_DHT = 0xC4
private const val MARKER_SOS = 0xDA
private const val MARKER_DQT = 0xDB
private const val MARKER_DRI = 0xDD
private const val MARKER_EOI = 0xD9
private const val MARKER_TEM = 0x01
private const val MAX_DIMENSION = 100_000
