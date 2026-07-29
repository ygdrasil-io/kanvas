package org.graphiks.kanvas.surface.gpu

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * CPU reference oracles for prepared composite filter tests.
 *
 * These oracles do NOT call any production planner, WGSL helper,
 * or filter executor. They reimplement the expected pipeline purely
 * from scalar math so filter correctness can be validated independently.
 */
object GPUPreparedFilterCpuOracle {

    fun sampleLinearRGBA(
        rgba: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): IntArray {
        val xf = min(max(u * width - 0.5f, 0f), width - 1.001f)
        val yf = min(max(v * height - 0.5f, 0f), height - 1.001f)
        val x0 = xf.toInt()
        val y0 = yf.toInt()
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)
        val fx = xf - x0
        val fy = yf - y0
        val p00 = sampleRGBA(rgba, width, x0, y0)
        val p10 = sampleRGBA(rgba, width, x1, y0)
        val p01 = sampleRGBA(rgba, width, x0, y1)
        val p11 = sampleRGBA(rgba, width, x1, y1)
        return IntArray(4) { c ->
            val v00 = p00[c]
            val v10 = p10[c]
            val v01 = p01[c]
            val v11 = p11[c]
            (v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy)
                .toInt().coerceIn(0, 255)
        }
    }

    fun sampleNearestRGBA(
        rgba: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): IntArray {
        val x = min(max((u * width).toInt(), 0), width - 1)
        val y = min(max((v * height).toInt(), 0), height - 1)
        return sampleRGBA(rgba, width, x, y)
    }

    fun sampleNearestA8(
        a8: ByteArray,
        width: Int,
        height: Int,
        u: Float,
        v: Float,
    ): Int {
        val x = min(max((u * width).toInt(), 0), width - 1)
        val y = min(max((v * height).toInt(), 0), height - 1)
        return a8[y * width + x].toInt() and 0xFF
    }

    fun srgbToLinear(c: Int): Float {
        val normalized = (c and 0xFF) / 255f
        return if (normalized <= 0.04045f) {
            normalized / 12.92f
        } else {
            ((normalized + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    fun linearToSrgb(l: Float): Int {
        val clamped = l.coerceIn(0f, 1f)
        val srgb = if (clamped <= 0.0031308f) {
            clamped * 12.92f
        } else {
            1.055f * clamped.pow(1f / 2.4f) - 0.055f
        }
        return (srgb * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    fun applySrgbPipelineOrder(
        sample: IntArray,
        tint: FloatArray,
    ): IntArray {
        val rLinear = srgbToLinear(sample[0])
        val gLinear = srgbToLinear(sample[1])
        val bLinear = srgbToLinear(sample[2])
        val aUnorm = (sample[3] and 0xFF) / 255f
        val premulR = rLinear * aUnorm * tint[0]
        val premulG = gLinear * aUnorm * tint[1]
        val premulB = bLinear * aUnorm * tint[2]
        val premulA = aUnorm * tint.getOrElse(3) { 1f }
        return intArrayOf(
            linearToSrgb(premulR),
            linearToSrgb(premulG),
            linearToSrgb(premulB),
            (premulA * 255f + 0.5f).toInt().coerceIn(0, 255),
        )
    }

    fun computeMaxChannelDelta(
        expected: ByteArray,
        actual: ByteArray,
        width: Int,
        height: Int,
    ): Int {
        var maxDelta = 0
        for (i in 0 until width * height * 4) {
            val d = kotlin.math.abs((expected[i].toInt() and 0xFF) - (actual[i].toInt() and 0xFF))
            if (d > maxDelta) maxDelta = d
        }
        return maxDelta
    }

    fun assertWithinOneLsb(
        expected: ByteArray,
        actual: ByteArray,
        width: Int,
        height: Int,
    ): Boolean {
        return computeMaxChannelDelta(expected, actual, width, height) <= 1
    }

    private fun sampleRGBA(rgba: ByteArray, width: Int, x: Int, y: Int): IntArray {
        val offset = (y * width + x) * 4
        return intArrayOf(
            rgba[offset].toInt() and 0xFF,
            rgba[offset + 1].toInt() and 0xFF,
            rgba[offset + 2].toInt() and 0xFF,
            rgba[offset + 3].toInt() and 0xFF,
        )
    }
}
