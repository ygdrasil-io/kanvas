package org.graphiks.kanvas.surface.gpu

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.text.FontTypeface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Lattice
import org.graphiks.kanvas.types.LatticeFlags
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUPreparedSurfaceProductNativeSmokeTest {
    @AfterTest
    fun disposeSharedRuntime() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun `hardware sRGB store preserves semi transparent premultiplied readback`() {
        val color = Color.fromArgb(a = 160, r = 40, g = 120, b = 208)
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(rect(Rect.fromLTRB(0f, 0f, 4f, 4f), color)),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        )
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(31, 96, 169, 160))
    }

    @Test
    fun `mixed direct path direct frame uses the prepared product route with exact native evidence`() {
        // Prime the shared executor's cached session deterministically, independent of test
        // order: render one frame through the shared port, then dispose the runtime. The dispose
        // forces the next frame to open a fresh runtime with a new device generation, which the
        // executor observes as a generation boundary: it invalidates the primed session and
        // prepares a new one. The asserted frame below therefore always sees (targetCreations 1,
        // targetCloses 0), whether or not a previous test in the class already rendered.
        renderViaGpu(
            buffer = StaticDisplayListBuffer(listOf(rect(Rect.fromLTRB(0f, 0f, 4f, 4f), Color.RED))),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
        )
        GPUBackendRuntimeFactory.dispose()

        val operations = listOf(
            rect(Rect.fromLTRB(1f, 1f, 7f, 7f), Color.RED),
            DisplayOp.DrawPath(
                triangle(),
                Paint.fill(Color.GREEN).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            rect(Rect.fromLTRB(22f, 18f, 30f, 26f), Color.BLUE),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(operations),
            width = 32,
            height = 32,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        assertEquals(1, decisions.size)
        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertPixel(result.pixels.toByteArray(), 32, 3, 3, listOf(255, 0, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 14, 5, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 32, 25, 21, listOf(0, 0, 255, 255))
        assertPixel(result.pixels.toByteArray(), 32, 31, 31, listOf(0, 0, 0, 0))

        assertEquals(1L, evidence.targetCreations)
        // The primed session from above is already closed by dispose at the device-generation
        // boundary, so this frame's evidence is invalidation+recreate, not a second close.
        assertEquals(0L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(1L, evidence.renderPasses)
        assertEquals(0L, evidence.draws)
        assertEquals(4L, evidence.drawIndexed)
        assertEquals(4L, evidence.pipelineBinds)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)

        assertEquals(3, result.stats.opsDispatched)
        assertEquals(0, result.stats.opsRefused)
        assertEquals(Math.toIntExact(evidence.draws + evidence.drawIndexed), result.stats.drawCallCount)
        assertEquals(Math.toIntExact(evidence.pipelineBinds), result.stats.pipelineCount)
        assertEquals(false, result.stats.coverageMeasured)
    }

    @Test
    fun `image only product frame executes prepared without a synthetic core draw`() {
        val source = image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "product-image-only",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(
                    drawImage(
                        source,
                        Rect.fromLTRB(1f, 1f, 3f, 3f),
                        SamplingOptions.NEAREST,
                    ),
                ),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val prepared = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        )
        assertEquals(
            GPUPreparedSurfaceExecutionRouteMarker.PreparedSurfaceDirect,
            prepared.evidence.routeMarker,
        )
        assertEquals(1L, prepared.evidence.submits)
        assertEquals(0, prepared.evidence.activeNativePayloads)
        assertEquals(1, result.stats.opsDispatched)
        assertPixel(result.pixels.toByteArray(), 4, 0, 0, listOf(0, 0, 0, 0))
        assertPixel(result.pixels.toByteArray(), 4, 1, 1, listOf(188, 0, 0, 128))
        assertPixel(result.pixels.toByteArray(), 4, 2, 1, listOf(0, 188, 0, 128))
        assertPixel(result.pixels.toByteArray(), 4, 1, 2, listOf(0, 0, 188, 128))
        assertPixel(result.pixels.toByteArray(), 4, 2, 2, listOf(188, 188, 188, 128))
    }

    @Test
    fun `direct prepared image frame preserves native pixels ordering and ownership`() {
        val rgba = image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "native-rgba",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
        )
        val bgra = image(
            width = GPUPreparedImageTestFixtures.bgraOpaque2x2Width,
            height = GPUPreparedImageTestFixtures.bgraOpaque2x2Height,
            colorType = GPUPreparedImageTestFixtures.bgraOpaque2x2ColorType,
            sourceId = "native-bgra",
            pixels = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes,
        )
        val alpha = image(
            width = GPUPreparedImageTestFixtures.a8_3x1Width,
            height = GPUPreparedImageTestFixtures.a8_3x1Height,
            colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
            sourceId = "native-a8",
            pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val linear = image(
            width = 2,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "native-linear",
            pixels = byteArrayOf(
                0, 0, 0, 255.toByte(),
                255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
            ),
        )
        val operations = listOf(
            rect(Rect.fromLTRB(0f, 0f, 2f, 2f), Color.RED),
            drawImage(rgba, Rect.fromLTRB(3f, 0f, 5f, 2f), SamplingOptions.NEAREST),
            drawImage(bgra, Rect.fromLTRB(6f, 0f, 8f, 2f), SamplingOptions.NEAREST),
            drawImage(
                alpha,
                Rect.fromLTRB(9f, 0f, 12f, 1f),
                SamplingOptions.NEAREST,
                Paint.fill(Color.RED),
            ),
            drawImage(linear, Rect.fromLTRB(13f, 0f, 14f, 1f), SamplingOptions.LINEAR),
            rect(Rect.fromLTRB(15f, 0f, 17f, 2f), Color.BLUE),
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 18,
                height = 4,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(execution, execution.toString())

        assertPixel(result.rgba, 18, 0, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 3, 0, listOf(188, 0, 0, 128))
        assertPixel(result.rgba, 18, 6, 0, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 18, 10, 0, listOf(188, 0, 0, 128))
        assertTrue(
            GPUPreparedImagePixelOracle.matchesWithinOneLsb(
                result.rgba.copyOfRange((13 * 4), (13 * 4) + 4),
                byteArrayOf(188.toByte(), 188.toByte(), 188.toByte(), 255.toByte()),
            ),
        )
        assertPixel(result.rgba, 18, 15, 0, listOf(0, 0, 255, 255))

        val evidence = result.evidence
        assertEquals(1L, evidence.targetCreations)
        assertEquals(0L, evidence.targetCloses)
        assertEquals(1L, evidence.frameCoordinatorCreations)
        assertEquals(1L, evidence.encoders)
        assertEquals(1L, evidence.commandBuffers)
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0L, evidence.destinationSnapshotCreations)
        assertEquals(0L, evidence.destinationReadbackSnapshots)
        assertEquals(3L, evidence.renderPasses)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, evidence.outputOwnedNativePayloads)
        assertEquals(0, evidence.quarantinedNativePayloads)
        assertEquals(evidence.retentionRegistrations, evidence.retentionCompletions)
        assertEquals(0L, evidence.retentionQuarantines)
        assertEquals(1, evidence.distinctRetentionTickets)
        assertEquals(operations.size, result.visualOperationCount)
    }

    @Test
    fun `direct prepared nine and mixed lattice preserve pixels affine placement and route order`() {
        val gridImage = image(
            width = GPUPreparedImageTestFixtures.imageNine6x6Width,
            height = GPUPreparedImageTestFixtures.imageNine6x6Height,
            colorType = GPUPreparedImageTestFixtures.imageNine6x6ColorType,
            sourceId = "native-grid",
            pixels = GPUPreparedImageTestFixtures.imageNine6x6Bytes,
        )
        val operations = listOf(
            DisplayOp.DrawImageNine(
                image = gridImage,
                center = Rect.fromLTRB(2f, 2f, 4f, 4f),
                dst = Rect.fromLTRB(0f, 0f, 18f, 18f),
                paint = null,
                transform = Matrix3x3F32.translation(1f, 1f),
                clip = ClipStack.WideOpen,
            ),
            DisplayOp.DrawImageLattice(
                image = gridImage,
                lattice = Lattice(
                    xDivs = listOf(2, 4),
                    yDivs = emptyList(),
                    rects = listOf(
                        Rect.fromLTRB(20f, 0f, 28f, 6f),
                        Rect.fromLTRB(24f, 0f, 32f, 6f),
                        Rect.fromLTRB(32f, 0f, 38f, 6f),
                    ),
                    colors = listOf(Color.TRANSPARENT, Color.GREEN, Color.TRANSPARENT),
                    flags = listOf(
                        LatticeFlags.DEFAULT,
                        LatticeFlags.FIXED_COLOR,
                        LatticeFlags.TRANSPARENT,
                    ),
                ),
                dst = Rect.fromLTRB(20f, 0f, 38f, 6f),
                paint = Paint.fill(Color.WHITE).copy(antiAlias = false),
                transform = Matrix3x3F32.translation(0f, 10f),
                clip = ClipStack.WideOpen,
                sampling = SamplingOptions.NEAREST,
            ),
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val execution = GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 40,
                height = 20,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )

        assertPixel(result.rgba, 40, 2, 2, listOf(255, 255, 255, 255))
        assertPixel(result.rgba, 40, 10, 2, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 40, 2, 10, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 40, 10, 10, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 40, 18, 18, listOf(255, 255, 255, 255))
        assertPixel(result.rgba, 40, 21, 11, listOf(255, 255, 255, 255))
        // Both emitted lattice cells cover this pixel; green proves fixed cell 1 ran after sampled cell 0.
        assertPixel(
            result.rgba,
            40,
            25,
            11,
            listOf(0, 255, 0, 255),
        )
        assertPixel(result.rgba, 40, 30, 11, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 40, 37, 11, listOf(0, 0, 0, 0))
        assertEquals(11, result.visualOperationCount)
        assertEquals(1L, result.evidence.targetCreations)
        assertEquals(0L, result.evidence.targetCloses)
        assertEquals(1L, result.evidence.submits)
        assertEquals(1L, result.evidence.readbackCopies)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct affine atlas executes exact integral scissor through the product gate`() {
        val atlas = image(
            width = GPUPreparedImageTestFixtures.atlas4x4Width,
            height = GPUPreparedImageTestFixtures.atlas4x4Height,
            colorType = GPUPreparedImageTestFixtures.atlas4x4ColorType,
            sourceId = "native-atlas",
            pixels = GPUPreparedImageTestFixtures.atlas4x4Bytes,
        )
        val operations = listOf(
            rect(Rect.fromLTRB(12f, 12f, 16f, 16f), Color.GREEN),
            DisplayOp.DrawAtlas(
                atlas = atlas,
                transforms = listOf(
                    Matrix3x3F32.translation(2f, 2f),
                    Matrix3x3F32.translation(8f, 2f),
                ),
                texRects = listOf(
                    Rect.fromLTRB(0f, 0f, 2f, 2f),
                    Rect.fromLTRB(2f, 0f, 4f, 2f),
                ),
                colors = listOf(Color.BLUE, Color.RED),
                blendMode = BlendMode.SRC,
                paint = Paint.fill(Color.WHITE),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.DeviceRect(
                    rect = Rect.fromLTRB(3f, 2f, 11f, 4f),
                    antiAlias = false,
                ),
            ),
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = operations,
            target = GPUTargetFacts(16, 16, "rgba8unorm-srgb"),
            config = RenderConfig.DEFAULT,
            capabilities = GPUProductFlagConfig().buildCapabilities(),
        )
        assertEquals(null, inventory.preparedRefusal)
        assertTrue(inventory.visualCommands.any { it.normalized.source.operation == "drawAtlas" })
        val candidate = assertIs<GPUPreparedSurfaceEligibility.Candidate>(
            GPUPreparedSurfaceFrameGate.classify(operations, RenderConfig.DEFAULT),
        )
        assertEquals(operations, candidate.operations)

        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceNativeBackendPortFactory,
        ).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 16,
                height = 16,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )

        assertPixel(result.rgba, 16, 2, 3, listOf(0, 0, 0, 0))
        assertPixel(result.rgba, 16, 3, 3, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 16, 10, 3, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 16, 11, 3, listOf(0, 0, 0, 0))
        assertPixel(result.rgba, 16, 13, 13, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct A8 atlas applies nontrivial paint tint and alpha once in all source modes`() {
        val atlas = image(
            width = GPUPreparedImageTestFixtures.a8_3x1Width,
            height = GPUPreparedImageTestFixtures.a8_3x1Height,
            colorType = GPUPreparedImageTestFixtures.a8_3x1ColorType,
            sourceId = "native-a8-atlas-modes",
            pixels = GPUPreparedImageTestFixtures.a8_3x1Bytes,
        )
        val modes = listOf(
            BlendMode.SRC,
            BlendMode.DST,
            BlendMode.SRC_OVER,
            BlendMode.PLUS,
            BlendMode.MODULATE,
        )
        val operations = buildList {
            add(rect(Rect.fromLTRB(6f, 10f, 8f, 12f), Color.GREEN))
            modes.forEachIndexed { index, mode ->
                add(
                    DisplayOp.DrawAtlas(
                        atlas = atlas,
                        transforms = listOf(Matrix3x3F32.translation(0f, (index * 2).toFloat())),
                        texRects = listOf(Rect.fromLTRB(0f, 0f, 3f, 1f)),
                        colors = listOf(Color.fromArgb(160, 192, 96, 32)),
                        blendMode = mode,
                        paint = Paint.fill(Color.fromArgb(192, 128, 64, 160)),
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                )
            }
        }
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val execution = GPUPreparedSurfaceFrameExecutor(
            GPUPreparedSurfaceNativeBackendPortFactory,
        ).execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 8,
                height = 12,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )
        val halfCoverage = listOf(
            listOf(84, 41, 37, 60),
            listOf(121, 87, 134, 96),
            listOf(111, 68, 92, 96),
            listOf(121, 87, 134, 96),
            listOf(84, 41, 37, 60),
        )
        val fullCoverage = listOf(
            listOf(117, 60, 54, 120),
            listOf(165, 120, 183, 192),
            listOf(153, 95, 127, 192),
            listOf(165, 120, 183, 192),
            listOf(117, 60, 54, 120),
        )

        modes.indices.forEach { index ->
            val y = index * 2
            assertPixel(result.rgba, 8, 0, y, listOf(0, 0, 0, 0))
            assertPixelWithinOne(result.rgba, 8, 1, y, halfCoverage[index])
            assertPixelWithinOne(result.rgba, 8, 2, y, fullCoverage[index])
        }
        assertPixel(result.rgba, 8, 7, 11, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `direct RGBA atlas ignores paint RGB and applies paint alpha once in all source modes`() {
        val atlas = image(
            width = 1,
            height = 1,
            colorType = ColorType.RGBA_8888,
            sourceId = "native-rgba-atlas-modes",
            pixels = byteArrayOf(96, 48, 24, 160.toByte()),
        )
        val modes = listOf(
            BlendMode.SRC,
            BlendMode.DST,
            BlendMode.SRC_OVER,
            BlendMode.PLUS,
            BlendMode.MODULATE,
        )
        val paints = listOf(
            Paint.fill(Color.fromArgb(192, 128, 64, 160)),
            Paint.fill(Color.fromArgb(192, 32, 224, 96)),
        )
        val operations = buildList {
            add(rect(Rect.fromLTRB(2f, 10f, 4f, 12f), Color.GREEN))
            modes.forEachIndexed { index, mode ->
                paints.forEachIndexed { paintIndex, paint ->
                    add(
                        DisplayOp.DrawAtlas(
                            atlas = atlas,
                            transforms = listOf(
                                Matrix3x3F32.translation(
                                    paintIndex.toFloat(),
                                    (index * 2).toFloat(),
                                ),
                            ),
                            texRects = listOf(Rect.fromLTRB(0f, 0f, 1f, 1f)),
                            colors = listOf(Color.fromArgb(176, 80, 160, 48)),
                            blendMode = mode,
                            paint = paint,
                            transform = Matrix3x3F32.Identity,
                            clip = ClipStack.WideOpen,
                        ),
                    )
                }
            }
        }
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )

        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            GPUPreparedSurfaceFrameExecutor(
                GPUPreparedSurfaceNativeBackendPortFactory,
            ).execute(
                GPUPreparedSurfaceExecutionRequest(
                    candidate = GPUPreparedSurfaceEligibility.Candidate(
                        operations = operations,
                        config = RenderConfig.DEFAULT,
                        color = color,
                    ),
                    width = 4,
                    height = 12,
                ),
            ),
        )
        val expected = listOf(
            listOf(112, 155, 88, 133),
            listOf(108, 52, 24, 120),
            listOf(126, 157, 89, 170),
            listOf(152, 162, 92, 192),
            listOf(51, 33, 4, 83),
        )

        modes.indices.forEach { index ->
            assertPixelWithinOne(result.rgba, 4, 0, index * 2, expected[index])
            assertPixelWithinOne(result.rgba, 4, 1, index * 2, expected[index])
        }
        assertPixel(result.rgba, 4, 3, 11, listOf(0, 255, 0, 255))
        assertEquals("prepared.surface.direct", result.evidence.routeMarker.stableLabel)
        assertEquals(1L, result.evidence.submits)
        assertEquals(0, result.evidence.activeNativePayloads)
    }

    @Test
    fun `heterogeneous Core Image Text Vertices Core frame uses one prepared submission and one readback`() {
        val typeface = FontTypeface(
            GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer(),
            "Task 14 heterogeneous COLRv0",
        )
        val image = Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = ColorType.RGBA_8888,
            sourceId = "task14-heterogeneous-image",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = AlphaType.PREMUL,
        )
        val operations = listOf(
            rect(Rect.fromLTRB(0f, 0f, 4f, 4f), Color.RED),
            drawImage(
                image,
                Rect.fromLTRB(48f, 0f, 50f, 2f),
                SamplingOptions.NEAREST,
            ),
            text(typeface, GPUPreparedTextTestFixtures.A8_GLYPH_ID, 12, 58, Color.WHITE),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(
                        Point2F32(0f, 0f),
                        Point2F32(4f, 0f),
                        Point2F32(0f, 4f),
                    ),
                ),
                paint = Paint.fill(Color.GREEN).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
            rect(Rect.fromLTRB(6f, 0f, 10f, 4f), Color.BLUE),
        )
        var captured: GPUPreparedSurfaceFrameBuildResult.Ready? = null
        val executor = GPUPreparedSurfaceFrameExecutor(
            backendFactory = GPUPreparedSurfaceNativeBackendPortFactory,
            frameBuilder = { request ->
                GPUPreparedSurfaceFrameBuilder.build(request).also { result ->
                    if (result is GPUPreparedSurfaceFrameBuildResult.Ready) captured = result
                }
            },
        )
        val color = assertIs<GPUPreparedSurfaceColorMapping.Ready>(
            RenderConfig.DEFAULT.mapPreparedGpuColorConfig(),
        )
        val execution = executor.execute(
            GPUPreparedSurfaceExecutionRequest(
                candidate = GPUPreparedSurfaceEligibility.Candidate(
                    operations = operations,
                    config = RenderConfig.DEFAULT,
                    color = color,
                ),
                width = 160,
                height = 96,
                output = GPUPreparedSurfaceRequestedOutput.ReadbackRgba,
            ),
        )
        val result = assertIs<GPUPreparedSurfaceExecutionResult.Succeeded>(
            execution,
            execution.toString(),
        )
        val semanticOrder = checkNotNull(captured).taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .mapNotNull(GPUDrawPacket::semanticPayload)
            .map(GPUDrawSemanticPayload::canonicalType)
        assertEquals(
            listOf("CorePrimitive", "SampledImage", "TextA8", "Vertices", "CorePrimitive"),
            semanticOrder,
        )

        // Pixel order: the vertices triangle overlaps the first core rect and is painted
        // after it, so the overlap pixel must be the vertices green.
        assertPixel(result.rgba, 160, 1, 1, listOf(0, 255, 0, 255))
        assertPixel(result.rgba, 160, 2, 3, listOf(255, 0, 0, 255))
        assertPixel(result.rgba, 160, 7, 0, listOf(0, 0, 255, 255))
        assertPixel(result.rgba, 160, 48, 0, listOf(188, 0, 0, 128))
        val textDelta = deltaWithinOneLsb(
            result.rgba,
            160,
            16,
            58,
            GPUPreparedTextPixelOracle.a8SourceOver(
                GPUPreparedTextPixelOracle.StraightSrgb(255, 255, 255),
                paintAlpha = 1f,
                coverage = 255,
            ).bytes(),
        )
        assertTrue(textDelta <= 1, "heterogeneous text pixel delta=$textDelta")

        // The vertices overlap region must match the Task 13 CPU oracle (red rect under a
        // green triangle) within one LSB of the declared UNORM quantization.
        val expectedVertices = GPUPreparedVerticesCpuOracle.renderVertices(
            listOf(
                GPUPreparedVerticesTestFixture.create(
                    positions = floatArrayOf(0f, 0f, 4f, 0f, 0f, 4f, 4f, 0f, 4f, 4f, 0f, 4f),
                    colorsRgba8 = byteArrayOf(
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                    ),
                    indices = intArrayOf(0, 1, 2, 1, 4, 2, 2, 4, 5, 0, 2, 5),
                    topology = GPUPreparedVerticesTopology.TRIANGLES,
                    pixelWidth = 4,
                    pixelHeight = 4,
                ),
                GPUPreparedVerticesTestFixture.create(
                    positions = floatArrayOf(0f, 0f, 4f, 0f, 0f, 4f),
                    colorsRgba8 = byteArrayOf(
                        0, 255.toByte(), 0, 255.toByte(),
                        0, 255.toByte(), 0, 255.toByte(),
                        0, 255.toByte(), 0, 255.toByte(),
                    ),
                    topology = GPUPreparedVerticesTopology.TRIANGLES,
                    pixelWidth = 4,
                    pixelHeight = 4,
                ),
            ),
        )
        val actualRegion = ByteArray(4 * 4 * 4)
        for (regionY in 0 until 4) {
            for (regionX in 0 until 4) {
                val source = (regionY * 160 + regionX) * 4
                val target = (regionY * 4 + regionX) * 4
                (0 until 4).forEach { channel ->
                    actualRegion[target + channel] = result.rgba[source + channel]
                }
            }
        }
        val verticesDelta = GPUPreparedVerticesCpuOracle.comparePixels(
            actualRegion,
            expectedVertices,
        )
        assertTrue(
            verticesDelta.matchesWithinOneLsb,
            "heterogeneous vertices region maxChannelDelta=${verticesDelta.maxChannelDelta} " +
                "differing=${verticesDelta.differingChannels}/${verticesDelta.comparedChannels}",
        )

        assertEquals(4, result.visualOperationCount)
        assertEquals(1L, result.evidence.encoders)
        assertEquals(1L, result.evidence.commandBuffers)
        assertEquals(1L, result.evidence.submits)
        assertEquals(1L, result.evidence.readbackCopies)
        assertEquals(0, result.evidence.activeNativePayloads)
        assertEquals(0, result.evidence.outputOwnedNativePayloads)
        assertEquals(0, result.evidence.quarantinedNativePayloads)
        assertEquals(result.evidence.retentionRegistrations, result.evidence.retentionCompletions)
        assertEquals(0L, result.evidence.retentionQuarantines)
        assertEquals(1, result.evidence.distinctRetentionTickets)
        assertEquals(5L, result.evidence.renderPasses)
        assertTrue(result.evidence.draws + result.evidence.drawIndexed >= 2L)
        assertTrue(
            captured.taskList.tasks
                .filterIsInstance<GPUTask.Upload>()
                .any { upload -> upload.destination.value.contains("prepared-vertices") },
            "vertices upload must be planned before its consuming render",
        )
        println(
            "task14.native heterogeneous prepared=true skipped=0 submits=" +
                "${result.evidence.submits} readbacks=${result.evidence.readbackCopies} " +
                "renderPasses=${result.evidence.renderPasses} " +
                "verticesDelta=${verticesDelta.maxChannelDelta} textDelta=$textDelta",
        )
    }

    @Test
    fun `vertices frame routes through the product gate as prepared with exact native pixels`() {
        val operations = listOf(
            rect(Rect.fromLTRB(0f, 0f, 4f, 4f), Color.RED),
            DisplayOp.DrawVertices(
                vertices = Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f)),
                ),
                paint = Paint.fill(Color.GREEN).copy(antiAlias = false),
                transform = Matrix3x3F32.Identity,
                clip = ClipStack.WideOpen,
            ),
        )
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(operations),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        // The green triangle covers x+y<=4; pixels beyond it keep the red rect. Interior
        // pixels avoid the exact x+y=4 rasterization tie.
        assertPixel(result.pixels.toByteArray(), 4, 1, 1, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 2, 0, listOf(0, 255, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 3, 2, listOf(255, 0, 0, 255))
        assertPixel(result.pixels.toByteArray(), 4, 1, 3, listOf(255, 0, 0, 255))
        assertEquals(1L, evidence.submits)
        assertEquals(1L, evidence.readbackCopies)
        assertEquals(0, evidence.activeNativePayloads)
        assertEquals(0, result.stats.opsRefused)
    }

    @Test
    fun `bgra8 surface renders prepared with native BGRA byte order and exact format`() {
        val decisions = mutableListOf<GPUPreparedSurfaceRouteDecision>()

        val result = renderViaGpu(
            buffer = StaticDisplayListBuffer(
                listOf(rect(Rect.fromLTRB(0f, 0f, 2f, 1f), Color.RED)),
            ),
            width = 2,
            height = 1,
            format = PixelFormat.BGRA8,
            config = RenderConfig.DEFAULT,
            preparedRouteTrace = GPUPreparedSurfaceRouteTrace(decisions::add),
        )

        val evidence = assertIs<GPUPreparedSurfaceRouteDecision.Prepared>(
            decisions.single(),
            decisions.single().toString(),
        ).evidence
        assertEquals(1L, evidence.submits)
        assertEquals(PixelFormat.BGRA8, result.format)
        assertContentEquals(
            byteArrayOf(0, 0, 255.toByte(), 255.toByte(), 0, 0, 255.toByte(), 255.toByte()),
            result.pixels.toByteArray(),
        )
    }

    @Test
    fun `unregistered mesh program is terminal through the product gate`() {
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point2F32(0f, 0f), Point2F32(4f, 0f), Point2F32(0f, 4f)),
        )

        val failure = assertFailsWith<GPUPreparedSurfaceTerminalException> {
            GPUPreparedSurfaceProductEntry.render(
                operations = listOf(
                    DisplayOp.DrawMesh(
                        mesh = org.graphiks.kanvas.types.Mesh(
                            vertices = triangle,
                            program = org.graphiks.kanvas.paint.MeshProgram(
                                effect = org.graphiks.kanvas.pipeline.RuntimeEffect(
                                    id = "not.registered",
                                    module = org.graphiks.kanvas.pipeline.ShaderModule.fromSource(
                                        "fixture",
                                    ),
                                    uniformLayout = org.graphiks.kanvas.pipeline.UniformLayout(
                                        emptyList(),
                                    ),
                                    children = emptyList(),
                                ),
                                uniforms = org.graphiks.kanvas.pipeline.UniformBlock {},
                            ),
                            bounds = Rect.fromLTRB(0f, 0f, 4f, 4f),
                        ),
                        paint = Paint.fill(Color.GREEN).copy(antiAlias = false),
                        blendMode = null,
                        transform = Matrix3x3F32.Identity,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                executionPort =
                    GPUPreparedSurfaceFrameExecutor(GPUPreparedSurfaceNativeBackendPortFactory),
            )
        }

        assertEquals(
            org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
                .MeshProgramUnregistered,
            failure.diagnostic.code.value,
        )
    }

    private fun rect(bounds: Rect, color: Color) = DisplayOp.DrawRect(
        bounds,
        Paint.fill(color).copy(antiAlias = false),
        Matrix3x3F32.Identity,
        ClipStack.WideOpen,
    )

    private fun text(
        typeface: FontTypeface,
        glyphId: Int,
        x: Int,
        baselineY: Int,
        color: Color,
    ): DisplayOp.DrawText = DisplayOp.DrawText(
        blob = TextBlob(
            glyphRuns = listOf(
                KanvasGlyphRun(
                    glyphs = listOf(glyphId.toUShort()),
                    positions = listOf(Point2F32(0f, 0f)),
                    fontSize = 48f,
                ),
            ),
            typeface = typeface,
            fontSize = 48f,
        ),
        x = x.toFloat(),
        y = baselineY.toFloat(),
        paint = Paint.fill(color),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun deltaWithinOneLsb(
        rgba: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: ByteArray,
    ): Int {
        val offset = (y * width + x) * 4
        return GPUPreparedImagePixelOracle.maxChannelDelta(
            rgba.copyOfRange(offset, offset + 4),
            expected,
        )
    }

    private fun drawImage(
        image: Image,
        dst: Rect,
        sampling: SamplingOptions,
        paint: Paint = Paint.fill(Color.WHITE),
    ) = DisplayOp.DrawImage(
        image = image,
        src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
        dst = dst,
        paint = paint.copy(shader = Shader.Image(image, sampling = sampling)),
        transform = Matrix3x3F32.Identity,
        clip = ClipStack.WideOpen,
    )

    private fun image(
        width: Int,
        height: Int,
        colorType: ColorType,
        sourceId: String,
        pixels: ByteArray,
    ) = Image(
        width = width,
        height = height,
        colorType = colorType,
        sourceId = sourceId,
        pixels = pixels,
        alphaType = AlphaType.PREMUL,
    )

    private fun triangle(): Path = Path().apply {
        moveTo(10f, 2f)
        lineTo(19f, 2f)
        lineTo(14f, 11f)
        close()
    }

    private fun assertPixel(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        assertEquals(expected, (0..3).map { bytes[offset + it].toInt() and 0xff }, "pixel ($x,$y)")
    }

    private fun assertPixelWithinOne(
        bytes: ByteArray,
        width: Int,
        x: Int,
        y: Int,
        expected: List<Int>,
    ) {
        val offset = (y * width + x) * 4
        val actual = bytes.copyOfRange(offset, offset + 4)
        val expectedBytes = expected.map(Int::toByte).toByteArray()
        assertTrue(
            GPUPreparedImagePixelOracle.maxChannelDelta(actual, expectedBytes) <= 1,
            "pixel ($x,$y) expected=$expected actual=${actual.map { it.toInt() and 0xff }}",
        )
    }

    private class StaticDisplayListBuffer(
        private val operations: List<DisplayOp>,
    ) : DisplayListBuffer {
        override fun append(op: DisplayOp) = error("static buffer")
        override fun ops(): List<DisplayOp> = operations
    }
}
