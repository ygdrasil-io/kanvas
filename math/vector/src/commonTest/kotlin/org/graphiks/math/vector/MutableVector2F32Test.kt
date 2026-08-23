package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `normalize in place rejects near zero and clears components`() {
        val value = MutableVector2F32(1e-8f, -1e-8f)

        assertFalse(value.normalizeInPlace())
        assertEquals(0f, value.x)
        assertEquals(0f, value.y)
    }

    @Test
    fun `same component checks support mutable and immutable values`() {
        val value = MutableVector2F32(2f, 3f)

        assertTrue(value.hasSameComponentsAs(MutableVector2F32(2f, 3f)))
        assertTrue(value.hasSameComponentsAs(Vector2F32(2f, 3f)))
        assertFalse(value.hasSameComponentsAs(Vector2F32(2f, 4f)))
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

class MutableVector2F64Test {
    @Test
    fun `F64 mutable normalization and conversion use literal values`() {
        val mutable = MutableVector2F64(3.0, 4.0)

        assertTrue(mutable.normalizeInPlace())
        assertEquals(0.6, mutable.x, 1e-12)
        assertEquals(0.8, mutable.y, 1e-12)
        val immutable = mutable.toImmutable()
        mutable.x = 2.0
        assertEquals(0.6, immutable.x, 1e-12)
    }
}
