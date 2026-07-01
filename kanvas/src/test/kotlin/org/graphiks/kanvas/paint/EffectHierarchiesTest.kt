package org.graphiks.kanvas.paint

import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class EffectHierarchiesTest {
    @Test
    fun `ColorFilter Matrix`() {
        val f = ColorFilter.Matrix(FloatArray(20) { it.toFloat() })
        assertEquals(20, f.values.size)
    }

    @Test
    fun `ColorFilter Blend`() {
        val f = ColorFilter.Blend(Color.RED, BlendMode.MULTIPLY)
        assertEquals(Color.RED, f.color)
        assertEquals(BlendMode.MULTIPLY, f.mode)
    }

    @Test
    fun `ColorFilter Compose`() {
        val inner = ColorFilter.Blend(Color.BLUE, BlendMode.SRC_OVER)
        val outer = ColorFilter.Matrix(FloatArray(20) { 1f })
        val f = ColorFilter.Compose(outer, inner)
        assertEquals(inner, f.inner)
    }

    @Test
    fun `ColorFilter Table`() {
        val table = UByteArray(256) { it.toUByte() }
        val f = ColorFilter.Table(table)
        assertEquals(256, f.table.size)
    }

    @Test
    fun `ColorFilter Lighting`() {
        val f = ColorFilter.Lighting(Color(0xFF808080u), Color(0xFF404040u))
        assertEquals(Color(0xFF808080u), f.mul)
    }

    @Test
    fun `ColorFilter SRGBToLinear and LinearToSRGB are singletons`() {
        assertEquals(ColorFilter.SRGBToLinear, ColorFilter.SRGBToLinear)
        assertEquals(ColorFilter.LinearToSRGB, ColorFilter.LinearToSRGB)
    }

    @Test
    fun `MaskFilter Blur`() {
        val f = MaskFilter.Blur(BlurStyle.NORMAL, 4f)
        assertEquals(BlurStyle.NORMAL, f.style)
        assertEquals(4f, f.sigma)
    }

    @Test
    fun `PathEffect Dash`() {
        val f = PathEffect.Dash(floatArrayOf(10f, 5f), 2f)
        assertEquals(10f, f.intervals[0])
        assertEquals(5f, f.intervals[1])
        assertEquals(2f, f.phase)
    }

    @Test
    fun `PathEffect Corner`() {
        val f = PathEffect.Corner(5f)
        assertEquals(5f, f.radius)
    }

    @Test
    fun `PathEffect Discrete`() {
        val f = PathEffect.Discrete(10f, 2f)
        assertEquals(10f, f.segmentLength)
        assertEquals(2f, f.deviation)
    }

    @Test
    fun `Blender Mode`() {
        val b = Blender.Mode(BlendMode.MULTIPLY)
        assertEquals(BlendMode.MULTIPLY, b.mode)
    }

    @Test
    fun `Blender Arithmetic`() {
        val b = Blender.Arithmetic(0.5f, 0.25f, 0.25f, 0f)
        assertEquals(0.5f, b.k1)
    }
}
