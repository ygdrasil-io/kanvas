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
    fun `approximatelyZero`() {
        kotlin.test.assertTrue(PathOpsEpsilon.approximatelyZero(0.0))
        kotlin.test.assertTrue(PathOpsEpsilon.approximatelyZero(1e-8))
        kotlin.test.assertFalse(PathOpsEpsilon.approximatelyZero(0.001))
    }

    @Test
    fun `approximatelyEqual`() {
        kotlin.test.assertTrue(PathOpsEpsilon.approximatelyEqual(0.5, 0.5 + 1e-8))
        kotlin.test.assertFalse(PathOpsEpsilon.approximatelyEqual(0.5, 0.5001))
    }

    @Test
    fun `preciselyEqual`() {
        kotlin.test.assertTrue(PathOpsEpsilon.preciselyEqual(0.0, 1e-30))
        kotlin.test.assertFalse(PathOpsEpsilon.preciselyEqual(0.0, 1e-12))
    }

    @Test
    fun `zeroOrOne exactly zero or one`() {
        kotlin.test.assertTrue(PathOpsEpsilon.zeroOrOne(0.0))
        kotlin.test.assertTrue(PathOpsEpsilon.zeroOrOne(1.0))
        kotlin.test.assertFalse(PathOpsEpsilon.zeroOrOne(0.5))
    }

    @Test
    fun `pinT clamps to 0_1 range`() {
        kotlin.test.assertEquals(0.0, PathOpsEpsilon.pinT(-0.001))
        kotlin.test.assertEquals(1.0, PathOpsEpsilon.pinT(1.001))
        kotlin.test.assertEquals(0.5, PathOpsEpsilon.pinT(0.5))
    }

    @Test
    fun `between predicate`() {
        kotlin.test.assertTrue(PathOpsEpsilon.between(0.0, 0.5, 1.0))
        kotlin.test.assertTrue(PathOpsEpsilon.between(1.0, 0.5, 0.0))
        kotlin.test.assertFalse(PathOpsEpsilon.between(0.0, 2.0, 1.0))
    }

    @Test
    fun `almostEqualUlps for equal values`() {
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(1f, 1f))
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(0f, 0f))
    }

    @Test
    fun `almostEqualUlps enforces boundaries on Float and Double`() {
        val oneFloatBits = 1f.toRawBits()
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(1f, Float.fromBits(oneFloatBits + 15)))
        kotlin.test.assertFalse(PathOpsEpsilon.almostEqualUlps(1f, Float.fromBits(oneFloatBits + 16)))

        val oneDoubleBits = 1.0.toRawBits()
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(1.0, Double.fromBits(oneDoubleBits + 15)))
        kotlin.test.assertFalse(PathOpsEpsilon.almostEqualUlps(1.0, Double.fromBits(oneDoubleBits + 16)))
    }

    @Test
    fun `almostEqualUlps handles signed zero and pinned non-finite values`() {
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(-0f, 0f))
        kotlin.test.assertTrue(PathOpsEpsilon.almostEqualUlps(-0.0, 0.0))
        kotlin.test.assertFalse(
            PathOpsEpsilon.almostEqualUlpsPin(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
        )
        kotlin.test.assertFalse(
            PathOpsEpsilon.almostEqualUlpsPin(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
        )
    }

    @Test
    fun `interpolate linear`() {
        kotlin.test.assertEquals(0.0, PathOpsEpsilon.interpolate(0.0, 10.0, 0.0))
        kotlin.test.assertEquals(10.0, PathOpsEpsilon.interpolate(0.0, 10.0, 1.0))
        kotlin.test.assertEquals(5.0, PathOpsEpsilon.interpolate(0.0, 10.0, 0.5))
    }

    @Test
    fun `sign returns -1 0 1`() {
        kotlin.test.assertEquals(1, PathOpsEpsilon.sign(5.0))
        kotlin.test.assertEquals(0, PathOpsEpsilon.sign(0.0))
        kotlin.test.assertEquals(-1, PathOpsEpsilon.sign(-3.0))
    }

    @Test
    fun `roughlyEqual`() {
        kotlin.test.assertTrue(PathOpsEpsilon.roughlyEqual(0.0, 1e-6))
        kotlin.test.assertFalse(PathOpsEpsilon.roughlyEqual(0.0, 0.01))
    }
}
