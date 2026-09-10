@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
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
        val foreground = ColorARGB.White
        val picture = record(foreground, BlendMode.DST_OVER)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        W5bBlendCpuOracle.assertDstOver(ColorARGB.Transparent, foreground, .9921875f, result.pixels.copyOfRange(0, 4))
        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(foreground, .9921875f, 1f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `Picture replays DST NoOp Solid Opacity`() {
        val picture = record(ColorARGB.White, BlendMode.DST)
        val result = Surface(4, 4).also { surface ->
            surface.canvas { requireNotNull(Picture.fromByteArray(picture.toByteArray())).playback(this) }
        }.render()
        assertContentEquals(UByteArray(4), result.pixels.copyOfRange(0, 4))
    }

    private fun record(foreground: ColorARGB, mode: BlendMode) = PictureRecorder().also { recorder ->
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).apply {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = Shader.Opacity(Shader.SolidColor(foreground), .9921875f), antiAlias = false, blendMode = mode))
        }
    }.finishRecordingAsPicture()
}
