package org.graphiks.math.geometry

/** Immutable cumulative usage captured only after a bounded stroke operation commits. */
public data class PathStrokeWorkUsageI64(
    public val attemptedGeometryUnitCountI64: Long = 0L,
    public val emittedVertexCountI64: Long = 0L,
    public val emittedIndexCountI64: Long = 0L,
    public val snapshotByteCountI64: Long = 0L,
) {
    init {
        require(attemptedGeometryUnitCountI64 >= 0L)
        require(emittedVertexCountI64 >= 0L)
        require(emittedIndexCountI64 >= 0L)
        require(snapshotByteCountI64 >= 0L)
    }
}

/** Internal control flow used by stroke preparation to publish a stable resource-limit reason. */
internal class PathStrokeResourceLimitAbort(
    val reason: PathStrokeResourceLimitReason,
) : RuntimeException()

/**
 * Transactional, non-published ledger shared by every stage of stroke preparation.
 *
 * A debit either updates both path and frame totals or publishes no mutation.  Callers debit
 * immediately before the represented work or allocation and expose only the immutable snapshots.
 */
internal class PathStrokeWorkLedgerI64(
    pathWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    frameWorkUsageBeforeI64: PathStrokeWorkUsageI64,
    private val limitsI32: PathStrokeLimitsI32,
    private val limitsI64: PathStrokeLimitsI64,
) {
    private var pathWorkUsageI64: PathStrokeWorkUsageI64 = pathWorkUsageBeforeI64
    private var frameWorkUsageI64: PathStrokeWorkUsageI64 = frameWorkUsageBeforeI64

    init {
        requireWithinPathLimits(pathWorkUsageI64)
        requireWithinFrameLimits(frameWorkUsageI64)
    }

    public fun debitBeforeEmissionI64(deltaI64: PathStrokeWorkUsageI64) {
        val nextPathUsageI64 = addUsageI64(pathWorkUsageI64, deltaI64)
        val nextFrameUsageI64 = addUsageI64(frameWorkUsageI64, deltaI64)
        requireWithinPathLimits(nextPathUsageI64)
        requireWithinFrameLimits(nextFrameUsageI64)
        pathWorkUsageI64 = nextPathUsageI64
        frameWorkUsageI64 = nextFrameUsageI64
    }

    public fun debitTopologyBeforeEmissionI64(unitCountI64: Long) {
        if (unitCountI64 < 0L) throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.TopologyLimit)
        debitBeforeEmissionI64(PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = unitCountI64))
    }

    public fun snapshotPathUsageI64(): PathStrokeWorkUsageI64 = pathWorkUsageI64

    public fun snapshotFrameUsageAfterI64(): PathStrokeWorkUsageI64 = frameWorkUsageI64

    private fun requireWithinPathLimits(usageI64: PathStrokeWorkUsageI64) {
        if (usageI64.attemptedGeometryUnitCountI64 > limitsI32.maxAttemptedGeometryUnitsPerPathI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.PathWorkLimit)
        }
        if (usageI64.emittedVertexCountI64 > limitsI32.maxEmittedVertexCountPerPathI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.VertexLimit)
        }
        if (usageI64.emittedIndexCountI64 > limitsI32.maxEmittedIndexCountPerPathI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.IndexLimit)
        }
        if (usageI64.snapshotByteCountI64 > limitsI64.maxSnapshotByteCountPerPathI64) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.SnapshotByteLimit)
        }
    }

    private fun requireWithinFrameLimits(usageI64: PathStrokeWorkUsageI64) {
        if (usageI64.attemptedGeometryUnitCountI64 > limitsI32.maxAttemptedGeometryUnitsPerFrameI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.FrameWorkLimit)
        }
        if (usageI64.emittedVertexCountI64 > limitsI32.maxEmittedVertexCountPerFrameI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.VertexLimit)
        }
        if (usageI64.emittedIndexCountI64 > limitsI32.maxEmittedIndexCountPerFrameI32.toLong()) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.IndexLimit)
        }
        if (usageI64.snapshotByteCountI64 > limitsI64.maxSnapshotByteCountPerFrameI64) {
            throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.SnapshotByteLimit)
        }
    }
}

private fun addUsageI64(
    firstI64: PathStrokeWorkUsageI64,
    secondI64: PathStrokeWorkUsageI64,
): PathStrokeWorkUsageI64 = try {
    PathStrokeWorkUsageI64(
        attemptedGeometryUnitCountI64 = checkedStrokeAddI64(
            firstI64.attemptedGeometryUnitCountI64,
            secondI64.attemptedGeometryUnitCountI64,
        ),
        emittedVertexCountI64 = checkedStrokeAddI64(firstI64.emittedVertexCountI64, secondI64.emittedVertexCountI64),
        emittedIndexCountI64 = checkedStrokeAddI64(firstI64.emittedIndexCountI64, secondI64.emittedIndexCountI64),
        snapshotByteCountI64 = checkedStrokeAddI64(firstI64.snapshotByteCountI64, secondI64.snapshotByteCountI64),
    )
} catch (_: IllegalStateException) {
    throw PathStrokeResourceLimitAbort(PathStrokeResourceLimitReason.HostSizeOverflow)
}

/** Portable checked addition for non-negative `Long` work quantities. */
private fun checkedStrokeAddI64(firstI64: Long, secondI64: Long): Long {
    if (firstI64 < 0L || secondI64 < 0L || firstI64 > Long.MAX_VALUE - secondI64) {
        throw IllegalStateException("stroke-work-overflow")
    }
    return firstI64 + secondI64
}
