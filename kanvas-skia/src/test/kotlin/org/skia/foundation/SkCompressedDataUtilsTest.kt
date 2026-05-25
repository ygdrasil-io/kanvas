package org.skia.foundation

import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkISize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SkCompressedDataUtilsTest {

    @Test
    fun `SkCompressedDataSize computes BC1 base level`() {
        val size = SkCompressedDataUtils.SkCompressedDataSize(
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkISize.Make(16, 8),
            mipMapOffsetsAndSizes = null,
            mipMapped = false,
        )
        assertEquals(64L, size)
    }

    @Test
    fun `SkCompressedDataSize computes BC1 mip chain`() {
        val size = SkCompressedDataUtils.SkCompressedDataSize(
            SkTextureCompressionType.kBC1_RGB8_UNORM,
            SkISize.Make(13, 61),
            mipMapOffsetsAndSizes = null,
            mipMapped = true,
        )
        assertEquals(704L, size)
    }

    @Test
    fun `RasterFromCompressedTextureData decodes BC1 RGB and RGBA semantics`() {
        val block = byteArrayOf(
            0x00, 0x00, // color0 = black
            0xFF.toByte(), 0xFF.toByte(), // color1 = white (c0 <= c1 => transparent mode)
            0xE4.toByte(), 0xE4.toByte(), 0xE4.toByte(), 0xE4.toByte(), // indices 0,1,2,3 pattern
        )
        val rgb = SkImages.RasterFromCompressedTextureData(
            block,
            4,
            4,
            SkTextureCompressionType.kBC1_RGB8_UNORM,
        )
        val rgba = SkImages.RasterFromCompressedTextureData(
            block,
            4,
            4,
            SkTextureCompressionType.kBC1_RGBA8_UNORM,
        )
        assertNotNull(rgb)
        assertNotNull(rgba)

        // Pixel with index 3: opaque black in BC1-RGB, transparent in BC1-RGBA.
        val rgbIdx3 = rgb!!.peekPixel(3, 0)
        val rgbaIdx3 = rgba!!.peekPixel(3, 0)
        assertEquals(255, SkColorGetA(rgbIdx3))
        assertEquals(0, SkColorGetR(rgbIdx3))
        assertEquals(0, SkColorGetA(rgbaIdx3))
    }
}
