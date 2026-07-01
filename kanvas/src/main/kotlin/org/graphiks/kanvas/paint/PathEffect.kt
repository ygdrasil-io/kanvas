package org.graphiks.kanvas.paint

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
}
