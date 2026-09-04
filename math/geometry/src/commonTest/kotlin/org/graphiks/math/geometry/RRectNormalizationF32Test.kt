package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RRectNormalizationF32Test {
    @Test
    fun normalizationScalesEveryCornerByOneF64Factor() {
        val source = RRectF32.of(
            RectF32(0f, 0f, 10f, 10f),
            CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(8f, 8f),
            CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(8f, 8f),
        )

        val shape = accepted(source.normalizeForAnalyticFillF32())

        assertEquals(CornerRadiiF32.of(5f, 5f), shape.topLeft)
        assertEquals(CornerRadiiF32.of(5f, 5f), shape.topRight)
        assertEquals(CornerRadiiF32.of(5f, 5f), shape.bottomRight)
        assertEquals(CornerRadiiF32.of(5f, 5f), shape.bottomLeft)
    }

    @Test
    fun normalizationCanonicalizesEitherZeroPairToPositiveZero() {
        val source = RRectF32.of(
            RectF32(0f, 0f, 4f, 4f),
            CornerRadiiF32.of(-0f, 3f), CornerRadiiF32.of(2f, 0f),
        )

        val shape = accepted(source.normalizeForAnalyticFillF32())

        assertEquals(0f.toRawBits(), shape.topLeft.x.toRawBits())
        assertEquals(0f.toRawBits(), shape.topLeft.y.toRawBits())
        assertEquals(0f.toRawBits(), shape.topRight.x.toRawBits())
        assertEquals(0f.toRawBits(), shape.topRight.y.toRawBits())
    }

    @Test
    fun normalizationRejectsNaNBounds() {
        assertRejected(
            RRectF32.of(RectF32(Float.NaN, 0f, 4f, 4f)).normalizeForAnalyticFillF32(),
            RRectNormalizationF32Rejection.NonFiniteBounds,
        )
    }

    @Test
    fun normalizationRejectsInvertedBounds() {
        assertRejected(
            RRectF32.of(RectF32(4f, 0f, 0f, 4f)).normalizeForAnalyticFillF32(),
            RRectNormalizationF32Rejection.EmptyBounds,
        )
    }

    @Test
    fun normalizationRejectsInfiniteRadius() {
        assertRejected(
            RRectF32.of(RectF32(0f, 0f, 4f, 4f), radius = Float.POSITIVE_INFINITY)
                .normalizeForAnalyticFillF32(),
            RRectNormalizationF32Rejection.NonFiniteRadii,
        )
    }

    @Test
    fun normalizationRejectsNegativeRadius() {
        assertRejected(
            RRectF32.of(RectF32(0f, 0f, 4f, 4f), radius = -1f).normalizeForAnalyticFillF32(),
            RRectNormalizationF32Rejection.NegativeRadii,
        )
    }

    @Test
    fun normalizationRepairsF32RoundingConstraintViolationByOneUlp() {
        val source = RRectF32.of(
            RectF32(0f, 0f, 1f, 1f),
            topLeft = CornerRadiiF32.of(Float.fromBits(0x3b09a031), 0.001f),
            topRight = CornerRadiiF32.of(Float.fromBits(0x3faf3eb7), 0.001f),
            bottomRight = CornerRadiiF32.of(0.001f, 0.001f),
            bottomLeft = CornerRadiiF32.of(0.001f, 0.001f),
        )

        val shape = accepted(source.normalizeForAnalyticFillF32())

        assertEquals(0x3f7f9ba1, shape.topRight.x.toRawBits())
        assertConstraints(shape)
    }

    @Test
    fun normalizationRepairsRoundedConstraintWhenPreferredRadiusIsZero() {
        val source = RRectF32.of(
            rect = RectF32(Float.fromBits(0xb3c00000.toInt()), 0f, 1f, 1f),
            topLeft = CornerRadiiF32.of(2f, 1f),
            topRight = CornerRadiiF32.Zero,
        )

        val shape = accepted(source.normalizeForAnalyticFillF32())

        assertEquals(0x3f800000, shape.topLeft.x.toRawBits())
        assertEquals(0f.toRawBits(), shape.topRight.x.toRawBits())
        assertConstraints(shape)
    }

    private fun accepted(result: RRectNormalizationF32Result): RRectF32 =
        assertIs<RRectNormalizationF32Result.Accepted>(result).shape

    private fun assertRejected(
        result: RRectNormalizationF32Result,
        rejection: RRectNormalizationF32Rejection,
    ) {
        assertEquals(rejection, assertIs<RRectNormalizationF32Result.Rejected>(result).rejection)
    }

    private fun assertConstraints(shape: RRectF32) {
        val width = shape.rect.right.toDouble() - shape.rect.left.toDouble()
        val height = shape.rect.bottom.toDouble() - shape.rect.top.toDouble()
        assertTrue(shape.topLeft.x.toDouble() + shape.topRight.x.toDouble() <= width)
        assertTrue(shape.bottomLeft.x.toDouble() + shape.bottomRight.x.toDouble() <= width)
        assertTrue(shape.topLeft.y.toDouble() + shape.bottomLeft.y.toDouble() <= height)
        assertTrue(shape.topRight.y.toDouble() + shape.bottomRight.y.toDouble() <= height)
    }
}
