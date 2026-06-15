package org.graphiks.kanvas.gpu.renderer.resources

import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImagePixelsDescriptor
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageSamplingPlan
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageShaderPreparedPlanner
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class UploadedTextureArtifactOwnershipGateTest {
    @Test
    fun `uploaded texture artifact builds ownership and upload allocation evidence`() {
        val imagePlan = decodedImagePlan()
        val gate = GPUUploadedTextureArtifactOwnershipGate().plan(
            request = uploadedTextureRequest(imagePlan),
        )

        assertEquals("CPUPreparedGPU", gate.routeKind)
        assertEquals("uploaded-image:checker", gate.ownership.ownerLabel)
        assertEquals("recording-local", gate.ownership.lifetimeClass)
        assertEquals("recording-complete", gate.ownership.releasePolicy)
        assertEquals(setOf("copy_dst", "texture_binding"), gate.requiredUsageLabels)
        val allocation = assertIs<GPUTextureAllocationPlan.UploadFromArtifact>(gate.allocation)
        assertEquals(imagePlan.artifact.artifactKey.value, allocation.artifactKey)
        assertFalse(gate.dumpLines().joinToString("\n").contains("secret-texture-handle"))
        assertEquals(
            listOf(
                "texture:uploaded-artifact.accepted routeKind=CPUPreparedGPU provenance=UploadedTextureArtifact owner=uploaded-image:checker lifetime=recording-local allocation=UploadFromArtifact",
                "texture:artifact key=${imagePlan.artifact.artifactKey.value} generation=3 expectedGeneration=3 budget=image-small uploadBeforeSample=true",
                "texture:usage required=copy_dst,texture_binding available=copy_dst,texture_binding deviceGeneration=17 expectedDeviceGeneration=17",
                "texture:view descriptor=${imagePlan.viewDescriptor.textureDescriptorHash} view=2d mipRange=0..0 sampler=clamp-to-edge/clamp-to-edge/linear/linear/none",
                "texture:ownership owner=uploaded-image:checker scope=GPURecorderScope release=recording-complete materialization=upload-before-sample",
                "nonclaim:no-product-activation no-adapter-backed-execution no-live-resource-handle no-cache-residency-claim no-codec-support no-cpu-rendered-compat-texture",
            ),
            gate.dumpLines(),
        )
    }

    @Test
    fun `uploaded texture artifact refuses stale incompatible or unsafe resources`() {
        val imagePlan = decodedImagePlan()
        val cases = listOf(
            RefusalCase(
                expectedCode = "unsupported.texture.upload_artifact_generation_stale",
                request = uploadedTextureRequest(imagePlan, expectedArtifactGeneration = 4),
            ),
            RefusalCase(
                expectedCode = "unsupported.texture.usage_missing",
                request = uploadedTextureRequest(
                    imagePlan,
                    textureDescriptor = imagePlan.textureDescriptor.copy(usageLabels = setOf("copy_dst")),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.texture.device_generation_stale",
                request = uploadedTextureRequest(imagePlan, artifactDeviceGeneration = 16),
            ),
            RefusalCase(
                expectedCode = "unsupported.texture.active_attachment_sampled",
                request = uploadedTextureRequest(imagePlan, activeAttachmentSampled = true),
            ),
            RefusalCase(
                expectedCode = "unsupported.texture.descriptor_invalid",
                request = uploadedTextureRequest(
                    imagePlan,
                    textureDescriptor = imagePlan.textureDescriptor.copy(width = 4),
                ),
            ),
            RefusalCase(
                expectedCode = "unsupported.texture.upload_artifact_missing",
                request = uploadedTextureRequest(
                    imagePlan,
                    artifactType = "DecodedPixelsOnly",
                ),
            ),
        )

        for (case in cases) {
            val gate = GPUUploadedTextureArtifactOwnershipGate().plan(case.request)

            assertEquals("RefuseDiagnostic", gate.routeKind)
            assertEquals(case.expectedCode, gate.diagnostics.first().code)
            assertContains(gate.dumpLines().first(), "reason=${case.expectedCode}")
            assertContains(gate.dumpLines().last(), "no-cpu-rendered-compat-texture")
        }
    }
}

private data class RefusalCase(
    val expectedCode: String,
    val request: GPUUploadedTextureArtifactOwnershipRequest,
)

private fun decodedImagePlan() =
    GPUDecodedImageShaderPreparedPlanner().plan(
        source = GPUDecodedImagePixelsDescriptor(
            sourceId = "image:checker:v1",
            width = 2,
            height = 2,
            pixelFormat = "RGBA8Unorm",
            rowBytes = 8,
            alphaType = "Premul",
            colorProfileLabel = "srgb",
            orientationState = "Applied",
            generation = 3,
            contentHash = "sha256:checker-pixels-v1",
            provenance = "unit-test",
        ),
        sampling = GPUDecodedImageSamplingPlan(
            tileModeX = "clamp",
            tileModeY = "clamp",
            filterMode = "linear",
            mipmapMode = "none",
        ),
    )

private fun uploadedTextureRequest(
    imagePlan: org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageShaderRoutePlan,
    textureDescriptor: GPUTextureDescriptor = imagePlan.textureDescriptor,
    expectedArtifactGeneration: Long = imagePlan.artifact.generation,
    artifactDeviceGeneration: Long = 17,
    activeAttachmentSampled: Boolean = false,
    artifactType: String = imagePlan.artifact.artifactType,
) = GPUUploadedTextureArtifactOwnershipRequest(
    artifact = GPUUploadedTextureArtifactFacts(
        artifactKey = imagePlan.artifact.artifactKey.value,
        artifactType = artifactType,
        pixelWidth = imagePlan.artifact.pixelPlan.width,
        pixelHeight = imagePlan.artifact.pixelPlan.height,
        pixelFormat = imagePlan.artifact.pixelPlan.format,
        generation = imagePlan.artifact.generation,
        lifetimeClass = imagePlan.artifact.lifetimeClass,
        uploadBudgetClass = imagePlan.artifact.uploadPlan.uploadBudgetClass,
    ),
    textureDescriptor = textureDescriptor,
    viewDescriptor = imagePlan.viewDescriptor,
    samplerDescriptor = imagePlan.samplerDescriptor,
    ownerLabel = "uploaded-image:checker",
    ownerScope = "GPURecorderScope",
    expectedArtifactGeneration = expectedArtifactGeneration,
    artifactDeviceGeneration = artifactDeviceGeneration,
    expectedDeviceGeneration = 17,
    requiredUsageLabels = setOf("copy_dst", "texture_binding"),
    activeAttachmentSampled = activeAttachmentSampled,
    debugLiveResourceLabel = "secret-texture-handle-0xCAFE",
)
