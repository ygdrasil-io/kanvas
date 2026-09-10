package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.types.PointMode

/**
 * Captures the W5a Point/POINTS material subset before legacy command mapping.
 *
 * Individual public draws are captured only to obtain their immutable DrawNode.  Their entries
 * are then copied into one frame-owned [MaterialPlanTable]; mapper and core commands receive
 * only the rebased [MaterialPlanRef].
 */
internal data class W5aPreparedCorePointMaterialBridge(
    val table: MaterialPlanTable,
    val refsByOperationIndex: Map<Int, MaterialPlanRef>,
) {
    internal companion object {
        fun capture(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
        ): W5aPreparedCorePointMaterialBridge? {
            val entries = mutableListOf<MaterialPlanEntry>()
            val refs = linkedMapOf<Int, MaterialPlanRef>()
            operations.forEachIndexed { operationIndex, operation ->
                if (!operation.isW5aPreparedCorePointCandidate()) return@forEachIndexed
                val captured = DisplayOpSceneAdapter.capture(
                    operations = listOf(operation),
                    extent = SceneExtent(width, height),
                    colorSpace = ColorSpace.SRGB,
                ) as? SceneCaptureResult.Captured
                    ?: error("invalid.material.w5a_core_capture")
                val draw = captured.scene.singleOrNull() as? SceneCommand.Draw
                    ?: error("invalid.material.w5a_core_draw")
                // Unsupported effects/material kinds retain their pre-W5 admission contract;
                // a Ready result is the point at which this frame owns the material.
                val planned = EffectiveMaterialPlanner.plan(draw.node)
                    as? EffectiveMaterialPlanner.Result.Ready ?: return@forEachIndexed
                val offset = entries.size
                entries += planned.table.entries()
                refs[operationIndex] = MaterialPlanRef(offset + planned.root.indexI32)
            }
            if (entries.isEmpty()) return null
            // Once at least one point selects W5a, an invalid aggregate table is terminal.
            val table = MaterialPlanTable.of(entries)
            return W5aPreparedCorePointMaterialBridge(
                table = table,
                refsByOperationIndex = java.util.Collections.unmodifiableMap(LinkedHashMap(refs)),
            )
        }

        private fun DisplayOp.isW5aPreparedCorePointCandidate(): Boolean = when (this) {
            is DisplayOp.DrawPoint ->
                paint.blendMode == BlendMode.SRC_OVER && paint.strokeCap != StrokeCap.ROUND &&
                    paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawPoints ->
                mode == PointMode.POINTS &&
                    paint.blendMode == BlendMode.SRC_OVER && paint.strokeCap != StrokeCap.ROUND &&
                    paint.shader.isW5aSolidOpacity()
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
