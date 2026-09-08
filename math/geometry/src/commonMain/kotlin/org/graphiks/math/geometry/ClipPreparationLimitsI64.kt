package org.graphiks.math.geometry

/** Snapshot-byte ceilings for one entry, one clip stack, and one frame. */
public data class ClipPreparationLimitsI64(
    public val maxSnapshotByteCountPerEntryI64: Long = 16L * 1024L * 1024L,
    public val maxSnapshotByteCountPerStackI64: Long = 64L * 1024L * 1024L,
    public val maxSnapshotByteCountPerFrameI64: Long = 256L * 1024L * 1024L,
) { init { require(maxSnapshotByteCountPerEntryI64 > 0L); require(maxSnapshotByteCountPerStackI64 > 0L); require(maxSnapshotByteCountPerFrameI64 > 0L) } }
