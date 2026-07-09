package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GPUSolidColorFilterMaterialTest {
    @Test
    fun `matrix color filter is folded into solid material`() {
        val matrix = floatArrayOf(
            0f, 1f, 0f, 0f, 0.1f,
            1f, 0f, 0f, 0f, 0.0f,
            0f, 0f, 1f, 0f, 0.2f,
            0f, 0f, 0f, 1f, 0.0f,
        )

        val material = Paint(
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.5f),
            colorFilter = ColorFilter.Matrix(matrix),
        ).toMaterial() as GPUMaterialDescriptor.SolidColor

        assertEquals(0.5f, material.r, 0.01f)
        assertEquals(0.2f, material.g, 0.01f)
        assertEquals(0.8f, material.b, 0.01f)
        assertEquals(0.5f, material.a, 0.01f)
    }

    @Test
    fun `compose applies inner color filter before outer color filter`() {
        val invertTable = UByteArray(256) { (255 - it).toUByte() }
        val alphaHalf = ColorFilter.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 0.5f, 0f,
        ))

        val material = Paint(
            color = Color.fromRGBA(0.25f, 0.5f, 0.75f, 0.8f),
            colorFilter = ColorFilter.Compose(alphaHalf, ColorFilter.Table(invertTable)),
        ).toMaterial() as GPUMaterialDescriptor.SolidColor

        assertEquals(0.75f, material.r, 0.01f)
        assertEquals(0.5f, material.g, 0.01f)
        assertEquals(0.25f, material.b, 0.01f)
        assertEquals(0.1f, material.a, 0.02f)
    }

    @Test
    fun `blend color filter supports porter duff modes used by modecolorfilters`() {
        val srcIn = Paint(
            color = Color.fromRGBA(0.8f, 0.2f, 0.4f, 0.5f),
            colorFilter = ColorFilter.Blend(Color.fromRGBA(0.1f, 0.6f, 0.3f, 0.75f), BlendMode.SRC_IN),
        ).toMaterial() as GPUMaterialDescriptor.SolidColor

        assertEquals(0.1f, srcIn.r, 0.01f)
        assertEquals(0.6f, srcIn.g, 0.01f)
        assertEquals(0.3f, srcIn.b, 0.01f)
        assertEquals(0.375f, srcIn.a, 0.01f)

        val modulate = Paint(
            color = Color.fromRGBA(0.8f, 0.2f, 0.4f, 0.5f),
            colorFilter = ColorFilter.Blend(Color.fromRGBA(0.5f, 0.5f, 1f, 1f), BlendMode.MODULATE),
        ).toMaterial() as GPUMaterialDescriptor.SolidColor

        assertEquals(0.4f, modulate.r, 0.01f)
        assertEquals(0.1f, modulate.g, 0.01f)
        assertEquals(0.4f, modulate.b, 0.01f)
        assertEquals(0.5f, modulate.a, 0.01f)
    }

    @Test
    fun `hsla matrix color filter is not folded as rgba matrix`() {
        val hueShiftAsRgbaMatrix = floatArrayOf(
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        val material = Paint(
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.8f),
            colorFilter = ColorFilter.HSLAMatrix(hueShiftAsRgbaMatrix),
        ).toMaterial() as GPUMaterialDescriptor.SolidColor

        assertEquals(0.2f, material.r, 0.01f)
        assertEquals(0.4f, material.g, 0.01f)
        assertEquals(0.6f, material.b, 0.01f)
        assertEquals(0.8f, material.a, 0.01f)
    }
}
