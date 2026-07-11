package org.graphiks.kanvas.codec.jpeg

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class DecodedJpegSamples(
    val width: Int,
    val height: Int,
    val precision: Int,
    val planes: List<IntArray>,
)

/** Decodes all validated sequential scans and composes their native component planes. */
internal fun decodeSequentialDct(frame: ParsedJpeg): DecodedJpegSamples {
    if (frame.coding != JpegCoding.kBaseline) fail()
    val maxH = frame.components.maxOf { it.h }
    val maxV = frame.components.maxOf { it.v }
    val maxSample = (1 shl frame.precision) - 1
    val centerSample = 1 shl (frame.precision - 1)
    val nativePlanes = frame.components.map { component ->
        SequentialComponentPlane(
            component = component,
            width = (frame.width * component.h + maxH - 1) / maxH,
            height = (frame.height * component.v + maxV - 1) / maxV,
        )
    }
    for (scan in frame.scans) {
        decodeSequentialScan(frame, scan, nativePlanes, maxH, maxV, centerSample, maxSample)
    }
    val planes = Array(frame.components.size) { IntArray(frame.width * frame.height) }
    for (y in 0 until frame.height) {
        for (x in 0 until frame.width) {
            for (plane in nativePlanes) {
                planes[plane.component.frameIndex][y * frame.width + x] =
                    sampleSequentialComponent(plane, x, y, maxH, maxV, maxSample)
            }
        }
    }
    return DecodedJpegSamples(frame.width, frame.height, frame.precision, planes.toList())
}

private data class SequentialComponentPlane(
    val component: Component,
    val width: Int,
    val height: Int,
    val samples: IntArray = IntArray(width * height),
)

private fun decodeSequentialScan(
    frame: ParsedJpeg,
    entropyScan: EntropyScan,
    planes: List<SequentialComponentPlane>,
    maxH: Int,
    maxV: Int,
    centerSample: Int,
    maxSample: Int,
) {
    val scanComponents = entropyScan.scan.components
    val nonInterleaved = scanComponents.size == 1
    val onlyPlane = if (nonInterleaved) planes[scanComponents.single().frameIndex] else null
    val mcuWidth = if (nonInterleaved) 8 else maxH * 8
    val mcuHeight = if (nonInterleaved) 8 else maxV * 8
    val blocksX = if (nonInterleaved) {
        (onlyPlane!!.width + 7) / 8
    } else {
        (frame.width + mcuWidth - 1) / mcuWidth
    }
    val blocksY = if (nonInterleaved) {
        (onlyPlane!!.height + 7) / 8
    } else {
        (frame.height + mcuHeight - 1) / mcuHeight
    }
    val totalMcus = blocksX * blocksY
    val reader = EntropyBitReader(entropyScan.data)
    val previousDc = IntArray(frame.components.size)
    var nextRestartMarker = 0
    var mcu = 0

    for (mcuY in 0 until blocksY) {
        for (mcuX in 0 until blocksX) {
            for (component in scanComponents) {
                val plane = planes[component.frameIndex]
                val componentBlockX = if (nonInterleaved) mcuX else mcuX * component.h
                val componentBlockY = if (nonInterleaved) mcuY else mcuY * component.v
                val blocksWide = if (nonInterleaved) 1 else component.h
                val blocksHigh = if (nonInterleaved) 1 else component.v
                for (blockY in 0 until blocksHigh) {
                    for (blockX in 0 until blocksWide) {
                        writeSequentialBlock(
                            plane,
                            componentBlockX + blockX,
                            componentBlockY + blockY,
                            decodeSequentialBlock(frame.precision, entropyScan, component, reader, previousDc),
                            centerSample,
                            maxSample,
                        )
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
                previousDc.fill(0)
            }
        }
    }
}

private fun decodeSequentialBlock(
    precision: Int,
    entropyScan: EntropyScan,
    component: Component,
    reader: EntropyBitReader,
    previousDc: IntArray,
): IntArray {
    val quant = entropyScan.quantTables[component.quantTable] ?: fail()
    val dcTable = entropyScan.dcTables[component.dcTable] ?: fail()
    val acTable = entropyScan.acTables[component.acTable] ?: fail()
    val coeffs = IntArray(64)
    val dcCategory = dcTable.decode(reader)
    if (dcCategory !in 0..if (precision == 8) 11 else 15) fail()
    previousDc[component.frameIndex] += receiveAndExtend(reader, dcCategory)
    coeffs[0] = previousDc[component.frameIndex] * quant[0]

    var coefficient = 1
    while (coefficient < 64) {
        val runAndSize = acTable.decode(reader)
        if (runAndSize == 0) break
        val run = runAndSize ushr 4
        val size = runAndSize and 0x0F
        if (size == 0) {
            if (run != 15) fail()
            if (coefficient + 16 > 64) fail()
            coefficient += 16
            continue
        }
        if (size > if (precision == 8) 10 else 14) fail()
        coefficient += run
        if (coefficient >= 64) fail()
        val index = JPEG_ZIGZAG[coefficient]
        coeffs[index] = receiveAndExtend(reader, size) * quant[index]
        coefficient++
    }
    return idct(coeffs)
}

private fun writeSequentialBlock(
    plane: SequentialComponentPlane,
    blockX: Int,
    blockY: Int,
    block: IntArray,
    centerSample: Int,
    maxSample: Int,
) {
    for (y in 0 until 8) {
        val targetY = blockY * 8 + y
        if (targetY >= plane.height) continue
        for (x in 0 until 8) {
            val targetX = blockX * 8 + x
            if (targetX >= plane.width) continue
            plane.samples[targetY * plane.width + targetX] =
                (block[y * 8 + x] + centerSample).coerceIn(0, maxSample)
        }
    }
}

private fun sampleSequentialComponent(
    plane: SequentialComponentPlane,
    x: Int,
    y: Int,
    maxH: Int,
    maxV: Int,
    maxSample: Int,
): Int {
    val component = plane.component
    if (component.h == maxH && component.v == maxV) {
        return plane.samples[y * plane.width + x]
    }
    val sourceX = ((x + 0.5) * component.h / maxH) - 0.5
    val sourceY = ((y + 0.5) * component.v / maxV) - 0.5
    val x0 = floor(sourceX).toInt().coerceIn(0, plane.width - 1)
    val y0 = floor(sourceY).toInt().coerceIn(0, plane.height - 1)
    val x1 = (x0 + 1).coerceAtMost(plane.width - 1)
    val y1 = (y0 + 1).coerceAtMost(plane.height - 1)
    val fx = (sourceX - x0).coerceIn(0.0, 1.0)
    val fy = (sourceY - y0).coerceIn(0.0, 1.0)
    val top = sampleSequentialComponentAt(plane, x0, y0) * (1.0 - fx) +
        sampleSequentialComponentAt(plane, x1, y0) * fx
    val bottom = sampleSequentialComponentAt(plane, x0, y1) * (1.0 - fx) +
        sampleSequentialComponentAt(plane, x1, y1) * fx
    return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, maxSample)
}

private fun sampleSequentialComponentAt(
    plane: SequentialComponentPlane,
    x: Int,
    y: Int,
): Int = plane.samples[y * plane.width + x]

internal fun idct(coeffs: IntArray): IntArray {
    val output = IntArray(64)
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
            output[y * 8 + x] = (sum / 4.0).roundToInt()
        }
    }
    return output
}

internal val JPEG_ZIGZAG = intArrayOf(
    0, 1, 8, 16, 9, 2, 3, 10,
    17, 24, 32, 25, 18, 11, 4, 5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13, 6, 7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63,
)

private val INV_SQRT2 = 1.0 / sqrt(2.0)
