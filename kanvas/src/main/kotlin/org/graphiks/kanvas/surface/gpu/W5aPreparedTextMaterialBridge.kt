package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent

/**
 * Captures only W5a text candidates before prepared glyph lowering.
 *
 * Text keeps its established glyph, atlas, and coverage preparation.  A sealed W5a material
 * pair is issued only after the lowerer has proved that the whole prepared run is A8 coverage.
 * Thus color or mixed runs never acquire a material-plan authority and retain their historical
 * material route.
 */
internal class W5aPreparedTextMaterialBridge private constructor(
    private val candidatesByOperationIndex: Map<Int, DisplayOp.DrawText>,
    private val width: Int,
    private val height: Int,
) {
    fun materialFor(operationIndex: Int): GPUPreparedTextMaterialPlan? {
        val operation = candidatesByOperationIndex[operationIndex] ?: return null
        // Scene capture is an optional W5a admission step. Invalid/non-finite inputs must
        // continue to prepared-text validation so its typed diagnostic is preserved rather
        // than being replaced by a generic frame error.
        val captured = runCatching {
            DisplayOpSceneAdapter.capture(
                operations = listOf(operation),
                extent = SceneExtent(width, height),
                colorSpace = ColorSpace.SRGB,
            )
        }.getOrNull() as? SceneCaptureResult.Captured ?: return null
        val draw = captured.scene.singleOrNull() as? SceneCommand.Draw ?: return null
        // W5b and later material kinds never acquire W5a text ownership.
        val planned = EffectiveMaterialPlanner.plan(draw.node)
            as? EffectiveMaterialPlanner.Result.Ready ?: return null
        return GPUPreparedTextMaterialPlan(planned.table, planned.root)
    }

    internal companion object {
        fun capture(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
        ): W5aPreparedTextMaterialBridge? {
            val candidates = linkedMapOf<Int, DisplayOp.DrawText>()
            operations.forEachIndexed { operationIndex, operation ->
                if (operation !is DisplayOp.DrawText || !operation.isW5aPreparedTextCandidate()) {
                    return@forEachIndexed
                }
                candidates[operationIndex] = operation
            }
            if (candidates.isEmpty()) return null
            return W5aPreparedTextMaterialBridge(
                candidatesByOperationIndex = candidates,
                width = width,
                height = height,
            )
        }

        private fun DisplayOp.DrawText.isW5aPreparedTextCandidate(): Boolean =
            paint.blendMode == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()

        private fun Shader?.isW5aSolidOpacity(): Boolean {
            var source = this
            var depth = 0
            while (source is Shader.Opacity) {
                if (++depth > 64) return false
                source = source.shader
            }
            return source == null || source is Shader.SolidColor
        }
    }
}
