package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class MutableVector2F32Test {
    @Test
    fun `mutable operations update literal components`() {
        val value = MutableVector2F32(1f, 2f)

        value.add(Vector2F32(3f, 4f))
        assertEquals(4f, value.x)
        assertEquals(6f, value.y)
        value.subtract(Vector2F32(1f, 2f))
        assertEquals(3f, value.x)
        assertEquals(4f, value.y)
        value.scaleBy(2f)
        assertEquals(6f, value.x)
        assertEquals(8f, value.y)
    }

    @Test
    fun `normalize in place returns success with literal unit components`() {
        val value = MutableVector2F32(3f, 4f)

        assertTrue(value.normalizeInPlace())
        assertEquals(0.6f, value.x, 1e-6f)
        assertEquals(0.8f, value.y, 1e-6f)
    }

    @Test
    fun `normalize in place rejects near zero and preserves exact components`() {
        val value = MutableVector2F32(-0f, -1e-8f)

        assertFalse(value.normalizeInPlace())
        assertEquals((-0f).toBits(), value.x.toBits())
        assertEquals(-1e-8f, value.y)
    }

    @Test
    fun `normalize in place rejects NaN and preserves exact components`() {
        val value = MutableVector2F32(Float.NaN, 7f)

        assertFalse(value.normalizeInPlace())
        assertEquals(Float.NaN.toBits(), value.x.toBits())
        assertEquals(7f, value.y)
    }

    @Test
    fun `normalize in place rejects infinity and preserves exact components`() {
        val value = MutableVector2F32(Float.NEGATIVE_INFINITY, -3f)

        assertFalse(value.normalizeInPlace())
        assertEquals(Float.NEGATIVE_INFINITY, value.x)
        assertEquals(-3f, value.y)
    }

    @Test
    fun `same component checks support mutable and immutable values`() {
        val value = MutableVector2F32(2f, 3f)

        assertTrue(value.hasSameComponentsAs(MutableVector2F32(2f, 3f)))
        assertTrue(value.hasSameComponentsAs(Vector2F32(2f, 3f)))
        assertFalse(value.hasSameComponentsAs(Vector2F32(2f, 4f)))
    }

    @Test
    fun `distinct mutables retain reference equality with matching components`() {
        val first = MutableVector2F32(2f, 3f)
        val second = MutableVector2F32(2f, 3f)

        assertNotSame(first, second)
        assertNotEquals(first, second)
        assertTrue(first.hasSameComponentsAs(second))
    }

    @Test
    fun `immutable conversion copies components without aliasing`() {
        val mutable = MutableVector2F32(2f, 3f)
        val immutable = mutable.toImmutable()

        mutable.x = 9f
        assertEquals(2f, immutable.x)
        assertEquals(9f, mutable.x)
    }
}
