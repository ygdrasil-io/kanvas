package org.graphiks.kanvas.paint

import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class PaintTest {
    @Test
    fun `Paint fill factory`() {
        val p = Paint.fill(ColorARGB.Red)
        assertEquals(ColorARGB.Red, p.color)
        assertEquals(PaintStyle.FILL, p.style)
    }

    @Test
    fun `Paint stroke factory`() {
        val p = Paint.stroke(ColorARGB.Blue, 3f)
        assertEquals(ColorARGB.Blue, p.color)
        assertEquals(PaintStyle.STROKE, p.style)
        assertEquals(3f, p.strokeWidth)
    }

    @Test
    fun `Paint defaults`() {
        val p = Paint()
        assertEquals(ColorARGB.Black, p.color)
        assertEquals(BlendMode.SRC_OVER, p.blendMode)
        assertEquals(PaintStyle.FILL, p.style)
        assertTrue(p.antiAlias)
        assertNull(p.shader)
        assertNull(p.maskFilter)
        assertNull(p.pathEffect)
        assertNull(p.imageFilter)
        assertNull(p.blender)
    }

    @Test
    fun `Paint copy with shader`() {
        val p = Paint.fill(ColorARGB.Red).copy(
            shader = Shader.LinearGradient(
                start = Point2F32(0f, 0f),
                end = Point2F32(100f, 0f),
                stops = listOf(GradientStop(0f, ColorARGB.White), GradientStop(1f, ColorARGB.Black)),
            ),
            blendMode = BlendMode.MULTIPLY,
        )
        assertTrue(p.shader is Shader.LinearGradient)
        assertEquals(BlendMode.MULTIPLY, p.blendMode)
    }

    @Test
    fun `Paint copy preserves unset fields`() {
        val p1 = Paint.fill(ColorARGB.Green)
        val p2 = p1.copy(blendMode = BlendMode.SCREEN)
        assertEquals(ColorARGB.Green, p2.color)
        assertEquals(PaintStyle.FILL, p2.style)
        assertEquals(BlendMode.SCREEN, p2.blendMode)
    }
}
