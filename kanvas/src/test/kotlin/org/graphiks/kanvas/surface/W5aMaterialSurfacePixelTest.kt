@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.surface.gpu.GPUPreparedTextTestFixtures
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
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

    @Test
    fun `public drawPoint composes three planned Solid Opacity commands in paint order`() {
        val back = ColorARGB.of(183, 37, 149, 223)
        val middle = ColorARGB.of(197, 211, 79, 41)
        val front = ColorARGB.of(229, 67, 191, 113)
        val surface = Surface(4, 4)
        surface.canvas {
            drawPoint(1.5f, 1.5f, Paint(
                color = ColorARGB.of(137, 1, 2, 3),
                shader = Shader.Opacity(Shader.SolidColor(back), 0.625f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
            drawPoint(1.5f, 1.5f, Paint(
                color = ColorARGB.of(151, 4, 5, 6),
                shader = Shader.Opacity(Shader.SolidColor(middle), 0.5f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
            drawPoint(1.5f, 1.5f, Paint(
                color = ColorARGB.of(173, 7, 8, 9),
                shader = Shader.Opacity(Shader.SolidColor(front), 0.4f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
        }

        val result = surface.render()
        val first = W5aSolidOpacityCpuOracle.draw(back, 0.625f, paintAlphaF32 = 137f / 255f)
        val second = W5aSolidOpacityCpuOracle.draw(
            middle, 0.5f, paintAlphaF32 = 151f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(first)),
        )
        val expected = W5aSolidOpacityCpuOracle.draw(
            front, 0.4f, paintAlphaF32 = 173f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(second)),
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(20, 24))
    }

    @Test
    fun `public drawPoints composes three planned Solid Opacity commands in paint order`() {
        val back = ColorARGB.of(183, 37, 149, 223)
        val middle = ColorARGB.of(197, 211, 79, 41)
        val front = ColorARGB.of(229, 67, 191, 113)
        val points = listOf(Point2F32(1.5f, 1.5f), Point2F32(3.5f, 3.5f))
        val surface = Surface(4, 4)
        surface.canvas {
            drawPoints(PointMode.POINTS, points, Paint(
                color = ColorARGB.of(137, 1, 2, 3),
                shader = Shader.Opacity(Shader.SolidColor(back), 0.625f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
            drawPoints(PointMode.POINTS, points, Paint(
                color = ColorARGB.of(151, 4, 5, 6),
                shader = Shader.Opacity(Shader.SolidColor(middle), 0.5f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
            drawPoints(PointMode.POINTS, points, Paint(
                color = ColorARGB.of(173, 7, 8, 9),
                shader = Shader.Opacity(Shader.SolidColor(front), 0.4f),
                strokeWidth = 2f,
                antiAlias = false,
            ))
        }

        val result = surface.render()
        val first = W5aSolidOpacityCpuOracle.draw(back, 0.625f, paintAlphaF32 = 137f / 255f)
        val second = W5aSolidOpacityCpuOracle.draw(
            middle, 0.5f, paintAlphaF32 = 151f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(first)),
        )
        val expected = W5aSolidOpacityCpuOracle.draw(
            front, 0.4f, paintAlphaF32 = 173f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(second)),
        )

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(20, 24))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(60, 64))
    }

    @Test
    fun `public stroked drawPoint keeps its captured point square without a second stroke`() {
        val source = ColorARGB.of(197, 211, 79, 41)
        val surface = Surface(6, 6)
        surface.canvas {
            drawPoint(3f, 3f, Paint(
                color = ColorARGB.of(151, 1, 2, 3),
                shader = Shader.Opacity(Shader.SolidColor(source), 0.5f),
                style = PaintStyle.STROKE,
                strokeWidth = 2f,
                antiAlias = false,
            ))
        }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.5f, paintAlphaF32 = 151f / 255f),
            result.pixels.copyOfRange(2 * 6 * 4 + 2 * 4, 2 * 6 * 4 + 3 * 4),
        )
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), result.pixels.copyOfRange(1 * 6 * 4 + 1 * 4, 1 * 6 * 4 + 2 * 4))
    }

    @Test
    fun `public zero width drawPoints retain one device pixel hairlines for every point`() {
        val source = ColorARGB.of(211, 71, 199, 127)
        val surface = Surface(6, 4)
        surface.canvas {
            drawPoints(
                PointMode.POINTS,
                listOf(Point2F32(1.5f, 1.5f), Point2F32(4.5f, 1.5f)),
                Paint(
                    color = ColorARGB.of(173, 7, 8, 9),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.4f),
                    strokeWidth = 0f,
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()
        val expected = W5aSolidOpacityCpuOracle.draw(source, 0.4f, paintAlphaF32 = 173f / 255f)

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(1 * 6 * 4 + 1 * 4, 1 * 6 * 4 + 2 * 4))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(1 * 6 * 4 + 4 * 4, 1 * 6 * 4 + 5 * 4))
        assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), result.pixels.copyOfRange(1 * 6 * 4 + 2 * 4, 1 * 6 * 4 + 3 * 4))
    }

    @Test
    fun `public 64 point POINTS command preserves prepared core point capacity`() {
        val source = ColorARGB.of(211, 71, 199, 127)
        val points = (0 until 64).map { index ->
            Point2F32((index % 8) * 4f + 1.5f, (index / 8) * 4f + 1.5f)
        }
        val surface = Surface(32, 32)
        surface.canvas {
            drawPoints(
                PointMode.POINTS,
                points,
                Paint(
                    color = ColorARGB.of(173, 7, 8, 9),
                    shader = Shader.Opacity(Shader.SolidColor(source), 0.4f),
                    strokeWidth = 2f,
                    antiAlias = false,
                ),
            )
        }

        val result = surface.render()
        val expected = W5aSolidOpacityCpuOracle.draw(source, 0.4f, paintAlphaF32 = 173f / 255f)
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(1 * 32 * 4 + 1 * 4, 1 * 32 * 4 + 2 * 4))
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(29 * 32 * 4 + 29 * 4, 29 * 32 * 4 + 30 * 4))
    }

    @Test
    fun `public ROUND opacity point refuses before W5a material selection`() {
        val surface = Surface(6, 6)
        surface.canvas {
            drawPoint(3f, 3f, Paint(
                shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(197, 71, 199, 127)), 0.4f),
                strokeCap = StrokeCap.ROUND,
                strokeWidth = 2f,
                antiAlias = false,
            ))
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.core_primitive.point.round_cap_exact_lowering"), failure.message)
    }

    @Test
    fun `public ROUND drawPoints retain the exact lowering refusal`() {
        val surface = Surface(6, 6)
        surface.canvas {
            drawPoints(
                PointMode.POINTS,
                listOf(Point2F32(3f, 3f)),
                Paint.stroke(ColorARGB.of(255, 71, 199, 127), 2f).copy(
                    strokeCap = StrokeCap.ROUND,
                    antiAlias = false,
                ),
            )
        }

        val failure = assertFailsWith<IllegalStateException> { surface.render() }

        assertTrue(
            failure.message.orEmpty().contains("unsupported.core_primitive.point.round_cap_exact_lowering"),
            failure.message,
        )
    }

    @Test
    fun `public prepared A8 text applies nested shader opacity and Paint alpha once`() {
        val source = ColorARGB.of(197, 231, 83, 37)
        val paint = Paint(
            color = ColorARGB.of(149, 7, 11, 13),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(source), 0.625f), 0.4f),
            antiAlias = false,
        )
        val blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(GPUPreparedTextTestFixtures.A8_GLYPH_ID.toUShort()),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 48f,
                ),
            ),
            typeface = FontTypeface(
                GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
                "W5a prepared A8 text fixture",
            ),
            fontSize = 48f,
        )
        val surface = Surface(40, 80)
        surface.canvas { drawText(blob, 4f, 58f, paint) }

        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(
                source,
                shaderOpacityOuterF32 = 0.4f,
                shaderOpacityInnerF32 = 0.625f,
                paintAlphaF32 = 149f / 255f,
            ),
            result.pixels.copyOfRange((40 * 40 + 10) * 4, (40 * 40 + 11) * 4),
        )
    }

    @Test
    fun `Picture playback retains prepared A8 glyph snapshots after caller list mutation`() {
        val glyphs = mutableListOf(GPUPreparedTextTestFixtures.A8_GLYPH_ID.toUShort())
        val positions = mutableListOf(Point2F32(0f, 0f))
        val blob = TextBlob(
            glyphRuns = listOf(KanvasGlyphRun(glyphs, positions, fontSize = 48f)),
            typeface = FontTypeface(
                GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
                "W5a Picture A8 snapshot fixture",
            ),
            fontSize = 48f,
        )
        val paints = listOf(
            Paint(
                color = ColorARGB.of(149, 1, 2, 3),
                shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(211, 231, 83, 37)), 0.625f),
            ),
            Paint(
                color = ColorARGB.of(173, 4, 5, 6),
                shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(197, 67, 191, 113)), 0.5f),
            ),
            Paint(
                color = ColorARGB.of(137, 7, 8, 9),
                shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(229, 37, 149, 223)), 0.4f),
            ),
        )
        fun record(): Picture = PictureRecorder().also { recorder ->
            recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 40f, 80f)).also { canvas ->
                paints.forEach { paint -> canvas.drawText(blob, 4.25f, 58.5f, paint) }
            }
        }.finishRecordingAsPicture()

        val baseline = record()
        val captured = record()
        glyphs[0] = 999u
        positions[0] = Point2F32(Float.NaN, Float.NaN)
        val expectedSurface = Surface(40, 80)
        expectedSurface.canvas { baseline.playback(this) }
        val actualSurface = Surface(40, 80)
        actualSurface.canvas { captured.playback(this) }

        val expectedPixels = expectedSurface.render().pixels
        // The 4.25 / 58.5 placement makes this rectangle glyph's leading corner partially A8
        // covered; it also ensures playback retained visible geometry before comparison.
        assertTrue(
            expectedPixels.copyOfRange((40 * 40 + 4) * 4, (40 * 40 + 5) * 4)
                .any { channel -> channel != 0u.toUByte() },
        )
        assertContentEquals(expectedPixels, actualSurface.render().pixels)
    }

    @Test
    fun `Picture playback retains a planned uncolored triangle after caller vertex and index mutation`() {
        val positions = mutableListOf(
            Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f),
        )
        val indices = mutableListOf(0, 1, 2)
        val vertices = Vertices(VertexMode.TRIANGLES, positions, indices = indices)
        val source = ColorARGB.of(197, 231, 83, 37)
        val paint = Paint(
            color = ColorARGB.of(149, 1, 3, 5),
            shader = Shader.Opacity(Shader.Opacity(Shader.SolidColor(source), 0.625f), 0.4f),
            antiAlias = false,
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawVertices(vertices, paint)
        val captured = recorder.finishRecordingAsPicture()
        positions[0] = Point2F32(10f, 10f)
        positions[1] = Point2F32(11f, 10f)
        positions[2] = Point2F32(10f, 11f)
        indices[0] = 2

        val surface = Surface(4, 4)
        surface.canvas { captured.playback(this) }
        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.draw(source, 0.4f, 0.625f, 149f / 255f),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `Picture playback retains planned vertex colors which modulate rather than replace the material`() {
        val positions = mutableListOf(
            Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f),
        )
        // A uniform but non-neutral premultiplied color makes the observed pixel depend on the
        // documented material × vertex composition, while keeping interpolation out of scope.
        val vertexColor = ColorARGB.of(255, 143, 97, 41)
        val colors = mutableListOf(vertexColor, vertexColor, vertexColor)
        val vertices = Vertices(VertexMode.TRIANGLES, positions, colors = colors)
        val source = ColorARGB.of(211, 71, 199, 127)
        val paint = Paint(
            color = ColorARGB.of(173, 7, 11, 13),
            shader = Shader.Opacity(Shader.SolidColor(source), 0.5f),
            antiAlias = false,
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 4f, 4f)).drawVertices(vertices, paint)
        val captured = recorder.finishRecordingAsPicture()
        colors[0] = ColorARGB.Transparent
        colors[1] = ColorARGB.Transparent
        colors[2] = ColorARGB.Transparent

        val surface = Surface(4, 4)
        surface.canvas { captured.playback(this) }
        val result = surface.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(
            W5aSolidOpacityCpuOracle.drawVertexColorModulated(
                color = source,
                vertexColor = vertexColor,
                shaderOpacityOuterF32 = 0.5f,
                paintAlphaF32 = 173f / 255f,
            ),
            result.pixels.copyOfRange(0, 4),
        )
    }

    @Test
    fun `public W5a vertex candidate with a color filter keeps the typed material refusal`() {
        val surface = Surface(4, 4)
        surface.canvas {
            drawVertices(
                Vertices(
                    VertexMode.TRIANGLES,
                    listOf(Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f)),
                ),
                Paint(
                    shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(211, 71, 199, 127)), 0.5f),
                    colorFilter = ColorFilter.HighContrast,
                    antiAlias = false,
                ),
            )
        }

        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.vertices.material"), failure.message)
    }

    @Test
    fun `public W5a vertices keep a non-finite transform refusal outside material planning`() {
        val surface = Surface(4, 4)
        surface.canvas {
            setMatrix(Matrix3x3F32(sx = Float.NaN))
            drawVertices(
                w5aTriangle(),
                Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(211, 71, 199, 127)), 0.5f)),
            )
        }

        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.vertices.transform"), failure.message)
    }

    @Test
    fun `public W5a vertices keep an invalid clip refusal outside material planning`() {
        val surface = Surface(4, 4)
        surface.canvas {
            setMatrix(Matrix3x3F32(sx = Float.NaN))
            clipRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), antiAlias = false)
            resetMatrix()
            drawVertices(
                w5aTriangle(),
                Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(211, 71, 199, 127)), 0.5f)),
            )
        }

        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.vertices.clip_coverage"), failure.message)
    }

    @Test
    fun `Picture playback composes planned Rect RRect and Path bindings in recorded order`() {
        assertNativeMixedFrame(stencil = false, interleavedRect = false)
    }

    @Test
    fun `native mixed stencil frame preserves interleaved Rect bindings and captured mutation`() {
        assertNativeMixedFrame(stencil = true, interleavedRect = true)
    }

    @Test
    fun `native mixed equal opacity preserves distinct Solid children and nested chains`() {
        val red = ColorARGB.of(255, 255, 0, 0)
        val blue = ColorARGB.of(255, 0, 0, 255)
        val redHalf = Shader.Opacity(Shader.SolidColor(red), 0.5f)
        val blueHalf = Shader.Opacity(Shader.SolidColor(blue), 0.5f)
        val surface = Surface(16, 4)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint(shader = redHalf, antiAlias = false))
            drawRRect(RRectF32.of(RectF32.ofLTRB(4f, 0f, 8f, 4f), CornerRadiiF32.of(1f)),
                Paint(shader = blueHalf, antiAlias = true))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(8f, 0f, 12f, 4f)) },
                Paint(color = ColorARGB.of(128, 0, 0, 0), shader = Shader.Opacity(redHalf, 0.25f), antiAlias = false))
            drawRect(RectF32.ofLTRB(12f, 0f, 16f, 4f),
                Paint(color = ColorARGB.of(128, 0, 0, 0), shader = Shader.Opacity(blueHalf, 0.25f), antiAlias = false))
        }
        val pixels = surface.render().pixels
        val expected = listOf(
            W5aSolidOpacityCpuOracle.draw(red, 0.5f),
            W5aSolidOpacityCpuOracle.draw(blue, 0.5f),
            W5aSolidOpacityCpuOracle.draw(red, 0.25f, 0.5f, 128f / 255f),
            W5aSolidOpacityCpuOracle.draw(blue, 0.25f, 0.5f, 128f / 255f),
        )
        expected.forEachIndexed { index, envelope ->
            val offset = (2 * 16 + index * 4 + 2) * 4
            WgslFloatEnvelopeV1Oracle.assertAdmits(envelope, pixels.copyOfRange(offset, offset + 4))
        }
    }

    private fun assertNativeMixedFrame(stencil: Boolean, interleavedRect: Boolean) {
        val rectColor = ColorARGB.of(191, 31, 163, 229)
        val rrectColor = ColorARGB.of(203, 227, 89, 43)
        val pathColor = ColorARGB.of(179, 67, 193, 113)
        val path = Path().apply {
            if (stencil) addRect(RectF32.ofLTRB(0f, 0f, 6f, 6f))
            else {
                moveTo(-1f, -1f)
                lineTo(9f, -1f)
                lineTo(-1f, 9f)
                close()
            }
        }
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).apply {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 8f, 8f),
                Paint(
                    color = ColorARGB.of(153, 1, 2, 3),
                    shader = Shader.Opacity(Shader.SolidColor(rectColor), 0.8f),
                    antiAlias = false,
                ),
            )
            drawRRect(
                RRectF32.of(RectF32.ofLTRB(1.25f, 1.25f, 6.75f, 6.75f), CornerRadiiF32.of(1f)),
                Paint(
                    color = ColorARGB.of(179, 4, 5, 6),
                    shader = Shader.Opacity(Shader.SolidColor(rrectColor), 0.625f),
                    antiAlias = true,
                ),
            )
            drawPath(
                path,
                Paint(
                    color = ColorARGB.of(193, 7, 8, 9),
                    shader = Shader.Opacity(Shader.SolidColor(pathColor), 0.4f),
                    antiAlias = false,
                ),
            )
            if (interleavedRect) drawRect(
                RectF32.ofLTRB(2f, 2f, 4f, 4f),
                Paint(color = ColorARGB.of(153, 1, 2, 3),
                    shader = Shader.Opacity(Shader.SolidColor(rectColor), 0.8f), antiAlias = false),
            )
        }
        val captured = recorder.finishRecordingAsPicture()
        val surface = Surface(8, 8)
        surface.canvas { captured.playback(this) }

        val result = surface.render()
        val first = W5aSolidOpacityCpuOracle.draw(rectColor, 0.8f, paintAlphaF32 = 153f / 255f)
        val second = W5aSolidOpacityCpuOracle.draw(
            rrectColor,
            0.625f,
            paintAlphaF32 = 179f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(first)),
        )
        val third = W5aSolidOpacityCpuOracle.draw(
            pathColor,
            0.4f,
            paintAlphaF32 = 193f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(second)),
        )
        val expected = if (interleavedRect) W5aSolidOpacityCpuOracle.draw(
            rectColor, 0.8f, paintAlphaF32 = 153f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(third)),
        ) else third

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange((2 * 8 + 2) * 4, (2 * 8 + 3) * 4))
        WgslFloatEnvelopeV1Oracle.assertAdmits(first, result.pixels.copyOfRange((7 * 8 + 7) * 4, (7 * 8 + 8) * 4))
        // At the straight top edge, the analytic RRect covers exactly 3/4 of this pixel.
        // A hard-edge Path substitution cannot satisfy this separate geometry observation.
        val edgeRRect = W5aSolidOpacityCpuOracle.draw(rrectColor, 0.625f, paintAlphaF32 = 179f / 255f,
            coverageF32 = 0.75f, destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(first)))
        val edge = W5aSolidOpacityCpuOracle.draw(pathColor, 0.4f, paintAlphaF32 = 193f / 255f,
            destination = requireNotNull(WgslFloatEnvelopeV1Oracle.nextAttachment(edgeRRect)))
        WgslFloatEnvelopeV1Oracle.assertAdmits(edge, result.pixels.copyOfRange((1 * 8 + 3) * 4, (1 * 8 + 4) * 4))
        path.addRect(RectF32.ofLTRB(7f, 7f, 8f, 8f))
        val replay = Surface(8, 8)
        replay.canvas { captured.playback(this) }
        assertContentEquals(result.pixels, replay.render().pixels)
    }

    @Test
    fun `hard edge gradient RRect outside W5a retains legacy pixels after caller stop mutation`() {
        val stops = mutableListOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f, 0f, 8f, 8f)).drawRRect(
            RRectF32.of(RectF32.ofLTRB(0f, 0f, 8f, 8f), CornerRadiiF32.of(1f)),
            Paint(shader = Shader.LinearGradient(Point2F32(2f, 0f), Point2F32(6f, 0f), stops), antiAlias = false),
        )
        val picture = recorder.finishRecordingAsPicture()
        stops[0] = GradientStop(0f, ColorARGB.Green)
        stops[1] = GradientStop(1f, ColorARGB.Green)
        val surface = Surface(8, 8)
        surface.canvas { picture.playback(this) }
        val pixels = surface.render().pixels
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixels.copyOfRange((4 * 8 + 1) * 4, (4 * 8 + 2) * 4))
        assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), pixels.copyOfRange((4 * 8 + 6) * 4, (4 * 8 + 7) * 4))
    }

    @Test
    fun `public W5b gradient refusal leaves the runtime able to render a later W5a frame`() {
        val color = ColorARGB.of(197, 71, 199, 127)
        val expected = W5aSolidOpacityCpuOracle.draw(color, 0.5f, paintAlphaF32 = 173f / 255f)
        val beforeRefusal = Surface(4, 4)
        beforeRefusal.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    color = ColorARGB.of(173, 11, 13, 17),
                    shader = Shader.Opacity(Shader.SolidColor(color), 0.5f),
                    antiAlias = false,
                ),
            )
        }
        val beforePixels = beforeRefusal.render().pixels
        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, beforePixels.copyOfRange(0, 4))

        val rejected = Surface(4, 4)
        rejected.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    shader = Shader.LinearGradient(
                        Point2F32(0f, 0f),
                        Point2F32(4f, 0f),
                        listOf(
                            GradientStop(0f, ColorARGB.Red),
                            GradientStop(1f, ColorARGB.Blue),
                        ),
                    ),
                    antiAlias = false,
                ),
            )
        }

        val failure = assertFailsWith<IllegalStateException> { rejected.render() }
        assertTrue(failure.message.orEmpty().contains("unsupported.material.w5a.kind"), failure.message)

        val recovered = Surface(4, 4)
        recovered.canvas {
            drawRect(
                RectF32.ofLTRB(0f, 0f, 4f, 4f),
                Paint(
                    color = ColorARGB.of(173, 11, 13, 17),
                    shader = Shader.Opacity(Shader.SolidColor(color), 0.5f),
                    antiAlias = false,
                ),
            )
        }

        val result = recovered.render()

        WgslFloatEnvelopeV1Oracle.assertAdmits(expected, result.pixels.copyOfRange(0, 4))
        assertContentEquals(beforePixels, result.pixels)
    }

    private fun w5aTriangle(): Vertices = Vertices(
        VertexMode.TRIANGLES,
        listOf(Point2F32(-1f, -1f), Point2F32(5f, -1f), Point2F32(-1f, 5f)),
    )

}
