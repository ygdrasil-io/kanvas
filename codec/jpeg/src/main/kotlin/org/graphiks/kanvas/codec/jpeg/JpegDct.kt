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

/** Decodes one validated interleaved sequential scan into full-resolution component planes. */
internal fun decodeSequentialDct(frame: ParsedJpeg, scan: EntropyScan): DecodedJpegSamples {
    if (frame.coding != JpegCoding.kBaseline || scan.scan.components.size != frame.components.size) fail()
    val components = scan.scan.components
    val maxH = frame.components.maxOf { it.h }
    val maxV = frame.components.maxOf { it.v }
    val mcuWidth = maxH * 8
    val mcuHeight = maxV * 8
    val blocksX = (frame.width + mcuWidth - 1) / mcuWidth
    val blocksY = (frame.height + mcuHeight - 1) / mcuHeight
    val totalMcus = blocksX * blocksY
    val maxSample = (1 shl frame.precision) - 1
    val centerSample = 1 shl (frame.precision - 1)
    val reader = EntropyBitReader(scan.data)
    val previousDc = IntArray(frame.components.size)
    val planes = Array(frame.components.size) { IntArray(frame.width * frame.height) }
    val blocks = Array(frame.components.size) { index ->
        val component = frame.components.first { it.frameIndex == index }
        Array(component.h * component.v) { IntArray(64) }
    }
    var nextRestartMarker = 0
    var mcu = 0

    for (mcuY in 0 until blocksY) {
        for (mcuX in 0 until blocksX) {
            for (component in components) {
                for (blockY in 0 until component.v) {
                    for (blockX in 0 until component.h) {
                        blocks[component.frameIndex][blockY * component.h + blockX] =
                            decodeSequentialBlock(frame, component, reader, previousDc)
                    }
                }
            }
            for (y in 0 until mcuHeight) {
                val py = mcuY * mcuHeight + y
                if (py >= frame.height) continue
                for (x in 0 until mcuWidth) {
                    val px = mcuX * mcuWidth + x
                    if (px >= frame.width) continue
                    for (component in frame.components) {
                        val value = sampleSequentialComponent(
                            blocks[component.frameIndex],
                            component,
                            x,
                            y,
                            mcuWidth,
                            mcuHeight,
                            centerSample,
                            maxSample,
                        )
                        planes[component.frameIndex][py * frame.width + px] = value
                    }
                }
            }
            mcu++
            if (frame.restartInterval > 0 && mcu % frame.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
            }
        }
    }
    return DecodedJpegSamples(frame.width, frame.height, frame.precision, planes.toList())
}

private fun decodeSequentialBlock(
    frame: ParsedJpeg,
    component: Component,
    reader: EntropyBitReader,
    previousDc: IntArray,
): IntArray {
    val quant = frame.quantTables[component.quantTable] ?: fail()
    val dcTable = frame.dcTables[component.dcTable] ?: fail()
    val acTable = frame.acTables[component.acTable] ?: fail()
    val coeffs = IntArray(64)
    val dcCategory = dcTable.decode(reader)
    if (dcCategory !in 0..if (frame.precision == 8) 11 else 15) fail()
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
        if (size > if (frame.precision == 8) 10 else 14) fail()
        coefficient += run
        if (coefficient >= 64) fail()
        val index = JPEG_ZIGZAG[coefficient]
        coeffs[index] = receiveAndExtend(reader, size) * quant[index]
        coefficient++
    }
    return idct(coeffs)
}

private fun sampleSequentialComponent(
    blocks: Array<IntArray>,
    component: Component,
    mcuX: Int,
    mcuY: Int,
    mcuWidth: Int,
    mcuHeight: Int,
    centerSample: Int,
    maxSample: Int,
): Int {
    val componentWidth = component.h * 8
    val componentHeight = component.v * 8
    if (componentWidth == mcuWidth && componentHeight == mcuHeight) {
        return sampleSequentialComponentAt(blocks, component, mcuX, mcuY, centerSample, maxSample)
    }
    val sourceX = ((mcuX + 0.5) * componentWidth / mcuWidth) - 0.5
    val sourceY = ((mcuY + 0.5) * componentHeight / mcuHeight) - 0.5
    val x0 = floor(sourceX).toInt().coerceIn(0, componentWidth - 1)
    val y0 = floor(sourceY).toInt().coerceIn(0, componentHeight - 1)
    val x1 = (x0 + 1).coerceAtMost(componentWidth - 1)
    val y1 = (y0 + 1).coerceAtMost(componentHeight - 1)
    val fx = (sourceX - x0).coerceIn(0.0, 1.0)
    val fy = (sourceY - y0).coerceIn(0.0, 1.0)
    val top = sampleSequentialComponentAt(blocks, component, x0, y0, centerSample, maxSample) * (1.0 - fx) +
        sampleSequentialComponentAt(blocks, component, x1, y0, centerSample, maxSample) * fx
    val bottom = sampleSequentialComponentAt(blocks, component, x0, y1, centerSample, maxSample) * (1.0 - fx) +
        sampleSequentialComponentAt(blocks, component, x1, y1, centerSample, maxSample) * fx
    return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, maxSample)
}

private fun sampleSequentialComponentAt(
    blocks: Array<IntArray>,
    component: Component,
    x: Int,
    y: Int,
    centerSample: Int,
    maxSample: Int,
): Int {
    val block = blocks[(y / 8) * component.h + (x / 8)]
    return (block[(y and 7) * 8 + (x and 7)] + centerSample).coerceIn(0, maxSample)
}

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
