package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.exp

/** Fixture-scoped, independently quantized normal mask blur oracle. */
class SurfaceSrgbSeparableMaskBlurCpuOracle : CpuOracle {
    data class BlurStages(
        val mask: List<Double>,
        val horizontal: List<Double>,
        val vertical: List<Double>,
        val style: List<Double>,
    )

    companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
        const val SIGMA = 3.0
        val SOURCE_BOUNDS = SurfaceSrgbOracleMath.PixelRect(16, 16, 48, 48)
        val LOCAL_FRAME = SurfaceSrgbOracleMath.PixelRect(7, 7, 57, 57)
        private val SOURCE_RGBA = intArrayOf(46, 107, 194, 255)
    }

    val localFrame: SurfaceSrgbOracleMath.PixelRect get() = LOCAL_FRAME
    private val weights = gaussianWeights()
    fun kernelWeights(): DoubleArray = weights.copyOf()

    fun sampleMask(x: Int, y: Int): Double = if (SOURCE_BOUNDS.contains(x, y)) 1.0 else 0.0

    fun stages(): BlurStages {
        val mask = DoubleArray(WIDTH * HEIGHT) { index -> SurfaceSrgbOracleMath.q8(sampleMask(index % WIDTH, index / WIDTH)) / 255.0 }
        val horizontal = DoubleArray(mask.size)
        val vertical = DoubleArray(mask.size)
        val radius = weights.size / 2
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            horizontal[y * WIDTH + x] = quantizedConvolution(mask, WIDTH, HEIGHT, x, y, radius, horizontalPass = true)
        }
        for (y in 0 until HEIGHT) for (x in 0 until WIDTH) {
            vertical[y * WIDTH + x] = quantizedConvolution(horizontal, WIDTH, HEIGHT, x, y, radius, horizontalPass = false)
        }
        val style = DoubleArray(vertical.size) { SurfaceSrgbOracleMath.q8(vertical[it]) / 255.0 }
        return BlurStages(mask.toList(), horizontal.toList(), vertical.toList(), style.toList())
    }

    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        val style = stages().style
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val coverage = style[y * width + x]
            val offset = (y * width + x) * 4
            for (channel in 0..2) {
                val sourceLinear = SurfaceSrgbOracleMath.srgbToLinear(SOURCE_RGBA[channel] / 255.0)
                output[offset + channel] = SurfaceSrgbOracleMath.q8(SurfaceSrgbOracleMath.linearToSrgb(sourceLinear * coverage)).toByte()
            }
            output[offset + 3] = SurfaceSrgbOracleMath.q8(coverage).toByte()
        }
        return output
    }

    private fun quantizedConvolution(
        input: DoubleArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int,
        horizontalPass: Boolean,
    ): Double {
        var value = 0.0
        for (tap in weights.indices) {
            val sampleX = if (horizontalPass) x + tap - radius else x
            val sampleY = if (horizontalPass) y else y + tap - radius
            if (sampleX in 0 until width && sampleY in 0 until height) value += input[sampleY * width + sampleX] * weights[tap]
        }
        return SurfaceSrgbOracleMath.q8(value) / 255.0
    }

    private fun gaussianWeights(): DoubleArray {
        val raw = DoubleArray(7) { index ->
            val distance = (index - 3).toDouble()
            exp(-(distance * distance) / (2.0 * SIGMA * SIGMA))
        }
        val sum = raw.sum()
        return DoubleArray(raw.size) { raw[it] / sum }
    }
}
