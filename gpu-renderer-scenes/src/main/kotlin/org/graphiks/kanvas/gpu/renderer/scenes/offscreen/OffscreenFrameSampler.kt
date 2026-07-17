package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class OffscreenFrameSampler {
    fun sample(
        scene: GPURendererScene<SceneCommand>,
        frames: Int,
        outputDir: Path,
    ): OffscreenFrameSampleReport {
        require(frames > 1) { "offscreen frame sampling requires frames > 1" }
        val sceneId = scene.sceneId.value
        if (scene.usesPreparedSolidRectPilot()) {
            return samplePreparedSolidRect(scene, frames, outputDir)
        }
        val unsupportedReason = rectOnlyCommandSequenceUnsupportedReason(scene.commands)
        if (unsupportedReason != null) {
            return OffscreenFrameSampleReport.notYetRendered(sceneId, unsupportedReason).also { report ->
                report.writeTo(outputDir)
            }
        }

        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = sceneId,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenFrameSampleReport.failed(sceneId, "webgpu-context-unavailable").also { report ->
                report.writeTo(outputDir)
            }
        val renderer = RectOnlyOffscreenRenderer()

        runtime.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = scene.dimensions.width,
                    height = scene.dimensions.height,
                    colorFormat = OFFSCREEN_FRAME_SAMPLE_COLOR_FORMAT,
                ),
            ).use { target ->
                val samples = mutableListOf<Long>()
                repeat(frames) {
                    val frameStart = System.nanoTime()
                    renderer.renderToPixels(target, drawPlan)
                    samples += (System.nanoTime() - frameStart).coerceAtLeast(1L)
                }

                return OffscreenFrameSampleReport.sampled(
                    sceneId = sceneId,
                    adapterInfo = session.adapterInfo?.summary ?: "unknown-adapter",
                    warmupFrames = frameTimingWarmupFrames(samples.size),
                    samples = samples,
                    diagnostics = rectOnlyRenderedDiagnostics(
                        sceneId = sceneId,
                        adapterInfo = session.adapterInfo?.summary,
                        clearCount = drawPlan.clearCount,
                        fillRectCount = drawPlan.fillRectCount,
                        fillRRectCount = drawPlan.fillRRectCount,
                        linearGradientRectCount = drawPlan.linearGradientRectCount,
                        clipCount = drawPlan.clipCount,
                        bitmapRectCount = drawPlan.bitmapRectCount,
                        blurRectCount = drawPlan.blurRectCount,
                        colorMatrixRectCount = drawPlan.colorMatrixRectCount,
                        strokeRectCount = drawPlan.strokeRectCount,
                        textRunRectCount = drawPlan.textRunCount,
                        saveLayerRectCount = drawPlan.saveLayerRectCount,
                        filters = drawPlan.filters,
                        saveLayers = drawPlan.saveLayers,
                        runtimeEffects = drawPlan.runtimeEffects,
                        meshRibbons = drawPlan.meshRibbons,
                    ) + listOf(
                        "sampled $sceneId via WebGPU offscreen render+readback",
                        "metricSource=wall-clock-offscreen-render-readback",
                        "warmupFrames=${frameTimingWarmupFrames(samples.size)}",
                        "stableFrames=${samples.size - frameTimingWarmupFrames(samples.size)}",
                    ),
                ).also { report ->
                    report.writeTo(outputDir)
                }
            }
        }
    }

    private fun samplePreparedSolidRect(
        scene: GPURendererScene<SceneCommand>,
        frames: Int,
        outputDir: Path,
    ): OffscreenFrameSampleReport {
        val sceneId = scene.sceneId.value
        val runtime = GPUBackendRuntimeFactory.createOrNull()
            ?: return OffscreenFrameSampleReport.failed(sceneId, "webgpu-context-unavailable").also {
                it.writeTo(outputDir)
            }
        return runtime.use { session ->
            val capabilities = session.capabilities
                ?: return@use OffscreenFrameSampleReport.failed(
                    sceneId,
                    "prepared-solid-rect-capabilities-unavailable",
                ).also { it.writeTo(outputDir) }
            val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
                ?.let(::GPUDeviceGenerationID)
                ?: return@use OffscreenFrameSampleReport.failed(
                    sceneId,
                    "prepared-solid-rect-device-generation-unavailable",
                ).also { it.writeTo(outputDir) }
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    OFFSCREEN_FRAME_SAMPLE_COLOR_FORMAT,
                ),
            ).use { preparedSession ->
                val samples = mutableListOf<Long>()
                repeat(frames) { index ->
                    val recorded = when (
                        val result = PreparedSolidRectSceneFrameRecorder().record(
                            scene,
                            capabilities,
                            generation,
                            frameOrdinal = index + 1L,
                            withReadback = false,
                        )
                    ) {
                        is PreparedSolidRectSceneFrameResult.Recorded -> result
                        is PreparedSolidRectSceneFrameResult.Refused -> return@use OffscreenFrameSampleReport.failed(
                            sceneId,
                            result.reason,
                        ).also { it.writeTo(outputDir) }
                    }
                    val frameStart = System.nanoTime()
                    val terminal = preparedSession.renderFrame(
                        recorded.taskList,
                        GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
                    ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                    samples += (System.nanoTime() - frameStart).coerceAtLeast(1L)
                    if (terminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                        return@use OffscreenFrameSampleReport.failed(
                            sceneId,
                            terminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                                ?: "prepared-solid-rect-frame-failed",
                        ).also { it.writeTo(outputDir) }
                    }
                }

                // Correctness readback is deliberately outside the measured frame loop.
                val finalFrame = when (
                    val result = PreparedSolidRectSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal = frames + 1L,
                        withReadback = true,
                    )
                ) {
                    is PreparedSolidRectSceneFrameResult.Recorded -> result
                    is PreparedSolidRectSceneFrameResult.Refused -> return@use OffscreenFrameSampleReport.failed(
                        sceneId,
                        result.reason,
                    ).also { it.writeTo(outputDir) }
                }
                val requestId = requireNotNull(finalFrame.readbackRequestId)
                val finalTerminal = preparedSession.renderFrame(
                    finalFrame.taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                if (finalTerminal.outcome != GPUFrameStructuralOutcome.Succeeded) {
                    return@use OffscreenFrameSampleReport.failed(
                        sceneId,
                        finalTerminal.diagnostic?.let { "${it.code.value}: ${it.message}" }
                            ?: "prepared-solid-rect-final-readback-failed",
                    ).also { it.writeTo(outputDir) }
                }
                val counters = preparedSession.nativeCounters()
                OffscreenFrameSampleReport.sampled(
                    sceneId = sceneId,
                    adapterInfo = session.adapterInfo?.summary ?: "unknown-adapter",
                    warmupFrames = frameTimingWarmupFrames(samples.size),
                    samples = samples,
                    metricSource = "wall-clock-prepared-submit-completion",
                    diagnostics = listOf(
                        "sampled $sceneId via reusable prepared WebGPU session",
                        "metricSource=wall-clock-prepared-submit-completion",
                        "measuredReadbacks=0 finalValidationReadbacks=${counters.readbackCopies}",
                        "nativeFrames=${frames + 1} encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                        "solidRectCache creations=${counters.solidRectInvariantCreations} " +
                            "reuses=${counters.solidRectInvariantReuses}",
                        "warmupFrames=${frameTimingWarmupFrames(samples.size)}",
                        "stableFrames=${samples.size - frameTimingWarmupFrames(samples.size)}",
                    ),
                ).also { it.writeTo(outputDir) }
            }
        }
    }
}

private const val OFFSCREEN_FRAME_SAMPLE_COLOR_FORMAT: String = "rgba8unorm"

private fun frameTimingWarmupFrames(sampleCount: Int): Int =
    if (sampleCount <= 1) 0 else minOf(3, sampleCount - 1)
