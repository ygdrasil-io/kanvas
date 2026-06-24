package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
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
}

private const val OFFSCREEN_FRAME_SAMPLE_COLOR_FORMAT: String = "rgba8unorm"

private fun frameTimingWarmupFrames(sampleCount: Int): Int =
    if (sampleCount <= 1) 0 else minOf(3, sampleCount - 1)
