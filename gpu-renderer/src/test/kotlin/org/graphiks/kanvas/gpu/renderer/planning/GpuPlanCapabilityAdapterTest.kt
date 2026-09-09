package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport

class GpuPlanCapabilityAdapterTest {
    @Test
    fun `adapter preserves W4c D24S8 facts from single sample evidence`() {
        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            capabilities(depthStencilFormatSupported = false, depthStencilSamples = setOf(1))
                .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot

        assertEquals(
            setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
            snapshot.supportedDepthStencilFormats(),
        )
        assertTrue(PlanOperationCapability.DepthStencilAttachment in snapshot.supportedOperations())
        assertTrue(PlanOperationCapability.StencilCover in snapshot.supportedOperations())
    }

    @Test
    fun `adapter publishes W4c D24S8 facts when depth stencil supports one sample`() {
        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            capabilities(depthStencilFormatSupported = true, depthStencilSamples = setOf(1))
                .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot

        assertEquals(
            setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
            snapshot.supportedDepthStencilFormats(),
        )
        assertTrue(PlanOperationCapability.DepthStencilAttachment in snapshot.supportedOperations())
        assertTrue(PlanOperationCapability.StencilCover in snapshot.supportedOperations())
    }

    @Test
    fun `adapter leaves W4c depth stencil facts absent without one sample evidence`() {
        val absentEvidence = capabilities(
            depthStencilFormatSupported = false,
            depthStencilSamples = emptySet(),
        )
        val fourSamplesOnly = capabilities(
            depthStencilFormatSupported = false,
            depthStencilSamples = setOf(4),
        )

        listOf(absentEvidence, fourSamplesOnly).forEach { physical ->
            val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
                physical.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            ).snapshot

            assertEquals(emptySet(), snapshot.supportedDepthStencilFormats())
            assertEquals(false, PlanOperationCapability.DepthStencilAttachment in snapshot.supportedOperations())
            assertEquals(false, PlanOperationCapability.StencilCover in snapshot.supportedOperations())
        }
    }

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

    @Test
    fun `adapter projects each isolated color usage bit exactly without AA4`() {
        val color = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val planUsages = listOf(
            PlanResourceUsage.RenderAttachment,
            PlanResourceUsage.CopySource,
            PlanResourceUsage.CopyDestination,
            PlanResourceUsage.Sampled,
        )
        val cases = listOf(
            GPUTextureUsage.RenderAttachment to PlanResourceUsage.RenderAttachment,
            GPUTextureUsage.CopySrc to PlanResourceUsage.CopySource,
            GPUTextureUsage.CopyDst to PlanResourceUsage.CopyDestination,
            GPUTextureUsage.TextureBinding to PlanResourceUsage.Sampled,
        )

        cases.forEach { (observedUsage, expectedUsage) ->
            val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
                capabilities(textureUsage = observedUsage).toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            ).snapshot

            planUsages.forEach { usage ->
                assertEquals(
                    usage == expectedUsage,
                    snapshot.supportsTexture(color, 1, setOf(usage)),
                    "$observedUsage must project only to $expectedUsage",
                )
            }
            assertEquals(false, snapshot.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
        }
    }

    @Test
    fun `adapter keeps one sample color usage projection exact when AA4 topology is active`() {
        val color = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val planUsages = listOf(
            PlanResourceUsage.RenderAttachment,
            PlanResourceUsage.CopySource,
            PlanResourceUsage.CopyDestination,
            PlanResourceUsage.Sampled,
        )
        val cases = listOf(
            Pair(
                GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc or GPUTextureUsage.TextureBinding,
                setOf(
                    PlanResourceUsage.RenderAttachment,
                    PlanResourceUsage.CopySource,
                    PlanResourceUsage.Sampled,
                ),
            ),
            Pair(
                GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc or
                    GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding,
                setOf(
                    PlanResourceUsage.RenderAttachment,
                    PlanResourceUsage.CopySource,
                    PlanResourceUsage.CopyDestination,
                    PlanResourceUsage.Sampled,
                ),
            ),
        )

        cases.forEach { (observedUsage, expectedUsages) ->
            val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
                w4dPhysicalCapabilities(textureUsage = observedUsage)
                    .toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            ).snapshot

            planUsages.forEach { usage ->
                assertEquals(
                    usage in expectedUsages,
                    snapshot.supportsTexture(color, 1, setOf(usage)),
                    "$observedUsage must preserve $usage exactly while AA4 is available",
                )
            }
            assertTrue(snapshot.supportsTexture(color, 1, expectedUsages))
            assertTrue(snapshot.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
        }
    }

    @Test
    fun `adapter withholds hard-mask facts when a required physical observation is absent`() {
        val physical = w4dPhysicalCapabilities()
        val supported = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            physical.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot
        val color = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)

        assertTrue(supported.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
        assertTrue(supported.supportsResolve(color, 4, 1))
        assertTrue(
            supported.supportsTexture(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        )

        val missingLinearMaskFormat = physical.copy(
            supportedTextureFormats = physical.supportedTextureFormats - GPUTextureFormat.RGBA8Unorm,
        )
        val missingTextureBinding = physical.copy(
            supportedTextureUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
        )
        val missingCopySource = physical.copy(
            supportedTextureUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding,
        )
        val missingOneSample = physical.copy(
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(
                physical.textureFormatSampleSupport + (
                    GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                        renderAttachmentSampleCounts = setOf(4),
                    )
                ),
            ),
        )

        listOf(missingLinearMaskFormat, missingTextureBinding).forEach { incomplete ->
            val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
                incomplete.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
            ).snapshot
            assertEquals(false, snapshot.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
            assertEquals(false, snapshot.supportsResolve(color, 4, 1))
            assertEquals(
                false,
                snapshot.supportsTexture(
                    PlanTextureFormat.CoverageMask,
                    1,
                    setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
                ),
            )
        }

        val sampleableWithoutCopySource = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            missingCopySource.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot
        assertTrue(
            sampleableWithoutCopySource.supportsTexture(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        )

        val missingSampleSnapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            missingOneSample.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot
        assertEquals(
            false,
            missingSampleSnapshot.supportsTexture(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        )
    }

    @Test
    fun `adapter refuses generic incomplete facts`() {
        val genericIncompleteFacts = w4dPhysicalCapabilities().copy(
            textureFormatSampleSupport = GPUTextureFormatSampleSupport(),
        )
        assertIs<GpuPlanCapabilityAdapterResult.Unsupported>(
            genericIncompleteFacts.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        )
    }

    @Test
    fun `adapter publishes W4d AA from render-only D24S8 table evidence`() {
        val productionShaped = w4dPhysicalCapabilities(
            includeDepthStencilInBroadFormats = false,
        )

        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            productionShaped.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot
        val color = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val depth = PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)

        assertTrue(snapshot.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
        assertTrue(snapshot.supportsResolve(color, 4, 1))
        assertTrue(snapshot.supportsTexture(depth, 1, setOf(PlanResourceUsage.DepthStencilAttachment)))
        assertTrue(snapshot.supportsTexture(depth, 4, setOf(PlanResourceUsage.DepthStencilAttachment)))
    }

    @Test
    fun `adapter revokes W4d AA when render-only D24S8 lacks four sample evidence`() {
        val incomplete = w4dPhysicalCapabilities(depthStencilSamples = setOf(1))

        val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
            incomplete.toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
        ).snapshot
        val color = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val depth = PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8)

        assertEquals(false, snapshot.supportsTexture(color, 4, setOf(PlanResourceUsage.RenderAttachment)))
        assertEquals(false, snapshot.supportsResolve(color, 4, 1))
        assertTrue(snapshot.supportsTexture(depth, 1, setOf(PlanResourceUsage.DepthStencilAttachment)))
        assertEquals(false, snapshot.supportsTexture(depth, 4, setOf(PlanResourceUsage.DepthStencilAttachment)))
    }

    private fun requiredPlanFeatures(): Set<GPURendererFeature> = setOf(
        GPURendererFeature.RenderPass,
        GPURendererFeature.CopyUpload,
        GPURendererFeature.UniformBuffer,
        GPURendererFeature.Readback,
    )

    private fun capabilities(
        rendererFeatures: Set<GPURendererFeature> = requiredPlanFeatures(),
        depthStencilFormatSupported: Boolean = false,
        depthStencilSamples: Set<Int> = emptySet(),
        textureUsage: GPUTextureUsage = GPUTextureUsage.RenderAttachment or
            GPUTextureUsage.CopySrc or
            GPUTextureUsage.CopyDst or
            GPUTextureUsage.TextureBinding,
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
        supportedTextureFormats = buildSet {
            add(GPUTextureFormat.RGBA8UnormSrgb)
            if (depthStencilFormatSupported) add(GPUTextureFormat.Depth24PlusStencil8)
        },
        supportedTextureUsage = textureUsage,
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            buildMap {
                put(
                    GPUTextureFormat.RGBA8UnormSrgb,
                    GPUTextureSampleCountSupport(renderAttachmentSampleCounts = setOf(1)),
                )
                if (depthStencilSamples.isNotEmpty()) {
                    put(
                        GPUTextureFormat.Depth24PlusStencil8,
                        GPUTextureSampleCountSupport(renderAttachmentSampleCounts = depthStencilSamples),
                    )
                }
            },
        ),
        rendererFeatures = rendererFeatures,
    )

    private fun w4dPhysicalCapabilities(
        depthStencilSamples: Set<Int> = setOf(1, 4),
        includeDepthStencilInBroadFormats: Boolean = true,
        textureUsage: GPUTextureUsage = GPUTextureUsage.RenderAttachment or
            GPUTextureUsage.CopySrc or
            GPUTextureUsage.CopyDst or
            GPUTextureUsage.TextureBinding,
    ): GPUCapabilities = capabilities(
        depthStencilFormatSupported = includeDepthStencilInBroadFormats,
        depthStencilSamples = depthStencilSamples,
        textureUsage = textureUsage,
    ).copy(
        supportedTextureFormats = buildSet {
            add(GPUTextureFormat.RGBA8UnormSrgb)
            add(GPUTextureFormat.RGBA8Unorm)
            if (includeDepthStencilInBroadFormats) add(GPUTextureFormat.Depth24PlusStencil8)
        },
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(
                GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1, 4),
                    resolveSourceSampleCounts = setOf(4),
                ),
                GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = setOf(1),
                ),
                GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(
                    renderAttachmentSampleCounts = depthStencilSamples,
                ),
            ),
        ),
    )
}
