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
    private val pointResults = mutableMapOf<Triple<Draw, Draw, Float>, WgslFloatEnvelopeV1Oracle.DrawResult>()
    private val backgrounds = mutableMapOf<Draw, WgslFloatEnvelopeV1Oracle.DrawResult>()

    /** Fixed before rendering; every actual channel must retain the independent strict envelope. */
    fun pointFixture(mode: BlendMode): Pair<Draw, Draw> {
        val color = when (mode) {
            BlendMode.PLUS -> ColorARGB.White
            BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT -> ColorARGB.of(255, 240, 192, 128)
            BlendMode.EXCLUSION, BlendMode.HUE -> ColorARGB.of(255, 192, 224, 240)
            else -> ColorARGB.of(255, 224, 240, 192)
        }
        val opacityF32 = when (mode) {
            BlendMode.PLUS, BlendMode.COLOR_BURN, BlendMode.SOFT_LIGHT, BlendMode.LUMINOSITY -> .9375f
            BlendMode.DIFFERENCE -> .75f
            else -> .875f
        }
        val source = Draw(color, opacityF32, mode)
        val destination = Draw(if (mode == BlendMode.HUE) ColorARGB.Blue else ColorARGB.Green, .0625f, BlendMode.SRC_OVER)
        val background = point(source, destination, 0f) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
            ?: error("Point fixture background is unbounded")
        val full = point(source, destination, 1f) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
            ?: error("Point fixture $mode is unbounded")
        assertTrue(full.channels.zip(background.channels).any { (a, b) -> a.intersect(b).isEmpty() },
            "$mode must change its destination")
        val exclusion = WgslFloatEnvelopeV1Oracle.sourceOverExclusion(table(source.color, source.opacityF32),
            MaterialPlanRef(1), background.state)
        assertTrue(full.channels.zip(exclusion.channels).any { (a, b) -> a.intersect(b).isEmpty() },
            "$mode must be analytically disjoint from SRC_OVER")
        return source to destination
    }

    fun assertPoint(source: Draw, destination: Draw, coverageF32: Float, actual: UByteArray) {
        val expected = point(source, destination, coverageF32)
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, actual)
    }

    fun point(source: Draw, destination: Draw, coverageF32: Float): WgslFloatEnvelopeV1Oracle.DrawResult =
        pointResults.getOrPut(Triple(source, destination, coverageF32)) { pointUncached(source, destination, coverageF32) }

    private fun pointUncached(source: Draw, destination: Draw, coverageF32: Float): WgslFloatEnvelopeV1Oracle.DrawResult {
        val background = backgrounds.getOrPut(destination) { W5aSolidOpacityCpuOracle.draw(destination.color, destination.opacityF32) }
        if (coverageF32 == 0f || background is WgslFloatEnvelopeV1Oracle.DrawResult.Unbounded) return background
        val attachment = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val material = table(source.color, source.opacityF32)
        return if (source.mode == BlendMode.PLUS && coverageF32 == 1f) WgslFloatEnvelopeV1Oracle.drawPlus(material, MaterialPlanRef(1), attachment)
        else WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), attachment, source.mode, coverageF32)
    }

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
