package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

/**
 * Phase R2 — covers [SkPixelRef]'s lifecycle: dimension / buffer
 * accessors, generation-id bump on `notifyPixelsChanged`, and the
 * one-way [setImmutable] lock.
 */
class SkPixelRefTest {

    private fun ref(w: Int, h: Int): SkPixelRef {
        val rowBytes = w * 4
        return SkPixelRef(w, h, ByteBuffer.allocate(rowBytes * h), rowBytes)
    }

    @Test
    fun `accessors expose constructor args`() {
        val buf = ByteBuffer.allocate(16)
        val r = SkPixelRef(2, 2, buf, 8)
        assertEquals(2, r.width())
        assertEquals(2, r.height())
        assertEquals(8, r.rowBytes())
        assertEquals(buf, r.pixels())
    }

    @Test
    fun `fresh ref has positive generationID`() {
        val r = ref(4, 4)
        assertTrue(r.generationID() != 0, "0 is reserved as 'unassigned'")
    }

    @Test
    fun `notifyPixelsChanged bumps the generationID`() {
        val r = ref(4, 4)
        val before = r.generationID()
        r.notifyPixelsChanged()
        val after = r.generationID()
        assertNotEquals(before, after)
    }

    @Test
    fun `distinct refs have distinct generationIDs`() {
        val a = ref(2, 2)
        val b = ref(2, 2)
        assertNotEquals(a.generationID(), b.generationID())
    }

    @Test
    fun `setImmutable freezes the ref`() {
        val r = ref(2, 2)
        assertFalse(r.isImmutable())
        r.setImmutable()
        assertTrue(r.isImmutable())
    }

    @Test
    fun `setImmutable is idempotent`() {
        val r = ref(2, 2)
        r.setImmutable()
        r.setImmutable()
        assertTrue(r.isImmutable())
    }

    @Test
    fun `notifyPixelsChanged on immutable ref is a no-op`() {
        val r = ref(2, 2)
        r.setImmutable()
        val before = r.generationID()
        r.notifyPixelsChanged()
        assertEquals(before, r.generationID(),
            "immutable refs must not acquire a new gen id")
    }
}
