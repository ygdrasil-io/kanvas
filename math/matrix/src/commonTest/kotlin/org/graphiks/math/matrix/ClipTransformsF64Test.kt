package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.ClipOperation
import org.graphiks.math.geometry.ClipPreparationLimitsI32
import org.graphiks.math.geometry.ClipPreparationLimitsI64
import org.graphiks.math.geometry.ClipPreparationPolicyF64
import org.graphiks.math.geometry.ClipPreparationResourceLimitReason
import org.graphiks.math.geometry.ClipStackPreparationResult
import org.graphiks.math.geometry.ClipWorkUsageI64
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.RRectF64
import org.graphiks.math.geometry.CornerRadiiF64
import org.graphiks.math.geometry.PathBuilder

class ClipTransformsF64Test {
    @Test
    fun `axis aligned clips remain typed while general affine and perspective clips become paths`() {
        val inputsF64 = listOf(
            ClipTransformInputF64.of(
                ClipTransformGeometryF64.Rect(RectF64(1.0, 2.0, 3.0, 4.0)),
                Matrix3x3F64(sxF64 = 2.0, syF64 = 3.0, txF64 = 1.0, tyF64 = -2.0),
                ClipOperation.Intersect,
            ),
            ClipTransformInputF64.of(
                ClipTransformGeometryF64.Rect(RectF64(1.0, 2.0, 3.0, 4.0)),
                Matrix3x3F64(kxF64 = 1.0),
                ClipOperation.Difference,
            ),
            ClipTransformInputF64.of(
                ClipTransformGeometryF64.Rect(RectF64(1.0, 2.0, 3.0, 4.0)),
                Matrix3x3F64(persp0F64 = 0.1),
                ClipOperation.Intersect,
            ),
        )

        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(inputsF64, RectI32(-32, -32, 32, 32)),
        )

        assertIs<ClipGeometryF32.Rect>(result.entriesF32[0].geometryF32)
        assertIs<ClipGeometryF32.Path>(result.entriesF32[1].geometryF32)
        assertIs<ClipGeometryF32.Path>(result.entriesF32[2].geometryF32)
        assertEquals(ClipOperation.Difference, result.entriesF32[1].operation)
    }

    @Test
    fun `projection and geometry costs combine at the entry limit before geometry publishes`() {
        val result = prepareTransformedClipStackGeometryF32(
            entriesF64 = listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.Rect(RectF64(0.0, 0.0, 4.0, 4.0)),
                    Matrix3x3F64(kxF64 = 1.0),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerEntryI32 = 7),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntryAttemptedEdgeLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `axis reflection preserves exact rounded rectangle radii and general affine preserves rounded path bounds`() {
        val rrectF64 = RRectF64.of(
            RectF64(1.0, 2.0, 5.0, 8.0),
            CornerRadiiF64.of(1.0, 2.0), CornerRadiiF64.of(3.0, 4.0),
            CornerRadiiF64.of(5.0, 6.0), CornerRadiiF64.of(7.0, 8.0),
        )
        val reflected = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(
                listOf(ClipTransformInputF64.of(ClipTransformGeometryF64.RRect(rrectF64), Matrix3x3F64(sxF64 = -2.0, syF64 = 3.0), ClipOperation.Intersect)),
                RectI32(-16, -16, 16, 32),
            ),
        )
        val reflectedRRectF32 = assertIs<ClipGeometryF32.RRect>(reflected.entriesF32.single().geometryF32).copyRRectF32()
        assertEquals(6f, reflectedRRectF32.topLeft.x)
        assertEquals(12f, reflectedRRectF32.topLeft.y)
        assertEquals(2f, reflectedRRectF32.topRight.x)
        assertEquals(6f, reflectedRRectF32.topRight.y)

        val affine = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(
                listOf(ClipTransformInputF64.of(ClipTransformGeometryF64.RRect(rrectF64), Matrix3x3F64(kxF64 = 1.0), ClipOperation.Intersect)),
                RectI32(-16, -16, 32, 32),
            ),
        )
        assertIs<ClipGeometryF32.Path>(affine.entriesF32.single().geometryF32)
        assertEquals(RectI32(3, 2, 12, 8), affine.entriesF32.single().copyConservativeScissorI32())
    }

    @Test
    fun `perspective curve that crosses a horizon refuses before clip geometry publishes`() {
        val pathF32 = PathBuilder().moveTo(1f, 0f).quadTo(-3f, 1f, 1f, 0f).close().build()
        val result = prepareTransformedClipStackGeometryF32(
            listOf(ClipTransformInputF64.of(ClipTransformGeometryF64.Path(pathF32), Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0), ClipOperation.Intersect)),
            RectI32(-16, -16, 16, 16),
        )

        assertIs<ClipStackPreparationResult.InvalidScene>(result)
    }

    @Test
    fun `perspective rounded rectangle horizon uses projective path preparation`() {
        val result = prepareTransformedClipStackGeometryF32(
            listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.RRect(RRectF64.of(RectF64(-1.0, 0.0, 1.0, 2.0), 0.25)),
                    Matrix3x3F64(persp0F64 = 1.0, persp2F64 = 0.0), ClipOperation.Intersect,
                ),
            ),
            RectI32(-16, -16, 16, 16),
        )

        assertIs<ClipStackPreparationResult.InvalidScene>(result)
    }

    @Test
    fun `general affine keeps adjacent F64 source coordinates distinct until transform`() {
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(
                listOf(
                    ClipTransformInputF64.of(
                        ClipTransformGeometryF64.Rect(RectF64(16_777_216.0, 0.0, 16_777_217.0, 1.0)),
                        Matrix3x3F64(sxF64 = 1.0, syF64 = 1.0, kxF64 = 1.0e-20, txF64 = -16_777_216.0),
                        ClipOperation.Intersect,
                    ),
                ),
                RectI32(0, 0, 4, 4),
            ),
        )

        assertIs<ClipGeometryF32.Path>(result.entriesF32.single().geometryF32)
        assertEquals(RectI32(0, 0, 1, 1), result.entriesF32.single().copyConservativeScissorI32())
    }

    @Test
    fun `general affine accepts F64 source outside F32 when device result is representable`() {
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(
                listOf(
                    ClipTransformInputF64.of(
                        ClipTransformGeometryF64.Rect(RectF64(1.0e100, 0.0, 2.0e100, 1.0)),
                        Matrix3x3F64(sxF64 = 1.0e-100, syF64 = 1.0, kxF64 = 1.0e-100),
                        ClipOperation.Intersect,
                    ),
                ),
                RectI32(0, 0, 4, 4),
            ),
        )

        assertIs<ClipGeometryF32.Path>(result.entriesF32.single().geometryF32)
        assertEquals(RectI32(1, 0, 2, 1), result.entriesF32.single().copyConservativeScissorI32())
    }

    @Test
    fun `projective work is admitted from incremental frame cost`() {
        val result = assertIs<ClipStackPreparationResult.Ready>(
            prepareTransformedClipStackGeometryF32(
                listOf(
                    ClipTransformInputF64.of(
                        ClipTransformGeometryF64.Rect(RectF64(1.0, 1.0, 2.0, 2.0)),
                        Matrix3x3F64(persp0F64 = 0.01),
                        ClipOperation.Intersect,
                    ),
                ),
                RectI32(0, 0, 4, 4),
                ClipPreparationPolicyF64(limitsI32 = ClipPreparationLimitsI32(maxAttemptedEdgesPerFrameI32 = 100)),
                frameWorkUsageBeforeI64 = ClipWorkUsageI64(attemptedEdgeCountI64 = 1L),
            ),
        )

        assertTrue(result.frameWorkUsageAfterI64.attemptedEdgeCountI64 in 2L..100L)
        assertIs<ClipGeometryF32.Path>(result.entriesF32.single().geometryF32)
    }

    @Test
    fun `large transformed F32 path refuses a tiny snapshot budget before source copy`() {
        val builder = PathBuilder().moveTo(0f, 0f)
        repeat(512) { indexI32 -> builder.lineTo(indexI32.toFloat(), 1f) }
        val path = builder.build()
        builder.lineTo(-1f, -1f)

        val result = prepareTransformedClipStackGeometryF32(
            listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.Path(path), Matrix3x3F64(kxF64 = 1.0), ClipOperation.Intersect,
                ),
            ),
            RectI32(0, 0, 8, 8),
            ClipPreparationPolicyF64(limitsI64 = org.graphiks.math.geometry.ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 15L)),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `general affine rect debits its snapshot before inspecting it`() {
        val result = prepareTransformedClipStackGeometryF32(
            entriesF64 = listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.Rect(RectF64(Double.NaN, 0.0, 4.0, 4.0)),
                    Matrix3x3F64(kxF64 = 1.0),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 15L),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `general affine rounded rectangle debits its snapshot before inspecting it`() {
        val result = prepareTransformedClipStackGeometryF32(
            entriesF64 = listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.RRect(
                        RRectF64.of(RectF64(Double.NaN, 0.0, 4.0, 4.0), 0.0),
                    ),
                    Matrix3x3F64(kxF64 = 1.0),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 47L),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `general affine rect includes canonical path materialization in its snapshot budget`() {
        val result = prepareTransformedClipStackGeometryF32(
            entriesF64 = listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.Rect(RectF64(0.0, 0.0, 4.0, 4.0)),
                    Matrix3x3F64(kxF64 = 1.0),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 883L),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }

    @Test
    fun `general affine rounded rectangle includes canonical path materialization in its snapshot budget`() {
        val result = prepareTransformedClipStackGeometryF32(
            entriesF64 = listOf(
                ClipTransformInputF64.of(
                    ClipTransformGeometryF64.RRect(RRectF64.of(RectF64(0.0, 0.0, 4.0, 4.0), 0.0)),
                    Matrix3x3F64(kxF64 = 1.0),
                    ClipOperation.Intersect,
                ),
            ),
            targetDomainI32 = RectI32(0, 0, 8, 8),
            policyF64 = ClipPreparationPolicyF64(
                limitsI64 = ClipPreparationLimitsI64(maxSnapshotByteCountPerEntryI64 = 1_555L),
            ),
        )

        assertEquals(
            ClipPreparationResourceLimitReason.EntrySnapshotByteLimit,
            assertIs<ClipStackPreparationResult.ResourceLimitExceeded>(result).reason,
        )
    }
}
