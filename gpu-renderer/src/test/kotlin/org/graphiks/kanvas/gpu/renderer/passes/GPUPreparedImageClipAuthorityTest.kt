package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

class GPUPreparedImageClipAuthorityTest {
    private val targetBounds = GPUPixelBounds(0, 0, 4, 4)
    private val scissorBounds = GPUPixelBounds(1, 1, 3, 3)

    @Test
    fun `exact prepared image clip packets produce accepted passive handoff evidence`() {
        assertIs<GPUPreparedImageClipAuthorityValidation.Accepted>(
            packet().validatePreparedImageClipAuthority(targetBounds, scissorBounds),
        )
        assertIs<GPUPreparedImageClipAuthorityValidation.Accepted>(
            packet(
                scissorBoundsHash = null,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            ).validatePreparedImageClipAuthority(targetBounds, targetBounds),
        )
    }

    @Test
    fun `forged prepared image clip packets retain the first exact mismatch kind`() {
        assertIs<GPUPreparedImageClipAuthorityValidation.ScissorAuthorityMismatch>(
            packet(
                scissorBoundsHash = "forged.scissor.authority",
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            ).validatePreparedImageClipAuthority(targetBounds, scissorBounds),
        )
        assertIs<GPUPreparedImageClipAuthorityValidation.CoverageMismatch>(
            packet(
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            ).validatePreparedImageClipAuthority(targetBounds, scissorBounds),
        )
        assertIs<GPUPreparedImageClipAuthorityValidation.ExecutionMismatch>(
            packet(
                clipExecutionPlan = GPUClipExecutionPlan.NoClip,
            ).validatePreparedImageClipAuthority(targetBounds, scissorBounds),
        )
    }

    private fun packet(
        scissorBoundsHash: String? = "prepared-image-scissor.1.1.3.3",
        clipCoveragePlan: GPUClipCoveragePlan = GPUClipCoveragePlan.Scissor(
            GPUBounds(1f, 1f, 3f, 3f),
        ),
        clipExecutionPlan: GPUClipExecutionPlan =
            GPUClipExecutionPlan.ScissorOnly(scissorBounds),
    ): GPUDrawPacket = GPUDrawPacket(
        packetId = GPUDrawPacketID("packet.image"),
        commandIdValue = 7,
        analysisRecordId = "analysis.image",
        passId = "pass.image",
        layerId = "layer.root",
        bindingListId = "bindings.image",
        insertionReasonCode = "direct",
        sortKey = 0L,
        sortKeyPreimage = "image",
        renderStepId = GPURenderStepID("image.draw.texture_upload"),
        renderStepVersion = 1,
        role = GPUDrawPacketRole.Shading,
        renderPipelineKey = GPURenderPipelineKey("pipeline.image"),
        bindingLayoutHash = "bindings.image.v1",
        vertexSourceLabel = "image-quad",
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = "target.rgba8",
        originalPaintOrder = 0,
        resourceGeneration = 0L,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
    )
}
