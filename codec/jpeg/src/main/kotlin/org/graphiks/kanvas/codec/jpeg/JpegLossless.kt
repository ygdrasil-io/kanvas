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
internal fun decodeLossless(frame: ParsedJpeg): DecodedJpegSamples {
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
            decodeLosslessScan(frame, scan, planes, maxH, maxV, mcusWide, mcusHigh)
        }
        val samples = Array(frame.components.size) { IntArray(frame.width * frame.height) }
        val maxSample = (1 shl frame.precision) - 1
        for (plane in planes) {
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    samples[plane.component.frameIndex][y * frame.width + x] =
                        sampleLosslessComponent(plane, x, y, maxH, maxV, maxSample)
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
    if (sample !in 0..maxSample) losslessFailure("jpeg.lossless.sample.range")
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
    return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, maxSample)
}

private fun LosslessComponentPlane.sampleAt(x: Int, y: Int): Int = samples[y * codedWidth + x]
