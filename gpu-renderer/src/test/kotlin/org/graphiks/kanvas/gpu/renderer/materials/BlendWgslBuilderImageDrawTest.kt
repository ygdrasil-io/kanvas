package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import kotlin.test.Test
import kotlin.test.assertTrue

class BlendWgslBuilderImageDrawTest {

    @Test
    fun `buildWgsl with ImageDraw src includes texture sampler bindings`() {
        val dst = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f)
        val px = byteArrayOf(0xFF.toByte(), 0.toByte(), 0.toByte(), 0xFF.toByte())
        val src = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "test",
            imageWidth = 4,
            imageHeight = 4,
            rgbaPixels = px,
        )
        val wgsl = BlendWgslBuilder.buildWgsl(dst, src, "src_over")

        assertTrue(wgsl.contains("var<uniform> blend:"), "should have uniform block")
        assertTrue(wgsl.contains("blend_image_sampler"), "should have sampler binding")
        assertTrue(wgsl.contains("blend_image_texture"), "should have texture binding")
        assertTrue(wgsl.contains("textureSample(blend_image_texture"), "should call textureSample")
        assertTrue(wgsl.contains("src_uv"), "should have UV coords for src")
        assertTrue(wgsl.contains("src_sampled"), "should have sampled var for src")
        assertTrue(wgsl.contains("src_result"), "should have src_result")
    }

    @Test
    fun `buildWgsl with ImageDraw dst includes texture sampler bindings`() {
        val dst = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "test",
            imageWidth = 4,
            imageHeight = 4,
        )
        val src = GPUMaterialDescriptor.SolidColor(r = 0f, g = 1f, b = 0f, a = 1f)
        val wgsl = BlendWgslBuilder.buildWgsl(dst, src, "src_over")

        assertTrue(wgsl.contains("blend_image_sampler"), "should have sampler binding")
        assertTrue(wgsl.contains("blend_image_texture"), "should have texture binding")
        assertTrue(wgsl.contains("dst_uv"), "should have UV coords for dst")
        assertTrue(wgsl.contains("dst_sampled"), "should have sampled var for dst")
        assertTrue(wgsl.contains("dst_result"), "should have dst_result")
    }

    @Test
    fun `buildWgsl without ImageDraw does not include texture bindings`() {
        val dst = GPUMaterialDescriptor.SolidColor(r = 1f, g = 0f, b = 0f, a = 1f)
        val src = GPUMaterialDescriptor.SolidColor(r = 0f, g = 1f, b = 0f, a = 1f)
        val wgsl = BlendWgslBuilder.buildWgsl(dst, src, "src_over")

        assertTrue(wgsl.contains("var<uniform> blend:"), "should have uniform block")
        assertTrue(!wgsl.contains("texture_2d<f32>"), "should not have texture bindings")
        assertTrue(!wgsl.contains("textureSample"), "should not have texture sampling")
    }

    @Test
    fun `packUniforms with ImageDraw child packs uniform bytes`() {
        val dst = GPUMaterialDescriptor.ImageDraw(
            imageSourceId = "test",
            imageWidth = 4,
            imageHeight = 4,
            rgbaPixels = ByteArray(4 * 4 * 4) { 0xFF.toByte() },
        )
        val src = GPUMaterialDescriptor.SolidColor(r = 0f, g = 1f, b = 0f, a = 1f)
        val bytes = BlendWgslBuilder.packUniforms(dst, src, "src_over")

        assertTrue(bytes.isNotEmpty(), "uniform bytes must not be empty")
        assertTrue(bytes.size >= 64 + 32, "should have space for two child blocks + 12 bytes padding")
    }

    @Test
    fun `GPUBlendShaderLowering accepts ImageDraw child`() {
        val desc = GPUMaterialDescriptor.BlendShader(
            mode = "src_over",
            dst = GPUMaterialDescriptor.SolidColor(r = 1f, g = 1f, b = 1f, a = 1f),
            src = GPUMaterialDescriptor.ImageDraw(
                imageSourceId = "test",
                imageWidth = 4,
                imageHeight = 4,
                rgbaPixels = ByteArray(4 * 4 * 4) { 0xFF.toByte() },
            ),
        )
        assertTrue(GPUBlendShaderLowering.canHandle(desc), "should accept ImageDraw child with pixels")
    }

    @Test
    fun `GPUBlendShaderLowering refuses ImageDraw without pixels`() {
        val desc = GPUMaterialDescriptor.BlendShader(
            mode = "src_over",
            dst = GPUMaterialDescriptor.SolidColor(r = 1f, g = 1f, b = 1f, a = 1f),
            src = GPUMaterialDescriptor.ImageDraw(
                imageSourceId = "test",
                imageWidth = 4,
                imageHeight = 4,
            ),
        )
        assertTrue(!GPUBlendShaderLowering.canHandle(desc), "should refuse ImageDraw child without pixels")
    }
}
