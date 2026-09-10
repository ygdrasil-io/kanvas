package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.EffectiveMaterialPlanner
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32

/**
 * Selects the W5a material authority before prepared vertices lowering.  The lowerer receives
 * only this sealed table/reference pair and therefore never rebuilds a material descriptor for
 * an admitted Solid/Opacity draw.
 */
internal class W5aPreparedVerticesMaterialBridge private constructor(
    private val operations: List<DisplayOp>,
    private val width: Int,
    private val height: Int,
    private val frameMaterials: W5aPreparedCorePointMaterialBridge?,
) {
    sealed interface Result {
        data object NotCandidate : Result
        data class Ready(val materialPlan: GPUPreparedVerticesMaterialPlan) : Result
        data class Refused(val code: String, val facts: Map<String, String>) : Result
    }

    fun materialFor(operationIndex: Int): Result {
        val operation = operations.getOrNull(operationIndex) ?: return Result.NotCandidate
        if (!operation.isW5aPreparedVerticesCandidate()) return Result.NotCandidate
        frameMaterials?.refsByOperationIndex?.get(operationIndex)?.let { ref ->
            return Result.Ready(GPUPreparedVerticesMaterialPlan(frameMaterials.table, ref))
        }
        // W5a owns paint material only. Capture it against neutral geometry/state so transform,
        // clip, bounds, and public geometry validation retain their established lowerer authority.
        val materialCaptureOperation = operation.materialCaptureOperation()
        val captured = runCatching {
            DisplayOpSceneAdapter.capture(
                operations = listOf(materialCaptureOperation),
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
            frameMaterials: W5aPreparedCorePointMaterialBridge? = null,
        ): W5aPreparedVerticesMaterialBridge =
            W5aPreparedVerticesMaterialBridge(operations.toList(), width, height, frameMaterials)

        private fun DisplayOp.isW5aPreparedVerticesCandidate(): Boolean = when (this) {
            is DisplayOp.DrawVertices -> paint.blendMode == BlendMode.SRC_OVER &&
                paint.shader.isW5aSolidOpacity()
            is DisplayOp.DrawMesh -> mesh.program == null &&
                (blendMode ?: paint.blendMode) == BlendMode.SRC_OVER && paint.shader.isW5aSolidOpacity()
            else -> false
        }

        /**
         * A material-only Scene capture cannot reclassify invalid caller geometry, transform, or
         * clip as a material failure. DrawMesh without a program normalizes its selected blend
         * into the same DrawVertices paint route used by the lowerer.
         */
        private fun DisplayOp.materialCaptureOperation(): DisplayOp.DrawVertices = when (this) {
            is DisplayOp.DrawVertices -> DisplayOp.DrawVertices(
                vertices = materialCaptureTriangle(),
                paint = paint,
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            )
            is DisplayOp.DrawMesh -> DisplayOp.DrawVertices(
                vertices = materialCaptureTriangle(),
                paint = paint.copy(blendMode = blendMode ?: paint.blendMode),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            )
            else -> error("W5a material capture only accepts prepared vertices operations")
        }

        private fun materialCaptureTriangle(): Vertices = Vertices(
            VertexMode.TRIANGLES,
            listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
        )

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
