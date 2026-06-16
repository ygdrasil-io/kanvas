package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.lang.reflect.InvocationTargetException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableDiagnostics
import org.graphiks.kanvas.gpu.renderer.scenes.commands.textRunRouteUnavailableReason
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
        appendLine("  \"error\": ${error?.json() ?: "null"},")
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
                diagnostics = scene.windowedSceneDiagnostics(),
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
    commands.filterIsInstance<SceneCommand>().textRunRouteUnavailableReason()?.let { return it }

    val unsupportedFamilies = commands
        .mapNotNull { command ->
            when (command) {
                is SceneCommand.Clear,
                is SceneCommand.FillRect,
                is SceneCommand.FillRRect,
                is SceneCommand.LinearGradientRect,
                is SceneCommand.Clip,
                is SceneCommand.BitmapRect,
                is SceneCommand.SaveLayer,
                is SceneCommand.FilterNode,
                is SceneCommand.RuntimeEffectTile,
                is SceneCommand.MeshRibbon -> null
                is SceneCommand -> command.family
                else -> command::class.simpleName ?: "unknown-command"
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only windowed render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, clip, fixture-backed bitmap-rect, fixture-backed save-layer, fixture-backed filter-node, fixture-backed runtime-effect, and fixture-backed mesh-ribbon command families: " +
            unsupportedFamilies.joinToString()
    }

    val bitmapMarkers = commands.filterIsInstance<SceneCommand.BitmapRect>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (bitmapMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed BitmapRect payloads: " +
            bitmapMarkers.joinToString()
    }

    val saveLayerMarkers = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (saveLayerMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed SaveLayer payloads: " +
            saveLayerMarkers.joinToString()
    }

    val filterMarkers = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (filterMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed FilterNode payloads: " +
            filterMarkers.joinToString()
    }

    val runtimeEffectMarkers = commands.filterIsInstance<SceneCommand.RuntimeEffectTile>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (runtimeEffectMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed RuntimeEffectTile payloads: " +
            runtimeEffectMarkers.joinToString()
    }

    val meshRibbonMarkers = commands.filterIsInstance<SceneCommand.MeshRibbon>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (meshRibbonMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed MeshRibbon payloads: " +
            meshRibbonMarkers.joinToString()
    }

    val unsupportedRuntimeEffects = commands.filterIsInstance<SceneCommand.RuntimeEffectTile>()
        .filter { it.hasFixturePayload && !it.isRegisteredSimpleRt }
        .map { it.label }
    if (unsupportedRuntimeEffects.isNotEmpty()) {
        return "rect-only windowed render supports only registered runtime.simple_rt RuntimeEffectTile payloads: " +
            unsupportedRuntimeEffects.joinToString()
    }

    val fixtureBitmapLabels = commands.filterIsInstance<SceneCommand.BitmapRect>()
        .filter { it.hasFixturePayload }
        .map { it.label }
        .toSet()
    val fixtureSaveLayerLabels = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filter { it.hasFixturePayload }
        .map { it.label }
        .toSet()
    val filters = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filter { it.hasFixturePayload }
    val invalidFilterInputs = filters
        .mapNotNull { filter ->
            val inputLabel = filter.inputLabel
            when (filter.kind) {
                SceneFilterKind.LumaTint -> if (inputLabel !in fixtureBitmapLabels) {
                    "${filter.label}->${filter.inputLabel}:luma-tint requires BitmapRect"
                } else {
                    null
                }
                SceneFilterKind.DropShadow -> if (inputLabel !in fixtureSaveLayerLabels) {
                    "${filter.label}->${filter.inputLabel}:drop-shadow requires SaveLayer"
                } else {
                    null
                }
                null -> null
            }
        }
    if (invalidFilterInputs.isNotEmpty()) {
        return "rect-only windowed render requires FilterNode inputs to reference compatible fixture-backed labels: " +
            invalidFilterInputs.joinToString()
    }

    val missingDropShadowLayers = fixtureSaveLayerLabels
        .filter { label ->
            filters.none { filter -> filter.inputLabel == label && filter.kind == SceneFilterKind.DropShadow }
        }
    if (missingDropShadowLayers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed SaveLayer inputs to have one DropShadow FilterNode: " +
            missingDropShadowLayers.joinToString()
    }

    val duplicateFilterInputs = filters
        .mapNotNull { it.inputLabel }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    if (duplicateFilterInputs.isNotEmpty()) {
        return "rect-only windowed render supports at most one FilterNode per fixture input: " +
            duplicateFilterInputs.joinToString()
    }

    val outOfBoundsSaveLayerDraws = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filter { it.hasFixturePayload }
        .flatMap { layer ->
            listOf(
                "${layer.label}-shadow" to layer.shadowRect,
                "${layer.label}-content" to layer.contentRect,
            )
        }
        .filter { (_, rect) -> rect == null || !rect.isInsideTarget(dimensions.width, dimensions.height) }
        .map { (label, _) -> label }
    if (outOfBoundsSaveLayerDraws.isNotEmpty()) {
        return "rect-only windowed render requires SaveLayer materialized draws inside positive bounds: " +
            outOfBoundsSaveLayerDraws.joinToString()
    }

    val outOfBoundsMeshRibbons = commands.filterIsInstance<SceneCommand.MeshRibbon>()
        .filter { it.hasFixturePayload }
        .filter { ribbon ->
            val bounds = ribbon.bounds
            bounds == null || !bounds.isInsideTarget(dimensions.width, dimensions.height)
        }
        .map { it.label }
    if (outOfBoundsMeshRibbons.isNotEmpty()) {
        return "rect-only windowed render requires MeshRibbon bounds inside positive target: " +
            outOfBoundsMeshRibbons.joinToString()
    }

    if (commands.none {
            it is SceneCommand.FillRect ||
                it is SceneCommand.FillRRect ||
                it is SceneCommand.LinearGradientRect ||
                it is SceneCommand.BitmapRect ||
                it is SceneCommand.SaveLayer ||
                it is SceneCommand.RuntimeEffectTile ||
                it is SceneCommand.MeshRibbon
        }
    ) {
        return "rect-only windowed render requires at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only windowed render supports zero or one initial Clear before drawable commands"
    }

    return null
}

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

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top
