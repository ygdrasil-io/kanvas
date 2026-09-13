package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap composition admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceCandidateGate {
    fun ownsW5eImages(operations: List<DisplayOp>): Boolean =
        operations.any { it is DisplayOp.DrawImage || it.isW5eShaderOperation() } && operations.all { operation ->
            when (operation) {
                is DisplayOp.DrawImage -> operation.image.pixels != null &&
                    operation.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888,
                        org.graphiks.kanvas.image.ColorType.BGRA_8888, org.graphiks.kanvas.image.ColorType.SRGBA_8888,
                        org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
                    operation.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE,
                        org.graphiks.kanvas.image.AlphaType.PREMUL, org.graphiks.kanvas.image.AlphaType.UNPREMUL) &&
                    (operation.sampling in setOf(org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
                        org.graphiks.kanvas.paint.SamplingOptions.LINEAR) || operation.sampling is org.graphiks.kanvas.paint.SamplingOptions.Cubic) &&
                    operation.paint.let { it == null || it.blender == null &&
                        it.colorFilter == null && it.maskFilter == null && it.imageFilter == null &&
                        it.pathEffect == null && it.style == org.graphiks.kanvas.paint.PaintStyle.FILL }
                is DisplayOp.DrawRect -> operation.isW5eShaderOperation() || operation.paint.shader?.imageLeafW5e() == null
                is DisplayOp.DrawPath -> operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName &&
                    (operation.isW5eShaderOperation() || operation.paint.shader?.imageLeafW5e() == null)
                is DisplayOp.DrawRRect -> operation.paint.shader?.imageLeafW5e() == null
                is DisplayOp.SetTransform, is DisplayOp.SetClip, is DisplayOp.Annotation -> true
                else -> false
            }
        }

    private fun DisplayOp.isW5eShaderOperation(): Boolean {
        val paint = when (this) {
            is DisplayOp.DrawRect -> paint
            is DisplayOp.DrawPath -> if (sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) paint else return false
            else -> return false
        }
        if (paint.style != org.graphiks.kanvas.paint.PaintStyle.FILL || paint.blender != null || paint.colorFilter != null ||
            paint.maskFilter != null || paint.imageFilter != null || paint.pathEffect != null) return false
        val leaf = paint.shader?.imageLeafW5e() ?: return false
        return leaf.image.pixels != null && (leaf.sampling in setOf(org.graphiks.kanvas.paint.SamplingOptions.NEAREST,
            org.graphiks.kanvas.paint.SamplingOptions.LINEAR) || leaf.sampling is org.graphiks.kanvas.paint.SamplingOptions.Cubic) &&
            leaf.image.colorType in setOf(org.graphiks.kanvas.image.ColorType.RGBA_8888, org.graphiks.kanvas.image.ColorType.BGRA_8888,
                org.graphiks.kanvas.image.ColorType.SRGBA_8888, org.graphiks.kanvas.image.ColorType.ALPHA_8) &&
            leaf.image.alphaType in setOf(org.graphiks.kanvas.image.AlphaType.OPAQUE, org.graphiks.kanvas.image.AlphaType.PREMUL,
                org.graphiks.kanvas.image.AlphaType.UNPREMUL)
    }

    private fun org.graphiks.kanvas.paint.Shader.imageLeafW5e(): org.graphiks.kanvas.paint.Shader.Image? {
        var source = this
        repeat(65) {
            source = when (val node = source) {
                is org.graphiks.kanvas.paint.Shader.WithLocalMatrix -> node.shader
                is org.graphiks.kanvas.paint.Shader.Opacity -> node.shader
                else -> return node as? org.graphiks.kanvas.paint.Shader.Image
            }
        }
        return null
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
