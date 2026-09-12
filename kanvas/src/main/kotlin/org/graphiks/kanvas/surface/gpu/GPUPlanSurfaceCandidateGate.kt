package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.ColorSpaceInterpolation

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
                if (!acceptsGradient(shader)) return@all false
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawRRect ||
                    (operation is DisplayOp.DrawPath &&
                        operation.sourceOperation == DrawPathSourceOperation.DRAW_PATH.stableName) ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            }

    private fun acceptsGradient(shader: Shader?): Boolean {
        var source = shader
        var depthI32 = 0
        while (source is Shader.Opacity) {
            // Admission does not own diagnostics; capture reports the public graph-depth refusal.
            if (++depthI32 > 64) return true
            source = source.shader
        }
        return when (source) {
            is Shader.LinearGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.RadialGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.SweepGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.ConicalGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
            is Shader.WithLocalMatrix, is Shader.CoordClamp -> false
            else -> true
        }
    }

}
