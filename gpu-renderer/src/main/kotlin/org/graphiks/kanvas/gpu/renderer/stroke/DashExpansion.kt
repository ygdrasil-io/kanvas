package org.graphiks.kanvas.gpu.renderer.stroke

import kotlin.math.abs
import kotlin.math.sqrt

data class DashInterval(val length: Float, val isOn: Boolean)

/**
 * Result of CPU pre-expansion of a tessellated path into dashed vertex segments.
 *
 * @property vertices Flat interleaved (x, y) vertex pairs for all dash-on segments.
 * @property contourStarts Vertex indices (in vertex count, not float count) for each contour.
 * @property edgeCount Total edges across all dash-on contours.
 */
data class DashVertexExpansion(
    val vertices: List<Float>,
    val contourStarts: List<Int>,
    val edgeCount: Int,
) {
    companion object {
        /**
         * Pre-expands a tessellated path into visible dash-on vertex segments.
         *
         * Walks the tessellated vertices as a chain of line segments, computes cumulative
         * arc length, and extracts the sub-chain of vertices for each dash-on interval.
         * Each "on" segment becomes a separate contour.
         *
         * @param tessellatedVertices Flat interleaved (x, y) vertex pairs.
         * @param dashIntervals Dash on/off lengths.
         * @param dashPhase Offset into dash pattern.
         * @param strokeWidth Stroke width (used only for estimate, not for geometry offset).
         * @return [DashVertexExpansion] with concatenated on-segment vertices.
         */
        fun expandVertices(
            tessellatedVertices: List<Float>,
            dashIntervals: FloatArray,
            dashPhase: Float,
            strokeWidth: Float,
        ): DashVertexExpansion {
            if (tessellatedVertices.size < 4 || dashIntervals.isEmpty()) {
                return DashVertexExpansion(tessellatedVertices, listOf(0), 0)
            }
            if (dashIntervals.sum() <= 0f) {
                return DashVertexExpansion(emptyList(), emptyList(), 0)
            }

            val cumulative = cumulativeArcLengths(tessellatedVertices)
            val pathLength = cumulative.last()
            if (pathLength <= 0f) return DashVertexExpansion(emptyList(), emptyList(), 0)

            val resultVerts = mutableListOf<Float>()
            val resultContours = mutableListOf<Int>()
            val normalizedPhase = normalizedDashOffset(dashPhase, dashIntervals.sum())
            var currentPos = -normalizedPhase
            var dashIdx = 0
            var isOn = true
            var zeroIntervalSteps = 0
            var lastOnSegmentEnd: Float? = null

            while (currentPos < pathLength) {
                val dashLen = dashIntervals[dashIdx % dashIntervals.size]
                if (dashLen == 0f) {
                    dashIdx++
                    isOn = !isOn
                    zeroIntervalSteps++
                    if (zeroIntervalSteps >= dashIntervals.size) break
                    continue
                }
                zeroIntervalSteps = 0

                val segStart = maxOf(currentPos, 0f)
                val segEnd = minOf(currentPos + dashLen, pathLength)

                if (isOn && segStart < segEnd) {
                    val startVertexIdx = findVertexAtLength(cumulative, segStart)
                    val endVertexIdx = findVertexAtLength(cumulative, segEnd)
                    val continuePreviousContour = lastOnSegmentEnd?.let { abs(it - segStart) <= 1e-6f } == true

                    if (!continuePreviousContour) {
                        resultContours.add(resultVerts.size / 2)
                    }

                    val firstVertexIdx = if (
                        continuePreviousContour &&
                        resultVerts.size >= 2 &&
                        tessellatedVertices[startVertexIdx * 2] == resultVerts[resultVerts.size - 2] &&
                        tessellatedVertices[startVertexIdx * 2 + 1] == resultVerts[resultVerts.size - 1]
                    ) {
                        startVertexIdx + 1
                    } else {
                        startVertexIdx
                    }

                    for (vi in firstVertexIdx..endVertexIdx) {
                        resultVerts.add(tessellatedVertices[vi * 2])
                        resultVerts.add(tessellatedVertices[vi * 2 + 1])
                    }
                    lastOnSegmentEnd = segEnd
                } else if (!isOn && segStart < segEnd) {
                    lastOnSegmentEnd = null
                }

                currentPos += dashLen
                isOn = !isOn
                dashIdx++
            }

            val edgeCount = maxOf(0, (resultVerts.size / 2) - 1)
            return DashVertexExpansion(
                vertices = resultVerts,
                contourStarts = resultContours,
                edgeCount = edgeCount,
            )
        }

        private fun cumulativeArcLengths(vertices: List<Float>): List<Float> {
            val cumulative = mutableListOf(0f)
            var i = 2
            while (i < vertices.size) {
                val dx = vertices[i] - vertices[i - 2]
                val dy = vertices[i + 1] - vertices[i - 1]
                cumulative.add(cumulative.last() + sqrt(dx * dx + dy * dy))
                i += 2
            }
            return cumulative
        }

        private fun findVertexAtLength(cumulative: List<Float>, target: Float): Int {
            var lo = 0
            var hi = cumulative.size - 1
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (cumulative[mid] < target) lo = mid + 1
                else hi = mid
            }
            return lo
        }
    }
}

data class DashExpansion(val intervals: List<DashInterval>) {
    companion object {
        fun expand(dashes: FloatArray, dashOffset: Float, pathLength: Float): DashExpansion {
            if (dashes.isEmpty() || pathLength <= 0f) return DashExpansion(emptyList())
            if (dashes.sum() <= 0f) return DashExpansion(emptyList())

            val intervals = mutableListOf<DashInterval>()
            val normalizedOffset = normalizedDashOffset(dashOffset, dashes.sum())
            var currentPos = -normalizedOffset
            var dashIdx = 0
            var isOn = true
            var zeroIntervalSteps = 0
            while (currentPos < pathLength) {
                val dashLen = dashes[dashIdx % dashes.size]
                if (dashLen == 0f) {
                    dashIdx++
                    isOn = !isOn
                    zeroIntervalSteps++
                    if (zeroIntervalSteps >= dashes.size) break
                    continue
                }
                zeroIntervalSteps = 0

                val segmentStart = maxOf(currentPos, 0f)
                val segmentEnd = minOf(currentPos + dashLen, pathLength)
                val visibleLen = if (segmentEnd > segmentStart) segmentEnd - segmentStart else 0f
                if (visibleLen > 0f) {
                    intervals.addMerged(DashInterval(length = visibleLen, isOn = isOn))
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
            require(dashArray.any { it > 0f }) { "Dash array must contain a positive interval" }
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

private fun normalizedDashOffset(offset: Float, patternLength: Float): Float {
    if (patternLength <= 0f) return 0f
    val mod = offset % patternLength
    return if (mod < 0f) mod + patternLength else mod
}

private fun MutableList<DashInterval>.addMerged(interval: DashInterval) {
    val previous = lastOrNull()
    if (previous != null && previous.isOn == interval.isOn) {
        this[lastIndex] = previous.copy(length = previous.length + interval.length)
    } else {
        add(interval)
    }
}
