package semanticfixtures

import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.times

fun vectorTimesVector(left: Vector2F32, right: Vector2F32) {
    val illegal = left * right
}
