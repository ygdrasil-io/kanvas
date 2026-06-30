package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ShaderTest {
    @Test
    fun `SolidColor shader`() {
        val s = Shader.SolidColor(Color.RED)
        assertEquals(Color.RED, s.color)
    }

    @Test
    fun `LinearGradient with stops`() {
        val s = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(100f, 0f),
            stops = listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
        )
        assertEquals(Point(0f, 0f), s.start)
        assertEquals(2, s.stops.size)
        assertEquals(TileMode.CLAMP, s.tileMode)
    }

    @Test
    fun `RadialGradient shader`() {
        val s = Shader.RadialGradient(
            center = Point(50f, 50f),
            radius = 80f,
            stops = listOf(GradientStop(0f, Color.GREEN), GradientStop(1f, Color.TRANSPARENT)),
        )
        assertTrue(s is Shader.RadialGradient)
    }
}
