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
    data class Draw(val color: ColorARGB, val opacityF32: Float, val mode: BlendMode,
        val preparation: List<Draw> = emptyList())
    private data class PointKey(val source: Draw, val destination: Draw, val coverageF32: Float, val scalarMask: Boolean)
    private val pointResults = mutableMapOf<PointKey, WgslFloatEnvelopeV1Oracle.DrawResult>()
    private val backgrounds = mutableMapOf<Draw, WgslFloatEnvelopeV1Oracle.DrawResult>()
    data class PointFixture(val center: Draw, val edge: Draw, val destination: Draw, val centerDestination: Draw,
        val centerSetup: Draw? = null)
    private val historicalFixtures = mutableMapOf<BlendMode, PointFixture>()

    /** Fixed fixtures: source/destination selection never consults a rendered pixel. */
    fun historicalPointFixture(mode: BlendMode): PointFixture = historicalFixtures.getOrPut(mode) {
        val (edge, destination) = historicalEdgeFixture(mode)
        val center = when (mode) {
            BlendMode.HUE -> Draw(ColorARGB.of(255, 239, 225, 240), .375f, mode)
            BlendMode.PLUS, BlendMode.SOFT_LIGHT -> Draw(ColorARGB.of(255, 240, 192, 224), .928f, mode)
            BlendMode.COLOR_BURN, BlendMode.COLOR -> Draw(ColorARGB.of(255, 240, 192, 232), .928f, mode)
            else -> edge
        }
        val setup = if (mode == BlendMode.COLOR_BURN) Draw(ColorARGB.of(255, 0, 0, 152), .125f, BlendMode.SRC) else null
        val centerDestination = setup?.copy(preparation = destination.preparation + destination) ?: destination
        val fixture = PointFixture(center, edge, destination, centerDestination, setup)
        require(center.mode == mode && edge.mode == mode)
        require(listOf(center, edge).all { it.opacityF32 > 0f && it.opacityF32 < 1f })
        if (mode != BlendMode.PLUS) {
            require(listOf(center, edge, destination, centerDestination).all {
                setOf(it.color.red, it.color.green, it.color.blue).size > 1
            })
            val background = bounded(point(center, centerDestination, 0f))
            require(WgslFloatEnvelopeV1Oracle.hasPositiveArtisticTerm(table(center.color, center.opacityF32),
                MaterialPlanRef(1), background.state, mode)) { "$mode must activate a positive artistic term" }
        }
        proveFullPoint(center, centerDestination)
        // With the local COLOR_BURN setup, the other fully covered points see the
        // unmodified general background, and therefore require their own proof.
        if (setup != null) proveFullPoint(edge, destination)
        proveMaskedEdge(edge, destination)
        fixture
    }

    private fun historicalEdgeFixture(mode: BlendMode): Pair<Draw, Draw> = when (mode) {
        BlendMode.PLUS -> Draw(ColorARGB.of(255, 224, 240, 192), .5f, mode) to Draw(ColorARGB.Black, .0625f, BlendMode.SRC_OVER)
        BlendMode.MULTIPLY -> Draw(ColorARGB.of(255, 240, 192, 129), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 93), .25f, BlendMode.SRC_OVER)
        BlendMode.OVERLAY -> Draw(ColorARGB.of(255, 240, 192, 127), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 93), .25f, BlendMode.SRC_OVER)
        BlendMode.DARKEN -> Draw(ColorARGB.of(255, 240, 192, 66), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 93), .25f, BlendMode.SRC_OVER)
        BlendMode.LIGHTEN -> Draw(ColorARGB.of(255, 240, 240, 129), .922f, mode) to Draw(ColorARGB.of(250, 78, 78, 128), 1f, BlendMode.PLUS,
            preparation = listOf(Draw(ColorARGB.of(239, 0, 0, 255), 1f, BlendMode.PLUS)))
        BlendMode.COLOR_DODGE -> Draw(ColorARGB.of(255, 224, 192, 129), .958f, mode) to Draw(ColorARGB.of(255, 0, 0, 49), .25f, BlendMode.SRC_OVER)
        BlendMode.COLOR_BURN -> Draw(ColorARGB.of(255, 240, 192, 49), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 49), .25f, BlendMode.SRC_OVER)
        BlendMode.HARD_LIGHT -> Draw(ColorARGB.of(255, 240, 192, 80), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 49), .25f, BlendMode.SRC_OVER)
        BlendMode.SOFT_LIGHT -> Draw(ColorARGB.of(255, 240, 192, 50), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 49), .25f, BlendMode.SRC_OVER)
        BlendMode.DIFFERENCE -> Draw(ColorARGB.of(255, 240, 192, 129), .928f, mode) to Draw(ColorARGB.of(255, 0, 0, 93), .25f, BlendMode.SRC_OVER)
        BlendMode.EXCLUSION -> Draw(ColorARGB.of(255, 240, 240, 131), .992f, mode) to Draw(ColorARGB.of(255, 0, 0, 93), .25f, BlendMode.SRC_OVER)
        BlendMode.HUE -> Draw(ColorARGB.of(255, 239, 224, 240), .957f, mode) to Draw(ColorARGB.of(250, 128, 128, 78), 1f, BlendMode.PLUS,
            preparation = listOf(Draw(ColorARGB.of(239, 255, 255, 0), 1f, BlendMode.PLUS)))
        BlendMode.SATURATION -> Draw(ColorARGB.of(255, 192, 160, 240), .984f, mode) to Draw(ColorARGB.of(255, 255, 0, 255), 1f, BlendMode.SRC_OVER)
        BlendMode.COLOR -> Draw(ColorARGB.of(255, 240, 80, 160), .875f, mode) to Draw(ColorARGB.of(255, 255, 0, 255), 1f, BlendMode.SRC_OVER)
        BlendMode.LUMINOSITY -> Draw(ColorARGB.of(255, 255, 254, 248), .75f, mode) to Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER)
        else -> error("Not a historical W5b Point mode: $mode")
    }

    private fun bounded(result: WgslFloatEnvelopeV1Oracle.DrawResult): WgslFloatEnvelopeV1Oracle.DrawResult.Bounded =
        result as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded ?: error("Historical Point requires a strict envelope: $result")

    private fun disjoint(a: List<Set<Int>>, b: List<Set<Int>>) = a.zip(b).any { (x, y) -> x.intersect(y).isEmpty() }

    private fun proveFullPoint(source: Draw, destination: Draw) {
        val background = bounded(point(source, destination, 0f))
        val material = table(source.color, source.opacityF32)
        val over = WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background.state)
        for (scalar in listOf(false, true)) {
            val full = bounded(point(source, destination, 1f, scalar))
            require(disjoint(full.channels, background.channels)) { "${source.mode} full must differ from DST" }
            require(disjoint(full.channels, over.channels)) { "${source.mode} full must differ from SRC_OVER" }
            for (sibling in confusableModes(source.mode)) {
                val counterfactual = WgslFloatEnvelopeV1Oracle.destinationExclusion(material, MaterialPlanRef(1),
                    background.state, sibling, 1f, scalar)
                require(disjoint(full.channels, counterfactual.channels)) { "${source.mode} full must differ from $sibling" }
            }
        }
    }

    private fun proveMaskedEdge(source: Draw, destination: Draw) {
        val background = bounded(point(source, destination, 0f))
        val half = bounded(point(source, destination, .5f, true))
        val material = table(source.color, source.opacityF32)
        val full = WgslFloatEnvelopeV1Oracle.destinationExclusion(material, MaterialPlanRef(1), background.state, source.mode, 1f, true)
        val over = WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background.state, .5f)
        require(disjoint(half.channels, full.channels)) { "${source.mode} mask edge must differ from full coverage" }
        require(disjoint(half.channels, background.channels)) { "${source.mode} mask edge must differ from DST" }
        require(disjoint(half.channels, over.channels)) { "${source.mode} mask edge must differ from SRC_OVER" }
        for (sibling in confusableModes(source.mode)) {
            val counterfactual = WgslFloatEnvelopeV1Oracle.destinationExclusion(material, MaterialPlanRef(1), background.state, sibling, .5f, true)
            require(disjoint(half.channels, counterfactual.channels)) { "${source.mode} mask edge must differ from $sibling" }
        }
    }

    private fun confusableModes(mode: BlendMode): List<BlendMode> = when (mode) {
        BlendMode.MULTIPLY -> listOf(BlendMode.DARKEN)
        BlendMode.DARKEN -> listOf(BlendMode.MULTIPLY)
        BlendMode.OVERLAY -> listOf(BlendMode.HARD_LIGHT)
        BlendMode.HARD_LIGHT -> listOf(BlendMode.OVERLAY)
        BlendMode.COLOR_DODGE -> listOf(BlendMode.LIGHTEN)
        BlendMode.LIGHTEN -> listOf(BlendMode.COLOR_DODGE)
        BlendMode.COLOR_BURN -> listOf(BlendMode.DARKEN)
        BlendMode.SOFT_LIGHT -> listOf(BlendMode.HARD_LIGHT)
        BlendMode.DIFFERENCE -> listOf(BlendMode.EXCLUSION)
        BlendMode.EXCLUSION -> listOf(BlendMode.DIFFERENCE)
        BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY ->
            listOf(BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR, BlendMode.LUMINOSITY).filter { it != mode }
        else -> emptyList()
    }

    fun assertPoint(source: Draw, destination: Draw, coverageF32: Float, actual: UByteArray, scalarMask: Boolean = false) {
        val expected = point(source, destination, coverageF32, scalarMask)
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, actual)
    }

    fun point(source: Draw, destination: Draw, coverageF32: Float, scalarMask: Boolean = false): WgslFloatEnvelopeV1Oracle.DrawResult =
        pointResults.getOrPut(PointKey(source, destination, coverageF32, scalarMask)) { pointUncached(source, destination, coverageF32, scalarMask) }

    private fun pointUncached(source: Draw, destination: Draw, coverageF32: Float, scalarMask: Boolean): WgslFloatEnvelopeV1Oracle.DrawResult {
        val background = backgrounds.getOrPut(destination) {
            var state = WgslFloatEnvelopeV1Oracle.clearAttachment()
            var result: WgslFloatEnvelopeV1Oracle.DrawResult? = null
            for (draw in destination.preparation + destination) {
                result = if (draw.mode == BlendMode.SRC) WgslFloatEnvelopeV1Oracle.drawPlus(
                    table(draw.color, draw.opacityF32), MaterialPlanRef(1), WgslFloatEnvelopeV1Oracle.clearAttachment())
                else if (draw.mode == BlendMode.PLUS) WgslFloatEnvelopeV1Oracle.drawPlus(
                    table(draw.color, draw.opacityF32), MaterialPlanRef(1), state)
                else W5aSolidOpacityCpuOracle.draw(draw.color, draw.opacityF32, destination = state)
                if (result is WgslFloatEnvelopeV1Oracle.DrawResult.Unbounded) return@getOrPut requireNotNull(result)
                state = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(requireNotNull(result)))
            }
            requireNotNull(result)
        }
        if (coverageF32 == 0f || background is WgslFloatEnvelopeV1Oracle.DrawResult.Unbounded) return background
        val attachment = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background))
        val material = table(source.color, source.opacityF32)
        return if (source.mode == BlendMode.PLUS && coverageF32 == 1f && !scalarMask) WgslFloatEnvelopeV1Oracle.drawPlus(material, MaterialPlanRef(1), attachment)
        else WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), attachment, source.mode, coverageF32, scalarMask)
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
