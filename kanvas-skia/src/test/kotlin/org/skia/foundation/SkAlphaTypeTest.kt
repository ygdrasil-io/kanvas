package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkAlphaTypeTest {

    @Test
    fun `four enum members in upstream declaration order`() {
        assertEquals(
            listOf(
                SkAlphaType.kUnknown,
                SkAlphaType.kOpaque,
                SkAlphaType.kPremul,
                SkAlphaType.kUnpremul,
            ),
            SkAlphaType.entries,
        )
    }

    @Test
    fun `ordinals match upstream enum order`() {
        // Upstream `enum SkAlphaType : int` with explicit declaration order.
        assertEquals(0, SkAlphaType.kUnknown.ordinal)
        assertEquals(1, SkAlphaType.kOpaque.ordinal)
        assertEquals(2, SkAlphaType.kPremul.ordinal)
        assertEquals(3, SkAlphaType.kUnpremul.ordinal)
    }

    @Test
    fun `isOpaque is true only for kOpaque`() {
        assertTrue(SkAlphaType.kOpaque.isOpaque())
        assertFalse(SkAlphaType.kUnknown.isOpaque())
        assertFalse(SkAlphaType.kPremul.isOpaque())
        assertFalse(SkAlphaType.kUnpremul.isOpaque())
    }

    @Test
    fun `isValid is true for everything except kUnknown`() {
        assertFalse(SkAlphaType.kUnknown.isValid())
        assertTrue(SkAlphaType.kOpaque.isValid())
        assertTrue(SkAlphaType.kPremul.isValid())
        assertTrue(SkAlphaType.kUnpremul.isValid())
    }
}
