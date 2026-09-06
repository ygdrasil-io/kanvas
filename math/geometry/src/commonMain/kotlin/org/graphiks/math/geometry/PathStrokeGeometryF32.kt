package org.graphiks.math.geometry

/**
 * Immutable geometry authority emitted by bounded stroke preparation.
 *
 * The retained fill geometry remains the only direct/stencil authority.  Every mutable payload
 * reachable through this value is copied when captured and copied again when read.
 */
public class PathStrokeGeometryF32 private constructor(
    fillGeometryF32: PathFillGeometryF32,
    conservativeBoundsF32: RectF32,
    public val workUsageI64: PathStrokeWorkUsageI64,
) {
    private val fillGeometrySnapshotF32: PathFillGeometryF32 = fillGeometryF32.copyStrokeSnapshotF32()
    private val conservativeBoundsSnapshotF32: RectF32 = conservativeBoundsF32.copyStrokeSnapshotF32()

    init {
        require(conservativeBoundsSnapshotF32.isFinite())
    }

    public val vertexCostI64: Long
        get() = fillGeometrySnapshotF32.vertexCostI64

    public val indexCostI64: Long
        get() = fillGeometrySnapshotF32.indexCostI64

    public val snapshotByteCostI64: Long
        get() = workUsageI64.snapshotByteCountI64

    public fun copyFillGeometryF32(): PathFillGeometryF32 = fillGeometrySnapshotF32.copyStrokeSnapshotF32()

    public fun copyConservativeBoundsF32(): RectF32 = conservativeBoundsSnapshotF32.copyStrokeSnapshotF32()

    public fun copyConservativeScissorI32(): RectI32 = fillGeometrySnapshotF32.copyConservativeScissorI32()

    internal companion object {
        internal fun of(
            fillGeometryF32: PathFillGeometryF32,
            conservativeBoundsF32: RectF32,
            workUsageI64: PathStrokeWorkUsageI64,
        ): PathStrokeGeometryF32 = PathStrokeGeometryF32(
            fillGeometryF32 = fillGeometryF32,
            conservativeBoundsF32 = conservativeBoundsF32,
            workUsageI64 = workUsageI64,
        )
    }
}

private fun PathFillGeometryF32.copyStrokeSnapshotF32(): PathFillGeometryF32 = PathFillGeometryF32(
    fillRule = fillRule,
    attemptedEdgeCountI32 = attemptedEdgeCountI32,
    emittedNonZeroClosedEdgeCountI32 = emittedNonZeroClosedEdgeCountI32,
    conservativeScissorI32 = copyConservativeScissorI32(),
    directTriangleF32 = copyDirectTriangleF32OrNull(),
    stencilEdgeFanF32 = copyStencilEdgeFanF32OrNull(),
)

private fun RectF32.copyStrokeSnapshotF32(): RectF32 = RectF32(left, top, right, bottom)
