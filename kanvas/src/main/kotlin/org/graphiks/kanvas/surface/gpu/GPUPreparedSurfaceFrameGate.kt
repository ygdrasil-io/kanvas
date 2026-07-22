package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.surface.RenderConfig

internal sealed interface GPUPreparedSurfaceEligibility {
    data class Candidate(
        val operations: List<DisplayOp>,
        val config: RenderConfig,
        val color: GPUPreparedSurfaceColorMapping.Ready,
    ) : GPUPreparedSurfaceEligibility

    data class Legacy(
        val code: String,
        val operationIndex: Int? = null,
        val family: LegacyDisplayOpFamily? = null,
    ) : GPUPreparedSurfaceEligibility
}

/** Pure whole-frame admission gate for the prepared Surface route. */
internal object GPUPreparedSurfaceFrameGate {
    fun classify(
        operations: List<DisplayOp>,
        config: RenderConfig,
    ): GPUPreparedSurfaceEligibility {
        val color = when (val mapping = config.mapPreparedGpuColorConfig()) {
            is GPUPreparedSurfaceColorMapping.Ready -> mapping
            is GPUPreparedSurfaceColorMapping.Refused -> return GPUPreparedSurfaceEligibility.Legacy(
                code = mapping.code,
            )
        }
        var hasVisual = false
        operations.forEachIndexed { operationIndex, operation ->
            when (operation) {
                is DisplayOp.DrawColor,
                is DisplayOp.Clear,
                is DisplayOp.DrawPoint,
                is DisplayOp.DrawPoints,
                is DisplayOp.DrawRect,
                is DisplayOp.DrawRRect,
                is DisplayOp.DrawDRRect,
                is DisplayOp.DrawPath,
                -> hasVisual = true

                is DisplayOp.SetTransform,
                is DisplayOp.SetClip,
                is DisplayOp.Annotation,
                -> Unit

                is DisplayOp.FlushAndSnapshot -> return GPUPreparedSurfaceEligibility.Legacy(
                    code = "legacy.surface.prepared.flush-snapshot",
                    operationIndex = operationIndex,
                )

                is DisplayOp.DrawImage,
                is DisplayOp.DrawImageNine,
                is DisplayOp.DrawImageLattice,
                is DisplayOp.DrawAtlas,
                is DisplayOp.DrawText,
                is DisplayOp.DrawVertices,
                is DisplayOp.DrawMesh,
                is DisplayOp.DrawPicture,
                is DisplayOp.BeginLayer,
                DisplayOp.EndLayer,
                -> {
                    val family = requireNotNull(GPULegacyImmediatePathAdapter.familyOrNull(operation))
                    return GPUPreparedSurfaceEligibility.Legacy(
                        code = family.preparedSurfaceCode(),
                        operationIndex = operationIndex,
                        family = family,
                    )
                }
            }
        }
        if (!hasVisual) {
            return GPUPreparedSurfaceEligibility.Legacy(
                code = "legacy.surface.prepared.empty-frame",
            )
        }
        return GPUPreparedSurfaceEligibility.Candidate(
            operations = Collections.unmodifiableList(ArrayList(operations)),
            config = config,
            color = color,
        )
    }
}

private fun LegacyDisplayOpFamily.preparedSurfaceCode(): String = when (this) {
    LegacyDisplayOpFamily.Images -> "legacy.surface.prepared.family.images"
    LegacyDisplayOpFamily.Text -> "legacy.surface.prepared.family.text"
    LegacyDisplayOpFamily.Vertices -> "legacy.surface.prepared.family.vertices"
    LegacyDisplayOpFamily.Composites -> "legacy.surface.prepared.family.composites"
}
