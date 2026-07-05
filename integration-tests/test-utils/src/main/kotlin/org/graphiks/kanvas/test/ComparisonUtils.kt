package org.graphiks.kanvas.test

import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.math.abs

object ComparisonUtils {
    private const val BYTES_PER_PIXEL = 4

    data class ComparisonResult(
        val similarity: Double,
        val totalPixels: Int,
        val matchingPixels: Int,
        val maxDiff: IntArray,
        val meanDiff: DoubleArray,
        val diffRgba: ByteArray?,
        val isPassing: Boolean,
        val minSimilarity: Double,
    )

    fun compareRgba(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int = 0,
        minSimilarity: Double = 100.0,
    ): ComparisonResult {
        require(actual.size == reference.size) {
            "Buffer sizes differ: actual=${actual.size}, reference=${reference.size}"
        }
        require(actual.size == width * height * BYTES_PER_PIXEL) {
            "Buffer size mismatch: expected=${width * height * BYTES_PER_PIXEL}, actual=${actual.size}"
        }

        val totalPixels = width * height
        var matchingPixels = 0
        val maxDiff = intArrayOf(0, 0, 0, 0)
        val sumDiff = longArrayOf(0L, 0L, 0L, 0L)
        var mismatchCount = 0
        val diffRgba = ByteArray(actual.size)

        for (i in 0 until totalPixels) {
            val base = i * BYTES_PER_PIXEL
            val aR = actual[base].toInt() and 0xFF
            val aG = actual[base + 1].toInt() and 0xFF
            val aB = actual[base + 2].toInt() and 0xFF
            val aA = actual[base + 3].toInt() and 0xFF

            val rR = reference[base].toInt() and 0xFF
            val rG = reference[base + 1].toInt() and 0xFF
            val rB = reference[base + 2].toInt() and 0xFF
            val rA = reference[base + 3].toInt() and 0xFF

            val dR = abs(aR - rR)
            val dG = abs(aG - rG)
            val dB = abs(aB - rB)
            val dA = abs(aA - rA)

            if (dR <= tolerance && dG <= tolerance && dB <= tolerance && dA <= tolerance) {
                matchingPixels++
                diffRgba[base] = 0
                diffRgba[base + 1] = 0
                diffRgba[base + 2] = 0
                diffRgba[base + 3] = 0
            } else {
                mismatchCount++
                maxDiff[0] = maxOf(maxDiff[0], dR)
                maxDiff[1] = maxOf(maxDiff[1], dG)
                maxDiff[2] = maxOf(maxDiff[2], dB)
                maxDiff[3] = maxOf(maxDiff[3], dA)
                sumDiff[0] += dR
                sumDiff[1] += dG
                sumDiff[2] += dB
                sumDiff[3] += dA

                diffRgba[base] = 255.toByte()
                diffRgba[base + 1] = 0.toByte()
                diffRgba[base + 2] = 0.toByte()
                diffRgba[base + 3] = 255.toByte()
            }
        }

        val similarity = if (totalPixels > 0) (matchingPixels.toDouble() / totalPixels) * 100.0 else 100.0
        val meanDiff = sumDiff.map { if (mismatchCount > 0) it.toDouble() / mismatchCount else 0.0 }.toDoubleArray()
        val isPassing = similarity >= minSimilarity

        return ComparisonResult(
            similarity = similarity,
            totalPixels = totalPixels,
            matchingPixels = matchingPixels,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            diffRgba = if (mismatchCount > 0) diffRgba else null,
            isPassing = isPassing,
            minSimilarity = minSimilarity,
        )
    }

    fun rgbaToBufferedImage(rgba: ByteArray, width: Int, height: Int): BufferedImage {
        require(rgba.size == width * height * BYTES_PER_PIXEL) { "RGBA buffer size mismatch" }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = (y * width + x) * BYTES_PER_PIXEL
                val r = rgba[i].toInt() and 0xFF
                val g = rgba[i + 1].toInt() and 0xFF
                val b = rgba[i + 2].toInt() and 0xFF
                val a = rgba[i + 3].toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    fun saveRgbaAsPng(rgba: ByteArray, width: Int, height: Int, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val image = rgbaToBufferedImage(rgba, width, height)
        ImageIO.write(image, "png", outputFile)
    }

    fun bufferedImageToRgba(image: BufferedImage): ByteArray {
        val width = image.width
        val height = image.height
        val rgba = ByteArray(width * height * BYTES_PER_PIXEL)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = image.getRGB(x, y)
                val i = (y * width + x) * BYTES_PER_PIXEL
                rgba[i] = ((rgb shr 16) and 0xFF).toByte()
                rgba[i + 1] = ((rgb shr 8) and 0xFF).toByte()
                rgba[i + 2] = (rgb and 0xFF).toByte()
                rgba[i + 3] = ((rgb shr 24) and 0xFF).toByte()
            }
        }
        return rgba
    }

    fun computeSSIM(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
    ): Double {
        val blockSize = 16
        val C1 = (0.01 * 255.0).let { it * it }
        val C2 = (0.03 * 255.0).let { it * it }

        fun luminance(r: Int, g: Int, b: Int): Double =
            0.299 * r + 0.587 * g + 0.114 * b

        val blocksX = width / blockSize
        val blocksY = height / blockSize
        if (blocksX == 0 || blocksY == 0) return 1.0

        var totalSSIM = 0.0
        var blockCount = 0

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val n = blockSize * blockSize
                var sumX = 0.0
                var sumY = 0.0
                var sumXX = 0.0
                var sumYY = 0.0
                var sumXY = 0.0

                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                        val i = px * 4
                        val ar = actual[i].toInt() and 0xFF
                        val ag = actual[i + 1].toInt() and 0xFF
                        val ab = actual[i + 2].toInt() and 0xFF
                        val rr = reference[i].toInt() and 0xFF
                        val rg = reference[i + 1].toInt() and 0xFF
                        val rb = reference[i + 2].toInt() and 0xFF

                        val lx = luminance(ar, ag, ab)
                        val ly = luminance(rr, rg, rb)
                        sumX += lx; sumY += ly
                        sumXX += lx * lx; sumYY += ly * ly
                        sumXY += lx * ly
                    }
                }

                val meanX = sumX / n
                val meanY = sumY / n
                val varX = sumXX / n - meanX * meanX
                val varY = sumYY / n - meanY * meanY
                val covXY = sumXY / n - meanX * meanY

                val numerator = (2.0 * meanX * meanY + C1) * (2.0 * covXY + C2)
                val denominator = (meanX * meanX + meanY * meanY + C1) * (varX + varY + C2)
                val ssim = if (denominator > 0.0) numerator / denominator else 1.0
                totalSSIM += ssim
                blockCount++
            }
        }

        return if (blockCount > 0) totalSSIM / blockCount else 1.0
    }

    fun computeSSIMBlocks(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        blockSize: Int = 16,
    ): List<SsimBlock> {
        val C1 = (0.01 * 255.0).let { it * it }
        val C2 = (0.03 * 255.0).let { it * it }
        fun lum(r: Int, g: Int, b: Int): Double = 0.299 * r + 0.587 * g + 0.114 * b

        val blocksX = width / blockSize
        val blocksY = height / blockSize
        if (blocksX == 0 || blocksY == 0) return emptyList()

        val results = mutableListOf<SsimBlock>()
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val n = blockSize * blockSize
                var sumX = 0.0; var sumY = 0.0; var sumXX = 0.0; var sumYY = 0.0; var sumXY = 0.0
                for (dy in 0 until blockSize) {
                    for (dx in 0 until blockSize) {
                        val px = (by * blockSize + dy) * width + (bx * blockSize + dx)
                        val i = px * 4
                        val lx = lum(actual[i].toInt() and 0xFF, actual[i + 1].toInt() and 0xFF, actual[i + 2].toInt() and 0xFF)
                        val ly = lum(reference[i].toInt() and 0xFF, reference[i + 1].toInt() and 0xFF, reference[i + 2].toInt() and 0xFF)
                        sumX += lx; sumY += ly; sumXX += lx * lx; sumYY += ly * ly; sumXY += lx * ly
                    }
                }
                val mx = sumX / n; val my = sumY / n
                val vx = sumXX / n - mx * mx; val vy = sumYY / n - my * my; val cv = sumXY / n - mx * my
                val num = (2.0 * mx * my + C1) * (2.0 * cv + C2)
                val den = (mx * mx + my * my + C1) * (vx + vy + C2)
                val s = if (den > 0.0) num / den else 1.0
                results.add(SsimBlock(x = bx * blockSize, y = by * blockSize, score = s))
            }
        }
        return results
    }
}

data class SsimBlock(val x: Int, val y: Int, val score: Double)
