package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

/** Per-logical-draw ownership for source-then-clip composition. */
internal data class GPUClipRouteContext(
    val sceneLabel: String,
    val sourceLabel: String,
    val sourceLabelForDiagnostics: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val colorFormat: String,
    val config: RenderConfig,
    val frameCache: GPUClipCoverageFrameCache,
    val destinationReadComposer: GPUClipDestinationReadComposer = GPUClipDestinationReadRefusalComposer,
    val trace: GPUClipRouteTrace? = null,
)

/** Test-visible accounting that prevents a ComplexStack draw from reaching a direct route. */
internal class GPUClipRouteTrace {
    var logicalDrawCount: Int = 0
        private set
    var sourceThenCompositeCount: Int = 0
        private set
    var directComplexClipDispatches: Int = 0
        private set

    internal fun direct(plan: GPUClipCoveragePlan) {
        logicalDrawCount++
        if (plan is GPUClipCoveragePlan.Mask) directComplexClipDispatches++
    }

    internal fun sourceThenComposite() {
        logicalDrawCount++
        sourceThenCompositeCount++
    }
}

/**
 * Task 8 owns actual destination snapshots and blend shaders. The source callback is deliberately
 * passed to the composer so the temporary refusal does not emit an uncomposited source texture.
 */
internal fun interface GPUClipDestinationReadComposer {
    fun compose(
        context: GPUClipRouteContext,
        clipMaskLabel: String?,
        blend: GPUBlendFacts,
        diagnostics: Diagnostics,
        encodeSource: () -> Boolean,
    ): Boolean
}

/** Stable temporary policy until the destination-read composer is implemented. */
internal object GPUClipDestinationReadRefusalComposer : GPUClipDestinationReadComposer {
    override fun compose(
        context: GPUClipRouteContext,
        clipMaskLabel: String?,
        blend: GPUBlendFacts,
        diagnostics: Diagnostics,
        encodeSource: () -> Boolean,
    ): Boolean {
        diagnostics.fatal(
            code = "refuse:clip-destination-read:${context.sourceLabelForDiagnostics}",
            operation = context.sourceLabelForDiagnostics,
            reason = "unsupported.clip.destination_read.pending_task8:${blend.modeLabel}",
            facts = listOf(
                DiagnosticFact("clip.strategy", if (clipMaskLabel == null) "direct" else "alpha-mask"),
                DiagnosticFact("clip.destination-read", "task8-pending"),
            ),
        )
        return false
    }
}

/**
 * Applies a normalized clip plan exactly once to a logical draw. Source callbacks own their source
 * render pass because image-filter output has a multi-pass source route; this wrapper owns the
 * only scene composite and the mask lease lifetime.
 */
internal fun GPUBackendOffscreenTarget.renderWithClip(
    context: GPUClipRouteContext,
    clipPlan: GPUClipCoveragePlan,
    blend: GPUBlendFacts,
    diagnostics: Diagnostics,
    encodeDirect: () -> Boolean,
    encodeSource: () -> Boolean,
): Boolean = when (clipPlan) {
    GPUClipCoveragePlan.NoClip,
    is GPUClipCoveragePlan.Scissor,
    -> {
        if (blend.requiresDestinationRead) {
            context.destinationReadComposer.compose(context, null, blend, diagnostics, encodeSource)
        } else {
            context.trace?.direct(clipPlan)
            if (clipPlan is GPUClipCoveragePlan.Scissor) {
                diagnostics.degrade(
                    code = "route:clip:${context.sourceLabelForDiagnostics}",
                    operation = context.sourceLabelForDiagnostics,
                    reason = "clip-direct",
                    facts = listOf(DiagnosticFact("clip.strategy", "scissor")),
                )
            }
            encodeDirect()
        }
    }
    is GPUClipCoveragePlan.Mask -> {
        if (blend.requiresDestinationRead) {
            try {
                acquireClipMask(clipPlan, context.frameCache, diagnostics, context.config).use { lease ->
                    context.destinationReadComposer.compose(
                        context,
                        lease.mask.sampleLabel,
                        blend,
                        diagnostics,
                        encodeSource,
                    )
                }
            } catch (_: GPUClipCoverageFrameBudgetExceededException) {
                diagnostics.fatal(
                    code = "refuse:clip-mask:${context.sourceLabelForDiagnostics}",
                    operation = context.sourceLabelForDiagnostics,
                    reason = "unsupported.clip.frame_budget",
                )
                false
            }
        } else {
            try {
                acquireClipMask(clipPlan, context.frameCache, diagnostics, context.config).use { lease ->
                    if (!encodeSource()) return@use false
                    encodeOffscreenTexture(context.sceneLabel, null) {
                        drawTwoTexturePass(
                            wgsl = CLIP_MASK_COMPOSITE_WGSL,
                            colorFormat = context.colorFormat,
                            firstTextureLabel = context.sourceLabel,
                            secondTextureLabel = lease.mask.sampleLabel,
                            draws = listOf(clipMaskCompositeUniformDraw(context.targetWidth, context.targetHeight)),
                            blendMode = blend.blendMode ?: GPUBlendMode.SRC_OVER,
                        )
                    }
                    context.trace?.sourceThenComposite()
                    diagnostics.degrade(
                        code = "route:clip:${context.sourceLabelForDiagnostics}",
                        operation = context.sourceLabelForDiagnostics,
                        reason = "clip-source-then-composite",
                        facts = listOf(DiagnosticFact("clip.strategy", "alpha-mask")),
                    )
                    true
                }
            } catch (_: GPUClipCoverageFrameBudgetExceededException) {
                diagnostics.fatal(
                    code = "refuse:clip-mask:${context.sourceLabelForDiagnostics}",
                    operation = context.sourceLabelForDiagnostics,
                    reason = "unsupported.clip.frame_budget",
                )
                false
            }
        }
    }
    is GPUClipCoveragePlan.Refused -> false
}

/** Source passes ignore the captured clip and always use fixed-function SrcOver into a full target. */
private fun clipSourceFacts(targetWidth: Int, targetHeight: Int): GPUClipFacts =
    GPUClipFacts.wideOpen(GPUBounds(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()))

internal fun NormalizedDrawCommand.FillRect.copyForClipSource(
    targetWidth: Int,
    targetHeight: Int,
): NormalizedDrawCommand.FillRect = copy(
    clip = clipSourceFacts(targetWidth, targetHeight),
    blend = GPUBlendFacts.srcOver(),
)

internal fun NormalizedDrawCommand.FillRRect.copyForClipSource(
    targetWidth: Int,
    targetHeight: Int,
): NormalizedDrawCommand.FillRRect = copy(
    clip = clipSourceFacts(targetWidth, targetHeight),
    blend = GPUBlendFacts.srcOver(),
)

internal fun NormalizedDrawCommand.FillPath.copyForClipSource(
    targetWidth: Int,
    targetHeight: Int,
): NormalizedDrawCommand.FillPath = copy(
    clip = clipSourceFacts(targetWidth, targetHeight),
    blend = GPUBlendFacts.srcOver(),
)

internal fun NormalizedDrawCommand.DrawImageRect.copyForClipSource(
    targetWidth: Int,
    targetHeight: Int,
): NormalizedDrawCommand.DrawImageRect = copy(
    clip = clipSourceFacts(targetWidth, targetHeight),
    blend = GPUBlendFacts.srcOver(),
    imageFilterPlan = imageFilterPlan.withFullTargetSourceBounds(dst, targetWidth, targetHeight),
)

private fun GPUImageFilterPlan.withFullTargetSourceBounds(
    destination: GPURect,
    targetWidth: Int,
    targetHeight: Int,
): GPUImageFilterPlan = when (this) {
    is GPUImageFilterPlan.Blur -> copy(
        outputBounds = GPURect(
            left = (destination.left - haloX).coerceAtLeast(0f),
            top = (destination.top - haloY).coerceAtLeast(0f),
            right = (destination.right + haloX).coerceAtMost(targetWidth.toFloat()),
            bottom = (destination.bottom + haloY).coerceAtMost(targetHeight.toFloat()),
        ),
    )
    else -> this
}

internal fun NormalizedDrawCommand.copyForClipSource(
    targetWidth: Int,
    targetHeight: Int,
): NormalizedDrawCommand = when (this) {
    is NormalizedDrawCommand.FillRect -> copyForClipSource(targetWidth, targetHeight)
    is NormalizedDrawCommand.FillRRect -> copyForClipSource(targetWidth, targetHeight)
    is NormalizedDrawCommand.FillPath -> copyForClipSource(targetWidth, targetHeight)
    is NormalizedDrawCommand.DrawImageRect -> copyForClipSource(targetWidth, targetHeight)
    else -> this
}
