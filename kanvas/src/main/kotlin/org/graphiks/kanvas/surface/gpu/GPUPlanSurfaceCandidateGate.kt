package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap composition admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceCandidateGate {
    fun accepts(operations: List<DisplayOp>, config: RenderConfig): Boolean =
        config.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB &&
            operations.all { operation ->
                val shader = when (operation) {
                    is DisplayOp.DrawRect -> operation.paint.shader
                    is DisplayOp.DrawRRect -> operation.paint.shader
                    is DisplayOp.DrawPath -> operation.paint.shader
                    else -> null
                }
                if (shader != null && !shader.isW5dGradientCandidateV2(
                    allowLocalMatrix = operation is DisplayOp.DrawPath ||
                        operation is DisplayOp.DrawRect && !operation.paint.isStroke() ||
                        operation is DisplayOp.DrawRRect && !operation.paint.isStroke() && operation.paint.antiAlias,
                    allowNonGradient = true)) return@all false
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawRRect ||
                    (operation is DisplayOp.DrawPath &&
                        operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            }
}
