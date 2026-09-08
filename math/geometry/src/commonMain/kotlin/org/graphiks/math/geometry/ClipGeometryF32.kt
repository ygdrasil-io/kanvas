package org.graphiks.math.geometry

/** Boolean operation applied in insertion order to a clip stack. */
public enum class ClipOperation { Intersect, Difference }

/** Immutable device-space input geometry.  Mutable source rects are copied at construction. */
public sealed interface ClipDeviceGeometryF64 {
    public class Rect(rectF64: RectF64) : ClipDeviceGeometryF64 {
        private val snapshotF64: RectF64 = rectF64.copyF64()
        public fun copyRectF64(): RectF64 = snapshotF64.copyF64()
    }

    public class RRect(rrectF64: RRectF64) : ClipDeviceGeometryF64 {
        private val snapshotF64: RRectF64 = rrectF64.copyF64()
        public fun copyRRectF64(): RRectF64 = snapshotF64.copyF64()
    }

    public class Path(public val inputF64: PathFillInputF64) : ClipDeviceGeometryF64
}

/** A device-space clip input plus independent operation and AA facts. */
public class ClipDeviceInputF64 private constructor(
    internal val geometryF64: ClipDeviceGeometryF64,
    public val operation: ClipOperation,
    public val antiAlias: Boolean,
    /** Projection-only cost accumulated for this entry before geometry finalization begins. */
    public val entryWorkUsageBeforeGeometryI64: ClipWorkUsageI64,
) {
    public companion object {
        public fun of(
            geometryF64: ClipDeviceGeometryF64,
            operation: ClipOperation,
            antiAlias: Boolean = true,
            entryWorkUsageBeforeGeometryI64: ClipWorkUsageI64 = ClipWorkUsageI64(),
        ): ClipDeviceInputF64 = ClipDeviceInputF64(
            geometryF64 = geometryF64.snapshotF64(),
            operation = operation,
            antiAlias = antiAlias,
            entryWorkUsageBeforeGeometryI64 = entryWorkUsageBeforeGeometryI64,
        )
    }
}

/** Immutable device geometry published to downstream GPU work. */
public sealed interface ClipGeometryF32 {
    public class Rect(rectF32: RectF32) : ClipGeometryF32 {
        private val snapshotF32: RectF32 = RectF32(rectF32.left, rectF32.top, rectF32.right, rectF32.bottom)
        public fun copyRectF32(): RectF32 = RectF32(snapshotF32.left, snapshotF32.top, snapshotF32.right, snapshotF32.bottom)
    }

    public class RRect(rrectF32: RRectF32) : ClipGeometryF32 {
        private val snapshotF32: RRectF32 = rrectF32.copyClipSnapshotF32()
        public fun copyRRectF32(): RRectF32 = snapshotF32.copyClipSnapshotF32()
    }

    public class Path(geometryF32: PathFillGeometryF32) : ClipGeometryF32 {
        private val snapshotF32: PathFillGeometryF32 = geometryF32.copyClipSnapshotF32()
        public fun copyPathGeometryF32(): PathFillGeometryF32 = snapshotF32.copyClipSnapshotF32()
    }

    public data object Empty : ClipGeometryF32
}

/** Ordered clip entry whose geometry and metadata are independently preserved. */
public class ClipPreparedEntryF32 internal constructor(
    public val geometryF32: ClipGeometryF32,
    public val operation: ClipOperation,
    public val antiAlias: Boolean,
    public val inverseFill: Boolean,
    conservativeScissorI32: RectI32,
) {
    private val conservativeScissorSnapshotI32 = RectI32(
        conservativeScissorI32.left, conservativeScissorI32.top,
        conservativeScissorI32.right, conservativeScissorI32.bottom,
    )

    public fun copyConservativeScissorI32(): RectI32 = RectI32(
        conservativeScissorSnapshotI32.left, conservativeScissorSnapshotI32.top,
        conservativeScissorSnapshotI32.right, conservativeScissorSnapshotI32.bottom,
    )
}

private fun ClipDeviceGeometryF64.snapshotF64(): ClipDeviceGeometryF64 = when (this) {
    is ClipDeviceGeometryF64.Rect -> ClipDeviceGeometryF64.Rect(copyRectF64())
    is ClipDeviceGeometryF64.RRect -> ClipDeviceGeometryF64.RRect(copyRRectF64())
    is ClipDeviceGeometryF64.Path -> ClipDeviceGeometryF64.Path(inputF64)
}

private fun RRectF32.copyClipSnapshotF32(): RRectF32 = RRectF32.of(
    rect = RectF32(rect.left, rect.top, rect.right, rect.bottom),
    topLeft = topLeft,
    topRight = topRight,
    bottomRight = bottomRight,
    bottomLeft = bottomLeft,
)

private fun PathFillGeometryF32.copyClipSnapshotF32(): PathFillGeometryF32 = PathFillGeometryF32(
    fillRule = fillRule,
    attemptedEdgeCountI32 = attemptedEdgeCountI32,
    emittedNonZeroClosedEdgeCountI32 = emittedNonZeroClosedEdgeCountI32,
    conservativeScissorI32 = copyConservativeScissorI32(),
    directTriangleF32 = copyDirectTriangleF32OrNull(),
    stencilEdgeFanF32 = copyStencilEdgeFanF32OrNull(),
)
