package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.pow
import kotlin.math.roundToInt

/** Local double-precision math for the two sRGB Surface evidence fixtures. */
object SurfaceSrgbOracleMath {
    data class LinearPremul(val red: Double, val green: Double, val blue: Double, val alpha: Double) {
        init {
            require(listOf(red, green, blue, alpha).all { it.isFinite() && it >= 0.0 }) { "linear premul values must be finite and non-negative" }
            require(alpha <= 1.0 && red <= alpha && green <= alpha && blue <= alpha) { "linear premul values must be normalized" }
        }

        fun srcOver(source: LinearPremul): LinearPremul {
            val inverseSourceAlpha = 1.0 - source.alpha
            return LinearPremul(
                source.red + red * inverseSourceAlpha,
                source.green + green * inverseSourceAlpha,
                source.blue + blue * inverseSourceAlpha,
                source.alpha + alpha * inverseSourceAlpha,
            )
        }
    }

    data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        init {
            require(left <= right && top <= bottom) { "invalid pixel rect" }
        }

        val width: Int get() = right - left
        val height: Int get() = bottom - top
        fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
    }

    fun srgbToLinear(encoded: Double): Double {
        require(encoded.isFinite()) { "sRGB value must be finite" }
        val value = encoded.coerceIn(0.0, 1.0)
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    fun linearToSrgb(linear: Double): Double {
        require(linear.isFinite()) { "linear value must be finite" }
        val value = linear.coerceIn(0.0, 1.0)
        return if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055
    }

    fun q8(value: Double): Int = (value.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)

    fun decodeStraight(rgba: IntArray): LinearPremul {
        require(rgba.size == 4 && rgba.all { it in 0..255 }) { "RGBA must contain four unsigned bytes" }
        val alpha = rgba[3] / 255.0
        return LinearPremul(
            srgbToLinear(rgba[0] / 255.0) * alpha,
            srgbToLinear(rgba[1] / 255.0) * alpha,
            srgbToLinear(rgba[2] / 255.0) * alpha,
            alpha,
        )
    }

    fun srcOver(destination: LinearPremul, source: LinearPremul): LinearPremul = destination.srcOver(source)

    fun storeSrgb(color: LinearPremul): IntArray {
        if (color.alpha == 0.0) return intArrayOf(0, 0, 0, 0)
        return intArrayOf(
            q8(linearToSrgb(color.red)),
            q8(linearToSrgb(color.green)),
            q8(linearToSrgb(color.blue)),
            q8(color.alpha),
        )
    }

}
