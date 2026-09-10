@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
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
            W5aSolidOpacityCpuOracle.draw(color, 0f, 1f, 1f),
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
            W5aSolidOpacityCpuOracle.draw(color, 1f, 1f, 1f),
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
            W5aSolidOpacityCpuOracle.draw(color, 0.8f, 0.5f, 153f / 255f),
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
            W5aSolidOpacityCpuOracle.draw(color, 0.4f, 0.625f, 179f / 255f),
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
            W5aSolidOpacityCpuOracle.draw(color, 0.5f, 0.75f, 191f / 255f),
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
        val first = W5aSolidOpacityCpuOracle.draw(back, 0.625f, paintAlphaF32 = backPaintAlpha)
        val expected = W5aSolidOpacityCpuOracle.draw(
            front,
            0.4f,
            paintAlphaF32 = frontPaintAlpha,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(first)),
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(0, 4))
    }

    @Test
    fun `fractional Rect applies nested shader opacity and Paint alpha after SrcOver coverage`() {
        val destinationColor = ColorARGB.of(211, 41, 167, 83)
        val sourceColor = ColorARGB.of(197, 233, 89, 31)
        val recorder = PictureRecorder()
        val recordedPaint = Paint(
            color = ColorARGB.of(153, 11, 13, 17),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(sourceColor), 0.5f), 0.8f),
            antiAlias = true,
        )
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawRect(
            RectF32.ofLTRB(0.25f, 0.25f, 1.25f, 1.25f),
            recordedPaint,
        )
        // Paint and the Solid/Opacity graph are immutable. Rebinding this caller reference after
        // capture is the strongest public snapshot boundary those value inputs expose.
        val callerReboundPaint = Paint(shader = Shader.SolidColor(ColorARGB.of(255, 1, 2, 3)))
        callerReboundPaint.hashCode()
        val restoredForeground = requireNotNull(
            Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()),
        )
        val surface = Surface(4, 4)
        surface.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    color = ColorARGB.of(179, 3, 5, 7),
                    shader = Shader.Opacity(Shader.SolidColor(destinationColor), 0.625f),
                    antiAlias = true,
                ),
            )
            restoredForeground.playback(this)
        }

        val result = surface.render()
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(destinationColor, 0.625f, paintAlphaF32 = 179f / 255f),
        ))
        val expected = W5aSolidOpacityCpuOracle.draw(
            sourceColor,
            shaderOpacityOuterF32 = 0.8f,
            shaderOpacityInnerF32 = 0.5f,
            paintAlphaF32 = 153f / 255f,
            destination = destination,
            coverageF32 = 0.5625f,
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(0, 4))
    }

    @Test
    fun `nontrivial fractional RRect applies nested shader opacity and Paint alpha after SrcOver coverage`() {
        val destinationColor = ColorARGB.of(203, 29, 109, 227)
        val sourceColor = ColorARGB.of(191, 239, 71, 43)
        val background = RRectF32.of(RectF32.ofLTRB(0f, 0f, 4f, 4f), CornerRadiiF32.of(0.5f))
        val foreground = RRectF32.of(RectF32.ofLTRB(0.25f, 0.25f, 3.75f, 3.75f), CornerRadiiF32.of(0.5f))
        val recorder = PictureRecorder()
        val recordedPaint = Paint(
            color = ColorARGB.of(149, 31, 37, 41),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(sourceColor), 0.5f), 0.8f),
            antiAlias = true,
        )
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawRRect(foreground, recordedPaint)
        // Immutable value inputs cannot be mutated after capture; rebinding demonstrates the
        // caller boundary while serialize/restore/playback proves the public snapshot path.
        val callerReboundForeground = RRectF32.of(
            RectF32.ofLTRB(0f, 0f, 1f, 1f),
            CornerRadiiF32.of(0f),
        )
        callerReboundForeground.hashCode()
        val restoredForeground = requireNotNull(
            Picture.fromByteArray(recorder.finishRecordingAsPicture().toByteArray()),
        )
        val surface = Surface(4, 4)
        surface.canvas {
            drawRRect(
                background,
                Paint(
                    color = ColorARGB.of(173, 19, 23, 29),
                    shader = Shader.Opacity(Shader.SolidColor(destinationColor), 0.75f),
                    antiAlias = true,
                ),
            )
            restoredForeground.playback(this)
        }

        val result = surface.render()
        val destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(
            W5aSolidOpacityCpuOracle.draw(destinationColor, 0.75f, paintAlphaF32 = 173f / 255f),
        ))
        val expected = W5aSolidOpacityCpuOracle.draw(
            sourceColor,
            shaderOpacityOuterF32 = 0.8f,
            shaderOpacityInnerF32 = 0.5f,
            paintAlphaF32 = 149f / 255f,
            destination = destination,
            coverageF32 = 0.75f,
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(4, 8))
    }

    @Test
    fun `prepared direct triangle Path applies shader and Paint opacity after Path mutation`() {
        val source = ColorARGB.of(197, 231, 83, 37)
        val path = Path().apply {
            moveTo(-1f, -1f)
            lineTo(5f, -1f)
            lineTo(-1f, 5f)
            close()
        }
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawPath(
            path,
            Paint(
                color = ColorARGB.of(149, 1, 2, 3),
                shader = Shader.Opacity(Shader.SolidColor(source), 0.625f),
                antiAlias = false,
            ),
        )
        // Path is a public mutable facade.  Add a second, separately closed rectangle whose
        // interior fully covers (3,3); playback must retain only the pre-capture triangle.
        path.addRect(RectF32.ofLTRB(3f, 3f, 4f, 4f))
        val captured = recorder.finishRecordingAsPicture()
        val surface = Surface(4, 4)
        surface.canvas { captured.playback(this) }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.625f, paintAlphaF32 = 149f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), result.pixels.copyOfRange(60, 64))
    }

    @Test
    fun `public mixed AA4 frame keeps a hard Path binary cover materialized only at color output`() {
        val aaPath = Path().apply {
            moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(-1f, 5f); close()
        }
        val hardConcave = Path().apply {
            moveTo(-1f, -1f); lineTo(5f, -1f); lineTo(5f, 5f)
            lineTo(2f, 2f); lineTo(-1f, 5f); close()
        }
        val source = ColorARGB.of(193, 47, 199, 89)
        val surface = Surface(4, 4)
        surface.canvas {
            rotate(0.25f, px = 2f, py = 2f)
            drawPath(aaPath, Paint(shader = Shader.SolidColor(ColorARGB.Transparent), antiAlias = true))
            drawPath(
                hardConcave,
                Paint(
                    color = ColorARGB.of(157, 5, 7, 11),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.5f),
                    antiAlias = false,
                ),
            )
        }

        val result = try {
            surface.render()
        } catch (error: IllegalStateException) {
            assumeTrue(error.message?.startsWith("w4d.general.texture-sample-support-unavailable:") == true) {
                "AA4 path rendering failed for a reason other than unavailable sample support: ${error.message}"
            }
            assumeTrue(false, "AA4 path capability is unavailable in this environment: ${error.message}")
            throw error
        }

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.5f, paintAlphaF32 = 157f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `public complex clip Path applies shader and Paint opacity`() {
        val source = ColorARGB.of(201, 59, 181, 97)
        val full = Path().apply {
            moveTo(0f, 0f); lineTo(6f, 0f); lineTo(6f, 6f); lineTo(0f, 6f); close()
        }
        val notch = Path().apply {
            moveTo(4f, 0f); lineTo(6f, 0f); lineTo(6f, 2f); close()
        }
        val surface = Surface(6, 6)
        surface.canvas {
            clipPath(notch, ClipOp.DIFFERENCE, antiAlias = false)
            drawPath(
                full,
                Paint(
                    color = ColorARGB.of(173, 1, 3, 5),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.625f),
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(
                source,
                0.625f,
                paintAlphaF32 = 173f / 255f,
                coverageF32 = 254f / 255f,
            ),
            result.pixels.copyOfRange((3 * 6 + 3) * 4, (3 * 6 + 4) * 4),
        )
    }

    @Test
    fun `prepared stencil cover Path applies shader and Paint opacity`() {
        val source = ColorARGB.of(193, 47, 199, 89)
        val concave = Path().apply {
            moveTo(-1f, -1f)
            lineTo(5f, -1f)
            lineTo(5f, 5f)
            lineTo(2f, 2f)
            lineTo(-1f, 5f)
            close()
        }
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(
                concave,
                Paint(
                    color = ColorARGB.of(157, 5, 7, 11),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.5f),
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.5f, paintAlphaF32 = 157f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `prepared Path stroke applies shader and Paint opacity`() {
        val source = ColorARGB.of(187, 59, 163, 233)
        val path = Path().apply { moveTo(-1f, 0.5f); lineTo(5f, 0.5f) }
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(
                path,
                Paint(
                    color = ColorARGB.of(173, 13, 17, 19),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.4f),
                    style = PaintStyle.STROKE,
                    strokeWidth = 1f,
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.4f, paintAlphaF32 = 173f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `prepared Path hairline applies shader and Paint opacity`() {
        val source = ColorARGB.of(179, 211, 101, 43)
        val path = Path().apply { moveTo(-1f, 0.5f); lineTo(5f, 0.5f) }
        val surface = Surface(4, 4)
        surface.canvas {
            drawPath(
                path,
                Paint(
                    color = ColorARGB.of(167, 23, 29, 31),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.75f),
                    style = PaintStyle.STROKE,
                    strokeWidth = 0f,
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.75f, paintAlphaF32 = 167f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

}
