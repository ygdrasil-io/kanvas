package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport

class GpuPlanCapabilityAdapterTest {
    @Test
    fun `adapter publishes exact W4 planning facts`() {
        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            capabilities(rendererFeatures = requiredPlanFeatures()).toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot

        assertEquals(256, snapshot.minUniformBufferOffsetAlignment)
        assertEquals(1, snapshot.maxDynamicUniformBuffersPerPipelineLayout)
        assertEquals(
            setOf(
                PlanOperationCapability.RenderPass,
                PlanOperationCapability.CopyUpload,
                PlanOperationCapability.UniformBuffer,
                PlanOperationCapability.Readback,
            ),
            snapshot.supportedOperations(),
        )
        assertEquals(16_384, snapshot.bufferAllocationPolicy.vertexFloorBytes)
    }

    @Test
    fun `adapter keeps missing renderer operations absent from the snapshot`() {
        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            capabilities(rendererFeatures = requiredPlanFeatures() - GPURendererFeature.UniformBuffer)
                .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot

        assertEquals(false, PlanOperationCapability.UniformBuffer in snapshot.supportedOperations())
    }

    @Test
    fun `supported renderer capabilities become a handle-free W3 snapshot`() {
        val result = capabilities().toPlanCapabilitySnapshot(GPUDeviceGenerationID(7))

        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(result).snapshot
        assertEquals(7L, snapshot.deviceGeneration)
        assertEquals(2048, snapshot.maxTextureDimension2D)
        assertEquals(1L shl 20, snapshot.maxBufferSizeBytes)
        assertEquals(256, snapshot.copyBytesPerRowAlignment)
        assertEquals(
            setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            snapshot.supportedFormats(),
        )
    }

    @Test
    fun `missing observed buffer limit is rejected as unsupported capability`() {
        val result = capabilities().copy(limits = capabilities().limits?.copy(maxBufferSize = null))
            .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7))

        assertIs<GpuPlanCapabilityAdapterResult.Unsupported>(result)
    }

    @Test
    fun `missing observed limits are rejected as unsupported capability`() {
        val result = capabilities().copy(limits = null)
            .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7))

        assertIs<GpuPlanCapabilityAdapterResult.Unsupported>(result)
    }

    @Test
    fun `unrepresentable limits and missing sRGB format are rejected`() {
        val oversizedTexture = capabilities().copy(
            limits = capabilities().limits?.copy(
                maxTextureDimension2D = Int.MAX_VALUE.toLong() + 1L,
            ),
        )
        val oversizedAlignment = capabilities().copy(
            limits = capabilities().limits?.copy(
                copyBytesPerRowAlignment = Int.MAX_VALUE.toLong() + 1L,
            ),
        )
        val nonPowerOfTwoAlignment = capabilities().copy(
            limits = capabilities().limits?.copy(copyBytesPerRowAlignment = 3L),
        )
        val noSrgb = capabilities().copy(
            supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm),
        )

        listOf(oversizedTexture, oversizedAlignment, nonPowerOfTwoAlignment, noSrgb).forEach { capabilities ->
            assertIs<GpuPlanCapabilityAdapterResult.Unsupported>(
                capabilities.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            )
        }
    }

    @Test
    fun `sRGB is advertised only with an observed single sample render attachment`() {
        val missingSampleEvidence = capabilities().copy(
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(),
        )
        val fourSamplesOnly = capabilities().copy(
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(
                mapOf(
                    GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                        renderAttachmentSampleCounts = setOf(4),
                        resolveSourceSampleCounts = setOf(4),
                    ),
                ),
            ),
        )

        listOf(missingSampleEvidence, fourSamplesOnly).forEach { unsupported ->
            val result = assertIs<GpuPlanCapabilityAdapterResult.Unsupported>(
                unsupported.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            )
            assertEquals("w3.capability.format", result.diagnostic.code.value)
        }
    }

    private fun requiredPlanFeatures(): Set<GPURendererFeature> = setOf(
        GPURendererFeature.RenderPass,
        GPURendererFeature.CopyUpload,
        GPURendererFeature.UniformBuffer,
        GPURendererFeature.Readback,
    )

    private fun capabilities(
        rendererFeatures: Set<GPURendererFeature> = requiredPlanFeatures(),
    ) = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "w3-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
                ),
            ),
        ),
        rendererFeatures = rendererFeatures,
    )
}
