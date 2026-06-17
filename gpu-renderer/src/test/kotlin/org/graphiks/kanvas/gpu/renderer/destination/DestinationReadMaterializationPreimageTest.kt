package org.graphiks.kanvas.gpu.renderer.destination

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

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
                requirement = GPUDestinationReadRequirement.ExistingIntermediate,
                strategy = GPUDestinationReadStrategy.BindIntermediate,
                action = GPUDestinationReadAction.UseExistingIntermediate,
                intermediateLabel = "intermediate:layer-card",
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
    requirement: GPUDestinationReadRequirement = GPUDestinationReadRequirement.TargetCopy,
    strategy: GPUDestinationReadStrategy = GPUDestinationReadStrategy.CopyTarget,
    action: GPUDestinationReadAction = GPUDestinationReadAction.SplitPassAndCopyTarget,
    activeAttachmentSampled: Boolean = false,
    intermediateLabel: String = "target:main",
): GPUDestinationReadStrategyRequest = GPUDestinationReadStrategyRequest(
    commandId = "blend:screen",
    requirement = requirement,
    strategy = strategy,
    action = action,
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
    intermediateLabel = intermediateLabel,
)
