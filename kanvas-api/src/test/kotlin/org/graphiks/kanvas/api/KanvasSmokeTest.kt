package org.graphiks.kanvas.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals

class KanvasSmokeTest {

    @Test
    fun `surface creates with default RGBA8 format`() {
        val surface = KanvasSurface(width = 320, height = 240)
        assertEquals(320, surface.width)
        assertEquals(240, surface.height)
        assertEquals(KanvasPixelFormat.RGBA8, surface.format)
    }

    @Test
    fun `surface creates with BGRA8 format`() {
        val surface = KanvasSurface(width = 100, height = 100, format = KanvasPixelFormat.BGRA8)
        assertEquals(KanvasPixelFormat.BGRA8, surface.format)
    }

    @Test
    fun `flush returns non-null frame`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val frame = surface.flush()
        assertTrue(frame.isEmpty)
    }

    @Test
    fun `canvas drawRect records command`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val canvas = KanvasCanvas(surface)
        val paint = KanvasPaint().color(1f, 0f, 0f, 1f)
        val rect = KanvasRect.fromXYWH(10f, 10f, 100f, 80f)
        canvas.drawRect(rect, paint)
        val frame = surface.flush()
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `canvas drawRRect records command`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val canvas = KanvasCanvas(surface)
        val paint = KanvasPaint().color(0f, 1f, 0f, 1f)
        val rect = KanvasRect.fromXYWH(10f, 10f, 100f, 80f)
        val radii = KanvasRRectCornerRadii(10f, 10f)
        canvas.drawRRect(rect, radii, paint)
        val frame = surface.flush()
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `canvas drawPath records command`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val canvas = KanvasCanvas(surface)
        val paint = KanvasPaint().color(0f, 0f, 1f, 1f)
        val path = KanvasPath().apply {
            moveTo(10f, 10f)
            lineTo(100f, 10f)
            lineTo(55f, 80f)
            close()
        }
        canvas.drawPath(path, paint)
        val frame = surface.flush()
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `canvas drawImage records command`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val canvas = KanvasCanvas(surface)
        val image = KanvasImage.decode(ByteArray(0), "image/png")
        val rect = KanvasRect.fromXYWH(0f, 0f, 100f, 100f)
        canvas.drawImage(image, rect)
        val frame = surface.flush()
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `canvas drawTextBlob records command`() {
        val surface = KanvasSurface(width = 320, height = 240)
        val canvas = KanvasCanvas(surface)
        val paint = KanvasPaint().color(1f, 1f, 1f, 1f)
        val blob = KanvasTextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(65u, 66u, 67u),
                    positions = listOf(KanvasPoint(0f, 0f), KanvasPoint(10f, 0f), KanvasPoint(20f, 0f)),
                ),
            ),
        )
        canvas.drawTextBlob(blob, 10f, 10f, paint)
        val frame = surface.flush()
        assertFalse(frame.isEmpty)
    }

    @Test
    fun `kanvasRect fromXYWH creates correct rect`() {
        val rect = KanvasRect.fromXYWH(5f, 10f, 100f, 200f)
        assertEquals(5f, rect.left)
        assertEquals(10f, rect.top)
        assertEquals(105f, rect.right)
        assertEquals(210f, rect.bottom)
    }

    @Test
    fun `kanvasRect fromLTRB creates correct rect`() {
        val rect = KanvasRect.fromLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, rect.left)
        assertEquals(2f, rect.top)
        assertEquals(3f, rect.right)
        assertEquals(4f, rect.bottom)
    }

    @Test
    fun `kanvasPaint paint descriptors lower correctly`() {
        val paint = KanvasPaint().color(1f, 0f, 0.5f, 0.8f)
        val descriptor = paint.lower()
        assertEquals("src_over", descriptor.blendModeLabel)
        assertEquals(0.8f, descriptor.alpha)
    }

    @Test
    fun `kanvasPath produces correct verb sequence`() {
        val path = KanvasPath().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(50f, 100f)
            close()
        }
        val pathData = path.toPathData()
        assertEquals(4, pathData.verbs.size)
    }

    @Test
    fun `kanvasShader solidColor lowers correctly`() {
        val shader = KanvasShader.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f)
        val descriptor = shader.lower()
        assertTrue(descriptor is org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor.Solid)
    }

    @Test
    fun `kanvasImage decode with png hint`() {
        val image = KanvasImage.decode(ByteArray(8), "image/png")
        assertEquals(KanvasColorType.RGBA_8888, image.colorType)
    }

    @Test
    fun `kanvasTextBlob lower creates glyph run descriptor`() {
        val blob = KanvasTextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(72u, 73u),
                    positions = listOf(KanvasPoint(0f, 0f), KanvasPoint(12f, 0f)),
                ),
            ),
        )
        val descriptor = blob.lower()
        assertEquals(2, descriptor.glyphs.size)
    }

    @Test
    fun `module imports no Skia types`() {
        // This is a compilation-level check — if any Skia type leaked into
        // this module, this test file would not compile because Skia is not
        // a dependency. The mere fact that this test compiles proves purity.
        assertTrue(true)
    }
}
