package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathPredicatesF64Test {
    @Test
    fun `F64 ULP comparison distinguishes the configured boundary`() {
        val one = 1.0.toRawBits()

        assertTrue(PathPredicatesF64.almostEqualUlps(1.0, Double.fromBits(one + 15)))
        assertFalse(PathPredicatesF64.almostEqualUlps(1.0, Double.fromBits(one + 16)))
    }

    @Test
    fun `F64 ULP comparison preserves the near-zero tolerance`() {
        assertTrue(PathPredicatesF64.almostEqualUlps(0.0, 1.0e-163, maxUlps = 2))
    }

    @Test
    fun `on segment is stable after large translation`() {
        val offset = 1.0e12

        assertTrue(
            PathPredicatesF64.onSegment(
                Point2F64(offset + 5.0, offset + 5.0),
                Point2F64(offset, offset),
                Point2F64(offset + 10.0, offset + 10.0),
            ),
        )
    }

    @Test
    fun `on segment rejects points outside the exact line`() {
        assertFalse(
            PathPredicatesF64.onSegment(
                Point2F64(5.0, 5.25),
                Point2F64(0.0, 0.0),
                Point2F64(10.0, 10.0),
            ),
        )
    }
}
