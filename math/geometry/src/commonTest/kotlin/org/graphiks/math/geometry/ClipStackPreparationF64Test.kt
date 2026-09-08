package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClipStackPreparationF64Test {
    @Test
    fun `prepares ordered typed clips with operation aa inverse and conservative scissors`() {
        val pathF64 = PathFillInputF64.of(
            FillRule.INVERSE_EVEN_ODD,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(2.0, 2.0)),
                PathFillSegmentF64.LineTo(Point2F64(8.0, 2.0)),
                PathFillSegmentF64.LineTo(Point2F64(2.0, 8.0)),
                PathFillSegmentF64.Close,
            ),
        )
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                entriesF64 = listOf(
                    ClipDeviceInputF64.of(
                        ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 12.0, 12.0)),
                        ClipOperation.Intersect,
                        antiAlias = false,
                    ),
                    ClipDeviceInputF64.of(
                        ClipDeviceGeometryF64.RRect(RRectF64.of(RectF64(-2.0, 1.0, 6.0, 11.0), 2.0)),
                        ClipOperation.Difference,
                        antiAlias = true,
                    ),
                    ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(pathF64), ClipOperation.Intersect, antiAlias = true),
                ),
                targetDomainI32 = RectI32(0, 0, 10, 10),
            ),
        )

        assertEquals(3, result.entriesF32.size)
        assertEquals(ClipOperation.Intersect, result.entriesF32[0].operation)
        assertEquals(ClipOperation.Difference, result.entriesF32[1].operation)
        assertTrue(result.entriesF32[1].antiAlias)
        assertTrue(result.entriesF32[2].inverseFill)
        assertIs<ClipGeometryF32.Rect>(result.entriesF32[0].geometryF32)
        assertIs<ClipGeometryF32.RRect>(result.entriesF32[1].geometryF32)
        assertIs<ClipGeometryF32.Path>(result.entriesF32[2].geometryF32)
        assertEquals(RectI32(0, 0, 10, 10), result.entriesF32[0].copyConservativeScissorI32())
        assertEquals(RectI32(0, 1, 6, 10), result.entriesF32[1].copyConservativeScissorI32())
        assertEquals(RectI32(2, 2, 8, 8), result.entriesF32[2].copyConservativeScissorI32())
    }

    @Test
    fun `snapshots both input and published typed geometry defensively`() {
        val sourceF64 = RectF64(1.0, 2.0, 7.0, 8.0)
        val inputF64 = ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(sourceF64), ClipOperation.Intersect)
        sourceF64.left = -100.0

        val ready = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(listOf(inputF64), RectI32(0, 0, 16, 16)),
        )
        val geometryF32 = assertIs<ClipGeometryF32.Rect>(ready.entriesF32.single().geometryF32)
        val firstCopyF32 = geometryF32.copyRectF32()
        firstCopyF32.left = -50f

        assertEquals(1f, geometryF32.copyRectF32().left)
        assertEquals(RectI32(1, 2, 7, 8), ready.entriesF32.single().copyConservativeScissorI32())
    }

    @Test
    fun `refuses an entry attempted edge budget before publishing a stack`() {
        val result = prepareClipStackGeometryF32(
            entriesF64 = listOf(
                ClipDeviceInputF64.of(
                    ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 4.0, 4.0)),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerEntryI32 = 3),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `refuses vertex index and snapshot budgets before emitting typed geometry`() {
        val inputF64 = ClipDeviceInputF64.of(
            ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 4.0, 4.0)), ClipOperation.Intersect,
        )
        val limitResults = listOf(
            ClipPreparationPolicyF64(limitsI32 = ClipPreparationLimitsI32(maxEmittedVertexCountPerEntryI32 = 3)) to
                ClipPreparationResourceLimitReason.EntryVertexLimit,
            ClipPreparationPolicyF64(limitsI32 = ClipPreparationLimitsI32(maxEmittedIndexCountPerEntryI32 = 5)) to
                ClipPreparationResourceLimitReason.EntryIndexLimit,
            ClipPreparationPolicyF64(limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 15L)) to
                ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
        )

        limitResults.forEach { (policyF64, expectedReason) ->
            val result = prepareClipStackGeometryF32(listOf(inputF64), RectI32(0, 0, 8, 8), policyF64)
            assertEquals(expectedReason, assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason)
        }
    }

    @Test
    fun `retains an empty operation in order without charging final geometry`() {
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(2.0, 2.0, 2.0, 4.0)), ClipOperation.Difference)),
                RectI32(0, 0, 8, 8),
            ),
        )

        assertEquals(1, result.entriesF32.size)
        assertIs<ClipGeometryF32.Empty>(result.entriesF32.single().geometryF32)
        assertEquals(ClipOperation.Difference, result.entriesF32.single().operation)
        assertEquals(ClipWorkUsageI64(), result.stackWorkUsageAfterI64)
    }

    @Test
    fun `cumulative frame usage rejects a later stack before it emits`() {
        val policyF64 = ClipPreparationPolicyF64(
            limitsI32 = ClipPreparationLimitsI32(
                maxAttemptedEdgesPerEntryI32 = 8,
                maxAttemptedEdgesPerStackI32 = 8,
                maxAttemptedEdgesPerFrameI32 = 4,
            ),
        )
        val first = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 2.0, 2.0)), ClipOperation.Intersect)),
                RectI32(0, 0, 8, 8),
                policyF64,
            ),
        )
        val second = prepareClipStackGeometryF32(
            listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(3.0, 3.0, 5.0, 5.0)), ClipOperation.Intersect)),
            RectI32(0, 0, 8, 8),
            policyF64,
            frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(
            ClipPreparationResourceLimitReason.FrameAttemptedEdgeLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(second).reason,
        )
    }
}
