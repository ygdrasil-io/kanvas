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
    fun `Picture replays fixed function DST OVER Solid Opacity`() {
        val background = ColorARGB.Black
        val foreground = ColorARGB.Blue
        val picture = record(background, .015625f, BlendMode.SRC_OVER, foreground, .9921875f, BlendMode.DST_OVER)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        W5bBlendCpuOracle.assertDstOver(background, .015625f, foreground, .9921875f, result.pixels.copyOfRange(0, 4))
    }

    @Test
    fun `Picture replays DST NoOp Solid Opacity`() {
        val background = ColorARGB.White
        val picture = record(background, .5f, BlendMode.SRC_OVER, ColorARGB.Blue, 1f, BlendMode.DST)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        W5bBlendCpuOracle.assertDst(background, .5f, result.pixels.copyOfRange(0, 4))
    }

    private fun record(
        background: ColorARGB,
        backgroundOpacity: Float,
        backgroundMode: BlendMode,
        foreground: ColorARGB,
        foregroundOpacity: Float,
        foregroundMode: BlendMode,
    ) = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).apply {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(background), backgroundOpacity), antiAlias = false, blendMode = backgroundMode))
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(foreground), foregroundOpacity), antiAlias = false, blendMode = foregroundMode))
        }
    }.finishRecordingAsPicture()
}
