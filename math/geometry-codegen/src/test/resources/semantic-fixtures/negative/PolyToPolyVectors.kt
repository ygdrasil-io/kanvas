package semanticfixtures

import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.vector.Vector2F32

fun polyToPolyVectors(source: Array<Vector2F32>, destination: Array<Vector2F32>) {
    Matrix3x3F32.polyToPoly(source, destination)
}
