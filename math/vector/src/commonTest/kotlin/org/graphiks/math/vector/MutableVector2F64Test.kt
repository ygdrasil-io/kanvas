package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `F64 normalization rejects near zero and preserves exact components`() {
        val value = MutableVector2F64(-0.0, 1e-13)

        assertFalse(value.normalizeInPlace())
        assertEquals((-0.0).toBits(), value.x.toBits())
        assertEquals(1e-13, value.y)
    }

    @Test
    fun `F64 normalization rejects NaN and preserves exact components`() {
        val value = MutableVector2F64(Double.NaN, 11.0)

        assertFalse(value.normalizeInPlace())
        assertEquals(Double.NaN.toBits(), value.x.toBits())
        assertEquals(11.0, value.y)
    }

    @Test
    fun `F64 normalization rejects infinity and preserves exact components`() {
        val value = MutableVector2F64(Double.POSITIVE_INFINITY, -5.0)

        assertFalse(value.normalizeInPlace())
        assertEquals(Double.POSITIVE_INFINITY, value.x)
        assertEquals(-5.0, value.y)
    }
}
