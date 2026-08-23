package org.graphiks.math.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.math.vector.Vector2I32

class Point2I32Test {
    @Test
    fun `integer point translation saturates instead of wrapping`() {
        val moved = Point2I32(2_147_483_647, -2_147_483_648) + Vector2I32(1, -1)

        assertEquals(2_147_483_647, moved.x)
        assertEquals(-2_147_483_648, moved.y)
    }

    @Test
    fun `subtracting integer points yields a saturating vector`() {
        val delta: Vector2I32 =
            Point2I32(2_147_483_647, -2_147_483_648) - Point2I32(-1, 1)

        assertEquals(2_147_483_647, delta.x)
        assertEquals(-2_147_483_648, delta.y)
    }
}
