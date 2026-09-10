package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.math.color.ColorF32

/** Normalizes the W5a Solid/Opacity subset once, before a graph is published Ready. */
public object EffectiveMaterialPlanner {
    public sealed interface Result {
        public data class Ready(
            public val table: MaterialPlanTable,
            public val root: MaterialPlanRef,
            public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
        ) : Result
        public data class Refused(public val diagnosticCode: String) : Result
    }

    /** Callers without a sealed target fact intentionally retain no clamp capability. */
    public fun plan(draw: DrawNode): Result = plan(draw, BlendTargetClampV1.Unavailable)

    internal sealed interface Normalization {
        data object NoOp : Normalization
        data class Source(val table: MaterialPlanTable, val root: MaterialPlanRef, val blend: BlendPlan) : Normalization
        data class Refused(val diagnosticCode: String) : Normalization
    }

    /** Compatibility boundary: existing owners cannot promote destination-read draws. */
    public fun plan(draw: DrawNode, targetClamp: BlendTargetClampV1): Result =
        when (val source = normalize(draw, targetClamp)) {
            Normalization.NoOp -> error("Compatibility normalization cannot elide draws")
            is Normalization.Refused -> Result.Refused(source.diagnosticCode)
            is Normalization.Source -> if (source.blend is BlendPlan.DestinationReadV1) {
                Result.Refused("unsupported.w5b.destination-read.task-2")
            } else Result.Ready(source.table, source.root, source.blend)
        }

    internal fun normalize(draw: DrawNode, targetClamp: BlendTargetClampV1, allowDestinationCandidate: Boolean = false): Normalization {
        val blend = FinalBlendPlanner.plan(draw.blend, CoveragePlan.FullOrScissor, SamplePlan.SingleSample, targetClamp)
            ?: return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        if (blend is BlendPlan.DestinationReadV1 && !allowDestinationCandidate) {
            return Normalization.Refused("unsupported.w5b.destination-read.task-2")
        }
        if (allowDestinationCandidate && blend == BlendPlan.NoOpV1) return Normalization.NoOp
        if (draw.effects !is EffectStack.Empty || draw.resource != null || draw.operationBlendMode != null) {
            return Normalization.Refused(W5aPlanDiagnostics.UnsupportedDrawState)
        }
        var material = draw.material
        val opacityInnerToOuter = mutableListOf<Float>()
        var visited = 0
        while (material is MaterialNode.Opacity) {
            if (++visited > 64 || !material.alpha.isFinite() || material.alpha !in 0f..1f) {
                return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
            }
            opacityInnerToOuter += material.alpha
            material = material.material
        }
        val base = when (material) {
            MaterialNode.Transparent -> return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
            is MaterialNode.Solid -> MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(
                    material.color.redNormalized,
                    material.color.greenNormalized,
                    material.color.blueNormalized,
                    material.color.alphaNormalized,
                )),
            )
            else -> return Normalization.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
        }
        val shaderAlpha = opacityInnerToOuter.asReversed().fold(1f) { accumulated, alpha -> accumulated * alpha }
        val paintAlpha = draw.paint?.takeIf { it.shader != null }?.color?.alphaNormalized ?: 1f
        if (!shaderAlpha.isFinite() || shaderAlpha !in 0f..1f || !paintAlpha.isFinite() || paintAlpha !in 0f..1f) {
            return Normalization.Refused(W5aPlanDiagnostics.InvalidOpacity)
        }
        if (shaderAlpha == 0f || paintAlpha == 0f) {
            return Normalization.Source(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0), blend,
            )
        }
        val entries = mutableListOf(base)
        if (shaderAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlpha != 1f) {
            val child = entries.last().program
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(child),
                MaterialBindingPlan.OpacityF32V1.of(paintAlpha),
            )
        }
        return Normalization.Source(MaterialPlanTable.of(entries), MaterialPlanRef(entries.lastIndex), blend)
    }
}
