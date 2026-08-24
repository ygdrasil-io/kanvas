package org.graphiks.kanvas.surface.gpu

import org.graphiks.math.color.ColorMatrixF32

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import kotlin.test.Test
import kotlin.test.assertEquals

class GPUGradientColorFilterMaterialTest {
    @Test
    fun `paint matrix color filter transforms linear gradient stops`() {
        val shader = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(10f, 0f),
            stops = listOf(
                GradientStop(0f, ColorARGB.Red),
                GradientStop(1f, ColorARGB.Blue),
            ),
        )
        val swapRedBlue = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))

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
            start = Point2F32(0f, 0f),
            startRadius = 0f,
            end = Point2F32(20f, 20f),
            endRadius = 20f,
            stops = listOf(
                GradientStop(0f, ColorARGB.Red),
                GradientStop(1f, ColorARGB.Green),
            ),
        )
        val filtered = Shader.WithColorFilter(
            shader = shader,
            filter = ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC),
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
            start = Point2F32(0f, 0f),
            end = Point2F32(10f, 0f),
            stops = listOf(
                GradientStop(0f, ColorARGB.Black),
                GradientStop(1f, ColorARGB.White),
            ),
        )
        val clampingMatrix = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            2f, 0f, 0f, 0f, -0.5f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))

        val material = Paint(shader = shader, colorFilter = clampingMatrix).toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(0f, material.startR)
        assertEquals(1f, material.endR)
        assertEquals(listOf(0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f), material.allStopColors!!.toList())
    }

    @Test
    fun `source blend color filter is not folded into decal gradients`() {
        val shader = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(10f, 0f),
            stops = listOf(
                GradientStop(0f, ColorARGB.Red),
                GradientStop(1f, ColorARGB.Green),
            ),
            tileMode = TileMode.DECAL,
        )

        val material = Paint(
            shader = shader,
            colorFilter = ColorFilter.Blend(ColorARGB.Blue, BlendMode.SRC),
        ).toMaterial() as GPUMaterialDescriptor.LinearGradient

        assertEquals(1f, material.startR)
        assertEquals(0f, material.startB)
        assertEquals(0f, material.endR)
        assertEquals(0f, material.endB)
        assertEquals("decal", material.tileMode)
    }
}
