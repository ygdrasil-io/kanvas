@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.math.color.ColorARGB

/** Independent straight-alpha source fixture for the first public W5a Rect proof. */
internal object W5aSolidOpacityCpuOracle {
    fun source(
        color: ColorARGB,
        shaderOpacityOuterF32: Float,
        shaderOpacityInnerF32: Float,
        paintAlphaF32: Float,
    ): FloatArray {
        val source = floatArrayOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized)
        val clear = floatArrayOf(0f, 0f, 0f, 0f)
        val solidGraph = MaterialProgramPlan.SolidLinearPremulV1.copyNumericOperationGraphV1()
        val solid = WgslFloatEnvelopeV1Oracle.evaluateMaterialSource(
            solidGraph,
            WgslFloatEnvelopeV1Oracle.Inputs(solidSrgbaStraight = source, destinationLinearPremul = clear, coverageF32 = 1f),
        )
        val opacityGraph = MaterialProgramPlan.OpacityV1.copyNumericOperationGraphV1()
        return WgslFloatEnvelopeV1Oracle.evaluateAttachmentOutput(
            opacityGraph,
            WgslFloatEnvelopeV1Oracle.Inputs(
                materialLinearPremul = solid,
                destinationLinearPremul = clear,
                coverageF32 = 1f,
                opacityF32 = shaderOpacityOuterF32 * shaderOpacityInnerF32 * paintAlphaF32,
            ),
        )
    }

    fun srcOver(
        destinationLinearPremul: FloatArray,
        color: ColorARGB,
        shaderOpacityF32: Float,
        paintAlphaF32: Float,
    ): FloatArray {
        val clear = floatArrayOf(0f, 0f, 0f, 0f)
        val solid = WgslFloatEnvelopeV1Oracle.evaluateMaterialSource(
            MaterialProgramPlan.SolidLinearPremulV1.copyNumericOperationGraphV1(),
            WgslFloatEnvelopeV1Oracle.Inputs(
                solidSrgbaStraight = floatArrayOf(color.redNormalized, color.greenNormalized, color.blueNormalized, color.alphaNormalized),
                destinationLinearPremul = clear,
                coverageF32 = 1f,
            ),
        )
        val graph = MaterialProgramPlan.OpacityV1.copyNumericOperationGraphV1()
        return WgslFloatEnvelopeV1Oracle.evaluateLinearOutput(
            graph,
            WgslFloatEnvelopeV1Oracle.Inputs(
                materialLinearPremul = solid,
                destinationLinearPremul = destinationLinearPremul,
                coverageF32 = 1f,
                opacityF32 = shaderOpacityF32 * paintAlphaF32,
            ),
        )
    }

    fun encode(linearPremul: FloatArray): FloatArray = WgslFloatEnvelopeV1Oracle.evaluateAttachmentOutput(
        MaterialProgramPlan.TransparentV1.copyNumericOperationGraphV1(),
        WgslFloatEnvelopeV1Oracle.Inputs(
            destinationLinearPremul = linearPremul,
            coverageF32 = 0f,
        ),
    )
}
