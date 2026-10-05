package org.graphiks.math.geometry

/** Closed triangle topology and immutable source-space geometry; no GPU allocation policy. */
public enum class TriangleTopologyI32 { List, Strip, Fan }

public class TriangleMeshF32 private constructor(
    public val topologyI32: TriangleTopologyI32,
    positionsF32: FloatArray,
    coordinatesF32: FloatArray?,
    indicesI32: IntArray?,
    boundsF32: RectF32,
    public val fanExpanded: Boolean,
) {
    private val positions = positionsF32.copyOf()
    private val coordinates = coordinatesF32?.copyOf()
    private val indices = indicesI32?.copyOf()
    private val bounds = boundsF32.copy()
    public val vertexCountI32: Int get() = positions.size / 2
    public val indexCountI32: Int? get() = indices?.size
    public val maxIndexI32: Int? get() = indices?.maxOrNull()
    public fun copyPositionsF32(): FloatArray = positions.copyOf()
    public fun copyCoordinatesF32(): FloatArray? = coordinates?.copyOf()
    public fun copyIndicesI32(): IntArray? = indices?.copyOf()
    public fun copyBoundsF32(): RectF32 = bounds.copy()

    public companion object {
        public fun ofOrNull(topologyI32: TriangleTopologyI32, positionsF32: FloatArray,
            coordinatesF32: FloatArray?, indicesI32: IntArray?, maxVerticesI32: Int,
            maxIndicesI32: Int): TriangleMeshF32? {
            if (positionsF32.isEmpty() || positionsF32.size % 2 != 0 ||
                positionsF32.size / 2 > maxVerticesI32 || positionsF32.any { !it.isFinite() }) return null
            if (coordinatesF32 != null && (coordinatesF32.size != positionsF32.size || coordinatesF32.any { !it.isFinite() })) return null
            val countI32 = positionsF32.size / 2
            if (indicesI32 != null && (indicesI32.isEmpty() || indicesI32.size > maxIndicesI32 ||
                    indicesI32.any { it !in 0 until countI32 })) return null
            val elementCountI32 = indicesI32?.size ?: countI32
            if (elementCountI32 < 3 || topologyI32 == TriangleTopologyI32.List && elementCountI32 % 3 != 0) return null
            val canonical = if (topologyI32 == TriangleTopologyI32.Fan) {
                val expandedI64 = (elementCountI32.toLong() - 2L) * 3L
                if (expandedI64 > maxIndicesI32 || expandedI64 > Int.MAX_VALUE) return null
                IntArray(expandedI64.toInt()) { indexI32 ->
                    val originalI32 = when (indexI32 % 3) { 0 -> 0; 1 -> indexI32 / 3 + 1; else -> indexI32 / 3 + 2 }
                    indicesI32?.get(originalI32) ?: originalI32
                }
            } else indicesI32
            var leftF32 = positionsF32[0]; var rightF32 = leftF32
            var topF32 = positionsF32[1]; var bottomF32 = topF32
            for (indexI32 in 1 until countI32) {
                leftF32 = minOf(leftF32, positionsF32[indexI32 * 2]); rightF32 = maxOf(rightF32, positionsF32[indexI32 * 2])
                topF32 = minOf(topF32, positionsF32[indexI32 * 2 + 1]); bottomF32 = maxOf(bottomF32, positionsF32[indexI32 * 2 + 1])
            }
            return TriangleMeshF32(if (topologyI32 == TriangleTopologyI32.Fan) TriangleTopologyI32.List else topologyI32,
                positionsF32, coordinatesF32, canonical, RectF32(leftF32, topF32, rightF32, bottomF32), topologyI32 == TriangleTopologyI32.Fan)
        }
    }
}
