package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Path
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

fun main(args: Array<String>) {
    renderGpuRendererSceneOffscreen(args)
}

fun renderGpuRendererSceneOffscreen(args: Array<String>): OffscreenRunReport {
    require(args.size == 2) {
        "Usage: RenderGpuRendererSceneOffscreenMainKt <scene-id> <output-root>"
    }

    val sceneId = args[0]
    val outputRoot = Path.of(args[1])
    val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)
    val sceneOutput = outputRoot.resolve(sceneId)

    val report = if (scene.supportsRectOnlyOffscreen()) {
        runCatching {
            RectOnlyOffscreenRenderer().render(scene, sceneOutput)
        }.getOrElse { failure ->
            OffscreenRunReport.failed(
                sceneId = scene.sceneId.value,
                reason = failure.toReportReason(),
            )
        }
    } else {
        OffscreenRunReport.notYetRendered(
            sceneId = scene.sceneId.value,
            reason = "runner-subset:${scene.sceneId.value}",
        )
    }

    report.writeTo(sceneOutput)
    println(
        "GPU renderer scene offscreen complete: sceneId=${scene.sceneId.value} " +
            "status=${report.status} output=${sceneOutput.toAbsolutePath()}"
    )
    return report
}

private fun GPURendererScene<SceneCommand>.supportsRectOnlyOffscreen(): Boolean =
    rectOnlyCommandSequenceUnsupportedReason(commands) == null

private fun Throwable.toReportReason(): String {
    val className = this::class.qualifiedName ?: this::class.simpleName ?: "Throwable"
    val messageText = message?.takeIf { it.isNotBlank() }
    return if (messageText == null) className else "$className: $messageText"
}
