package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
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
        return samplePreparedScene(scene, frames, outputDir)
    }

    private fun samplePreparedScene(
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
            val generation = session.deviceGeneration
            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                ),
            ).use { preparedSession ->
                val samples = mutableListOf<Long>()
                repeat(frames) { index ->
                    val recorded = when (
                        val result = PreparedSceneFrameRecorder().record(
                            scene,
                            capabilities,
                            generation,
                            frameOrdinal = index + 1L,
                            withReadback = false,
                        )
                    ) {
                        is PreparedSceneFrameResult.Recorded -> result
                        is PreparedSceneFrameResult.Refused -> return@use OffscreenFrameSampleReport.notYetRendered(
                            sceneId,
                            "${result.code}: ${result.message}",
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
                                ?: "prepared-scene-frame-failed",
                        ).also { it.writeTo(outputDir) }
                    }
                }

                // Correctness readback is deliberately outside the measured frame loop.
                val finalFrame = when (
                    val result = PreparedSceneFrameRecorder().record(
                        scene,
                        capabilities,
                        generation,
                        frameOrdinal = frames + 1L,
                        withReadback = true,
                    )
                ) {
                    is PreparedSceneFrameResult.Recorded -> result
                    is PreparedSceneFrameResult.Refused -> return@use OffscreenFrameSampleReport.notYetRendered(
                        sceneId,
                        "${result.code}: ${result.message}",
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
                            ?: "prepared-scene-final-readback-failed",
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
                        "sampled $sceneId via reusable prepared WebGPU session route=${finalFrame.route}",
                        "metricSource=wall-clock-prepared-submit-completion",
                        "measuredReadbacks=0 finalValidationReadbacks=${counters.readbackCopies}",
                        "nativeFrames=${frames + 1} encoders=${counters.encoders} " +
                            "commandBuffers=${counters.commandBuffers} submits=${counters.submits}",
                        "preparedCaches solid=${counters.solidRectInvariantCreations}/${counters.solidRectInvariantReuses} " +
                            "registered=${counters.registeredUniformInvariantCreations}/" +
                            "${counters.registeredUniformInvariantReuses}",
                        "warmupFrames=${frameTimingWarmupFrames(samples.size)}",
                        "stableFrames=${samples.size - frameTimingWarmupFrames(samples.size)}",
                    ),
                ).also { it.writeTo(outputDir) }
            }
        }
    }
}


private fun frameTimingWarmupFrames(sampleCount: Int): Int =
    if (sampleCount <= 1) 0 else minOf(3, sampleCount - 1)
