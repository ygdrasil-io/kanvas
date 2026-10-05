package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFails

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
        assertEquals(RectI32(0, 0, 10, 10), result.entriesF32[2].copyConservativeScissorI32())
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
        assertEquals(ClipWorkUsageI64(clipEntryCountI64 = 1L), result.stackWorkUsageAfterI64)
    }

    @Test
    fun `stack entry count refuses a large empty list before entry preparation`() {
        val emptyEntryF64 = ClipDeviceInputF64.of(
            ClipDeviceGeometryF64.Rect(RectF64(2.0, 2.0, 2.0, 4.0)),
            ClipOperation.Intersect,
        )

        val result = prepareClipStackGeometryF32(
            entriesF64 = List(65) { emptyEntryF64 },
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxClipEntryCountPerStackI32 = 64),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.StackEntryCountLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `frame entry count is carried into the following stack`() {
        val policyF64 = ClipPreparationPolicyF64(
            limitsI32 = ClipPreparationLimitsI32(maxClipEntryCountPerFrameI32 = 1),
        )
        val entryF64 = ClipDeviceInputF64.of(
            ClipDeviceGeometryF64.Rect(RectF64(2.0, 2.0, 2.0, 4.0)),
            ClipOperation.Intersect,
        )
        val first = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(listOf(entryF64), RectI32(0, 0, 8, 8), policyF64),
        )
        val second = prepareClipStackGeometryF32(
            entriesF64 = listOf(entryF64),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = policyF64,
            frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(1L, first.frameWorkUsageAfterI64.clipEntryCountI64)
        assertEquals(
            ClipPreparationResourceLimitReason.FrameEntryCountLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(second).reason,
        )
    }

    @Test
    fun `inverse fill scissors cover the complete target including empty geometry`() {
        listOf(FillRule.INVERSE_WINDING, FillRule.INVERSE_EVEN_ODD).forEach { fillRule ->
            val inputF64 = PathFillInputF64.of(fillRule, emptyList())
            val result = assertIs<ClipStackPreparationResult.Ready>(
                prepareClipStackGeometryF32(
                    listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(inputF64), ClipOperation.Intersect)),
                    RectI32(3, 4, 11, 12),
                ),
            )
            assertIs<ClipGeometryF32.Empty>(result.entriesF32.single().geometryF32)
            assertEquals(RectI32(3, 4, 11, 12), result.entriesF32.single().copyConservativeScissorI32())
        }
    }

    @Test
    fun `empty path attempts participate in the following frame budget`() {
        val emptyPathF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)), PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0))),
        )
        val policyF64 = ClipPreparationPolicyF64(
            limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerFrameI32 = 5),
        )
        val first = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(emptyPathF64), ClipOperation.Intersect)),
                RectI32(0, 0, 8, 8), policyF64,
            ),
        )
        val second = prepareClipStackGeometryF32(
            listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 2.0, 2.0)), ClipOperation.Intersect)),
            RectI32(0, 0, 8, 8), policyF64, frameWorkUsageBeforeI64 = first.frameWorkUsageAfterI64,
        )

        assertEquals(2L, first.frameWorkUsageAfterI64.attemptedEdgeCountI64)
        assertEquals(ClipPreparationResourceLimitReason.FrameAttemptedEdgeLimit, assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(second).reason)
    }

    @Test
    fun `empty path is admitted from its real work instead of a subdivision upper bound`() {
        val pathF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(PathFillSegmentF64.MoveTo(Point2F64(2.0, 2.0))),
        )

        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(pathF64), ClipOperation.Intersect)),
                RectI32(0, 0, 8, 8),
                ClipPreparationPolicyF64(limitsI32 = ClipPreparationLimitsI32(maxEmittedVertexCountPerEntryI32 = 1)),
            ),
        )

        assertIs<ClipGeometryF32.Empty>(result.entriesF32.single().geometryF32)
        assertEquals(0L, result.stackWorkUsageAfterI64.emittedVertexCountI64)
    }

    @Test
    fun `repeated close charges only the observed edge work`() {
        val pathF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.Close,
                PathFillSegmentF64.Close,
            ),
        )

        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(pathF64), ClipOperation.Intersect)),
                RectI32(0, 0, 8, 8),
                ClipPreparationPolicyF64(limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerEntryI32 = 1)),
            ),
        )

        assertEquals(1L, result.stackWorkUsageAfterI64.attemptedEdgeCountI64)
        assertIs<ClipGeometryF32.Empty>(result.entriesF32.single().geometryF32)
    }

    @Test
    fun `published entry list cannot be mutated through a mutable list cast`() {
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareClipStackGeometryF32(
                listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, 2.0, 2.0)), ClipOperation.Intersect)),
                RectI32(0, 0, 8, 8),
            ),
        )

        assertFails { (result.entriesF32 as MutableList<ClipPreparedEntryF32>).clear() }
        assertEquals(1, result.entriesF32.size)
    }

    @Test
    fun `finite F64 geometry outside F32 range fails closed`() {
        val result = prepareClipStackGeometryF32(
            listOf(ClipDeviceInputF64.of(ClipDeviceGeometryF64.Rect(RectF64(0.0, 0.0, Double.MAX_VALUE, 1.0)), ClipOperation.Intersect)),
            RectI32(0, 0, 8, 8),
        )

        assertEquals(ClipPreparationInvalidSceneReason.NonFiniteGeometry, assertIs<ClipStackPreparationResult.InvalidScene>(result).reason)
    }

    @Test
    fun `rrect and path inputs retain their construction snapshots after source mutation`() {
        val rrectBoundsF64 = RectF64(1.0, 2.0, 7.0, 8.0)
        val rrectF64 = RRectF64.of(rrectBoundsF64, 2.0)
        val sourceSegmentsF64 = mutableListOf<PathFillSegmentF64>(
            PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(3.0, 0.0)),
            PathFillSegmentF64.LineTo(Point2F64(0.0, 3.0)),
            PathFillSegmentF64.Close,
        )
        val pathF64 = PathFillInputF64.of(FillRule.WINDING, sourceSegmentsF64)
        val inputsF64 = listOf(
            ClipDeviceInputF64.of(ClipDeviceGeometryF64.RRect(rrectF64), ClipOperation.Intersect),
            ClipDeviceInputF64.of(ClipDeviceGeometryF64.Path(pathF64), ClipOperation.Intersect),
        )
        rrectBoundsF64.left = -99.0
        sourceSegmentsF64.clear()

        val result = assertIs<ClipStackPreparationResult.Ready>(prepareClipStackGeometryF32(inputsF64, RectI32(0, 0, 16, 16)))
        assertEquals(1f, assertIs<ClipGeometryF32.RRect>(result.entriesF32[0].geometryF32).copyRRectF32().rect.left)
        assertIs<ClipGeometryF32.Path>(result.entriesF32[1].geometryF32)
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
