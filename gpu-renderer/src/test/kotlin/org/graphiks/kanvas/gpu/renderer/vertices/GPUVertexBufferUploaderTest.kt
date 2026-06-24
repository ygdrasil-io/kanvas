package org.graphiks.kanvas.gpu.renderer.vertices

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureAllocationPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUVertexBufferUploaderTest {
    @Test
    fun `upload with vertices returns expected stats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f, 16f, 16f)
        val uploader = GPUVertexBufferUploader()
        val stats = uploader.upload(vertices, null, 8)

        assertEquals(4, stats.vertexCount)
        assertEquals(32L, stats.bufferBytes)
        assertTrue(stats.uploaded)
        assertTrue(stats.providerUsed)
    }

    @Test
    fun `upload with colors returns expected stats`() {
        val vertices = listOf(0f, 0f, 16f, 0f, 0f, 16f)
        val colors = listOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 1f)
        val uploader = GPUVertexBufferUploader()
        val stats = uploader.upload(vertices, colors, 12)

        assertEquals(3, stats.vertexCount)
        assertEquals(36L, stats.bufferBytes)
        assertTrue(stats.uploaded)
    }

    @Test
    fun `upload rejects empty vertices`() {
        val uploader = GPUVertexBufferUploader()
        val exception = runCatching {
            uploader.upload(emptyList(), null, 8)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("non-empty") == true)
    }

    @Test
    fun `upload rejects odd float count`() {
        val uploader = GPUVertexBufferUploader()
        val exception = runCatching {
            uploader.upload(listOf(0f, 0f, 16f), null, 8)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("pairs") == true)
    }

    @Test
    fun `upload rejects non-positive stride`() {
        val uploader = GPUVertexBufferUploader()
        val exception = runCatching {
            uploader.upload(listOf(0f, 0f), null, 0)
        }.exceptionOrNull()

        assertTrue(exception?.message?.contains("positive stride") == true)
    }

    @Test
    fun `nonClaim line is present in stats`() {
        val uploader = GPUVertexBufferUploader()
        val stats = uploader.upload(listOf(0f, 0f, 16f, 0f), null, 8)

        assertTrue(stats.nonClaimLine.isNotBlank())
        assertTrue(stats.nonClaimLine.contains("vertexBufferUploadSupported=true"))
        assertFalse(stats.nonClaimLine.contains("productActivation=true"))
    }

    @Test
    fun `default resource provider refuses materialization`() {
        val provider = GPUVertexBufferUploader.defaultResourceProvider()
        val diagnostic = GPUResourceDiagnostic(
            code = "unsupported.resource.provider_unconfigured",
            resourceLabel = "vertex-buffer-uploader",
            message = "GPUVertexBufferUploader default provider is not configured",
            terminal = true,
        )
        val decision = provider.materialize(
            GPUTextureAllocationPlan.Refuse(diagnostic),
            GPUTargetPreparationContext(
                targetId = "vertex-buffer-uploader",
                frameId = "test-frame",
                deviceGeneration = 0L,
                budgetClass = "test",
            ),
        )

        val refused = decision as? GPUResourceMaterializationDecision.Refused
        assertTrue(refused != null)
        assertEquals("unsupported.resource.provider_unconfigured", refused.diagnostic.code)
    }
}
