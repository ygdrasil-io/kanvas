@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32

/** Public-test-only final-blend oracle closed by the independent WGSL/attachment envelope. */
internal object W5bBlendCpuOracle {
    data class Draw(val color: ColorARGB, val opacityF32: Float, val mode: BlendMode)

    fun assertOrder(draws: List<Draw>, actual: UByteArray, reversedActual: UByteArray) {
        val forward = replay(draws)
        // Reverse the recorded draws, preserving each draw's source AND final blend mode.
        val reverse = replay(draws.reversed())
        assertDisjoint(forward, reverse)
        WgslFloatEnvelopeV1Oracle.assertAdmits(forward, actual)
        WgslFloatEnvelopeV1Oracle.assertAdmits(reverse, reversedActual)
    }

    private fun replay(draws: List<Draw>): WgslFloatEnvelopeV1Oracle.DrawResult {
        var attachment = WgslFloatEnvelopeV1Oracle.clearAttachment()
        var result: WgslFloatEnvelopeV1Oracle.DrawResult? = null
        for (draw in draws) {
            val source = table(draw.color, draw.opacityF32)
            result = when (draw.mode) {
                BlendMode.SRC_OVER -> WgslFloatEnvelopeV1Oracle.draw(source, MaterialPlanRef(1), attachment)
                BlendMode.SRC_IN -> WgslFloatEnvelopeV1Oracle.drawSrcIn(source, MaterialPlanRef(1), attachment)
                else -> error("Unsupported public oracle mode: ${draw.mode}")
            }
            attachment = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(result))
        }
        return requireNotNull(result)
    }

    fun assertDst(background: ColorARGB, backgroundOpacityF32: Float, actual: UByteArray) =
        WgslFloatEnvelopeV1Oracle.assertAdmits(W5aSolidOpacityCpuOracle.draw(background, backgroundOpacityF32), actual)

    private fun assertDisjoint(forward: WgslFloatEnvelopeV1Oracle.DrawResult, reverse: WgslFloatEnvelopeV1Oracle.DrawResult) {
        val forwardCodes = (forward as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)?.channels
            ?: error("Forward draw result is not bounded: $forward")
        val reverseCodes = (reverse as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)?.channels
            ?: error("Reversed draw result is not bounded: $reverse")
        assertTrue(
            forwardCodes.zip(reverseCodes).any { (direct, inverted) -> direct.intersect(inverted).isEmpty() },
            "Picture result must be disjoint from the reversed draw order: forward=$forwardCodes reverse=$reverseCodes",
        )
    }

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
