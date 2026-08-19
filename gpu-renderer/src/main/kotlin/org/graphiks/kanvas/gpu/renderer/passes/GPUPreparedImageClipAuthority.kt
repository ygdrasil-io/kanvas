package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.preparedImageScissorAuthority

/** Passive prepared-image clip handoff consumed by execution without clip-domain imports. */
internal sealed interface GPUPreparedImageClipAuthorityValidation {
    data object Accepted : GPUPreparedImageClipAuthorityValidation

    data object ScissorAuthorityMismatch : GPUPreparedImageClipAuthorityValidation

    data object CoverageMismatch : GPUPreparedImageClipAuthorityValidation

    data object ExecutionMismatch : GPUPreparedImageClipAuthorityValidation
}

/** Validates exact prepared-image clip facts while concrete clip types stay outside execution. */
internal fun GPUDrawPacket.validatePreparedImageClipAuthority(
    targetBounds: GPUPixelBounds,
    scissorBounds: GPUPixelBounds,
): GPUPreparedImageClipAuthorityValidation {
    val hasScissor = scissorBounds != targetBounds
    val expectedScissorAuthority = if (hasScissor) {
        preparedImageScissorAuthority(scissorBounds)
    } else {
        null
    }
    if (scissorBoundsHash != expectedScissorAuthority) {
        return GPUPreparedImageClipAuthorityValidation.ScissorAuthorityMismatch
    }

    val expectedCoverage = if (hasScissor) {
        GPUClipCoveragePlan.Scissor(
            GPUBounds(
                scissorBounds.left.toFloat(),
                scissorBounds.top.toFloat(),
                scissorBounds.right.toFloat(),
                scissorBounds.bottom.toFloat(),
            ),
        )
    } else {
        GPUClipCoveragePlan.NoClip
    }
    if (clipCoveragePlan != expectedCoverage) {
        return GPUPreparedImageClipAuthorityValidation.CoverageMismatch
    }

    val expectedExecution = if (hasScissor) {
        GPUClipExecutionPlan.ScissorOnly(scissorBounds)
    } else {
        GPUClipExecutionPlan.NoClip
    }
    return if (clipExecutionPlan == expectedExecution) {
        GPUPreparedImageClipAuthorityValidation.Accepted
    } else {
        GPUPreparedImageClipAuthorityValidation.ExecutionMismatch
    }
}
