package org.graphiks.kanvas.surface

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ImageEncoderTest {
    @Test fun `find unknown format`() { assertNull(ImageEncoderRegistry.find("unknown")) }

    @Test fun `toPng throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toPng(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:png")) }
    }

    @Test fun `toJpeg throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toJpeg(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:jpeg")) }
    }

    @Test fun `toWebP throws without encoder`() {
        val r = RenderResult(UByteArray(16){128u}, 2, 2, PixelFormat.RGBA8, org.graphiks.kanvas.types.ColorSpace.SRGB, Diagnostics(), RenderStats(0,0,0,0,0f))
        try { r.toWebP(); fail() } catch (e: Exception) { assertTrue(e.message!!.contains("codec:webp")) }
    }
}
