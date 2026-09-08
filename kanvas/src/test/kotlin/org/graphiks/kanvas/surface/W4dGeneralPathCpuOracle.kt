package org.graphiks.kanvas.surface

import kotlin.math.pow
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathSegmentF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Independent pixel oracle for deliberately linear W4d.2 fixtures.
 *
 * It maps the original public path through a homogeneous F64 matrix, makes
 * coverage decisions in device space, and composites each draw itself.  It
 * intentionally does not reuse planner, renderer, flattening, or geometry
 * preparation code.  Fixtures keep edges away from sample positions, so an
 * AA draw has only 0 or 1 geometric coverage; the 0.5 case is a public source
 * alpha fixture and is independent of legal MSAA sample positions.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4dGeneralPathCpuOracle {
    internal data class Draw(
        val path: PathF32,
        val paint: Paint,
        val transform: Matrix3x3F32,
        val scissorI32: RectI32,
    )

    internal fun render(
        widthI32: Int,
        heightI32: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray {
        require(widthI32 > 0 && heightI32 > 0)
        val pixels = Array(widthI32 * heightI32) { LinearPremul.Transparent }
        draws.forEach { draw ->
            val contours = mapLinearContours(draw.path, draw.transform)
            val source = LinearPremul.from(draw.paint.color)
            for (yI32 in 0 until heightI32) {
                for (xI32 in 0 until widthI32) {
                    if (xI32 !in draw.scissorI32.left until draw.scissorI32.right ||
                        yI32 !in draw.scissorI32.top until draw.scissorI32.bottom
                    ) continue
                    val point = PointF64(xI32 + 0.5, yI32 + 0.5)
                    val covered = when (draw.paint.style) {
                        PaintStyle.FILL -> contains(contours, draw.path.fillRule, point)
                        PaintStyle.STROKE -> containsDeviceStroke(
                            contours = contours,
                            point = point,
                            halfWidthF64 = if (draw.paint.strokeWidth == 0f) 0.5 else draw.paint.strokeWidth / 2.0,
                        )
                        PaintStyle.STROKE_AND_FILL -> contains(contours, draw.path.fillRule, point) ||
                            (draw.paint.strokeWidth > 0f && containsDeviceStroke(contours, point, draw.paint.strokeWidth / 2.0))
                    }
                    if (!covered) continue
                    val indexI32 = yI32 * widthI32 + xI32
                    pixels[indexI32] = srcOver(source, pixels[indexI32]).quantizedForAttachment()
                }
            }
        }
        return UByteArray(widthI32 * heightI32 * 4).also { bytes ->
            pixels.forEachIndexed { pixelIndexI32, color ->
                val offsetI32 = pixelIndexI32 * 4
                val red = color.red.toSrgbByte()
                val green = color.green.toSrgbByte()
                val blue = color.blue.toSrgbByte()
                when (format) {
                    PixelFormat.RGBA8 -> {
                        bytes[offsetI32] = red
                        bytes[offsetI32 + 1] = green
                        bytes[offsetI32 + 2] = blue
                    }
                    PixelFormat.BGRA8 -> {
                        bytes[offsetI32] = blue
                        bytes[offsetI32 + 1] = green
                        bytes[offsetI32 + 2] = red
                    }
                }
                bytes[offsetI32 + 3] = color.alpha.toUByte()
            }
        }
    }

    private fun mapLinearContours(path: PathF32, matrix: Matrix3x3F32): List<List<PointF64>> {
        val contours = mutableListOf<List<PointF64>>()
        var points: MutableList<PointF64>? = null
        fun finish() {
            points?.takeIf { it.size >= 2 }?.let { contours += it.toList() }
            points = null
        }
        path.forEach { segment ->
            when (segment) {
                is PathSegmentF32.MoveTo -> {
                    finish()
                    points = mutableListOf(map(matrix, segment.point.x.toDouble(), segment.point.y.toDouble()))
                }
                is PathSegmentF32.LineTo -> {
                    val point = map(matrix, segment.point.x.toDouble(), segment.point.y.toDouble())
                    val value = requireNotNull(points) { "Linear fixture must begin with MoveTo." }
                    if (value.last() != point) value += point
                }
                PathSegmentF32.Close -> Unit
                is PathSegmentF32.QuadTo,
                is PathSegmentF32.CubicTo,
                is PathSegmentF32.ArcTo,
                -> error("W4d.2 Surface oracle accepts linear fixtures only")
            }
        }
        finish()
        return contours
    }

    private fun map(matrix: Matrix3x3F32, xF64: Double, yF64: Double): PointF64 {
        val wF64 = matrix.persp0.toDouble() * xF64 + matrix.persp1.toDouble() * yF64 + matrix.persp2.toDouble()
        require(wF64 != 0.0 && wF64.isFinite()) { "Fixture crosses a projective horizon." }
        return PointF64(
            (matrix.sx.toDouble() * xF64 + matrix.kx.toDouble() * yF64 + matrix.tx.toDouble()) / wF64,
            (matrix.ky.toDouble() * xF64 + matrix.sy.toDouble() * yF64 + matrix.ty.toDouble()) / wF64,
        )
    }

    private fun contains(contours: List<List<PointF64>>, rule: FillRule, point: PointF64): Boolean {
        require(rule == FillRule.WINDING || rule == FillRule.EVEN_ODD)
        var windingI32 = 0
        var crossingsI32 = 0
        contours.forEach { contour ->
            if (contour.size < 3) return@forEach
            contour.indices.forEach { indexI32 ->
                val first = contour[indexI32]
                val second = contour[(indexI32 + 1) % contour.size]
                if ((first.y <= point.y && second.y > point.y) || (second.y <= point.y && first.y > point.y)) {
                    val crossingXF64 = first.x + (point.y - first.y) * (second.x - first.x) / (second.y - first.y)
                    if (crossingXF64 > point.x) {
                        crossingsI32 += 1
                        windingI32 += if (second.y > first.y) 1 else -1
                    }
                }
            }
        }
        return if (rule == FillRule.WINDING) windingI32 != 0 else crossingsI32 % 2 != 0
    }

    private fun containsDeviceStroke(contours: List<List<PointF64>>, point: PointF64, halfWidthF64: Double): Boolean =
        contours.any { contour ->
            contour.zipWithNext().any { (start, end) ->
                val vector = end - start
                val lengthSquaredF64 = vector.dot(vector)
                if (lengthSquaredF64 == 0.0) false else {
                    val projectionF64 = (point - start).dot(vector) / lengthSquaredF64
                    projectionF64 in 0.0..1.0 &&
                        (point - start).dot(point - start) - projectionF64 * projectionF64 * lengthSquaredF64 <= halfWidthF64 * halfWidthF64
                }
            }
        }

    private fun srcOver(source: LinearPremul, destination: LinearPremul): LinearPremul {
        val inverseSourceAlpha = 1f - source.alpha
        return LinearPremul(
            source.red + destination.red * inverseSourceAlpha,
            source.green + destination.green * inverseSourceAlpha,
            source.blue + destination.blue * inverseSourceAlpha,
            source.alpha + destination.alpha * inverseSourceAlpha,
        )
    }

    private data class PointF64(val x: Double, val y: Double) {
        operator fun minus(other: PointF64): PointF64 = PointF64(x - other.x, y - other.y)
        fun dot(other: PointF64): Double = x * other.x + y * other.y
    }

    private data class LinearPremul(val red: Float, val green: Float, val blue: Float, val alpha: Float) {
        fun quantizedForAttachment(): LinearPremul = LinearPremul(
            red.toSrgbByte().toInt().srgbToLinear(),
            green.toSrgbByte().toInt().srgbToLinear(),
            blue.toSrgbByte().toInt().srgbToLinear(),
            alpha.toUByte().toInt() / 255f,
        )

        companion object {
            val Transparent = LinearPremul(0f, 0f, 0f, 0f)

            fun from(color: ColorARGB): LinearPremul {
                val alpha = color.alpha / 255f
                return LinearPremul(
                    color.red.srgbToLinear() * alpha,
                    color.green.srgbToLinear() * alpha,
                    color.blue.srgbToLinear() * alpha,
                    alpha,
                )
            }
        }
    }

    private fun Int.srgbToLinear(): Float {
        val encoded = this / 255f
        return if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun Float.toSrgbByte(): UByte {
        val linear = coerceIn(0f, 1f)
        val encoded = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (encoded * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()
    }

    private fun Float.toUByte(): UByte = (coerceIn(0f, 1f) * 255f + 0.5f).toInt().toUByte()
}
