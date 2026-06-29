package org.graphiks.kanvas.codec.jpeg

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPixmap
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

    public data class Options(
        val quality: Int = 100,
        val downsample: Downsample = Downsample.k420,
        val alphaOption: AlphaOption = AlphaOption.kIgnore,
    ) {
        init {
            require(quality in 0..100) { "quality must be in [0, 100], got $quality" }
        }
    }

    private val defaultOptions = Options()

    init {
        org.skia.encode.JpegCall.setEncoder { bitmap, quality ->
            encode(bitmap, Options(quality = quality))
        }
    }

    public fun encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (encode(baos, src, options)) baos.toByteArray() else null
    }

    public fun encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            if (src.width <= 0 || src.height <= 0) return false
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

private class JpegWriter(
    private val out: OutputStream,
    private val bitmap: SkBitmap,
    private val options: JpegEncoder.Options,
) {
    private val sampling = Sampling.from(options.downsample)
    private val qLuma = scaledQuantTable(STD_LUMA_Q, options.quality)
    private val qChroma = scaledQuantTable(STD_CHROMA_Q, options.quality)
    private val dcLuma = EncoderHuffmanTable(STD_DC_LUMA_BITS, STD_DC_VALUES)
    private val acLuma = EncoderHuffmanTable(STD_AC_LUMA_BITS, STD_AC_LUMA_VALUES)
    private val dcChroma = EncoderHuffmanTable(STD_DC_CHROMA_BITS, STD_DC_VALUES)
    private val acChroma = EncoderHuffmanTable(STD_AC_CHROMA_BITS, STD_AC_CHROMA_VALUES)
    private val rgb = IntArray(bitmap.width * bitmap.height)
    private val previousDc = IntArray(3)

    fun write() {
        materializeRgb()
        writeMarker(SOI)
        writeApp0()
        writeDqt()
        writeSof0()
        writeDht(0, 0, STD_DC_LUMA_BITS, STD_DC_VALUES)
        writeDht(1, 0, STD_AC_LUMA_BITS, STD_AC_LUMA_VALUES)
        writeDht(0, 1, STD_DC_CHROMA_BITS, STD_DC_VALUES)
        writeDht(1, 1, STD_AC_CHROMA_BITS, STD_AC_CHROMA_VALUES)
        writeSos()

        val bits = EntropyWriter(out)
        val mcuWidth = sampling.maxH * 8
        val mcuHeight = sampling.maxV * 8
        val mcusX = ceilDiv(bitmap.width, mcuWidth)
        val mcusY = ceilDiv(bitmap.height, mcuHeight)
        for (my in 0 until mcusY) {
            for (mx in 0 until mcusX) {
                encodeMcu(bits, mx, my)
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
                val packed = when (options.alphaOption) {
                    JpegEncoder.AlphaOption.kIgnore -> packRgb(r, g, b)
                    JpegEncoder.AlphaOption.kBlendOnBlack -> {
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

    private fun encodeMcu(bits: EntropyWriter, mcuX: Int, mcuY: Int) {
        for (blockY in 0 until sampling.yV) {
            for (blockX in 0 until sampling.yH) {
                encodeBlock(bits, COMPONENT_Y, mcuX, mcuY, blockX, blockY)
            }
        }
        encodeBlock(bits, COMPONENT_CB, mcuX, mcuY, 0, 0)
        encodeBlock(bits, COMPONENT_CR, mcuX, mcuY, 0, 0)
    }

    private fun encodeBlock(
        bits: EntropyWriter,
        component: Int,
        mcuX: Int,
        mcuY: Int,
        blockX: Int,
        blockY: Int,
    ) {
        val samples = DoubleArray(64)
        val scaleX = if (component == COMPONENT_Y) 1 else sampling.maxH / sampling.cH
        val scaleY = if (component == COMPONENT_Y) 1 else sampling.maxV / sampling.cV
        val baseX = mcuX * sampling.maxH * 8 + blockX * 8 * scaleX
        val baseY = mcuY * sampling.maxV * 8 + blockY * 8 * scaleY

        for (sy in 0 until 8) {
            for (sx in 0 until 8) {
                samples[sy * 8 + sx] = componentSample(component, baseX + sx * scaleX, baseY + sy * scaleY, scaleX, scaleY)
            }
        }

        val quant = if (component == COMPONENT_Y) qLuma else qChroma
        val coeffs = fdctQuantize(samples, quant)
        val dcTable = if (component == COMPONENT_Y) dcLuma else dcChroma
        val acTable = if (component == COMPONENT_Y) acLuma else acChroma
        writeCoefficients(bits, coeffs, component, dcTable, acTable)
    }

    private fun componentSample(component: Int, x: Int, y: Int, scaleX: Int, scaleY: Int): Double {
        var sum = 0.0
        var count = 0
        for (dy in 0 until scaleY) {
            for (dx in 0 until scaleX) {
                val px = rgb[clamp(y + dy, 0, bitmap.height - 1) * bitmap.width + clamp(x + dx, 0, bitmap.width - 1)]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                sum += when (component) {
                    COMPONENT_Y -> 0.299 * r + 0.587 * g + 0.114 * b
                    COMPONENT_CB -> -0.168736 * r - 0.331264 * g + 0.5 * b + 128.0
                    else -> 0.5 * r - 0.418688 * g - 0.081312 * b + 128.0
                }
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
                        sum += (samples[y * 8 + x] - 128.0) *
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
        bits: EntropyWriter,
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

    private fun writeDqt() {
        writeSegment(DQT, byteArrayOf(0x00) + quantBytes(qLuma) + byteArrayOf(0x01) + quantBytes(qChroma))
    }

    private fun writeSof0() {
        val payload = ByteArrayOutputStream()
        payload.write(8)
        writeU16BE(payload, bitmap.height)
        writeU16BE(payload, bitmap.width)
        payload.write(3)
        payload.write(1)
        payload.write((sampling.yH shl 4) or sampling.yV)
        payload.write(0)
        payload.write(2)
        payload.write(0x11)
        payload.write(1)
        payload.write(3)
        payload.write(0x11)
        payload.write(1)
        writeSegment(SOF0, payload.toByteArray())
    }

    private fun writeDht(tableClass: Int, tableId: Int, bits: IntArray, values: IntArray) {
        val payload = ByteArrayOutputStream()
        payload.write((tableClass shl 4) or tableId)
        bits.forEach { payload.write(it) }
        values.forEach { payload.write(it) }
        writeSegment(DHT, payload.toByteArray())
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

    private fun writeMarker(marker: Int) {
        out.write(0xFF)
        out.write(marker)
    }
}

private data class Sampling(
    val yH: Int,
    val yV: Int,
) {
    val cH: Int = 1
    val cV: Int = 1
    val maxH: Int = yH
    val maxV: Int = yV

    companion object {
        fun from(downsample: JpegEncoder.Downsample): Sampling =
            when (downsample) {
                JpegEncoder.Downsample.k420 -> Sampling(yH = 2, yV = 2)
                JpegEncoder.Downsample.k422 -> Sampling(yH = 2, yV = 1)
                JpegEncoder.Downsample.k444 -> Sampling(yH = 1, yV = 1)
            }
    }
}

private class EntropyWriter(private val out: OutputStream) {
    private var buffer = 0
    private var bitCount = 0

    fun write(code: Int, length: Int) {
        for (i in length - 1 downTo 0) {
            buffer = (buffer shl 1) or ((code ushr i) and 1)
            bitCount++
            if (bitCount == 8) flushByte(buffer)
        }
    }

    fun flush() {
        if (bitCount > 0) {
            flushByte((buffer shl (8 - bitCount)) or ((1 shl (8 - bitCount)) - 1))
        }
    }

    private fun flushByte(value: Int) {
        val b = value and 0xFF
        out.write(b)
        if (b == 0xFF) out.write(0x00)
        buffer = 0
        bitCount = 0
    }
}

private class EncoderHuffmanTable(bits: IntArray, values: IntArray) {
    private val codes = IntArray(256)
    private val lengths = IntArray(256)

    init {
        var code = 0
        var valueIndex = 0
        for (length in 1..16) {
            repeat(bits[length - 1]) {
                val symbol = values[valueIndex++]
                codes[symbol] = code
                lengths[symbol] = length
                code++
            }
            code = code shl 1
        }
    }

    fun code(symbol: Int): Int = codes[symbol]

    fun length(symbol: Int): Int = lengths[symbol]
}

private fun scaledQuantTable(base: IntArray, quality: Int): IntArray {
    val q = quality.coerceIn(1, 100)
    val scale = if (q < 50) 5000 / q else 200 - q * 2
    return IntArray(64) { i ->
        ((base[ZIGZAG[i]] * scale + 50) / 100).coerceIn(1, 255)
    }
}

private fun quantBytes(table: IntArray): ByteArray =
    ByteArray(64) { i -> table[i].toByte() }

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
private const val DQT = 0xDB
private const val SOF0 = 0xC0
private const val DHT = 0xC4
private const val SOS = 0xDA

private const val INV_SQRT2 = 0.7071067811865476

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
