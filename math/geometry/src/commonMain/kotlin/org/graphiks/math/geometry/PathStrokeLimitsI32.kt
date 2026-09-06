package org.graphiks.math.geometry

/** Integer limits that bound one path and the cumulative work in one frame. */
public data class PathStrokeLimitsI32(
    public val maxSubdivisionDepthI32: Int = 32,
    public val maxAttemptedGeometryUnitsPerPathI32: Int = 65_536,
    public val maxAttemptedGeometryUnitsPerFrameI32: Int = 262_144,
    public val maxEmittedVertexCountPerPathI32: Int = 262_144,
    public val maxEmittedVertexCountPerFrameI32: Int = 1_048_576,
    public val maxEmittedIndexCountPerPathI32: Int = 786_432,
    public val maxEmittedIndexCountPerFrameI32: Int = 3_145_728,
) {
    init {
        require(maxSubdivisionDepthI32 >= 0)
        require(maxAttemptedGeometryUnitsPerPathI32 > 0)
        require(maxAttemptedGeometryUnitsPerFrameI32 > 0)
        require(maxEmittedVertexCountPerPathI32 > 0)
        require(maxEmittedVertexCountPerFrameI32 > 0)
        require(maxEmittedIndexCountPerPathI32 > 0)
        require(maxEmittedIndexCountPerFrameI32 > 0)
    }
}
