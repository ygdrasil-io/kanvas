package org.graphiks.kanvas

import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.types.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class KanvasSmokeTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

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
            drawRect(RectF32.ofOriginSize(10f, 10f, 100f, 80f), Paint.fill(ColorARGB.Red))
        }
        val result = surface.render()
        assertTrue(result.stats.opsDispatched > 0)
    }

    @Test
    fun `canvas drawRRect records command`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawRRect(RRectF32.of(RectF32.ofOriginSize(10f, 10f, 100f, 80f), 10f), Paint.fill(ColorARGB.Green))
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
            }, Paint.fill(ColorARGB.Blue))
        }
        val result = surface.render()
        assertTrue(result.stats.opsDispatched > 0)
    }

    @Test
    fun `RectF32 fromXYWH creates correct rect`() {
        val rect = RectF32.ofOriginSize(5f, 10f, 100f, 200f)
        assertEquals(5f, rect.left)
        assertEquals(10f, rect.top)
        assertEquals(105f, rect.right)
        assertEquals(210f, rect.bottom)
    }

    @Test
    fun `RectF32 fromLTRB creates correct rect`() {
        val rect = RectF32.ofLTRB(1f, 2f, 3f, 4f)
        assertEquals(1f, rect.left)
        assertEquals(2f, rect.top)
        assertEquals(3f, rect.right)
        assertEquals(4f, rect.bottom)
    }

    @Test
    fun `canvas drawRect produces correct DisplayOp`() {
        val surface = Surface(width = 320, height = 240)
        surface.canvas {
            drawRect(RectF32.ofOriginSize(10f, 10f, 100f, 80f), Paint.fill(ColorARGB.Red))
        }
        val result = surface.render()
        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(0, result.diagnostics.fatalCount)
    }
}
