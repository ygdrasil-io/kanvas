package org.graphiks.math.geometry

/** The finite coverage removed from a bounded inverse draw. */
public sealed interface InverseInteriorCoverageF32 {
    /** No finite interior is removed, so the inverse draw covers its complete domain. */
    public data object Zero : InverseInteriorCoverageF32

    public class Geometry private constructor(geometryF32: PathFillGeometryF32) : InverseInteriorCoverageF32 {
        private val geometrySnapshotF32: PathFillGeometryF32 = geometryF32.copyInverseSnapshotF32()

        public fun copyGeometryF32(): PathFillGeometryF32 = geometrySnapshotF32.copyInverseSnapshotF32()

        public companion object {
            public fun of(geometryF32: PathFillGeometryF32): Geometry = Geometry(geometryF32)
        }
    }
}

/** Selects the finite coverage removed from an inverse draw. */
public enum class InversePathDrawMode { Fill, StrokeAndFill }

/** Immutable bounded inverse-path payload. */
public class InversePathGeometryF32 private constructor(
    public val interiorCoverageF32: InverseInteriorCoverageF32,
    domainI32: RectI32,
) {
    private val domainSnapshotI32: RectI32 = RectI32(domainI32.left, domainI32.top, domainI32.right, domainI32.bottom)

    public fun copyDomainI32(): RectI32 = RectI32(
        domainSnapshotI32.left,
        domainSnapshotI32.top,
        domainSnapshotI32.right,
        domainSnapshotI32.bottom,
    )

    public companion object {
        public fun of(
            interiorCoverageF32: InverseInteriorCoverageF32,
            domainI32: RectI32,
        ): InversePathGeometryF32 = InversePathGeometryF32(interiorCoverageF32, domainI32)
    }
}

private fun PathFillGeometryF32.copyInverseSnapshotF32(): PathFillGeometryF32 = PathFillGeometryF32(
    fillRule = fillRule,
    attemptedEdgeCountI32 = attemptedEdgeCountI32,
    emittedNonZeroClosedEdgeCountI32 = emittedNonZeroClosedEdgeCountI32,
    conservativeScissorI32 = copyConservativeScissorI32(),
    directTriangleF32 = copyDirectTriangleF32OrNull(),
    stencilEdgeFanF32 = copyStencilEdgeFanF32OrNull(),
)
