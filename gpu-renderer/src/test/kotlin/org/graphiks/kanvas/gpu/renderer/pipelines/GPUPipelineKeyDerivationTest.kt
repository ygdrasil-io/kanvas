package org.graphiks.kanvas.gpu.renderer.pipelines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/** Verifies deterministic render pipeline key derivation boundaries. */
class GPUPipelineKeyDerivationTest {
    /** Equivalent render preimages produce identical canonical dumps and keys. */
    @Test
    fun `render pipeline preimage dump and keys are deterministic`() {
        val base = renderPreimage(capabilityFacts = listOf("limit:maxColorAttachments=4", "feature:timestamp=false"))
        val reorderedCapabilities = base.copy(
            capabilityFacts = listOf("feature:timestamp=false", "limit:maxColorAttachments=4"),
        )

        val expectedDump = """
            kind=render
            rendererSalt=kanvas-gpu-renderer-v1
            renderStepIdentity=rect-fill
            renderStepVersion=1
            primitiveTopology=triangle-list
            materialKeyHash=material:solid
            materialProgramId=program:solid
            materialDictionaryVersion=dict:v1
            materialLayoutHash=layout:solid-rgba
            snippetIdentityHash=snippet:solid-source
            moduleHash=module:solid-v1
            vertexLayoutHash=vertex:rect-v1
            targetFormatClass=rgba8unorm
            blendStateHash=blend:src-over
            sampleStateHash=sample:1
            bindGroupLayoutHash=bind-layout:solid-v1
            capabilityClass=webgpu-core
            capabilityFacts=[feature:timestamp=false,limit:maxColorAttachments=4]
        """.trimIndent()

        assertEquals(expectedDump, GPUPipelineKeys.canonicalRenderPreimage(base))
        assertEquals(expectedDump, GPUPipelineKeys.canonicalRenderPreimage(reorderedCapabilities))
        assertEquals(
            GPUPipelineKeys.renderPipelineKey(base),
            GPUPipelineKeys.renderPipelineKey(reorderedCapabilities),
        )
        assertEquals(
            GPUPipelineKeys.pipelineCacheKey(base),
            GPUPipelineKeys.pipelineCacheKey(reorderedCapabilities),
        )
    }

    /** Only executable layout, code, state, and capability axes affect render keys. */
    @Test
    fun `render pipeline keys change for executable key axes only`() {
        val base = renderPreimage()
        val baseKey = GPUPipelineKeys.renderPipelineKey(base)

        val keyChangingAxes = listOf(
            base.copy(materialLayoutHash = "layout:solid-rgba-v2"),
            base.copy(snippetIdentityHash = "snippet:solid-source-v2"),
            base.copy(materialDictionaryVersion = "dict:v2"),
            base.copy(materialProgramId = "program:solid-v2"),
            base.copy(moduleHash = "module:solid-v2"),
            base.copy(targetFormatClass = "bgra8unorm"),
            base.copy(blendStateHash = "blend:copy"),
            base.copy(sampleStateHash = "sample:4"),
            base.copy(bindGroupLayoutHash = "bind-layout:solid-v2"),
            base.copy(vertexLayoutHash = "vertex:rect-v2"),
            base.copy(capabilityClass = "webgpu-core-msaa"),
            base.copy(capabilityFacts = listOf("feature:timestamp=true", "limit:maxColorAttachments=4")),
        )

        keyChangingAxes.forEach { changed ->
            assertNotEquals(baseKey, GPUPipelineKeys.renderPipelineKey(changed))
        }
    }

    /** Render preimage fields exclude per-draw and concrete resource identity. */
    @Test
    fun `render pipeline preimage does not accept uniform or concrete resource identity fields`() {
        val forbiddenFieldFragments = listOf(
            "rect",
            "bounds",
            "radii",
            "rgba",
            "colorValue",
            "textureHandle",
            "surfaceLease",
            "bindGroupInstance",
            "resourceHandle",
        )
        val renderFieldNames = GPUPipelineKeyPreimage.Render::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }

        forbiddenFieldFragments.forEach { fragment ->
            assertFalse(
                actual = renderFieldNames.any { it.contains(fragment, ignoreCase = true) },
                message = "Render pipeline key preimage must not include $fragment fields: $renderFieldNames",
            )
        }
    }

    private fun renderPreimage(
        capabilityFacts: List<String> = listOf("feature:timestamp=false", "limit:maxColorAttachments=4"),
    ): GPUPipelineKeyPreimage.Render =
        GPUPipelineKeyPreimage.Render(
            renderStepIdentity = "rect-fill",
            renderStepVersion = "1",
            primitiveTopology = "triangle-list",
            materialKeyHash = "material:solid",
            materialProgramId = "program:solid",
            materialDictionaryVersion = "dict:v1",
            materialLayoutHash = "layout:solid-rgba",
            snippetIdentityHash = "snippet:solid-source",
            moduleHash = "module:solid-v1",
            vertexLayoutHash = "vertex:rect-v1",
            targetFormatClass = "rgba8unorm",
            blendStateHash = "blend:src-over",
            sampleStateHash = "sample:1",
            bindGroupLayoutHash = "bind-layout:solid-v1",
            capabilityClass = "webgpu-core",
            capabilityFacts = capabilityFacts,
            rendererSalt = "kanvas-gpu-renderer-v1",
        )
}
