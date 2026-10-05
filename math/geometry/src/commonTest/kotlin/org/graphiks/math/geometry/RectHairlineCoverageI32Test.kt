package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RectHairlineCoverageI32Test {
    @Test
    fun `integerRingIsDisjointAndClippedWithoutInventingEdges`() {
        val bands = rectHairlineCoverageBandsI32(
            deviceRectI32 = RectI32(2, 2, 5, 5),
            clipI32 = RectI32(0, 0, 8, 8),
        )

        assertEquals(
            listOf(
                RectI32(2, 2, 6, 3), RectI32(2, 5, 6, 6),
                RectI32(2, 3, 3, 5), RectI32(5, 3, 6, 5),
            ),
            bands,
        )
        val covered = bands.flatMap { band ->
            (band.top until band.bottom).flatMap { y -> (band.left until band.right).map { x -> x to y } }
        }
        assertEquals(12, covered.size)
        assertEquals(covered.size, covered.toSet().size)
        assertEquals(
            setOf(
                2 to 2, 3 to 2, 4 to 2, 5 to 2,
                2 to 3, 5 to 3, 2 to 4, 5 to 4,
                2 to 5, 3 to 5, 4 to 5, 5 to 5,
            ),
            covered.toSet(),
        )
    }

    @Test
    fun `thinRingAndI32EdgesDoNotOverlapOrOverflow`() {
        assertEquals(
            listOf(
                RectI32(2, 2, 4, 3), RectI32(2, 5, 4, 6),
                RectI32(2, 3, 3, 5), RectI32(3, 3, 4, 5),
            ),
            rectHairlineCoverageBandsI32(RectI32(2, 2, 3, 5), RectI32(-8, -8, 8, 8)),
        )
        val heightOne = rectHairlineCoverageBandsI32(
            deviceRectI32 = RectI32(2, 2, 5, 3),
            clipI32 = RectI32(-8, -8, 8, 8),
        )
        assertEquals(listOf(RectI32(2, 2, 6, 3), RectI32(2, 3, 6, 4)), heightOne)
        val heightOneCovered = heightOne.flatMap { band ->
            (band.top until band.bottom).flatMap { y -> (band.left until band.right).map { x -> x to y } }
        }
        assertEquals(8, heightOneCovered.size)
        assertEquals(heightOneCovered.size, heightOneCovered.toSet().size)
        assertEquals(
            setOf(2 to 2, 3 to 2, 4 to 2, 5 to 2, 2 to 3, 3 to 3, 4 to 3, 5 to 3),
            heightOneCovered.toSet(),
        )
        assertEquals(
            listOf(RectI32(-2, 0, 3, 1), RectI32(-2, 2, 3, 3)),
            rectHairlineCoverageBandsI32(
                deviceRectI32 = RectI32(-4, 0, Int.MAX_VALUE, 2),
                clipI32 = RectI32(-2, 0, 3, 4),
            ),
        )
        val clipped = rectHairlineCoverageBandsI32(
            deviceRectI32 = RectI32(-4, 0, Int.MAX_VALUE, 2),
            clipI32 = RectI32(-2, 0, 3, 4),
        )
        assertTrue(clipped.all { it.left >= -2 && it.right <= 3 && it.top >= 0 && it.bottom <= 4 })
    }
}
