package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathPredicatesF32Test {
    @Test
    fun `default limits are positive and internally coherent`() {
        val limits = PathOpsLimitsI32()

        assertEquals(32, limits.maxSubdivisionDepth)
        assertEquals(65_536, limits.maxFlattenedEdgesPerOperand)
        assertEquals(16_777_216, limits.maxCandidateProbes)
        assertTrue(limits.maxHalfEdges >= limits.maxVertices * 2)
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxSubdivisionDepth = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxFlattenedEdgesPerOperand = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxIntersections = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxVertices = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxHalfEdges = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxCandidateProbes = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PathOpsLimitsI32(maxCandidateProbes = -1)
        }
    }

    @Test
    fun `F32 ULP comparison distinguishes the configured boundary`() {
        val one = 1f.toRawBits()

        assertTrue(PathPredicatesF32.almostEqualUlps(1f, Float.fromBits(one + 15)))
        assertFalse(PathPredicatesF32.almostEqualUlps(1f, Float.fromBits(one + 16)))
    }

    @Test
    fun `F32 ULP comparison preserves the near-zero tolerance`() {
        assertTrue(PathPredicatesF32.almostEqualUlps(0f, 1.0e-9f, maxUlps = 2))
    }
}
