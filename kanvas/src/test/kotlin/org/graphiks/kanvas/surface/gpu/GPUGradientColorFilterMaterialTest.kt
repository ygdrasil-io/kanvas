package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
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

    @Test
    fun `matrix color filter is not folded into gradient stops when clamping would be required`() {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(10f, 0f),
            stops = listOf(
                GradientStop(0f, Color.BLACK),
                GradientStop(1f, Color.WHITE),
            ),
        )
        val clampingMatrix = ColorFilter.Matrix(floatArrayOf(
            2f, 0f, 0f, 0f, -0.5f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))

        val material = Paint(shader = shader, colorFilter = clampingMatrix).toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(0f, material.startR)
        assertEquals(1f, material.endR)
        assertEquals(listOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f), material.allStopColors!!.toList())
    }

    @Test
    fun `source blend color filter is not folded into decal gradients`() {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(10f, 0f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.GREEN),
            ),
            tileMode = TileMode.DECAL,
        )

        val material = Paint(
            shader = shader,
            colorFilter = ColorFilter.Blend(Color.BLUE, BlendMode.SRC),
        ).toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(1f, material.startR)
        assertEquals(0f, material.startB)
        assertEquals(0f, material.endR)
        assertEquals(0f, material.endB)
        assertEquals("decal", material.tileMode)
    }
}
