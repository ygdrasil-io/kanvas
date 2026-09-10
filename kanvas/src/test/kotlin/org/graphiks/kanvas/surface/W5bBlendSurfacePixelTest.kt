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
        val background = ColorARGB.Red
        val foreground = ColorARGB.Blue
        val picture = record(background, BlendMode.SRC_OVER, foreground, BlendMode.DST_OVER)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        W5bBlendCpuOracle.assertDstOver(background, 1f, foreground, .9921875f, result.pixels.copyOfRange(0, 4))
    }

    @Test
    fun `Picture replays DST NoOp Solid Opacity`() {
        val background = ColorARGB.Red
        val picture = record(background, BlendMode.SRC_OVER, ColorARGB.Blue, BlendMode.DST)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        W5bBlendCpuOracle.assertDst(background, 1f, result.pixels.copyOfRange(0, 4))
    }

    private fun record(
        background: ColorARGB,
        backgroundMode: BlendMode,
        foreground: ColorARGB,
        foregroundMode: BlendMode,
    ) = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).apply {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(background), 1f), antiAlias = false, blendMode = backgroundMode))
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(foreground), if (foregroundMode == BlendMode.DST_OVER) .9921875f else 1f), antiAlias = false, blendMode = foregroundMode))
        }
    }.finishRecordingAsPicture()
}
