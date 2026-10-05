package org.graphiks.math.geometry

/** Device-space precision and work bounds used by W4c path-fill preparation. */
public data class PathFillFlatteningPolicyF64(
    public val maximumSagittaErrorF64: Double = 0.25,
    public val limitsI32: PathFillLimitsI32 = PathFillLimitsI32(),
) {
    init {
        require(maximumSagittaErrorF64.isFinite() && maximumSagittaErrorF64 > 0.0)
    }
}
