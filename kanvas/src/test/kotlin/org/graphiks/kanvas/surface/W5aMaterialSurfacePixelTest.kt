@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory

class W5aMaterialSurfacePixelTest {
    @AfterEach
    fun disposeGpuRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `public Opacity zero canonicalizes to transparent Rect pixels`() {
        val color = ColorARGB.of(211, 173, 71, 29)
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(shader = Shader.Opacity(Shader.SolidColor(color), 0f), antiAlias = false),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.source(color, 0f, 1f, 1f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `public Opacity one preserves Solid Rect pixels`() {
        val color = ColorARGB.of(211, 173, 71, 29)
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(shader = Shader.Opacity(Shader.SolidColor(color), 1f), antiAlias = false),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.source(color, 1f, 1f, 1f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `nested public opacity and Paint alpha render an integral Rect within the W5a numeric envelope`() {
        val color = ColorARGB.of(153, 203, 101, 47)
        val paint = Paint(
            color = ColorARGB.of(153, 1, 2, 3),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(color), 0.5f), 0.8f),
            antiAlias = false,
        )
        val surface = Surface(4, 4)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint) }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.source(color, 0.8f, 0.5f, 153f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `nontrivial opacity and Picture round trip preserve the public material result`() {
        val color = ColorARGB.of(221, 101, 203, 47)
        val recordedPaint = Paint(
            color = ColorARGB.of(179, 9, 8, 7),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(color), 0.625f), 0.4f),
            antiAlias = false,
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawRect(
            RectF32.ofLTRB(0f, 0f, 4f, 4f),
            recordedPaint,
        )
        val restored = requireNotNull(Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()))
        val surface = Surface(4, 4)
        surface.canvas { restored.playback(this) }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.source(color, 0.4f, 0.625f, 179f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `recording preserves the original immutable opacity graph`() {
        val color = ColorARGB.of(171, 31, 181, 217)
        val paint = Paint(
            color = ColorARGB.of(191, 0, 0, 0),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(color), 0.75f), 0.5f),
            antiAlias = false,
        )
        val surface = Surface(4, 4)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), paint) }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.source(color, 0.5f, 0.75f, 191f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `overlapping planned Rect materials source over the attachment through the numeric envelope`() {
        val back = ColorARGB.of(181, 25, 153, 229)
        val front = ColorARGB.of(203, 231, 83, 31)
        val backPaintAlpha = 137f / 255f
        val frontPaintAlpha = 193f / 255f
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    color = ColorARGB.of(137, 1, 2, 3),
                    shader = Shader.Opacity(Shader.SolidColor(back), 0.625f),
                    antiAlias = false,
                ),
            )
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    color = ColorARGB.of(193, 4, 5, 6),
                    shader = Shader.Opacity(Shader.SolidColor(front), 0.4f),
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()
        val first = W5aSolidOpacityCpuOracle.srcOver(floatArrayOf(0f, 0f, 0f, 0f), back, 0.625f, backPaintAlpha)
        val expected = W5aSolidOpacityCpuOracle.encode(
            W5aSolidOpacityCpuOracle.srcOver(first, front, 0.4f, frontPaintAlpha),
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(0, 4))
    }
}
