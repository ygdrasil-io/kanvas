@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
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
