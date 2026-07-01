package org.graphiks.kanvas.paint

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Matrix33

enum class Path1DStyle { TRANSLATE, ROTATE, MORPH }

sealed interface PathEffect {
    data class Dash(val intervals: FloatArray, val phase: Float = 0f) : PathEffect {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Dash) return false
            return intervals.contentEquals(other.intervals) && phase == other.phase
        }
        override fun hashCode(): Int = 31 * intervals.contentHashCode() + phase.hashCode()
    }
    data class Corner(val radius: Float) : PathEffect
    data class Discrete(val segmentLength: Float, val deviation: Float) : PathEffect
    data class Path1D(val path: Path, val advance: Float, val phase: Float, val style: Path1DStyle) : PathEffect
    data class Path2D(val matrix: Matrix33, val path: Path) : PathEffect
    data class Trim(val start: Float, val stop: Float) : PathEffect
}
