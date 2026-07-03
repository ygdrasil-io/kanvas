package org.graphiks.kanvas.image

import org.graphiks.math.halfToFloat
import org.graphiks.math.floatToHalf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HalfFloatTest {
    @Test
    fun `zero round-trips`() {
        assertEquals(0f, halfToFloat(0.toShort()))
        assertEquals(0.toShort(), floatToHalf(0f))
    }

    @Test
    fun `one round-trips`() {
        assertEquals(1f, halfToFloat(floatToHalf(1f)), 0.001f)
    }

    @Test
    fun `negative round-trips`() {
        val h = floatToHalf(-1f)
        assertEquals(-1f, halfToFloat(h), 0.001f)
    }

    @Test
    fun `small values round-trip`() {
        val v = 0.5f
        val h = floatToHalf(v)
        assertEquals(v, halfToFloat(h), 0.001f)
    }
}
