package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionF32Test {
    @Test
    fun `regions return new values for boolean operations`() {
        val left = RegionF32(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        val right = RegionF32(RectF32.ofLTRB(5f, 0f, 15f, 10f))
        val union = left.op(right, RegionBooleanOp.UNION)
        val difference = left.op(right, RegionBooleanOp.DIFFERENCE)

        assertTrue(left.contains(2f, 5f))
        assertFalse(left.contains(12f, 5f))
        assertTrue(union.contains(12f, 5f))
        assertTrue(difference.contains(2f, 5f))
        assertFalse(difference.contains(7f, 5f))
    }

    @Test
    fun `region boolean variants and bounds are geometric`() {
        val left = RegionF32(RectF32.ofLTRB(0f, 0f, 10f, 10f))
        val right = RegionF32(RectF32.ofLTRB(5f, 0f, 15f, 10f))

        assertTrue(left.op(right, RegionBooleanOp.INTERSECT).contains(7f, 5f))
        assertFalse(left.op(right, RegionBooleanOp.XOR).contains(7f, 5f))
        assertTrue(left.op(right, RegionBooleanOp.REVERSE_DIFFERENCE).contains(12f, 5f))
        assertTrue(left.op(right, RegionBooleanOp.REPLACE).contains(12f, 5f))
        assertEquals(RectF32.ofLTRB(0f, 0f, 15f, 10f), left.op(right, RegionBooleanOp.UNION).bounds)
    }
}
