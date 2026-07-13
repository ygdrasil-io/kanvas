package org.graphiks.kanvas.gpu.renderer.destination

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor

class DestinationReadMaterializationPreimageTest {
    @Test
    fun `target copy strategy derives destination copy materialization preimage`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(destinationPreimageRequest())

        val preimage = gate.toDestinationReadMaterializationPreimage()

        assertEquals(true, preimage.accepted)
        assertFalse(preimage.nonClaims.adapterBacked)
        assertFalse(preimage.nonClaims.liveHandles)
        assertFalse(preimage.nonClaims.productRoute)
        assertEquals(listOf(GPUMaterializedResourceRole.DestinationCopyTexture), preimage.resources.map { it.role })
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=destination-read:blend-screen source=gpu-renderer.destination-read.strategy resources=dst-copy:blend-screen bindings=dst-read:blend-screen adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=dst-copy:blend-screen role=destination-copy-texture generation=42 lifetime=pass-local descriptor=${gate.copyDescriptorHash} usage=copy_dst,texture_binding facts=action=SplitPassAndCopyTarget;source=target:main",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `validated intermediate strategy derives existing intermediate materialization preimage`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(
            destinationPreimageRequest(
                requirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
                eligibleIntermediate = destinationEligibleIntermediate(),
            ),
        )

        val preimage = gate.toDestinationReadMaterializationPreimage()

        assertEquals(listOf(GPUMaterializedResourceRole.IntermediateTexture), preimage.resources.map { it.role })
        assertEquals(
            listOf(
                "resource-preimage:accepted plan=destination-read:blend-screen source=gpu-renderer.destination-read.strategy resources=intermediate:layer-card bindings=dst-read:blend-screen adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:resource label=intermediate:layer-card role=intermediate-texture generation=42 lifetime=layer-local descriptor=${gate.copyDescriptorHash} usage=texture_binding facts=action=UseExistingIntermediate;source=intermediate:layer-card",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }

    @Test
    fun `refused destination strategy derives refused materialization preimage`() {
        val gate = GPUDestinationReadStrategyPlanner().plan(
            destinationPreimageRequest(activeAttachmentSampled = true),
        )

        val preimage = gate.toDestinationReadMaterializationPreimage()

        assertFalse(preimage.accepted)
        assertEquals("unsupported.destination_read.active_attachment_sampled", preimage.refusalCode)
        assertEquals(
            listOf(
                "resource-preimage:refused plan=destination-read:blend-screen source=gpu-renderer.destination-read.strategy reason=unsupported.destination_read.active_attachment_sampled resources=none bindings=none adapterBacked=false liveHandles=false productRoute=false",
                "resource-preimage:nonclaim adapterBacked=false liveHandles=false productRoute=false providerCalled=false submitCalled=false",
            ),
            preimage.dumpLines(),
        )
    }
}

private fun destinationPreimageRequest(
    requirement: GPUBlendDestinationReadRequirement = GPUBlendDestinationReadRequirement.DestinationTextureRequired,
    activeAttachmentSampled: Boolean = false,
    eligibleIntermediate: GPUDestinationReadEligibleIntermediate? = null,
): GPUDestinationReadStrategyRequest = GPUDestinationReadStrategyRequest(
    commandId = "blend:screen",
    requirement = requirement,
    bounds = GPUDestinationReadBounds(
        boundsLabel = "shape-local",
        conservative = true,
        pixelAligned = true,
        requestedBoundsLabel = "shape-local",
        unclippedBoundsLabel = "0,0,80,40",
        clippedBoundsLabel = "4,8,64,32",
        copyBoundsLabel = "4,8,64,32",
        originX = 4,
        originY = 8,
        width = 64,
        height = 32,
        targetWidth = 128,
        targetHeight = 96,
    ),
    sourceTargetLabel = "target:main",
    sourceUsageLabels = setOf("render_attachment", "copy_src"),
    copyUsageLabels = setOf("copy_dst", "texture_binding"),
    targetFormatClass = "rgba8unorm",
    targetGeneration = 42,
    activeAttachmentSampled = activeAttachmentSampled,
    eligibleIntermediate = eligibleIntermediate,
)

private fun destinationEligibleIntermediate(): GPUDestinationReadEligibleIntermediate =
    GPUDestinationReadEligibleIntermediate(
        descriptor = GPUIntermediateTextureDescriptor(
            label = "intermediate:layer-card",
            purpose = GPUIntermediatePurpose.ExistingIntermediate,
            descriptorHash = "descriptor:layer-card",
            sourceTargetLabel = "target:main",
            boundsLabel = "4,8,64,32",
            width = 64,
            height = 32,
            formatClass = "rgba8unorm",
            usageLabels = listOf("texture_binding"),
            sampleCount = 1,
            generation = 42,
            lifetimeClass = "layer-local",
            ownerScope = "layer:card",
            byteEstimate = 8192,
        ),
    )
