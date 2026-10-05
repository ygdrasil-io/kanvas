package org.graphiks.math.geometry

import org.graphiks.math.vector.Vector2F64

/** Immutable source-curve evaluator retained by F64 stroke preparation. */
public sealed interface PathStrokePrimitiveF64 {
    public fun pointAtF64(parameterF64: Double): Point2F64

    public fun derivativeAtF64(parameterF64: Double): Vector2F64
}

/** A finite, ordered interval of an immutable source [PathStrokePrimitiveF64]. */
public data class PathStrokePrimitiveSpanF64(
    public val primitiveF64: PathStrokePrimitiveF64,
    public val startParameterF64: Double,
    public val endParameterF64: Double,
) {
    init {
        require(startParameterF64.isFinite() && endParameterF64.isFinite())
        require(startParameterF64 in 0.0..1.0)
        require(endParameterF64 in 0.0..1.0)
        require(startParameterF64 <= endParameterF64)
    }
}

internal data class PathStrokeLinePrimitiveF64(
    val startF64: Point2F64,
    val endF64: Point2F64,
) : PathStrokePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = Point2F64(
        interpolatedStrokeCoordinateF64(startF64.x, endF64.x, parameterF64),
        interpolatedStrokeCoordinateF64(startF64.y, endF64.y, parameterF64),
    )

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 = endF64 - startF64
}

internal data class PathStrokeQuadPrimitiveF64(
    val startF64: Point2F64,
    val controlF64: Point2F64,
    val endF64: Point2F64,
) : PathStrokePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 {
        val inverseF64 = 1.0 - parameterF64
        return Point2F64(
            startF64.x * inverseF64 * inverseF64 + controlF64.x * 2.0 * inverseF64 * parameterF64 +
                endF64.x * parameterF64 * parameterF64,
            startF64.y * inverseF64 * inverseF64 + controlF64.y * 2.0 * inverseF64 * parameterF64 +
                endF64.y * parameterF64 * parameterF64,
        )
    }

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 {
        val inverseF64 = 1.0 - parameterF64
        return Vector2F64(
            2.0 * inverseF64 * (controlF64.x - startF64.x) + 2.0 * parameterF64 * (endF64.x - controlF64.x),
            2.0 * inverseF64 * (controlF64.y - startF64.y) + 2.0 * parameterF64 * (endF64.y - controlF64.y),
        )
    }
}

internal data class PathStrokeCubicPrimitiveF64(
    val startF64: Point2F64,
    val control1F64: Point2F64,
    val control2F64: Point2F64,
    val endF64: Point2F64,
) : PathStrokePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 {
        val inverseF64 = 1.0 - parameterF64
        return Point2F64(
            startF64.x * inverseF64 * inverseF64 * inverseF64 +
                control1F64.x * 3.0 * inverseF64 * inverseF64 * parameterF64 +
                control2F64.x * 3.0 * inverseF64 * parameterF64 * parameterF64 +
                endF64.x * parameterF64 * parameterF64 * parameterF64,
            startF64.y * inverseF64 * inverseF64 * inverseF64 +
                control1F64.y * 3.0 * inverseF64 * inverseF64 * parameterF64 +
                control2F64.y * 3.0 * inverseF64 * parameterF64 * parameterF64 +
                endF64.y * parameterF64 * parameterF64 * parameterF64,
        )
    }

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 {
        val inverseF64 = 1.0 - parameterF64
        return Vector2F64(
            3.0 * inverseF64 * inverseF64 * (control1F64.x - startF64.x) +
                6.0 * inverseF64 * parameterF64 * (control2F64.x - control1F64.x) +
                3.0 * parameterF64 * parameterF64 * (endF64.x - control2F64.x),
            3.0 * inverseF64 * inverseF64 * (control1F64.y - startF64.y) +
                6.0 * inverseF64 * parameterF64 * (control2F64.y - control1F64.y) +
                3.0 * parameterF64 * parameterF64 * (endF64.y - control2F64.y),
        )
    }
}

internal data class PathStrokeSvgArcPrimitiveF64(
    val startF64: Point2F64,
    val endF64: Point2F64,
    val arcF64: ArcCenterF64?,
) : PathStrokePrimitiveF64 {
    override fun pointAtF64(parameterF64: Double): Point2F64 = arcF64?.pointAt(parameterF64) ?: Point2F64(
        interpolatedStrokeCoordinateF64(startF64.x, endF64.x, parameterF64),
        interpolatedStrokeCoordinateF64(startF64.y, endF64.y, parameterF64),
    )

    override fun derivativeAtF64(parameterF64: Double): Vector2F64 =
        arcF64?.derivativeAt(parameterF64) ?: endF64 - startF64
}

private fun interpolatedStrokeCoordinateF64(firstF64: Double, secondF64: Double, parameterF64: Double): Double =
    if ((firstF64 < 0.0) == (secondF64 < 0.0)) {
        firstF64 + (secondF64 - firstF64) * parameterF64
    } else {
        firstF64 * (1.0 - parameterF64) + secondF64 * parameterF64
    }
