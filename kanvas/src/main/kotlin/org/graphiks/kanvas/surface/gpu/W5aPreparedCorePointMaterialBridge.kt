package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
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
 * Interns immutable W5a sources for authentic prepared core, A8 text and vertices lanes.
 *
 * Individual public draws are captured only to obtain their immutable DrawNode.  Their entries
 * are then interned into one frame-owned [MaterialPlanTable]; all native families receive
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
            admittedTextDraws: List<GPUPreparedTextDraw>,
            admittedVerticesDraws: List<GPUPreparedVerticesDraw>,
        ): W5aPreparedCorePointMaterialBridge? {
            val plannedByOperationIndex = linkedMapOf<Int, EffectiveMaterialPlanner.Result.Ready>()
            operations.forEachIndexed { operationIndex, operation ->
                if (operation.corePointGeometryRefusalOrNull() != null) return@forEachIndexed
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
                plannedByOperationIndex[operationIndex] = planned
            }
            // Only actual lowerer admissions enter the table. In particular, non-A8 text and
            // invalid vertices cannot consume capacity or preempt geometry diagnostics.
            admittedTextDraws.forEach { draw -> draw.materialPlan?.let { material ->
                plannedByOperationIndex[draw.operationIndex] = EffectiveMaterialPlanner.Result.Ready(material.table, material.ref)
            } }
            admittedVerticesDraws.forEach { draw -> draw.materialPlan?.let { material ->
                plannedByOperationIndex[draw.operationIndex] = EffectiveMaterialPlanner.Result.Ready(material.table, material.ref)
            } }
            val orderedPlans = plannedByOperationIndex.toSortedMap()
            if (orderedPlans.isEmpty()) return null
            // Once at least one source selects W5a, an invalid aggregate table is terminal.
            val interned = try {
                MaterialPlanTable.intern(orderedPlans.values.map { it.table })
            } catch (_: IllegalArgumentException) {
                throw GPUPreparedSurfaceTerminalException(org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic(
                    org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode("resource.material.w5a.table-limit"),
                    org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain.Resources,
                    org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity.Error,
                    "The W5a frame material table exceeds its bounded entry capacity.",
                ))
            }
            val refs = orderedPlans.entries.mapIndexed { laneOrdinalI32, (operationIndex, planned) ->
                operationIndex to interned.remap(laneOrdinalI32, planned.root)
            }.toMap()
            return W5aPreparedCorePointMaterialBridge(
                table = interned.table,
                refsByOperationIndex = java.util.Collections.unmodifiableMap(LinkedHashMap(refs)),
            )
        }

        private fun DisplayOp.isW5aPreparedCorePointCandidate(): Boolean = when (this) {
            is DisplayOp.DrawRect -> !paint.isStroke() && paint.blendMode == BlendMode.SRC_OVER &&
                paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawRRect -> !paint.isStroke() && paint.blendMode == BlendMode.SRC_OVER &&
                paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawPath -> paint.blendMode == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()
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
