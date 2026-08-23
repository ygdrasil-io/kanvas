package semanticfixtures

import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

fun pointAssignedAsVector(matrix: Matrix3x3F32, point: Point2F32) {
    val illegal: Vector2F32 = matrix.transform(point)
}
