package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap composition admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceCandidateGate {
    fun accepts(operations: List<DisplayOp>, config: RenderConfig): Boolean =
        config.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB &&
            operations.all { operation ->
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawRRect ||
                    (operation is DisplayOp.DrawPoint && operation.acceptsW5aPointMaterial()) ||
                    (operation is DisplayOp.DrawPoints && operation.acceptsW5aPointMaterial()) ||
                    (operation is DisplayOp.DrawPath &&
                        operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            }

    private fun DisplayOp.DrawPoint.acceptsW5aPointMaterial(): Boolean =
        paint.blendMode == BlendMode.SRC_OVER && paint.strokeCap != StrokeCap.ROUND

    private fun DisplayOp.DrawPoints.acceptsW5aPointMaterial(): Boolean =
        paint.blendMode == BlendMode.SRC_OVER && paint.strokeCap != StrokeCap.ROUND
}
