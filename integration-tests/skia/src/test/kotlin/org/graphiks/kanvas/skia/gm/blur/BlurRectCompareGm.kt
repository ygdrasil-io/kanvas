package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Port of upstream Skia `gm/blurrect.cpp::BlurRectCompareGM` (900 x 1220).
 *
 * Renders three columns: brute-force gaussian reference masks,
 * the MaskFilter.Blur result, and an amplified green difference image.
 * @see https://github.com/google/skia/blob/main/gm/blurrect.cpp
 */
class BlurRectCompareGm : SkiaGm {
    override val name = "blurrect_compare"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 79.7
    override val width = 900
    override val height = 1220

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        canvas.translate(MARGIN, MARGIN)

        val references = Array(SIGMAS.size) { sigmaIdx ->
            Array(SIZES.size) { heightIdx ->
                Array(SIZES.size) { widthIdx ->
                    createReferenceMask(SIZES[widthIdx], SIZES[heightIdx], SIGMAS[sigmaIdx])
                }
            }
        }
        val actuals = Array(SIGMAS.size) { sigmaIdx ->
            Array(SIZES.size) { heightIdx ->
                Array(SIZES.size) { widthIdx ->
                    createActualMask(SIZES[widthIdx], SIZES[heightIdx], SIGMAS[sigmaIdx])
                }
            }
        }
        val diffs = Array(SIGMAS.size) { sigmaIdx ->
            Array(SIZES.size) { heightIdx ->
                Array(SIZES.size) { widthIdx ->
                    createDifferenceMask(
                        references[sigmaIdx][heightIdx][widthIdx],
                        actuals[sigmaIdx][heightIdx][widthIdx],
                    )
                }
            }
        }

        val columns = arrayOf(references, actuals, diffs)
        for (column in columns) {
            canvas.save()
            drawColumn(canvas, column)
            canvas.restore()
            canvas.translate(TOTAL_COLUMN_WIDTH + 2f * MARGIN, 0f)
        }
    }

    private fun drawColumn(canvas: GmCanvas, masks: Array<Array<Array<Image>>>) {
        for (sigmaIdx in SIGMAS.indices) {
            val pad = padForSigma(SIGMAS[sigmaIdx])
            for (heightIdx in SIZES.indices) {
                canvas.save()
                for (widthIdx in SIZES.indices) {
                    val img = masks[sigmaIdx][heightIdx][widthIdx]
                    canvas.drawImage(
                        img,
                        Rect.fromXYWH(-pad.toFloat(), -pad.toFloat(), img.width.toFloat(), img.height.toFloat()),
                    )
                    canvas.translate(SIZES[widthIdx] + MARGIN, 0f)
                }
                canvas.restore()
                canvas.translate(0f, SIZES[heightIdx] + MARGIN)
            }
        }
    }

    private fun createReferenceMask(width: Int, height: Int, sigma: Float): Image {
        val pad = padForSigma(sigma)
        val maskW = width + 2 * pad
        val maskH = height + 2 * pad
        val subpixels = 8
        val scaledW = width * subpixels
        val scaledH = height * subpixels
        val scaledSigma = sigma * subpixels
        val scale = SQRT_1_2 / scaledSigma

        fun integralApprox(a: Float, b: Float): Float =
            0.5f * (erf(b * scale) - erf(a * scale))

        val row = FloatArray(maskW * subpixels)
        for (col in row.indices) {
            val ldiff = subpixels * pad - (col + 0.5f)
            val rdiff = ldiff + scaledW
            row[col] = integralApprox(ldiff, rdiff)
        }

        val accums = FloatArray(maskW)
        val accumScale = 1f / (subpixels * subpixels)
        val pixels = ByteArray(maskW * maskH)
        for (y in 0 until maskH) {
            accums.fill(0f)
            for (ys in 0 until subpixels) {
                val tdiff = subpixels * pad - (y * subpixels + ys + 0.5f)
                val bdiff = tdiff + scaledH
                val integral = integralApprox(tdiff, bdiff)
                for (x in 0 until maskW) {
                    for (xs in 0 until subpixels) {
                        accums[x] += integral * row[x * subpixels + xs]
                    }
                }
            }
            for (x in 0 until maskW) {
                val alpha = (255f * accums[x] * accumScale).roundToInt().coerceIn(0, 255)
                pixels[y * maskW + x] = alpha.toByte()
            }
        }
        return Image.fromPixels(maskW, maskH, pixels, ColorType.ALPHA_8, "ref-$width-$height-$sigma")
    }

    private fun createActualMask(width: Int, height: Int, sigma: Float): Image {
        val pad = padForSigma(sigma)
        val maskW = width + 2 * pad
        val maskH = height + 2 * pad

        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
        )

        val pixels = ByteArray(maskW * maskH)
        for (y in 0 until maskH) {
            for (x in 0 until maskW) {
                var alpha = 0f
                val samples = 4
                for (sy in 0 until samples) {
                    for (sx in 0 until samples) {
                        val fx = x.toFloat() + (sx.toFloat() + 0.5f) / samples
                        val fy = y.toFloat() + (sy.toFloat() + 0.5f) / samples
                        if (fx >= pad && fx < pad + width && fy >= pad && fy < pad + height) {
                            alpha += 1f
                        }
                    }
                }
                alpha /= (samples * samples).toFloat()

                val blurred = if (sigma > 0f) {
                    val kernelRadius = ceil(sigma * 3f).toInt()
                    var total = 0f
                    var weightSum = 0f
                    for (ky in -kernelRadius..kernelRadius) {
                        for (kx in -kernelRadius..kernelRadius) {
                            val gx = x + kx
                            val gy = y + ky
                            if (gx in 0 until maskW && gy in 0 until maskH) {
                                var inside = 0f
                                for (sy in 0 until samples) {
                                    for (sx in 0 until samples) {
                                        val fx = gx.toFloat() + (sx.toFloat() + 0.5f) / samples
                                        val fy = gy.toFloat() + (sy.toFloat() + 0.5f) / samples
                                        if (fx >= pad && fx < pad + width && fy >= pad && fy < pad + height) {
                                            inside += 1f
                                        }
                                    }
                                }
                                inside /= (samples * samples).toFloat()
                                val w = exp(-(kx * kx + ky * ky) / (2f * sigma * sigma))
                                total += inside * w
                                weightSum += w
                            }
                        }
                    }
                    total / weightSum
                } else {
                    alpha
                }

                val alphaByte = (blurred * 255f).toInt().coerceIn(0, 255)
                pixels[y * maskW + x] = alphaByte.toByte()
            }
        }
        return Image.fromPixels(maskW, maskH, pixels, ColorType.ALPHA_8, "actual-$width-$height-$sigma")
    }

    private fun createDifferenceMask(reference: Image, actual: Image): Image {
        val w = minOf(reference.width, actual.width)
        val h = minOf(reference.height, actual.height)
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val refA = reference.pixels?.getOrElse(y * reference.width + x) { 0 }?.toInt()?.and(0xFF) ?: 0
                val actualA = actual.pixels?.getOrElse(y * actual.width + x) { 0 }?.toInt()?.and(0xFF) ?: 0
                val diff = (abs(actualA - refA) * 8).coerceIn(0, 255)
                val i = (y * w + x) * 4
                pixels[i] = 0
                pixels[i + 1] = diff.toByte()
                pixels[i + 2] = 0
                pixels[i + 3] = (-1).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "diff")
    }

    private companion object {
        private const val MARGIN = 30f
        private val SIZES = intArrayOf(1, 2, 4, 8, 16, 32)
        private val SIGMAS = floatArrayOf(0.5f, 1.2f, 2.3f, 3.9f, 7.4f)
        private val TOTAL_COLUMN_WIDTH: Float = SIZES.sumOf { it }.toFloat() + SIZES.size * MARGIN
        private val SQRT_1_2 = sqrt(0.5f)

        private fun padForSigma(sigma: Float): Int = ceil(4f * sigma).toInt()

        private fun erf(x: Float): Float {
            val sign = if (x < 0f) -1f else 1f
            val ax = abs(x)
            val t = 1f / (1f + 0.3275911f * ax)
            val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t -
                0.284496736f) * t + 0.254829592f) * t * exp(-(ax * ax))
            return sign * y
        }
    }
}
