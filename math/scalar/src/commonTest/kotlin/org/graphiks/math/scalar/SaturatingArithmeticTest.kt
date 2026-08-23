package org.graphiks.math.scalar

import kotlin.test.Test
import kotlin.test.assertEquals

class SaturatingArithmeticTest {
    @Test
    fun `I32 addition saturates at both bounds`() {
        assertEquals(2_147_483_647, saturatingAddI32(2_147_483_647, 1))
        assertEquals(-2_147_483_648, saturatingAddI32(-2_147_483_648, -1))
        assertEquals(42, saturatingAddI32(19, 23))
    }

    @Test
    fun `I32 subtraction saturates at both bounds`() {
        assertEquals(-2_147_483_648, saturatingSubtractI32(-2_147_483_648, 1))
        assertEquals(2_147_483_647, saturatingSubtractI32(2_147_483_647, -1))
        assertEquals(-4, saturatingSubtractI32(19, 23))
    }

    @Test
    fun `I32 negation saturates the asymmetric minimum`() {
        assertEquals(2_147_483_647, saturatingNegateI32(-2_147_483_648))
        assertEquals(-2_147_483_647, saturatingNegateI32(2_147_483_647))
        assertEquals(8, saturatingNegateI32(-8))
    }

    @Test
    fun `I32 multiplication saturates at both bounds`() {
        assertEquals(2_147_483_647, saturatingMultiplyI32(2_147_483_647, 2))
        assertEquals(-2_147_483_648, saturatingMultiplyI32(-2_147_483_648, 2))
        assertEquals(2_147_483_647, saturatingMultiplyI32(-2_147_483_648, -1))
        assertEquals(-42, saturatingMultiplyI32(-7, 6))
    }

    @Test
    fun `I64 addition detects overflow before the operation`() {
        assertEquals(9_223_372_036_854_775_807L, saturatingAddI64(9_223_372_036_854_775_807L, 1L))
        assertEquals(-9_223_372_036_854_775_807L - 1L, saturatingAddI64(Long.MIN_VALUE, -1L))
        assertEquals(42L, saturatingAddI64(19L, 23L))
    }

    @Test
    fun `I64 subtraction detects overflow before the operation`() {
        assertEquals(-9_223_372_036_854_775_807L - 1L, saturatingSubtractI64(Long.MIN_VALUE, 1L))
        assertEquals(9_223_372_036_854_775_807L, saturatingSubtractI64(Long.MAX_VALUE, -1L))
        assertEquals(-4L, saturatingSubtractI64(19L, 23L))
    }
}
