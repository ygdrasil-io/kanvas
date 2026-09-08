package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.ClipOperation
import org.graphiks.math.geometry.ClipPreparationLimitsI32
import org.graphiks.math.geometry.ClipPreparationPolicyF64
import org.graphiks.math.geometry.ClipPreparationResourceLimitReason
import org.graphiks.math.geometry.ClipStackPreparationResult
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32

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
}
