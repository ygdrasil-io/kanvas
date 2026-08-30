package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal data class ArcEndpointF64(
    val start: Point2F64,
    val end: Point2F64,
    val radius: Vector2F64,
    val xAxisRotationDegrees: Double,
    val largeArc: Boolean,
    val sweep: Boolean,
)

internal data class ArcCenterF64(
    val center: Point2F64,
    val radiusX: Double,
    val radiusY: Double,
    val rotationRadians: Double,
    val startAngle: Double,
    val sweepAngle: Double,
) {
    fun pointAt(t: Double): Point2F64 {
        val angle = startAngle + sweepAngle * t
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val cosRotation = cos(rotationRadians)
        val sinRotation = sin(rotationRadians)
        return Point2F64(
            center.x + radiusX * cosRotation * cosAngle - radiusY * sinRotation * sinAngle,
            center.y + radiusX * sinRotation * cosAngle + radiusY * cosRotation * sinAngle,
        )
    }

    fun derivativeAt(t: Double): Vector2F64 {
        val angle = startAngle + sweepAngle * t
        val cosAngle = cos(angle)
        val sinAngle = sin(angle)
        val cosRotation = cos(rotationRadians)
        val sinRotation = sin(rotationRadians)
        return Vector2F64(
            (-radiusX * cosRotation * sinAngle - radiusY * sinRotation * cosAngle) * sweepAngle,
            (-radiusX * sinRotation * sinAngle + radiusY * cosRotation * cosAngle) * sweepAngle,
        )
    }

    fun extrema(): List<Point2F64> {
        val xAngle = atan2(-radiusY * sin(rotationRadians), radiusX * cos(rotationRadians))
        val yAngle = atan2(radiusY * cos(rotationRadians), radiusX * sin(rotationRadians))
        return buildList {
            add(pointAt(0.0))
            add(pointAt(1.0))
            listOf(xAngle, xAngle + PI, yAngle, yAngle + PI)
                .filter(::isInSweep)
                .forEach { angle -> add(pointAt(parameterAt(angle))) }
        }
    }

    private fun isInSweep(angle: Double): Boolean {
        val distance = if (sweepAngle >= 0.0) {
            positiveAngle(angle - startAngle)
        } else {
            positiveAngle(startAngle - angle)
        }
        return distance <= abs(sweepAngle) + 1e-12
    }

    private fun parameterAt(angle: Double): Double {
        val distance = if (sweepAngle >= 0.0) {
            positiveAngle(angle - startAngle)
        } else {
            -positiveAngle(startAngle - angle)
        }
        return if (sweepAngle == 0.0) 0.0 else distance / sweepAngle
    }
}

internal fun arcCenterF64(arc: ArcEndpointF64): ArcCenterF64? {
    var radiusX = abs(arc.radius.x)
    var radiusY = abs(arc.radius.y)
    if (!arc.start.isFinite() || !arc.end.isFinite() || !radiusX.isFinite() || !radiusY.isFinite() ||
        !arc.xAxisRotationDegrees.isFinite() || radiusX == 0.0 || radiusY == 0.0 || arc.start == arc.end
    ) return null

    val rotationDegrees = arc.xAxisRotationDegrees % 360.0
    val rotationRadians = rotationDegrees * PI / 180.0
    val cosRotation = cos(rotationRadians)
    val sinRotation = sin(rotationRadians)
    val halfDeltaX = (arc.start.x - arc.end.x) * 0.5
    val halfDeltaY = (arc.start.y - arc.end.y) * 0.5
    val startX = cosRotation * halfDeltaX + sinRotation * halfDeltaY
    val startY = -sinRotation * halfDeltaX + cosRotation * halfDeltaY
    val lambda = startX * startX / (radiusX * radiusX) + startY * startY / (radiusY * radiusY)
    if (!lambda.isFinite()) return null
    if (lambda > 1.0) {
        val correction = sqrt(lambda)
        radiusX *= correction
        radiusY *= correction
    }

    val radiusXSquared = radiusX * radiusX
    val radiusYSquared = radiusY * radiusY
    val startXSquared = startX * startX
    val startYSquared = startY * startY
    val denominator = radiusXSquared * startYSquared + radiusYSquared * startXSquared
    val numerator = radiusXSquared * radiusYSquared - radiusXSquared * startYSquared - radiusYSquared * startXSquared
    val sign = if (arc.largeArc == arc.sweep) -1.0 else 1.0
    val factor = if (denominator == 0.0) 0.0 else sign * sqrt(max(0.0, numerator / denominator))
    val centerXPrime = factor * radiusX * startY / radiusY
    val centerYPrime = -factor * radiusY * startX / radiusX
    val center = Point2F64(
        cosRotation * centerXPrime - sinRotation * centerYPrime + (arc.start.x + arc.end.x) * 0.5,
        sinRotation * centerXPrime + cosRotation * centerYPrime + (arc.start.y + arc.end.y) * 0.5,
    )
    val startAngle = atan2((startY - centerYPrime) / radiusY, (startX - centerXPrime) / radiusX)
    val endAngle = atan2((-startY - centerYPrime) / radiusY, (-startX - centerXPrime) / radiusX)
    var sweepAngle = endAngle - startAngle
    if (!arc.sweep && sweepAngle > 0.0) sweepAngle -= 2.0 * PI
    if (arc.sweep && sweepAngle < 0.0) sweepAngle += 2.0 * PI
    return ArcCenterF64(center, radiusX, radiusY, rotationRadians, startAngle, sweepAngle)
}

private fun positiveAngle(angle: Double): Double {
    val result = angle % (2.0 * PI)
    return if (result < 0.0) result + 2.0 * PI else result
}
