package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit coverage for [QuadAAFlags] — verifies that the bit values match
 * upstream Skia's `SkCanvas::QuadAAFlags` constants
 * (`kLeft = 0b0001`, `kTop = 0b0010`, `kRight = 0b0100`,
 * `kBottom = 0b1000`, `kNone = 0`, `kAll = 0b1111`) and that the
 * [QuadAAFlags.Companion.of] helper composes flag bitmasks by OR.
 */
class QuadAAFlagsTest {

    @Test
    fun `enum bit values mirror upstream Skia constants`() {
        assertEquals(0b0000, QuadAAFlags.kNone.bits)
        assertEquals(0b0001, QuadAAFlags.kLeft.bits)
        assertEquals(0b0010, QuadAAFlags.kTop.bits)
        assertEquals(0b0100, QuadAAFlags.kRight.bits)
        assertEquals(0b1000, QuadAAFlags.kBottom.bits)
        assertEquals(0b1111, QuadAAFlags.kAll.bits)
    }

    @Test
    fun `companion constants match enum bits`() {
        assertEquals(QuadAAFlags.kNone.bits, QuadAAFlags.kNone_QuadAAFlags)
        assertEquals(QuadAAFlags.kLeft.bits, QuadAAFlags.kLeft_QuadAAFlag)
        assertEquals(QuadAAFlags.kTop.bits, QuadAAFlags.kTop_QuadAAFlag)
        assertEquals(QuadAAFlags.kRight.bits, QuadAAFlags.kRight_QuadAAFlag)
        assertEquals(QuadAAFlags.kBottom.bits, QuadAAFlags.kBottom_QuadAAFlag)
        assertEquals(QuadAAFlags.kAll.bits, QuadAAFlags.kAll_QuadAAFlags)
    }

    @Test
    fun `of() with no arguments returns 0`() {
        assertEquals(0, QuadAAFlags.of())
    }

    @Test
    fun `of() with a single flag returns its bit`() {
        assertEquals(0b0001, QuadAAFlags.of(QuadAAFlags.kLeft))
        assertEquals(0b1000, QuadAAFlags.of(QuadAAFlags.kBottom))
    }

    @Test
    fun `of() composes multiple flags by OR`() {
        assertEquals(
            0b0011,
            QuadAAFlags.of(QuadAAFlags.kLeft, QuadAAFlags.kTop),
        )
        assertEquals(
            0b0101,
            QuadAAFlags.of(QuadAAFlags.kLeft, QuadAAFlags.kRight),
        )
        assertEquals(
            0b1100,
            QuadAAFlags.of(QuadAAFlags.kBottom, QuadAAFlags.kRight),
        )
    }

    @Test
    fun `of() with all four side flags equals kAll_QuadAAFlags`() {
        val mask = QuadAAFlags.of(
            QuadAAFlags.kLeft,
            QuadAAFlags.kTop,
            QuadAAFlags.kRight,
            QuadAAFlags.kBottom,
        )
        assertEquals(QuadAAFlags.kAll_QuadAAFlags, mask)
        assertEquals(0b1111, mask)
    }

    @Test
    fun `of() is OR-idempotent on duplicates`() {
        // Duplicates should not change the resulting bitmask.
        val withDuplicate = QuadAAFlags.of(
            QuadAAFlags.kLeft,
            QuadAAFlags.kLeft,
            QuadAAFlags.kTop,
        )
        val withoutDuplicate = QuadAAFlags.of(QuadAAFlags.kLeft, QuadAAFlags.kTop)
        assertEquals(withoutDuplicate, withDuplicate)
        assertEquals(0b0011, withDuplicate)
    }

    @Test
    fun `of(kNone) is zero and OR with kNone is a no-op`() {
        assertEquals(0, QuadAAFlags.of(QuadAAFlags.kNone))
        assertEquals(
            QuadAAFlags.kRight.bits,
            QuadAAFlags.of(QuadAAFlags.kNone, QuadAAFlags.kRight),
        )
    }

    @Test
    fun `of(kAll) returns kAll bits regardless of other flags`() {
        // kAll is already 0b1111 — combining with any side flag is a no-op.
        val mask = QuadAAFlags.of(QuadAAFlags.kAll, QuadAAFlags.kLeft)
        assertEquals(QuadAAFlags.kAll_QuadAAFlags, mask)
    }

    @Test
    fun `side flags are pairwise distinct`() {
        val sides = listOf(
            QuadAAFlags.kLeft,
            QuadAAFlags.kTop,
            QuadAAFlags.kRight,
            QuadAAFlags.kBottom,
        )
        for (i in sides.indices) {
            for (j in i + 1 until sides.size) {
                assertNotEquals(
                    sides[i].bits,
                    sides[j].bits,
                    "Side flags ${sides[i]} and ${sides[j]} collide",
                )
            }
        }
    }
}
