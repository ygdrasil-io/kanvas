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
