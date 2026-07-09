package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GPUImagePixelsTest {
    @Test
    fun `expands RGB565 pixels to RGBA upload bytes`() {
        val bitmap = Bitmap(128, 128, ColorType.RGB_565)
        bitmap.eraseColor(Color.RED)

        val rgba = bitmap.toImage().expandToRgbaForGpu()

        assertEquals(128 * 128 * 4, rgba.size)
        assertEquals(255, rgba[0].toInt() and 0xFF)
        assertEquals(0, rgba[1].toInt() and 0xFF)
        assertEquals(0, rgba[2].toInt() and 0xFF)
        assertEquals(255, rgba[3].toInt() and 0xFF)
    }

    @Test
    fun `expands gray8 pixels to opaque grayscale upload bytes`() {
        val bitmap = Bitmap(1, 1, ColorType.GRAY_8)
        bitmap.setPixel(0, 0, Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f))

        val rgba = bitmap.toImage().expandToRgbaForGpu()

        assertEquals(4, rgba.size)
        assertEquals(rgba[0], rgba[1])
        assertEquals(rgba[1], rgba[2])
        assertEquals(255, rgba[3].toInt() and 0xFF)
    }
}
