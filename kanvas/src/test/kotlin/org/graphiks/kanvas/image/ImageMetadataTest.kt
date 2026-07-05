package org.graphiks.kanvas.image

import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageMetadataTest {
    @Test
    fun `AlphaType reports opacity and validity`() {
        assertFalse(AlphaType.UNKNOWN.isValid())
        assertFalse(AlphaType.UNKNOWN.isOpaque())
        assertTrue(AlphaType.OPAQUE.isValid())
        assertTrue(AlphaType.OPAQUE.isOpaque())
        assertTrue(AlphaType.PREMUL.isValid())
        assertTrue(AlphaType.UNPREMUL.isValid())
    }

    @Test
    fun `ImageInfo exposes dimensions bounds row bytes and copies`() {
        val info = ImageInfo.make(
            width = 3,
            height = 4,
            colorType = ColorType.RGBA_8888,
            alphaType = AlphaType.UNPREMUL,
            colorSpace = ColorSpace.SRGB,
        )

        assertEquals(SkISize.Make(3, 4), info.dimensions())
        assertEquals(SkIRect.MakeWH(3, 4), info.bounds())
        assertFalse(info.isEmpty())
        assertFalse(info.isOpaque())
        assertEquals(4, info.bytesPerPixel())
        assertEquals(12, info.minRowBytes())
        assertEquals(info.copy(width = 5, height = 6), info.makeWH(5, 6))
        assertEquals(info.copy(alphaType = AlphaType.OPAQUE), info.makeAlphaType(AlphaType.OPAQUE))
    }

    @Test
    fun `ImageInfo convenience factories pick Kanvas defaults`() {
        assertEquals(
            ImageInfo(2, 3, ColorType.RGBA_8888, AlphaType.UNPREMUL, ColorSpace.SRGB),
            ImageInfo.makeN32(2, 3),
        )
        assertEquals(
            ImageInfo(2, 3, ColorType.RGBA_8888, AlphaType.PREMUL, ColorSpace.SRGB),
            ImageInfo.makeN32Premul(2, 3),
        )
        assertEquals(
            ImageInfo(2, 3, ColorType.RGB_565, AlphaType.OPAQUE, ColorSpace.SRGB),
            ImageInfo.makeRgb565(2, 3),
        )
        assertEquals(
            ImageInfo(2, 3, ColorType.GRAY_8, AlphaType.OPAQUE, ColorSpace.SRGB),
            ImageInfo.makeGray8(2, 3),
        )
    }

    @Test
    fun `EncodedImageFormat covers active and deferred formats`() {
        assertEquals(EncodedImageFormat.PNG, EncodedImageFormat.valueOf("PNG"))
        assertEquals(EncodedImageFormat.JPEG, EncodedImageFormat.valueOf("JPEG"))
        assertEquals(EncodedImageFormat.AVIF, EncodedImageFormat.valueOf("AVIF"))
        assertEquals(EncodedImageFormat.JPEGXL, EncodedImageFormat.valueOf("JPEGXL"))
    }

    @Test
    fun `EncodedOrigin maps exif values and matrices`() {
        assertFalse(EncodedOrigin.TOP_LEFT.swapsWidthHeight())
        assertTrue(EncodedOrigin.RIGHT_TOP.swapsWidthHeight())
        assertEquals(EncodedOrigin.RIGHT_TOP, EncodedOrigin.fromExifValue(6))
        assertEquals(EncodedOrigin.TOP_LEFT, EncodedOrigin.fromExifValue(99))

        val matrix = EncodedOrigin.RIGHT_TOP.toMatrix(10, 20)
        assertEquals(0f, matrix.scaleX)
        assertEquals(-1f, matrix.skewX)
        assertEquals(10f, matrix.transX)
        assertEquals(1f, matrix.skewY)
        assertEquals(0f, matrix.scaleY)
        assertEquals(0f, matrix.transY)
    }
}
