package org.graphiks.kanvas.codec.jpeg

import kotlin.math.floor
import kotlin.math.roundToInt

/** Stable SOF3 failure surfaced by [JpegDocument.decode]. */
internal class LosslessJpegException(
    val diagnosticCode: String,
) : IllegalArgumentException(diagnosticCode)

internal fun losslessFailure(diagnosticCode: String): Nothing =
    throw LosslessJpegException(diagnosticCode)

/** Decodes all validated SOF3 Huffman scans into native component sample planes. */
internal fun decodeLossless(frame: ParsedJpeg): DecodedJpegSamples =
    decodeLossless(frame, differential = false)

/** Decodes a SOF7 residual with the mandated zero predictor and no output clamp. */
internal fun decodeDifferentialLossless(frame: ParsedJpeg): DecodedJpegSamples =
    decodeLossless(frame, differential = true)

/**
 * Decodes SOF15 directly with the JPEG QM arithmetic context state. SOF11
 * remains refused by [JpegCodec] and is deliberately not routed here.
 */
internal fun decodeDifferentialArithmeticLossless(frame: ParsedJpeg): DecodedJpegSamples {
    if (
        frame.coding != JpegCoding.kLossless ||
        frame.entropyCoding != JpegEntropyCoding.ARITHMETIC ||
        !frame.frameSpec.differential
    ) {
        arithmeticFailure("jpeg.arithmetic.differential-lossless.frame.invalid")
    }
    return try {
        val maxH = frame.components.maxOf { it.h }
        val maxV = frame.components.maxOf { it.v }
        val mcusWide = (frame.width + maxH - 1) / maxH
        val mcusHigh = (frame.height + maxV - 1) / maxV
        val planes = frame.components.map { component ->
            LosslessComponentPlane(
                component = component,
                width = (frame.width * component.h + maxH - 1) / maxH,
                height = (frame.height * component.v + maxV - 1) / maxV,
                codedWidth = mcusWide * component.h,
                codedHeight = mcusHigh * component.v,
            )
        }
        for (scan in frame.scans) {
            decodeArithmeticDifferentialLosslessScan(frame, scan, planes, maxH, maxV, mcusWide, mcusHigh)
        }
        val samples = Array(frame.components.size) { IntArray(frame.width * frame.height) }
        for (plane in planes) {
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    samples[plane.component.frameIndex][y * frame.width + x] =
                        sampleLosslessComponent(plane, x, y, maxH, maxV, maxSample = 0, clamp = false)
                }
            }
        }
        DecodedJpegSamples(frame.width, frame.height, frame.precision, samples.toList())
    } catch (failure: ArithmeticJpegException) {
        throw failure
    } catch (failure: IllegalArgumentException) {
        val arithmeticFailure = ArithmeticJpegException("jpeg.arithmetic.differential-lossless.entropy.invalid")
        arithmeticFailure.initCause(failure)
        throw arithmeticFailure
    }
}

private fun decodeLossless(frame: ParsedJpeg, differential: Boolean): DecodedJpegSamples {
    if (frame.coding != JpegCoding.kLossless) losslessFailure("jpeg.lossless.frame.invalid")
    if (frame.scans.isEmpty()) losslessFailure("jpeg.lossless.scan.incomplete")
    return try {
        val maxH = frame.components.maxOf { it.h }
        val maxV = frame.components.maxOf { it.v }
        val mcusWide = (frame.width + maxH - 1) / maxH
        val mcusHigh = (frame.height + maxV - 1) / maxV
        val planes = frame.components.map { component ->
            LosslessComponentPlane(
                component = component,
                width = (frame.width * component.h + maxH - 1) / maxH,
                height = (frame.height * component.v + maxV - 1) / maxV,
                codedWidth = mcusWide * component.h,
                codedHeight = mcusHigh * component.v,
            )
        }
        for (scan in frame.scans) {
            decodeLosslessScan(frame, scan, planes, maxH, maxV, mcusWide, mcusHigh, differential)
        }
        val samples = Array(frame.components.size) { IntArray(frame.width * frame.height) }
        val maxSample = (1 shl frame.precision) - 1
        for (plane in planes) {
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    samples[plane.component.frameIndex][y * frame.width + x] =
                        sampleLosslessComponent(plane, x, y, maxH, maxV, maxSample, clamp = !differential)
                }
            }
        }
        DecodedJpegSamples(frame.width, frame.height, frame.precision, samples.toList())
    } catch (failure: LosslessJpegException) {
        throw failure
    } catch (failure: IllegalArgumentException) {
        val losslessFailure = LosslessJpegException("jpeg.lossless.entropy.invalid")
        losslessFailure.initCause(failure)
        throw losslessFailure
    }
}

private data class LosslessComponentPlane(
    val component: Component,
    val width: Int,
    val height: Int,
    val codedWidth: Int,
    val codedHeight: Int,
    val samples: IntArray = IntArray(codedWidth * codedHeight),
)

private fun decodeLosslessScan(
    frame: ParsedJpeg,
    entropyScan: EntropyScan,
    planes: List<LosslessComponentPlane>,
    maxH: Int,
    maxV: Int,
    frameMcusWide: Int,
    frameMcusHigh: Int,
    differential: Boolean,
) {
    val scan = entropyScan.scan
    val nonInterleaved = scan.components.size == 1
    val onlyPlane = if (nonInterleaved) planes[scan.components.single().frameIndex] else null
    val mcusWide = if (nonInterleaved) onlyPlane!!.width else frameMcusWide
    val mcusHigh = if (nonInterleaved) onlyPlane!!.height else frameMcusHigh
    val totalMcus = mcusWide * mcusHigh
    val reader = EntropyBitReader(entropyScan.data)
    val pointTransform = scan.successiveApprox and 0x0F
    var nextRestartMarker = 0
    var mcu = 0

    for (mcuY in 0 until mcusHigh) {
        for (mcuX in 0 until mcusWide) {
            val intervalStart = mcu == 0 ||
                (entropyScan.restartInterval > 0 && mcu % entropyScan.restartInterval == 0)
            for (component in scan.components) {
                val plane = planes[component.frameIndex]
                val componentX = if (nonInterleaved) mcuX else mcuX * component.h
                val componentY = if (nonInterleaved) mcuY else mcuY * component.v
                val samplesWide = if (nonInterleaved) 1 else component.h
                val samplesHigh = if (nonInterleaved) 1 else component.v
                var firstSampleInInterval = intervalStart
                for (sampleY in 0 until samplesHigh) {
                    for (sampleX in 0 until samplesWide) {
                        decodeLosslessSample(
                            frame = frame,
                            entropyScan = entropyScan,
                            component = component,
                            plane = plane,
                            x = componentX + sampleX,
                            y = componentY + sampleY,
                            predictor = scan.spectralStart,
                            pointTransform = pointTransform,
                            initialPredictor = firstSampleInInterval,
                            reader = reader,
                            differential = differential,
                        )
                        firstSampleInInterval = false
                    }
                }
            }
            mcu++
            if (
                entropyScan.restartInterval > 0 &&
                mcu % entropyScan.restartInterval == 0 &&
                mcu < totalMcus
            ) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
            }
        }
    }
}

private fun decodeArithmeticDifferentialLosslessScan(
    frame: ParsedJpeg,
    entropyScan: EntropyScan,
    planes: List<LosslessComponentPlane>,
    maxH: Int,
    maxV: Int,
    frameMcusWide: Int,
    frameMcusHigh: Int,
) {
    val conditioning = entropyScan.arithmeticConditioning
        ?: arithmeticFailure("jpeg.arithmetic.differential-lossless.conditioning")
    val scan = entropyScan.scan
    val nonInterleaved = scan.components.size == 1
    val onlyPlane = if (nonInterleaved) planes[scan.components.single().frameIndex] else null
    val mcusWide = if (nonInterleaved) onlyPlane!!.width else frameMcusWide
    val mcusHigh = if (nonInterleaved) onlyPlane!!.height else frameMcusHigh
    val totalMcus = mcusWide * mcusHigh
    val decoder = ArithmeticDecoder(entropyScan.data)
    val contexts = Array(16) { ArithmeticLosslessContextSet() }
    val leftDifferences = planes.map { plane -> IntArray(plane.component.v) }
    val aboveDifferences = planes.map { plane -> IntArray(plane.codedWidth) }
    val pointTransform = scan.successiveApprox and 0x0F
    var mcu = 0

    for (mcuY in 0 until mcusHigh) {
        for (mcuX in 0 until mcusWide) {
            for (component in scan.components) {
                val plane = planes[component.frameIndex]
                val componentX = if (nonInterleaved) mcuX else mcuX * component.h
                val componentY = if (nonInterleaved) mcuY else mcuY * component.v
                val samplesWide = if (nonInterleaved) 1 else component.h
                val samplesHigh = if (nonInterleaved) 1 else component.v
                for (sampleY in 0 until samplesHigh) {
                    for (sampleX in 0 until samplesWide) {
                        val x = componentX + sampleX
                        val y = componentY + sampleY
                        if (x !in 0 until plane.codedWidth || y !in 0 until plane.codedHeight) {
                            arithmeticFailure("jpeg.arithmetic.differential-lossless.geometry")
                        }
                        val difference = decodeArithmeticLosslessDifference(
                            decoder = decoder,
                            contexts = contexts[component.dcTable],
                            leftDifference = leftDifferences[component.frameIndex][sampleY],
                            aboveDifference = aboveDifferences[component.frameIndex][x],
                            lower = conditioning.dcLower[component.dcTable],
                            upper = conditioning.dcUpper[component.dcTable],
                        )
                        plane.samples[y * plane.codedWidth + x] = difference.value shl pointTransform
                        leftDifferences[component.frameIndex][sampleY] = difference.value
                        aboveDifferences[component.frameIndex][x] = difference.value
                    }
                }
            }
            mcu++
            if (
                entropyScan.restartInterval > 0 &&
                mcu % entropyScan.restartInterval == 0 &&
                mcu < totalMcus
            ) {
                decoder.resetForRestart(scan.components, resetDc = true, resetAc = false)
                contexts.forEach(ArithmeticLosslessContextSet::reset)
                leftDifferences.forEach { it.fill(0) }
                aboveDifferences.forEach { it.fill(0) }
            }
        }
        // Da is the left-neighbour differential for each MCU-internal row;
        // it is reset after completing a whole MCU row, while Db retains the
        // above-neighbour differentials for the next row.
        leftDifferences.forEach { it.fill(0) }
    }
}

/** Annex D lossless arithmetic contexts: S0/SS/SP/SN plus low/high magnitudes. */
private class ArithmeticLosslessContextSet {
    private val zeroSign = ByteArray(5 * 5 * ZERO_SIGN_CONTEXTS)
    private val lowMagnitude = ByteArray(MAGNITUDE_CONTEXTS * 2)
    private val highMagnitude = ByteArray(MAGNITUDE_CONTEXTS * 2)

    fun zeroSignContexts(leftDifference: Int, aboveDifference: Int, lower: Int, upper: Int): ByteArrayIndex =
        ByteArrayIndex(
            zeroSign,
            ((classify(leftDifference, lower, upper) + 2) * 5 + classify(aboveDifference, lower, upper) + 2) *
                ZERO_SIGN_CONTEXTS,
        )

    fun magnitudeContexts(aboveDifference: Int, upper: Int): ByteArray =
        if (absoluteExceeds(aboveDifference, upper)) highMagnitude else lowMagnitude

    fun reset() {
        zeroSign.fill(0)
        lowMagnitude.fill(0)
        highMagnitude.fill(0)
    }

    private fun classify(difference: Int, lower: Int, upper: Int): Int {
        val magnitude = kotlin.math.abs(difference.toLong())
        return when {
            magnitude <= ((1L shl lower) shr 1) -> 0
            magnitude <= (1L shl upper) -> if (difference < 0) -1 else 1
            difference < 0 -> -2
            else -> 2
        }
    }

    private fun absoluteExceeds(difference: Int, upper: Int): Boolean =
        kotlin.math.abs(difference.toLong()) > (1L shl upper)

    private companion object {
        const val ZERO_SIGN_CONTEXTS = 4
        const val MAGNITUDE_CONTEXTS = 15
    }
}

private data class ByteArrayIndex(val contexts: ByteArray, val index: Int)

private data class ArithmeticLosslessDifference(val value: Int)

private fun decodeArithmeticLosslessDifference(
    decoder: ArithmeticDecoder,
    contexts: ArithmeticLosslessContextSet,
    leftDifference: Int,
    aboveDifference: Int,
    lower: Int,
    upper: Int,
): ArithmeticLosslessDifference {
    val zeroSign = contexts.zeroSignContexts(leftDifference, aboveDifference, lower, upper)
    if (decoder.decodeContext(zeroSign.contexts, zeroSign.index) == 0) {
        return ArithmeticLosslessDifference(0)
    }

    val negative = decoder.decodeContext(zeroSign.contexts, zeroSign.index + 1) != 0
    var magnitudeMinusOne = 0
    val signMagnitudeContext = if (negative) zeroSign.index + 3 else zeroSign.index + 2
    if (decoder.decodeContext(zeroSign.contexts, signMagnitudeContext) != 0) {
        val magnitudeContexts = contexts.magnitudeContexts(aboveDifference, upper)
        var context = 0
        var magnitude = 2
        while (decoder.decodeContext(magnitudeContexts, context) != 0) {
            magnitude = magnitude shl 1
            context++
            if (context >= 15) arithmeticFailure("jpeg.arithmetic.differential-lossless.magnitude")
        }
        magnitude = magnitude shr 1
        magnitudeMinusOne = magnitude
        while (true) {
            magnitude = magnitude shr 1
            if (magnitude == 0) break
            if (decoder.decodeContext(magnitudeContexts, 15 + context) != 0) {
                magnitudeMinusOne = magnitudeMinusOne or magnitude
            }
        }
    }
    return ArithmeticLosslessDifference(if (negative) -magnitudeMinusOne - 1 else magnitudeMinusOne + 1)
}

private fun decodeLosslessSample(
    frame: ParsedJpeg,
    entropyScan: EntropyScan,
    component: Component,
    plane: LosslessComponentPlane,
    x: Int,
    y: Int,
    predictor: Int,
    pointTransform: Int,
    initialPredictor: Boolean,
    reader: EntropyBitReader,
    differential: Boolean,
) {
    if (x !in 0 until plane.codedWidth || y !in 0 until plane.codedHeight) {
        losslessFailure("jpeg.lossless.scan.geometry")
    }
    val table = entropyScan.dcTables.getOrNull(component.dcTable)
        ?: losslessFailure("jpeg.lossless.scan.table")
    val category = table.decode(reader)
    if (category !in 0..frame.precision - pointTransform) {
        losslessFailure("jpeg.lossless.entropy.category")
    }
    val difference = receiveAndExtend(reader, category)
    val predicted = when {
        differential -> 0
        initialPredictor -> 1 shl (frame.precision - 1)
        y == 0 -> plane.sampleAt(x - 1, y)
        x == 0 -> plane.sampleAt(x, y - 1)
        else -> losslessPredictor(
            predictor,
            left = plane.sampleAt(x - 1, y) shr pointTransform,
            above = plane.sampleAt(x, y - 1) shr pointTransform,
            upperLeft = plane.sampleAt(x - 1, y - 1) shr pointTransform,
        ) shl pointTransform
    }
    val sample = predicted + (difference shl pointTransform)
    val maxSample = (1 shl frame.precision) - 1
    if (!differential && sample !in 0..maxSample) losslessFailure("jpeg.lossless.sample.range")
    plane.samples[y * plane.codedWidth + x] = sample
}

private fun losslessPredictor(predictor: Int, left: Int, above: Int, upperLeft: Int): Int = when (predictor) {
    1 -> left
    2 -> above
    3 -> upperLeft
    4 -> left + above - upperLeft
    5 -> left + ((above - upperLeft) shr 1)
    6 -> above + ((left - upperLeft) shr 1)
    7 -> (left + above) shr 1
    else -> losslessFailure("jpeg.lossless.predictor.invalid")
}

private fun sampleLosslessComponent(
    plane: LosslessComponentPlane,
    x: Int,
    y: Int,
    maxH: Int,
    maxV: Int,
    maxSample: Int,
    clamp: Boolean = true,
): Int {
    val component = plane.component
    if (component.h == maxH && component.v == maxV) return plane.sampleAt(x, y)
    val sourceX = ((x + 0.5) * component.h / maxH) - 0.5
    val sourceY = ((y + 0.5) * component.v / maxV) - 0.5
    val x0 = floor(sourceX).toInt().coerceIn(0, plane.width - 1)
    val y0 = floor(sourceY).toInt().coerceIn(0, plane.height - 1)
    val x1 = (x0 + 1).coerceAtMost(plane.width - 1)
    val y1 = (y0 + 1).coerceAtMost(plane.height - 1)
    val fx = (sourceX - x0).coerceIn(0.0, 1.0)
    val fy = (sourceY - y0).coerceIn(0.0, 1.0)
    val top = plane.sampleAt(x0, y0) * (1.0 - fx) + plane.sampleAt(x1, y0) * fx
    val bottom = plane.sampleAt(x0, y1) * (1.0 - fx) + plane.sampleAt(x1, y1) * fx
    val value = (top * (1.0 - fy) + bottom * fy).roundToInt()
    return if (clamp) value.coerceIn(0, maxSample) else value
}

private fun LosslessComponentPlane.sampleAt(x: Int, y: Int): Int = samples[y * codedWidth + x]
