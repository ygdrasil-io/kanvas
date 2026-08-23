package org.graphiks.math.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MutableVector3F32Test {
    @Test
    fun `3D mutable operations update literal components`() {
        val value = MutableVector3F32(1f, 2f, 3f)

        value.add(Vector3F32(4f, 5f, 6f))
        assertEquals(5f, value.x)
        assertEquals(7f, value.y)
        assertEquals(9f, value.z)
        value.subtract(Vector3F32(1f, 2f, 3f))
        value.scaleBy(0.5f)
        assertEquals(2f, value.x)
        assertEquals(2.5f, value.y)
        assertEquals(3f, value.z)
    }

    @Test
    fun `3D mutable normalization scales large finite components`() {
        val value = MutableVector3F32(1e30f, 1e30f, 1e30f)

        assertTrue(value.normalizeInPlace())
        assertEquals(0.57735026f, value.x, 1e-6f)
        assertEquals(0.57735026f, value.y, 1e-6f)
        assertEquals(0.57735026f, value.z, 1e-6f)
    }

    @Test
    fun `3D mutable normalization rejects zero`() {
        val value = MutableVector3F32(0f, 0f, 0f)

        assertFalse(value.normalizeInPlace())
        assertEquals(0f, value.x)
        assertEquals(0f, value.y)
        assertEquals(0f, value.z)
    }

    @Test
    fun `3D mutable conversion and component checks do not alias`() {
        val mutable = MutableVector3F32(2f, 3f, 4f)

        assertTrue(mutable.hasSameComponentsAs(Vector3F32(2f, 3f, 4f)))
        assertTrue(mutable.hasSameComponentsAs(MutableVector3F32(2f, 3f, 4f)))
        val immutable = mutable.toImmutable()
        mutable.z = 9f
        assertEquals(4f, immutable.z)
        assertEquals(9f, mutable.z)
    }
}
