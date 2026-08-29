package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.max
import kotlin.math.min

/**
 * Independent exact-area oracle for the bounded scalar-AA rectangle route.
 *
 * Coverage is applied to the premultiplied linear-light SrcOver result before
 * the final straight-sRGB readback encoding.  This is deliberately distinct
 * from interpolating the encoded bytes, which would visibly darken AA edges.
 */
class SurfaceSrgbFractionalRectCoverageCpuOracle(
    background: IntArray,
    private val rectangles: List<Rectangle>,
    private val clip: DeviceRect? = null,
) : CpuOracle {
    data class DeviceRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        init {
            require(listOf(left, top, right, bottom).all(Float::isFinite))
            require(right >= left && bottom >= top)
        }
    }

    data class Rectangle(val bounds: DeviceRect, val rgba: IntArray) {
        init {
            require(rgba.size == 4 && rgba.all { it in 0..255 })
            require(rgba[3] == 255) { "bounded fractional-AA oracle accepts opaque source rectangles only" }
        }
    }

    private val background = background.copyOf().also(::requireRgba)
    private val backgroundLinear = SurfaceSrgbOracleMath.decodeStraight(background)
    private val rectanglesLinear = rectangles.map { rectangle ->
        SurfaceSrgbOracleMath.decodeStraight(rectangle.rgba)
    }

    override fun render(width: Int, height: Int): ByteArray {
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            var color = backgroundLinear
            rectangles.forEachIndexed { index, rectangle ->
                val coverage = coverage(rectangle.bounds, x, y) * (clip?.let { coverage(it, x, y) } ?: 1f)
                if (coverage == 0f) return@forEachIndexed
                val source = rectanglesLinear[index]
                color = SurfaceSrgbOracleMath.LinearPremul(
                    red = color.red + coverage * (source.red - color.red),
                    green = color.green + coverage * (source.green - color.green),
                    blue = color.blue + coverage * (source.blue - color.blue),
                    alpha = color.alpha + coverage * (source.alpha - color.alpha),
                )
            }
            val offset = (y * width + x) * 4
            SurfaceSrgbOracleMath.storeSrgb(color).forEachIndexed { channel, value ->
                output[offset + channel] = value.toByte()
            }
        }
        return output
    }

    private fun coverage(bounds: DeviceRect, x: Int, y: Int): Float {
        val horizontal = max(0f, min(x + 1f, bounds.right) - max(x.toFloat(), bounds.left))
        val vertical = max(0f, min(y + 1f, bounds.bottom) - max(y.toFloat(), bounds.top))
        return horizontal * vertical
    }

    private fun requireRgba(color: IntArray) {
        require(color.size == 4 && color.all { it in 0..255 }) { "RGBA color must have four byte channels" }
        require(color[3] == 255) { "bounded fractional-AA oracle requires an opaque background" }
    }
}
