package org.graphiks.kanvas

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class KanvasSmokeTest {

    @Test
    fun `surface creates with default RGBA8 format`() {
        val surface = Surface(width = 320, height = 240)
        assertEquals(320, surface.width)
        assertEquals(240, surface.height)
        assertEquals(PixelFormat.RGBA8, surface.format)
    }

    @Test
    fun `surface creates with BGRA8 format`() {
        val surface = Surface(width = 100, height = 100, format = PixelFormat.BGRA8)
        assertEquals(PixelFormat.BGRA8, surface.format)
    }

    @Test
    fun `canvas drawRect records command`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawRect(Rect.fromXYWH(10f, 10f, 100f, 80f), Paint.fill(Color.RED))
        }
        val result = surface.render()
        assertTrue(result.stats.opsDispatched > 0)
    }

    @Test
    fun `canvas drawRRect records command`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawRRect(RRect(Rect.fromXYWH(10f, 10f, 100f, 80f), 10f), Paint.fill(Color.GREEN))
        }
        val result = surface.render()
        assertTrue(result.stats.opsDispatched > 0)
    }

    @Test
    fun `canvas drawPath records command`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawPath(org.graphiks.kanvas.geometry.Path {
                moveTo(10f, 10f)
                lineTo(100f, 10f)
                lineTo(55f, 80f)
                close()
            }, Paint.fill(Color.BLUE))
        }
        val result = surface.render()
        assertTrue(result.stats.opsDispatched > 0)
    }

    @Test
    fun `Rect fromXYWH creates correct rect`() {
        val rect = Rect.fromXYWH(5f, 10f, 100f, 200f)
        assertEquals(5f, rect.left)
        assertEquals(10f, rect.top)
        assertEquals(105f, rect.right)
        assertEquals(210f, rect.bottom)
    }

    @Test
    fun `Rect fromLTRB creates correct rect`() {
        val rect = Rect.fromLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, rect.left)
        assertEquals(2f, rect.top)
        assertEquals(3f, rect.right)
        assertEquals(4f, rect.bottom)
    }

    @Test
    fun `canvas drawRect produces correct DisplayOp`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawRect(Rect.fromXYWH(10f, 10f, 100f, 80f), Paint.fill(Color.RED))
        }
        val result = surface.render()
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(0, result.diagnostics.fatalCount)
    }
}
