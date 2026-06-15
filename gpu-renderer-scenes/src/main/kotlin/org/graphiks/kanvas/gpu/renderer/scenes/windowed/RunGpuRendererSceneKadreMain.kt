package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

private const val SOLID_CARD_STACK_SCENE_ID = "solid-card-stack"
private const val KADRE_RUNNER_CLASS =
    "org.graphiks.kanvas.gpu.renderer.scenes.windowed.KadreWindowedSceneRunner"

fun main(args: Array<String>) = runGpuRendererSceneKadre(args)

fun runGpuRendererSceneKadre(args: Array<String>) {
    require(args.size == 3) {
        "Usage: RunGpuRendererSceneKadreMainKt <scene-id> <frames> <session-output>"
    }

    val sceneId = args[0]
    val frames = parseFrames(args[1])
    val output = Path.of(args[2])
    val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)

    if (frames == 0) {
        WindowedSceneSessionReport.drySession(scene, frames).writeTo(output)
        println(windowedCompletionMessage(scene.sceneId.value, "dry-session", output))
        return
    }

    if (scene.sceneId.value != SOLID_CARD_STACK_SCENE_ID) {
        WindowedSceneSessionReport.notYetRendered(scene, frames).writeTo(output)
        println(windowedCompletionMessage(scene.sceneId.value, "not-yet-rendered", output))
        return
    }

    runCatching {
        runKadreWindowedScene(scene, frames, output)
    }.getOrElse { failure ->
        if (!output.exists()) {
            WindowedSceneSessionReport.blocked(
                scene = scene,
                requestedFrames = frames,
                reason = "kadre-windowed-run-failed",
                error = failure.toReportError(),
            ).writeTo(output)
        }
        println(windowedCompletionMessage(scene.sceneId.value, "blocked", output))
    }
}

private fun parseFrames(raw: String): Int {
    val frames = raw.toIntOrNull()
        ?: throw IllegalArgumentException("frames must be an Int: $raw")
    require(frames >= 0) { "frames must be >= 0: $frames" }
    return frames
}

private fun runKadreWindowedScene(
    scene: GPURendererScene<*>,
    frames: Int,
    output: Path,
) {
    val runnerClass = Class.forName(KADRE_RUNNER_CLASS)
    val runner = runnerClass.getConstructor(GPURendererScene::class.java).newInstance(scene)
    runnerClass.getMethod("run", Int::class.javaPrimitiveType, Path::class.java)
        .invoke(runner, frames, output)
}

private fun Throwable.toReportError(): String {
    val cause = if (this is InvocationTargetException) targetException ?: this else this
    val className = cause::class.qualifiedName ?: cause::class.simpleName ?: "Throwable"
    val messageText = cause.message?.takeIf { it.isNotBlank() }
    return if (messageText == null) className else "$className: $messageText"
}

private fun windowedCompletionMessage(sceneId: String, status: String, output: Path): String =
    "GPU renderer scene Kadre windowed complete: sceneId=$sceneId " +
        "status=$status output=${output.toAbsolutePath()}"

data class WindowedSceneSurface(
    val width: Int,
    val height: Int,
    val format: String?,
) {
    init {
        require(width > 0) { "surface width must be positive" }
        require(height > 0) { "surface height must be positive" }
    }
}

data class WindowedSceneSessionReport(
    val sceneId: String,
    val status: String,
    val reason: String?,
    val requestedFrames: Int,
    val presentedFrames: Int,
    val surface: WindowedSceneSurface,
    val adapterInfo: String?,
    val error: String?,
) {
    val manualValidation: Boolean = true

    init {
        require(sceneId.isNotBlank()) { "sceneId must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        require(reason == null || reason.isNotBlank()) { "reason must not be blank" }
        require(requestedFrames >= 0) { "requestedFrames must be >= 0" }
        require(presentedFrames >= 0) { "presentedFrames must be >= 0" }
        require(presentedFrames <= requestedFrames) {
            "presentedFrames must not exceed requestedFrames"
        }
        require(error == null || error.isNotBlank()) { "error must not be blank" }
    }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sceneId\": ${sceneId.json()},")
        appendLine("  \"status\": ${status.json()},")
        appendLine("  \"reason\": ${reason?.json() ?: "null"},")
        appendLine("  \"requestedFrames\": $requestedFrames,")
        appendLine("  \"presentedFrames\": $presentedFrames,")
        appendLine("  \"manualValidation\": $manualValidation,")
        appendLine("  \"surface\": {")
        appendLine("    \"width\": ${surface.width},")
        appendLine("    \"height\": ${surface.height},")
        appendLine("    \"format\": ${surface.format?.json() ?: "null"}")
        appendLine("  },")
        appendLine("  \"adapterInfo\": ${adapterInfo?.json() ?: "null"},")
        appendLine("  \"error\": ${error?.json() ?: "null"}")
        appendLine("}")
    }

    fun writeTo(output: Path) {
        output.parent?.createDirectories()
        output.writeText(toJson())
    }

    companion object {
        fun drySession(scene: GPURendererScene<*>, requestedFrames: Int): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "dry-session",
                reason = "frames-zero-dry-session",
                requestedFrames = requestedFrames,
                presentedFrames = 0,
                surface = scene.surface(format = null),
                adapterInfo = null,
                error = null,
            )

        fun notYetRendered(scene: GPURendererScene<*>, requestedFrames: Int): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "not-yet-rendered",
                reason = "scene-renderer-not-yet-implemented",
                requestedFrames = requestedFrames,
                presentedFrames = 0,
                surface = scene.surface(format = null),
                adapterInfo = null,
                error = null,
            )

        fun blocked(
            scene: GPURendererScene<*>,
            requestedFrames: Int,
            reason: String,
            error: String,
        ): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "blocked",
                reason = reason,
                requestedFrames = requestedFrames,
                presentedFrames = 0,
                surface = scene.surface(format = null),
                adapterInfo = null,
                error = error,
            )

        fun presented(
            scene: GPURendererScene<*>,
            requestedFrames: Int,
            surfaceFormat: String,
            adapterInfo: String,
        ): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "presented",
                reason = "kadre-windowed-presented-frames",
                requestedFrames = requestedFrames,
                presentedFrames = requestedFrames,
                surface = scene.surface(format = surfaceFormat),
                adapterInfo = adapterInfo,
                error = null,
            )
    }
}

private fun GPURendererScene<*>.surface(format: String?): WindowedSceneSurface =
    WindowedSceneSurface(
        width = dimensions.width,
        height = dimensions.height,
        format = format,
    )
