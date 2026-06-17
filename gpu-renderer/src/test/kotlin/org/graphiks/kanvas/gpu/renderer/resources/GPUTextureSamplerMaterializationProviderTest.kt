package org.graphiks.kanvas.gpu.renderer.resources

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImagePixelsDescriptor
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageSamplingPlan
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageShaderPreparedPlanner
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageShaderRoutePlan

/** Verifies KGPU-M11-004 texture/view/sampler live materialization contracts. */
class GPUTextureSamplerMaterializationProviderTest {
    /** Accepted uploaded texture ownership plans become provider-owned texture, view, and sampler operands. */
    @Test
    fun `texture sampler provider materializes uploaded artifact texture view and sampler refs`() {
        val imagePlan = decodedImagePlan()
        val ownershipGate = GPUUploadedTextureArtifactOwnershipGate().plan(uploadedTextureRequest(imagePlan))

        val decision = ValidatingTextureSamplerResourceProvider().materializeTextureSamplerBinding(
            request = textureSamplerRequest(imagePlan, ownershipGate),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(decision)
        assertEquals(listOf(GPUTextureResourceRef("texture-ref:uploaded-image:checker")), materialized.resources)
        assertEquals(
            listOf(
                GPUMaterializedCommandOperandKind.Texture,
                GPUMaterializedCommandOperandKind.TextureView,
                GPUMaterializedCommandOperandKind.Sampler,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.kind },
        )

        val dump = materialized.dumpLines().joinToString("\n")
        assertContains(dump, "operand=texture:uploaded-image:checker kind=texture")
        assertContains(dump, "operand=texture-view:sampled-texture.image-shader kind=texture-view")
        assertContains(dump, "operand=sampler:sampled-texture.image-shader kind=sampler")
        assertContains(dump, "allocation=UploadFromArtifact")
        assertContains(dump, "uploadBeforeSample=true")
        assertContains(dump, "cpuRenderedCompatTexture=false")
        assertContains(dump, "viewDimension=2d")
        assertContains(dump, "mipRange=0..0")
        assertContains(dump, "address=clamp-to-edge/clamp-to-edge")
        assertContains(dump, "filter=linear/linear")
        assertFalse(dump.contains("WGPU"))
        assertFalse(dump.contains("@0x"))
        assertFalse(dump.contains("secret-texture-handle"))

        val skippedReadback = skippedTextureSamplerReadback()
        val readbackDump = skippedReadback.dumpLines().joinToString("\n")
        assertContains(readbackDump, "execution.readback:skipped")
        assertContains(readbackDump, "reason=unsupported.execution.readback_unavailable")
        assertContains(readbackDump, "failureReason=kgpu-m11-004.adapter-readback-not-promoted")
        assertFalse(readbackDump.contains("execution.readback:completed"))
    }

    /** Texture/sampler materialization refuses unsupported boundary and resource facts before command encoding. */
    @Test
    fun `texture sampler provider refuses missing usage mips swizzle stale generations and upload failures`() {
        val imagePlan = decodedImagePlan()
        val ownershipGate = GPUUploadedTextureArtifactOwnershipGate().plan(uploadedTextureRequest(imagePlan))
        val cases = listOf(
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.usage_missing",
                request = textureSamplerRequest(
                    imagePlan,
                    ownershipGate,
                    textureDescriptor = imagePlan.textureDescriptor.copy(usageLabels = setOf("copy_dst")),
                ),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.device_generation_stale",
                request = textureSamplerRequest(imagePlan, ownershipGate, deviceGeneration = 16),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.resource.binding_generation_stale",
                request = textureSamplerRequest(imagePlan, ownershipGate, actualResourceGeneration = 2),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.mipmap_unavailable",
                request = textureSamplerRequest(imagePlan, ownershipGate, requiredMipLevels = 2),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.swizzle_unimplemented",
                request = textureSamplerRequest(imagePlan, ownershipGate, swizzleRequired = true),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.image.sampling_cubic",
                request = textureSamplerRequest(
                    imagePlan,
                    ownershipGate,
                    unsupportedSamplingReason = "unsupported.image.sampling_cubic",
                ),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.allocation_failed",
                request = textureSamplerRequest(imagePlan, ownershipGate, uploadFailedReason = "queue-write-failed"),
            ),
            TextureSamplerRefusalCase(
                expectedCode = "unsupported.texture.active_attachment_sampled",
                request = textureSamplerRequest(imagePlan, ownershipGate, activeAttachmentSampled = true),
            ),
        )

        for (case in cases) {
            val decision = ValidatingTextureSamplerResourceProvider().materializeTextureSamplerBinding(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(decision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
            assertContains(refused.dumpLines().joinToString("\n"), "resource.materialization:refused")
            assertFalse(refused.dumpLines().joinToString("\n").contains("secret-texture-handle"))
        }
    }

    /** Upload artifact keys are dump facts and must reject handle-like evidence before refusal dumps. */
    @Test
    fun `texture sampler request rejects handle like upload artifact keys`() {
        val imagePlan = decodedImagePlan()
        val ownershipGate = GPUUploadedTextureArtifactOwnershipGate().plan(uploadedTextureRequest(imagePlan))

        assertFailsWith<IllegalArgumentException> {
            textureSamplerRequest(
                imagePlan,
                ownershipGate,
                allocation = GPUTextureAllocationPlan.UploadFromArtifact(
                    artifactKey = "WGPUTextureHandle0xCAFE",
                    descriptor = imagePlan.textureDescriptor,
                ),
                uploadCapabilityAvailable = false,
            )
        }
    }
}

private data class TextureSamplerRefusalCase(
    val expectedCode: String,
    val request: GPUTextureSamplerMaterializationRequest,
)

private fun decodedImagePlan(): GPUDecodedImageShaderRoutePlan =
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
    imagePlan: GPUDecodedImageShaderRoutePlan,
): GPUUploadedTextureArtifactOwnershipRequest =
    GPUUploadedTextureArtifactOwnershipRequest(
        artifact = GPUUploadedTextureArtifactFacts(
            artifactKey = imagePlan.artifact.artifactKey.value,
            artifactType = imagePlan.artifact.artifactType,
            pixelWidth = imagePlan.artifact.pixelPlan.width,
            pixelHeight = imagePlan.artifact.pixelPlan.height,
            pixelFormat = imagePlan.artifact.pixelPlan.format,
            generation = imagePlan.artifact.generation,
            lifetimeClass = imagePlan.artifact.lifetimeClass,
            uploadBudgetClass = imagePlan.artifact.uploadPlan.uploadBudgetClass,
        ),
        textureDescriptor = imagePlan.textureDescriptor,
        viewDescriptor = imagePlan.viewDescriptor,
        samplerDescriptor = imagePlan.samplerDescriptor,
        ownerLabel = "uploaded-image:checker",
        ownerScope = "GPURecorderScope",
        expectedArtifactGeneration = imagePlan.artifact.generation,
        artifactDeviceGeneration = 17,
        expectedDeviceGeneration = 17,
        requiredUsageLabels = setOf("copy_dst", "texture_binding"),
        debugLiveResourceLabel = "secret-texture-handle-0xCAFE",
    )

private fun textureSamplerRequest(
    imagePlan: GPUDecodedImageShaderRoutePlan,
    ownershipGate: GPUUploadedTextureArtifactOwnershipGatePlan,
    textureDescriptor: GPUTextureDescriptor = imagePlan.textureDescriptor,
    allocation: GPUTextureAllocationPlan = ownershipGate.allocation,
    deviceGeneration: Long = 17L,
    actualResourceGeneration: Long = imagePlan.artifact.generation,
    requiredMipLevels: Int = 1,
    uploadCapabilityAvailable: Boolean = true,
    swizzleRequired: Boolean = false,
    unsupportedSamplingReason: String? = null,
    uploadFailedReason: String? = null,
    activeAttachmentSampled: Boolean = false,
): GPUTextureSamplerMaterializationRequest =
    GPUTextureSamplerMaterializationRequest(
        targetId = "root-target",
        packetId = "packet-image-1",
        taskIds = listOf("task-texture-sampler"),
        resourcePlanLabels = listOf("texture-sampler:checker"),
        allocation = allocation,
        ownership = ownershipGate.ownership,
        textureDescriptor = textureDescriptor,
        viewDescriptor = imagePlan.viewDescriptor,
        samplerDescriptor = imagePlan.samplerDescriptor,
        binding = imagePlan.binding,
        bindingLayoutHash = "layout-image-sampler-v1",
        deviceGeneration = deviceGeneration,
        expectedResourceGeneration = imagePlan.artifact.generation,
        actualResourceGeneration = actualResourceGeneration,
        requiredTextureUsageLabels = setOf("copy_dst", "texture_binding"),
        availableTextureUsageLabels = textureDescriptor.usageLabels,
        requiredMipLevels = requiredMipLevels,
        uploadBytes = 16L,
        uploadBudgetBytes = 256L,
        uploadCapabilityAvailable = uploadCapabilityAvailable,
        swizzleRequired = swizzleRequired,
        unsupportedSamplingReason = unsupportedSamplingReason,
        uploadFailedReason = uploadFailedReason,
        activeAttachmentSampled = activeAttachmentSampled,
    )

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 17,
        budgetClass = "unit-test",
    )

private fun skippedTextureSamplerReadback(): GPUReadbackResult.Skipped {
    val request = GPUReadbackRequest(
        requestId = "readback-texture-sampler-skipped",
        sourceLabel = "kgpu-m11-004-texture-sampler-materialization",
        boundsLabel = "0,0 2x2",
        format = "rgba8unorm",
        synchronizationLabel = "after-materialization",
        expectedArtifactLabel = "texture-sampler-readback.png",
        failureReason = "kgpu-m11-004.adapter-readback-not-promoted",
    )
    return GPUReadbackResult.Skipped(
        request = request,
        reasonCode = "unsupported.execution.readback_unavailable",
        diagnostics = listOf(GPUExecutionDiagnostic.readbackUnavailable(request, stage = "readback")),
    )
}
