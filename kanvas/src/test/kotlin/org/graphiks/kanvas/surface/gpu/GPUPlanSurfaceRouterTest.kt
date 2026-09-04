package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameChannelOrder
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameMetrics
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameOutput
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceSubmitResult
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.render.ir.DrawOrigin
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
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GPUPlanSurfaceRouterTest {
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
        val rrect = assertIs<org.graphiks.kanvas.render.ir.SceneCommand.Draw>(requireNotNull(captured).commandAt(1)).node
        assertEquals(DrawOrigin.RRECT, rrect.origin)
        assertIs<GeometryNode.RRect>(rrect.geometry)
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
    fun `double rounded rectangles and paths retain the legacy frame before planning`() {
        val cases = listOf(
            DisplayOp.DrawDRRect(
                roundedRect(),
                RRectF32.of(RectF32.ofLTRB(2f, 2f, 3f, 3f), radius = 0.5f),
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawPath(
                Path().addRect(RectF32.ofLTRB(0f, 0f, 4f, 4f)),
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
        )

        cases.forEach { operation ->
            val legacy = legacyResult()
            val result = GPUPlanSurfaceRouter(
                planPort = object : GPUPlanSurfacePort {
                    override fun plan(
                        scene: SceneSnapshot,
                        target: RenderTargetDescriptor,
                        frameLocalBudgetBytes: Long,
                    ): GpuPlanSurfacePlanResult = error("DrawDRRect and DrawPath must not reach planning")

                    override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
                        error("DrawDRRect and DrawPath must not submit a prepared frame")
                },
            ).render(
                operations = listOf(operation),
                width = 4,
                height = 4,
                format = PixelFormat.RGBA8,
                config = RenderConfig.DEFAULT,
                legacy = { legacy },
            )

            assertContentEquals(legacy.pixels, result.pixels, operation::class.simpleName)
        }
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
    fun `ready submission terminal never returns the legacy pixel sentinel`() {
        val legacy = legacyResult()
        val readyToken = object : GpuPlanSurfaceReadyToken {}

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
                    DisplayOp.DrawColor(
                        ColorARGB.Red,
                        BlendMode.SRC_OVER,
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
