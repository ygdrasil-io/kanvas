package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Handle-free prepared packets bind their exact target generation during preflight. */
internal const val PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION: Long = 0L

/** One exact premultiplied SrcOver authority shared by recording and native materialization. */
internal fun canonicalSolidRectSrcOverBlendPlan(): GPUBlendPlan.FixedFunctionBlend =
    GPUBlendPlan.FixedFunctionBlend(
        mode = GPUBlendMode.SRC_OVER,
        state = GPUFixedFunctionBlendState(
            stateId = "one_isa",
            color = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            alpha = GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
    )

internal fun GPUBlendPlan?.isCanonicalSolidRectSrcOver(): Boolean =
    this == canonicalSolidRectSrcOverBlendPlan()
