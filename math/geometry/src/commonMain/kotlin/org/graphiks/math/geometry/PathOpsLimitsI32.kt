package org.graphiks.math.geometry

internal data class PathOpsLimitsI32(
    val maxSubdivisionDepth: Int = 32,
    val maxFlattenedEdgesPerOperand: Int = 65_536,
    val maxIntersections: Int = 262_144,
    val maxVertices: Int = 262_144,
    val maxHalfEdges: Int = 1_048_576,
) {
    init {
        require(maxSubdivisionDepth > 0)
        require(maxFlattenedEdgesPerOperand > 0)
        require(maxIntersections > 0)
        require(maxVertices > 0)
        require(maxHalfEdges > 0)
    }
}
