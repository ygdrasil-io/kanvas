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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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
        val colorModel: JpegEncodeColorModel = JpegEncodeColorModel.YCbCr,
        /** Required for a progressive Huffman or arithmetic process. */
        val progressiveScans: List<JpegProgressiveScan> = emptyList(),
        /** Required for [JpegEncodeProcess.LosslessHuffman]. */
        val losslessParameters: JpegLosslessParameters? = null,
    ) {
        init {
            require(quality in 0..100) { "quality must be in [0, 100], got $quality" }
            require(
                precision == 8 || precision == 12 ||
                    (precision == 16 && process == JpegEncodeProcess.LosslessHuffman),
            ) { "precision must be 8 or 12 (or 16 for lossless Huffman), got $precision" }
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

        internal fun isSupportedRequest(): Boolean =
            process in setOf(
                JpegEncodeProcess.SequentialHuffman,
                JpegEncodeProcess.SequentialArithmetic,
                JpegEncodeProcess.ProgressiveHuffman,
                JpegEncodeProcess.ProgressiveArithmetic,
                JpegEncodeProcess.LosslessHuffman,
            ) && hierarchy.isEmpty()
    }

    private val defaultOptions = Options()

    public fun encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (encode(baos, src, options)) baos.toByteArray() else null
    }

    public fun encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        return try {
            if (src.width !in 1..0xFFFF || src.height !in 1..0xFFFF) return false
            if (options.hierarchy.isEmpty()) {
                if (!options.isSupportedRequest()) return false
                JpegWriter(dst, src, options).write()
            } else {
                JpegHierarchyWriter(dst, src, options).write()
            }
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
            if (src.width() !in 1..0xFFFF || src.height() !in 1..0xFFFF) return null
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

/**
 * Emits the single hierarchy intersection for which both sides have local,
 * pixel-level evidence: a grayscale SOF0 reference at one half resolution and
 * a SOF5 Huffman residual after a two-axis EXP.  This is deliberately a
 * container writer, not a marker patcher: both SOF frames are independently
 * serialized by [JpegWriter].
 */
private class JpegHierarchyWriter(
    private val out: OutputStream,
    private val bitmap: SkBitmap,
    private val options: JpegEncoder.Options,
) {
    fun write() {
        validateRequest()
        val baseWidth = bitmap.width / 2
        val baseHeight = bitmap.height / 2
        val baseBitmap = SkBitmap(baseWidth, baseHeight, bitmap.colorSpace, SkColorType.kRGBA_8888)
        for (y in 0 until baseHeight) {
            for (x in 0 until baseWidth) {
                baseBitmap.setPixel(x, y, bitmap.getPixel(x * 2, y * 2))
            }
        }

        val baseOptions = options.copy(hierarchy = emptyList())
        val baseBytes = ByteArrayOutputStream().also { JpegWriter(it, baseBitmap, baseOptions).write() }.toByteArray()
        val baseDocument = requireNotNull(JpegDocument.open(baseBytes).document) { "hierarchy base document is invalid" }
        val baseFrame = requireNotNull(parseJpeg(baseBytes, baseDocument.metadata)) { "hierarchy base frame is invalid" }
        val baseSamples = decodeSequentialDct(baseFrame)
        require(baseSamples.planes.size == 1) { "hierarchy base must be grayscale" }
        val expandedReference = expandReference(baseSamples.planes.single(), baseWidth, baseHeight)
        val residual = IntArray(bitmap.width * bitmap.height) { index ->
            grayscaleSample(bitmap.getPixel(index % bitmap.width, index / bitmap.width)) - expandedReference[index]
        }

        val baseSofOffset = baseDocument.segments.firstOrNull { it.marker == SOF0 }?.offset?.toInt()
            ?: error("hierarchy base SOF is missing")
        require(baseSofOffset in 2 until baseBytes.size - 2) { "hierarchy base SOF offset is invalid" }
        require(baseBytes[0] == 0xFF.toByte() && (baseBytes[1].toInt() and 0xFF) == SOI) {
            "hierarchy base SOI is invalid"
        }
        require(baseBytes[baseBytes.size - 2] == 0xFF.toByte() && (baseBytes.last().toInt() and 0xFF) == EOI) {
            "hierarchy base EOI is invalid"
        }

        out.write(baseBytes, 0, baseSofOffset)
        writeDhp()
        out.write(baseBytes, baseSofOffset, baseBytes.size - baseSofOffset - 2)
        writeSegment(EXP, byteArrayOf(0x11))
        JpegWriter(out, bitmap, options, residual).writeDifferentialSequentialFrame()
        writeMarker(EOI)
    }

    private fun validateRequest() {
        val level = options.hierarchy.singleOrNull()
            ?: error("hierarchy encoding requires exactly one expansion level")
        require(options.process == JpegEncodeProcess.SequentialHuffman) {
            "hierarchy base frame must use sequential Huffman coding"
        }
        require(level.process == JpegEncodeProcess.DifferentialSequentialHuffman) {
            "hierarchy residual must use differential sequential Huffman coding"
        }
        require(level.scaleNumerator == 1 && level.scaleDenominator == 2) {
            "hierarchy supports exactly a one-half reference followed by EXP 0x11"
        }
        require(options.precision == 8) { "hierarchy supports only 8-bit precision" }
        require(options.colorModel == JpegEncodeColorModel.Grayscale) { "hierarchy supports only grayscale samples" }
        require(options.effectiveSampling().components.all { it.horizontal == 1 && it.vertical == 1 }) {
            "hierarchy grayscale output requires 1x1 sampling"
        }
        require(options.progressiveScans.isEmpty()) { "hierarchy does not accept a progressive scan script" }
        require(options.losslessParameters == null) { "hierarchy does not accept lossless parameters" }
        require(bitmap.width >= 2 && bitmap.height >= 2 && bitmap.width % 2 == 0 && bitmap.height % 2 == 0) {
            "hierarchy requires even dimensions of at least 2x2"
        }
    }

    private fun writeDhp() {
        val payload = ByteArrayOutputStream().apply {
            write(options.precision)
            writeU16BE(this, bitmap.height)
            writeU16BE(this, bitmap.width)
            write(1)
            write(1)
            write(0x11)
            write(0)
        }.toByteArray()
        writeSegment(DHP, payload)
    }

    /** Mirrors the decoder's required EXP horizontal-then-vertical interpolation exactly. */
    private fun expandReference(base: IntArray, baseWidth: Int, baseHeight: Int): IntArray {
        val horizontallyExpandedWidth = baseWidth * 2
        val horizontal = IntArray(horizontallyExpandedWidth * baseHeight)
        for (y in 0 until baseHeight) {
            for (x in 0 until baseWidth) {
                val value = base[y * baseWidth + x]
                horizontal[y * horizontallyExpandedWidth + x * 2] = value
                horizontal[y * horizontallyExpandedWidth + x * 2 + 1] =
                    (value + base[y * baseWidth + (x + 1).coerceAtMost(baseWidth - 1)]) shr 1
            }
        }
        val expanded = IntArray(horizontallyExpandedWidth * baseHeight * 2)
        for (y in 0 until baseHeight) {
            for (x in 0 until horizontallyExpandedWidth) {
                val value = horizontal[y * horizontallyExpandedWidth + x]
                expanded[y * 2 * horizontallyExpandedWidth + x] = value
                expanded[(y * 2 + 1) * horizontallyExpandedWidth + x] =
                    (value + horizontal[(y + 1).coerceAtMost(baseHeight - 1) * horizontallyExpandedWidth + x]) shr 1
            }
        }
        return expanded
    }

    private fun grayscaleSample(argb: Int): Int {
        var r = SkColorGetR(argb)
        var g = SkColorGetG(argb)
        var b = SkColorGetB(argb)
        if (options.effectiveAlphaPolicy() == JpegAlphaPolicy.BlendOnBlack) {
            val alpha = SkColorGetA(argb)
            r = (r * alpha + 127) / 255
            g = (g * alpha + 127) / 255
            b = (b * alpha + 127) / 255
        }
        return (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
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

@Suppress("DEPRECATION")
private class JpegWriter(
    private val out: OutputStream,
    private val bitmap: SkBitmap,
    private val options: JpegEncoder.Options,
    /** Signed target-minus-EXP(reference) samples for the one supported SOF5 writer path. */
    private val differentialResidual: IntArray? = null,
) {
    private val sampling = Sampling.from(options.effectiveSampling())
    private val componentCount = if (options.colorModel == JpegEncodeColorModel.Grayscale) 1 else 3
    private val componentSampling = sampling.components.take(componentCount)
    private val maxH = componentSampling.maxOf(JpegSamplingFactor::horizontal)
    private val maxV = componentSampling.maxOf(JpegSamplingFactor::vertical)
    private val isDct: Boolean = options.process != JpegEncodeProcess.LosslessHuffman
    private val qLuma = scaledQuantTable(STD_LUMA_Q, options.quality, options.precision)
    private val qChroma = scaledQuantTable(STD_CHROMA_Q, options.quality, options.precision)
    private val extendedHuffman = options.precision > 8
    private val dcLumaBits = if (extendedHuffman) EXTENDED_HUFFMAN_BITS else STD_DC_LUMA_BITS
    private val dcLumaValues = if (extendedHuffman) EXTENDED_HUFFMAN_VALUES else STD_DC_VALUES
    private val acLumaBits = if (extendedHuffman) EXTENDED_HUFFMAN_BITS else STD_AC_LUMA_BITS
    private val acLumaValues = if (extendedHuffman) EXTENDED_HUFFMAN_VALUES else STD_AC_LUMA_VALUES
    private val dcChromaBits = if (extendedHuffman) EXTENDED_HUFFMAN_BITS else STD_DC_CHROMA_BITS
    private val dcChromaValues = if (extendedHuffman) EXTENDED_HUFFMAN_VALUES else STD_DC_VALUES
    private val acChromaBits = if (extendedHuffman) EXTENDED_HUFFMAN_BITS else STD_AC_CHROMA_BITS
    private val acChromaValues = if (extendedHuffman) EXTENDED_HUFFMAN_VALUES else STD_AC_CHROMA_VALUES
    private val dcLuma = EncoderHuffmanTable(dcLumaBits, dcLumaValues)
    private val acLuma = EncoderHuffmanTable(acLumaBits, acLumaValues)
    private val dcChroma = EncoderHuffmanTable(dcChromaBits, dcChromaValues)
    private val acChroma = EncoderHuffmanTable(acChromaBits, acChromaValues)
    private val rgb = IntArray(bitmap.width * bitmap.height)
    private val previousDc = IntArray(componentCount)

    fun write() {
        validateRequest()
        materializeRgb()
        writeMarker(SOI)
        if (options.colorModel != JpegEncodeColorModel.Rgb) writeApp0()
        writeMetadata()
        if (isDct) writeDqt()
        writeSof()
        when (options.process) {
            JpegEncodeProcess.SequentialArithmetic,
            JpegEncodeProcess.ProgressiveArithmetic,
            -> writeArithmeticConditioning()
            else -> writeHuffmanTables()
        }
        if (options.restartInterval > 0) writeDri()
        when (options.process) {
            JpegEncodeProcess.SequentialHuffman -> writeSequentialEntropy()
            JpegEncodeProcess.SequentialArithmetic -> writeSequentialEntropy()
            JpegEncodeProcess.ProgressiveHuffman -> writeProgressiveEntropy()
            JpegEncodeProcess.ProgressiveArithmetic -> writeProgressiveEntropy()
            JpegEncodeProcess.LosslessHuffman -> writeLosslessEntropy()
            else -> error("unsupported JPEG process")
        }
        writeMarker(EOI)
    }

    private fun validateRequest() {
        when (options.process) {
            JpegEncodeProcess.SequentialHuffman -> {
                require(options.colorModel != JpegEncodeColorModel.Rgb) { "sequential RGB output is not implemented" }
                require(options.progressiveScans.isEmpty()) { "sequential output does not accept a progressive scan script" }
                require(options.losslessParameters == null) { "sequential output does not accept lossless parameters" }
            }
            JpegEncodeProcess.SequentialArithmetic -> {
                require(options.colorModel != JpegEncodeColorModel.Rgb) { "sequential RGB output is not implemented" }
                require(options.progressiveScans.isEmpty()) { "sequential output does not accept a progressive scan script" }
                require(options.losslessParameters == null) { "sequential output does not accept lossless parameters" }
            }
            JpegEncodeProcess.ProgressiveHuffman,
            JpegEncodeProcess.ProgressiveArithmetic,
            -> {
                require(options.colorModel != JpegEncodeColorModel.Rgb) { "progressive RGB output is not implemented" }
                require(options.losslessParameters == null) { "progressive output does not accept lossless parameters" }
                require(options.precision == 8 || options.precision == 12) { "progressive precision must be 8 or 12" }
                validatedProgressiveScans()
            }
            JpegEncodeProcess.LosslessHuffman -> {
                require(options.colorModel in setOf(JpegEncodeColorModel.Grayscale, JpegEncodeColorModel.Rgb)) {
                    "lossless output requires Grayscale or Rgb components"
                }
                require(options.progressiveScans.isEmpty()) { "lossless output does not accept a progressive scan script" }
                require(options.losslessParameters != null) { "lossless output requires predictor and point transform" }
                require(options.losslessParameters.pointTransform < options.precision) {
                    "lossless point transform must be less than precision"
                }
                require(componentSampling.all { it.horizontal == 1 && it.vertical == 1 }) {
                    "lossless RGB and grayscale output requires 1x1 sampling"
                }
            }
            else -> error("unsupported JPEG process")
        }
    }

    /** Emits a normal SOF0/SOF1 scan or the signed-residual SOF5 scan used by [JpegHierarchyWriter]. */
    private fun writeSequentialEntropy(differential: Boolean = false) {
        writeSos(componentIds(), spectralStart = 0, spectralEnd = 63, successiveApprox = 0)
        if (options.process == JpegEncodeProcess.SequentialArithmetic) {
            require(!differential) { "arithmetic differential encoding is not implemented" }
            writeArithmeticSequentialEntropy()
            return
        }
        val bits = EntropyBitWriter(out)
        val mcusX = ceilDiv(bitmap.width, maxH * 8)
        val mcusY = ceilDiv(bitmap.height, maxV * 8)
        var mcu = 0
        var restartMarker = 0
        for (my in 0 until mcusY) {
            for (mx in 0 until mcusX) {
                encodeMcu(bits, mx, my, differential)
                mcu++
                if (atRestartBoundary(mcu, mcusX * mcusY)) {
                    bits.writeRestart(restartMarker)
                    restartMarker = (restartMarker + 1) and 7
                    previousDc.fill(0)
                }
            }
        }
        bits.flush()
    }

    /** Serializes the differential SOF5 frame without duplicating container, metadata, or tables. */
    fun writeDifferentialSequentialFrame() {
        require(options.process == JpegEncodeProcess.SequentialHuffman) {
            "SOF5 is defined only for the hierarchy writer's Huffman base options"
        }
        require(options.precision == 8 && componentCount == 1 && differentialResidual != null) {
            "SOF5 writer requires signed 8-bit grayscale residuals"
        }
        writeSof(differentialSequential = true)
        writeSequentialEntropy(differential = true)
    }

    private fun writeArithmeticSequentialEntropy() {
        val arithmetic = ArithmeticEncoder(out, componentCount)
        val dcTables = IntArray(componentCount) { if (it == COMPONENT_Y) 0 else 1 }
        val acTables = IntArray(componentCount) { if (it == COMPONENT_Y) 0 else 1 }
        val mcusX = ceilDiv(bitmap.width, maxH * 8)
        val mcusY = ceilDiv(bitmap.height, maxV * 8)
        var mcu = 0
        for (mcuY in 0 until mcusY) {
            for (mcuX in 0 until mcusX) {
                for (component in 0 until componentCount) {
                    val factor = componentSampling[component]
                    for (blockY in 0 until factor.vertical) {
                        for (blockX in 0 until factor.horizontal) {
                            arithmetic.encodeSequentialBlock(
                                coefficients = dctBlockCoefficients(
                                    component,
                                    mcuX * factor.horizontal + blockX,
                                    mcuY * factor.vertical + blockY,
                                ),
                                component = component,
                                dcTable = dcTables[component],
                                acTable = acTables[component],
                                dcLower = ARITHMETIC_DC_LOWER,
                                dcUpper = ARITHMETIC_DC_UPPER,
                                acK = ARITHMETIC_AC_K,
                            )
                        }
                    }
                }
                mcu++
                if (atRestartBoundary(mcu, mcusX * mcusY)) {
                    arithmetic.writeRestart(dcTables, acTables)
                }
            }
        }
        arithmetic.finish()
    }

    private fun atRestartBoundary(mcu: Int, totalMcus: Int): Boolean =
        options.restartInterval > 0 && mcu % options.restartInterval == 0 && mcu < totalMcus

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

    private fun encodeMcu(bits: EntropyBitWriter, mcuX: Int, mcuY: Int, differential: Boolean) {
        for (component in 0 until componentCount) {
            val factor = componentSampling[component]
            for (blockY in 0 until factor.vertical) {
                for (blockX in 0 until factor.horizontal) {
                    encodeBlock(bits, component, mcuX, mcuY, blockX, blockY, differential)
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
        differential: Boolean,
    ) {
        val factor = componentSampling[component]
        val coeffs = dctBlockCoefficients(
            component = component,
            globalBlockX = mcuX * factor.horizontal + blockX,
            globalBlockY = mcuY * factor.vertical + blockY,
        )
        val dcTable = if (component == COMPONENT_Y) dcLuma else dcChroma
        val acTable = if (component == COMPONENT_Y) acLuma else acChroma
        writeCoefficients(bits, coeffs, component, dcTable, acTable, differential)
    }

    private fun dctBlockCoefficients(component: Int, globalBlockX: Int, globalBlockY: Int): IntArray {
        val samples = DoubleArray(64)
        for (sy in 0 until 8) {
            for (sx in 0 until 8) {
                samples[sy * 8 + sx] = componentSample(component, globalBlockX * 8 + sx, globalBlockY * 8 + sy)
            }
        }
        return fdctQuantize(
            samples,
            if (component == COMPONENT_Y) qLuma else qChroma,
            residual = differentialResidual != null,
        )
    }

    private fun componentSample(component: Int, sampleX: Int, sampleY: Int): Double {
        differentialResidual?.let { residual ->
            require(component == COMPONENT_Y && componentCount == 1) { "SOF5 residual layout is invalid" }
            val x = clamp(sampleX, 0, bitmap.width - 1)
            val y = clamp(sampleY, 0, bitmap.height - 1)
            return residual[y * bitmap.width + x].toDouble()
        }
        val factor = componentSampling[component]
        return areaAverage(
            left = sampleX.toDouble() * maxH / factor.horizontal,
            top = sampleY.toDouble() * maxV / factor.vertical,
            right = (sampleX + 1).toDouble() * maxH / factor.horizontal,
            bottom = (sampleY + 1).toDouble() * maxV / factor.vertical,
        ) { x, y ->
            val px = rgb[clamp(y, 0, bitmap.height - 1) * bitmap.width + clamp(x, 0, bitmap.width - 1)]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val sample8 = when (options.colorModel) {
                JpegEncodeColorModel.Grayscale -> 0.299 * r + 0.587 * g + 0.114 * b
                JpegEncodeColorModel.YCbCr -> when (component) {
                    COMPONENT_Y -> 0.299 * r + 0.587 * g + 0.114 * b
                    COMPONENT_CB -> -0.168736 * r - 0.331264 * g + 0.5 * b + 128.0
                    else -> 0.5 * r - 0.418688 * g - 0.081312 * b + 128.0
                }
                JpegEncodeColorModel.Rgb -> error("DCT RGB output is not implemented")
            }
            sample8 * ((1 shl options.precision) - 1) / 255.0
        }
    }

    private fun fdctQuantize(samples: DoubleArray, quant: IntArray, residual: Boolean = false): IntArray {
        val out = IntArray(64)
        for (v in 0 until 8) {
            for (u in 0 until 8) {
                var sum = 0.0
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        sum += (samples[y * 8 + x] - if (residual) 0 else (1 shl (options.precision - 1))) *
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
        differential: Boolean = false,
    ) {
        val diff = if (differential) coeffs[0] else coeffs[0] - previousDc[component]
        if (!differential) previousDc[component] = coeffs[0]
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
        if (options.process == JpegEncodeProcess.LosslessHuffman && options.colorModel == JpegEncodeColorModel.Rgb) {
            require(requested.adobeTransform == null) { "lossless RGB writes Adobe transform 0 itself" }
            writeAdobeApp14(0)
        } else {
            requested.adobeTransform?.let(::writeAdobeApp14)
        }
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
        val payload = ByteArrayOutputStream().apply {
            write(lumaSpec)
            write(quantBytes(qLuma, sixteenBit))
            if (componentCount > 1) {
                write(chromaSpec)
                write(quantBytes(qChroma, sixteenBit))
            }
        }.toByteArray()
        writeSegment(DQT, payload)
    }

    private fun writeSof(differentialSequential: Boolean = false) {
        val payload = ByteArrayOutputStream()
        payload.write(options.precision)
        writeU16BE(payload, bitmap.height)
        writeU16BE(payload, bitmap.width)
        payload.write(componentCount)
        for (component in 0 until componentCount) {
            val factor = componentSampling[component]
            payload.write(component + 1)
            payload.write((factor.horizontal shl 4) or factor.vertical)
            payload.write(if (options.process == JpegEncodeProcess.LosslessHuffman || component == COMPONENT_Y) 0 else 1)
        }
        val marker = if (differentialSequential) {
            SOF5
        } else when (options.process) {
            JpegEncodeProcess.SequentialHuffman -> if (options.precision == 8) SOF0 else SOF1
            JpegEncodeProcess.SequentialArithmetic -> SOF9
            JpegEncodeProcess.ProgressiveHuffman -> SOF2
            JpegEncodeProcess.ProgressiveArithmetic -> SOF10
            JpegEncodeProcess.LosslessHuffman -> SOF3
            else -> error("unsupported JPEG process")
        }
        writeSegment(marker, payload.toByteArray())
    }

    private fun writeHuffmanTables() {
        writeDht(0, 0, dcLumaBits, dcLumaValues)
        if (isDct) writeDht(1, 0, acLumaBits, acLumaValues)
        if (componentCount > 1) {
            writeDht(0, 1, dcChromaBits, dcChromaValues)
            if (isDct) writeDht(1, 1, acChromaBits, acChromaValues)
        }
    }

    /**
     * Emit the default Annex F arithmetic conditioning explicitly.  The DAC
     * marker makes the selected table ids self-describing and avoids relying
     * on a decoder's implicit defaults.
     */
    private fun writeArithmeticConditioning() {
        val tables = if (componentCount == 1) intArrayOf(0) else intArrayOf(0, 1)
        val payload = ByteArrayOutputStream()
        for (table in tables) {
            payload.write(table)
            payload.write((ARITHMETIC_DC_UPPER shl 4) or ARITHMETIC_DC_LOWER)
        }
        for (table in tables) {
            payload.write(0x10 or table)
            payload.write(ARITHMETIC_AC_K)
        }
        writeSegment(DAC, payload.toByteArray())
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

    private fun writeSos(componentIds: List<Int>, spectralStart: Int, spectralEnd: Int, successiveApprox: Int) {
        val payload = ByteArrayOutputStream()
        payload.write(componentIds.size)
        for (id in componentIds) {
            payload.write(id)
            val table = if (id == 1) 0 else 1
            payload.write(if (options.process == JpegEncodeProcess.LosslessHuffman) table shl 4 else (table shl 4) or table)
        }
        payload.write(spectralStart)
        payload.write(spectralEnd)
        payload.write(successiveApprox)
        writeSegment(SOS, payload.toByteArray())
    }

    private fun componentIds(): List<Int> = (1..componentCount).toList()

    private fun validatedProgressiveScans(): List<JpegProgressiveScan> {
        val scans = options.progressiveScans
        require(scans.isNotEmpty()) { "progressive output requires an explicit scan script" }
        val seen = Array(componentCount) { BooleanArray(64) }
        val dcSeen = BooleanArray(componentCount)
        for (scan in scans) {
            require(scan.successiveHigh == 0 && scan.successiveLow == 0) {
                "progressive refinement scans are not implemented"
            }
            require(scan.componentIds.all { it in 1..componentCount }) { "progressive scan references an unknown component" }
            if (scan.spectralStart == 0) {
                require(scan.spectralEnd == 0) { "a progressive DC scan must use Se = 0" }
            } else {
                require(scan.componentIds.size == 1) { "a progressive AC scan must contain exactly one component" }
                require(dcSeen[scan.componentIds.single() - 1]) { "a progressive AC scan must follow its DC scan" }
            }
            for (id in scan.componentIds) {
                for (coefficient in scan.spectralStart..scan.spectralEnd) {
                    require(!seen[id - 1][coefficient]) { "progressive scan script writes a coefficient twice" }
                    seen[id - 1][coefficient] = true
                }
                if (scan.spectralStart == 0) dcSeen[id - 1] = true
            }
        }
        require(seen.all { it[0] }) { "progressive scan script must initialize DC for every component" }
        return scans
    }

    private data class EncodedDctPlane(
        val blocksWide: Int,
        val blocksHigh: Int,
        val coefficients: Array<IntArray>,
    ) {
        fun block(x: Int, y: Int): IntArray = coefficients[y * blocksWide + x]
    }

    private fun buildDctPlanes(): List<EncodedDctPlane> {
        val frameMcusWide = ceilDiv(bitmap.width, maxH * 8)
        val frameMcusHigh = ceilDiv(bitmap.height, maxV * 8)
        return List(componentCount) { component ->
            val factor = componentSampling[component]
            val blocksWide = frameMcusWide * factor.horizontal
            val blocksHigh = frameMcusHigh * factor.vertical
            EncodedDctPlane(
                blocksWide = blocksWide,
                blocksHigh = blocksHigh,
                coefficients = Array(blocksWide * blocksHigh) { index ->
                    dctBlockCoefficients(component, index % blocksWide, index / blocksWide)
                },
            )
        }
    }

    private fun writeProgressiveEntropy() {
        val planes = buildDctPlanes()
        if (options.process == JpegEncodeProcess.ProgressiveArithmetic) {
            writeArithmeticProgressiveEntropy(planes)
            return
        }
        for (scan in validatedProgressiveScans()) {
            writeSos(scan.componentIds, scan.spectralStart, scan.spectralEnd, 0)
            val bits = EntropyBitWriter(out)
            if (scan.spectralStart == 0) {
                writeProgressiveDcScan(bits, scan, planes)
            } else {
                writeProgressiveAcScan(bits, scan, planes)
            }
            bits.flush()
        }
    }

    /** Writes only progressive initial scans (`Ah = Al = 0`) using Annex D's QM coder. */
    private fun writeArithmeticProgressiveEntropy(planes: List<EncodedDctPlane>) {
        for (scan in validatedProgressiveScans()) {
            writeSos(scan.componentIds, scan.spectralStart, scan.spectralEnd, successiveApprox = 0)
            val arithmetic = ArithmeticEncoder(out, componentCount)
            if (scan.spectralStart == 0) {
                writeArithmeticProgressiveDcScan(arithmetic, scan, planes)
            } else {
                writeArithmeticProgressiveAcScan(arithmetic, scan, planes)
            }
            arithmetic.finish()
        }
    }

    private fun writeArithmeticProgressiveDcScan(
        arithmetic: ArithmeticEncoder,
        scan: JpegProgressiveScan,
        planes: List<EncodedDctPlane>,
    ) {
        val components = scan.componentIds.map { it - 1 }
        val nonInterleaved = components.size == 1
        val frameMcusWide = ceilDiv(bitmap.width, maxH * 8)
        val frameMcusHigh = ceilDiv(bitmap.height, maxV * 8)
        val mcusWide = if (nonInterleaved) planes[components.single()].blocksWide else frameMcusWide
        val mcusHigh = if (nonInterleaved) planes[components.single()].blocksHigh else frameMcusHigh
        val dcTables = components.map { if (it == COMPONENT_Y) 0 else 1 }.toIntArray()
        var mcu = 0
        for (mcuY in 0 until mcusHigh) {
            for (mcuX in 0 until mcusWide) {
                for (component in components) {
                    val factor = componentSampling[component]
                    val baseX = if (nonInterleaved) mcuX else mcuX * factor.horizontal
                    val baseY = if (nonInterleaved) mcuY else mcuY * factor.vertical
                    val blocksWide = if (nonInterleaved) 1 else factor.horizontal
                    val blocksHigh = if (nonInterleaved) 1 else factor.vertical
                    val table = if (component == COMPONENT_Y) 0 else 1
                    for (blockY in 0 until blocksHigh) {
                        for (blockX in 0 until blocksWide) {
                            arithmetic.encodeProgressiveDcInitial(
                                component = component,
                                dcTable = table,
                                coefficient = planes[component].block(baseX + blockX, baseY + blockY)[0],
                                dcLower = ARITHMETIC_DC_LOWER,
                                dcUpper = ARITHMETIC_DC_UPPER,
                            )
                        }
                    }
                }
                mcu++
                if (atRestartBoundary(mcu, mcusWide * mcusHigh)) {
                    arithmetic.writeRestart(dcTables, intArrayOf())
                }
            }
        }
    }

    private fun writeArithmeticProgressiveAcScan(
        arithmetic: ArithmeticEncoder,
        scan: JpegProgressiveScan,
        planes: List<EncodedDctPlane>,
    ) {
        val component = scan.componentIds.single() - 1
        val plane = planes[component]
        val table = if (component == COMPONENT_Y) 0 else 1
        var mcu = 0
        for (blockY in 0 until plane.blocksHigh) {
            for (blockX in 0 until plane.blocksWide) {
                arithmetic.encodeProgressiveAcInitial(
                    acTable = table,
                    coefficients = plane.block(blockX, blockY),
                    startCoefficient = scan.spectralStart,
                    endCoefficient = scan.spectralEnd,
                    conditioningK = ARITHMETIC_AC_K,
                )
                mcu++
                if (atRestartBoundary(mcu, plane.blocksWide * plane.blocksHigh)) {
                    arithmetic.writeRestart(intArrayOf(), intArrayOf(table))
                }
            }
        }
    }

    private fun writeProgressiveDcScan(
        bits: EntropyBitWriter,
        scan: JpegProgressiveScan,
        planes: List<EncodedDctPlane>,
    ) {
        val components = scan.componentIds.map { it - 1 }
        val nonInterleaved = components.size == 1
        val frameMcusWide = ceilDiv(bitmap.width, maxH * 8)
        val frameMcusHigh = ceilDiv(bitmap.height, maxV * 8)
        val mcusWide = if (nonInterleaved) planes[components.single()].blocksWide else frameMcusWide
        val mcusHigh = if (nonInterleaved) planes[components.single()].blocksHigh else frameMcusHigh
        val predictors = IntArray(componentCount)
        var mcu = 0
        var restartMarker = 0
        for (mcuY in 0 until mcusHigh) {
            for (mcuX in 0 until mcusWide) {
                for (component in components) {
                    val factor = componentSampling[component]
                    val baseX = if (nonInterleaved) mcuX else mcuX * factor.horizontal
                    val baseY = if (nonInterleaved) mcuY else mcuY * factor.vertical
                    val blocksWide = if (nonInterleaved) 1 else factor.horizontal
                    val blocksHigh = if (nonInterleaved) 1 else factor.vertical
                    val table = if (component == COMPONENT_Y) dcLuma else dcChroma
                    for (blockY in 0 until blocksHigh) {
                        for (blockX in 0 until blocksWide) {
                            val coefficient = planes[component].block(baseX + blockX, baseY + blockY)[0]
                            val difference = coefficient - predictors[component]
                            predictors[component] = coefficient
                            writeHuffmanValue(bits, table, difference)
                        }
                    }
                }
                mcu++
                if (atRestartBoundary(mcu, mcusWide * mcusHigh)) {
                    bits.writeRestart(restartMarker)
                    restartMarker = (restartMarker + 1) and 7
                    predictors.fill(0)
                }
            }
        }
    }

    private fun writeProgressiveAcScan(
        bits: EntropyBitWriter,
        scan: JpegProgressiveScan,
        planes: List<EncodedDctPlane>,
    ) {
        val component = scan.componentIds.single() - 1
        val plane = planes[component]
        val table = if (component == COMPONENT_Y) acLuma else acChroma
        var mcu = 0
        var restartMarker = 0
        for (blockY in 0 until plane.blocksHigh) {
            for (blockX in 0 until plane.blocksWide) {
                writeInitialAcCoefficients(bits, plane.block(blockX, blockY), scan.spectralStart, scan.spectralEnd, table)
                mcu++
                if (atRestartBoundary(mcu, plane.blocksWide * plane.blocksHigh)) {
                    bits.writeRestart(restartMarker)
                    restartMarker = (restartMarker + 1) and 7
                }
            }
        }
    }

    private fun writeInitialAcCoefficients(
        bits: EntropyBitWriter,
        coefficients: IntArray,
        spectralStart: Int,
        spectralEnd: Int,
        table: EncoderHuffmanTable,
    ) {
        var zeroRun = 0
        for (coefficient in spectralStart..spectralEnd) {
            val value = coefficients[coefficient]
            if (value == 0) {
                zeroRun++
                continue
            }
            while (zeroRun >= 16) {
                bits.write(table.code(0xF0), table.length(0xF0))
                zeroRun -= 16
            }
            val size = coefficientSize(value)
            val symbol = (zeroRun shl 4) or size
            bits.write(table.code(symbol), table.length(symbol))
            bits.write(amplitudeBits(value, size), size)
            zeroRun = 0
        }
        if (zeroRun > 0) bits.write(table.code(0), table.length(0))
    }

    private fun writeLosslessEntropy() {
        val parameters = requireNotNull(options.losslessParameters)
        val planes = buildLosslessPlanes(parameters.pointTransform)
        writeSos(componentIds(), parameters.predictor, spectralEnd = 0, successiveApprox = parameters.pointTransform)
        val bits = EntropyBitWriter(out)
        var mcu = 0
        var restartMarker = 0
        val totalMcus = bitmap.width * bitmap.height
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val intervalStart = mcu == 0 || (options.restartInterval > 0 && mcu % options.restartInterval == 0)
                for (component in 0 until componentCount) {
                    val value = planes[component][y * bitmap.width + x]
                    val predicted = losslessPrediction(
                        plane = planes[component],
                        x = x,
                        y = y,
                        predictor = parameters.predictor,
                        pointTransform = parameters.pointTransform,
                        initialPredictor = intervalStart,
                    )
                    val difference = (value - predicted) shr parameters.pointTransform
                    writeHuffmanValue(bits, if (component == COMPONENT_Y) dcLuma else dcChroma, difference)
                }
                mcu++
                if (atRestartBoundary(mcu, totalMcus)) {
                    bits.writeRestart(restartMarker)
                    restartMarker = (restartMarker + 1) and 7
                }
            }
        }
        bits.flush()
    }

    private fun buildLosslessPlanes(pointTransform: Int): List<IntArray> {
        val maxSample = (1 shl options.precision) - 1
        return List(componentCount) { component ->
            IntArray(bitmap.width * bitmap.height) { index ->
                val packed = rgb[index]
                val sample8 = when (options.colorModel) {
                    JpegEncodeColorModel.Grayscale -> (
                        0.299 * ((packed ushr 16) and 0xFF) +
                            0.587 * ((packed ushr 8) and 0xFF) +
                            0.114 * (packed and 0xFF)
                        ).roundToInt()
                    JpegEncodeColorModel.Rgb -> when (component) {
                        0 -> (packed ushr 16) and 0xFF
                        1 -> (packed ushr 8) and 0xFF
                        else -> packed and 0xFF
                    }
                    JpegEncodeColorModel.YCbCr -> error("lossless YCbCr output is not implemented")
                }
                (((sample8 * maxSample + 127) / 255) ushr pointTransform) shl pointTransform
            }
        }
    }

    private fun losslessPrediction(
        plane: IntArray,
        x: Int,
        y: Int,
        predictor: Int,
        pointTransform: Int,
        initialPredictor: Boolean,
    ): Int = when {
        initialPredictor -> 1 shl (options.precision - 1)
        y == 0 -> plane[y * bitmap.width + x - 1]
        x == 0 -> plane[(y - 1) * bitmap.width + x]
        else -> losslessEncoderPredictor(
            predictor = predictor,
            left = plane[y * bitmap.width + x - 1] shr pointTransform,
            above = plane[(y - 1) * bitmap.width + x] shr pointTransform,
            upperLeft = plane[(y - 1) * bitmap.width + x - 1] shr pointTransform,
        ) shl pointTransform
    }

    private fun losslessEncoderPredictor(predictor: Int, left: Int, above: Int, upperLeft: Int): Int = when (predictor) {
        1 -> left
        2 -> above
        3 -> upperLeft
        4 -> left + above - upperLeft
        5 -> left + ((above - upperLeft) shr 1)
        6 -> above + ((left - upperLeft) shr 1)
        7 -> (left + above) shr 1
        else -> error("lossless predictor is invalid")
    }

    private fun writeHuffmanValue(bits: EntropyBitWriter, table: EncoderHuffmanTable, value: Int) {
        val size = coefficientSize(value)
        bits.write(table.code(size), table.length(size))
        if (size > 0) bits.write(amplitudeBits(value, size), size)
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

/** Area-weighted sample over a continuous source cell; the callback owns edge extension. */
internal fun areaAverage(
    left: Double,
    top: Double,
    right: Double,
    bottom: Double,
    sample: (x: Int, y: Int) -> Double,
): Double {
    require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite())
    require(right > left && bottom > top)
    var weightedSum = 0.0
    var totalWeight = 0.0
    for (y in floor(top).toInt() until ceil(bottom).toInt()) {
        val verticalWeight = (min(bottom, y + 1.0) - max(top, y.toDouble())).coerceAtLeast(0.0)
        if (verticalWeight == 0.0) continue
        for (x in floor(left).toInt() until ceil(right).toInt()) {
            val horizontalWeight = (min(right, x + 1.0) - max(left, x.toDouble())).coerceAtLeast(0.0)
            val weight = horizontalWeight * verticalWeight
            if (weight == 0.0) continue
            weightedSum += sample(x, y) * weight
            totalWeight += weight
        }
    }
    require(totalWeight > 0.0)
    return weightedSum / totalWeight
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
private const val SOF2 = 0xC2
private const val SOF3 = 0xC3
private const val SOF5 = 0xC5
private const val DHT = 0xC4
private const val SOF9 = 0xC9
private const val SOF10 = 0xCA
private const val DAC = 0xCC
private const val SOS = 0xDA
private const val DRI = 0xDD
private const val COM = 0xFE
private const val DHP = 0xDE
private const val EXP = 0xDF

private const val INV_SQRT2 = 0.7071067811865476
private const val ARITHMETIC_DC_LOWER = 0
private const val ARITHMETIC_DC_UPPER = 1
private const val ARITHMETIC_AC_K = 5

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
 * A complete canonical table for 12-bit DCT output. The baseline standard AC
 * tables omit coefficient categories 11..14, whereas extended JPEG permits
 * them. Counts 255/1 avoid the one-byte DHT count limit while retaining a
 * valid prefix code for every byte-valued symbol.
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
