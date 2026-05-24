package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurMask
import org.skia.foundation.SkBlurStyle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Port of upstream Skia `gm/blurrect.cpp::BlurRectCompareGM`.
 *
 * The GM renders three columns: brute-force gaussian reference masks,
 * the raster [SkBlurMask.BlurRect] result, and an amplified green
 * difference image. It intentionally keeps the upstream static sigma set;
 * viewer animation is not part of the screenshot test harness.
 */
public class BlurRectCompareGM : GM() {
    override fun getName(): String = "blurrect_compare"
    override fun getISize(): SkISize = SkISize.Make(900, 1220)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        c.translate(MARGIN, MARGIN)

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
            c.save()
            drawColumn(c, column)
            c.restore()
            c.translate(TOTAL_COLUMN_WIDTH + 2f * MARGIN, 0f)
        }
    }

    private fun drawColumn(canvas: SkCanvas, masks: Array<Array<Array<SkBitmap>>>) {
        for (sigmaIdx in SIGMAS.indices) {
            val pad = padForSigma(SIGMAS[sigmaIdx])
            for (heightIdx in SIZES.indices) {
                canvas.save()
                for (widthIdx in SIZES.indices) {
                    canvas.drawImage(masks[sigmaIdx][heightIdx][widthIdx].asImage(), -pad.toFloat(), -pad.toFloat())
                    canvas.translate(SIZES[widthIdx] + MARGIN, 0f)
                }
                canvas.restore()
                canvas.translate(0f, SIZES[heightIdx] + MARGIN)
            }
        }
    }

    private fun createReferenceMask(width: Int, height: Int, sigma: Float): SkBitmap {
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

        val bitmap = SkBitmap(maskW, maskH)
        val accums = FloatArray(maskW)
        val accumScale = 1f / (subpixels * subpixels)
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
                bitmap.setPixel(x, y, SkColorSetARGB(alpha, 255, 255, 255))
            }
        }
        return bitmap
    }

    private fun createActualMask(width: Int, height: Int, sigma: Float): SkBitmap {
        val mask = SkBlurMask.BlurRect(sigma, SkRect.MakeWH(width.toFloat(), height.toFloat()), SkBlurStyle.kNormal)
        val bitmap = SkBitmap(width + 2 * padForSigma(sigma), height + 2 * padForSigma(sigma))
        if (mask == null) return bitmap
        val copyW = minOf(bitmap.width, mask.fBounds.width())
        val copyH = minOf(bitmap.height, mask.fBounds.height())
        for (y in 0 until copyH) {
            for (x in 0 until copyW) {
                val alpha = mask.image[y * mask.fRowBytes + x].toInt() and 0xFF
                bitmap.setPixel(x, y, SkColorSetARGB(alpha, 255, 255, 255))
            }
        }
        return bitmap
    }

    private fun createDifferenceMask(reference: SkBitmap, actual: SkBitmap): SkBitmap {
        val bitmap = SkBitmap(reference.width, reference.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val refA = reference.getPixel(x, y).ushr(24) and 0xFF
                val actualA = actual.getPixel(x, y).ushr(24) and 0xFF
                val diff = (abs(actualA - refA) * 8).coerceIn(0, 255)
                bitmap.setPixel(x, y, SkColorSetARGB(255, 0, diff, 0))
            }
        }
        return bitmap
    }

    public companion object {
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
