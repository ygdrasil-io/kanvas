package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.junit.jupiter.api.Test

class GPUMaterialMapperTest {
    @Test
    fun `unsupported blend shader mapping preserves the blend descriptor`() {
        val dst = imageShader("dst", byteArrayOf(1, 2, 3, 4))
        val src = imageShader("src", byteArrayOf(5, 6, 7, 8))
        val paint = Paint(
            shader = Shader.Blend(
                mode = BlendMode.SRC_OVER,
                dst = dst,
                src = src,
            ),
        )

        val mapped = assertIs<GPUMaterialDescriptor.BlendShader>(paint.toMaterial())

        assertIs<GPUMaterialDescriptor.ImageDraw>(mapped.dst)
        assertIs<GPUMaterialDescriptor.ImageDraw>(mapped.src)
        assertEquals("", mapped.wgslCombined)
        assertEquals(0, mapped.uniformBytes.size)
    }

    @Test
    fun `prepared solid mapping represents paint color alpha exactly once`() {
        val mapping = Paint(
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.5f),
        ).toPreparedMaterialMapping()
        val solid = assertIs<GPUMaterialDescriptor.SolidColor>(mapping.descriptor)

        assertEquals(0.5f, solid.a, 0.002f)
        assertEquals(1f, mapping.paintAlpha)
    }

    @Test
    fun `prepared gradient mapping retains source alpha and separates caller modulation`() {
        val gradient = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(10f, 0f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 0.25f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 0.75f)),
            ),
        )
        val mapping = Paint(
            color = Color.fromRGBA(1f, 1f, 1f, 0.5f),
            shader = gradient,
        ).toPreparedMaterialMapping()
        val descriptor = assertIs<GPUMaterialDescriptor.LinearGradient>(mapping.descriptor)

        assertEquals(0.25f, descriptor.startA, 0.002f)
        assertEquals(0.75f, descriptor.endA, 0.002f)
        assertEquals(0.5f, mapping.paintAlpha, 0.002f)
    }

    @Test
    fun `prepared alpha image keeps tint RGB and moves caller alpha to paint modulation`() {
        val mapping = Paint(
            color = Color.fromRGBA(0.25f, 0.5f, 0.75f, 0.5f),
            shader = imageShader("mask", byteArrayOf(0x80.toByte()), ColorType.ALPHA_8),
        ).toPreparedMaterialMapping()
        val descriptor = assertIs<GPUMaterialDescriptor.ImageDraw>(mapping.descriptor)

        assertEquals(true, descriptor.alphaOnly)
        assertEquals(0.25f, descriptor.tintR, 0.002f)
        assertEquals(0.5f, descriptor.tintG, 0.002f)
        assertEquals(0.75f, descriptor.tintB, 0.002f)
        assertEquals(1f, descriptor.tintA)
        assertEquals(0.5f, mapping.paintAlpha, 0.002f)
    }

    private fun imageShader(
        sourceId: String,
        pixels: ByteArray,
        colorType: ColorType = ColorType.RGBA_8888,
    ): Shader.Image =
        Shader.Image(
            Image.fromPixels(
                width = 1,
                height = 1,
                pixels = pixels,
                colorType = colorType,
                sourceId = sourceId,
            ),
        )
}
