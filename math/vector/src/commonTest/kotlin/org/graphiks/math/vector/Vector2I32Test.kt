package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals

class Vector2I32Test {
    @Test
    fun `I32 arithmetic saturates all four operations`() {
        val added = Vector2I32(2_147_483_647, -2_147_483_648) + Vector2I32(1, -1)
        assertEquals(2_147_483_647, added.x)
        assertEquals(-2_147_483_648, added.y)

        val subtracted = Vector2I32(-2_147_483_648, 2_147_483_647) - Vector2I32(1, -1)
        assertEquals(-2_147_483_648, subtracted.x)
        assertEquals(2_147_483_647, subtracted.y)

        val negated = -Vector2I32(-2_147_483_648, 2_147_483_647)
        assertEquals(2_147_483_647, negated.x)
        assertEquals(-2_147_483_647, negated.y)

        val multiplied = Vector2I32(2_147_483_647, -2_147_483_648) * 2
        assertEquals(2_147_483_647, multiplied.x)
        assertEquals(-2_147_483_648, multiplied.y)
    }

    @Test
    fun `I32 accumulation saturates after widened products`() {
        val value = Vector2I32(Int.MIN_VALUE, Int.MIN_VALUE)

        assertEquals(9_223_372_036_854_775_807L, value.dot(value))
    }

    @Test
    fun `I32 dot and cross use widened scalar expectations`() {
        val a = Vector2I32(30_000, -40_000)
        val b = Vector2I32(-20_000, 50_000)

        assertEquals(-2_600_000_000L, a.dot(b))
        assertEquals(700_000_000L, a.cross(b))
    }
}
