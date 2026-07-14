package org.graphiks.math.geometry

import kotlin.test.Test

class PathOpsEpsilonTest {

    @Test
    fun `FLT_EPSILON is positive and small`() {
        kotlin.test.assertTrue(FLT_EPSILON > 0.0)
        kotlin.test.assertTrue(FLT_EPSILON < 1e-6)
    }

    @Test
    fun `DBL_EPSILON is smaller than FLT_EPSILON`() {
        kotlin.test.assertTrue(DBL_EPSILON < FLT_EPSILON)
    }

    @Test
    fun `approximately_zero`() {
        kotlin.test.assertTrue(approximately_zero(0.0))
        kotlin.test.assertTrue(approximately_zero(1e-8))
        kotlin.test.assertFalse(approximately_zero(0.001))
    }

    @Test
    fun `approximately_equal`() {
        kotlin.test.assertTrue(approximately_equal(0.5, 0.5 + 1e-8))
        kotlin.test.assertFalse(approximately_equal(0.5, 0.5001))
    }

    @Test
    fun `precisely_equal`() {
        kotlin.test.assertTrue(precisely_equal(0.0, 1e-30))
        kotlin.test.assertFalse(precisely_equal(0.0, 1e-12))
    }

    @Test
    fun `zero_or_one exactly zero or one`() {
        kotlin.test.assertTrue(zero_or_one(0.0))
        kotlin.test.assertTrue(zero_or_one(1.0))
        kotlin.test.assertFalse(zero_or_one(0.5))
    }

    @Test
    fun `pinT clamps to 0_1 range`() {
        kotlin.test.assertEquals(0.0, pinT(-0.001))
        kotlin.test.assertEquals(1.0, pinT(1.001))
        kotlin.test.assertEquals(0.5, pinT(0.5))
    }

    @Test
    fun `between predicate`() {
        kotlin.test.assertTrue(between(0.0, 0.5, 1.0))
        kotlin.test.assertTrue(between(1.0, 0.5, 0.0))
        kotlin.test.assertFalse(between(0.0, 2.0, 1.0))
    }

    @Test
    fun `AlmostEqualUlps for equal values`() {
        kotlin.test.assertTrue(AlmostEqualUlps(1f, 1f))
        kotlin.test.assertTrue(AlmostEqualUlps(0f, 0f))
    }

    @Test
    fun `dInterp linear`() {
        kotlin.test.assertEquals(0.0, dInterp(0.0, 10.0, 0.0))
        kotlin.test.assertEquals(10.0, dInterp(0.0, 10.0, 1.0))
        kotlin.test.assertEquals(5.0, dInterp(0.0, 10.0, 0.5))
    }

    @Test
    fun `dSign returns -1 0 1`() {
        kotlin.test.assertEquals(1, dSign(5.0))
        kotlin.test.assertEquals(0, dSign(0.0))
        kotlin.test.assertEquals(-1, dSign(-3.0))
    }

    @Test
    fun `roughly_equal`() {
        kotlin.test.assertTrue(roughly_equal(0.0, 1e-6))
        kotlin.test.assertFalse(roughly_equal(0.0, 0.01))
    }
}
