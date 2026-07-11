package org.graphiks.kanvas.codec.jpeg

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkICC
import org.skia.foundation.stream.SkWStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.roundToInt

public object JpegEncoder {

    public enum class AlphaOption {
        kIgnore,
        kBlendOnBlack,
    }

    public enum class Downsample {
        k420,
        k422,
        k444,
    }

    public data class Options @JvmOverloads constructor(
        val quality: Int = 100,
        @Deprecated("Use sampling") val downsample: Downsample = Downsample.k420,
        @Deprecated("Use alphaPolicy") val alphaOption: AlphaOption = AlphaOption.kIgnore,
        @Deprecated("Use metadata.exif") val orientation: SkEncodedOrigin = SkEncodedOrigin.kTopLeft,
        val process: JpegEncodeProcess = JpegEncodeProcess.SequentialHuffman,
        val precision: Int = 8,
        /** When absent, [downsample] remains the deprecated compatibility mapping. */
        val sampling: JpegSampling? = null,
        val restartInterval: Int = 0,
        val metadata: JpegEncodeMetadata = JpegEncodeMetadata(),
        /** When absent, [alphaOption] remains the deprecated compatibility mapping. */
        val alphaPolicy: JpegAlphaPolicy? = null,
        val hierarchy: List<JpegHierarchyLevel> = emptyList(),
    ) {
        init {
            require(quality in 0..100) { "quality must be in [0, 100], got $quality" }
            require(precision == 8 || precision == 12) { "precision must be 8 or 12, got $precision" }
            require(restartInterval in 0..0xFFFF) {
                "restart interval must be in [0, 65535], got $restartInterval"
            }
        }

        @Suppress("DEPRECATION")
        internal fun effectiveSampling(): JpegSampling = sampling ?: when (downsample) {
            Downsample.k420 -> JpegSampling.S420
            Downsample.k422 -> JpegSampling.S422
            Downsample.k444 -> JpegSampling.S444
        }

        @Suppress("DEPRECATION")
        internal fun effectiveAlphaPolicy(): JpegAlphaPolicy = alphaPolicy ?: when (alphaOption) {
            AlphaOption.kIgnore -> JpegAlphaPolicy.Ignore
            AlphaOption.kBlendOnBlack -> JpegAlphaPolicy.BlendOnBlack
        }

        internal fun isSequentialHuffmanRequest(): Boolean =
            process == JpegEncodeProcess.SequentialHuffman && hierarchy.isEmpty()
    }

    private val defaultOptions = Options()

    public fun encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (encode(baos, src, options)) baos.toByteArray() else null
    }

    public fun encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            if (src.width <= 0 || src.height <= 0 || !options.isSequentialHuffmanRequest()) return false
            JpegWriter(dst, src, options).write()
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

    private object encoderSupport {
        fun pixmapToBitmap(src: SkPixmap): SkBitmap? {
            if (src.width() <= 0 || src.height() <= 0) return null
            if (src.colorType() == SkColorType.kUnknown) return null
            val cs = src.colorSpace() ?: org.skia.foundation.SkColorSpace.makeSRGB()
            val bm = SkBitmap(src.width(), src.height(), cs, SkColorType.kRGBA_8888)
            for (y in 0 until src.height()) {
                for (x in 0 until src.width()) {
                    bm.setPixel(x, y, src.getColor(x, y))
                }
            }
            return bm
        }
    }
}

@Suppress("DEPRECATION")
private class JpegWriter(
    private val out: OutputStream,
    private val bitmap: SkBitmap,
    private val options: JpegEncoder.Options,
) {
    private val sampling = Sampling.from(options.effectiveSampling())
    private val qLuma = scaledQuantTable(STD_LUMA_Q, options.quality, options.precision)
    private val qChroma = scaledQuantTable(STD_CHROMA_Q, options.quality, options.precision)
    private val dcLumaBits = if (options.precision == 12) EXTENDED_HUFFMAN_BITS else STD_DC_LUMA_BITS
    private val dcLumaValues = if (options.precision == 12) EXTENDED_HUFFMAN_VALUES else STD_DC_VALUES
    private val acLumaBits = if (options.precision == 12) EXTENDED_HUFFMAN_BITS else STD_AC_LUMA_BITS
    private val acLumaValues = if (options.precision == 12) EXTENDED_HUFFMAN_VALUES else STD_AC_LUMA_VALUES
    private val dcChromaBits = if (options.precision == 12) EXTENDED_HUFFMAN_BITS else STD_DC_CHROMA_BITS
    private val dcChromaValues = if (options.precision == 12) EXTENDED_HUFFMAN_VALUES else STD_DC_VALUES
    private val acChromaBits = if (options.precision == 12) EXTENDED_HUFFMAN_BITS else STD_AC_CHROMA_BITS
    private val acChromaValues = if (options.precision == 12) EXTENDED_HUFFMAN_VALUES else STD_AC_CHROMA_VALUES
    private val dcLuma = EncoderHuffmanTable(dcLumaBits, dcLumaValues)
    private val acLuma = EncoderHuffmanTable(acLumaBits, acLumaValues)
    private val dcChroma = EncoderHuffmanTable(dcChromaBits, dcChromaValues)
    private val acChroma = EncoderHuffmanTable(acChromaBits, acChromaValues)
    private val rgb = IntArray(bitmap.width * bitmap.height)
    private val previousDc = IntArray(3)

    fun write() {
        materializeRgb()
        writeMarker(SOI)
        writeApp0()
        writeMetadata()
        writeDqt()
        writeSof()
        writeDht(0, 0, dcLumaBits, dcLumaValues)
        writeDht(1, 0, acLumaBits, acLumaValues)
        writeDht(0, 1, dcChromaBits, dcChromaValues)
        writeDht(1, 1, acChromaBits, acChromaValues)
        if (options.restartInterval > 0) writeDri()
        writeSos()

        val bits = EntropyBitWriter(out)
        val mcuWidth = sampling.maxH * 8
        val mcuHeight = sampling.maxV * 8
        val mcusX = ceilDiv(bitmap.width, mcuWidth)
        val mcusY = ceilDiv(bitmap.height, mcuHeight)
        var mcu = 0
        var restartMarker = 0
        for (my in 0 until mcusY) {
            for (mx in 0 until mcusX) {
                encodeMcu(bits, mx, my)
                mcu++
                if (
                    options.restartInterval > 0 &&
                    mcu % options.restartInterval == 0 &&
                    mcu < mcusX * mcusY
                ) {
                    bits.writeRestart(restartMarker)
                    restartMarker = (restartMarker + 1) and 7
                    previousDc.fill(0)
                }
            }
        }
        bits.flush()
        writeMarker(EOI)
    }

    private fun materializeRgb() {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val argb = bitmap.getPixel(x, y)
                val a = SkColorGetA(argb)
                val r = SkColorGetR(argb)
                val g = SkColorGetG(argb)
                val b = SkColorGetB(argb)
                val packed = when (options.effectiveAlphaPolicy()) {
                    JpegAlphaPolicy.Ignore -> packRgb(r, g, b)
                    JpegAlphaPolicy.BlendOnBlack -> {
                        packRgb(
                            (r * a + 127) / 255,
                            (g * a + 127) / 255,
                            (b * a + 127) / 255,
                        )
                    }
                }
                rgb[y * bitmap.width + x] = packed
            }
        }
    }

    private fun encodeMcu(bits: EntropyBitWriter, mcuX: Int, mcuY: Int) {
        for (component in 0 until 3) {
            val componentSampling = sampling.components[component]
            for (blockY in 0 until componentSampling.vertical) {
                for (blockX in 0 until componentSampling.horizontal) {
                    encodeBlock(bits, component, mcuX, mcuY, blockX, blockY)
                }
            }
        }
    }

    private fun encodeBlock(
        bits: EntropyBitWriter,
        component: Int,
        mcuX: Int,
        mcuY: Int,
        blockX: Int,
        blockY: Int,
    ) {
        val samples = DoubleArray(64)
        for (sy in 0 until 8) {
            for (sx in 0 until 8) {
                samples[sy * 8 + sx] = componentSample(
                    component,
                    mcuX * sampling.components[component].horizontal * 8 + blockX * 8 + sx,
                    mcuY * sampling.components[component].vertical * 8 + blockY * 8 + sy,
                )
            }
        }

        val quant = if (component == COMPONENT_Y) qLuma else qChroma
        val coeffs = fdctQuantize(samples, quant)
        val dcTable = if (component == COMPONENT_Y) dcLuma else dcChroma
        val acTable = if (component == COMPONENT_Y) acLuma else acChroma
        writeCoefficients(bits, coeffs, component, dcTable, acTable)
    }

    private fun componentSample(component: Int, sampleX: Int, sampleY: Int): Double {
        var sum = 0.0
        var count = 0
        val componentSampling = sampling.components[component]
        val startX = Math.floorDiv(sampleX * sampling.maxH, componentSampling.horizontal)
        val endX = ceilDiv((sampleX + 1) * sampling.maxH, componentSampling.horizontal)
        val startY = Math.floorDiv(sampleY * sampling.maxV, componentSampling.vertical)
        val endY = ceilDiv((sampleY + 1) * sampling.maxV, componentSampling.vertical)
        for (y in startY until endY) {
            for (x in startX until endX) {
                val px = rgb[clamp(y, 0, bitmap.height - 1) * bitmap.width + clamp(x, 0, bitmap.width - 1)]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val sample8 = when (component) {
                    COMPONENT_Y -> 0.299 * r + 0.587 * g + 0.114 * b
                    COMPONENT_CB -> -0.168736 * r - 0.331264 * g + 0.5 * b + 128.0
                    else -> 0.5 * r - 0.418688 * g - 0.081312 * b + 128.0
                }
                sum += sample8 * ((1 shl options.precision) - 1) / 255.0
                count++
            }
        }
        return sum / count
    }

    private fun fdctQuantize(samples: DoubleArray, quant: IntArray): IntArray {
        val out = IntArray(64)
        for (v in 0 until 8) {
            for (u in 0 until 8) {
                var sum = 0.0
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        sum += (samples[y * 8 + x] - (1 shl (options.precision - 1))) *
                            COS_TABLE[u * 8 + x] *
                            COS_TABLE[v * 8 + y]
                    }
                }
                val cu = if (u == 0) INV_SQRT2 else 1.0
                val cv = if (v == 0) INV_SQRT2 else 1.0
                val naturalIndex = v * 8 + u
                val zigZagIndex = ZIGZAG.indexOf(naturalIndex)
                out[zigZagIndex] = (0.25 * cu * cv * sum / quant[zigZagIndex]).roundToInt()
            }
        }
        return out
    }

    private fun writeCoefficients(
        bits: EntropyBitWriter,
        coeffs: IntArray,
        component: Int,
        dcTable: EncoderHuffmanTable,
        acTable: EncoderHuffmanTable,
    ) {
        val diff = coeffs[0] - previousDc[component]
        previousDc[component] = coeffs[0]
        val dcSize = coefficientSize(diff)
        bits.write(dcTable.code(dcSize), dcTable.length(dcSize))
        if (dcSize > 0) bits.write(amplitudeBits(diff, dcSize), dcSize)

        var zeroRun = 0
        for (i in 1 until 64) {
            val value = coeffs[i]
            if (value == 0) {
                zeroRun++
                continue
            }
            while (zeroRun >= 16) {
                bits.write(acTable.code(0xF0), acTable.length(0xF0))
                zeroRun -= 16
            }
            val size = coefficientSize(value)
            val symbol = (zeroRun shl 4) or size
            bits.write(acTable.code(symbol), acTable.length(symbol))
            bits.write(amplitudeBits(value, size), size)
            zeroRun = 0
        }
        if (zeroRun > 0) {
            bits.write(acTable.code(0x00), acTable.length(0x00))
        }
    }

    private fun writeApp0() {
        val payload = byteArrayOf(
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01,
            0x00,
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00,
        )
        writeSegment(APP0, payload)
    }

    private fun writeMetadata() {
        val requested = options.metadata
        val icc = requested.icc ?: if (!bitmap.colorSpace.isSRGB()) {
            SkICC.WriteToICC(bitmap.colorSpace.transferFn, bitmap.colorSpace.toXYZD50)
        } else {
            null
        }
        if (icc != null) writeIccApp2(icc)
        requested.adobeTransform?.let(::writeAdobeApp14)
        val exif = requested.exif
        when {
            exif != null -> writeExifApp1(exif)
            options.orientation != SkEncodedOrigin.kTopLeft -> writeExifApp1(orientationTiff(options.orientation.exifValue))
        }
        requested.xmp?.let(::writeXmpApp1)
        requested.comment?.let { writeSegment(COM, it) }
    }

    private fun writeDqt() {
        val sixteenBit = options.precision == 12
        val lumaSpec = if (sixteenBit) 0x10 else 0x00
        val chromaSpec = if (sixteenBit) 0x11 else 0x01
        writeSegment(DQT, byteArrayOf(lumaSpec.toByte()) + quantBytes(qLuma, sixteenBit) + byteArrayOf(chromaSpec.toByte()) + quantBytes(qChroma, sixteenBit))
    }

    private fun writeSof() {
        val payload = ByteArrayOutputStream()
        payload.write(options.precision)
        writeU16BE(payload, bitmap.height)
        writeU16BE(payload, bitmap.width)
        payload.write(3)
        for (component in 0 until 3) {
            val factor = sampling.components[component]
            payload.write(component + 1)
            payload.write((factor.horizontal shl 4) or factor.vertical)
            payload.write(if (component == COMPONENT_Y) 0 else 1)
        }
        writeSegment(if (options.precision == 8) SOF0 else SOF1, payload.toByteArray())
    }

    private fun writeDht(tableClass: Int, tableId: Int, bits: IntArray, values: IntArray) {
        val payload = ByteArrayOutputStream()
        payload.write((tableClass shl 4) or tableId)
        bits.forEach { payload.write(it) }
        values.forEach { payload.write(it) }
        writeSegment(DHT, payload.toByteArray())
    }

    private fun writeDri() {
        val payload = ByteArrayOutputStream()
        writeU16BE(payload, options.restartInterval)
        writeSegment(DRI, payload.toByteArray())
    }

    private fun writeSos() {
        val payload = byteArrayOf(
            0x03,
            0x01, 0x00,
            0x02, 0x11,
            0x03, 0x11,
            0x00, 0x3F, 0x00,
        )
        writeSegment(SOS, payload)
    }

    private fun writeSegment(marker: Int, payload: ByteArray) {
        writeMarker(marker)
        writeU16BE(out, payload.size + 2)
        out.write(payload)
    }

    private fun writeIccApp2(iccBytes: ByteArray) {
        val signature = byteArrayOf(0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00)
        val maxChunkPayload = JpegWriterLimits.MAX_ICC_CHUNK_BYTES
        val totalChunks = (iccBytes.size + maxChunkPayload - 1) / maxChunkPayload
        var offset = 0
        for (i in 1..totalChunks) {
            val chunkSize = minOf(maxChunkPayload, iccBytes.size - offset)
            val chunkPayload = ByteArray(chunkSize)
            iccBytes.copyInto(chunkPayload, 0, offset, offset + chunkSize)
            val segmentPayload = signature + byteArrayOf(i.toByte(), totalChunks.toByte()) + chunkPayload
            writeSegment(APP2, segmentPayload)
            offset += chunkSize
        }
    }

    private fun writeAdobeApp14(transform: Int) {
        val payload = byteArrayOf(
            'A'.code.toByte(), 'd'.code.toByte(), 'o'.code.toByte(), 'b'.code.toByte(), 'e'.code.toByte(),
            0x00, 0x64, 0x00, 0x00, 0x00, 0x00, transform.toByte(),
        )
        writeSegment(APP14, payload)
    }

    private fun writeXmpApp1(xmp: ByteArray) {
        writeSegment(APP1, XMP_IDENTIFIER + xmp)
    }

    private fun writeExifApp1(tiff: ByteArray) {
        writeSegment(APP1, EXIF_IDENTIFIER + tiff)
    }

    private fun orientationTiff(orientationValue: Int): ByteArray {
        val tiffPayload = ByteArrayOutputStream()
        tiffPayload.write('M'.code)
        tiffPayload.write('M'.code)
        writeU16BE(tiffPayload, 0x002A)
        writeU32BE(tiffPayload, 8)
        writeU16BE(tiffPayload, 1)
        writeU16BE(tiffPayload, 0x0112)
        writeU16BE(tiffPayload, 3)
        writeU32BE(tiffPayload, 1)
        writeU16BE(tiffPayload, orientationValue)
        writeU16BE(tiffPayload, 0)
        writeU32BE(tiffPayload, 0)
        return tiffPayload.toByteArray()
    }

    private fun writeU32BE(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeMarker(marker: Int) {
        out.write(0xFF)
        out.write(marker)
    }
}

private data class Sampling(
    val components: List<JpegSamplingFactor>,
) {
    val maxH: Int = components.maxOf(JpegSamplingFactor::horizontal)
    val maxV: Int = components.maxOf(JpegSamplingFactor::vertical)

    companion object {
        fun from(sampling: JpegSampling): Sampling = Sampling(sampling.components)
    }
}

private fun scaledQuantTable(base: IntArray, quality: Int, precision: Int): IntArray {
    val q = quality.coerceIn(1, 100)
    val scale = if (q < 50) 5000 / q else 200 - q * 2
    val sampleScale = if (precision == 12) 16 else 1
    return IntArray(64) { i ->
        (((base[ZIGZAG[i]] * scale + 50) / 100) * sampleScale).coerceIn(1, 65535)
    }
}

private fun quantBytes(table: IntArray, sixteenBit: Boolean): ByteArray =
    if (sixteenBit) ByteArray(128).also { bytes ->
        for (i in table.indices) {
            bytes[i * 2] = (table[i] ushr 8).toByte()
            bytes[i * 2 + 1] = table[i].toByte()
        }
    } else ByteArray(64) { i -> table[i].toByte() }

private fun coefficientSize(value: Int): Int {
    if (value == 0) return 0
    var abs = kotlin.math.abs(value)
    var size = 0
    while (abs > 0) {
        abs = abs ushr 1
        size++
    }
    return size
}

private fun amplitudeBits(value: Int, size: Int): Int =
    if (value >= 0) value else value + (1 shl size) - 1

private fun writeU16BE(out: OutputStream, value: Int) {
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

private fun packRgb(r: Int, g: Int, b: Int): Int =
    ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

private fun clamp(value: Int, min: Int, max: Int): Int =
    when {
        value < min -> min
        value > max -> max
        else -> value
    }

private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

private const val COMPONENT_Y = 0
private const val COMPONENT_CB = 1
private const val COMPONENT_CR = 2

private const val SOI = 0xD8
private const val EOI = 0xD9
private const val APP0 = 0xE0
private const val APP1 = 0xE1
private const val APP2 = 0xE2
private const val APP14 = 0xEE
private const val DQT = 0xDB
private const val SOF0 = 0xC0
private const val SOF1 = 0xC1
private const val DHT = 0xC4
private const val SOS = 0xDA
private const val DRI = 0xDD
private const val COM = 0xFE

private const val INV_SQRT2 = 0.7071067811865476

private val EXIF_IDENTIFIER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
private val XMP_IDENTIFIER = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()

private val COS_TABLE = DoubleArray(64) { index ->
    val u = index / 8
    val x = index % 8
    cos(((2 * x + 1) * u * Math.PI) / 16.0)
}

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

private val STD_LUMA_Q = intArrayOf(
    16, 11, 10, 16, 24, 40, 51, 61,
    12, 12, 14, 19, 26, 58, 60, 55,
    14, 13, 16, 24, 40, 57, 69, 56,
    14, 17, 22, 29, 51, 87, 80, 62,
    18, 22, 37, 56, 68, 109, 103, 77,
    24, 35, 55, 64, 81, 104, 113, 92,
    49, 64, 78, 87, 103, 121, 120, 101,
    72, 92, 95, 98, 112, 100, 103, 99,
)

private val STD_CHROMA_Q = intArrayOf(
    17, 18, 24, 47, 99, 99, 99, 99,
    18, 21, 26, 66, 99, 99, 99, 99,
    24, 26, 56, 99, 99, 99, 99, 99,
    47, 66, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
    99, 99, 99, 99, 99, 99, 99, 99,
)

private val STD_DC_LUMA_BITS = intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0)
private val STD_DC_CHROMA_BITS = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0)
private val STD_DC_VALUES = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

/**
 * A complete canonical table used only for 12-bit sequential output. The
 * baseline standard AC tables omit coefficient categories 11..14, whereas
 * extended JPEG permits them. Counts 255/1 avoid the one-byte DHT count
 * limit while retaining a valid prefix code for every byte-valued symbol.
 */
private val EXTENDED_HUFFMAN_BITS = IntArray(16).also { bits ->
    bits[7] = 255
    bits[8] = 1
}
private val EXTENDED_HUFFMAN_VALUES = IntArray(256) { it }

private val STD_AC_LUMA_BITS = intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7D)
private val STD_AC_LUMA_VALUES = intArrayOf(
    0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
    0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
    0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08,
    0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0,
    0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0A, 0x16,
    0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28,
    0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
    0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
    0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
    0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
    0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
    0x7A, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
    0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
    0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7,
    0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6,
    0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5,
    0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4,
    0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
    0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA,
    0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
    0xF9, 0xFA,
)

private val STD_AC_CHROMA_BITS = intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77)
private val STD_AC_CHROMA_VALUES = intArrayOf(
    0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
    0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
    0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
    0xA1, 0xB1, 0xC1, 0x09, 0x23, 0x33, 0x52, 0xF0,
    0x15, 0x62, 0x72, 0xD1, 0x0A, 0x16, 0x24, 0x34,
    0xE1, 0x25, 0xF1, 0x17, 0x18, 0x19, 0x1A, 0x26,
    0x27, 0x28, 0x29, 0x2A, 0x35, 0x36, 0x37, 0x38,
    0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
    0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
    0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
    0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
    0x79, 0x7A, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
    0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96,
    0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3, 0xA4, 0xA5,
    0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4,
    0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3,
    0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2,
    0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA,
    0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9,
    0xEA, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8,
    0xF9, 0xFA,
)
