package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.images.AlphaType
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactFactory
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageArtifactResult
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageOrientation
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProfile
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageProvenance
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceClass
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceFormat
import org.graphiks.kanvas.gpu.renderer.images.GPUPreparedImageSourceInput
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID

class GPUPreparedImageFrameResourcePlanTest {
    @Test
    fun `width three A8 upload keeps logical RGBA8 stride and zeroes aligned padding`() {
        val artifact = artifact(
            format = GPUPreparedImageSourceFormat.A8,
            sourceRowBytes = 3,
            bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
        )

        val plan = buildPreparedImageFrameResourcePlan(
            artifact = artifact,
            packetIds = listOf("packet.image.4", "packet.image.9"),
            bindingLayoutHash = "texture-sampler-tint.v1",
            capabilities = capabilities(),
            frameIdentity = "frame-17",
            uploadTaskId = GPUTaskID("task.image.upload"),
        )

        assertEquals(12, plan.uploadLayout.logicalBytesPerRow)
        assertEquals(256, plan.uploadLayout.bytesPerRow)
        assertEquals(2, plan.uploadLayout.rowsPerImage)
        val upload = plan.uploadLayout.bytesForUpload()
        assertEquals(512, upload.size)
        assertContentEquals(
            byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3),
            upload.copyOfRange(0, 12),
        )
        assertTrue(upload.copyOfRange(12, 256).all { it == 0.toByte() })
        assertContentEquals(
            byteArrayOf(4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6),
            upload.copyOfRange(256, 268),
        )
        assertTrue(upload.copyOfRange(268, 512).all { it == 0.toByte() })
        assertEquals("RGBA8Unorm", plan.textureDescriptor.format)
        assertEquals(setOf("copy_dst", "texture_binding"), plan.textureDescriptor.usageLabels)
        assertEquals(listOf(0L, 256L), plan.bindingRequests.map { it.uniformAllocation.offset })
        assertTrue(plan.bindingRequests.all { it.uniformAllocation.offset % 256L == 0L })
        assertTrue(plan.bindingRequests.all { it.artifactKey == artifact.key })
        assertTrue(plan.bindingRequests.all { it.bindingLayoutHash == "texture-sampler-tint.v1" })
    }

    @Test
    fun `width three BGRA upload normalizes channel order before adding zero padding`() {
        val artifact = artifact(
            format = GPUPreparedImageSourceFormat.Bgra8,
            sourceRowBytes = 12,
            bytes = byteArrayOf(
                3, 2, 1, -1,
                6, 5, 4, -1,
                9, 8, 7, -1,
                12, 11, 10, -1,
                15, 14, 13, -1,
                18, 17, 16, -1,
            ),
        )

        val plan = buildPreparedImageFrameResourcePlan(
            artifact = artifact,
            packetIds = listOf("packet.image"),
            bindingLayoutHash = "texture-sampler-tint.v1",
            capabilities = capabilities(),
            frameIdentity = "frame-18",
            uploadTaskId = GPUTaskID("task.image.upload"),
        )

        assertContentEquals(
            byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1, 7, 8, 9, -1),
            plan.uploadLayout.bytesForUpload().copyOfRange(0, 12),
        )
        assertTrue(plan.uploadLayout.bytesForUpload().copyOfRange(12, 256).all { it == 0.toByte() })
    }

    private fun artifact(
        format: GPUPreparedImageSourceFormat,
        sourceRowBytes: Long,
        bytes: ByteArray,
    ) = (GPUPreparedImageArtifactFactory.prepare(
        GPUPreparedImageSourceInput(
            sourceClass = GPUPreparedImageSourceClass.DecodedCpu,
            sourceId = "width-three",
            width = 3,
            height = 2,
            sourceFormat = format,
            alphaType = AlphaType.PREMUL,
            sourceRowBytes = sourceRowBytes,
            profile = GPUPreparedImageProfile.Srgb,
            orientation = GPUPreparedImageOrientation.AppliedIdentity,
            provenance = GPUPreparedImageProvenance.CallerPixels,
            sourceGeneration = 4,
            pixelBytes = bytes,
        ),
    ) as GPUPreparedImageArtifactResult.Ready).artifact

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "adapter", "device"),
        facts = emptyList(),
        snapshotId = "prepared-image-resource-plan",
        limits = GPULimits(
            maxTextureDimension2D = 8192,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 30,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
    )
}
