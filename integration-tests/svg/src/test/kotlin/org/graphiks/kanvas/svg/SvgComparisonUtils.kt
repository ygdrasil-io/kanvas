package org.graphiks.kanvas.svg

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Utilitaires pour comparer des images RGBA et générer des rapports détaillés.
 */
object SvgComparisonUtils {
    private const val BYTES_PER_PIXEL = 4

    /**
     * Résultat de la comparaison entre deux images.
     */
    data class ComparisonResult(
        val similarity: Double,
        val totalPixels: Int,
        val matchingPixels: Int,
        val maxDiff: IntArray,
        val meanDiff: DoubleArray,
        val diffRgba: ByteArray?,
        val isPassing: Boolean,
        val minSimilarity: Double
    )

    /**
     * Compare deux buffers RGBA pixel par pixel.
     * @param actual Buffer RGBA du rendu Kanvas
     * @param reference Buffer RGBA de la référence
     * @param width Largeur en pixels
     * @param height Hauteur en pixels
     * @param tolerance Tolérance par canal (0-255)
     * @param minSimilarity Seuil de similarité minimale (0-100)
     */
    fun compareRgba(
        actual: ByteArray,
        reference: ByteArray,
        width: Int,
        height: Int,
        tolerance: Int = 0,
        minSimilarity: Double = 100.0
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
            minSimilarity = minSimilarity
        )
    }

    /**
     * Convertit une BufferedImage en ByteArray RGBA.
     */
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

    /**
     * Convertit un ByteArray RGBA en BufferedImage.
     */
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

    /**
     * Sauvegarde un ByteArray RGBA en PNG.
     */
    fun saveRgbaAsPng(rgba: ByteArray, width: Int, height: Int, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val image = rgbaToBufferedImage(rgba, width, height)
        ImageIO.write(image, "png", outputFile)
    }
}
