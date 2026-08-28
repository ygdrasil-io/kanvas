package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Independent exact-area oracle for the bounded scalar-AA rectangle route.
 *
 * The product route first quantizes every opaque SrcOver draw into an RGBA8
 * target, then samples that target for the next draw.  The oracle preserves
 * that observable per-draw quantization without sharing GPU implementation
 * code or shader functions.
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

    override fun render(width: Int, height: Int): ByteArray {
        val output = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val color = background.copyOf()
            rectangles.forEach { rectangle ->
                val coverage = coverage(rectangle.bounds, x, y) * (clip?.let { coverage(it, x, y) } ?: 1f)
                if (coverage == 0f) return@forEach
                repeat(3) { channel ->
                    color[channel] = (color[channel] + coverage * (rectangle.rgba[channel] - color[channel]))
                        .roundToInt().coerceIn(0, 255)
                }
            }
            val offset = (y * width + x) * 4
            repeat(4) { channel -> output[offset + channel] = color[channel].toByte() }
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
