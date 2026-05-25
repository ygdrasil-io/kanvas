package org.skia.core

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkIRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo

class SkSurfaceAsyncReadTest {

    @Test
    fun `asyncRescaleAndReadPixels returns single RGBA plane synchronously`() {
        val surface = coloredSurface2x2()
        var result: SkSurface.AsyncReadResult? = null

        surface.asyncRescaleAndReadPixels(
            info = SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul),
            srcRect = SkIRect.MakeLTRB(0, 0, 2, 2),
        ) { result = it }

        assertNotNull(result)
        val actual = result!!
        assertEquals(1, actual.count())
        assertEquals(8, actual.rowBytes(0))
        val bytes = actual.data(0)
        assertEquals(16, bytes.size)
        assertRgba(bytes, 0, SK_ColorRED)
        assertRgba(bytes, 4, SK_ColorGREEN)
        assertRgba(bytes, 8, SK_ColorBLUE)
        assertRgba(bytes, 12, SK_ColorWHITE)
    }

    @Test
    fun `asyncRescaleAndReadPixels uses SkPixmap scaling for RGBA output`() {
        val surface = coloredSurface2x2()
        var result: SkSurface.AsyncReadResult? = null

        surface.asyncRescaleAndReadPixels(
            info = SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul),
            srcRect = SkIRect.MakeLTRB(0, 0, 2, 2),
            rescaleMode = SkSurface.RescaleMode.kNearest,
        ) { result = it }

        assertNotNull(result)
        val bytes = result!!.data(0)
        assertEquals(4, bytes.size)
        assertRgba(bytes, 0, SK_ColorRED)
    }

    @Test
    fun `asyncRescaleAndReadPixels reports null for non RGBA fallback types`() {
        var result: SkSurface.AsyncReadResult? = null

        coloredSurface2x2().asyncRescaleAndReadPixels(
            info = SkImageInfo.Make(1, 1, SkColorType.kRGBA_F16Norm, SkAlphaType.kPremul),
            srcRect = SkIRect.MakeLTRB(0, 0, 2, 2),
        ) { result = it }

        assertNull(result)
    }

    @Test
    fun `asyncRescaleAndReadPixels reports null when source rect escapes surface bounds`() {
        var result: SkSurface.AsyncReadResult? = null

        coloredSurface2x2().asyncRescaleAndReadPixels(
            info = SkImageInfo.Make(1, 1, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul),
            srcRect = SkIRect.MakeLTRB(1, 1, 3, 3),
        ) { result = it }

        assertNull(result)
    }

    private fun coloredSurface2x2(): SkSurface {
        val bitmap = SkBitmap(2, 2)
        bitmap.setPixel(0, 0, SK_ColorRED)
        bitmap.setPixel(1, 0, SK_ColorGREEN)
        bitmap.setPixel(0, 1, SK_ColorBLUE)
        bitmap.setPixel(1, 1, SK_ColorWHITE)
        return SkSurface.MakeRasterDirect(bitmap)
    }

    private fun assertRgba(bytes: ByteArray, offset: Int, color: Int) {
        assertEquals((color ushr 16) and 0xFF, bytes[offset].toInt() and 0xFF, "R@$offset")
        assertEquals((color ushr 8) and 0xFF, bytes[offset + 1].toInt() and 0xFF, "G@$offset")
        assertEquals(color and 0xFF, bytes[offset + 2].toInt() and 0xFF, "B@$offset")
        assertEquals((color ushr 24) and 0xFF, bytes[offset + 3].toInt() and 0xFF, "A@$offset")
    }
}
