package org.graphiks.math.geometry

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
