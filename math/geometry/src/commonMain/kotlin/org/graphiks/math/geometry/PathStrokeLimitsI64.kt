package org.graphiks.math.geometry

/** F64 flattening and dash tolerances together with the bounded stroke budgets. */
public data class PathStrokePolicyF64(
    public val maximumSagittaErrorF64: Double = 0.25,
    public val maximumDashArcLengthErrorF64: Double = 0.0625,
    public val limitsI32: PathStrokeLimitsI32 = PathStrokeLimitsI32(),
    public val limitsI64: PathStrokeLimitsI64 = PathStrokeLimitsI64(),
) {
    init {
        require(maximumSagittaErrorF64.isFinite() && maximumSagittaErrorF64 > 0.0)
        require(maximumDashArcLengthErrorF64.isFinite() && maximumDashArcLengthErrorF64 > 0.0)
    }
}

/** Byte limits that bound retained immutable snapshots for a path and a frame. */
public data class PathStrokeLimitsI64(
    public val maxSnapshotByteCountPerPathI64: Long = 16L * 1024L * 1024L,
    public val maxSnapshotByteCountPerFrameI64: Long = 64L * 1024L * 1024L,
) {
    init {
        require(maxSnapshotByteCountPerPathI64 > 0L)
        require(maxSnapshotByteCountPerFrameI64 > 0L)
    }
}
