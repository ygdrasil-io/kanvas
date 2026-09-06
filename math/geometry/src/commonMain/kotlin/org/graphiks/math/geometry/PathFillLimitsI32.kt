package org.graphiks.math.geometry

/** Bounds all CPU-side work performed while preparing one W4c path fill. */
public data class PathFillLimitsI32(
    public val maxSubdivisionDepthI32: Int = 32,
    public val maxAttemptedEdgesPerPathI32: Int = 65_536,
    public val maxAttemptedEdgesPerFrameI32: Int = 262_144,
) {
    init {
        require(maxSubdivisionDepthI32 >= 0)
        require(maxAttemptedEdgesPerPathI32 > 0)
        require(maxAttemptedEdgesPerFrameI32 > 0)
    }
}
