package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SurfaceTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test fun `Surface dimensions`() { val s = Surface(320, 240); assertEquals(320, s.width); assertEquals(240, s.height); assertEquals(PixelFormat.RGBA8, s.format) }
    @Test fun `Surface BGRA8`() { assertEquals(PixelFormat.BGRA8, Surface(100, 100, PixelFormat.BGRA8).format) }
    @Test fun `Surface canvas DSL`() { val s = Surface(320, 240); s.canvas { drawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED)) }; val r = s.render(); assertEquals(1, r.stats.opsDispatched) }
    @Test
    fun `readPixels copies correct region`() {
        val surface = Surface(100, 100)
        surface.canvas { drawColor(Color.RED) }
        val buffer = UByteArray(10 * 10 * 4)
        val ok = surface.readPixels(Rect.fromLTRB(0f, 0f, 10f, 10f), buffer)
        assertTrue(ok)
        // Verify first pixel is red (RGBA = 255,0,0,255)
        assertEquals(255.toByte(), buffer[0].toByte()) // R
        assertEquals(0.toByte(), buffer[1].toByte())   // G
        assertEquals(0.toByte(), buffer[2].toByte())   // B
        assertEquals(255.toByte(), buffer[3].toByte()) // A
    }
    @Test
    fun `Image decode detects PNG magic bytes`() {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val img = Image.decode(pngHeader)
        assertTrue(img.sourceId.contains("png"))
    }
    @Test
    fun `Image decode detects JPEG magic bytes`() {
        val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val img = Image.decode(jpegHeader)
        assertTrue(img.sourceId.contains("jpeg"))
    }
    @Test
    fun `Image decode detects WebP magic bytes with WEBP fourCC`() {
        val webpHeader = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        val img = Image.decode(webpHeader)
        assertTrue(img.sourceId.contains("webp"))
    }
    @Test
    fun `Image decode rejects RIFF without WEBP fourCC`() {
        val riffHeader = byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20) // AVI
        val img = Image.decode(riffHeader)
        assertTrue(img.sourceId.contains("unknown"), "RIFF without WEBP should not be detected as webp")
    }
    @Test
    fun `drawImage produces non-blank pixels`() {
        val pixels = ByteArray(10 * 10 * 4) { 255.toByte() }
        val img = Image.fromPixels(10, 10, pixels, ColorType.RGBA_8888, "test-white")
        val surface = Surface(100, 100)
        surface.canvas {
            drawImage(img, Rect.fromLTRB(0f, 0f, 10f, 10f))
        }
        val result = surface.render()
        val nonZero = (0 until result.pixels.size step 4).any { idx ->
            result.pixels[idx].toInt() and 0xFF > 0
        }
        assertTrue(nonZero, "drawImage should produce visible pixels")
    }
}
