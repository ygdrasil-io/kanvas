package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class GPUGradientColorFilterMaterialTest {
    @Test
    fun `paint matrix color filter transforms linear gradient stops`() {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(10f, 0f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.BLUE),
            ),
        )
        val swapRedBlue = ColorFilter.Matrix(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))

        val material = Paint(shader = shader, colorFilter = swapRedBlue).toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(0f, material.startR)
        assertEquals(1f, material.startB)
        assertEquals(1f, material.endR)
        assertEquals(0f, material.endB)
        assertEquals(listOf(0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f), material.allStopColors!!.toList())
    }

    @Test
    fun `shader color filter applies source blend to conical gradient`() {
        val shader = Shader.ConicalGradient(
            start = Point(0f, 0f),
            startRadius = 0f,
            end = Point(20f, 20f),
            endRadius = 20f,
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.GREEN),
            ),
        )
        val filtered = Shader.WithColorFilter(
            shader = shader,
            filter = ColorFilter.Blend(Color.BLUE, BlendMode.SRC),
        )

        val material = filtered.toMaterial() as GPUMaterialDescriptor.ConicalGradient

        assertEquals(0f, material.startR)
        assertEquals(1f, material.startB)
        assertEquals(0f, material.endR)
        assertEquals(1f, material.endB)
        assertEquals(listOf(0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f), material.allStopColors!!.toList())
    }
}
