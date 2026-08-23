package semanticfixtures

import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.times

fun allowed(
    p1: Point2F32,
    p2: Point2F32,
    v: Vector2F32,
    matrix: Matrix3x3F32,
) {
    val moved: Point2F32 = p1 + v
    val delta: Vector2F32 = p2 - p1
    val scaled: Vector2F32 = 2f * v
    val transformedPoint: Point2F32 = matrix * p1
    val transformedVector: Vector2F32 = matrix * v
    val correspondence: Matrix3x3F32? = Matrix3x3F32.polyToPoly(
        arrayOf(p1, p2),
        arrayOf(p2, p1),
    )
}
