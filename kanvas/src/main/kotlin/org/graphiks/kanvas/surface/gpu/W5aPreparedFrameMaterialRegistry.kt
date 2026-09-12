package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Interns immutable W5a sources for authentic prepared core, A8 text and vertices lanes.
 *
 * Only completed mapper/lowerer admissions may enter the frame table. Local paint candidates
 * are not frame ownership and never consume the aggregate table capacity.
 */
internal data class W5aPreparedFrameMaterialRegistry(
    val table: MaterialPlanTable,
    val refsByCommandId: Map<Int, MaterialPlanRef>,
) {
    internal companion object {
        fun capturePointClips(operations: List<DisplayOp>): Map<Int, org.graphiks.kanvas.render.ir.ClipStackNode> =
            operations.mapIndexedNotNull { index, operation ->
                val clip = when (operation) {
                    is DisplayOp.DrawPoint -> operation.clip
                    is DisplayOp.DrawPoints -> operation.clip
                    else -> return@mapIndexedNotNull null
                }
                if (clip == ClipStack.WideOpen) null else index to DisplayOpSceneAdapter.captureClip(clip)
            }.toMap()

        fun captureCoreCandidates(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
            targetClamp: org.graphiks.kanvas.gpu.plan.BlendTargetClampV1,
        ): Map<Int, EffectiveMaterialPlanner.Result.Ready> {
            val plannedByOperationIndex = linkedMapOf<Int, EffectiveMaterialPlanner.Result.Ready>()
            operations.forEachIndexed { operationIndex, operation ->
                if (!operation.isW5aCoreMaterialCandidate()) return@forEachIndexed
                val paint = when (operation) {
                    is DisplayOp.DrawRect -> operation.paint
                    is DisplayOp.DrawRRect -> operation.paint
                    is DisplayOp.DrawPath -> operation.paint
                    is DisplayOp.DrawPoint -> operation.paint
                    is DisplayOp.DrawPoints -> operation.paint
                    else -> return@forEachIndexed
                }
                // Gradient numeric authority must see the actual geometry and captured CTM.
                // Deferred Point(s) retain their existing material-only capture.
                val captured = runCatching { DisplayOpSceneAdapter.capture(
                    operations = listOf(if (operation is DisplayOp.DrawPoint || operation is DisplayOp.DrawPoints)
                        DisplayOp.DrawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint, Matrix3x3F32.Identity, ClipStack.WideOpen)
                        else operation),
                    extent = SceneExtent(width, height),
                    colorSpace = ColorSpace.SRGB,
                ) }.getOrNull() as? SceneCaptureResult.Captured ?: return@forEachIndexed
                val draw = captured.scene.singleOrNull() as? SceneCommand.Draw
                    ?: return@forEachIndexed
                val planned = (if (operation is DisplayOp.DrawPoint || operation is DisplayOp.DrawPoints)
                    org.graphiks.kanvas.gpu.plan.W5bCorePrimitiveGraph.normalizeSource(draw.node, targetClamp)
                    else EffectiveMaterialPlanner.planW5b(draw.node, targetClamp,
                        gradientDeviceBoundsI32 = org.graphiks.math.geometry.RectI32(0, 0, width, height)))
                    as? EffectiveMaterialPlanner.Result.Ready ?: return@forEachIndexed
                plannedByOperationIndex[operationIndex] = planned
            }
            return java.util.Collections.unmodifiableMap(plannedByOperationIndex)
        }

        fun seal(
            semantics: Map<Int, GPUDrawSemanticPayload>,
            corePlansByCommandId: Map<Int, EffectiveMaterialPlanner.Result.Ready>,
        ): W5aPreparedFrameMaterialRegistry? {
            val orderedPlans = sortedMapOf<Int, EffectiveMaterialPlanner.Result.Ready>()
            semantics.forEach { (commandId, semantic) ->
                val planned = when (semantic) {
                    is GPUDrawSemanticPayload.CorePrimitive -> {
                        val material = semantic.material as? GPUCorePrimitiveMaterialPayload.W5aMaterialPlanRefV1
                            ?: return@forEach
                        requireNotNull(corePlansByCommandId[commandId]).also { require(it.root == material.ref) }
                    }
                    is GPUDrawSemanticPayload.TextA8 -> semantic.materialPlanProvenance?.let {
                        EffectiveMaterialPlanner.Result.Ready(it.sourcePlanTable, it.ref)
                    }
                    is GPUDrawSemanticPayload.Vertices -> semantic.materialPlanProvenance?.let {
                        EffectiveMaterialPlanner.Result.Ready(it.sourcePlanTable, it.ref)
                    }
                    else -> null
                }
                if (planned != null) orderedPlans[commandId] = planned
            }
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
            return W5aPreparedFrameMaterialRegistry(
                table = interned.table,
                refsByCommandId = java.util.Collections.unmodifiableMap(LinkedHashMap(refs)),
            )
        }

        private fun DisplayOp.isW5aCoreMaterialCandidate(): Boolean = when (this) {
            is DisplayOp.DrawRect -> !paint.isStroke() &&
                paint.shader.isW5aSolidOpacity(allowGradient = true)
            is DisplayOp.DrawRRect -> !paint.isStroke() &&
                paint.shader.isW5aSolidOpacity(allowGradient = paint.antiAlias)
            is DisplayOp.DrawPath -> paint.shader.isW5aSolidOpacity(allowGradient = true)
            is DisplayOp.DrawPoint ->
                paint.blendMode in POINT_MATERIAL_BLENDS && paint.strokeCap != StrokeCap.ROUND &&
                    paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawPoints ->
                mode == PointMode.POINTS &&
                    paint.blendMode in POINT_MATERIAL_BLENDS && paint.strokeCap != StrokeCap.ROUND &&
                    paint.shader.isW5aSolidOpacity()
            else -> false
        }

        private fun Shader?.isW5aSolidOpacity(allowGradient: Boolean = false): Boolean {
            var source = this
            var depth = 0
            while (source is Shader.Opacity) {
                if (++depth > 64) return false
                source = source.shader
            }
            return source == null || source is Shader.SolidColor || allowGradient && when (source) {
                is Shader.LinearGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
                is Shader.RadialGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
                is Shader.SweepGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
                is Shader.ConicalGradient -> source.tileMode == TileMode.CLAMP && source.interpolation == ColorSpaceInterpolation.SRGB
                else -> false
            }
        }

        private val POINT_MATERIAL_BLENDS = setOf(BlendMode.SRC_OVER, BlendMode.PLUS, BlendMode.MULTIPLY,
            BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN,
            BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION,
            BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY)
    }
}
