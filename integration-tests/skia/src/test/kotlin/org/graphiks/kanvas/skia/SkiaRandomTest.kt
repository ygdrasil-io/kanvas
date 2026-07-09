package org.graphiks.kanvas.skia

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkiaRandomTest {
    @Test
    fun `matches SkRandom nextU golden stream for seed zero`() {
        val random = SkiaRandom(0u)

        assertArrayEquals(
            arrayOf(0x4f9643a0u, 0x018cb5ecu, 0x79ea6f5cu, 0xbdc9934eu, 0x2fcfce7bu),
            Array(5) { random.nextU() },
        )
    }

    @Test
    fun `matches SkRandom signed and float golden values`() {
        val signed = SkiaRandom(42u)
        assertEquals(-836240769, signed.nextS())
        assertEquals(-685989928, signed.nextS())
        assertEquals(-175912196, signed.nextS())

        val floats = SkiaRandom(1u)
        assertEquals(0.015994906f, floats.nextF(), 0.0000005f)
        assertEquals(0.41842508f, floats.nextF(), 0.0000005f)
        assertEquals(0.7300186f, floats.nextF(), 0.0000005f)
    }
}
