package org.graphiks.kanvas.gpu.renderer.pipelines

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class GPUPipelineProviderTest {
    @Test
    fun fullscreenRequestRejectsBlankShaderSource() {
        assertFailsWith<IllegalArgumentException> {
            GPUFullscreenPipelineRequest(
                shaderSource = " ",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
            )
        }
    }

    @Test
    fun texturedVertexRequestRejectsBlankShaderSource() {
        assertFailsWith<IllegalArgumentException> {
            GPUTexturedVertexPipelineRequest(
                shaderSource = "\n",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
                textureFormat = GPUTextureFormat.RGBA8Unorm,
            )
        }
    }

    @Test
    fun dualUvVertexRequestRejectsBlankShaderSource() {
        assertFailsWith<IllegalArgumentException> {
            GPUDualUvVertexPipelineRequest(
                shaderSource = "\r",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
                textureFormat = GPUTextureFormat.RGBA8Unorm,
            )
        }
    }

    @Test
    fun renderStepRequestRejectsBlankRenderStepIdentity() {
        assertFailsWith<IllegalArgumentException> {
            GPURenderStepPipelineRequest(
                renderStepIdentity = "\t",
                renderStepVersion = "v1",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
            )
        }
    }

    @Test
    fun renderStepRequestRejectsBlankRenderStepVersion() {
        assertFailsWith<IllegalArgumentException> {
            GPURenderStepPipelineRequest(
                renderStepIdentity = "fill-rect",
                renderStepVersion = " ",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
            )
        }
    }

    @Test
    fun texturedAndDualUvRequestsCarryTextureFormatEnums() {
        val textured = GPUTexturedVertexPipelineRequest(
            shaderSource = "textured shader",
            colorFormat = GPUTextureFormat.BGRA8Unorm,
            textureFormat = GPUTextureFormat.RGBA8Unorm,
            blendMode = GPUBlendMode.SrcOver,
        )
        val dualUv = GPUDualUvVertexPipelineRequest(
            shaderSource = "dual uv shader",
            colorFormat = GPUTextureFormat.RGBA8UnormSrgb,
            textureFormat = GPUTextureFormat.R8Unorm,
            blendMode = GPUBlendMode.Src,
        )

        assertEquals(GPUTextureFormat.BGRA8Unorm, textured.colorFormat)
        assertEquals(GPUTextureFormat.RGBA8Unorm, textured.textureFormat)
        assertEquals(GPUTextureFormat.RGBA8UnormSrgb, dualUv.colorFormat)
        assertEquals(GPUTextureFormat.R8Unorm, dualUv.textureFormat)
        assertEquals(GPUBlendMode.Src, dualUv.blendMode)
    }

    @Test
    fun fakeProviderCanResolvePipelinesWithStringHandles() {
        val provider = FakePipelineProvider()

        val fullscreen = provider.resolveFullscreenPipeline(
            GPUFullscreenPipelineRequest(
                shaderSource = "fullscreen shader",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
            ),
        )
        val textured = provider.resolveTexturedVertexPipeline(
            GPUTexturedVertexPipelineRequest(
                shaderSource = "textured shader",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
                textureFormat = GPUTextureFormat.RGBA8Unorm,
                blendMode = GPUBlendMode.SrcOver,
            ),
        )
        val dualUv = provider.resolveDualUvVertexPipeline(
            GPUDualUvVertexPipelineRequest(
                shaderSource = "dual uv shader",
                colorFormat = GPUTextureFormat.RGBA8UnormSrgb,
                textureFormat = GPUTextureFormat.R8Unorm,
            ),
        )
        val renderStep = provider.resolveRenderStepPipeline(
            GPURenderStepPipelineRequest(
                renderStepIdentity = "fill-rect",
                renderStepVersion = "v1",
                colorFormat = GPUTextureFormat.BGRA8Unorm,
            ),
        )

        assertEquals("fullscreen:BGRA8Unorm", fullscreen.pipeline)
        assertEquals(listOf("fullscreen-layout:BGRA8Unorm"), fullscreen.bindGroupLayouts)
        assertEquals("textured:BGRA8Unorm:RGBA8Unorm:SrcOver", textured.pipeline)
        assertEquals(listOf("textured-layout:RGBA8Unorm", "sampler-layout:RGBA8Unorm"), textured.bindGroupLayouts)
        assertEquals("dual-uv:RGBA8UnormSrgb:R8Unorm", dualUv.pipeline)
        assertEquals(listOf("dual-uv-layout:R8Unorm"), dualUv.bindGroupLayouts)
        assertEquals("render-step:fill-rect:v1:BGRA8Unorm", renderStep.pipeline)
        assertEquals(listOf("render-step-layout:fill-rect:v1"), renderStep.bindGroupLayouts)
    }
}

private class FakePipelineProvider : GPUPipelineProvider<String, String> {
    override fun resolveFullscreenPipeline(
        request: GPUFullscreenPipelineRequest,
    ): GPUPipelineResolution<String, String> =
        GPUPipelineResolution(
            pipeline = "fullscreen:${request.colorFormat.name}",
            bindGroupLayouts = listOf("fullscreen-layout:${request.colorFormat.name}"),
        )

    override fun resolveTexturedVertexPipeline(
        request: GPUTexturedVertexPipelineRequest,
    ): GPUPipelineResolution<String, String> =
        GPUPipelineResolution(
            pipeline = "textured:${request.colorFormat.name}:${request.textureFormat.name}:${request.blendMode?.name}",
            bindGroupLayouts = listOf(
                "textured-layout:${request.textureFormat.name}",
                "sampler-layout:${request.textureFormat.name}",
            ),
        )

    override fun resolveDualUvVertexPipeline(
        request: GPUDualUvVertexPipelineRequest,
    ): GPUPipelineResolution<String, String> =
        GPUPipelineResolution(
            pipeline = "dual-uv:${request.colorFormat.name}:${request.textureFormat.name}",
            bindGroupLayouts = listOf("dual-uv-layout:${request.textureFormat.name}"),
        )

    override fun resolveRenderStepPipeline(
        request: GPURenderStepPipelineRequest,
    ): GPUPipelineResolution<String, String> =
        GPUPipelineResolution(
            pipeline = "render-step:${request.renderStepIdentity}:${request.renderStepVersion}:${request.colorFormat.name}",
            bindGroupLayouts = listOf("render-step-layout:${request.renderStepIdentity}:${request.renderStepVersion}"),
        )
}
