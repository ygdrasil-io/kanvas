package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.renderer.planning.GpuFrameOutput
import org.graphiks.kanvas.render.ir.DisplayOpSceneAdapter
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceSubmitResult
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.RenderStats

internal fun interface SceneCapturePort {
    fun capture(
        operations: List<DisplayOp>,
        extent: SceneExtent,
        colorSpace: ColorSpace,
        limits: SceneCaptureLimits,
    ): SceneCaptureResult
}

/** Bounded GPU plan product seam: an admitted scene must plan before its token can submit. */
internal interface GPUPlanSurfacePort {
    fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        frameLocalBudgetBytes: Long,
    ): GpuPlanSurfacePlanResult

    fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult
}

internal class GPUPlanSurfaceTerminalException(
    val code: String,
    message: String,
) : IllegalStateException("$code: $message")

/** Whole-frame GPU plan routing. A promoted frame never enters the legacy route after planning. */
internal class GPUPlanSurfaceRouter(
    private val capturePort: SceneCapturePort = SceneCapturePort(DisplayOpSceneAdapter::capture),
    private val captureLimits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT,
    private val planPort: GPUPlanSurfacePort = ProductionGPUPlanSurfacePort(),
) {
    fun render(
        operations: List<DisplayOp>,
        width: Int,
        height: Int,
        format: PixelFormat,
        config: RenderConfig,
        legacy: () -> RenderResult,
    ): RenderResult {
        if (width <= 0 || height <= 0) {
            throw GPUPlanSurfaceTerminalException("w3.surface.invalid_dimensions", "Surface dimensions must be positive.")
        }
        if (!GPUPlanSurfaceCandidateGate.accepts(operations, config)) return legacy()

        val extent = SceneExtent(width, height)
        val scene = when (val captured = capturePort.capture(operations, extent, ColorSpace.SRGB, captureLimits)) {
            is SceneCaptureResult.Captured -> captured.scene
            is SceneCaptureResult.Invalid -> {
                if (captured.diagnostics.isNotEmpty() &&
                    captured.diagnostics.all { it.code.value in CAPTURE_LIMIT_CODES }
                ) return legacy()
                throw terminal(captured.diagnostics)
            }
        }
        return when (
            val planned = planPort.plan(
                scene,
                RenderTargetDescriptor(extent, ColorSpace.SRGB),
                config.frameLocalBudgetBytes,
            )
        ) {
            is GpuPlanSurfacePlanResult.GapNotMigrated -> legacy()
            is GpuPlanSurfacePlanResult.Terminal -> throw terminal(planned.diagnostics)
            is GpuPlanSurfacePlanResult.Ready -> when (val submitted = planPort.submit(planned.token)) {
                is GpuPlanSurfaceSubmitResult.Completed -> completed(submitted.output, format)
                is GpuPlanSurfaceSubmitResult.Terminal -> throw terminal(submitted.diagnostics)
            }
        }
    }

    private fun completed(output: GpuFrameOutput, format: PixelFormat): RenderResult {
        val bytes = output.copyBytes()
        val publicBytes = when (format) {
            PixelFormat.RGBA8 -> bytes
            PixelFormat.BGRA8 -> bytes.also { buffer ->
                buffer.indices.step(4).forEach { index ->
                    val red = buffer[index]
                    buffer[index] = buffer[index + 2]
                    buffer[index + 2] = red
                }
            }
        }
        val diagnostics = Diagnostics().apply {
            output.diagnostics().forEach { diagnostic ->
                warn(diagnostic.code.value, "w3", diagnostic.message)
            }
        }
        return RenderResult(
            pixels = publicBytes.toUByteArray(),
            width = output.width,
            height = output.height,
            format = format,
            colorSpace = ColorSpace.SRGB,
            diagnostics = diagnostics,
            stats = RenderStats(
                opsDispatched = output.metrics.opsDispatched,
                opsRefused = 0,
                pipelineCount = output.metrics.pipelineCount,
                drawCallCount = output.metrics.drawCallCount,
                coverage = output.metrics.coverage,
                coverageMeasured = output.metrics.coverageMeasured,
            ),
            structuralSteps = output.structuralSteps(),
            nativeEvidenceCounters = output.nativeEvidenceCounters(),
            nativeEvidenceScopeKinds = output.nativeEvidenceScopeKinds(),
        )
    }

    private fun terminal(diagnostics: List<RenderDiagnostic>): GPUPlanSurfaceTerminalException {
        val diagnostic = diagnostics.firstOrNull()
            ?: return GPUPlanSurfaceTerminalException("w3.surface.unknown", "The W3 route failed without a diagnostic.")
        return GPUPlanSurfaceTerminalException(diagnostic.code.value, diagnostic.message)
    }

    private companion object {
        val CAPTURE_LIMIT_CODES = setOf("scene-node-limit", "scene-resource-limit", "graph-node-limit")
    }
}

private class ProductionGPUPlanSurfacePort : GPUPlanSurfacePort {
    override fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        frameLocalBudgetBytes: Long,
    ): GpuPlanSurfacePlanResult = GPUPlanRenderContextOwner.plan(scene, target, frameLocalBudgetBytes)

    override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
        GPUPlanRenderContextOwner.submit(token)
}
