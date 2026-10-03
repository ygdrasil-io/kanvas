package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RectProjectionF32Test {

    @Test
    fun coordinateF32ToExactI32OrNullAcceptsExactFiniteI32Values() {
        assertEquals(0, coordinateF32ToExactI32OrNull(0f))
        assertEquals(0, coordinateF32ToExactI32OrNull(-0f))
        assertEquals(1, coordinateF32ToExactI32OrNull(1f))
        assertEquals(-1, coordinateF32ToExactI32OrNull(-1f))
        assertEquals(Int.MIN_VALUE, coordinateF32ToExactI32OrNull(Int.MIN_VALUE.toFloat()))
        assertEquals(2_147_483_520, coordinateF32ToExactI32OrNull(2_147_483_520f))
    }

    @Test
    fun coordinateF32ToExactI32OrNullRejectsHalfAndNearZeroFractions() {
        assertNull(coordinateF32ToExactI32OrNull(0.5f))
        assertNull(coordinateF32ToExactI32OrNull(-0.5f))
        assertNull(coordinateF32ToExactI32OrNull(Float.fromBits(1)))
        assertNull(coordinateF32ToExactI32OrNull(-Float.fromBits(1)))
    }

    @Test
    fun coordinateF32ToExactI32OrNullRejectsNonFiniteAndOutOfRangeValues() {
        assertNull(coordinateF32ToExactI32OrNull(Float.NaN))
        assertNull(coordinateF32ToExactI32OrNull(Float.POSITIVE_INFINITY))
        assertNull(coordinateF32ToExactI32OrNull(Float.NEGATIVE_INFINITY))
        assertNull(coordinateF32ToExactI32OrNull(Int.MAX_VALUE.toFloat()))
        assertNull(coordinateF32ToExactI32OrNull(-2_147_483_904f))
        assertNull(coordinateF32ToExactI32OrNull(Float.MAX_VALUE))
        assertNull(coordinateF32ToExactI32OrNull(-Float.MAX_VALUE))
    }

    @Test
    fun rectF32ToExactRectI32OrNullAcceptsLiteralIntegralEdges() {
        assertEquals(
            RectI32(-2, 3, 12, 20),
            RectF32.ofLTRB(-2f, 3f, 12f, 20f).toExactRectI32OrNull(),
        )
    }

    @Test
    fun rectF32ToExactRectI32OrNullRejectsOneInvalidEdge() {
        assertNull(RectF32.ofLTRB(0.5f, 0f, 12f, 12f).toExactRectI32OrNull())
        assertNull(RectF32.ofLTRB(0f, Float.NaN, 12f, 12f).toExactRectI32OrNull())
        assertNull(RectF32.ofLTRB(0f, 0f, Int.MAX_VALUE.toFloat(), 12f).toExactRectI32OrNull())
    }

    @Test
    fun rectF32ToExactRectI32OrNullRejectsEmptyAndInvertedEdges() {
        assertNull(RectF32.ofLTRB(1f, 2f, 1f, 3f).toExactRectI32OrNull())
        assertNull(RectF32.ofLTRB(1f, 2f, 3f, 2f).toExactRectI32OrNull())
        assertNull(RectF32.ofLTRB(2f, 2f, 1f, 3f).toExactRectI32OrNull())
        assertNull(RectF32.ofLTRB(1f, 3f, 2f, 2f).toExactRectI32OrNull())
    }

    @Test
    fun rectF32ToExactRectI32OrNullAcceptsValidEdgesWithSpanAboveIntMax() {
        val rect = assertNotNull(
            RectF32.ofLTRB(Int.MIN_VALUE.toFloat(), 0f, 0f, 1f).toExactRectI32OrNull(),
        )

        assertEquals(Int.MIN_VALUE, rect.left)
        assertEquals(0, rect.top)
        assertEquals(0, rect.right)
        assertEquals(1, rect.bottom)
        assertEquals(2_147_483_648L, rect.width64())
        assertFalse(rect.isEmpty64())
    }

    @Test
    fun rectF32ToExactRectI32OrNullAcceptsValidEdgesWithHeightAboveIntMax() {
        val rect = assertNotNull(
            RectF32.ofLTRB(0f, Int.MIN_VALUE.toFloat(), 1f, 0f).toExactRectI32OrNull(),
        )

        assertEquals(0, rect.left)
        assertEquals(Int.MIN_VALUE, rect.top)
        assertEquals(1, rect.right)
        assertEquals(0, rect.bottom)
        assertEquals(2_147_483_648L, rect.height64())
        assertFalse(rect.isEmpty64())
    }
}
