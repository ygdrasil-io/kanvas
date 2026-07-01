package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class PaintTest {
    @Test
    fun `Paint fill factory`() {
        val p = Paint.fill(Color.RED)
        assertEquals(Color.RED, p.color)
        assertEquals(PaintStyle.FILL, p.style)
    }

    @Test
    fun `Paint stroke factory`() {
        val p = Paint.stroke(Color.BLUE, 3f)
        assertEquals(Color.BLUE, p.color)
        assertEquals(PaintStyle.STROKE, p.style)
        assertEquals(3f, p.strokeWidth)
    }

    @Test
    fun `Paint defaults`() {
        val p = Paint()
        assertEquals(Color.BLACK, p.color)
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
        val p = Paint.fill(Color.RED).copy(
            shader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(100f, 0f),
                stops = listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
            ),
            blendMode = BlendMode.MULTIPLY,
        )
        assertTrue(p.shader is Shader.LinearGradient)
        assertEquals(BlendMode.MULTIPLY, p.blendMode)
    }

    @Test
    fun `Paint copy preserves unset fields`() {
        val p1 = Paint.fill(Color.GREEN)
        val p2 = p1.copy(blendMode = BlendMode.SCREEN)
        assertEquals(Color.GREEN, p2.color)
        assertEquals(PaintStyle.FILL, p2.style)
        assertEquals(BlendMode.SCREEN, p2.blendMode)
    }
}
