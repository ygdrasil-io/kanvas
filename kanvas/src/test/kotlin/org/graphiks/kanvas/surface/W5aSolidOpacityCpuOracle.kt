package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32

/** Independent public-pixel fixture: it feeds the sealed W5a DAG, never renderer helpers. */
internal object W5aSolidOpacityCpuOracle {
    fun draw(
        color: ColorARGB,
        shaderOpacityOuterF32: Float,
        shaderOpacityInnerF32: Float = 1f,
        paintAlphaF32: Float = 1f,
        destination: WgslFloatEnvelopeV1Oracle.AttachmentState = WgslFloatEnvelopeV1Oracle.clearAttachment(),
        coverageF32: Float = 1f,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        val shaderAlpha = shaderOpacityInnerF32 * shaderOpacityOuterF32
        if (shaderAlpha == 0f || paintAlphaF32 == 0f) {
            return WgslFloatEnvelopeV1Oracle.draw(
                MaterialPlanTable.of(listOf(MaterialPlanEntry(MaterialProgramPlan.TransparentV1, MaterialBindingPlan.EmptyV1))),
                MaterialPlanRef(0),
                destination,
                coverageF32,
            )
        }
        val entries = mutableListOf(
            MaterialPlanEntry(
                MaterialProgramPlan.SolidLinearPremulV1,
                MaterialBindingPlan.SolidRgbaF32V1.of(
                    ColorF32.of(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized),
                ),
            ),
        )
        if (shaderAlpha != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(shaderAlpha),
            )
        }
        if (paintAlphaF32 != 1f) {
            entries += MaterialPlanEntry(
                MaterialProgramPlan.OpacityV1(entries.last().program),
                MaterialBindingPlan.OpacityF32V1.of(paintAlphaF32),
            )
        }
        return WgslFloatEnvelopeV1Oracle.draw(
            MaterialPlanTable.of(entries),
            MaterialPlanRef(entries.lastIndex),
            destination,
            coverageF32,
        )
    }
}
