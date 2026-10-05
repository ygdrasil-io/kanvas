package org.graphiks.kanvas.geometry

import org.graphiks.math.geometry.MutablePoint2F32
import org.graphiks.math.geometry.PathMeasureF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.MutableVector2F32

/** Mutable-output compatibility facade over [PathMeasureF32]. */
class PathMeasure(path: Path, val forceClosed: Boolean = false, @Suppress("UNUSED_PARAMETER") resScale: Float = 1f) {
    private val measure = PathMeasureF32(path.toPathF32(), forceClosed)

    val length: Float get() = measure.length

    val isClosed: Boolean get() = measure.isClosed

    fun getPosition(distance: Float, position: MutablePoint2F32?, tangent: MutableVector2F32?): Boolean {
        val location = measure.position(distance) ?: return false
        position?.let { it.x = location.point.x; it.y = location.point.y }
        tangent?.let { it.x = location.tangent.x; it.y = location.tangent.y }
        return true
    }

    fun getSegment(startD: Float, stopD: Float, dst: Path, startWithMoveTo: Boolean): Boolean {
        val segment = measure.segment(startD, stopD, startWithMoveTo) ?: return false
        dst.addPath(segment.toCompatibilityPath())
        return true
    }

    fun getMatrix(
        distance: Float,
        @Suppress("UNUSED_PARAMETER") matrix: Matrix3x3F32,
        @Suppress("UNUSED_PARAMETER") flags: Int = POSITION_MATRIX_FLAG or TANGENT_MATRIX_FLAG,
    ): Boolean = measure.position(distance) != null

    fun nextContour(): Boolean = measure.nextContour()

    companion object {
        const val POSITION_MATRIX_FLAG = 1
        const val TANGENT_MATRIX_FLAG = 2
    }
}
