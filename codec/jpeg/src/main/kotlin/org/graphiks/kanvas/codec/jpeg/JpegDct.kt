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

/** Decodes all progressive scans into native component planes before composition. */
internal fun decodeProgressiveDct(frame: ParsedJpeg): DecodedJpegSamples {
    if (frame.coding != JpegCoding.kProgressive) progressiveFailure("jpeg.progressive.frame.invalid")
    if (frame.scans.isEmpty()) progressiveFailure("jpeg.progressive.scan.incomplete")
    return try {
        val maxH = frame.components.maxOf { it.h }
        val maxV = frame.components.maxOf { it.v }
        val maxSample = (1 shl frame.precision) - 1
        val centerSample = 1 shl (frame.precision - 1)
        val planes = frame.components.map { component ->
            ProgressiveComponentPlane(
                component = component,
                width = (frame.width * component.h + maxH - 1) / maxH,
                height = (frame.height * component.v + maxV - 1) / maxV,
            )
        }
        validateProgressiveScans(frame)
        for (entropyScan in frame.scans) {
            decodeProgressiveScan(frame, entropyScan, planes, maxH, maxV)
        }
        val samples = Array(frame.components.size) { IntArray(frame.width * frame.height) }
        for (plane in planes) {
            materializeProgressivePlane(plane, centerSample, maxSample)
            for (y in 0 until frame.height) {
                for (x in 0 until frame.width) {
                    samples[plane.component.frameIndex][y * frame.width + x] =
                        sampleProgressiveComponent(plane, x, y, maxH, maxV, maxSample)
                }
            }
        }
        DecodedJpegSamples(frame.width, frame.height, frame.precision, samples.toList())
    } catch (failure: ProgressiveJpegException) {
        throw failure
    } catch (failure: IllegalArgumentException) {
        val progressiveFailure = ProgressiveJpegException("jpeg.progressive.entropy.invalid")
        progressiveFailure.initCause(failure)
        throw progressiveFailure
    }
}

private data class ProgressiveComponentPlane(
    val component: Component,
    val width: Int,
    val height: Int,
    val blocksWide: Int = (width + 7) / 8,
    val blocksHigh: Int = (height + 7) / 8,
    val coefficients: Array<IntArray> = Array(blocksWide * blocksHigh) { IntArray(64) },
    val samples: IntArray = IntArray(width * height),
)

private fun validateProgressiveScans(frame: ParsedJpeg) {
    val approximation = Array(frame.components.size) { IntArray(64) { -1 } }
    for (entropyScan in frame.scans) {
        val scan = entropyScan.scan
        if (scan.components.isEmpty()) progressiveFailure("jpeg.progressive.scan.incomplete")
        val successiveHigh = scan.successiveApprox ushr 4
        val successiveLow = scan.successiveApprox and 0x0F
        if (successiveHigh > 0 && successiveLow != successiveHigh - 1) {
            progressiveFailure("jpeg.progressive.scan.refinement-order")
        }
        for (component in scan.components) {
            if (scan.spectralStart != 0 && approximation[component.frameIndex][0] == -1) {
                progressiveFailure("jpeg.progressive.scan.order")
            }
            val quant = entropyScan.quantTables.getOrNull(component.quantTable)
            val entropyTable = if (scan.spectralStart == 0) {
                entropyScan.dcTables.getOrNull(component.dcTable)
            } else {
                entropyScan.acTables.getOrNull(component.acTable)
            }
            if (quant == null || entropyTable == null) {
                progressiveFailure("jpeg.progressive.scan.table")
            }
            val state = approximation[component.frameIndex]
            for (coefficient in scan.spectralStart..scan.spectralEnd) {
                if (successiveHigh == 0) {
                    if (state[coefficient] != -1) {
                        progressiveFailure("jpeg.progressive.scan.duplicate")
                    }
                    state[coefficient] = successiveLow
                } else {
                    if (state[coefficient] == -1 && scan.spectralStart != 0) {
                        state[coefficient] = successiveLow
                    } else if (state[coefficient] != successiveHigh) {
                        progressiveFailure("jpeg.progressive.scan.refinement-order")
                    } else {
                        state[coefficient] = successiveLow
                    }
                }
            }
        }
    }
    if (approximation.any { it[0] == -1 }) {
        progressiveFailure("jpeg.progressive.scan.incomplete")
    }
}

private fun decodeProgressiveScan(
    frame: ParsedJpeg,
    entropyScan: EntropyScan,
    planes: List<ProgressiveComponentPlane>,
    maxH: Int,
    maxV: Int,
) {
    val scan = entropyScan.scan
    val nonInterleaved = scan.components.size == 1
    val onlyPlane = if (nonInterleaved) planes[scan.components.single().frameIndex] else null
    val mcuWidth = if (nonInterleaved) 8 else maxH * 8
    val mcuHeight = if (nonInterleaved) 8 else maxV * 8
    val mcusWide = if (nonInterleaved) onlyPlane!!.blocksWide else (frame.width + mcuWidth - 1) / mcuWidth
    val mcusHigh = if (nonInterleaved) onlyPlane!!.blocksHigh else (frame.height + mcuHeight - 1) / mcuHeight
    val totalMcus = mcusWide * mcusHigh
    val reader = EntropyBitReader(entropyScan.data)
    val previousDc = IntArray(frame.components.size)
    val successiveHigh = scan.successiveApprox ushr 4
    val successiveLow = scan.successiveApprox and 0x0F
    var eobRun = 0
    var nextRestartMarker = 0
    var mcu = 0

    for (mcuY in 0 until mcusHigh) {
        for (mcuX in 0 until mcusWide) {
            for (component in scan.components) {
                val plane = planes[component.frameIndex]
                val componentBlockX = if (nonInterleaved) mcuX else mcuX * component.h
                val componentBlockY = if (nonInterleaved) mcuY else mcuY * component.v
                val blocksWide = if (nonInterleaved) 1 else component.h
                val blocksHigh = if (nonInterleaved) 1 else component.v
                for (blockY in 0 until blocksHigh) {
                    for (blockX in 0 until blocksWide) {
                        val coefficients = plane.coefficients[
                            (componentBlockY + blockY) * plane.blocksWide + componentBlockX + blockX
                        ]
                        if (scan.spectralStart == 0) {
                            decodeProgressiveDcCoefficient(
                                entropyScan,
                                component,
                                reader,
                                previousDc,
                                coefficients,
                                successiveHigh,
                                successiveLow,
                            )
                        } else if (successiveHigh == 0) {
                            eobRun = decodeProgressiveAcInitial(
                                entropyScan,
                                component,
                                reader,
                                coefficients,
                                eobRun,
                            )
                        } else {
                            eobRun = decodeProgressiveAcRefinement(
                                entropyScan,
                                component,
                                reader,
                                coefficients,
                                eobRun,
                            )
                        }
                    }
                }
            }
            mcu++
            if (entropyScan.restartInterval > 0 && mcu % entropyScan.restartInterval == 0 && mcu < totalMcus) {
                reader.consumeRestart(nextRestartMarker)
                nextRestartMarker = (nextRestartMarker + 1) and 7
                previousDc.fill(0)
                eobRun = 0
            }
        }
    }
}

private fun decodeProgressiveDcCoefficient(
    entropyScan: EntropyScan,
    component: Component,
    reader: EntropyBitReader,
    previousDc: IntArray,
    coefficients: IntArray,
    successiveHigh: Int,
    successiveLow: Int,
) {
    val quant = entropyScan.quantTables[component.quantTable] ?: fail()
    if (successiveHigh == 0) {
        val dcTable = entropyScan.dcTables[component.dcTable] ?: fail()
        val category = dcTable.decode(reader)
        if (category !in 0..11) fail()
        previousDc[component.frameIndex] += receiveAndExtend(reader, category)
        coefficients[0] = previousDc[component.frameIndex] * (1 shl successiveLow) * quant[0]
    } else if (reader.readBit() != 0) {
        coefficients[0] += (1 shl successiveLow) * quant[0]
    }
}

private fun decodeProgressiveAcInitial(
    entropyScan: EntropyScan,
    component: Component,
    reader: EntropyBitReader,
    coefficients: IntArray,
    previousEobRun: Int,
): Int {
    if (previousEobRun > 0) return previousEobRun - 1
    val scan = entropyScan.scan
    val acTable = entropyScan.acTables[component.acTable] ?: fail()
    val quant = entropyScan.quantTables[component.quantTable] ?: fail()
    val scale = 1 shl (scan.successiveApprox and 0x0F)
    var coefficient = scan.spectralStart
    while (coefficient <= scan.spectralEnd) {
        val runAndSize = acTable.decode(reader)
        if (runAndSize == 0) return 0
        val run = runAndSize ushr 4
        val size = runAndSize and 0x0F
        if (size == 0) {
            if (run == 15) {
                if (coefficient + 16 > scan.spectralEnd + 1) fail()
                coefficient += 16
                continue
            }
            return (1 shl run) + reader.readBits(run) - 1
        }
        if (size > 10) fail()
        coefficient += run
        if (coefficient > scan.spectralEnd) fail()
        val index = JPEG_ZIGZAG[coefficient]
        coefficients[index] = receiveAndExtend(reader, size) * scale * quant[index]
        coefficient++
    }
    return 0
}

private fun decodeProgressiveAcRefinement(
    entropyScan: EntropyScan,
    component: Component,
    reader: EntropyBitReader,
    coefficients: IntArray,
    previousEobRun: Int,
): Int {
    val scan = entropyScan.scan
    val acTable = entropyScan.acTables[component.acTable] ?: fail()
    val quant = entropyScan.quantTables[component.quantTable] ?: fail()
    val refinement = 1 shl (scan.successiveApprox and 0x0F)
    var coefficient = scan.spectralStart
    if (previousEobRun > 0) {
        refineExistingProgressiveAc(coefficients, quant, reader, refinement, coefficient, scan.spectralEnd)
        return previousEobRun - 1
    }
    while (coefficient <= scan.spectralEnd) {
        val index = JPEG_ZIGZAG[coefficient]
        if (coefficients[index] != 0) {
            refineExistingProgressiveAcCoefficient(coefficients, quant, reader, refinement, index)
            coefficient++
            continue
        }
        val runAndSize = acTable.decode(reader)
        val run = runAndSize ushr 4
        val size = runAndSize and 0x0F
        if (size == 0) {
            if (run == 15) {
                coefficient = skipZeroesForProgressiveRefinement(
                    coefficients,
                    quant,
                    reader,
                    refinement,
                    coefficient,
                    scan.spectralEnd,
                    16,
                )
                continue
            }
            val eobRun = (1 shl run) + reader.readBits(run)
            refineExistingProgressiveAc(coefficients, quant, reader, refinement, coefficient, scan.spectralEnd)
            return eobRun - 1
        }
        if (size != 1) fail()
        coefficient = skipZeroesForProgressiveRefinement(
            coefficients,
            quant,
            reader,
            refinement,
            coefficient,
            scan.spectralEnd,
            run,
        )
        if (coefficient > scan.spectralEnd) fail()
        val newIndex = JPEG_ZIGZAG[coefficient]
        if (coefficients[newIndex] != 0) fail()
        coefficients[newIndex] = if (reader.readBit() == 0) {
            -refinement * quant[newIndex]
        } else {
            refinement * quant[newIndex]
        }
        coefficient++
    }
    return 0
}

private fun skipZeroesForProgressiveRefinement(
    coefficients: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    start: Int,
    end: Int,
    zeroCount: Int,
): Int {
    var coefficient = start
    var zeroes = zeroCount
    while (coefficient <= end) {
        val index = JPEG_ZIGZAG[coefficient]
        if (coefficients[index] != 0) {
            refineExistingProgressiveAcCoefficient(coefficients, quant, reader, refinement, index)
        } else {
            if (zeroes == 0) return coefficient
            zeroes--
        }
        coefficient++
    }
    if (zeroes != 0) fail()
    return coefficient
}

private fun refineExistingProgressiveAc(
    coefficients: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    start: Int,
    end: Int,
) {
    for (coefficient in start..end) {
        val index = JPEG_ZIGZAG[coefficient]
        if (coefficients[index] != 0) {
            refineExistingProgressiveAcCoefficient(coefficients, quant, reader, refinement, index)
        }
    }
}

private fun refineExistingProgressiveAcCoefficient(
    coefficients: IntArray,
    quant: IntArray,
    reader: EntropyBitReader,
    refinement: Int,
    index: Int,
) {
    val coefficient = coefficients[index]
    if (coefficient == 0 || reader.readBit() == 0) return
    coefficients[index] += if (coefficient > 0) refinement * quant[index] else -refinement * quant[index]
}

private fun materializeProgressivePlane(
    plane: ProgressiveComponentPlane,
    centerSample: Int,
    maxSample: Int,
) {
    for (blockY in 0 until plane.blocksHigh) {
        for (blockX in 0 until plane.blocksWide) {
            val block = idct(plane.coefficients[blockY * plane.blocksWide + blockX])
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
    }
}

private fun sampleProgressiveComponent(
    plane: ProgressiveComponentPlane,
    x: Int,
    y: Int,
    maxH: Int,
    maxV: Int,
    maxSample: Int,
): Int {
    val component = plane.component
    if (component.h == maxH && component.v == maxV) return plane.samples[y * plane.width + x]
    val sourceX = ((x + 0.5) * component.h / maxH) - 0.5
    val sourceY = ((y + 0.5) * component.v / maxV) - 0.5
    val x0 = floor(sourceX).toInt().coerceIn(0, plane.width - 1)
    val y0 = floor(sourceY).toInt().coerceIn(0, plane.height - 1)
    val x1 = (x0 + 1).coerceAtMost(plane.width - 1)
    val y1 = (y0 + 1).coerceAtMost(plane.height - 1)
    val fx = (sourceX - x0).coerceIn(0.0, 1.0)
    val fy = (sourceY - y0).coerceIn(0.0, 1.0)
    val top = plane.samples[y0 * plane.width + x0] * (1.0 - fx) + plane.samples[y0 * plane.width + x1] * fx
    val bottom = plane.samples[y1 * plane.width + x0] * (1.0 - fx) + plane.samples[y1 * plane.width + x1] * fx
    return (top * (1.0 - fy) + bottom * fy).roundToInt().coerceIn(0, maxSample)
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
