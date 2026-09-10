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
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class W5bBlendSurfacePixelTest {
    @AfterEach fun disposeGpuRuntime() = GPUBackendRuntimeFactory.dispose()

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
    fun `Picture multiplies captured half opaque black by opaque black`() {
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Black, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.Black, .5f, BlendMode.MULTIPLY),
        ))
        val black = W5aSolidOpacityCpuOracle.draw(ColorARGB.Black, 1f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(halfSolidSource(ColorF32.of(0f, 0f, 0f, 1f)),
            MaterialPlanRef(1), requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(black)), BlendMode.MULTIPLY)
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Picture differences the same captured half opaque white against opaque black`() {
        val picture = record(listOf(
            W5bBlendCpuOracle.Draw(ColorARGB.Black, 1f, BlendMode.SRC_OVER),
            W5bBlendCpuOracle.Draw(ColorARGB.White, .5f, BlendMode.DIFFERENCE),
        ))
        // S + D - 2*min(S*Da,D*Sa) = (.5,.5,.5), with alpha 1.
        // Close the shader formula directly: a fixed-function SRC_OVER surrogate
        // admits an additional coarse blend precision which is absent here.
        val background = W5aSolidOpacityCpuOracle.draw(ColorARGB.Black, 1f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(
            halfWhiteSource(), MaterialPlanRef(1),
            requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(background)), BlendMode.DIFFERENCE,
        )
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
            W5bBlendCpuOracle.Draw(ColorARGB.White, .5f, BlendMode.MULTIPLY),
            W5bBlendCpuOracle.Draw(ColorARGB.Black, 1f, BlendMode.SRC),
            W5bBlendCpuOracle.Draw(ColorARGB.White, .5f, BlendMode.DIFFERENCE),
        ))
        val black = W5aSolidOpacityCpuOracle.draw(ColorARGB.Black, 1f)
        val expected = WgslFloatEnvelopeV1Oracle.drawDestination(halfWhiteSource(), MaterialPlanRef(1),
            requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(black)), BlendMode.DIFFERENCE)
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, render(picture))
    }

    @Test
    fun `Surface destination read preserves integral scissor and outside destination`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.SolidColor(ColorARGB.Black), antiAlias = false))
            clipRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.White), .5f),
                antiAlias = false, blendMode = BlendMode.DIFFERENCE))
        }
        val pixels = surface.render().pixels
        val black = W5aSolidOpacityCpuOracle.draw(ColorARGB.Black, 1f)
        val inside = WgslFloatEnvelopeV1Oracle.drawDestination(halfWhiteSource(), MaterialPlanRef(1),
            requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(black)), BlendMode.DIFFERENCE)
        for (yI32 in 0 until 4) for (xI32 in 0 until 4) {
            val offsetI32 = (yI32 * 4 + xI32) * 4
            try {
                WgslFloatEnvelopeV1Oracle.assertAdmits(if (xI32 in 1..2 && yI32 in 1..2) inside else black,
                    pixels.copyOfRange(offsetI32, offsetI32 + 4))
            } catch (failure: IllegalArgumentException) {
                throw AssertionError("Pixel ($xI32,$yI32): ${failure.message}", failure)
            }
        }
    }

    private fun halfWhiteSource() = halfSolidSource(ColorF32.of(1f, 1f, 1f, 1f))

    private fun halfSolidSource(color: ColorF32) = MaterialPlanTable.of(listOf(
        MaterialPlanEntry(MaterialProgramPlan.SolidLinearPremulV1,
            MaterialBindingPlan.SolidRgbaF32V1.of(color)),
        MaterialPlanEntry(MaterialProgramPlan.OpacityV1(MaterialProgramPlan.SolidLinearPremulV1),
            MaterialBindingPlan.OpacityF32V1.of(.5f)),
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
