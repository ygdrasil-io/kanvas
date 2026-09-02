package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits

class GpuPlanCapabilityAdapterTest {
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

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "w3-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
    )
}
