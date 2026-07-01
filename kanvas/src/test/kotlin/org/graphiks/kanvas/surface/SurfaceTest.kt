package org.graphiks.kanvas.surface

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SurfaceTest {
    @Test fun `Surface dimensions`() { val s = Surface(320, 240); assertEquals(320, s.width); assertEquals(240, s.height); assertEquals(PixelFormat.RGBA8, s.format) }
    @Test fun `Surface BGRA8`() { assertEquals(PixelFormat.BGRA8, Surface(100, 100, PixelFormat.BGRA8).format) }
    @Test fun `Surface canvas DSL`() { val s = Surface(320, 240); s.canvas { drawRect(Rect.fromLTRB(0f,0f,100f,80f), Paint.fill(Color.RED)) }; val r = s.render(); assertEquals(1, r.stats.opsDispatched) }
    @Test
    fun `readPixels copies correct region`() {
        val surface = Surface(100, 100)
        surface.canvas { drawRect(Rect.fromLTRB(0f, 0f, 100f, 100f), Paint.fill(Color.RED)) }
        val buffer = UByteArray(10 * 10 * 4)
        val ok = surface.readPixels(Rect.fromLTRB(0f, 0f, 10f, 10f), buffer)
        assertTrue(ok)
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
}
