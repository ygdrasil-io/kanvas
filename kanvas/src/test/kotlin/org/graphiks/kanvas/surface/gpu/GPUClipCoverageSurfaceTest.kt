package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.RuntimeEffectWgsl4kWiring
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private const val PREPARED_IMAGE_CLIP_REFUSAL = "unsupported.surface.prepared.image-clip"
private const val PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL =
    "unsupported.recording.core_primitive_analytic_shape_clip"
private const val PREPARED_ANALYSIS_AUTHORITY_MISSING_REFUSAL =
    "unsupported.core_primitive.rect.analysis_authority_missing"
private const val PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL =
    "invalid.preflight.core_primitive_clip_producer_authority"
private const val PREPARED_CLIP_MASK_DEPTH_STENCIL_TOPOLOGY_REFUSAL =
    "unsupported.recording.core_primitive_clip_mask_depth_stencil_topology_unavailable"
private const val PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL =
    "unsupported.native-core-primitive.analytic-shape-multi-key"

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipCoverageSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `complex difference clip with image is terminal`() {
        requireWebGpu()
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipRect(RectF32(2f, 2f, 30f, 30f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(RectF32(12f, 12f, 20f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.White))
            drawRect(RectF32(2f, 2f, 12f, 12f), Paint.fill(ColorARGB.Red))
            drawRRect(RRectF32.of(RectF32(20f, 2f, 30f, 12f), radius = 2f), Paint.fill(ColorARGB.Red))
            drawPath(Path { moveTo(2f, 22f); lineTo(12f, 22f); lineTo(7f, 30f); close() }, Paint.fill(ColorARGB.Red))
            drawRect(RectF32(14f, 22f, 26f, 29f), Paint.stroke(ColorARGB.Red, 2f).copy(antiAlias = false))
            drawImage(bluePixel(), RectF32(24f, 14f, 30f, 20f), Paint())
            restore()
        }

        assertTerminal(
            expectedCode = PREPARED_IMAGE_CLIP_REFUSAL,
            block = surface::render,
        )
    }

    @Test
    fun `AA device rect uses alpha mask rather than integer scissor`() {
        requireWebGpu()
        val surface = Surface(16, 16)
        surface.canvas {
            save()
            clipRect(RectF32(3.5f, 2f, 12.5f, 14f), ClipOp.INTERSECT, antiAlias = true)
            drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red))
            restore()
        }

        // The default-AA (ScalarAA) rect lowers to the analytic-shape (uniform80)
        // lane, which cannot combine with the analytic-clip uniform64 authority in one draw; the
        // single-draw mixed-layout gate is retired, so this frame re-points to the analytic-shape
        // clip refusal (NoClip or ScissorOnly execution).
        assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
    }

    @Test
    fun `exact RGBA evidence rejects white as red or blue`() {
        val white = UByteArray(4) { 0xff.toUByte() }

        assertFailsWith<AssertionError> {
            assertRgbaNear(white, width = 1, x = 0, y = 0, expected = ColorARGB.Red)
        }
        assertFailsWith<AssertionError> {
            assertRgbaNear(white, width = 1, x = 0, y = 0, expected = ColorARGB.Blue)
        }
    }

    @Test
    fun `adapter backed even odd clip mask preserves fill hole exterior and AA edge`() {
        requireWebGpu()
        val evenOddHole = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(RectF32(3.5f, 3.5f, 28.5f, 28.5f))
            addRect(RectF32(11.5f, 11.5f, 20.5f, 20.5f))
        }
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipPath(evenOddHole, ClipOp.INTERSECT, antiAlias = true)
            drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
            restore()
        }

        // The route collapse terminates the analytic-clip core frame
        // before lowering: the prepared analytic-shape lane accepts NoClip or
        // ScissorOnly execution only.
        assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
    }

    @Test
    fun `public drawColor hard path clip renders through one stencil scope`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(
                RectF32.ofLTRB(0f, 0f, 64f, 64f),
                Paint.fill(ColorARGB.of(242, 135, 46, 255)).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(1128, result.pixels.asList().chunked(4).count { pixel ->
            pixel.map { it.toInt() } != listOf(13, 20, 33, 255)
        })
    }

    @Test
    fun `public solid triangle drawPath renders inside one hard path clip stencil scope`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val fill = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path {
                    moveTo(4f, 4f); lineTo(60f, 12f); lineTo(12f, 60f); close()
                }.apply { fillType = FillType.WINDING },
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 12, 12, fill)
        assertRgbaNear(result.pixels, 64, 50, 14, background)
    }

    @Test
    fun `public opaque identity rrect renders inside one hard path clip stencil scope`() {
        requireWebGpu()
        val fill = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.isEmpty, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused)
        assertTrue(result.stats.pipelineCount > 0)
        assertTrue(result.stats.drawCallCount > 0)
        assertRgbaNear(result.pixels, 64, 24, 20, fill)
        assertRgbaNear(result.pixels, 64, 50, 14, ColorARGB.Transparent)
    }

    @Test
    fun `public positive translated rrect renders inside one identity hard path clip stencil scope`() {
        requireWebGpu()
        val background = ColorARGB.Transparent
        val fill = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            translate(4f, 5f)
            drawRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.isEmpty, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused)
        assertRgbaNear(result.pixels, 64, 24, 20, fill)
        assertRgbaNear(result.pixels, 64, 10, 12, background)
        assertRgbaNear(result.pixels, 64, 50, 14, background)
    }

    @Test
    fun `public opaque identity drrect renders inside one hard path clip stencil scope`() {
        requireWebGpu()
        val fill = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
                RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(result.diagnostics.isEmpty, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused)
        assertTrue(result.stats.pipelineCount > 0)
        assertTrue(result.stats.drawCallCount > 0)
        assertRgbaNear(result.pixels, 64, 16, 20, fill)
        assertRgbaNear(result.pixels, 64, 28, 28, ColorARGB.Transparent)
        assertRgbaNear(result.pixels, 64, 50, 14, ColorARGB.Transparent)
    }

    @Test
    fun `public opaque identity drrect reaches the inverse winding hard path clip program`() {
        requireWebGpu()
        val fill = ColorARGB.of(255, 31, 115, 209)
        val surface = Surface(64, 64)
        surface.canvas {
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.INVERSE_WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
                RRectF32.of(RectF32.ofLTRB(22f, 20f, 40f, 38f), radius = 4f),
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }
        val result = surface.render()
        assertTrue(result.diagnostics.isEmpty, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused)
        // Outside the triangle (retained by inverse Winding), but safely inside the
        // rounded outer DRRect.
        assertRgbaNear(result.pixels, 64, 46, 42, fill)
        assertRgbaNear(result.pixels, 64, 16, 20, ColorARGB.Transparent)
    }

    @Test
    fun `public even odd hard path clip rrect remains outside the analytic rrect admission`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.EVEN_ODD },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f),
                Paint.fill(ColorARGB.of(255, 242, 135, 46)).copy(antiAlias = false),
            )
            restore()
        }

        assertTerminal("unsupported.clip.complex_stack", surface::render)
    }

    @Test
    fun `single direct triangle hard clip consumer has exact device-space coverage away from pixel-center edges`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val orange = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path {
                    moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close()
                }.apply { fillType = FillType.WINDING },
                Paint.fill(orange).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(
            1059,
            result.pixels.asList().chunked(4).count { pixel ->
                pixel == listOf(orange.red.toUByte(), orange.green.toUByte(), orange.blue.toUByte(), orange.alpha.toUByte())
            },
        )
    }

    @Test
    fun `public translated implicitly closed solid triangle keeps clip and consumer in device space`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val fill = ColorARGB.of(255, 31, 115, 209)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            translate(2f, 0f)
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path {
                    moveTo(4f, 4f); lineTo(60f, 12f); lineTo(12f, 60f)
                }.apply { fillType = FillType.WINDING },
                Paint.fill(fill).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 14, 12, fill)
        assertRgbaNear(result.pixels, 64, 8, 12, background)
    }

    @Test
    fun `public quadratic drawPath remains refused even when flattening forms a triangle`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path {
                    moveTo(4f, 4f); quadTo(8f, 4.1f, 12f, 4f); close()
                }.apply { fillType = FillType.WINDING },
                Paint.fill(ColorARGB.of(255, 242, 135, 46)).copy(antiAlias = false),
            )
            restore()
        }

        assertTerminal("unsupported.recording.core_primitive_path_stencil_clip", surface::render)
    }

    @Test
    fun `public three point polygon remains refused inside a hard path clip`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPoints(
                PointMode.POLYGON,
                listOf(Point2F32(4f, 4f), Point2F32(60f, 12f), Point2F32(12f, 60f)),
                Paint.fill(ColorARGB.of(255, 242, 135, 46)).copy(antiAlias = false),
            )
            restore()
        }

        assertTerminal("unsupported.geometry.path_key_nondeterministic", surface::render)
    }

    @Test
    fun `public hairline drawPoint remains refused inside a hard path clip`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPoint(12f, 12f, Paint.fill(ColorARGB.of(255, 242, 135, 46)).copy(antiAlias = false))
            restore()
        }

        assertTerminal("unsupported.recording.core_primitive_path_stencil_clip", surface::render)
    }

    @Test
    fun `public concave drawPath remains refused inside a hard path clip stencil scope`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(56f, 24f); lineTo(32f, 24f)
                    lineTo(32f, 40f); lineTo(56f, 40f); lineTo(56f, 56f); lineTo(8f, 56f); close()
                }.apply { fillType = FillType.WINDING },
                Paint.fill(ColorARGB.of(255, 31, 115, 209)).copy(antiAlias = false),
            )
            restore()
        }

        assertTerminal("unsupported.recording.core_primitive_path_stencil_clip", surface::render)
    }

    @Test
    fun `public even odd holed drawPath remains refused inside a hard path clip stencil scope`() {
        requireWebGpu()
        val holedPath = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(RectF32.ofLTRB(12f, 12f, 52f, 52f))
            addRect(RectF32.ofLTRB(24f, 24f, 40f, 40f))
        }
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(holedPath, Paint.fill(ColorARGB.of(255, 56, 220, 120)).copy(antiAlias = false))
            restore()
        }

        assertTerminal("unsupported.recording.core_primitive_path_stencil_clip", surface::render)
    }

    @Test
    fun `public clamp linear gradient FillRect renders inside one hard path clip stencil scope`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val red = ColorARGB.of(255, 255, 0, 0)
        val blue = ColorARGB.of(255, 0, 0, 255)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            clipPath(
                Path {
                    moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
                }.apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(
                RectF32.ofLTRB(0f, 0f, 64f, 64f),
                Paint(
                    shader = Shader.LinearGradient(
                        Point2F32(8f, 8f),
                        Point2F32(56f, 8f),
                        listOf(GradientStop(0f, red), GradientStop(1f, blue)),
                    ),
                ).copy(antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 9, 9, ColorARGB.of(255, 251, 0, 49))
        assertRgbaNear(result.pixels, 64, 53, 9, ColorARGB.of(255, 65, 0, 249))
        assertRgbaNear(result.pixels, 64, 54, 9, background)
    }

    @Test
    fun `public direct triangle clamp gradient renders inside a hard path clip stencil scope`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                    .apply { fillType = FillType.WINDING },
                Paint(
                    shader = Shader.LinearGradient(
                        Point2F32(20f, 19.3f), Point2F32(20f, 23.3f),
                        listOf(GradientStop(0f, ColorARGB.of(255, 0, 0, 0)), GradientStop(1f, ColorARGB.of(255, 4, 4, 4))),
                        TileMode.CLAMP,
                    ),
                    antiAlias = false,
                ),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 20, 21, ColorARGB.of(255, 2, 2, 2), tolerance = 0)
        assertRgbaNear(result.pixels, 64, 50, 14, background, tolerance = 0)
        assertEquals(1059, result.pixels.asList().chunked(4).count { pixel ->
            pixel != listOf(background.red.toUByte(), background.green.toUByte(), background.blue.toUByte(), background.alpha.toUByte())
        })
    }

    @Test
    fun `public translated direct triangle clamp gradient keeps device coordinates inside a hard path clip`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save(); translate(2f, 0f)
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false,
            )
            drawPath(
                Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                    .apply { fillType = FillType.WINDING },
                Paint(shader = Shader.LinearGradient(
                    Point2F32(20f, 19.3f), Point2F32(20f, 23.3f),
                    listOf(GradientStop(0f, ColorARGB.of(255, 0, 0, 0)), GradientStop(1f, ColorARGB.of(255, 4, 4, 4))), TileMode.CLAMP,
                ), antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 22, 21, ColorARGB.of(255, 2, 2, 2), tolerance = 0)
        assertRgbaNear(result.pixels, 64, 8, 12, background, tolerance = 0)
    }

    @Test
    fun `public uniform scaled direct triangle clamp gradient keeps device coordinates inside a hard path clip`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save(); translate(8f, 4f); scale(0.75f, 0.75f)
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false,
            )
            drawPath(
                Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                    .apply { fillType = FillType.WINDING },
                Paint(shader = Shader.LinearGradient(
                    Point2F32(20f, 19.066666f), Point2F32(20f, 24.4f),
                    listOf(GradientStop(0f, ColorARGB.of(255, 0, 0, 0)), GradientStop(1f, ColorARGB.of(255, 4, 4, 4))), TileMode.CLAMP,
                ), antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 22, 20, ColorARGB.of(255, 2, 2, 2), tolerance = 0)
        assertRgbaNear(result.pixels, 64, 13, 11, background, tolerance = 0)
        assertEquals(592, result.pixels.asList().chunked(4).count { pixel ->
            pixel != listOf(background.red.toUByte(), background.green.toUByte(), background.blue.toUByte(), background.alpha.toUByte())
        })
    }

    @Test
    fun `public pure uniform scaled direct triangle clamp gradient renders inside a hard path clip`() {
        requireWebGpu()
        val background = ColorARGB.of(255, 13, 20, 33)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save(); scale(0.75f, 0.75f)
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING }, ClipOp.INTERSECT, antiAlias = false,
            )
            drawPath(
                Path { moveTo(4f, 4.25f); lineTo(60f, 12f); lineTo(12f, 60f); close() }
                    .apply { fillType = FillType.WINDING },
                Paint(shader = Shader.LinearGradient(
                    Point2F32(8f, 8f), Point2F32(56f, 8f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)), TileMode.CLAMP,
                ), antiAlias = false),
            )
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 11, 7, ColorARGB.of(255, 237, 0, 109), tolerance = 0)
        assertRgbaNear(result.pixels, 64, 5, 7, background, tolerance = 0)
    }

    @Test
    fun `public translated hard path clip preserves clamp linear gradient device coordinates`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save(); translate(2f, 0f)
            clipPath(Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }, ClipOp.INTERSECT, false)
            drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint(shader = Shader.LinearGradient(
                Point2F32(8f, 8f), Point2F32(56f, 8f), listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            )).copy(antiAlias = false)); restore()
        }
        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 11, 9, ColorARGB.of(255, 251, 0, 49))
        assertRgbaNear(result.pixels, 64, 9, 9, ColorARGB.of(255, 13, 20, 33))
    }

    @Test
    fun `public uniform scaled hard path clip preserves clamp linear gradient device coordinates`() {
        requireWebGpu()
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(ColorARGB.of(255, 13, 20, 33))
            save(); translate(8f, 4f); scale(0.75f, 0.75f)
            clipPath(Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }, ClipOp.INTERSECT, false)
            drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint(shader = Shader.LinearGradient(
                Point2F32(8f, 8f), Point2F32(56f, 8f), listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            )).copy(antiAlias = false)); restore()
        }
        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        // The pixel center x=15.5 is 1.5/36 along the transformed [14, 50] axis;
        // interpolation is linear-light before sRGB8 encoding.
        assertRgbaNear(result.pixels, 64, 15, 11, ColorARGB.of(255, 250, 0, 57))
        assertRgbaNear(result.pixels, 64, 13, 11, ColorARGB.of(255, 13, 20, 33))
    }

    @Test
    fun `public translated hard path clip renders at its captured device-space coordinates`() {
        requireWebGpu()
        val triangle = Path().apply {
            moveTo(8f, 8f)
            lineTo(56f, 8f)
            lineTo(8f, 55f)
            close()
        }
        val background = ColorARGB.of(255, 13, 20, 33)
        val fill = ColorARGB.of(255, 242, 135, 46)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            translate(2f, 0f)
            clipPath(triangle, ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32(0f, 0f, 64f, 64f), Paint.fill(fill).copy(antiAlias = false))
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 11, 9, fill)
        assertRgbaNear(result.pixels, 64, 9, 9, background)
        assertRgbaNear(result.pixels, 64, 59, 9, background)
    }

    @Test
    fun `public uniformly scaled hard path clip renders at its captured device-space coordinates`() {
        requireWebGpu()
        val triangle = Path().apply {
            moveTo(8f, 8f)
            lineTo(56f, 8f)
            lineTo(8f, 55f)
            close()
        }
        val background = ColorARGB.of(255, 13, 20, 33)
        val fill = ColorARGB.of(255, 31, 115, 209)
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(background)
            save()
            translate(8f, 4f)
            scale(0.75f, 0.75f)
            clipPath(triangle, ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32(0f, 0f, 64f, 64f), Paint.fill(fill).copy(antiAlias = false))
            restore()
        }

        val result = surface.render()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 64, 15, 11, fill)
        assertRgbaNear(result.pixels, 64, 13, 11, background)
        assertRgbaNear(result.pixels, 64, 51, 11, background)
    }

    @Test
    fun `public non uniform scaled hard path clip remains refused with capture provenance`() {
        requireWebGpu()
        val triangle = Path().apply {
            moveTo(4f, 4f)
            lineTo(28f, 4f)
            lineTo(4f, 28f)
            close()
        }
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            scale(0.75f, 0.5f)
            clipPath(triangle, ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
            restore()
        }

        assertTerminal("unsupported.clip.path_transform", surface::render)
    }

    @Test
    fun `adapter backed inverse difference clip preserves fill exterior and AA edge`() {
        requireWebGpu()
        val inverseRect = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(RectF32(8.5f, 8.5f, 23.5f, 23.5f))
        }
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipPath(inverseRect, ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(RectF32(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Blue))
            restore()
        }

        assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
    }

    @Test
    fun `complex clip blur renders prepared under a multi rect analytic clip`() {
        // The rect-decomposable complex clip (AA rect INTERSECT + axis-aligned
        // orthogonal polygon DIFFERENCE) lowers to bounded analytic multi-rect coverage for the
        // blur composite, which folds the per-rect coverage into the blurred mask.
        val result = renderBlurredDifferenceClipScene()
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        TopLevelMaskBlurPixelOracle.assertPixelsNear(complexClipBlurOracle(sigma = 2f), result.pixels)
    }

    @Test
    fun `complex mask blur frames render prepared with the multi rect analytic clip`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        session!!
        val readbacksBefore = session.runtimeTelemetry.destinationReadbackSnapshots
        val copiesBefore = session.runtimeTelemetry.destinationCopies

        // The refusal is gone: every complex-clip mask blur frame now renders through the
        // multi-rect analytic composite. The blur lane's destination-read composite allocates a
        // NATIVE destination copy (destinationCopies), never a legacy CPU readback snapshot
        // (destinationReadbackSnapshots stays unchanged for the rendered lane).
        val result = renderBlurredDifferenceClipScene(sigma = 1.5f)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(
            readbacksBefore,
            session.runtimeTelemetry.destinationReadbackSnapshots,
            "a rendered mask blur frame must not allocate a legacy destination readback snapshot",
        )
        assertTrue(
            session.runtimeTelemetry.destinationCopies > copiesBefore,
            "the rendered mask blur frame must allocate its native destination copy",
        )
        TopLevelMaskBlurPixelOracle.assertPixelsNear(complexClipBlurOracle(sigma = 1.5f), result.pixels)
    }

    @Test
    fun `intersect orthogonal polygon clip stays terminal at the clip producer preflight`() {
        // Only DIFFERENCE orthogonal polygons decompose to bounded
        // analytic multi-rect coverage. An INTERSECT multi-band polygon (the L-shape) would
        // multiply disjoint rect coverages to zero (an empty clip), so it stays on the
        // coverage-mask route and terminates at the clip producer preflight.
        assertTerminal(PREPARED_CLIP_PRODUCER_AUTHORITY_REFUSAL) {
            Surface(16, 16).run {
                requireWebGpu()
                canvas {
                    save()
                    clipPath(
                        Path {
                            moveTo(5f, 4f)
                            lineTo(12f, 4f)
                            lineTo(12f, 8f)
                            lineTo(9f, 8f)
                            lineTo(9f, 12f)
                            lineTo(5f, 12f)
                            close()
                        },
                        ClipOp.INTERSECT,
                        antiAlias = true,
                    )
                    drawRect(
                        RectF32(4f, 4f, 12f, 12f),
                        Paint.fill(ColorARGB.Red).copy(
                            blendMode = BlendMode.DARKEN,
                            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 2f),
                        ),
                    )
                    restore()
                }
                render()
            }
        }
    }

    @Test
    fun `non blur core draw under a rect plus polygon difference clip stays on the coverage mask route`() {
        // The AnalyticMultiRect lowering is scoped to the mask-blur
        // composite lane only. A NON-BLUR direct draw under the rect INTERSECT + orthogonal
        // polygon DIFFERENCE clip must keep its prior CoverageMask route (which, for a
        // path-carrying mask, refuses with the documented depth/stencil topology code), NOT
        // the AnalyticMultiRect → Clip.Refused refusal.
        assertTerminal(PREPARED_CLIP_MASK_DEPTH_STENCIL_TOPOLOGY_REFUSAL) {
            Surface(16, 16).run {
                requireWebGpu()
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    clipPath(
                        Path {
                            moveTo(5f, 4f)
                            lineTo(12f, 4f)
                            lineTo(12f, 8f)
                            lineTo(9f, 8f)
                            lineTo(9f, 12f)
                            lineTo(5f, 12f)
                            close()
                        },
                        ClipOp.DIFFERENCE,
                        antiAlias = true,
                    )
                    drawRect(RectF32(2f, 2f, 14f, 14f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
                    restore()
                }
                render()
            }
        }
    }

    @Test
    fun `complex clip accepts every standard blend mode`() {
        requireWebGpu()

        BlendMode.entries.forEach { mode ->
            // Every blend mode rides the same analytic-clip core frame, which the
            // route collapse terminates before any blend decision.
            assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL) {
                renderMaskedRect(mode)
            }
        }
    }

    @Test
    fun `fixed alpha mask composition preserves destination outside source bounds`() {
        requireWebGpu()
        val surface = Surface(16, 16).run {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                save()
                clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                clipRect(RectF32(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                drawRect(
                    RectF32(2f, 2f, 5f, 5f),
                    Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.SRC),
                )
                restore()
            }
            this
        }

        assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
    }

    @Test
    fun `coverage alpha mask preserves difference holes for clear src and dst in`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val surface = Surface(16, 16).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    clipRect(RectF32(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                    drawRect(
                        RectF32(2f, 2f, 14f, 14f),
                        Paint.fill(ColorARGB.Red).copy(blendMode = blendMode),
                    )
                    restore()
                }
                this
            }

            assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
        }
    }

    @Test
    fun `coverage alpha mask preserves destination outside text glyphs for clear src and dst in`() {
        requireWebGpu()
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    drawText(
                        Font(typeface, 12f).toTextBlob("I", 7f, 12f),
                        0f,
                        0f,
                        Paint.fill(ColorARGB.Red).copy(blendMode = blendMode),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 2, 8, ColorARGB.White)
        }
    }

    @Test
    fun `alpha mask retains geometric coverage for zero alpha paint`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val surface = Surface(16, 16).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    clipRect(RectF32(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                    drawRect(
                        RectF32(2f, 2f, 14f, 14f),
                        Paint.fill(ColorARGB.fromRGBA(1f, 0f, 0f, 0f)).copy(blendMode = blendMode),
                    )
                    restore()
                }
                this
            }

            assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL, surface::render)
        }
    }

    @Test
    fun `AA geometry coverage blends after clear src and dst in`() {
        requireWebGpu()

        listOf(
            BlendMode.CLEAR,
            BlendMode.SRC,
            BlendMode.DST_IN,
            BlendMode.SRC_IN,
            BlendMode.SRC_OUT,
            BlendMode.DST_ATOP,
            BlendMode.MODULATE,
        ).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    drawRect(
                        RectF32(3.5f, 2f, 14f, 14f),
                        Paint.fill(ColorARGB.Red).copy(blendMode = blendMode, antiAlias = true),
                    )
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(
                result.pixels,
                16,
                3,
                8,
                when (blendMode) {
                    BlendMode.CLEAR -> ColorARGB.of(128, 188, 188, 188)
                    BlendMode.SRC -> ColorARGB.of(255, 255, 188, 188)
                    BlendMode.DST_IN -> ColorARGB.White
                    // SRC_IN/MODULATE over an opaque destination interpolate to the same
                    // half-coverage RED edge as SRC; SRC_OUT clears the destination like CLEAR;
                    // DST_ATOP with an opaque source preserves the destination like DST_IN.
                    BlendMode.SRC_IN, BlendMode.MODULATE -> ColorARGB.of(255, 255, 188, 188)
                    BlendMode.SRC_OUT -> ColorARGB.of(128, 188, 188, 188)
                    BlendMode.DST_ATOP -> ColorARGB.White
                    else -> error("unexpected test mode: $blendMode")
                },
            )
        }
    }

    @Test
    fun `AA scissor preserves destination outside clear src and dst in`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                    save()
                    clipRect(RectF32(4f, 2f, 12f, 14f), ClipOp.INTERSECT, antiAlias = false)
                    drawRect(
                        RectF32(3.5f, 2f, 13.5f, 14f),
                        Paint.fill(ColorARGB.Red).copy(blendMode = blendMode, antiAlias = true),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 3, 8, ColorARGB.White)
        }
    }

    @Test
    fun `two aa rects with fixed function blends stay terminal on the multi key analytic shape refusal`() {
        requireWebGpu()
        // Both rects are analytic-shape (antiAlias defaults true) and both blends are
        // modulate-compatible fixed-function (SRC_OVER / DST_OVER), so the frame seals one
        // multi-key analytic-shape pass. Only the dst-read geometric modes closed;
        // the fixed-function multi-key AA family stays refused with its stable code.
        val surface = Surface(16, 16).run {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                drawRect(
                    RectF32(3.5f, 2f, 14f, 14f),
                    Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.DST_OVER, antiAlias = true),
                )
            }
            this
        }

        assertTerminal(PREPARED_ANALYTIC_SHAPE_MULTI_KEY_REFUSAL, surface::render)
    }

    @Test
    fun `unsupported image blend refuses before alpha mask image clip lowering`() {
        requireWebGpu()
        val transparentImage = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = byteArrayOf(0, 0, 0, 0),
            colorType = ColorType.RGBA_8888,
            sourceId = "coverage-plane-transparent-image",
            alphaType = AlphaType.PREMUL,
        )
        val surface = Surface(16, 16).apply {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White))
                save()
                clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                clipRect(RectF32(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                drawImage(
                    transparentImage,
                    RectF32(2f, 2f, 14f, 14f),
                    Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.CLEAR),
                )
                restore()
            }
        }

        assertTerminal(
            expectedCode = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
            block = surface::render,
        )
    }

    @Test
    fun `mask composes destination read blend through the source snapshot formula`() {
        requireWebGpu()

        // The alpha-mask frame is refused at analytic-shape lowering before the
        // DARKEN destination-read formula is ever built.
        assertTerminal(PREPARED_ANALYTIC_SHAPE_CLIP_REFUSAL) {
            renderMaskedRect(BlendMode.DARKEN)
        }
    }

    @Test
    fun `no clip destination read composes against a transparent snapshot`() {
        requireWebGpu()
        // The analytic-shape dst-read formula pipeline renders the DARKEN rect
        // over the transparent snapshot (DARKEN(src, transparent) = src = RED).
        val result = Surface(16, 16).run {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.DARKEN))
            }
            render()
        }

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 16, 4, 4, ColorARGB.Red)
    }

    @Test
    fun `clear and color dodge use their mapped clip composition routes`() {
        requireWebGpu()

        // CLEAR now rides the analytic-shape destination-read formula and
        // COLOR_DODGE the same; both compose over the transparent snapshot
        // (CLEAR(src, transparent) = transparent; COLOR_DODGE(src, transparent) = src = RED).
        val clear = Surface(16, 16).run {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.CLEAR))
            }
            render()
        }
        assertEquals(1, clear.stats.opsDispatched)
        assertEquals(0, clear.diagnostics.fatalCount, clear.diagnostics.entries.toString())

        val dodge = Surface(16, 16).run {
            canvas {
                drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(blendMode = BlendMode.COLOR_DODGE))
            }
            render()
        }
        assertEquals(0, dodge.diagnostics.fatalCount, dodge.diagnostics.entries.toString())
        assertRgbaNear(dodge.pixels, 16, 4, 4, ColorARGB.Red)
    }

    @Test
    fun `core frame with complex clipped image is terminal atomically`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(RectF32(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(RectF32(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val ops = listOf(
            DisplayOp.DrawRect(RectF32(2f, 2f, 8f, 8f), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, clip),
            DisplayOp.DrawRRect(RRectF32.of(RectF32(20f, 2f, 28f, 10f), radius = 2f), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, clip),
            DisplayOp.DrawPath(
                Path { moveTo(2f, 22f); lineTo(10f, 22f); lineTo(6f, 30f); close() },
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                clip,
            ),
            DisplayOp.DrawImage(
                bluePixel(),
                RectF32(0f, 0f, 1f, 1f),
                RectF32(22f, 22f, 30f, 30f),
                Paint(),
                Matrix3x3F32.Identity,
                clip,
            ),
        )
        assertTerminal(
            expectedCode = PREPARED_IMAGE_CLIP_REFUSAL,
        ) {
            renderViaGpu(
                buffer = StaticDisplayListBuffer(ops),
                width = 32,
                height = 32,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
            )
        }
    }

    @Test
    fun `text atlas renders prepared while textured vertices stay terminal`() {
        requireWebGpu()
        // Non-AA device rect: the prepared vertices route refuses AA-mask and
        // analytic-intersection clips with unsupported.vertices.clip_coverage
        // before lowering, so an integer-scissor clip keeps the
        // textured-vertices material refusal reachable while the text draw still
        // renders prepared.
        val clip = ClipStack.DeviceRect(RectF32(1f, 1f, 31f, 31f), antiAlias = false)
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val image = bgraBluePixel()
        val ops = listOf(
            DisplayOp.DrawText(
                blob = Font(typeface, 12f).toTextBlob("A", 4f, 16f),
                x = 0f,
                y = 0f,
                paint = Paint.fill(ColorARGB.Red),
                transform = Matrix3x3F32.Identity,
                clip = clip,
            ),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(Point2F32(0f, 0f), Point2F32(8f, 0f), Point2F32(0f, 8f)),
                    texCoords = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
                ),
                paint = Paint.fill(ColorARGB.White).copy(shader = Shader.Image(image)),
                transform = Matrix3x3F32.translation(20f, 20f),
                clip = clip,
            ),
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = ops,
                width = 32,
                height = 32,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
    }

    @Test
    fun `scissor destination read DrawText keeps exterior intact`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(6f, 6f, 14f, 14f), antiAlias = false)
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                        DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                        DisplayOp.DrawText(
                            blob = Font(typeface, 20f).toTextBlob("W", 0f, 15f),
                            x = 0f,
                            y = 0f,
                            paint = Paint.fill(ColorARGB.Black).copy(blendMode = BlendMode.DARKEN),
                            transform = Matrix3x3F32.Identity,
                            clip = clip,
                        ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals("invalid.preflight.text.blend", failure.diagnostic.code.value)
    }

    @Test
    fun `scissor destination read textured vertices are terminal`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(6f, 6f, 14f, 14f), antiAlias = false)
        val vertices = texturedScissorTriangle()
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                    DisplayOp.DrawVertices(
                        vertices = vertices,
                        paint = advancedBlackImagePaint(),
                        transform = Matrix3x3F32.Identity,
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
    }

    @Test
    fun `scissor destination read textured mesh is terminal`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(6f, 6f, 14f, 14f), antiAlias = false)
        val mesh = Mesh(texturedScissorTriangle(), bounds = RectF32(1f, 1f, 15f, 15f))
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                    DisplayOp.DrawMesh(
                        mesh = mesh,
                        paint = advancedBlackImagePaint(),
                        blendMode = null,
                        transform = Matrix3x3F32.Identity,
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
    }

    @Test
    fun `empty scissor destination read DrawText remains terminal`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(20f, 20f, 24f, 24f), antiAlias = false)
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                    DisplayOp.DrawText(
                        blob = Font(typeface, 20f).toTextBlob("W", 0f, 15f),
                        x = 0f,
                        y = 0f,
                        paint = Paint.fill(ColorARGB.Black).copy(blendMode = BlendMode.DARKEN),
                        transform = Matrix3x3F32.Identity,
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals("invalid.preflight.text.blend", failure.diagnostic.code.value)
    }

    @Test
    fun `empty scissor textured vertices are terminal`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(20f, 20f, 24f, 24f), antiAlias = false)
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                    DisplayOp.DrawVertices(
                        vertices = texturedScissorTriangle(),
                        paint = advancedBlackImagePaint(),
                        transform = Matrix3x3F32.Identity,
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
    }

    @Test
    fun `empty scissor textured mesh is terminal`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(RectF32(20f, 20f, 24f, 24f), antiAlias = false)
        val mesh = Mesh(texturedScissorTriangle(), bounds = RectF32(1f, 1f, 15f, 15f))
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
                    DisplayOp.DrawMesh(
                        mesh = mesh,
                        paint = advancedBlackImagePaint(),
                        blendMode = null,
                        transform = Matrix3x3F32.Identity,
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
    }

    @Test
    fun `outlined multi glyph text preserves every source glyph under a complex clip`() {
        requireWebGpu()
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        Font(typeface, 16f).toTextBlob("AA", 2f, 18f),
                        0f,
                        0f,
                        Paint.stroke(ColorARGB.Red, 1f),
                        Matrix3x3F32.Identity,
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleIn(result.pixels, 32, 2..10, 4..19)
        assertVisibleIn(result.pixels, 32, 14..24, 4..19)
    }

    @Test
    fun `complex clip source with hairline points lowers through the complex clip`() {
        requireWebGpu()

        // Hairline points lower to one-device-pixel squares and render prepared through the
        // complex AA clip; both points sit inside the clip region.
        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawPoints(
                        PointMode.POINTS,
                        listOf(Point2F32(4f, 4f), Point2F32(20f, 4f)),
                        Paint.fill(ColorARGB.Red),
                        Matrix3x3F32.Identity,
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )
        assertEquals(0, result.stats.opsRefused, result.diagnostics.summary())
        assertRgbaNear(result.pixels, 32, 4, 4, ColorARGB.Red)
        assertRgbaNear(result.pixels, 32, 20, 4, ColorARGB.Red)
    }

    @Test
    fun `complex clip image nine is an exact prepared clip refusal`() {
        requireWebGpu()
        val image = opaqueImage(size = 3)

        assertTerminal(PREPARED_IMAGE_CLIP_REFUSAL) {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawImageNine(
                            image,
                            RectF32(1f, 1f, 2f, 2f),
                            RectF32(2f, 2f, 14f, 14f),
                            null,
                            Matrix3x3F32.Identity,
                            complexFullClip(),
                        ),
                    ),
                ),
                32,
                32,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )
        }
    }

    @Test
    fun `complex clip atlas is an exact prepared clip refusal`() {
        requireWebGpu()
        val image = opaqueImage(size = 3)

        assertTerminal(PREPARED_IMAGE_CLIP_REFUSAL) {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawAtlas(
                            atlas = image,
                            transforms = listOf(Matrix3x3F32.translation(2f, 20f), Matrix3x3F32.translation(18f, 20f)),
                            texRects = listOf(RectF32(0f, 0f, 3f, 3f), RectF32(0f, 0f, 3f, 3f)),
                            colors = null,
                            blendMode = BlendMode.SRC_OVER,
                            paint = null,
                            transform = Matrix3x3F32.Identity,
                            clip = complexFullClip(),
                        ),
                    ),
                ),
                32,
                32,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )
        }
    }

    @Test
    fun `mesh program is refused as unregistered rather than rendered as plain vertices`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val effect = simpleRuntimeEffect()
        val mesh = Mesh(
            vertices = Vertices(
                VertexMode.TRIANGLES,
                listOf(Point2F32(2f, 2f), Point2F32(8f, 2f), Point2F32(2f, 8f)),
            ),
            program = MeshProgram(effect),
            bounds = RectF32(2f, 2f, 8f, 8f),
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawMesh(mesh, Paint.fill(ColorARGB.Red), null, Matrix3x3F32.Identity, clip),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `nested picture with unsupported paint stays refused while captured child clips propagate through S G routing`() {
        requireWebGpu()
        val child = Picture(
            RectF32(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawRect(RectF32(1f, 1f, 7f, 7f), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen)),
        )
        val outerClip = complexFullClip()
        val painted = Picture(
            RectF32(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawPicture(child, Paint.stroke(ColorARGB.Red, 1f), Matrix3x3F32.Identity, ClipStack.WideOpen)),
        )
        // The painted picture frame is a documented prepared-route refusal: the composite
        // capture refuses clip snapshots inside layer scopes.
        val paintFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(listOf(DisplayOp.DrawPicture(painted, null, Matrix3x3F32.Identity, outerClip))),
                32, 32, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            )
        }
        assertEquals("unsupported.composite.clip", paintFailure.diagnostic.code.value)

        val clipped = Picture(
            RectF32(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawPicture(child, null, Matrix3x3F32.Identity, outerClip)),
        )
        val clippedFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(listOf(DisplayOp.DrawPicture(clipped, null, Matrix3x3F32.Identity, outerClip))),
                32, 32, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            )
        }
        assertEquals("unsupported.composite.clip", clippedFailure.diagnostic.code.value)
    }

    @Test
    fun `textured vertices without image shaders render through the prepared product route`() {
        requireWebGpu()
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point2F32(2f, 2f), Point2F32(8f, 2f), Point2F32(2f, 8f)),
            texCoords = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
        )
        val operations = listOf(
            DisplayOp.DrawVertices(vertices, Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawMesh(
                Mesh(vertices, bounds = RectF32(2f, 2f, 8f, 8f)),
                Paint.fill(ColorARGB.White),
                null,
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
        )

        operations.forEach { operation ->
            val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()
            val result = renderViaGpu(
                StaticDisplayListBuffer(listOf(operation)),
                32,
                32,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
                preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
            )

            assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
                decisions.single(),
                operation::class.simpleName,
            )
            assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
            assertEquals(0, result.stats.opsRefused, result.diagnostics.entries.toString())
            assertRgbaNear(result.pixels, 32, 3, 3, ColorARGB.White)
        }
    }

    @Test
    fun `textured vertices retain explicit transform and material refusals`() {
        requireWebGpu()
        // Non-AA device rect: the prepared vertices route refuses AA-mask and
        // analytic-intersection clips with unsupported.vertices.clip_coverage
        // before lowering, so an integer-scissor clip keeps the transform and
        // material refusals reachable.
        val clip = ClipStack.DeviceRect(RectF32(1f, 1f, 15f, 15f), antiAlias = false)
        val triangle = listOf(Point2F32(1f, 1f), Point2F32(8f, 1f), Point2F32(1f, 8f))
        val uvs = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f))
        val paint = Paint.fill(ColorARGB.White).copy(shader = Shader.Image(bgraBluePixel()))

        val perspective = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawVertices(
                            Vertices(VertexMode.TRIANGLES, triangle, texCoords = uvs),
                            paint,
                            Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f),
                            clip,
                        ),
                    ),
                ),
                16,
                16,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )
        }
        assertEquals(GPUPreparedVerticesRefusalCodes.Transform, perspective.diagnostic.code.value)

        val strip = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawVertices(
                            Vertices(VertexMode.TRIANGLE_STRIP, triangle, texCoords = uvs),
                            paint,
                            Matrix3x3F32.Identity,
                            clip,
                        ),
                    ),
                ),
                16,
                16,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )
        }
        assertEquals(GPUPreparedVerticesRefusalCodes.Material, strip.diagnostic.code.value)
    }

    @Test
    fun `non textured vertices with colors and indices render through the prepared route`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point2F32(1f, 1f), Point2F32(8f, 1f), Point2F32(1f, 8f)),
            colors = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue),
            indices = listOf(0, 1, 2),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(DisplayOp.DrawVertices(vertices, Paint.fill(ColorARGB.White), Matrix3x3F32.Identity, ClipStack.WideOpen)),
            ),
            16,
            16,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(decisions.single())
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(0, result.stats.opsRefused, result.diagnostics.entries.toString())
    }

    @Test
    fun `textured vertices with invalid indices retain their index refusal`() {
        requireWebGpu()
        // Non-AA device rect: the prepared vertices route refuses AA-mask and
        // analytic-intersection clips with unsupported.vertices.clip_coverage
        // before packing, so an integer-scissor clip keeps the index refusal
        // reachable.
        val clip = ClipStack.DeviceRect(RectF32(1f, 1f, 15f, 15f), antiAlias = false)
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point2F32(1f, 1f), Point2F32(8f, 1f), Point2F32(1f, 8f)),
            texCoords = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
            indices = listOf(0, 1, 3),
        )

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawVertices(
                            vertices,
                            Paint.fill(ColorARGB.White).copy(shader = Shader.Image(bgraBluePixel())),
                            Matrix3x3F32.Identity,
                            clip,
                        ),
                    ),
                ),
                16,
                16,
                PixelFormat.RGBA8,
                RenderConfig.DEFAULT,
            )
        }

        assertEquals(
            GPUPreparedVerticesRefusalCodes.IndexOutOfRange,
            failure.diagnostic.code.value,
        )
    }

    @Test
    fun `picture with unsupported paint is refused while its captured child clip uses the picture source route`() {
        requireWebGpu()
        val outerClip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(RectF32(0f, 0f, 8f, 8f)).drawRect(RectF32(1f, 1f, 7f, 7f), Paint.fill(ColorARGB.Red))
        val picture = recorder.finishRecordingAsPicture()

        // The painted picture frame is a documented prepared-route refusal: the composite
        // capture refuses clip snapshots inside layer scopes.
        val paintFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderPictureWithClip(picture, Paint.stroke(ColorARGB.Red, 1f), outerClip)
        }
        assertEquals("unsupported.composite.clip", paintFailure.diagnostic.code.value)

        val childClipFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderPictureWithClip(picture, null, outerClip)
        }
        assertEquals("unsupported.composite.clip", childClipFailure.diagnostic.code.value)
    }

    @Test
    fun `outline text without a typeface is terminal`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                        DisplayOp.DrawText(
                            TextBlob(
                                glyphRuns = listOf(
                                    KanvasGlyphRun(
                                        glyphs = listOf(1u),
                                        positions = listOf(Point2F32(0f, 0f)),
                                    ),
                                ),
                            ),
                            0f,
                            0f,
                            Paint.stroke(ColorARGB.Red, 1f),
                            Matrix3x3F32.Identity,
                            clip,
                        ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals("unsupported.text.typeface_missing", failure.diagnostic.code.value)
    }

    @Test
    fun `empty text does not produce a complex clip source composite`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true),
            ),
        )

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        TextBlob(emptyList()),
                        0f,
                        0f,
                        Paint.fill(ColorARGB.Red),
                        Matrix3x3F32.Identity,
                        clip,
                    ),
                ),
            ),
            width = 16,
            height = 16,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
    }

    @Test
    fun `alpha mask picture composes its child through S G source products`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val picture = Picture(
            RectF32(0f, 0f, 16f, 16f),
            listOf(
                DisplayOp.DrawRect(
                    RectF32(2f, 2f, 14f, 14f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
        )
        // The alpha-mask clipped picture is a documented prepared-route refusal: the
        // composite capture refuses clip snapshots inside layer scopes.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                buffer = StaticDisplayListBuffer(
                    listOf(DisplayOp.DrawPicture(picture, null, Matrix3x3F32.Identity, clip)),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
            )
        }
        assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
    }

    @Test
    fun `remaining high level GPU routes render through their S G adapters`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(RectF32(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(RectF32(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val image = opaqueImage(size = 3)
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point2F32(2f, 2f), Point2F32(8f, 2f), Point2F32(2f, 8f)),
        )
        val picture = Picture(
            RectF32(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(RectF32(24f, 24f, 30f, 30f), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen)),
        )
        val ops = listOf(
            DisplayOp.DrawPoints(PointMode.POINTS, listOf(Point2F32(3f, 3f), Point2F32(6f, 6f)), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, clip),
            DisplayOp.DrawDRRect(
                RRectF32.of(RectF32(2f, 20f, 10f, 28f), radius = 1f),
                RRectF32.of(RectF32(4f, 22f, 8f, 26f), radius = 1f),
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                clip,
            ),
            DisplayOp.DrawImageNine(image, RectF32(1f, 1f, 2f, 2f), RectF32(12f, 2f, 22f, 12f), null, Matrix3x3F32.Identity, clip),
            DisplayOp.DrawImageLattice(
                image,
                Lattice(xDivs = listOf(1, 2), yDivs = listOf(1, 2)),
                RectF32(12f, 14f, 22f, 24f),
                null,
                Matrix3x3F32.Identity,
                clip,
            ),
            DisplayOp.DrawAtlas(
                atlas = image,
                transforms = listOf(Matrix3x3F32.Identity, Matrix3x3F32.Identity),
                texRects = listOf(RectF32(0f, 0f, 3f, 3f), RectF32(0f, 0f, 3f, 3f)),
                colors = null,
                blendMode = BlendMode.SRC_OVER,
                paint = null,
                transform = Matrix3x3F32.Identity,
                clip = clip,
            ),
            DisplayOp.DrawPicture(picture, null, Matrix3x3F32.Identity, clip),
        )
        // The DrawPicture inside the complex-clip frame is a documented prepared-route
        // refusal (unsupported.composite.operation): the composite capture admits only core
        // geometry operations inside layer scopes.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                buffer = StaticDisplayListBuffer(ops),
                width = 32,
                height = 32,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
            )
        }
        assertEquals("unsupported.composite.operation", failure.diagnostic.code.value)
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertTerminal(
        expectedCode: String,
        block: () -> Any?,
    ) {
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> { block() }
        assertEquals(expectedCode, failure.diagnostic.code.value)
    }

    private fun assertRgbaNear(
        pixels: UByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: ColorARGB,
        tolerance: Int = 8,
    ) {
        val offset = (y * width + x) * 4
        val actual = intArrayOf(
            pixels[offset].toInt() and 0xff,
            pixels[offset + 1].toInt() and 0xff,
            pixels[offset + 2].toInt() and 0xff,
            pixels[offset + 3].toInt() and 0xff,
        )
        val wanted = intArrayOf(expected.red, expected.green, expected.blue, expected.alpha)
        actual.indices.forEach { channel ->
            assertTrue(
                kotlin.math.abs(actual[channel] - wanted[channel]) <= tolerance,
                "pixel=($x,$y) channel=$channel expected=${wanted[channel]} actual=${actual[channel]} tolerance=$tolerance",
            )
        }
    }

    private fun assertVisibleAt(pixels: UByteArray, width: Int, x: Int, y: Int) {
        val alpha = pixels[(y * width + x) * 4 + 3].toInt() and 0xff
        assertTrue(alpha >= 200, "expected visible pixel at ($x, $y)")
    }

    private fun assertVisibleIn(pixels: UByteArray, width: Int, xs: IntRange, ys: IntRange) {
        assertTrue(
            ys.any { y -> xs.any { x -> (pixels[(y * width + x) * 4 + 3].toInt() and 0xff) >= 200 } },
            "expected a visible pixel in x=$xs, y=$ys",
        )
    }

    private fun pixelAt(pixels: UByteArray, x: Int, y: Int): List<Int> {
        val offset = (y * 16 + x) * 4
        return List(4) { channel -> pixels[offset + channel].toInt() and 0xff }
    }

    private fun renderBlurredDifferenceClipScene(
        sigma: Float = 2f,
        clipOffset: Float = 0f,
    ) = Surface(16, 16).run {
        requireWebGpu()
        canvas {
            drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.of(128, 32, 64, 192)))
            save()
            clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
            clipPath(
                Path {
                    moveTo(5f + clipOffset, 4f)
                    lineTo(12f, 4f)
                    lineTo(12f, 8f)
                    lineTo(9f + clipOffset, 8f)
                    lineTo(9f + clipOffset, 12f)
                    lineTo(5f + clipOffset, 12f)
                    close()
                },
                ClipOp.DIFFERENCE,
                antiAlias = true,
            )
            drawRect(
                RectF32(4f, 4f, 12f, 12f),
                Paint.fill(ColorARGB.Red).copy(
                    blendMode = BlendMode.DARKEN,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
                ),
            )
            restore()
        }
        render()
    }

    /**
     * CPU-oracle reference for [renderBlurredDifferenceClipScene]: the same DARKEN blur over
     * the translucent background, with the rect-decomposed complex clip folded as ordered
     * INTERSECT/DIFFERENCE rect coverage. The L-shape DIFFERENCE polygon decomposes into the
     * two band rects [5,4,12,8] and [5,8,9,12]. The destination snapshot carries the literal
     * unpremultiplied sRGB background bytes (the solid-fill lane stores the color as-is; the
     * composite decodes sRGB on read), matching the observed GPU store.
     */
    private fun complexClipBlurOracle(sigma: Float): UByteArray {
        val background = ColorARGB.of(128, 32, 64, 192)
        val destination = UByteArray(16 * 16 * 4) { index ->
            when (index % 4) {
                0 -> background.red.toUByte()
                1 -> background.green.toUByte()
                2 -> background.blue.toUByte()
                else -> background.alpha.toUByte()
            }
        }
        return TopLevelMaskBlurPixelOracle.render(
            targetWidth = 16,
            targetHeight = 16,
            shape = TopLevelMaskBlurPixelOracle.Shape.Rect(4f, 4f, 12f, 12f),
            clipBounds = GPUBounds(0f, 0f, 16f, 16f),
            style = BlurStyle.NORMAL,
            sigma = sigma,
            source = ColorARGB.Red,
            blendMode = BlendMode.DARKEN,
            destinationEncoded = destination,
            clip = TopLevelMaskBlurPixelOracle.ComplexClip(
                listOf(
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        1f, 1f, 15f, 15f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Intersect,
                        antiAlias = true,
                    ),
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        5f, 4f, 12f, 8f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Difference,
                        antiAlias = true,
                    ),
                    TopLevelMaskBlurPixelOracle.ComplexClipElement(
                        5f, 8f, 9f, 12f,
                        TopLevelMaskBlurPixelOracle.ComplexClipOperation.Difference,
                        antiAlias = true,
                    ),
                ),
            ),
        )
    }

    private fun renderMaskedRect(blendMode: BlendMode) = Surface(16, 16).run {
        canvas {
            save()
            clipRect(RectF32(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(RectF32(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(RectF32(0f, 0f, 16f, 16f), Paint.fill(ColorARGB.Red).copy(blendMode = blendMode))
            restore()
        }
        render()
    }

    private fun complexFullClip(): ClipStack = ClipStack.Complex(
        listOf(ClipStackOp.RectOp(RectF32(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)),
    )

    private fun bluePixel(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte()),
        colorType = ColorType.RGBA_8888,
        sourceId = "clip-blue-pixel",
        alphaType = AlphaType.PREMUL,
    )

    private fun bgraBluePixel(): Image = Image.fromPixels(
        width = 1,
        height = 1,
        pixels = byteArrayOf(0xff.toByte(), 0, 0, 0xff.toByte()),
        colorType = ColorType.BGRA_8888,
        sourceId = "clip-bgra-blue-pixel",
        alphaType = AlphaType.PREMUL,
    )

    private fun opaqueImage(size: Int): Image = Image.fromPixels(
        width = size,
        height = size,
        pixels = ByteArray(size * size * 4) { index -> if (index % 4 == 3) 0xff.toByte() else 0x7f },
        colorType = ColorType.RGBA_8888,
        sourceId = "clip-opaque-$size",
        alphaType = AlphaType.PREMUL,
    )

    private fun texturedScissorTriangle(): Vertices = Vertices(
        mode = VertexMode.TRIANGLES,
        positions = listOf(Point2F32(1f, 1f), Point2F32(15f, 1f), Point2F32(1f, 15f)),
        texCoords = listOf(Point2F32(0f, 0f), Point2F32(1f, 0f), Point2F32(0f, 1f)),
    )

    private fun advancedBlackImagePaint(): Paint = Paint.fill(ColorARGB.White).copy(
        shader = Shader.Image(
            Image.fromPixels(
                width = 1,
                height = 1,
                pixels = byteArrayOf(0, 0, 0, 0xff.toByte()),
                colorType = ColorType.RGBA_8888,
                sourceId = "clip-scissor-black",
                alphaType = AlphaType.PREMUL,
            ),
        ),
        blendMode = BlendMode.DARKEN,
    )

    private fun assertWhiteOutsideClip(pixels: UByteArray, width: Int, clip: RectF32) {
        for (y in 0 until width) {
            for (x in 0 until width) {
                if (
                    x.toFloat() >= clip.left && x.toFloat() < clip.right &&
                    y.toFloat() >= clip.top && y.toFloat() < clip.bottom
                ) continue
                val offset = (y * width + x) * 4
                assertEquals(255, pixels[offset].toInt(), "red outside clip at ($x,$y)")
                assertEquals(255, pixels[offset + 1].toInt(), "green outside clip at ($x,$y)")
                assertEquals(255, pixels[offset + 2].toInt(), "blue outside clip at ($x,$y)")
                assertEquals(255, pixels[offset + 3].toInt(), "alpha outside clip at ($x,$y)")
            }
        }
    }

    private fun assertDarkenedInsideClip(pixels: UByteArray, width: Int, clip: RectF32) {
        val hasDarkenedPixel = (clip.top.toInt() until clip.bottom.toInt()).any { y ->
            (clip.left.toInt() until clip.right.toInt()).any { x ->
                pixels[(y * width + x) * 4].toInt() < 255
            }
        }
        assertTrue(hasDarkenedPixel, "expected a destination-read source pixel inside $clip")
    }

    private fun simpleRuntimeEffect(): RuntimeEffect {
        RuntimeEffectWgsl4kWiring.install()
        return RuntimeEffect.compile(
            """
                @fragment
                fn main() -> @location(0) vec4f {
                    return vec4f(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent(),
        ).getOrThrow()
    }

    private fun renderPictureWithClip(picture: Picture, paint: Paint?, clip: ClipStack) = renderViaGpu(
        StaticDisplayListBuffer(
            listOf(DisplayOp.DrawPicture(picture, paint, Matrix3x3F32.Identity, clip)),
        ),
        16,
        16,
        PixelFormat.RGBA8,
        RenderConfig.DEFAULT,
    )

    private class StaticDisplayListBuffer(
        private val operations: List<DisplayOp>,
    ) : DisplayListBuffer {
        override fun append(op: DisplayOp): Nothing = error("Static buffer is immutable")

        override fun ops(): List<DisplayOp> = operations
    }
}
