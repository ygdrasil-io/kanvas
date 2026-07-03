package org.graphiks.kanvas.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ColorTypeTest {
    @Test
    fun `bytesPerPixel matches expected values`() {
        assertEquals(4, ColorType.RGBA_8888.bytesPerPixel)
        assertEquals(4, ColorType.BGRA_8888.bytesPerPixel)
        assertEquals(1, ColorType.ALPHA_8.bytesPerPixel)
        assertEquals(1, ColorType.GRAY_8.bytesPerPixel)
        assertEquals(8, ColorType.RGBA_F16.bytesPerPixel)
        assertEquals(2, ColorType.RGB_565.bytesPerPixel)
        assertEquals(2, ColorType.ARGB_4444.bytesPerPixel)
    }
}
