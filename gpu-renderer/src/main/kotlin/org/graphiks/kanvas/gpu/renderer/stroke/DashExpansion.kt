package org.graphiks.kanvas.gpu.renderer.stroke

data class DashInterval(val length: Float, val isOn: Boolean)

data class DashExpansion(val intervals: List<DashInterval>) {
    companion object {
        fun expand(dashes: FloatArray, dashOffset: Float, pathLength: Float): DashExpansion {
            if (dashes.isEmpty() || pathLength <= 0f) return DashExpansion(emptyList())
            val intervals = mutableListOf<DashInterval>()
            var currentPos = -dashOffset
            var dashIdx = 0
            var isOn = true
            while (currentPos < pathLength) {
                val dashLen = dashes[dashIdx % dashes.size]
                val segmentStart = maxOf(currentPos, 0f)
                val segmentEnd = minOf(currentPos + dashLen, pathLength)
                val visibleLen = if (segmentEnd > segmentStart) segmentEnd - segmentStart else 0f
                if (visibleLen > 0f) {
                    intervals.add(DashInterval(length = visibleLen, isOn = isOn))
                }
                currentPos += dashLen
                isOn = !isOn
                dashIdx++
            }
            return DashExpansion(intervals)
        }
    }
}

enum class GPUDashClassification {
    SimpleRepeat,
    ComplexPattern,
    UnsupportedLength,
}

data class GPUComplexDashPlan(
    val dashArray: FloatArray,
    val dashPhase: Float,
    val classification: GPUDashClassification,
) {
    companion object {
        const val MAX_DASH_ELEMENTS = 32

        fun classify(dashArray: FloatArray): GPUDashClassification {
            return when {
                dashArray.size > MAX_DASH_ELEMENTS -> GPUDashClassification.UnsupportedLength
                dashArray.size <= 4 -> GPUDashClassification.SimpleRepeat
                else -> GPUDashClassification.ComplexPattern
            }
        }

        fun plan(dashArray: FloatArray, dashPhase: Float = 0f): GPUComplexDashPlan {
            require(!dashArray.containsNegative()) { "Dash array must not contain negative values" }
            return GPUComplexDashPlan(
                dashArray = dashArray,
                dashPhase = dashPhase,
                classification = classify(dashArray),
            )
        }

        private fun FloatArray.containsNegative(): Boolean = any { it < 0f }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GPUComplexDashPlan) return false
        return dashArray.contentEquals(other.dashArray) &&
            dashPhase == other.dashPhase &&
            classification == other.classification
    }

    override fun hashCode(): Int {
        var result = dashArray.contentHashCode()
        result = 31 * result + dashPhase.hashCode()
        result = 31 * result + classification.hashCode()
        return result
    }

    override fun toString(): String {
        return "GPUComplexDashPlan(dashArray=${dashArray.contentToString()}, dashPhase=$dashPhase, classification=$classification)"
    }
}
