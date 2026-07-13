package org.graphiks.kanvas.surface.gpu

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUImageFilterPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.surface.DiagnosticFact
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig

/** GPU-resident source products: unmodulated premultiplied color [S] and geometric coverage [G]. */
internal data class GPUClipSourceSurface(
    val colorLabel: String,
    val geometryCoverageLabel: String,
)

/** Per-logical-draw ownership for source-then-clip composition. */
internal data class GPUClipRouteContext(
    val sceneLabel: String,
    val sourceSurface: GPUClipSourceSurface,
    val sourceLabelForDiagnostics: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val colorFormat: String,
    val config: RenderConfig,
    val frameCache: GPUClipCoverageFrameCache,
    val destinationReadComposer: GPUClipDestinationReadComposer = GPUClipDestinationReadRefusalComposer,
    val trace: GPUClipRouteTrace? = null,
    /** Forces a source texture even for fixed-function no-clip/scissor composites. */
    val forceSourceComposition: Boolean = false,
    /** Fractional geometry coverage requires the S/G final compositor even for fixed-function modes. */
    val coverageCompositionRequired: Boolean = false,
    /** Bounds that contain the source's non-transparent content for fixed-function composition. */
    val sourceCompositeBounds: () -> GPUBounds? = { null },
) {
    val sourceLabel: String get() = sourceSurface.colorLabel
}

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
        clipScissor: GPUBounds?,
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
        clipScissor: GPUBounds?,
        blend: GPUBlendFacts,
        diagnostics: Diagnostics,
        encodeSource: () -> Boolean,
    ): Boolean {
        diagnostics.fatal(
            code = "refuse:clip-destination-read:${context.sourceLabelForDiagnostics}",
            operation = context.sourceLabelForDiagnostics,
            reason = "unsupported.clip.destination_read.pending_task8:${blend.mode.gpuLabel}",
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
): Boolean {
    clipPlan.preAcquireRefusalOrNull(blend)?.let { refusal ->
        diagnostics.fatal(
            code = "${refusal.diagnosticCode}:${context.sourceLabelForDiagnostics}",
            operation = context.sourceLabelForDiagnostics,
            reason = refusal.reason,
            facts = refusal.facts,
        )
        return false
    }
    return when (clipPlan) {
    GPUClipCoveragePlan.NoClip,
    is GPUClipCoveragePlan.Scissor,
    -> {
        if (blend.needsDestinationTexture() || context.coverageCompositionRequired) {
            context.destinationReadComposer.compose(
                context,
                null,
                (clipPlan as? GPUClipCoveragePlan.Scissor)?.bounds,
                blend,
                diagnostics,
                encodeSource,
            )
        } else if (context.forceSourceComposition) {
            if (!encodeSource()) return false
            if (!compositeFixedSource(context, clipPlan, blend)) return false
            context.trace?.sourceThenComposite()
            diagnostics.degrade(
                code = "route:clip:${context.sourceLabelForDiagnostics}",
                operation = context.sourceLabelForDiagnostics,
                reason = "clip-source-then-composite",
                facts = listOf(
                    DiagnosticFact(
                        "clip.strategy",
                        if (clipPlan is GPUClipCoveragePlan.Scissor) "scissor" else "source-target",
                    ),
                ),
            )
            true
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
        try {
            acquireClipMask(clipPlan, context.frameCache, diagnostics, context.config).use { lease ->
                val rendered = context.destinationReadComposer.compose(
                    context,
                    lease.mask.sampleLabel,
                    null,
                    blend,
                    diagnostics,
                    encodeSource,
                )
                if (!rendered) return@use false
                context.trace?.sourceThenComposite()
                diagnostics.degrade(
                    code = "route:clip:${context.sourceLabelForDiagnostics}",
                    operation = context.sourceLabelForDiagnostics,
                    reason = "clip-source-snapshot-formula",
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
    is GPUClipCoveragePlan.Refused -> false
    }
}

/** Applies a source texture at the final composition boundary, including a device-rect scissor. */
private fun GPUBackendOffscreenTarget.compositeFixedSource(
    context: GPUClipRouteContext,
    clipPlan: GPUClipCoveragePlan,
    blend: GPUBlendFacts,
): Boolean {
    val draw = sourceCompositeUniformDraw(context, clipPlan, context.sourceCompositeBounds()) ?: return false
    val fixedState = (blend.canonicalBlendPlan() as? GPUBlendPlan.FixedFunctionBlend)?.state ?: return false
    encodeOffscreenTexture(context.sceneLabel, null) {
        drawCompositePass(
            wgsl = COPY_WGSL,
            colorFormat = context.colorFormat,
            textureLabel = context.sourceLabel,
            draws = listOf(draw),
            blendMode = fixedState,
        )
    }
    return true
}

private fun sourceCompositeUniformDraw(
    context: GPUClipRouteContext,
    clipPlan: GPUClipCoveragePlan,
    sourceBounds: GPUBounds?,
): GPUBackendRawUniformDraw? {
    val scissor = (clipPlan as? GPUClipCoveragePlan.Scissor)?.bounds
    val left = floor(maxOf(0f, scissor?.left ?: 0f, sourceBounds?.left ?: 0f))
        .toInt().coerceIn(0, context.targetWidth)
    val top = floor(maxOf(0f, scissor?.top ?: 0f, sourceBounds?.top ?: 0f))
        .toInt().coerceIn(0, context.targetHeight)
    val right = ceil(minOf(
        context.targetWidth.toFloat(),
        scissor?.right ?: context.targetWidth.toFloat(),
        sourceBounds?.right ?: context.targetWidth.toFloat(),
    )).toInt().coerceIn(left, context.targetWidth)
    val bottom = ceil(minOf(
        context.targetHeight.toFloat(),
        scissor?.bottom ?: context.targetHeight.toFloat(),
        sourceBounds?.bottom ?: context.targetHeight.toFloat(),
    )).toInt().coerceIn(top, context.targetHeight)
    if (right == left || bottom == top) return null
    return GPUBackendRawUniformDraw(
        uniformBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0f); putFloat(0f); putFloat(0f); putFloat(0f)
        }.array(),
        scissorX = left,
        scissorY = top,
        scissorWidth = right - left,
        scissorHeight = bottom - top,
    )
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

/** A scissor source still needs fixed-function SrcOver, but it must retain its original clip. */
internal fun NormalizedDrawCommand.copyForDestinationReadSource(): NormalizedDrawCommand = when (this) {
    is NormalizedDrawCommand.FillRect -> copy(blend = GPUBlendFacts.srcOver())
    is NormalizedDrawCommand.FillRRect -> copy(blend = GPUBlendFacts.srcOver())
    is NormalizedDrawCommand.FillPath -> copy(blend = GPUBlendFacts.srcOver())
    is NormalizedDrawCommand.DrawImageRect -> copy(blend = GPUBlendFacts.srcOver())
    else -> this
}
