package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.math.color.ColorF32
import org.graphiks.math.color.ColorTransferFunction

/** Normalizes the W5a Solid/Opacity subset once, before a graph is published Ready. */
public object EffectiveMaterialPlanner {
    public sealed interface Result {
        public data class Ready(public val table: MaterialPlanTable, public val root: MaterialPlanRef) : Result
        public data class Refused(public val diagnosticCode: String) : Result
    }

    public fun plan(draw: DrawNode): Result {
        val srcOver = when (val blend = draw.blend) {
            BlendNode.SrcOver -> true
            is BlendNode.Mode -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER
            is BlendNode.Paint -> blend.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER && blend.blender == null
            is BlendNode.Custom -> false
        }
        if (!srcOver || draw.effects !is EffectStack.Empty || draw.resource != null || draw.operationBlendMode != null) {
            return Result.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        }
        var material = draw.material
        val opacityInnerToOuter = mutableListOf<Float>()
        var visited = 0
        while (material is MaterialNode.Opacity) {
            if (++visited > 64 || !material.alpha.isFinite() || material.alpha !in 0f..1f) {
                return Result.Refused(W5aPlanDiagnostics.InvalidOpacity)
            }
            opacityInnerToOuter += material.alpha
            material = material.material
        }
        val base = when (material) {
            MaterialNode.Transparent -> return Result.Ready(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0),
            )
            is MaterialNode.Solid -> MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(toLinearPremul(material.color.redNormalized, material.color.greenNormalized, material.color.blueNormalized, material.color.alphaNormalized)),
            )
            else -> return Result.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        }
        val alphaValues = opacityInnerToOuter.asReversed().toMutableList()
        draw.paint?.takeIf { it.shader != null }?.let { alphaValues += it.color.alphaNormalized }
        val combinedAlpha = alphaValues.fold(1f) { accumulated, alpha -> accumulated * alpha }
        if (!combinedAlpha.isFinite()) return Result.Refused(W5aPlanDiagnostics.InvalidOpacity)
        if (combinedAlpha == 0f) {
            return Result.Ready(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0),
            )
        }
        val entries = mutableListOf(base)
        if (combinedAlpha != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1,
                MaterialBindingPlan.OpacityF32V1.of(combinedAlpha, MaterialPlanRef(0)),
            )
        }
        return Result.Ready(MaterialPlanTable.of(entries), MaterialPlanRef(entries.lastIndex))
    }

    private fun toLinearPremul(r: Float, g: Float, b: Float, a: Float): ColorF32 = ColorF32.of(
        ColorTransferFunction.sRgb.toLinear(r) * a,
        ColorTransferFunction.sRgb.toLinear(g) * a,
        ColorTransferFunction.sRgb.toLinear(b) * a,
        a,
    )
}
