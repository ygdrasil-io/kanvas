package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.lang.reflect.InvocationTargetException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.reports.json

private const val KADRE_RUNNER_CLASS =
    "org.graphiks.kanvas.gpu.renderer.scenes.windowed.KadreWindowedSceneRunner"

internal fun interface WindowedSceneRunnerLauncher {
    fun run(scene: GPURendererScene<*>, frames: Int, output: Path)
}

internal var kadreWindowedSceneRunnerLauncher: WindowedSceneRunnerLauncher =
    WindowedSceneRunnerLauncher(::runReflectiveKadreWindowedScene)

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

    val unsupportedReason = scene.kadreWindowedRectOnlyUnsupportedReason()
    if (unsupportedReason != null) {
        WindowedSceneSessionReport.notYetRendered(scene, frames, unsupportedReason).writeTo(output)
        println(windowedCompletionMessage(scene.sceneId.value, "not-yet-rendered", output))
        return
    }

    runCatching {
        kadreWindowedSceneRunnerLauncher.run(scene, frames, output)
    }.getOrElse { failure ->
        val reason = failure.runnerFailureReason()
        WindowedSceneSessionReport.blocked(
            scene = scene,
            requestedFrames = frames,
            reason = reason,
            error = failure.toReportError(),
        ).writeTo(output)
        if (failure.isRunnerLoadingFailure()) {
            throw failure
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

private fun runReflectiveKadreWindowedScene(
    scene: GPURendererScene<*>,
    frames: Int,
    output: Path,
) {
    val runnerClass = Class.forName(KADRE_RUNNER_CLASS)
    val runner = runnerClass.getConstructor(GPURendererScene::class.java).newInstance(scene)
    runnerClass.getMethod("run", Int::class.javaPrimitiveType, Path::class.java)
        .invoke(runner, frames, output)
}

private fun Throwable.runnerFailureReason(): String =
    if (isRunnerLoadingFailure()) {
        "kadre-windowed-runner-loading-failed"
    } else {
        "kadre-windowed-runner-invocation-failed"
    }

private fun Throwable.isRunnerLoadingFailure(): Boolean {
    val cause = unwrapInvocationTarget()
    return cause is ClassNotFoundException || cause is NoSuchMethodException
}

private fun Throwable.toReportError(): String {
    val cause = unwrapInvocationTarget()
    val className = cause::class.qualifiedName ?: cause::class.simpleName ?: "Throwable"
    val messageText = cause.message?.takeIf { it.isNotBlank() }
    return if (messageText == null) className else "$className: $messageText"
}

private fun Throwable.unwrapInvocationTarget(): Throwable =
    if (this is InvocationTargetException) targetException ?: this else this

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

enum class WindowedSceneSessionStatus(val wireName: String) {
    DrySession("dry-session"),
    NotYetRendered("not-yet-rendered"),
    Blocked("blocked"),
    Presented("presented"),
}

data class WindowedSceneSessionReport(
    val sceneId: String,
    val runStatus: WindowedSceneSessionStatus,
    val reason: String?,
    val requestedFrames: Int,
    val presentedFrames: Int,
    val surface: WindowedSceneSurface,
    val adapterInfo: String?,
    val error: String?,
) {
    val manualValidation: Boolean = true
    val productRefusal: Boolean = false
    val status: String get() = runStatus.wireName

    init {
        require(sceneId.isNotBlank()) { "sceneId must not be blank" }
        require(reason == null || reason.isNotBlank()) { "reason must not be blank" }
        require(requestedFrames >= 0) { "requestedFrames must be >= 0" }
        require(presentedFrames >= 0) { "presentedFrames must be >= 0" }
        require(presentedFrames <= requestedFrames) {
            "presentedFrames must not exceed requestedFrames"
        }
        require(error == null || error.isNotBlank()) { "error must not be blank" }
        requireStatusInvariants()
    }

    private fun requireStatusInvariants() {
        when (runStatus) {
            WindowedSceneSessionStatus.DrySession -> {
                require(requestedFrames == 0) { "dry-session reports must have requestedFrames == 0" }
                require(presentedFrames == 0) { "dry-session reports must have presentedFrames == 0" }
            }
            WindowedSceneSessionStatus.NotYetRendered -> {
                require(!reason.isNullOrBlank()) { "not-yet-rendered reports must include a reason" }
                require(presentedFrames == 0) { "not-yet-rendered reports must have presentedFrames == 0" }
            }
            WindowedSceneSessionStatus.Blocked -> {
                require(!reason.isNullOrBlank()) { "blocked reports must include a reason" }
                require(!error.isNullOrBlank()) { "blocked reports must include an error" }
            }
            WindowedSceneSessionStatus.Presented -> {
                require(requestedFrames > 0) { "presented reports must have requestedFrames > 0" }
                require(presentedFrames == requestedFrames) {
                    "presented reports must have presentedFrames == requestedFrames"
                }
                require(!surface.format.isNullOrBlank()) { "presented reports must include a surface format" }
                require(!adapterInfo.isNullOrBlank()) { "presented reports must include adapterInfo" }
                require(error == null) { "presented reports must not include an error" }
            }
        }
    }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"sceneId\": ${sceneId.json()},")
        appendLine("  \"status\": ${status.json()},")
        appendLine("  \"productRefusal\": $productRefusal,")
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
        val absoluteOutput = output.toAbsolutePath()
        val parent = absoluteOutput.parent ?: Path.of(".").toAbsolutePath()
        parent.createDirectories()
        val temp = Files.createTempFile(parent, "windowed-session-${absoluteOutput.fileName}.", ".tmp")
        var moved = false
        try {
            Files.writeString(temp, toJson())
            try {
                Files.move(temp, absoluteOutput, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, absoluteOutput, StandardCopyOption.REPLACE_EXISTING)
            }
            moved = true
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp)
            }
        }
    }

    companion object {
        fun drySession(scene: GPURendererScene<*>, requestedFrames: Int): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.DrySession,
                reason = "frames-zero-dry-session",
                requestedFrames = requestedFrames,
                presentedFrames = 0,
                surface = scene.surface(format = null),
                adapterInfo = null,
                error = null,
            )

        fun notYetRendered(
            scene: GPURendererScene<*>,
            requestedFrames: Int,
            reason: String = "scene-renderer-not-yet-implemented",
        ): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.NotYetRendered,
                reason = reason,
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
            presentedFrames: Int = 0,
            surfaceFormat: String? = null,
            adapterInfo: String? = null,
        ): WindowedSceneSessionReport =
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                runStatus = WindowedSceneSessionStatus.Blocked,
                reason = reason,
                requestedFrames = requestedFrames,
                presentedFrames = presentedFrames,
                surface = scene.surface(format = surfaceFormat),
                adapterInfo = adapterInfo,
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
                runStatus = WindowedSceneSessionStatus.Presented,
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

internal fun GPURendererScene<*>.kadreWindowedRectOnlyUnsupportedReason(): String? {
    val unsupportedFamilies = commands
        .mapNotNull { command ->
            when (command) {
                is SceneCommand.Clear,
                is SceneCommand.FillRect,
                is SceneCommand.FillRRect,
                is SceneCommand.LinearGradientRect,
                is SceneCommand.Clip,
                is SceneCommand.BitmapRect,
                is SceneCommand.FilterNode -> null
                is SceneCommand -> command.family
                else -> command::class.simpleName ?: "unknown-command"
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only windowed render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, clip, fixture-backed bitmap-rect, and fixture-backed filter-node command families: " +
            unsupportedFamilies.joinToString()
    }

    val bitmapMarkers = commands.filterIsInstance<SceneCommand.BitmapRect>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (bitmapMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed BitmapRect payloads: " +
            bitmapMarkers.joinToString()
    }

    val filterMarkers = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (filterMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed FilterNode payloads: " +
            filterMarkers.joinToString()
    }

    val fixtureBitmapLabels = commands.filterIsInstance<SceneCommand.BitmapRect>()
        .filter { it.hasFixturePayload }
        .map { it.label }
        .toSet()
    val filters = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filter { it.hasFixturePayload }
    val invalidFilterInputs = filters
        .filter { it.inputLabel !in fixtureBitmapLabels }
        .map { "${it.label}->${it.inputLabel}" }
    if (invalidFilterInputs.isNotEmpty()) {
        return "rect-only windowed render requires FilterNode inputs to reference fixture-backed BitmapRect labels: " +
            invalidFilterInputs.joinToString()
    }

    val duplicateFilterInputs = filters
        .mapNotNull { it.inputLabel }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    if (duplicateFilterInputs.isNotEmpty()) {
        return "rect-only windowed render supports at most one FilterNode per BitmapRect input: " +
            duplicateFilterInputs.joinToString()
    }

    if (commands.none {
            it is SceneCommand.FillRect ||
                it is SceneCommand.FillRRect ||
                it is SceneCommand.LinearGradientRect ||
                it is SceneCommand.BitmapRect
        }
    ) {
        return "rect-only windowed render requires at least one FillRect, FillRRect, LinearGradientRect, or BitmapRect command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only windowed render supports zero or one initial Clear before drawable commands"
    }

    return null
}
