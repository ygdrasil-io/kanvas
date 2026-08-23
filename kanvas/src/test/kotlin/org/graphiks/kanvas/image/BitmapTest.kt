package org.graphiks.kanvas.image

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BitmapTest {

    @Test
    fun `construction allocates correct byte array size`() {
        val bmp = Bitmap(10, 10)
        assertEquals(400, bmp.pixels.size)
        assertEquals(10, bmp.width)
        assertEquals(10, bmp.height)
        assertEquals(ColorType.RGBA_8888, bmp.colorType)
    }

    @Test
    fun `construction with F16 allocates 8 bytes per pixel`() {
        val bmp = Bitmap(10, 10, ColorType.RGBA_F16)
        assertEquals(800, bmp.pixels.size)
    }

    @Test
    fun `construction rejects formats without allocation capability`() {
        assertThrows<IllegalArgumentException> { Bitmap(1, 1, ColorType.UNKNOWN) }
        assertThrows<IllegalArgumentException> { Bitmap(1, 1, ColorType.RGBA_1010102) }
    }

    @Test
    fun `F16 normalized supports the existing F16 pixel operations`() {
        val bitmap = Bitmap(1, 1, ColorType.RGBA_F16_NORM)

        bitmap.setPixel(0, 0, Color.RED)
        assertEquals(Color.RED, bitmap.getPixel(0, 0))

        bitmap.eraseColor(Color.BLUE)
        assertEquals(Color.BLUE, bitmap.getPixel(0, 0))
    }

    @Test
    fun `ImageColorSpace is retained and renderer conversion refuses unsupported profiles`() {
        val supported = ImageColorSpace.linearSrgb()
        val supportedBitmap = Bitmap(1, 1, colorSpace = supported)
        assertSame(supported, supportedBitmap.colorSpace)
        assertEquals(org.graphiks.kanvas.color.ColorSpace.LINEAR_SRGB, supportedBitmap.toImageOrNull()!!.colorSpace)

        val unsupported = ImageColorSpace.fromColorProfile(ColorProfile.unsupported("test.profile.unsupported"))
        val unsupportedBitmap = Bitmap(1, 1, colorSpace = unsupported)
        assertSame(unsupported, unsupportedBitmap.colorSpace)
        assertNull(unsupportedBitmap.toImageOrNull())
    }

    @Test
    fun `setPixel and getPixel round-trip RGBA`() {
        val bmp = Bitmap(2, 2)
        bmp.setPixel(0, 0, Color.RED)
        assertEquals(Color.RED, bmp.getPixel(0, 0))
    }

    @Test
    fun `setArgb and getArgb round-trip packed Color bits`() {
        val bmp = Bitmap(2, 2)
        val argb = 0x8044AA11.toInt()

        bmp.setArgb(1, 0, argb)

        assertEquals(argb, bmp.getArgb(1, 0))
        assertEquals(Color.fromArgbInt(argb), bmp.getPixel(1, 0))
    }

    @Test
    fun `setPixel out of bounds is no-op`() {
        val bmp = Bitmap(2, 2)
        bmp.setPixel(10, 10, Color.RED)
        assertEquals(Color(0u), bmp.getPixel(1, 1))
    }

    @Test
    fun `getPixel out of bounds throws`() {
        val bmp = Bitmap(2, 2)
        assertThrows<IndexOutOfBoundsException> {
            bmp.getPixel(10, 10)
        }
    }

    @Test
    fun `eraseColor fills all pixels`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.RED)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                assertEquals(Color.RED, bmp.getPixel(x, y))
            }
        }
    }

    @Test
    fun `eraseArea fills sub-region only`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.BLUE)
        bmp.eraseArea(Rect(1f, 1f, 3f, 3f), Color.RED)
        assertEquals(Color.RED, bmp.getPixel(1, 1))
        assertEquals(Color.RED, bmp.getPixel(2, 2))
        assertEquals(Color.BLUE, bmp.getPixel(0, 0))
        assertEquals(Color.BLUE, bmp.getPixel(3, 3))
    }

    @Test
    fun `extractSubset copies pixel data`() {
        val bmp = Bitmap(4, 4)
        bmp.eraseColor(Color.RED)
        bmp.setPixel(0, 0, Color.BLUE)
        val subset = bmp.extractSubset(Rect(0f, 0f, 2f, 2f))
        assertEquals(2, subset.width)
        assertEquals(2, subset.height)
        assertEquals(Color.BLUE, subset.getPixel(0, 0))
        assertEquals(Color.RED, subset.getPixel(1, 1))
    }

    @Test
    fun `toImageOrNull returns independent copy`() {
        val bmp = Bitmap(2, 2)
        bmp.eraseColor(Color.RED)
        val img = requireNotNull(bmp.toImageOrNull())
        bmp.setPixel(0, 0, Color.BLUE)
        assertEquals(Color.RED, Color.fromRGBA(1f, 0f, 0f, 1f))
        assertEquals(AlphaType.UNPREMUL, img.alphaType)
    }

    @Test
    fun `toImageOrNull uses the color type default alpha type for A8`() {
        val image = requireNotNull(Bitmap(1, 1, ColorType.ALPHA_8).toImageOrNull())

        assertEquals(AlphaType.PREMUL, image.alphaType)
    }

    @Test
    fun `fromImage copies image pixels`() {
        val pixels = byteArrayOf(
            0xFF.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x00.toByte(), 0xFF.toByte(),
        )
        val img = Image(2, 2, ColorType.RGBA_8888, "test", pixels)
        val bmp = Bitmap.fromImage(img)
        assertEquals(img.width, bmp.width)
        assertEquals(img.height, bmp.height)
        assertArrayEquals(img.pixels, bmp.pixels)
    }

    @Test
    fun `makeShader returns correct wrapping`() {
        val bmp = Bitmap(2, 2)
        bmp.eraseColor(Color.RED)
        val shader = bmp.makeShader(TileMode.REPEAT, TileMode.MIRROR, SamplingOptions.LINEAR, Matrix3x3F32.Identity)
        val wrapped = shader as org.graphiks.kanvas.paint.Shader.WithLocalMatrix
        val inner = wrapped.shader as org.graphiks.kanvas.paint.Shader.Image
        assertEquals(TileMode.REPEAT, inner.tileModeX)
        assertEquals(TileMode.MIRROR, inner.tileModeY)
        assertEquals(SamplingOptions.LINEAR, inner.sampling)
    }
}
