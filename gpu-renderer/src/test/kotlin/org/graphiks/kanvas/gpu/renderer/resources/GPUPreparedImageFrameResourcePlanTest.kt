package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotSame
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
    fun `upload layout exposes only logical row bytes for hashing and rejects nonzero padding`() {
        val padded = byteArrayOf(
            1, 2, 3, 4, 0, 0, 0, 0,
            5, 6, 7, 8, 0, 0, 0, 0,
        )
        val layout = GPUPreparedImageUploadLayout(
            sourceBytesPerRow = 4,
            logicalBytesPerRow = 4,
            bytesPerRow = 8,
            rowsPerImage = 2,
            width = 1,
            height = 2,
            paddedUploadBytes = padded,
        )

        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            layout.logicalBytesForHash(),
        )
        assertFails {
            GPUPreparedImageUploadLayout(
                sourceBytesPerRow = 4,
                logicalBytesPerRow = 4,
                bytesPerRow = 8,
                rowsPerImage = 2,
                width = 1,
                height = 2,
                paddedUploadBytes = padded.copyOf().also { it[6] = 9 },
            )
        }
    }

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

        assertEquals(3, plan.uploadLayout.sourceBytesPerRow)
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

        assertEquals(12, plan.uploadLayout.sourceBytesPerRow)
        assertEquals(12, plan.uploadLayout.logicalBytesPerRow)
        assertContentEquals(
            byteArrayOf(1, 2, 3, -1, 4, 5, 6, -1, 7, 8, 9, -1),
            plan.uploadLayout.bytesForUpload().copyOfRange(0, 12),
        )
        assertTrue(plan.uploadLayout.bytesForUpload().copyOfRange(12, 256).all { it == 0.toByte() })
    }

    @Test
    fun `resource plan collections and upload bytes are defensively immutable`() {
        val plan = buildPreparedImageFrameResourcePlan(
            artifact = artifact(
                format = GPUPreparedImageSourceFormat.A8,
                sourceRowBytes = 3,
                bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
            ),
            packetIds = listOf("packet.image"),
            bindingLayoutHash = "texture-sampler-tint.v1",
            capabilities = capabilities(),
            frameIdentity = "frame-immutable",
            uploadTaskId = GPUTaskID("task.image.upload"),
        )
        val originalBytes = plan.uploadLayout.bytesForUpload()
        val callerBytes = plan.uploadLayout.bytesForUpload().also { it[0] = 99 }

        assertTrue(callerBytes[0] != plan.uploadLayout.bytesForUpload()[0])
        assertContentEquals(originalBytes, plan.uploadLayout.bytesForUpload())
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (plan.bindingRequests as MutableList<GPUPreparedImageBindingRequest>)[0] =
                plan.bindingRequests.single().copy(packetId = "mutated")
        }
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (plan.preparationRequests as MutableList<GPUResourcePreparationRequest>)[0] =
                plan.preparationRequests[1]
        }
        assertFails {
            @Suppress("UNCHECKED_CAST")
            (plan.memoryAllocations as MutableList<GPUFrameMemoryAllocation>)[0] =
                plan.memoryAllocations.first().copy(label = "mutated")
        }
    }

    @Test
    fun `resource plan retains data class structure across copy and caller mutation`() {
        val template = buildPreparedImageFrameResourcePlan(
            artifact = artifact(
                format = GPUPreparedImageSourceFormat.A8,
                sourceRowBytes = 3,
                bytes = byteArrayOf(1, 2, 3, 4, 5, 6),
            ),
            packetIds = listOf("packet.image"),
            bindingLayoutHash = "texture-sampler-tint.v1",
            capabilities = capabilities(),
            frameIdentity = "frame-data-class",
            uploadTaskId = GPUTaskID("task.image.upload"),
        )
        val descriptorUsage = template.textureDescriptor.usageLabels.toMutableSet()
        val samplerRequirements = mutableSetOf("sampler.requirement")
        val preparationUsages = template.preparationRequests.first().usages.toMutableSet()
        val callerBytes = template.uploadLayout.bytesForUpload()
        val callerBindings = template.bindingRequests.map { binding ->
            binding.copy(
                texture = binding.texture.copy(usageLabels = descriptorUsage),
                sampler = binding.sampler.copy(capabilityRequirements = samplerRequirements),
            )
        }.toMutableList()
        val callerPreparations = template.preparationRequests.mapIndexed { index, request ->
            if (index == 0) {
                GPUResourcePreparationRequest(
                    resource = request.resource,
                    descriptor = request.descriptor,
                    role = request.role,
                    usages = preparationUsages,
                    lifetime = request.lifetime,
                    byteSize = request.byteSize,
                    diagnosticLabel = request.diagnosticLabel,
                )
            } else {
                request
            }
        }.toMutableList()
        val callerAllocations = template.memoryAllocations.toMutableList()
        val plan = GPUPreparedImageFrameResourcePlan(
            stagingRef = template.stagingRef,
            textureRef = template.textureRef,
            frameTextureRef = template.frameTextureRef,
            uniformRef = template.uniformRef,
            textureDescriptor = template.textureDescriptor.copy(usageLabels = descriptorUsage),
            uploadLayout = GPUPreparedImageUploadLayout(
                sourceBytesPerRow = template.uploadLayout.sourceBytesPerRow,
                logicalBytesPerRow = template.uploadLayout.logicalBytesPerRow,
                bytesPerRow = template.uploadLayout.bytesPerRow,
                rowsPerImage = template.uploadLayout.rowsPerImage,
                width = template.uploadLayout.width,
                height = template.uploadLayout.height,
                paddedUploadBytes = callerBytes,
            ),
            uploadTaskLayout = template.uploadTaskLayout,
            bindingRequests = callerBindings,
            preparationRequests = callerPreparations,
            memoryAllocations = callerAllocations,
            uploadTaskId = template.uploadTaskId,
        )
        val copied = plan.copy()
        val (
            componentStagingRef,
            componentTextureRef,
            componentFrameTextureRef,
            componentUniformRef,
            componentTextureDescriptor,
            componentUploadLayout,
            componentUploadTaskLayout,
            componentBindingRequests,
            componentPreparationRequests,
            componentMemoryAllocations,
            componentUploadTaskId,
        ) = plan

        assertEquals(plan, copied)
        assertEquals(plan.hashCode(), copied.hashCode())
        assertNotSame(plan, copied)
        assertTrue(plan.toString().startsWith("GPUPreparedImageFrameResourcePlan("))
        assertEquals(
            listOf(
                plan.stagingRef,
                plan.textureRef,
                plan.frameTextureRef,
                plan.uniformRef,
                plan.textureDescriptor,
                plan.uploadLayout,
                plan.uploadTaskLayout,
                plan.bindingRequests,
                plan.preparationRequests,
                plan.memoryAllocations,
                plan.uploadTaskId,
            ),
            listOf(
                componentStagingRef,
                componentTextureRef,
                componentFrameTextureRef,
                componentUniformRef,
                componentTextureDescriptor,
                componentUploadLayout,
                componentUploadTaskLayout,
                componentBindingRequests,
                componentPreparationRequests,
                componentMemoryAllocations,
                componentUploadTaskId,
            ),
        )

        descriptorUsage += "storage_binding"
        samplerRequirements += "sampler.mutated"
        preparationUsages += GPUFrameResourceUsage.StorageBinding
        callerBytes[0] = 99
        callerBindings.clear()
        callerPreparations.clear()
        callerAllocations.clear()

        assertEquals(setOf("copy_dst", "texture_binding"), plan.textureDescriptor.usageLabels)
        assertEquals(setOf("sampler.requirement"), plan.bindingRequests.single().sampler.capabilityRequirements)
        assertTrue(
            GPUFrameResourceUsage.StorageBinding !in plan.preparationRequests.first().usages,
        )
        assertTrue(plan.uploadLayout.bytesForUpload()[0] != 99.toByte())
        assertEquals(1, plan.bindingRequests.size)
        assertEquals(3, plan.preparationRequests.size)
        assertEquals(3, plan.memoryAllocations.size)
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
