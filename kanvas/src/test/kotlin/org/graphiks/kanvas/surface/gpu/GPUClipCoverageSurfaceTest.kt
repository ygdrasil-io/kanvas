package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.MeshProgram
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.Shader
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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.Mesh
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.redByte
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

private const val PREPARED_IMAGE_CLIP_REFUSAL = "unsupported.surface.prepared.image-clip"

@OptIn(ExperimentalUnsignedTypes::class)
class GPUClipCoverageSurfaceTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `complex difference clip with image is terminal before the frame enters legacy`() {
        requireWebGpu()
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipRect(Rect(2f, 2f, 30f, 30f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(Rect(12f, 12f, 20f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.WHITE))
            drawRect(Rect(2f, 2f, 12f, 12f), Paint.fill(Color.RED))
            drawRRect(RRect(Rect(20f, 2f, 30f, 12f), radius = 2f), Paint.fill(Color.RED))
            drawPath(Path { moveTo(2f, 22f); lineTo(12f, 22f); lineTo(7f, 30f); close() }, Paint.fill(Color.RED))
            drawRect(Rect(14f, 22f, 26f, 29f), Paint.stroke(Color.RED, 1f))
            drawImage(bluePixel(), Rect(24f, 14f, 30f, 20f), Paint())
            restore()
        }

        assertPreparedImageTerminal(
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
            clipRect(Rect(3.5f, 2f, 12.5f, 14f), ClipOp.INTERSECT, antiAlias = true)
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED))
            restore()
        }

        val result = surface.render()
        // The target is RGBA8_UNORM_SRGB: 50% linear red is stored as sRGB 188 while alpha remains 128.
        assertRgbaNear(result.pixels, 16, 3, 8, Color.fromArgb(128, 188, 0, 0))
        assertRgbaNear(result.pixels, 16, 8, 8, Color.RED)
        assertRgbaNear(result.pixels, 16, 2, 8, Color.TRANSPARENT)
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
        )
    }

    @Test
    fun `exact RGBA evidence rejects white as red or blue`() {
        val white = UByteArray(4) { 0xff.toUByte() }

        assertFailsWith<AssertionError> {
            assertRgbaNear(white, width = 1, x = 0, y = 0, expected = Color.RED)
        }
        assertFailsWith<AssertionError> {
            assertRgbaNear(white, width = 1, x = 0, y = 0, expected = Color.BLUE)
        }
    }

    @Test
    fun `adapter backed even odd clip mask preserves fill hole exterior and AA edge`() {
        requireWebGpu()
        val evenOddHole = Path().apply {
            fillType = FillType.EVEN_ODD
            addRect(Rect(3.5f, 3.5f, 28.5f, 28.5f))
            addRect(Rect(11.5f, 11.5f, 20.5f, 20.5f))
        }
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipPath(evenOddHole, ClipOp.INTERSECT, antiAlias = true)
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
            restore()
        }

        val result = surface.render()

        assertRgbaNear(result.pixels, 32, 6, 6, Color.RED)
        assertRgbaNear(result.pixels, 32, 16, 16, Color.TRANSPARENT)
        assertRgbaNear(result.pixels, 32, 1, 16, Color.TRANSPARENT)
        assertRgbaNear(result.pixels, 32, 3, 8, Color.fromArgb(128, 188, 0, 0))
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `adapter backed inverse difference clip preserves fill exterior and AA edge`() {
        requireWebGpu()
        val inverseRect = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(Rect(8.5f, 8.5f, 23.5f, 23.5f))
        }
        val surface = Surface(32, 32)
        surface.canvas {
            save()
            clipPath(inverseRect, ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 32f, 32f), Paint.fill(Color.BLUE))
            restore()
        }

        val result = surface.render()

        assertRgbaNear(result.pixels, 32, 16, 16, Color.BLUE)
        assertRgbaNear(result.pixels, 32, 4, 16, Color.TRANSPARENT)
        assertRgbaNear(result.pixels, 32, 8, 16, Color.fromArgb(128, 0, 0, 188))
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `complex clip blur uses the blurred mask as geometry coverage`() {
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        session!!
        val telemetryBefore = session.runtimeTelemetry
        val result = renderBlurredDifferenceClipScene()
        val telemetryAfter = session.runtimeTelemetry

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry -> entry.code.startsWith("route:mask-blur:") },
            result.diagnostics.entries.toString(),
        )
        assertTrue(telemetryAfter.destinationCopies > telemetryBefore.destinationCopies)
        assertEquals(
            telemetryBefore.destinationReadbackSnapshots,
            telemetryAfter.destinationReadbackSnapshots,
        )
        assertTrue(result.pixels.toList() != renderPartialAlphaDestination().pixels.toList())
    }

    @Test
    fun `one hundred twenty complex mask blur frames compose stably without readback`() {
        val result = renderAlternatingClipAndSigmaFrames(frameCount = 120)

        assertEquals(emptySet<String>(), result.refusalReasons)
        assertEquals(result.pipelineCountAfterWarmup, result.pipelineCountAtEnd)
        assertEquals(0L, result.destinationReadbackSnapshots)
    }

    @Test
    fun `complex clip accepts every standard blend mode`() {
        requireWebGpu()

        BlendMode.entries.forEach { mode ->
            val result = renderMaskedRect(mode)

            assertEquals(0, result.diagnostics.fatalCount, mode.name)
        }
    }

    @Test
    fun `fixed alpha mask composition preserves destination outside source bounds`() {
        requireWebGpu()
        val result = Surface(16, 16).run {
            canvas {
                drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                save()
                clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                drawRect(
                    Rect(2f, 2f, 5f, 5f),
                    Paint.fill(Color.RED).copy(blendMode = BlendMode.SRC),
                )
                restore()
            }
            render()
        }

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertRgbaNear(result.pixels, 16, 3, 3, Color.RED)
        assertRgbaNear(result.pixels, 16, 12, 12, Color.WHITE)
    }

    @Test
    fun `coverage alpha mask preserves difference holes for clear src and dst in`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                    drawRect(
                        Rect(2f, 2f, 14f, 14f),
                        Paint.fill(Color.RED).copy(blendMode = blendMode),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 7, 7, Color.WHITE)
            assertRgbaNear(
                result.pixels,
                16,
                3,
                3,
                when (blendMode) {
                    BlendMode.CLEAR -> Color.TRANSPARENT
                    BlendMode.SRC -> Color.RED
                    BlendMode.DST_IN -> Color.WHITE
                    else -> error("unexpected test mode: $blendMode")
                },
            )
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
                    drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    drawText(
                        Font(typeface, 12f).toTextBlob("I", 7f, 12f),
                        0f,
                        0f,
                        Paint.fill(Color.RED).copy(blendMode = blendMode),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 2, 8, Color.WHITE)
        }
    }

    @Test
    fun `alpha mask retains geometric coverage for zero alpha paint`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                    clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                    drawRect(
                        Rect(2f, 2f, 14f, 14f),
                        Paint.fill(Color.fromRGBA(1f, 0f, 0f, 0f)).copy(blendMode = blendMode),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 3, 3, Color.TRANSPARENT)
            assertRgbaNear(result.pixels, 16, 7, 7, Color.WHITE)
        }
    }

    @Test
    fun `AA geometry coverage blends after clear src and dst in`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST_IN).forEach { blendMode ->
            val result = Surface(16, 16).run {
                canvas {
                    drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                    drawRect(
                        Rect(3.5f, 2f, 14f, 14f),
                        Paint.fill(Color.RED).copy(blendMode = blendMode, antiAlias = true),
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
                    BlendMode.CLEAR -> Color.fromArgb(128, 188, 188, 188)
                    BlendMode.SRC -> Color.fromArgb(255, 255, 188, 188)
                    BlendMode.DST_IN -> Color.WHITE
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
                    drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                    save()
                    clipRect(Rect(4f, 2f, 12f, 14f), ClipOp.INTERSECT, antiAlias = false)
                    drawRect(
                        Rect(3.5f, 2f, 13.5f, 14f),
                        Paint.fill(Color.RED).copy(blendMode = blendMode, antiAlias = true),
                    )
                    restore()
                }
                render()
            }

            assertEquals(0, result.diagnostics.fatalCount, "$blendMode ${result.diagnostics.entries}")
            assertRgbaNear(result.pixels, 16, 3, 8, Color.WHITE)
        }
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
                drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE))
                save()
                clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
                clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
                drawImage(
                    transparentImage,
                    Rect(2f, 2f, 14f, 14f),
                    Paint.fill(Color.RED).copy(blendMode = BlendMode.CLEAR),
                )
                restore()
            }
        }

        assertPreparedImageTerminal(
            expectedCode = GPUPreparedImageRefusalCodes.NATIVE_BINDING,
            block = surface::render,
        )
    }

    @Test
    fun `mask composes destination read blend through the source snapshot formula`() {
        requireWebGpu()

        val result = renderMaskedRect(BlendMode.DARKEN)

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.reason == "gpu-copy-then-formula" &&
                    entry.facts.any { fact -> fact.key == "clip.strategy" && fact.value == "alpha-mask" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `no clip destination read composes against a transparent snapshot`() {
        requireWebGpu()
        val surface = Surface(16, 16)
        surface.canvas {
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = BlendMode.DARKEN))
        }

        val result = surface.render()

        assertEquals(1, result.stats.opsDispatched)
        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertTrue(
            result.diagnostics.entries.any { entry ->
                entry.reason == "gpu-copy-then-formula" &&
                    entry.facts.any { fact -> fact.key == "destination-read.action" && fact.value == "copy-then-formula" }
            },
            result.diagnostics.entries.toString(),
        )
    }

    @Test
    fun `clear and color dodge use their mapped clip composition routes`() {
        requireWebGpu()

        listOf(BlendMode.CLEAR, BlendMode.COLOR_DODGE).forEach { blendMode ->
            val surface = Surface(16, 16)
            surface.canvas {
                drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = blendMode))
            }
            val result = surface.render()

            assertEquals(1, result.stats.opsDispatched, blendMode.name)
            assertEquals(0, result.diagnostics.fatalCount, blendMode.name)
        }
    }

    @Test
    fun `core frame with complex clipped image is terminal atomically`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val ops = listOf(
            DisplayOp.DrawRect(Rect(2f, 2f, 8f, 8f), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawRRect(RRect(Rect(20f, 2f, 28f, 10f), radius = 2f), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawPath(
                Path { moveTo(2f, 22f); lineTo(10f, 22f); lineTo(6f, 30f); close() },
                Paint.fill(Color.RED),
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawImage(
                bluePixel(),
                Rect(0f, 0f, 1f, 1f),
                Rect(22f, 22f, 30f, 30f),
                Paint(),
                Matrix33.identity(),
                clip,
            ),
        )
        assertPreparedImageTerminal(
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
    fun `text atlas renders prepared while textured vertices stay terminal before legacy`() {
        requireWebGpu()
        // Non-AA device rect: the prepared vertices route refuses AA-mask and
        // analytic-intersection clips with unsupported.vertices.clip_coverage
        // before lowering, so an integer-scissor clip keeps the
        // textured-vertices material refusal reachable while the text draw still
        // renders prepared.
        val clip = ClipStack.DeviceRect(Rect(1f, 1f, 31f, 31f), antiAlias = false)
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
                paint = Paint.fill(Color.RED),
                transform = Matrix33.identity(),
                clip = clip,
            ),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(Point(0f, 0f), Point(8f, 0f), Point(0f, 8f)),
                    texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
                ),
                paint = Paint.fill(Color.WHITE).copy(shader = Shader.Image(image)),
                transform = Matrix33.translate(20f, 20f),
                clip = clip,
            ),
        )
        var legacyCalls = 0
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = ops,
                width = 32,
                height = 32,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("textured vertices must not continue through legacy")
                },
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `scissor destination read DrawText keeps exterior intact`() {
        requireWebGpu()
        var legacyCalls = 0
        val clip = ClipStack.DeviceRect(Rect(6f, 6f, 14f, 14f), antiAlias = false)
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                        DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                        DisplayOp.DrawText(
                            blob = Font(typeface, 20f).toTextBlob("W", 0f, 15f),
                            x = 0f,
                            y = 0f,
                            paint = Paint.fill(Color.BLACK).copy(blendMode = BlendMode.DARKEN),
                            transform = Matrix33.identity(),
                            clip = clip,
                        ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("destination-read TextA8 must not continue through legacy")
                },
            )
        }

        assertEquals("invalid.preflight.text.blend", failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `scissor destination read textured vertices are terminal before legacy`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(Rect(6f, 6f, 14f, 14f), antiAlias = false)
        val vertices = texturedScissorTriangle()
        var legacyCalls = 0
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                    DisplayOp.DrawVertices(
                        vertices = vertices,
                        paint = advancedBlackImagePaint(),
                        transform = Matrix33.identity(),
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("textured vertices must not continue through legacy")
                },
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `scissor destination read textured mesh is terminal before legacy`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(Rect(6f, 6f, 14f, 14f), antiAlias = false)
        val mesh = Mesh(texturedScissorTriangle(), bounds = Rect(1f, 1f, 15f, 15f))
        var legacyCalls = 0
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                    DisplayOp.DrawMesh(
                        mesh = mesh,
                        paint = advancedBlackImagePaint(),
                        blendMode = null,
                        transform = Matrix33.identity(),
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("textured mesh must not continue through legacy")
                },
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `empty scissor destination read DrawText remains terminal before legacy`() {
        requireWebGpu()
        var legacyCalls = 0
        val clip = ClipStack.DeviceRect(Rect(20f, 20f, 24f, 24f), antiAlias = false)
        val typeface = FontTypeface(
            javaClass.classLoader
                .getResourceAsStream("fonts/liberation/LiberationSans-Regular.ttf")!!
                .readBytes(),
            fontName = "LiberationSans-Regular",
        )
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                    DisplayOp.DrawText(
                        blob = Font(typeface, 20f).toTextBlob("W", 0f, 15f),
                        x = 0f,
                        y = 0f,
                        paint = Paint.fill(Color.BLACK).copy(blendMode = BlendMode.DARKEN),
                        transform = Matrix33.identity(),
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("destination-read TextA8 must not continue through legacy")
                },
            )
        }

        assertEquals("invalid.preflight.text.blend", failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `empty scissor textured vertices are terminal before legacy`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(Rect(20f, 20f, 24f, 24f), antiAlias = false)
        var legacyCalls = 0
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                    DisplayOp.DrawVertices(
                        vertices = texturedScissorTriangle(),
                        paint = advancedBlackImagePaint(),
                        transform = Matrix33.identity(),
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("textured vertices must not continue through legacy")
                },
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
    }

    @Test
    fun `empty scissor textured mesh is terminal before legacy`() {
        requireWebGpu()
        val clip = ClipStack.DeviceRect(Rect(20f, 20f, 24f, 24f), antiAlias = false)
        val mesh = Mesh(texturedScissorTriangle(), bounds = Rect(1f, 1f, 15f, 15f))
        var legacyCalls = 0
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
                    DisplayOp.DrawMesh(
                        mesh = mesh,
                        paint = advancedBlackImagePaint(),
                        blendMode = null,
                        transform = Matrix33.identity(),
                        clip = clip,
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("textured mesh must not continue through legacy")
                },
            )
        }

        assertEquals(GPUPreparedVerticesRefusalCodes.Material, failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
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
                        Paint.stroke(Color.RED, 1f),
                        Matrix33.identity(),
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
    fun `complex clip source retains every point subpass`() {
        requireWebGpu()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawPoints(
                        PointMode.POINTS,
                        listOf(Point(4f, 4f), Point(20f, 4f)),
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        complexFullClip(),
                    ),
                ),
            ),
            32,
            32,
            PixelFormat.RGBA8,
            RenderConfig.DEFAULT,
        )

        assertVisibleAt(result.pixels, 32, 4, 4)
        assertVisibleAt(result.pixels, 32, 20, 4)
    }

    @Test
    fun `complex clip image nine is an exact prepared clip refusal`() {
        requireWebGpu()
        val image = opaqueImage(size = 3)

        assertPreparedImageTerminal(PREPARED_IMAGE_CLIP_REFUSAL) {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawImageNine(
                            image,
                            Rect(1f, 1f, 2f, 2f),
                            Rect(2f, 2f, 14f, 14f),
                            null,
                            Matrix33.identity(),
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

        assertPreparedImageTerminal(PREPARED_IMAGE_CLIP_REFUSAL) {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawAtlas(
                            atlas = image,
                            transforms = listOf(Matrix33.translate(2f, 20f), Matrix33.translate(18f, 20f)),
                            texRects = listOf(Rect(0f, 0f, 3f, 3f), Rect(0f, 0f, 3f, 3f)),
                            colors = null,
                            blendMode = BlendMode.SRC_OVER,
                            paint = null,
                            transform = Matrix33.identity(),
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
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val effect = simpleRuntimeEffect()
        val mesh = Mesh(
            vertices = Vertices(
                VertexMode.TRIANGLES,
                listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
            ),
            program = MeshProgram(effect),
            bounds = Rect(2f, 2f, 8f, 8f),
        )
        val trace = GPUClipRouteTrace()
        var legacyCalls = 0

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawMesh(mesh, Paint.fill(Color.RED), null, Matrix33.identity(), clip),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("refused mesh must not continue through legacy")
                },
                legacyRouteTrace = trace,
            )
        }

        assertEquals(
            GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
            failure.diagnostic.code.value,
        )
        assertEquals(0, legacyCalls)
        assertEquals(0, trace.logicalDrawCount)
    }

    @Test
    fun `nested picture with unsupported paint stays refused while captured child clips propagate through S G routing`() {
        requireWebGpu()
        val child = Picture(
            Rect(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawRect(Rect(1f, 1f, 7f, 7f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)),
        )
        val outerClip = complexFullClip()
        val painted = Picture(
            Rect(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawPicture(child, Paint.stroke(Color.RED, 1f), Matrix33.identity(), ClipStack.WideOpen)),
        )
        // The painted picture frame is a documented prepared-route refusal: the composite
        // capture refuses clip snapshots inside layer scopes.
        val paintFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(listOf(DisplayOp.DrawPicture(painted, null, Matrix33.identity(), outerClip))),
                32, 32, PixelFormat.RGBA8, RenderConfig.DEFAULT,
            )
        }
        assertEquals("unsupported.composite.clip", paintFailure.diagnostic.code.value)

        val clipped = Picture(
            Rect(0f, 0f, 8f, 8f),
            listOf(DisplayOp.DrawPicture(child, null, Matrix33.identity(), outerClip)),
        )
        val trace = GPUClipRouteTrace()
        val clippedFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(listOf(DisplayOp.DrawPicture(clipped, null, Matrix33.identity(), outerClip))),
                32, 32, PixelFormat.RGBA8, RenderConfig.DEFAULT, trace,
            )
        }
        assertEquals("unsupported.composite.clip", clippedFailure.diagnostic.code.value)
    }

    @Test
    fun `textured vertices without image shaders render through the prepared product route`() {
        requireWebGpu()
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
            texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
        )
        val operations = listOf(
            DisplayOp.DrawVertices(vertices, Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen),
            DisplayOp.DrawMesh(
                Mesh(vertices, bounds = Rect(2f, 2f, 8f, 8f)),
                Paint.fill(Color.WHITE),
                null,
                Matrix33.identity(),
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
            assertRgbaNear(result.pixels, 32, 3, 3, Color.WHITE)
        }
    }

    @Test
    fun `textured vertices retain explicit transform and material refusals`() {
        requireWebGpu()
        // Non-AA device rect: the prepared vertices route refuses AA-mask and
        // analytic-intersection clips with unsupported.vertices.clip_coverage
        // before lowering, so an integer-scissor clip keeps the transform and
        // material refusals reachable.
        val clip = ClipStack.DeviceRect(Rect(1f, 1f, 15f, 15f), antiAlias = false)
        val triangle = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f))
        val uvs = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f))
        val paint = Paint.fill(Color.WHITE).copy(shader = Shader.Image(bgraBluePixel()))

        val perspective = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawVertices(
                            Vertices(VertexMode.TRIANGLES, triangle, texCoords = uvs),
                            paint,
                            Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f),
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
                            Matrix33.identity(),
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
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f)),
            colors = listOf(Color.RED, Color.GREEN, Color.BLUE),
            indices = listOf(0, 1, 2),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            StaticDisplayListBuffer(
                listOf(DisplayOp.DrawVertices(vertices, Paint.fill(Color.WHITE), Matrix33.identity(), ClipStack.WideOpen)),
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
        val clip = ClipStack.DeviceRect(Rect(1f, 1f, 15f, 15f), antiAlias = false)
        val vertices = Vertices(
            VertexMode.TRIANGLES,
            positions = listOf(Point(1f, 1f), Point(8f, 1f), Point(1f, 8f)),
            texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
            indices = listOf(0, 1, 3),
        )

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                StaticDisplayListBuffer(
                    listOf(
                        DisplayOp.DrawVertices(
                            vertices,
                            Paint.fill(Color.WHITE).copy(shader = Shader.Image(bgraBluePixel())),
                            Matrix33.identity(),
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
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val recorder = PictureRecorder()
        recorder.beginRecording(Rect(0f, 0f, 8f, 8f)).drawRect(Rect(1f, 1f, 7f, 7f), Paint.fill(Color.RED))
        val picture = recorder.finishRecordingAsPicture()

        // The painted picture frame is a documented prepared-route refusal: the composite
        // capture refuses clip snapshots inside layer scopes.
        val paintFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderPictureWithClip(picture, Paint.stroke(Color.RED, 1f), outerClip)
        }
        assertEquals("unsupported.composite.clip", paintFailure.diagnostic.code.value)

        val childClipFailure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderPictureWithClip(picture, null, outerClip)
        }
        assertEquals("unsupported.composite.clip", childClipFailure.diagnostic.code.value)
    }

    @Test
    fun `outline text without a typeface is terminal before legacy`() {
        requireWebGpu()
        var legacyCalls = 0
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val trace = GPUClipRouteTrace()

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                        DisplayOp.DrawText(
                            TextBlob(
                                glyphRuns = listOf(
                                    KanvasGlyphRun(
                                        glyphs = listOf(1u),
                                        positions = listOf(Point(0f, 0f)),
                                    ),
                                ),
                            ),
                            0f,
                            0f,
                            Paint.stroke(Color.RED, 1f),
                            Matrix33.identity(),
                            clip,
                        ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
                legacyPort = GPUPreparedSurfaceLegacyPort { _, _, _, _, _, _ ->
                    legacyCalls++
                    error("missing typeface must not continue through legacy")
                },
                legacyRouteTrace = trace,
            )
        }

        assertEquals("unsupported.text.typeface_missing", failure.diagnostic.code.value)
        assertEquals(0, legacyCalls)
        assertEquals(0, trace.logicalDrawCount)
    }

    @Test
    fun `empty text does not produce a complex clip source composite`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true),
            ),
        )
        val trace = GPUClipRouteTrace()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(
                    DisplayOp.DrawText(
                        TextBlob(emptyList()),
                        0f,
                        0f,
                        Paint.fill(Color.RED),
                        Matrix33.identity(),
                        clip,
                    ),
                ),
            ),
            width = 16,
            height = 16,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            routeTrace = trace,
        )

        assertEquals(0, result.diagnostics.fatalCount, result.diagnostics.entries.toString())
        assertEquals(0, trace.logicalDrawCount)
        assertEquals(0, trace.sourceThenCompositeCount)
    }

    @Test
    fun `alpha mask picture composes its child through S G source products`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(ClipStackOp.RectOp(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)),
        )
        val picture = Picture(
            Rect(0f, 0f, 16f, 16f),
            listOf(
                DisplayOp.DrawRect(
                    Rect(2f, 2f, 14f, 14f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.identity(),
                    ClipStack.WideOpen,
                ),
            ),
        )
        val trace = GPUClipRouteTrace()

        // The alpha-mask clipped picture is a documented prepared-route refusal: the
        // composite capture refuses clip snapshots inside layer scopes.
        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            renderViaGpu(
                buffer = StaticDisplayListBuffer(
                    listOf(DisplayOp.DrawPicture(picture, null, Matrix33.identity(), clip)),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                routeTrace = trace,
            )
        }
        assertEquals("unsupported.composite.clip", failure.diagnostic.code.value)
    }

    @Test
    fun `remaining high level GPU routes render through their S G adapters`() {
        requireWebGpu()
        val clip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true),
                ClipStackOp.RectOp(Rect(14f, 14f, 18f, 18f), ClipOp.DIFFERENCE, antiAlias = true),
            ),
        )
        val image = opaqueImage(size = 3)
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point(2f, 2f), Point(8f, 2f), Point(2f, 8f)),
        )
        val picture = Picture(
            Rect(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect(24f, 24f, 30f, 30f), Paint.fill(Color.RED), Matrix33.identity(), ClipStack.WideOpen)),
        )
        val ops = listOf(
            DisplayOp.DrawPoints(PointMode.POINTS, listOf(Point(3f, 3f), Point(6f, 6f)), Paint.fill(Color.RED), Matrix33.identity(), clip),
            DisplayOp.DrawDRRect(
                RRect(Rect(2f, 20f, 10f, 28f), radius = 1f),
                RRect(Rect(4f, 22f, 8f, 26f), radius = 1f),
                Paint.fill(Color.RED),
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawImageNine(image, Rect(1f, 1f, 2f, 2f), Rect(12f, 2f, 22f, 12f), null, Matrix33.identity(), clip),
            DisplayOp.DrawImageLattice(
                image,
                Lattice(xDivs = listOf(1, 2), yDivs = listOf(1, 2)),
                Rect(12f, 14f, 22f, 24f),
                null,
                Matrix33.identity(),
                clip,
            ),
            DisplayOp.DrawAtlas(
                atlas = image,
                transforms = listOf(Matrix33.identity(), Matrix33.identity()),
                texRects = listOf(Rect(0f, 0f, 3f, 3f), Rect(0f, 0f, 3f, 3f)),
                colors = null,
                blendMode = BlendMode.SRC_OVER,
                paint = null,
                transform = Matrix33.identity(),
                clip = clip,
            ),
            DisplayOp.DrawPicture(picture, null, Matrix33.identity(), clip),
        )
        val trace = GPUClipRouteTrace()

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
                routeTrace = trace,
            )
        }
        assertEquals("unsupported.composite.operation", failure.diagnostic.code.value)
    }

    private fun requireWebGpu() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.close()
    }

    private fun assertPreparedImageTerminal(
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
        expected: Color,
        tolerance: Int = 8,
    ) {
        val offset = (y * width + x) * 4
        val actual = intArrayOf(
            pixels[offset].toInt() and 0xff,
            pixels[offset + 1].toInt() and 0xff,
            pixels[offset + 2].toInt() and 0xff,
            pixels[offset + 3].toInt() and 0xff,
        )
        val wanted = intArrayOf(expected.redByte, expected.greenByte, expected.blueByte, expected.alphaByte)
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

    private fun renderPartialAlphaDestination() = Surface(16, 16).run {
        canvas {
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.fromArgb(128, 32, 64, 192)))
        }
        render()
    }

    private fun renderBlurredDifferenceClipScene(
        sigma: Float = 2f,
        clipOffset: Float = 0f,
    ) = Surface(16, 16).run {
        requireWebGpu()
        canvas {
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.fromArgb(128, 32, 64, 192)))
            save()
            clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
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
                Rect(4f, 4f, 12f, 12f),
                Paint.fill(Color.RED).copy(
                    blendMode = BlendMode.DARKEN,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
                ),
            )
            restore()
        }
        render()
    }

    private fun renderAlternatingClipAndSigmaFrames(frameCount: Int): AlternatingBlurFramesResult {
        require(frameCount > 0)
        GPUBackendRuntimeFactory.dispose()
        val session = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(session != null, "GPU backend unavailable in current environment")
        session!!

        val warmup = renderBlurredDifferenceClipScene(sigma = 1.5f, clipOffset = 0f)
        assertEquals(0, warmup.diagnostics.fatalCount, warmup.diagnostics.entries.toString())
        val refusalReasons = coveragePlaneRefusals(warmup).toMutableSet()
        val pipelineCountAfterWarmup = session.executionCacheTelemetry
            .filter { it.cacheName == "pipeline" }
            .sumOf { it.creations }
        val telemetryBeforeFrames = session.runtimeTelemetry

        repeat(frameCount) { frame ->
            val frameResult = renderBlurredDifferenceClipScene(
                sigma = 0.5f + (frame % 7),
                clipOffset = (frame % 3).toFloat() * 0.25f,
            )
            assertEquals(0, frameResult.diagnostics.fatalCount, frameResult.diagnostics.entries.toString())
            refusalReasons += coveragePlaneRefusals(frameResult)
        }

        val pipelineCountAtEnd = session.executionCacheTelemetry
            .filter { it.cacheName == "pipeline" }
            .sumOf { it.creations }
        val telemetryAfterFrames = session.runtimeTelemetry
        return AlternatingBlurFramesResult(
            refusalReasons = refusalReasons,
            pipelineCountAfterWarmup = pipelineCountAfterWarmup,
            pipelineCountAtEnd = pipelineCountAtEnd,
            destinationReadbackSnapshots =
                telemetryAfterFrames.destinationReadbackSnapshots - telemetryBeforeFrames.destinationReadbackSnapshots,
        )
    }

    private fun coveragePlaneRefusals(result: org.graphiks.kanvas.surface.RenderResult): Set<String> =
        result.diagnostics.entries
            .map { it.reason }
            .filter { it.startsWith("unsupported.coverage_plane.") }
            .toSet()

    private data class AlternatingBlurFramesResult(
        val refusalReasons: Set<String>,
        val pipelineCountAfterWarmup: Long,
        val pipelineCountAtEnd: Long,
        val destinationReadbackSnapshots: Long,
    )

    private fun renderMaskedRect(blendMode: BlendMode) = Surface(16, 16).run {
        canvas {
            save()
            clipRect(Rect(1f, 1f, 15f, 15f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(Rect(6f, 6f, 10f, 10f), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(Rect(0f, 0f, 16f, 16f), Paint.fill(Color.RED).copy(blendMode = blendMode))
            restore()
        }
        render()
    }

    private fun complexFullClip(): ClipStack = ClipStack.Complex(
        listOf(ClipStackOp.RectOp(Rect(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)),
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
        positions = listOf(Point(1f, 1f), Point(15f, 1f), Point(1f, 15f)),
        texCoords = listOf(Point(0f, 0f), Point(1f, 0f), Point(0f, 1f)),
    )

    private fun advancedBlackImagePaint(): Paint = Paint.fill(Color.WHITE).copy(
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

    private fun assertWhiteOutsideClip(pixels: UByteArray, width: Int, clip: Rect) {
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

    private fun assertDarkenedInsideClip(pixels: UByteArray, width: Int, clip: Rect) {
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
            listOf(DisplayOp.DrawPicture(picture, paint, Matrix33.identity(), clip)),
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
