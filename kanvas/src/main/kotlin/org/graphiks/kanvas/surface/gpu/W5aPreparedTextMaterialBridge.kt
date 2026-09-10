package org.graphiks.kanvas.surface.gpu

import java.util.Collections
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent

/**
 * Captures only the W5a text material subset before prepared glyph lowering.
 *
 * Text keeps its established glyph, atlas, and coverage preparation.  This bridge owns only
 * the immutable Solid/Opacity material pair, so later W5 material kinds remain outside text
 * ownership and follow their historical admission path.
 */
internal data class W5aPreparedTextMaterialBridge(
    val table: MaterialPlanTable,
    private val refsByOperationIndex: Map<Int, MaterialPlanRef>,
) {
    fun materialFor(operationIndex: Int): GPUPreparedTextMaterialPlan? =
        refsByOperationIndex[operationIndex]?.let { ref -> GPUPreparedTextMaterialPlan(table, ref) }

    internal companion object {
        fun capture(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
        ): W5aPreparedTextMaterialBridge? {
            val entries = mutableListOf<MaterialPlanEntry>()
            val refs = linkedMapOf<Int, MaterialPlanRef>()
            operations.forEachIndexed { operationIndex, operation ->
                if (operation !is DisplayOp.DrawText || !operation.isW5aPreparedTextCandidate()) {
                    return@forEachIndexed
                }
                val captured = DisplayOpSceneAdapter.capture(
                    operations = listOf(operation),
                    extent = SceneExtent(width, height),
                    colorSpace = ColorSpace.SRGB,
                ) as? SceneCaptureResult.Captured ?: error("invalid.material.w5a_text_capture")
                val draw = captured.scene.singleOrNull() as? SceneCommand.Draw
                    ?: error("invalid.material.w5a_text_draw")
                // W5b and later material kinds must not acquire W5a text ownership.
                val planned = EffectiveMaterialPlanner.plan(draw.node)
                    as? EffectiveMaterialPlanner.Result.Ready ?: return@forEachIndexed
                val offset = entries.size
                entries += planned.table.entries()
                refs[operationIndex] = MaterialPlanRef(offset + planned.root.indexI32)
            }
            if (entries.isEmpty()) return null
            return W5aPreparedTextMaterialBridge(
                table = MaterialPlanTable.of(entries),
                refsByOperationIndex = Collections.unmodifiableMap(LinkedHashMap(refs)),
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
