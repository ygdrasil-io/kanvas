@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.plan.MaterialPlanEntry
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialProgramPlan
import org.graphiks.kanvas.gpu.plan.MaterialBindingPlan
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W5bBlendSurfacePixelTest {
    @AfterEach fun disposeGpuRuntime() = GPUBackendRuntimeFactory.dispose()

    @Test
    fun `Surface W5b point does not hide a later round point refusal`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawPoint(1f, 1f, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
                antiAlias = false, blendMode = BlendMode.PLUS))
            drawPoint(2f, 2f, Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Red), .5f),
                strokeCap = StrokeCap.ROUND, strokeWidth = 2f, antiAlias = false))
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.core_primitive.point.round_cap_exact_lowering"), failure.message)
    }

    @Test
    fun `Surface HUE points retain bounded zero and near black source pixels`() {
        val destination = W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER)
        for (source in listOf(W5bBlendCpuOracle.Draw(ColorARGB.Black, .000001f, BlendMode.HUE),
            W5bBlendCpuOracle.Draw(ColorARGB.of(255, 1, 0, 0), .000001f, BlendMode.HUE))) {
            val surface = Surface(4, 4)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(destination.color), antiAlias = false))
                drawPoint(2f, 2f, Paint(shader = Shader.Opacity(Shader.SolidColor(source.color), source.opacityF32),
                    antiAlias = false, blendMode = source.mode))
            }
            W5bBlendCpuOracle.assertPoint(source, destination, 1f, surface.render().pixels.copyOfRange(40, 44))
        }
    }

    @Test
    fun `Picture replays fixed function SRC IN Solid Opacity in recorded order`() {
        val draws = listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Black, .5f, BlendMode.SRC_IN),
        )
        // Forward: half-alpha black SRC_IN opaque red. Reverse: SRC_IN clears,
        // then the same red SRC_OVER draw covers it. Both use the public replay path.
        val actual = render(record(draws))
        val reversedActual = render(record(draws.reversed()))
        W5bBlendCpuOracle.assertOrder(draws, actual, reversedActual)
    }

    @Test
    fun `Picture replays DST NoOp Solid Opacity`() {
        val background = ColorARGB.White
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(background, .5f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Blue, 1f, BlendMode.DST),
        ))
        W5bBlendCpuOracle.assertDst(background, .5f, render(picture))
    }

    @Test
    fun `Surface DST only returns clear without admitting its unused source`() {
        val source = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(4f, 0f),
            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        val surface = Surface(4, 4, config = RenderConfig(frameLocalBudgetBytes = 1088L))
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f),
            Paint(shader = source, antiAlias = false, blendMode = BlendMode.DST)) }
        val pixels = surface.render().pixels
        require(pixels.contentEquals(UByteArray(4 * 4 * 4)))
    }

    @Test
    fun `Picture multiply is disjoint from SRC OVER for the same captured source`() {
        val source = ColorARGB.of(255, 120, 0, 0)
        val destination = ColorARGB.Green
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(destination, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(source, .75f, BlendMode.MULTIPLY),
        ))
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(destination, 1f)))
        val material = solidSource(ColorF32.of(120f / 255f, 0f, 0f, 1f), .75f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.MULTIPLY)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture difference is disjoint from SRC OVER for the same captured source`() {
        val source = ColorARGB.White
        val destination = ColorARGB.Green
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(destination, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(source, .45f, BlendMode.DIFFERENCE),
        ))
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(destination, 1f)))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.DIFFERENCE)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture destination read first draw observes transparent initial target`() {
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(halfWhiteSource(), MaterialPlanRef(1),
            WgslFloatEnvelopeV1Oracle.clearAttachment(), BlendMode.MULTIPLY)
        val picture = record(listOf(W5bBlendCpuOracle.Draw(ColorARGB.White, .5f, BlendMode.MULTIPLY)))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture destination read observes write between two snapshots`() {
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Blue, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.of(255, 120, 0, 0), .5f, BlendMode.MULTIPLY),
            W5bBlendCpuOracle.Draw(ColorARGB.Green, 1f, BlendMode.SRC),
            W5bBlendCpuOracle.Draw(ColorARGB.Red, 1f, BlendMode.DST),
            W5bBlendCpuOracle.Draw(ColorARGB.White, .45f, BlendMode.DIFFERENCE),
        ))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val green = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)))
        val blue = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(W5aSolidOpacityCpuOracle.draw(ColorARGB.Blue, 1f)))
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), green, BlendMode.DIFFERENCE)
        val stale = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), blue, BlendMode.DIFFERENCE)
        check(stale is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded)
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.ConservativeExclusion(stale.channels))
        assertDisjoint(expected, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), green))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Surface destination read preserves integral scissor and outside destination`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(ColorARGB.Green), antiAlias = false))
            clipRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .45f),
                antiAlias = false, blendMode = BlendMode.DIFFERENCE))
        }
        val pixels = surface.render().pixels
        val green = W5aSolidOpacityCpuOracle.draw(ColorARGB.Green, 1f)
        val background = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(green))
        val material = solidSource(ColorF32.of(1f, 1f, 1f, 1f), .45f)
        val inside = WgslFloatEnvelopeV1Oracle.drawDestination(material, MaterialPlanRef(1), background, BlendMode.DIFFERENCE)
        assertDisjoint(inside, WgslFloatEnvelopeV1Oracle.sourceOverExclusion(material, MaterialPlanRef(1), background))
        for (yI32 in 0 until 4) for (xI32 in 0 until 4) {
            val offsetI32 = (yI32 * 4 + xI32) * 4
            try {
                WgslFloatEnvelopeV1Oracle.assertAdmits(if (xI32 in 1..2 && yI32 in 1..2) inside else green,
                    pixels.copyOfRange(offsetI32, offsetI32 + 4))
            } catch (failure: IllegalArgumentException) {
                throw AssertionError("Pixel ($xI32,$yI32): ${failure.message}", failure)
            }
        }
    }

    private fun halfWhiteSource() = halfSolidSource(ColorF32.of(1f, 1f, 1f, 1f))

    private fun halfSolidSource(color: ColorF32) = solidSource(color, .5f)

    private fun assertDisjoint(expected: WgslFloatEnvelopeV1Oracle.DrawResult, counterfactual: WgslFloatEnvelopeV1Oracle.ConservativeExclusion) {
        check(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { "Expected: $expected" }
        check(expected.channels.zip(counterfactual.channels).any { (a, b) -> a.intersect(b).isEmpty() }) {
            "Counterfactual overlaps: ${expected.channels} versus ${counterfactual.channels}"
        }
    }

    private fun solidSource(color: ColorF32, opacityF32: Float) = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(color)),
        MaterialPlanEntry(MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(opacityF32)),
    ))

    private fun render(picture: Picture): UByteArray = Surface(4, 4).also { surface ->
        surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
    }.render().pixels.copyOfRange(0, 4)

    private fun record(draws: List<W5bBlendCpuOracle.Draw>) = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).apply {
            for (draw in draws) {
                drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(draw.color), draw.opacityF32), antiAlias = false, blendMode = draw.mode))
            }
        }
    }.finishRecordingAsPicture()
}
