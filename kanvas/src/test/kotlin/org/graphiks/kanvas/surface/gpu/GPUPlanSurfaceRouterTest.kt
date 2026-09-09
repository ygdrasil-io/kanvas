package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.DrawPathSourceOperation
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.toPathF32
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameChannelOrder
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameMetrics
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameOutput
import org.graphiks.kanvas.gpu.renderer.planning.GpuRenderContext
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceSubmitResult
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.GPUColorFormat
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats
import org.graphiks.kanvas.surface.W4cPathFillCpuOracle
import org.graphiks.kanvas.surface.W4dPathStrokeCpuOracle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.Matrix3x3F32

@OptIn(ExperimentalUnsignedTypes::class)
class GPUPlanSurfaceRouterTest {
    @Test
    fun `W4e hard ordered mask clips reach public 1x completion rather than legacy`() {
        val context = GpuRenderContext.createProduction()
        try {
            val clip = ClipStack.Complex(
                listOf(
                    ClipStackOp.RectOp(RectF32.ofLTRB(1f, 1f, 7f, 7f), ClipOp.INTERSECT, antiAlias = false),
                    ClipStackOp.RectOp(RectF32.ofLTRB(3f, 3f, 5f, 5f), ClipOp.DIFFERENCE, antiAlias = false),
                ),
            )
            val operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 8f, 8f)) },
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        clip,
                    ),
                )
            val result = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = operations,
                width = 8,
                height = 8,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { error("W4e complex clips must not fall back after candidate admission") },
            )

            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(result, 2, 2))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 4, 4))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 0, 0))
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4e hard inverse draw reaches public 1x D24 completion and recovers for a second frame`() {
        val inverse = Path {
            moveTo(3f, 3f)
            lineTo(13f, 3f)
            lineTo(4f, 13f)
            close()
        }.apply { fillType = FillType.INVERSE_WINDING }
        val operations = listOf(
            DisplayOp.DrawPath(
                inverse,
                Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.Complex(listOf(ClipStackOp.PathOp(Path(), ClipOp.DIFFERENCE, antiAlias = false))),
            ),
        )
        val context = GpuRenderContext.createProduction()
        try {
            repeat(2) {
                val result = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                    operations = operations,
                    width = 16,
                    height = 16,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = { error("W4e inverse path must not fall back after candidate admission") },
                )

                assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(result, 0, 0))
                assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 5, 5))
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4e mixed frame keeps a W4d DeviceRect sibling scissored`() {
        val complexClip = ClipStack.Complex(
            listOf(
                ClipStackOp.RectOp(RectF32.ofLTRB(4f, 4f, 8f, 8f), ClipOp.INTERSECT, antiAlias = false),
                ClipStackOp.RectOp(RectF32.ofLTRB(6f, 6f, 7f, 7f), ClipOp.DIFFERENCE, antiAlias = false),
            ),
        )
        val fullFrame = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 10f, 10f)) }
        val operations = listOf(
            DisplayOp.DrawPath(fullFrame, Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, complexClip),
            DisplayOp.DrawPath(
                fullFrame,
                Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.DeviceRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), antiAlias = false),
            ),
        )
        val context = GpuRenderContext.createProduction()
        try {
            val result = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = operations,
                width = 10,
                height = 10,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { error("A mixed W4e/W4d frame must not fall back after candidate admission") },
            )

            assertContentEquals(ubyteArrayOf(0u, 0u, 255u, 255u), pixelAt(result, 1, 1))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 3, 1))
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(result, 4, 4))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 6, 6))
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4e inverse draw intersects its domain with a DeviceRect`() {
        val inverse = Path {
            moveTo(4f, 4f)
            lineTo(8f, 4f)
            lineTo(4f, 8f)
            close()
        }.apply { fillType = FillType.INVERSE_WINDING }
        val context = GpuRenderContext.createProduction()
        try {
            val result = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = listOf(
                    DisplayOp.DrawPath(
                        inverse,
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.DeviceRect(RectF32.ofLTRB(2f, 2f, 10f, 10f), antiAlias = false),
                    ),
                ),
                width = 12,
                height = 12,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { error("A DeviceRect-bounded inverse draw must not fall back after candidate admission") },
            )

            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 0, 0))
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(result, 2, 2))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 5, 5))
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 10, 2))
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4e public frames with varied native geometry reuse available buffers and recover`() {
        val shapes = listOf(
            Path { moveTo(8f, 1f); lineTo(15f, 15f); lineTo(1f, 15f); close() },
            Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 15f, 15f)) },
            Path { moveTo(8f, 1f); lineTo(15f, 6f); lineTo(12f, 15f); lineTo(4f, 15f); lineTo(1f, 6f); close() },
            Path { moveTo(8f, 1f); lineTo(14f, 4f); lineTo(15f, 11f); lineTo(8f, 15f); lineTo(1f, 11f); lineTo(2f, 4f); close() },
        )
        val fullFrame = Path().apply { addRect(RectF32.ofLTRB(0f, 0f, 16f, 16f)) }
        val harmlessHole = Path().apply { addRect(RectF32.ofLTRB(14f, 14f, 15f, 15f)) }
        val context = GpuRenderContext.createProduction()
        try {
            val router = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context))
            fun render(clipPath: Path): RenderResult = router.render(
                operations = listOf(
                    DisplayOp.DrawPath(
                        fullFrame,
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.Complex(
                            listOf(
                                ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false),
                                ClipStackOp.PathOp(harmlessHole, ClipOp.DIFFERENCE, antiAlias = false),
                            ),
                        ),
                    ),
                ),
                width = 16,
                height = 16,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { error("W4e geometry variants must not fall back after candidate admission") },
            )

            (shapes + shapes.first()).forEachIndexed { index, clipPath ->
                val result = try {
                    render(clipPath)
                } catch (failure: GPUPlanSurfaceTerminalException) {
                    throw AssertionError("W4e native-buffer frame $index was refused", failure)
                }
                assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(result, 8, 8))
                assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(result, 0, 0))
            }
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4dGeneral AA capability gap is terminal without legacy publication`() {
        val path = Path().apply {
            moveTo(1f, 2f)
            lineTo(4f, 2f)
            lineTo(4f, 4f)
            lineTo(1f, 4f)
            close()
        }
        val context = GpuRenderContext.createProduction()
        try {
            val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
                GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                    operations = listOf(
                        DisplayOp.DrawPath(
                            path,
                            Paint.fill(ColorARGB.Red).copy(antiAlias = true),
                            Matrix3x3F32.skewing(0.25f, 0f),
                            ClipStack.WideOpen,
                        ),
                    ),
                    width = 8,
                    height = 8,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = { error("A W4d.2 capability terminal must not enter the legacy path") },
                )
            }

            assertEquals("w4d.general.texture-sample-support-unavailable", failure.code)
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4dGeneral horizon crossing is terminal before legacy transform refusal`() {
        val path = Path().apply {
            moveTo(1f, 2f)
            lineTo(4f, 2f)
            lineTo(4f, 4f)
            lineTo(1f, 4f)
            close()
        }
        val context = GpuRenderContext.createProduction()
        try {
            val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
                GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                    operations = listOf(
                        DisplayOp.DrawPath(
                            path,
                            Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                            Matrix3x3F32(persp0 = -0.5f),
                            ClipStack.WideOpen,
                        ),
                    ),
                    width = 8,
                    height = 8,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = { error("A W4d.2 projection horizon must not enter the legacy path") },
                )
            }

            assertEquals("w4d.general.projection-horizon-crossing", failure.code)
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4d public DrawPath uses the generic Scene route and completes native readback`() {
        val path = Path().apply {
            moveTo(1f, 2.125f)
            lineTo(6f, 2.125f)
        }
        val paint = Paint.stroke(ColorARGB.of(255, 49, 173, 224), 1.5f).copy(antiAlias = false)
        val expected = W4dPathStrokeCpuOracle.render(
            7,
            5,
            listOf(W4dPathStrokeCpuOracle.Draw(path.toPathF32(), paint, scissorI32 = RectI32(0, 0, 7, 5))),
        )
        val context = GpuRenderContext.createProduction()
        try {
            val result = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = listOf(DisplayOp.DrawPath(path, paint, Matrix3x3F32.Identity, ClipStack.WideOpen)),
                width = 7,
                height = 5,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { error("A W4d stroke must not enter the legacy Surface route") },
            )

            assertContentEquals(expected, result.pixels)
            assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4d one and 512 draws plan while 513 is terminal before submit or legacy publication`() {
        val path = Path().apply {
            moveTo(0.25f, 0.5f)
            lineTo(0.75f, 0.5f)
        }
        val paint = Paint.stroke(ColorARGB.White, 0.5f).copy(antiAlias = false)
        val operation = DisplayOp.DrawPath(path, paint, Matrix3x3F32.Identity, ClipStack.WideOpen)
        val context = GpuRenderContext.createProduction()
        try {
            val executor = context.planSurfaceExecutor()
            listOf(1, 512).forEach { countI32 ->
                val scene = assertIs<SceneCaptureResult.Captured>(
                    DisplayOpSceneAdapter.capture(List(countI32) { operation }, org.graphiks.kanvas.render.ir.SceneExtent(1, 1), ColorSpace.SRGB),
                ).scene
                assertIs<GpuPlanSurfacePlanResult.Ready>(
                    executor.plan(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace), RenderConfig.DEFAULT.frameLocalBudgetBytes),
                )
            }

            val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
                GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                    operations = List(513) { operation },
                    width = 1,
                    height = 1,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = { error("A promoted W4d limit must not publish legacy pixels") },
                )
            }
            assertEquals("w4d.path-resource-limit", failure.code)
        } finally {
            context.close()
        }
    }

    @Test
    fun `W4d terminal planner and submit outcomes never fall back`() {
        val path = Path().apply {
            moveTo(0f, 0.5f)
            lineTo(1f, 0.5f)
        }
        val operation = DisplayOp.DrawPath(
            path,
            Paint.stroke(ColorARGB.Red, 1f).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        listOf("w4d.scene-invalid", "w4d.path-resource-limit", "w4d.capability-unavailable").forEach { code ->
            val failure = assertFailsWith<GPUPlanSurfaceTerminalException>(code) {
                GPUPlanSurfaceRouter(
                    planPort = object : GPUPlanSurfacePort {
                        override fun plan(
                            scene: SceneSnapshot,
                            target: RenderTargetDescriptor,
                            frameLocalBudgetBytes: Long,
                        ): GpuPlanSurfacePlanResult = GpuPlanSurfacePlanResult.Terminal(
                            listOf(
                                RenderDiagnostic(
                                    RenderDiagnosticCode(code),
                                    RenderDiagnosticDomain.RESOURCE,
                                    RenderDiagnosticSeverity.ERROR,
                                    code,
                                ),
                            ),
                        )

                        override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                            error("A terminal W4d plan cannot submit")
                    },
                ).render(
                    operations = listOf(operation),
                    width = 1,
                    height = 1,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = { error("A terminal W4d result cannot fall back") },
                )
            }
            assertEquals(code, failure.code)
        }
    }

    @Test
    fun `hard edge triangle reaches the prepared W4c route instead of the legacy sentinel`() {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 0f)
            lineTo(0f, 4f)
            close()
        }
        val expected = W4cPathFillCpuOracle.render(
            widthI32 = 4,
            heightI32 = 4,
            draws = listOf(
                W4cPathFillCpuOracle.Draw(
                    path = triangle.toPathF32(),
                    transform = Matrix3x3F32.Identity,
                    color = ColorARGB.Red,
                    scissorI32 = org.graphiks.math.geometry.RectI32(0, 0, 4, 4),
                ),
            ),
        )
        val readyToken = object : GpuPlanSurfaceReadyToken {}
        val legacy = legacyResult()

        val result = GPUPlanSurfaceRouter(
            planPort = object : GPUPlanSurfacePort {
                override fun plan(
                    scene: SceneSnapshot,
                    target: RenderTargetDescriptor,
                    frameLocalBudgetBytes: Long,
                ): GpuPlanSurfacePlanResult = GpuPlanSurfacePlanResult.Ready(readyToken)

                override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                    GpuPlanSurfaceSubmitResult.Completed(
                        GpuFrameOutput.of(
                            width = 4,
                            height = 4,
                            rowStrideBytes = 16,
                            channelOrder = GpuFrameChannelOrder.RGBA,
                            bytes = expected.toByteArray(),
                            metrics = GpuFrameMetrics(1, 1, 1, 1f, true),
                            diagnostics = emptyList(),
                            structuralSteps = emptyList(),
                            nativeEvidenceCounters = emptyMap(),
                            nativeEvidenceScopeKinds = listOf("Render", "Readback"),
                        ),
                    )
            },
        ).render(
            operations = listOf(
                DisplayOp.DrawPath(
                    triangle,
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            legacy = { legacy },
        )

        assertContentEquals(expected, result.pixels)
        assertFalse(result.pixels.contentEquals(legacy.pixels))
        assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    }

    @Test
    fun `text expanded paths remain on the legacy sentinel before W4c planning`() {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(1f, 0f)
            lineTo(0f, 1f)
            close()
        }
        val legacy = legacyResult()

        val result = GPUPlanSurfaceRouter(
            planPort = object : GPUPlanSurfacePort {
                override fun plan(
                    scene: SceneSnapshot,
                    target: RenderTargetDescriptor,
                    frameLocalBudgetBytes: Long,
                ): GpuPlanSurfacePlanResult = error("TEXT_EXPANDED_PATH must not reach prepared planning")

                override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                    error("TEXT_EXPANDED_PATH must not submit a prepared frame")
            },
        ).render(
            operations = listOf(
                DisplayOp.DrawPath.withSourceOperation(
                    path = path,
                    paint = Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    transform = Matrix3x3F32.Identity,
                    clip = ClipStack.WideOpen,
                    sourceOperation = DrawPathSourceOperation.TEXT_EXPANDED,
                ),
            ),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            legacy = { legacy },
        )

        assertContentEquals(legacy.pixels, result.pixels)
    }

    @Test
    fun `W4c mixed frame retains the legacy sentinel while inverse path promotes to W4e`() {
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 0f)
            lineTo(0f, 4f)
            close()
        }
        val hardFill = Paint.fill(ColorARGB.Red).copy(antiAlias = false)
        val path = DisplayOp.DrawPath(triangle, hardFill, Matrix3x3F32.Identity, ClipStack.WideOpen)
        val inverse = Path().apply {
            moveTo(0f, 0f)
            lineTo(4f, 0f)
            lineTo(0f, 4f)
            close()
            fillType = FillType.INVERSE_WINDING
        }
        val context = GpuRenderContext.createProduction()
        try {
            val legacy = legacyResult()
            val mixed = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = listOf(
                    path,
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(0f, 0f, 1f, 1f),
                        hardFill,
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )
            assertContentEquals(legacy.pixels, mixed.pixels, "mixed-frame")

            val promotedInverse = GPUPlanSurfaceRouter(planPort = capabilityChainPort(context)).render(
                operations = listOf(DisplayOp.DrawPath(inverse, hardFill, Matrix3x3F32.Identity, ClipStack.WideOpen)),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = ::legacyResult,
            )
            assertEquals(4, promotedInverse.width)
            assertContentEquals(ubyteArrayOf(0u, 0u, 0u, 0u), pixelAt(promotedInverse, 0, 0))
            assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), pixelAt(promotedInverse, 3, 3))
        } finally {
            context.close()
        }
    }

    @Test
    fun `mixed solid rect and rrect frame returns the prepared result`() {
        val readyToken = object : GpuPlanSurfaceReadyToken {}
        val bytes = ByteArray(4 * 4 * 4) { index -> index.toByte() }
        var captured: SceneSnapshot? = null
        val result = GPUPlanSurfaceRouter(
            planPort = object : GPUPlanSurfacePort {
                override fun plan(
                    scene: SceneSnapshot,
                    target: RenderTargetDescriptor,
                    frameLocalBudgetBytes: Long,
                ): GpuPlanSurfacePlanResult {
                    captured = scene
                    return GpuPlanSurfacePlanResult.Ready(readyToken)
                }

                override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                    GpuPlanSurfaceSubmitResult.Completed(
                        GpuFrameOutput.of(
                            width = 4,
                            height = 4,
                            rowStrideBytes = 16,
                            channelOrder = GpuFrameChannelOrder.RGBA,
                            bytes = bytes,
                            metrics = GpuFrameMetrics(2, 1, 2, 1f, true),
                            diagnostics = emptyList(),
                            structuralSteps = emptyList(),
                            nativeEvidenceCounters = emptyMap(),
                            nativeEvidenceScopeKinds = emptyList(),
                        ),
                    )
            },
        ).render(
            operations = mixedFrameOperations(),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            legacy = { error("A W4b-admissible mixed frame must not enter the legacy route") },
        )

        assertContentEquals(bytes.toUByteArray(), result.pixels)
        val nodes = listOf(0, 1).map { index ->
            assertIs<org.graphiks.kanvas.render.ir.SceneCommand.Draw>(requireNotNull(captured).commandAt(index)).node
        }
        assertEquals(listOf(DrawOrigin.RECT, DrawOrigin.RRECT), nodes.map { it.origin })
        assertIs<GeometryNode.RRect>(nodes[1].geometry)
    }

    @Test
    fun `rrect clips and gradients retain the legacy frame when planning reports a gap`() {
        val rrect = roundedRect()
        val rrectClip = ClipStack.Complex(listOf(ClipStackOp.RRectOp(rrect, ClipOp.INTERSECT)))
        val gradientPaint = Paint(
            shader = Shader.LinearGradient(
                Point2F32(0f, 0f),
                Point2F32(4f, 0f),
                listOf(GradientStop(0f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Red)),
            ),
        )
        val cases = listOf(
            DisplayOp.DrawRRect(rrect, Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, rrectClip),
            DisplayOp.DrawRRect(rrect, gradientPaint, Matrix3x3F32.Identity, ClipStack.WideOpen),
        )

        cases.forEach { operation ->
            val legacy = legacyResult()
            val result = GPUPlanSurfaceRouter(
                planPort = object : GPUPlanSurfacePort {
                    override fun plan(
                        scene: SceneSnapshot,
                        target: RenderTargetDescriptor,
                        frameLocalBudgetBytes: Long,
                    ): GpuPlanSurfacePlanResult = GpuPlanSurfacePlanResult.GapNotMigrated(emptyList())

                    override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                        error("A planning gap must not issue a ready token")
                },
            ).render(
                operations = listOf(operation),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )

            assertContentEquals(legacy.pixels, result.pixels)
        }
    }

    @Test
    fun `double rounded rectangles retain the legacy frame before planning`() {
        val operation = DisplayOp.DrawDRRect(
            roundedRect(),
            RRectF32.of(RectF32.ofLTRB(2f, 2f, 3f, 3f), radius = 0.5f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val legacy = legacyResult()
        val result = GPUPlanSurfaceRouter(
            planPort = object : GPUPlanSurfacePort {
                override fun plan(
                    scene: SceneSnapshot,
                    target: RenderTargetDescriptor,
                    frameLocalBudgetBytes: Long,
                ): GpuPlanSurfacePlanResult = error("DrawDRRect must not reach planning")

                override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                    error("DrawDRRect must not submit a prepared frame")
            },
        ).render(
            operations = listOf(operation),
            width = 4,
            height = 4,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            legacy = { legacy },
        )

        assertContentEquals(legacy.pixels, result.pixels)
    }

    @Test
    fun `clear keeps the legacy whole frame result before scene capture`() {
        val legacy = legacyResult()

        val result = routerReturningInvalid("non-finite-value").render(
            operations = listOf(DisplayOp.Clear(org.graphiks.math.color.ColorARGB.Red)),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig.DEFAULT,
            legacy = { legacy },
        )

        assertContentEquals(legacy.pixels, result.pixels)
    }

    @Test
    fun `a non W3 physical target keeps the legacy whole frame result`() {
        val legacy = legacyResult()

        val result = routerReturningInvalid("non-finite-value").render(
            operations = listOf(DisplayOp.Annotation(RectF32.Empty, "key", "value")),
            width = 1,
            height = 1,
            format = PixelFormat.RGBA8,
            config = RenderConfig(gpuColorFormat = GPUColorFormat.RGBA8_UNORM),
            legacy = { legacy },
        )

        assertContentEquals(legacy.pixels, result.pixels)
    }

    @Test
    fun `capture node and resource limits retain the legacy result`() {
        listOf("scene-node-limit", "scene-resource-limit", "graph-node-limit").forEach { code ->
            val legacy = legacyResult()
            val result = routerReturningInvalid(code).render(
                operations = listOf(DisplayOp.Annotation(RectF32.Empty, "key", "value")),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )
            assertContentEquals(legacy.pixels, result.pixels, code)
        }
    }

    @Test
    fun `capture corruptions terminate instead of rendering a second legacy frame`() {
        listOf("non-finite-value", "atlas-cardinality", "scene-capture-invalid", "unexpected-corruption").forEach { code ->
            val failure = assertFailsWith<GPUPlanSurfaceTerminalException>(code) {
                routerReturningInvalid(code).render(
                    operations = listOf(DisplayOp.Annotation(RectF32.Empty, "key", "value")),
                    width = 1,
                    height = 1,
                    format = PixelFormat.RGBA8,
                    config = RenderConfig.DEFAULT,
                    legacy = ::legacyResult,
                )
            }
            assertEquals(code, failure.code, code)
        }
    }

    @Test
    fun `empty capture invalid is terminal and never returns the legacy sentinel`() {
        val legacy = legacyResult()
        val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
            GPUPlanSurfaceRouter(
                capturePort = SceneCapturePort { _, _, _, _ -> SceneCaptureResult.Invalid(emptyList()) },
            ).render(
                operations = listOf(DisplayOp.Annotation(RectF32.Empty, "key", "value")),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )
        }

        assertEquals("w3.surface.unknown", failure.code)
    }

    @Test
    fun `ready W4c submission terminal never returns the legacy pixel sentinel`() {
        val legacy = legacyResult()
        val readyToken = object : GpuPlanSurfaceReadyToken {}
        val triangle = Path().apply {
            moveTo(0f, 0f)
            lineTo(1f, 0f)
            lineTo(0f, 1f)
            close()
        }

        val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
            GPUPlanSurfaceRouter(
                planPort = object : GPUPlanSurfacePort {
                    override fun plan(
                        scene: SceneSnapshot,
                        target: RenderTargetDescriptor,
                        frameLocalBudgetBytes: Long,
                    ): GpuPlanSurfacePlanResult = GpuPlanSurfacePlanResult.Ready(readyToken)

                    override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                        GpuPlanSurfaceSubmitResult.Terminal(
                            listOf(
                                RenderDiagnostic(
                                    RenderDiagnosticCode("w3.lowering.incompatible_plan"),
                                    RenderDiagnosticDomain.RESOURCE,
                                    RenderDiagnosticSeverity.ERROR,
                                    "The prepared W3 plan became incompatible after planning.",
                                ),
                            ),
                        )
                },
            ).render(
                operations = listOf(
                    DisplayOp.DrawPath(
                        triangle,
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                width = 1,
                height = 1,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )
        }

        assertEquals("w3.lowering.incompatible_plan", failure.code)
    }

    private fun routerReturningInvalid(code: String): GPUPlanSurfaceRouter = GPUPlanSurfaceRouter(
        capturePort = SceneCapturePort { _, _, _, _ ->
            SceneCaptureResult.Invalid(
                listOf(
                    RenderDiagnostic(
                        RenderDiagnosticCode(code),
                        RenderDiagnosticDomain.SCENE,
                        RenderDiagnosticSeverity.ERROR,
                        code,
                    ),
                ),
            )
        },
    )

    private fun capabilityChainPort(context: GpuRenderContext): GPUPlanSurfacePort = object : GPUPlanSurfacePort {
        private val executor = context.planSurfaceExecutor()

        override fun plan(
            scene: SceneSnapshot,
            target: RenderTargetDescriptor,
            frameLocalBudgetBytes: Long,
        ): GpuPlanSurfacePlanResult = executor.plan(scene, target, frameLocalBudgetBytes)

        override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
            executor.submit(token)
    }

    private fun pixelAt(result: RenderResult, x: Int, y: Int): UByteArray {
        val offset = (y * result.width + x) * 4
        return result.pixels.copyOfRange(offset, offset + 4)
    }

    private fun legacyResult() = RenderResult(
        pixels = ubyteArrayOf(9u, 8u, 7u, 6u),
        width = 1,
        height = 1,
        format = PixelFormat.RGBA8,
        colorSpace = ColorSpace.SRGB,
        diagnostics = Diagnostics(),
        stats = RenderStats(0, 0, 0, 0, 0f),
    )

    private fun mixedFrameOperations(): List<DisplayOp> {
        val fullScissor = ClipStack.DeviceRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), antiAlias = false)
        return listOf(
            DisplayOp.DrawRect(RectF32.ofLTRB(0f, 0f, 2f, 2f), Paint.fill(ColorARGB.Blue), Matrix3x3F32.Identity, fullScissor),
            DisplayOp.DrawRRect(roundedRect(), Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, fullScissor),
        )
    }

    private fun roundedRect(): RRectF32 = RRectF32.of(
        RectF32.ofLTRB(1f, 1f, 4f, 4f),
        CornerRadiiF32.of(1f, 1f),
        CornerRadiiF32.of(2f, 1f),
        CornerRadiiF32.of(1f, 2f),
        CornerRadiiF32.of(0.5f, 1f),
    )
}
