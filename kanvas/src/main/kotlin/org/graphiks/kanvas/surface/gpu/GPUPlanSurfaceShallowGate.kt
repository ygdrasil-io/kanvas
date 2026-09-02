package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.RenderConfig

/** Cheap W3 admission only: it intentionally has no Scene or backend dependency. */
internal object GPUPlanSurfaceShallowGate {
    const val MAX_W3_COMMANDS: Int = 512

    fun accepts(operations: List<DisplayOp>, config: RenderConfig): Boolean =
        config.gpuColorFormat == GPUColorFormat.RGBA8_UNORM_SRGB &&
            operations.size <= MAX_W3_COMMANDS &&
            operations.all { operation ->
                operation is DisplayOp.DrawRect ||
                    operation is DisplayOp.DrawColor ||
                    operation is DisplayOp.SetTransform ||
                    operation is DisplayOp.SetClip ||
                    operation is DisplayOp.Annotation
            }
}
