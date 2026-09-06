package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PathStrokeGeometryF32Test {
    @Test
    fun `stroke work usage rejects negative debits before geometry can be published`() {
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(attemptedGeometryUnitCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(emittedVertexCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(emittedIndexCountI64 = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeWorkUsageI64(snapshotByteCountI64 = -1L)
        }
    }

    @Test
    fun `stroke limits reject non-positive budgets and negative subdivision depth`() {
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxSubdivisionDepthI32 = -1) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxAttemptedGeometryUnitsPerPathI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxEmittedVertexCountPerFrameI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI32(maxEmittedIndexCountPerPathI32 = 0) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI64(maxSnapshotByteCountPerPathI64 = 0L) }
        assertFailsWith<IllegalArgumentException> { PathStrokeLimitsI64(maxSnapshotByteCountPerFrameI64 = 0L) }
    }
}
