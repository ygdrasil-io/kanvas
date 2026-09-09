package org.graphiks.kanvas.surface

/**
 * Deliberately small CPU reference for the public W4e pixel fixtures.
 *
 * It owns its geometry sampling, ordered clip folds, 8-bit coverage quantization and
 * premultiplied SrcOver arithmetic; it does not depend on a plan, renderer, or production helper.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4eClipCpuOracle {
    enum class ClipOperation { Intersect, Difference }

    enum class ChannelOrder { RGBA, BGRA }

    data class Rgba8(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
        init {
            require(listOf(red, green, blue, alpha).all { it in 0..255 })
        }
    }

    data class Point(val x: Double, val y: Double)

    sealed interface Shape {
        data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) : Shape
        data class RRect(val rect: Rect, val radiusX: Double, val radiusY: Double) : Shape
        data class Polygon(val points: List<Point>) : Shape {
            init { require(points.size >= 3) }
        }
        data class Inverse(val interior: Shape) : Shape
    }

    data class Clip(val shape: Shape, val operation: ClipOperation, val antiAlias: Boolean)
    data class Draw(val shape: Shape, val color: Rgba8, val antiAlias: Boolean, val clips: List<Clip>)

    fun render(width: Int, height: Int, draws: List<Draw>, channelOrder: ChannelOrder = ChannelOrder.RGBA): UByteArray {
        require(width > 0 && height > 0)
        val pixels = IntArray(width * height * 4)
        draws.forEach { draw ->
            for (y in 0 until height) for (x in 0 until width) {
                var clipCoverage = 255
                draw.clips.forEach { clip ->
                    val coverage = coverage8(x, y, clip.shape, clip.antiAlias)
                    clipCoverage = when (clip.operation) {
                        ClipOperation.Intersect -> multiply8(clipCoverage, coverage)
                        ClipOperation.Difference -> multiply8(clipCoverage, 255 - coverage)
                    }
                }
                val coverage = multiply8(clipCoverage, coverage8(x, y, draw.shape, draw.antiAlias))
                if (coverage != 0) srcOver(pixels, (y * width + x) * 4, draw.color, coverage)
            }
        }
        return UByteArray(pixels.size) { index ->
            val pixel = index / 4
            val channel = index % 4
            val base = pixel * 4
            when (channelOrder) {
                ChannelOrder.RGBA -> pixels[base + channel].toUByte()
                ChannelOrder.BGRA -> when (channel) {
                    0 -> pixels[base + 2].toUByte()
                    1 -> pixels[base + 1].toUByte()
                    2 -> pixels[base].toUByte()
                    else -> pixels[base + 3].toUByte()
                }
            }
        }
    }

    private fun coverage8(x: Int, y: Int, shape: Shape, antiAlias: Boolean): Int {
        if (!antiAlias) return if (contains(shape, x + 0.5, y + 0.5)) 255 else 0
        var covered = 0
        for (sampleY in 0 until 8) for (sampleX in 0 until 8) {
            if (contains(shape, x + (sampleX + 0.5) / 8.0, y + (sampleY + 0.5) / 8.0)) covered += 1
        }
        return ((covered * 255) + 32) / 64
    }

    private fun contains(shape: Shape, x: Double, y: Double): Boolean = when (shape) {
        is Shape.Rect -> x >= shape.left && x < shape.right && y >= shape.top && y < shape.bottom
        is Shape.RRect -> containsRRect(shape, x, y)
        is Shape.Polygon -> containsPolygon(shape.points, x, y)
        is Shape.Inverse -> !contains(shape.interior, x, y)
    }

    private fun containsRRect(shape: Shape.RRect, x: Double, y: Double): Boolean {
        val rect = shape.rect
        if (x < rect.left || x >= rect.right || y < rect.top || y >= rect.bottom) return false
        val radiusX = shape.radiusX.coerceAtMost((rect.right - rect.left) / 2.0)
        val radiusY = shape.radiusY.coerceAtMost((rect.bottom - rect.top) / 2.0)
        if (radiusX == 0.0 || radiusY == 0.0) return true
        val centerX = x.coerceIn(rect.left + radiusX, rect.right - radiusX)
        val centerY = y.coerceIn(rect.top + radiusY, rect.bottom - radiusY)
        val dx = (x - centerX) / radiusX
        val dy = (y - centerY) / radiusY
        return dx * dx + dy * dy <= 1.0
    }

    private fun containsPolygon(points: List<Point>, x: Double, y: Double): Boolean {
        var inside = false
        points.indices.forEach { index ->
            val a = points[index]
            val b = points[(index + points.size - 1) % points.size]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
        }
        return inside
    }

    private fun multiply8(a: Int, b: Int): Int = ((a * b) + 127) / 255

    private fun srcOver(pixels: IntArray, offset: Int, color: Rgba8, coverage: Int) {
        val sourceAlpha = multiply8(color.alpha, coverage)
        val sourceRed = multiply8(color.red, sourceAlpha)
        val sourceGreen = multiply8(color.green, sourceAlpha)
        val sourceBlue = multiply8(color.blue, sourceAlpha)
        val inverseAlpha = 255 - sourceAlpha
        pixels[offset] = (sourceRed + multiply8(pixels[offset], inverseAlpha)).coerceAtMost(255)
        pixels[offset + 1] = (sourceGreen + multiply8(pixels[offset + 1], inverseAlpha)).coerceAtMost(255)
        pixels[offset + 2] = (sourceBlue + multiply8(pixels[offset + 2], inverseAlpha)).coerceAtMost(255)
        pixels[offset + 3] = (sourceAlpha + multiply8(pixels[offset + 3], inverseAlpha)).coerceAtMost(255)
    }
}
