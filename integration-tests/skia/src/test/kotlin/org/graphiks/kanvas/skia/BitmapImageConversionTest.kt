package org.graphiks.kanvas.skia

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BitmapImageConversionTest {
    @Test
    fun `converts a supported bitmap to an image for GM rendering`() {
        val bitmap = Bitmap(1, 1).also { it.setPixel(0, 0, ColorARGB.Red) }

        val image = bitmap.toImageForGm("fixture")

        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertEquals("fixture", image.sourceId)
        assertArrayEquals(byteArrayOf(-1, 0, 0, -1), image.pixels)
    }

    @Test
    fun `refuses an unsupported bitmap color profile with its diagnostic code`() {
        val bitmap = Bitmap(
            1,
            1,
            colorSpace = ImageColorSpace.fromColorProfile(ColorProfile.unsupported("test.gm-profile.unsupported")),
        )

        val failure = assertThrows<IllegalArgumentException> { bitmap.toImageForGm() }

        assertEquals(
            "GM bitmap uses unsupported image color profile: test.gm-profile.unsupported",
            failure.message,
        )
    }
}
