package org.graphiks.kanvas.paint

import org.graphiks.math.geometry.Point2F32

import org.graphiks.kanvas.types.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ShaderTest {
    @Test
    fun `SolidColor shader`() {
        val s = Shader.SolidColor(ColorARGB.Red)
        assertEquals(ColorARGB.Red, s.color)
    }

    @Test
    fun `LinearGradient with stops`() {
        val s = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(100f, 0f),
            stops = listOf(GradientStop(0f, ColorARGB.White), GradientStop(1f, ColorARGB.Black)),
        )
        assertEquals(Point2F32(0f, 0f), s.start)
        assertEquals(2, s.stops.size)
        assertEquals(TileMode.CLAMP, s.tileMode)
    }

    @Test
    fun `RadialGradient shader`() {
        val s = Shader.RadialGradient(
            center = Point2F32(50f, 50f),
            radius = 80f,
            stops = listOf(GradientStop(0f, ColorARGB.Green), GradientStop(1f, ColorARGB.Transparent)),
        )
        assertTrue(s is Shader.RadialGradient)
    }
}
