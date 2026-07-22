package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

fun main(args: Array<String>) {
    try {
        renderGpuRendererSceneFrameSamples(args)
    } finally {
        GPUBackendRuntimeFactory.dispose()
    }
}

fun renderGpuRendererSceneFrameSamples(args: Array<String>) {
    require(args.size == 3) {
        "Usage: RenderGpuRendererSceneFrameSamplesMainKt <scene-id> <frames> <output-root>"
    }

    val sceneId = args[0]
    val frames = parseFrameSampleCount(args[1])
    val outputRoot = Path.of(args[2])
    val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)
    val report = OffscreenFrameSampler().sample(
        scene = scene,
        frames = frames,
        outputDir = outputRoot.resolve(sceneId),
    )

    println(
        "GPU renderer scene frame samples complete: sceneId=$sceneId " +
            "status=${report.status} output=${outputRoot.resolve(sceneId).toAbsolutePath()}",
    )
}

private fun parseFrameSampleCount(raw: String): Int {
    val frames = raw.toIntOrNull()
        ?: throw IllegalArgumentException("frames must be an Int: $raw")
    require(frames > 1) { "frames must be > 1: $frames" }
    return frames
}
