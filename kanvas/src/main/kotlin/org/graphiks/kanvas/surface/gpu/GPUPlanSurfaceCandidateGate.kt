package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap composition admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceCandidateGate {
    fun ownsW5eImages(operations: List<DisplayOp>): Boolean =
        operations.any { it is DisplayOp.DrawImage } && operations.all { operation ->
            when (operation) {
                is DisplayOp.DrawImage -> operation.image.pixels != null &&
                    operation.image.colorType == org.graphiks.kanvas.image.ColorType.RGBA_8888 &&
                    operation.image.alphaType == org.graphiks.kanvas.image.AlphaType.PREMUL &&
                    operation.image.colorSpace == org.graphiks.kanvas.color.ColorSpace.SRGB &&
                    operation.sampling == org.graphiks.kanvas.paint.SamplingOptions.NEAREST &&
                    operation.paint.let { it == null || it.shader == null && it.blender == null &&
                        it.colorFilter == null && it.maskFilter == null && it.imageFilter == null &&
                        it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL } &&
                    operation.clip !is org.graphiks.kanvas.canvas.ClipStack.Complex &&
                    (operation.clip as? org.graphiks.kanvas.canvas.ClipStack.DeviceRect)?.antiAlias != true
                is DisplayOp.SetTransform, is DisplayOp.SetClip, is DisplayOp.Annotation -> true
                else -> false
            }
        }
    fun accepts(operations: List<DisplayOp>, config: RenderConfig): Boolean =
        config.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB &&
            (ownsW5eImages(operations) || operations.all { operation ->
                if ((operation is DisplayOp.DrawRect || operation is DisplayOp.DrawRRect ||
                        operation is DisplayOp.DrawPath) &&
                    !operation.isW5dGradientCandidateV2(allowNonGradient = true)) return@all false
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawRRect ||
                    (operation is DisplayOp.DrawPath &&
                        operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            })
}
