package org.graphiks.kanvas.surface

import kotlin.math.pow

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

    /**
     * Coverage contract selected by the fixture, rather than a Boolean that conflates
     * the producer's analytic grid with native path MSAA.
     *
     * [Analytic2x2] uses the producer shader's offsets around the pixel centre:
     * (-0.25, -0.25), (0.25, -0.25), (-0.25, 0.25), and (0.25, 0.25).
     * [PathMsaa4] deliberately has no fractional sample-position model: hardware path
     * MSAA locations are not a declared contract.  It is valid only for fixtures whose
     * relevant pixels are completely covered or completely empty.
     */
    enum class AA { Hard, Analytic2x2, PathMsaa4 }

    data class HomographyF64(
        val m00F64: Double = 1.0,
        val m01F64: Double = 0.0,
        val m02F64: Double = 0.0,
        val m10F64: Double = 0.0,
        val m11F64: Double = 1.0,
        val m12F64: Double = 0.0,
        val m20F64: Double = 0.0,
        val m21F64: Double = 0.0,
        val m22F64: Double = 1.0,
    ) {
        init {
            require(
                listOf(
                    m00F64, m01F64, m02F64,
                    m10F64, m11F64, m12F64,
                    m20F64, m21F64, m22F64,
                ).all { it.isFinite() },
            ) { "Homography entries must be finite." }
        }

        /** Maps a device sample through the inverse local-to-device projective transform. */
        fun inverseMap(deviceXF64: Double, deviceYF64: Double): Point? {
            val cofactor00F64 = m11F64 * m22F64 - m12F64 * m21F64
            val cofactor01F64 = m12F64 * m20F64 - m10F64 * m22F64
            val cofactor02F64 = m10F64 * m21F64 - m11F64 * m20F64
            val determinantF64 = m00F64 * cofactor00F64 + m01F64 * cofactor01F64 + m02F64 * cofactor02F64
            if (determinantF64 == 0.0 || !determinantF64.isFinite()) return null

            val inverse00F64 = cofactor00F64 / determinantF64
            val inverse01F64 = (m02F64 * m21F64 - m01F64 * m22F64) / determinantF64
            val inverse02F64 = (m01F64 * m12F64 - m02F64 * m11F64) / determinantF64
            val inverse10F64 = cofactor01F64 / determinantF64
            val inverse11F64 = (m00F64 * m22F64 - m02F64 * m20F64) / determinantF64
            val inverse12F64 = (m02F64 * m10F64 - m00F64 * m12F64) / determinantF64
            val inverse20F64 = cofactor02F64 / determinantF64
            val inverse21F64 = (m01F64 * m20F64 - m00F64 * m21F64) / determinantF64
            val inverse22F64 = (m00F64 * m11F64 - m01F64 * m10F64) / determinantF64
            val localWF64 = inverse20F64 * deviceXF64 + inverse21F64 * deviceYF64 + inverse22F64
            if (localWF64 == 0.0 || !localWF64.isFinite()) return null
            val localXF64 = (inverse00F64 * deviceXF64 + inverse01F64 * deviceYF64 + inverse02F64) / localWF64
            val localYF64 = (inverse10F64 * deviceXF64 + inverse11F64 * deviceYF64 + inverse12F64) / localWF64
            return if (localXF64.isFinite() && localYF64.isFinite()) Point(localXF64, localYF64) else null
        }

        companion object {
            val Identity = HomographyF64()

            fun translation(xF64: Double, yF64: Double): HomographyF64 = HomographyF64(
                m02F64 = xF64,
                m12F64 = yF64,
            )
        }
    }

    data class ScissorI32(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        init {
            require(left <= right && top <= bottom)
        }

        companion object {
            val Unbounded = ScissorI32(Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        }

        fun contains(xI32: Int, yI32: Int): Boolean =
            xI32 in left until right && yI32 in top until bottom
    }

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

    data class Clip(
        val shape: Shape,
        val operation: ClipOperation,
        val antiAlias: AA,
        val transformF64: HomographyF64 = HomographyF64.Identity,
    )

    data class Draw(
        val shape: Shape,
        val color: Rgba8,
        val antiAlias: AA,
        val clips: List<Clip>,
        val transformF64: HomographyF64 = HomographyF64.Identity,
        val scissorI32: ScissorI32 = ScissorI32.Unbounded,
    )

    fun render(width: Int, height: Int, draws: List<Draw>, channelOrder: ChannelOrder = ChannelOrder.RGBA): UByteArray {
        require(width > 0 && height > 0)
        val pixels = IntArray(width * height * 4)
        draws.forEach { draw ->
            for (y in 0 until height) for (x in 0 until width) {
                if (!draw.scissorI32.contains(x, y)) continue
                var clipCoverage = 255
                draw.clips.forEach { clip ->
                    val coverage = coverage8(x, y, clip.shape, clip.antiAlias, clip.transformF64)
                    clipCoverage = when (clip.operation) {
                        ClipOperation.Intersect -> multiply8(clipCoverage, coverage)
                        ClipOperation.Difference -> multiply8(clipCoverage, 255 - coverage)
                    }
                }
                val coverage = multiply8(clipCoverage, coverage8(x, y, draw.shape, draw.antiAlias, draw.transformF64))
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

    private fun coverage8(
        x: Int,
        y: Int,
        shape: Shape,
        antiAlias: AA,
        transformF64: HomographyF64,
    ): Int {
        fun containsDevice(deviceXF64: Double, deviceYF64: Double): Boolean =
            transformF64.inverseMap(deviceXF64, deviceYF64)?.let { point -> contains(shape, point.x, point.y) } == true

        when (antiAlias) {
            AA.Hard,
            // This is only a binary fixture probe, not a claim about hardware MSAA positions.
            AA.PathMsaa4 -> return if (containsDevice(x + 0.5, y + 0.5)) 255 else 0
            AA.Analytic2x2 -> Unit
        }
        var covered = 0
        for (sampleY in listOf(0.25, 0.75)) for (sampleX in listOf(0.25, 0.75)) {
            if (containsDevice(x + sampleX, y + sampleY)) covered += 1
        }
        return (covered * 255 + 2) / 4
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
        val sourceAlpha = color.alpha / 255.0 * coverage / 255.0
        val inverseAlpha = 1.0 - sourceAlpha
        val destinationAlpha = pixels[offset + 3] / 255.0
        fun source(channel: Int): Double = srgbToLinear(channel / 255.0) * sourceAlpha
        fun destination(channel: Int): Double = srgbToLinear(pixels[offset + channel] / 255.0)
        pixels[offset] = encodeLinear(source(color.red) + destination(0) * inverseAlpha)
        pixels[offset + 1] = encodeLinear(source(color.green) + destination(1) * inverseAlpha)
        pixels[offset + 2] = encodeLinear(source(color.blue) + destination(2) * inverseAlpha)
        pixels[offset + 3] = ((sourceAlpha + destinationAlpha * inverseAlpha) * 255.0).toInt().coerceIn(0, 255)
    }

    private fun srgbToLinear(value: Double): Double =
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun encodeLinear(value: Double): Int {
        val encoded = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055
        return (encoded.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt()
    }
}
