package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HalfFloatTest {
    @Test
    fun `float to half matches the JDK reference on rounding boundaries`() {
        val values = intArrayOf(
            0x330007df,
            0x33800000,
            0x387fe000,
            0x3ffff000,
            0x477fefff,
            0xb30007df.toInt(),
            0xb87fe000.toInt(),
            0xbffff000.toInt(),
        )

        for (bits in values) {
            val value = Float.fromBits(bits)
            assertEquals(
                java.lang.Float.floatToFloat16(value),
                floatToHalf(value),
                "float bits 0x${bits.toUInt().toString(16)}",
            )
        }
    }

    @Test
    fun `float to half matches the JDK reference on a deterministic sample`() {
        var bits = 0u
        repeat(200_000) {
            val value = Float.fromBits(bits.toInt())
            if (!value.isNaN()) {
                assertEquals(
                    java.lang.Float.floatToFloat16(value),
                    floatToHalf(value),
                    "float bits 0x${bits.toString(16)}",
                )
            }
            bits += 0x9e3779b9u
        }
    }

    @Test
    fun `half to float matches the JDK reference exhaustively`() {
        for (bits in 0..0xffff) {
            val half = bits.toShort()
            val expected = java.lang.Float.float16ToFloat(half)
            val actual = halfToFloat(half)
            if (expected.isNaN()) {
                assertTrue(actual.isNaN(), "half bits 0x${bits.toString(16)}")
            } else {
                assertEquals(expected.toRawBits(), actual.toRawBits(), "half bits 0x${bits.toString(16)}")
            }
        }
    }
}
