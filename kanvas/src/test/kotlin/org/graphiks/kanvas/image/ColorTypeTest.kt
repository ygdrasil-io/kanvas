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

    @Test
    fun `RGBA and BGRA retain the same color with their native byte order`() {
        val color = org.graphiks.kanvas.types.Color.fromRGBA(1f, 0.5f, 0.25f, 1f)
        val rgba = Bitmap(1, 1, ColorType.RGBA_8888).also { it.setPixel(0, 0, color) }
        val bgra = Bitmap(1, 1, ColorType.BGRA_8888).also { it.setPixel(0, 0, color) }

        assertEquals(color, rgba.getPixel(0, 0))
        assertEquals(color, bgra.getPixel(0, 0))
        assertEquals(255.toByte(), rgba.pixels[0])
        assertEquals(64.toByte(), rgba.pixels[2])
        assertEquals(64.toByte(), bgra.pixels[0])
        assertEquals(255.toByte(), bgra.pixels[2])
    }
}
