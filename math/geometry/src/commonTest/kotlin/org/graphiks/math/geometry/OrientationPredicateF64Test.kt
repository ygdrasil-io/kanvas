package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class OrientationPredicateF64Test {
    @Test
    fun `orientation resolves cancellation that rounds the naive determinant`() {
        val n = 134_217_728.0
        val a = Point2F64(0.0, 0.0)
        val b = Point2F64(n + 1.0, n)
        val c = Point2F64(n, n - 1.0)

        assertEquals(-1, OrientationPredicateF64.sign(a, b, c))
        assertEquals(1, OrientationPredicateF64.sign(a, c, b))
    }

    @Test
    fun `orientation reports zero for collinear points`() {
        assertEquals(
            0,
            OrientationPredicateF64.sign(
                Point2F64(-5.0, -5.0),
                Point2F64(0.0, 0.0),
                Point2F64(5.0, 5.0),
            ),
        )
    }
}
