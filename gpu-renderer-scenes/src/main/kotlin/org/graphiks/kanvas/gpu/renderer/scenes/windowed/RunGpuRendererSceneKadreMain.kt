package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.lang.reflect.InvocationTargetException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import kotlin.io.path.createDirectories
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.a8GlyphAtlasGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.legacyRetirementBlockerDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pathStencilCoverGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.pmReadinessFreezeDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.runtimeEffectRefusalGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.textResourceBindingGateDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableDiagnostics
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
    val frameTiming: WindowedFrameTimingReport? = null,
    val diagnostics: List<String> = emptyList(),
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
        require(diagnostics.all { it.isNotBlank() }) { "diagnostics must not contain blank entries" }
        require(frameTiming == null || frameTiming.rawSampleCount <= requestedFrames) {
            "frameTiming rawSampleCount must not exceed requestedFrames"
        }
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
                require(frameTiming == null || frameTiming.rawSampleCount == presentedFrames) {
                    "presented reports with frameTiming must include one sample per presented frame"
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
        appendLine("  \"error\": ${error?.json() ?: "null"},")
        appendLine("  \"frameTiming\": ${frameTiming?.toJson(indent = "  ") ?: "null"},")
        appendLine("  \"diagnostics\": [${diagnostics.joinToString(",") { it.json() }}]")
        appendLine("}")
    }

    fun writeTo(output: Path) {
        val absoluteOutput = output.toAbsolutePath()
        writeJsonAtomically(absoluteOutput)
        sceneSessionMirrorOutput(absoluteOutput)?.let { mirrorOutput ->
            writeJsonAtomically(mirrorOutput)
        }
    }

    private fun writeJsonAtomically(absoluteOutput: Path) {
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

    private fun sceneSessionMirrorOutput(absoluteOutput: Path): Path? {
        if (absoluteOutput.fileName?.toString() != "session.json") return null
        val parent = absoluteOutput.parent ?: return null
        if (parent.fileName?.toString() == sceneId) return null
        return parent.resolve(sceneId).resolve("session.json")
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
                diagnostics = scene.windowedSceneDiagnostics(),
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
                diagnostics = scene.windowedSceneDiagnostics(),
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
                diagnostics = scene.windowedSceneDiagnostics(),
            )

        fun presented(
            scene: GPURendererScene<*>,
            requestedFrames: Int,
            surfaceFormat: String,
            adapterInfo: String,
            frameTiming: WindowedFrameTimingReport? = null,
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
                frameTiming = frameTiming,
                diagnostics = scene.windowedSceneDiagnostics(),
            )
    }
}

data class WindowedFrameTimingReport(
    val metricName: String,
    val metricSource: String,
    val warmupFrames: Int,
    val samples: List<WindowedFrameTimingSample>,
) {
    val rawSampleCount: Int get() = samples.size
    val stableFrames: Int get() = rawSampleCount - warmupFrames

    init {
        require(metricName.isNotBlank()) { "frame timing metricName must not be blank" }
        require(metricSource.isNotBlank()) { "frame timing metricSource must not be blank" }
        require(samples.isNotEmpty()) { "frame timing samples must not be empty" }
        require(warmupFrames >= 0) { "frame timing warmupFrames must not be negative" }
        require(warmupFrames < samples.size) { "frame timing warmupFrames must leave at least one stable sample" }
        require(samples.map { it.frameIndex } == (1..samples.size).toList()) {
            "frame timing sample frameIndex values must be one-based and contiguous"
        }
        samples.forEachIndexed { index, sample ->
            val expectedPhase = if (index < warmupFrames) "warmup" else "stable"
            require(sample.phase == expectedPhase) {
                "frame timing sample phase must match warmup split"
            }
        }
    }

    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"metricName\": ${metricName.json()},")
        appendLine("$indent  \"metricSource\": ${metricSource.json()},")
        appendLine("$indent  \"rawSampleCount\": $rawSampleCount,")
        appendLine("$indent  \"warmupFrames\": $warmupFrames,")
        appendLine("$indent  \"stableFrames\": $stableFrames,")
        appendLine("$indent  \"samples\": [")
        appendLine(samples.joinToString(",\n") { sample -> "$indent    ${sample.toJson()}" })
        appendLine("$indent  ]")
        append("$indent}")
    }

    companion object {
        fun wallClockEncodePresent(
            warmupFrames: Int,
            samples: List<Long>,
        ): WindowedFrameTimingReport {
            require(samples.all { sample -> sample > 0L }) {
                "frame timing wall-clock samples must be positive"
            }
            return WindowedFrameTimingReport(
                metricName = "frame-time-ms",
                metricSource = "wall-clock-encode-present",
                warmupFrames = warmupFrames,
                samples = samples.mapIndexed { index, durationNanos ->
                    WindowedFrameTimingSample(
                        frameIndex = index + 1,
                        phase = if (index < warmupFrames) "warmup" else "stable",
                        durationNanos = durationNanos,
                    )
                },
            )
        }
    }
}

data class WindowedFrameTimingSample(
    val frameIndex: Int,
    val phase: String,
    val durationNanos: Long,
) {
    init {
        require(frameIndex > 0) { "frame timing sample frameIndex must be positive" }
        require(phase == "warmup" || phase == "stable") {
            "frame timing sample phase must be warmup or stable"
        }
        require(durationNanos > 0L) { "frame timing sample durationNanos must be positive" }
    }

    val durationMs: Double get() = durationNanos / 1_000_000.0

    fun toJson(): String =
        "{\"frameIndex\": $frameIndex, \"phase\": ${phase.json()}, \"durationNanos\": $durationNanos, " +
            "\"durationMs\": ${durationMs.formatFrameTimingMs()}}"
}

private fun Double.formatFrameTimingMs(): String =
    String.format(Locale.US, "%.4f", this)

private fun GPURendererScene<*>.surface(format: String?): WindowedSceneSurface =
    WindowedSceneSurface(
        width = dimensions.width,
        height = dimensions.height,
        format = format,
    )

internal fun GPURendererScene<*>.windowedSceneDiagnostics(): List<String> {
    val textRunDiagnostics = commands.filterIsInstance<SceneCommand>()
        .textRunRouteUnavailableDiagnostics(sceneId.value)
    val saveLayers = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filter { it.hasFixturePayload }
    val meshRibbons = commands.filterIsInstance<SceneCommand.MeshRibbon>()
        .filter { it.hasFixturePayload }
    val runtimeEffectRefusalDiagnostics = runtimeEffectRefusalGateDiagnostics()
    val a8GlyphAtlasDiagnostics = a8GlyphAtlasGateDiagnostics()
    val textResourceBindingDiagnostics = textResourceBindingGateDiagnostics()
    val pmReadinessFreezeDiagnostics = pmReadinessFreezeDiagnostics()
    val legacyRetirementBlockerDiagnostics = legacyRetirementBlockerDiagnostics()
    val pathStencilCoverGateDiagnostics = pathStencilCoverGateDiagnostics()
    if (
        textRunDiagnostics.isEmpty() &&
        saveLayers.isEmpty() &&
        meshRibbons.isEmpty() &&
        runtimeEffectRefusalDiagnostics.isEmpty() &&
        a8GlyphAtlasDiagnostics.isEmpty() &&
        textResourceBindingDiagnostics.isEmpty() &&
        pmReadinessFreezeDiagnostics.isEmpty() &&
        legacyRetirementBlockerDiagnostics.isEmpty() &&
        pathStencilCoverGateDiagnostics.isEmpty()
    ) {
        return emptyList()
    }

    val saveLayerLabels = saveLayers.map { it.label }.toSet()
    val filters = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filter { it.hasFixturePayload && it.inputLabel in saveLayerLabels }
    return buildList {
        addAll(runtimeEffectRefusalDiagnostics)
        addAll(a8GlyphAtlasDiagnostics)
        addAll(textResourceBindingDiagnostics)
        addAll(pmReadinessFreezeDiagnostics)
        addAll(legacyRetirementBlockerDiagnostics)
        if (pathStencilCoverGateDiagnostics.isNotEmpty()) {
            add("clearCommands=${commands.count { it is SceneCommand.Clear }}")
            add("fillRectCommands=${commands.count { it is SceneCommand.FillRect }}")
            add("fillRRectCommands=${commands.count { it is SceneCommand.FillRRect }}")
            add("linearGradientRectCommands=${commands.count { it is SceneCommand.LinearGradientRect }}")
            add("clipCommands=${commands.count { it is SceneCommand.Clip }}")
            add("bitmapRectCommands=${commands.count { it is SceneCommand.BitmapRect }}")
        }
        addAll(pathStencilCoverGateDiagnostics)
        addAll(textRunDiagnostics)
        if (saveLayers.isNotEmpty()) {
            add("saveLayerCommands=${saveLayers.size}")
            add("saveLayerKinds=${saveLayers.joinToString { it.layerKind }}")
            add("saveLayerRoute=scene-fixture.bounded-shadow-card")
            add("saveLayerMaterializedDraws=${saveLayers.size * 2}")
            if (filters.isNotEmpty()) {
                add("filterRoutes=scene-fixture.bounded-drop-shadow")
            }
            add("generalSaveLayerSupport=false")
            add("imageFilterDagSupport=false")
        }
        if (meshRibbons.isNotEmpty()) {
            add("meshRibbonCommands=${meshRibbons.size}")
            add("meshRibbonKinds=${meshRibbons.joinToString { it.meshKind }}")
            add("meshRibbonRoute=scene-fixture.bounded-ribbon-strip")
            add("meshRibbonFallbackReason=none")
            add("generalVerticesSupport=false")
            add("vertexIndexBufferSupport=false")
        }
    }
}
