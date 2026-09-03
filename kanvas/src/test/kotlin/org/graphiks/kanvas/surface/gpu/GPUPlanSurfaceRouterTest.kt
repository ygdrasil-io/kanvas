package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfaceSubmitResult
import org.graphiks.kanvas.paint.BlendMode
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
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GPUPlanSurfaceRouterTest {
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
    fun `command limit admits 512 candidates and leaves 513 operations on legacy`() {
        val legacy = legacyResult()
        val operations = List(513) { DisplayOp.Annotation(RectF32.Empty, "key", "value") }
        val router = routerReturningInvalid("non-finite-value")

        assertFailsWith<IllegalStateException> {
            router.render(operations.take(512), 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT) { legacy }
        }
        val result = router.render(operations, 1, 1, PixelFormat.RGBA8, RenderConfig.DEFAULT) { legacy }

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
    fun `W3 Ready submission terminal never returns the legacy pixel sentinel`() {
        val legacy = legacyResult()
        val readyToken = object : GpuW3SurfaceReadyToken {}

        val failure = assertFailsWith<GPUPlanSurfaceTerminalException> {
            GPUPlanSurfaceRouter(
                w3Port = object : W3SurfacePlanSubmitPort {
                    override fun plan(
                        scene: SceneSnapshot,
                        target: RenderTargetDescriptor,
                        frameLocalBudgetBytes: Long,
                    ): GpuW3SurfacePlanResult = GpuW3SurfacePlanResult.Ready(readyToken)

                    override fun submit(token: GpuW3SurfaceReadyToken): GpuW3SurfaceSubmitResult =
                        GpuW3SurfaceSubmitResult.Terminal(
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
}
