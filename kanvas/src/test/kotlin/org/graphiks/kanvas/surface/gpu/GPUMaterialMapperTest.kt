package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.ShaderModule
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.pipeline.UniformLayout
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.b
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.r
import org.junit.jupiter.api.Test

class GPUMaterialMapperTest {
    @Test
    fun `legacy unsupported blend falls back to source while prepared mapping preserves blend`() {
        val dst = Shader.ConicalGradient(
            start = Point(0f, 0f),
            startRadius = 0f,
            end = Point(10f, 10f),
            endRadius = 10f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
        )
        val src = Shader.SolidColor(Color.fromRGBA(0.25f, 0.5f, 0.75f, 1f))
        val paint = Paint(
            shader = Shader.Blend(
                mode = BlendMode.SRC_OVER,
                dst = dst,
                src = src,
            ),
        )

        val legacy = assertIs<GPUMaterialDescriptor.SolidColor>(paint.toMaterial())
        val prepared = assertIs<GPUMaterialDescriptor.BlendShader>(
            paint.toPreparedMaterialMapping().descriptor,
        )

        assertEquals(src.color.r, legacy.r)
        assertEquals(src.color.g, legacy.g)
        assertEquals(src.color.b, legacy.b)
        assertIs<GPUMaterialDescriptor.ConicalGradient>(prepared.dst)
        assertIs<GPUMaterialDescriptor.SolidColor>(prepared.src)
        assertEquals("", prepared.wgslCombined)
        assertEquals(0, prepared.uniformBytes.size)
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

    @Test
    fun `prepared runtime mapping snapshots exact uniform payload and child facts`() {
        val matrixValues = FloatArray(16) { index -> index.toFloat() }
        val childMap = linkedMapOf<String, Shader>(
            "input" to Shader.SolidColor(Color.RED),
        )
        val shader = Shader.RuntimeEffect(
            effect = RuntimeEffect(
                id = "runtime.simple_rt",
                module = ShaderModule.fromSource("registered-only"),
                uniformLayout = UniformLayout(emptyList()),
                children = emptyList(),
            ),
            uniforms = UniformBlock {
                float4("gColor", 0.25f, 0.5f, 0.75f, 1f)
                mat4x4("transform", matrixValues)
            },
            children = childMap,
        )

        val mapping = Paint(shader = shader).toPreparedMaterialMapping()
        matrixValues.fill(99f)
        childMap.clear()

        val descriptor = assertIs<GPUMaterialDescriptor.RuntimeEffect>(mapping.descriptor)
        assertEquals(
            GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 1f),
            descriptor.uniforms.getValue("gColor"),
        )
        val matrix = assertIs<GPURuntimeEffectUniformValue.Matrix4x4>(
            descriptor.uniforms.getValue("transform"),
        )
        assertEquals((0 until 16).map(Int::toFloat), matrix.values)
        assertIs<GPUMaterialDescriptor.SolidColor>(descriptor.children.getValue("input"))
    }

    @Test
    fun `prepared mapping preserves legacy supported color filter behavior`() {
        val paint = Paint(
            color = Color.fromRGBA(0.2f, 0.4f, 0.6f, 0.8f),
            colorFilter = ColorFilter.Matrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 0.5f, 0f,
                ),
            ),
        )

        assertEquals(paint.toMaterial(), paint.toPreparedMaterialMapping().descriptor)
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
