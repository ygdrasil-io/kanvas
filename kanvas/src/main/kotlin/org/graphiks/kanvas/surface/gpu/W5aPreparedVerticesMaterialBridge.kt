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
 * Selects the W5a material authority before prepared vertices lowering.  The lowerer receives
 * only this sealed table/reference pair and therefore never rebuilds a material descriptor for
 * an admitted Solid/Opacity draw.
 */
internal class W5aPreparedVerticesMaterialBridge private constructor(
    private val candidatesByOperationIndex: Map<Int, DisplayOp>,
    private val width: Int,
    private val height: Int,
) {
    fun materialFor(operationIndex: Int): GPUPreparedVerticesMaterialPlan? {
        val operation = candidatesByOperationIndex[operationIndex] ?: return null
        val captured = runCatching {
            DisplayOpSceneAdapter.capture(
                operations = listOf(operation),
                extent = SceneExtent(width, height),
                colorSpace = ColorSpace.SRGB,
            )
        }.getOrNull() as? SceneCaptureResult.Captured ?: return null
        val draw = captured.scene.singleOrNull() as? SceneCommand.Draw ?: return null
        val planned = EffectiveMaterialPlanner.plan(draw.node)
            as? EffectiveMaterialPlanner.Result.Ready ?: return null
        return GPUPreparedVerticesMaterialPlan(planned.table, planned.root)
    }

    internal companion object {
        fun capture(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
        ): W5aPreparedVerticesMaterialBridge? {
            val candidates = linkedMapOf<Int, DisplayOp>()
            operations.forEachIndexed { operationIndex, operation ->
                if (operation.isW5aPreparedVerticesCandidate()) candidates[operationIndex] = operation
            }
            return candidates.takeIf { it.isNotEmpty() }?.let {
                W5aPreparedVerticesMaterialBridge(it, width, height)
            }
        }

        private fun DisplayOp.isW5aPreparedVerticesCandidate(): Boolean = when (this) {
            is DisplayOp.DrawVertices -> paint.blendMode == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawMesh -> mesh.program == null &&
                (blendMode ?: paint.blendMode) == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()
            else -> false
        }

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
