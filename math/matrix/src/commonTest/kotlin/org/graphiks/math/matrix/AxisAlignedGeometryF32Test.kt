package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RRectNormalizationF32Rejection
import org.graphiks.math.geometry.RRectNormalizationF32Result
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.normalizeForAnalyticFillF32

class AxisAlignedGeometryF32Test {
    @Test
    fun `maps an axis aligned rrect and preserves asymmetric mirrored corners`() {
        val matrix = Matrix3x3F32.translation(10f, 5f) * Matrix3x3F32.scaling(-2f, 3f)
        val rrect = RRectF32.of(
            rect = RectF32.ofLTRB(1f, 2f, 4f, 6f),
            topLeft = CornerRadiiF32.of(1f, 2f),
            topRight = CornerRadiiF32.of(2f, 3f),
            bottomRight = CornerRadiiF32.of(3f, 4f),
            bottomLeft = CornerRadiiF32.of(4f, 5f),
        )

        assertEquals(RectF32.ofLTRB(2f, 11f, 8f, 23f), matrix.mapAxisAlignedRect(rrect.rect))
        assertEquals(
            RRectF32.of(
                rect = RectF32.ofLTRB(2f, 11f, 8f, 23f),
                topLeft = CornerRadiiF32.of(4f, 9f),
                topRight = CornerRadiiF32.of(2f, 6f),
                bottomRight = CornerRadiiF32.of(8f, 15f),
                bottomLeft = CornerRadiiF32.of(6f, 12f),
            ),
            rrect.mapAxisAligned(matrix),
        )
    }

    @Test
    fun `rejects rotated and perspective transforms for axis aligned geometry`() {
        val rect = RectF32.ofLTRB(1f, 2f, 4f, 6f)

        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.rotation(45f).mapAxisAlignedRect(rect)
        }
        assertFailsWith<IllegalArgumentException> {
            Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f).mapAxisAlignedRect(rect)
        }
    }

    @Test
    fun `maps reflected rrect corners before analytic normalization`() {
        val source = RRectF32.of(
            rect = RectF32(0f, 0f, 20f, 20f),
            topLeft = CornerRadiiF32.of(1f, 2f),
            topRight = CornerRadiiF32.of(3f, 4f),
            bottomRight = CornerRadiiF32.of(5f, 6f),
            bottomLeft = CornerRadiiF32.of(7f, 8f),
        )
        val device = source.mapAxisAligned(Matrix3x3F32.scaling(-2f, 3f))

        assertEquals(CornerRadiiF32.of(6f, 12f), device.topLeft)
        assertEquals(CornerRadiiF32.of(2f, 6f), device.topRight)
        assertEquals(CornerRadiiF32.of(14f, 24f), device.bottomRight)
        assertEquals(CornerRadiiF32.of(10f, 18f), device.bottomLeft)

        val normalized = (device.normalizeForAnalyticFillF32() as RRectNormalizationF32Result.Accepted).shape
        val width = normalized.rect.right.toDouble() - normalized.rect.left.toDouble()
        val height = normalized.rect.bottom.toDouble() - normalized.rect.top.toDouble()
        assertTrue(normalized.topLeft.x.toDouble() + normalized.topRight.x.toDouble() <= width)
        assertTrue(normalized.bottomLeft.x.toDouble() + normalized.bottomRight.x.toDouble() <= width)
        assertTrue(normalized.topLeft.y.toDouble() + normalized.bottomLeft.y.toDouble() <= height)
        assertTrue(normalized.topRight.y.toDouble() + normalized.bottomRight.y.toDouble() <= height)
    }

    @Test
    fun `materializes mapped F32 inputs before analytic normalization`() {
        val acceptedDevice = RRectF32.of(
            rect = RectF32(0f, 0f, 3f, 5f),
            topLeft = CornerRadiiF32.of(1f, 2f),
        ).mapAxisAligned(Matrix3x3F32.scaling(0.1f, 1.5f))
        val accepted = assertIs<RRectNormalizationF32Result.Accepted>(
            acceptedDevice.normalizeForAnalyticFillF32(),
        ).shape

        assertEquals(0.3f.toRawBits(), accepted.rect.right.toRawBits())
        assertEquals(7.5f.toRawBits(), accepted.rect.bottom.toRawBits())
        assertEquals(0.1f.toRawBits(), accepted.topLeft.x.toRawBits())
        assertEquals(3f.toRawBits(), accepted.topLeft.y.toRawBits())

        val overflowBounds = RRectF32.of(
            rect = RectF32(0f, 0f, Float.MAX_VALUE, 1f),
        ).mapAxisAligned(Matrix3x3F32.scaling(1.5f, 0.5f))
        assertEquals(Float.POSITIVE_INFINITY.toRawBits(), overflowBounds.rect.right.toRawBits())
        assertEquals(
            RRectNormalizationF32Rejection.NonFiniteBounds,
            assertIs<RRectNormalizationF32Result.Rejected>(
                overflowBounds.normalizeForAnalyticFillF32(),
            ).rejection,
        )

        val overflowRadii = RRectF32.of(
            rect = RectF32(0f, 0f, 1f, 1f),
            topLeft = CornerRadiiF32.of(Float.MAX_VALUE, 1f),
        ).mapAxisAligned(Matrix3x3F32.scaling(1.5f, 0.5f))
        assertEquals(Float.POSITIVE_INFINITY.toRawBits(), overflowRadii.topLeft.x.toRawBits())
        assertEquals(
            RRectNormalizationF32Rejection.NonFiniteRadii,
            assertIs<RRectNormalizationF32Result.Rejected>(
                overflowRadii.normalizeForAnalyticFillF32(),
            ).rejection,
        )
    }
}
