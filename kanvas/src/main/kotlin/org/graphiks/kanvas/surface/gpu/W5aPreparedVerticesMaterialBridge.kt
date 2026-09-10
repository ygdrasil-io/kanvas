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
import org.graphiks.kanvas.types.Vertices

/**
 * Selects the W5a material authority before prepared vertices lowering.  The lowerer receives
 * only this sealed table/reference pair and therefore never rebuilds a material descriptor for
 * an admitted Solid/Opacity draw.
 */
internal class W5aPreparedVerticesMaterialBridge private constructor(
    private val operations: List<DisplayOp>,
    private val width: Int,
    private val height: Int,
) {
    sealed interface Result {
        data object NotCandidate : Result
        data class Ready(val materialPlan: GPUPreparedVerticesMaterialPlan) : Result
        data class Refused(val code: String, val facts: Map<String, String>) : Result
    }

    fun materialFor(operationIndex: Int): Result {
        val operation = operations.getOrNull(operationIndex) ?: return Result.NotCandidate
        if (!operation.isW5aPreparedVerticesCandidate()) return Result.NotCandidate
        val captured = runCatching {
            DisplayOpSceneAdapter.capture(
                operations = listOf(operation),
                extent = SceneExtent(width, height),
                colorSpace = ColorSpace.SRGB,
            )
        }.getOrElse {
            return refused("capture_exception")
        }
        val scene = (captured as? SceneCaptureResult.Captured)?.scene
            ?: return refused("capture_refused")
        val draw = scene.singleOrNull() as? SceneCommand.Draw
            ?: return refused("capture_shape")
        return when (val planned = EffectiveMaterialPlanner.plan(draw.node)) {
            is EffectiveMaterialPlanner.Result.Ready ->
                Result.Ready(GPUPreparedVerticesMaterialPlan(planned.table, planned.root))
            is EffectiveMaterialPlanner.Result.Refused -> Result.Refused(
                code = org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Material,
                facts = mapOf(
                    "authority" to "EffectiveMaterialPlanner",
                    "stage" to "material-plan",
                    "reason" to "planner_refused",
                    "diagnosticCode" to planned.diagnosticCode,
                ),
            )
        }
    }

    private fun refused(reason: String): Result.Refused = Result.Refused(
        code = org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes.Material,
        facts = mapOf(
            "authority" to "DisplayOpSceneAdapter",
            "stage" to "material-plan",
            "reason" to reason,
        ),
    )

    internal companion object {
        fun capture(
            operations: List<DisplayOp>,
            width: Int,
            height: Int,
        ): W5aPreparedVerticesMaterialBridge =
            W5aPreparedVerticesMaterialBridge(operations.toList(), width, height)

        private fun DisplayOp.isW5aPreparedVerticesCandidate(): Boolean = when (this) {
            is DisplayOp.DrawVertices -> vertices.isW5aCandidateGeometry() &&
                paint.blendMode == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawMesh -> mesh.program == null &&
                mesh.vertices.isW5aCandidateGeometry() &&
                listOf(mesh.bounds.left, mesh.bounds.top, mesh.bounds.right, mesh.bounds.bottom)
                    .all(Float::isFinite) && mesh.bounds.right >= mesh.bounds.left &&
                mesh.bounds.bottom >= mesh.bounds.top &&
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

        /** Geometry owns its own public refusal codes, so malformed geometry never enters W5a. */
        private fun Vertices.isW5aCandidateGeometry(): Boolean =
            positions.size >= 3 && positions.all { it.x.isFinite() && it.y.isFinite() } &&
                (texCoords == null ||
                    (texCoords.size == positions.size && texCoords.all { it.x.isFinite() && it.y.isFinite() })) &&
                (colors == null || colors.size == positions.size) &&
                (indices == null || (indices.size >= 3 && indices.all { it in positions.indices }))
    }
}
