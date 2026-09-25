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
import org.graphiks.kanvas.surface.GPUColorFormat

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
        materialFrameLimits: org.graphiks.kanvas.gpu.plan.MaterialFrameLimits =
            org.graphiks.kanvas.gpu.plan.MaterialFrameLimits(),
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
        // The compatibility lowerers below this continuation still own excluded
        // formats/effects in unpromoted mixtures. They never receive an owned plan.
        val planningOperations = operations.map { operation ->
            (operation as? DisplayOp.DrawPoints)?.w5hStrokePathOrNull() ?: operation
        }
        val layerOwned = GPUPlanSurfaceCandidateGate.ownsW6aLayers(planningOperations)
        val w6bOwned = GPUPlanSurfaceCandidateGate.ownsW6bFilters(planningOperations)
        val w6dOwned = GPUPlanSurfaceCandidateGate.ownsW6dAdvancedFilters(planningOperations)
        if (w6dOwned && config.gpuColorFormat == GPUColorFormat.RGBA16_FLOAT) {
            throw GPUPlanSurfaceTerminalException(
                org.graphiks.kanvas.gpu.plan.W6dPlanDiagnostics.UnsupportedTargetFormat,
                "W6d advanced filters do not support the public RGBA16_FLOAT target.",
            )
        }
        if (w6bOwned && config.gpuColorFormat != GPUColorFormat.RGBA8_UNORM_SRGB) {
            throw GPUPlanSurfaceTerminalException(
                org.graphiks.kanvas.gpu.plan.W6bFilterDiagnostics.UnsupportedTargetFormat,
                "W6b filters require the public RGBA8_UNORM_SRGB target.",
            )
        }
        if (!w6bOwned && layerOwned && config.gpuColorFormat != GPUColorFormat.RGBA8_UNORM_SRGB) {
            throw GPUPlanSurfaceTerminalException(
                "w6a.layer.unsupported_target_format",
                "W6a layers require the public RGBA8_UNORM_SRGB target.",
            )
        }
        if (!layerOwned && !w6bOwned && !GPUPlanSurfaceCandidateGate.accepts(planningOperations, config)) return legacy()
        val imageOwned = GPUPlanSurfaceCandidateGate.ownsW5eImages(planningOperations)

        val extent = SceneExtent(width, height)
        val scene = when (val captured = capturePort.capture(planningOperations, extent, ColorSpace.SRGB, captureLimits)) {
            is SceneCaptureResult.Captured -> captured.scene
            is SceneCaptureResult.Invalid -> {
                if (!layerOwned && !w6bOwned && !imageOwned && captured.diagnostics.isNotEmpty() &&
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
                org.graphiks.kanvas.gpu.plan.MaterialFrameLimits(config.maxNoiseOctaveEvaluationsI64),
            )
        ) {
            // No compiler owns a GapNotMigrated frame. This is the final legacy boundary.
            is GpuPlanSurfacePlanResult.GapNotMigrated -> if (layerOwned || w6bOwned || imageOwned) throw terminal(planned.diagnostics) else legacy()
            is GpuPlanSurfacePlanResult.Terminal -> throw terminal(planned.diagnostics)
            is GpuPlanSurfacePlanResult.Ready -> submitOwned(planned.token, format)
        }
    }

    /** An authenticated ready token has no legacy continuation or public paint to reclassify. */
    private fun submitOwned(token: GpuPlanSurfaceReadyToken, format: PixelFormat): RenderResult =
        when (val submitted = planPort.submit(token)) {
            is GpuPlanSurfaceSubmitResult.Completed -> completed(submitted.output, format)
            is GpuPlanSurfaceSubmitResult.Terminal -> throw terminal(submitted.diagnostics)
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
        materialFrameLimits: org.graphiks.kanvas.gpu.plan.MaterialFrameLimits,
    ): GpuPlanSurfacePlanResult = GPUPlanRenderContextOwner.plan(scene, target, frameLocalBudgetBytes, materialFrameLimits)

    override fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult =
        GPUPlanRenderContextOwner.submit(token)
}
