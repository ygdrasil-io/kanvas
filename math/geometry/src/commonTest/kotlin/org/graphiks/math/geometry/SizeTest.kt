package org.graphiks.math.geometry

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class SizeTest {

    @Test
    fun `SizeI32 Make stores width and height`() {
        val s = SizeI32.Make(3, 4)
        assertFalse(s.isEmpty())
        assertFalse(s.isZero())
    }

    @Test
    fun `SizeI32 isEmpty when negative or zero`() {
        assertTrue(SizeI32.MakeEmpty().isEmpty())
        assertTrue(SizeI32.Make(0, 0).isEmpty())
        assertTrue(SizeI32.Make(-1, 5).isEmpty())
        assertTrue(SizeI32.Make(5, -1).isEmpty())
        assertFalse(SizeI32.Make(5, 5).isEmpty())
    }

    @Test
    fun `SizeI32 isZero`() {
        assertTrue(SizeI32.Make(0, 0).isZero())
        assertFalse(SizeI32.Make(1, 0).isZero())
    }

    @Test
    fun `SizeF32 Make stores width and height`() {
        val s = SizeF32.Make(3f, 4f)
        assertFalse(s.isEmpty())
    }

    @Test
    fun `SizeF32 isEmpty when zero or negative`() {
        assertTrue(SizeF32.MakeEmpty().isEmpty())
        assertTrue(SizeF32.Make(0f, 0f).isEmpty())
        assertTrue(SizeF32.Make(-1f, 5f).isEmpty())
        assertTrue(SizeF32.Make(5f, -1f).isEmpty())
        assertFalse(SizeF32.Make(5f, 5f).isEmpty())
    }

    @Test
    fun `SizeF32 Make from SizeI32 promotes`() {
        val s = SizeF32.Make(SizeI32.Make(3, 4))
        assertFalse(s.isEmpty())
    }
}
