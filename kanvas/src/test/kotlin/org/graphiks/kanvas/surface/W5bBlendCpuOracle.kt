@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32

/** Public-test-only final-blend oracle closed by the independent WGSL/attachment envelope. */
internal object W5bBlendCpuOracle {
    fun assertDstOver(background: ColorARGB, backgroundOpacityF32: Float, foreground: ColorARGB, foregroundOpacityF32: Float, actual: UByteArray) {
        val destination = table(background, backgroundOpacityF32)
        WgslFloatEnvelopeV1Oracle.assertAdmits(
            WgslFloatEnvelopeV1Oracle.drawDstOver(table(foreground, foregroundOpacityF32), MaterialPlanRef(1), destination, MaterialPlanRef(1)),
            actual,
        )
    }

    fun assertDst(background: ColorARGB, backgroundOpacityF32: Float, actual: UByteArray) =
        WgslFloatEnvelopeV1Oracle.assertAdmits(W5aSolidOpacityCpuOracle.draw(background, backgroundOpacityF32), actual)

    private fun table(color: ColorARGB, opacityF32: Float): MaterialPlanTable = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(
            MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(ColorF32.of(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)),
        ),
        MaterialPlanEntry(
            MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(opacityF32),
        ),
    ))
}
