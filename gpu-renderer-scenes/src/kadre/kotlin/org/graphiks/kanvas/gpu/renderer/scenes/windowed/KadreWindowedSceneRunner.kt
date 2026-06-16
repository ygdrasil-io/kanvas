package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.nio.file.Path
import java.util.Locale
import org.graphiks.kadre.ActiveEventLoop
import org.graphiks.kadre.ApplicationHandler
import org.graphiks.kadre.EventLoop
import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.WindowId
import org.graphiks.kadre.core.ControlFlow
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendWindowSurface
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

class KadreWindowedSceneRunner(private val scene: GPURendererScene<*>) {
    fun run(frames: Int, output: Path) {
        require(frames > 0) { "Kadre windowed runner requires frames > 0" }
        val unsupportedReason = scene.kadreRunnerRectOnlyUnsupportedReason()
        require(unsupportedReason == null) { "${scene.sceneId.value} $unsupportedReason" }

        val osName = System.getProperty("os.name", "").lowercase(Locale.US)
        if (!osName.contains("mac")) {
            WindowedSceneSessionReport.blocked(
                scene = scene,
                reason = "kadre-windowed-runner-currently-macos-appkit",
                requestedFrames = frames,
                error = "Kadre windowed runner currently supports macOS AppKit + Metal only: os.name=$osName",
            ).writeTo(output)
            return
        }

        var completed = false
        runCatching {
            EventLoop().runApp(
                RectOnlyKadreApp(scene, frames, output) {
                    completed = true
                },
            )
        }.onFailure { failure ->
            if (!completed) {
                WindowedSceneSessionReport.blocked(
                    scene = scene,
                    reason = "kadre-windowed-initialization-failed",
                    requestedFrames = frames,
                    error = failure.message ?: failure.toString(),
                ).writeTo(output)
                completed = true
            }
        }
        if (!completed) {
            WindowedSceneSessionReport.blocked(
                scene = scene,
                reason = "kadre-windowed-initialization-failed",
                requestedFrames = frames,
                error = "Kadre event loop returned without writing a session report.",
            ).writeTo(output)
            completed = true
        }
    }
}

private class RectOnlyKadreApp(
    private val scene: GPURendererScene<*>,
    private val requestedFrames: Int,
    private val output: Path,
    private val onComplete: () -> Unit,
) : ApplicationHandler {
    private var runtimeSession: GPUBackendSession? = null
    private var surface: GPUBackendWindowSurface? = null
    private var window: Window? = null
    private var surfaceFormat: String? = null
    private var adapterInfo: String? = null
    private var presentedFrames = 0
    private var completed = false

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        runCatching {
            val win = eventLoop.createWindow(
                WindowAttributes(
                    title = "Kanvas GPU Renderer - ${scene.title}",
                    size = PhysicalSize(scene.dimensions.width, scene.dimensions.height),
                    visible = true,
                    resizable = true,
                ),
            )
            window = win

            val handle = win.rawWindowHandle
            if (handle !is RawWindowHandle.AppKit) {
                completeBlocked(eventLoop, "kadre-appkit-required", "Unsupported Kadre window handle: $handle")
                return
            }

            val layerAddress = handle.nsLayer.takeIf { it != 0L }
                ?: run {
                    completeBlocked(eventLoop, "kadre-appkit-required", "Kadre AppKit handle did not expose nsLayer")
                    return
                }

            val session = GPUBackendRuntimeFactory.createOrNull()
                ?: run {
                    completeBlocked(eventLoop, "wgpu-runtime-unavailable", "GPU backend runtime creation returned null")
                    return
                }
            runtimeSession = session
            try {
                adapterInfo = session.adapterInfo?.summary
                surface = session.createWindowSurface(
                    appKitMetalLayerBinding(
                        width = win.innerSize.width,
                        height = win.innerSize.height,
                        nsLayer = layerAddress,
                    ),
                )
                surfaceFormat = currentSurfaceFormat()
            } catch (failure: Throwable) {
                val error = failure.message ?: failure.toString()
                completeBlocked(eventLoop, initializationFailureReason(error), error)
                return
            }

            requestNextFrame(eventLoop)
        }.onFailure { failure ->
            completeBlocked(
                eventLoop,
                "kadre-windowed-initialization-failed",
                failure.message ?: failure.toString(),
            )
        }
    }

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        if (!completed) requestNextFrame(eventLoop)
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        when (event) {
            WindowEvent.RedrawRequested -> renderFrame(eventLoop)
            WindowEvent.CloseRequested -> {
                completeBlocked(
                    eventLoop,
                    "kadre-windowed-run-failed",
                    "Window closed before $requestedFrames requested frames were presented.",
                )
            }
            is WindowEvent.Resized -> resizeSurfaceIfDrawable(event.size)
            is WindowEvent.ScaleFactorChanged -> window?.innerSize?.let(::resizeSurfaceIfDrawable)
        }
    }

    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
        releaseResources()
    }

    private fun resizeSurfaceIfDrawable(size: PhysicalSize<Int>) {
        val windowSurface = surface ?: return
        if (size.width <= 0 || size.height <= 0) return
        windowSurface.resize(size.width, size.height)
        surfaceFormat = currentSurfaceFormat()
    }

    private fun renderFrame(eventLoop: ActiveEventLoop) {
        if (completed) return
        val windowSurface = surface ?: return
        val targetDescriptor = windowSurface.target.descriptor
        val draw = GPUBackendRectDraw(
            rgbaPremul = floatArrayOf(1f, 1f, 1f, 1f),
            scissorX = 0,
            scissorY = 0,
            scissorWidth = targetDescriptor.width,
            scissorHeight = targetDescriptor.height,
        )

        runCatching {
            windowSurface.encodeAndPresent(
                clearColor = WindowedRectOnlySceneShader.clearColor(scene).toGpuClearColor(),
            ) {
                drawFullscreenPass(
                    wgsl = WindowedRectOnlySceneShader.wgsl(scene),
                    colorFormat = targetDescriptor.colorFormat,
                    draws = listOf(draw),
                )
            }
        }.onFailure { failure ->
            val error = failure.message ?: failure.toString()
            val reason = if (error.startsWith("Surface texture acquisition failed with terminal status")) {
                "wgpu-surface-terminal-status"
            } else {
                "kadre-windowed-run-failed"
            }
            completeBlocked(eventLoop, reason, error)
            return
        }

        surfaceFormat = currentSurfaceFormat()
        presentedFrames++
        if (presentedFrames >= requestedFrames) {
            completePresented(eventLoop)
        } else {
            requestNextFrame(eventLoop)
        }
    }

    private fun requestNextFrame(eventLoop: ActiveEventLoop) {
        eventLoop.setControlFlow(ControlFlow.Poll)
        window?.requestRedraw()
    }

    private fun initializationFailureReason(error: String): String =
        when {
            "WGPU Metal instance creation returned null" in error -> "wgpu-instance-unavailable"
            "WGPU surface creation from Metal layer returned null" in error -> "wgpu-surface-unavailable"
            "WGPU adapter request failed for native surface" in error -> "wgpu-adapter-unavailable"
            "requestDevice" in error || "device" in error.lowercase(Locale.US) -> "wgpu-device-unavailable"
            else -> "kadre-windowed-initialization-failed"
        }

    private fun currentSurfaceFormat(): String? =
        surface?.target?.descriptor?.colorFormat?.toSessionSurfaceFormat()

    private fun String.toSessionSurfaceFormat(): String =
        when (lowercase(Locale.US)) {
            "bgra8unorm" -> "BGRA8Unorm"
            "bgra8unorm-srgb" -> "BGRA8UnormSrgb"
            "rgba8unorm" -> "RGBA8Unorm"
            "rgba8unorm-srgb" -> "RGBA8UnormSrgb"
            else -> this
        }

    private fun completePresented(eventLoop: ActiveEventLoop) {
        if (completed) return
        completed = true
        try {
            WindowedSceneSessionReport.presented(
                scene = scene,
                requestedFrames = requestedFrames,
                surfaceFormat = surfaceFormat ?: "unknown",
                adapterInfo = adapterInfo ?: "unknown-adapter",
            ).writeTo(output)
            onComplete()
        } finally {
            releaseResources()
            eventLoop.setControlFlow(ControlFlow.Wait)
            eventLoop.exit()
        }
    }

    private fun completeBlocked(eventLoop: ActiveEventLoop, reason: String, error: String) {
        if (completed) return
        completed = true
        try {
            WindowedSceneSessionReport.blocked(
                scene = scene,
                reason = reason,
                requestedFrames = requestedFrames,
                presentedFrames = presentedFrames,
                surfaceFormat = surfaceFormat,
                adapterInfo = adapterInfo,
                error = error,
            ).writeTo(output)
            onComplete()
        } finally {
            releaseResources()
            eventLoop.setControlFlow(ControlFlow.Wait)
            eventLoop.exit()
        }
    }

    private fun releaseResources() {
        surface?.let { runCatching { it.close() } }
        runtimeSession?.let { runCatching { it.close() } }
        surface = null
        runtimeSession = null
        window = null
    }
}

private fun SceneColor.toGpuClearColor(): GPUClearColor =
    GPUClearColor(
        red = (r * a).toDouble(),
        green = (g * a).toDouble(),
        blue = (b * a).toDouble(),
        alpha = a.toDouble(),
    )

private fun GPURendererScene<*>.kadreRunnerRectOnlyUnsupportedReason(): String? {
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

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top
