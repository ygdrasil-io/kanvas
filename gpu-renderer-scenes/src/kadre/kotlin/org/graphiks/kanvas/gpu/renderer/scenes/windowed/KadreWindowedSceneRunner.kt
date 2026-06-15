package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import ffi.JvmNativeAddress
import ffi.LibraryLoader
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.SurfaceTextureStatus
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.webgpu.WGPULowLevelApi
import java.lang.foreign.MemorySegment
import java.nio.file.Path
import java.util.Locale
import kotlinx.coroutines.runBlocking
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
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

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

@OptIn(WGPULowLevelApi::class)
private class RectOnlyKadreApp(
    private val scene: GPURendererScene<*>,
    private val requestedFrames: Int,
    private val output: Path,
    private val onComplete: () -> Unit,
) : ApplicationHandler {
    private var wgpu: WGPU? = null
    private var surface: NativeSurface? = null
    private var device: GPUDevice? = null
    private var window: Window? = null
    private var surfaceFormat: GPUTextureFormat? = null
    private var surfaceAlphaMode: CompositeAlphaMode = CompositeAlphaMode.Auto
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

            LibraryLoader.load()
            val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
                ?: run {
                    completeBlocked(eventLoop, "wgpu-instance-unavailable", "WGPU Metal instance creation returned null")
                    return
                }
            wgpu = instance

            val layerAddress = handle.nsLayer.takeIf { it != 0L }
                ?: run {
                    completeBlocked(eventLoop, "kadre-appkit-required", "Kadre AppKit handle did not expose nsLayer")
                    return
                }
            val surf = instance.getSurfaceFromMetalLayer(JvmNativeAddress(MemorySegment.ofAddress(layerAddress)))
                ?: run {
                    completeBlocked(eventLoop, "wgpu-surface-unavailable", "WGPU surface creation from Kadre CAMetalLayer returned null")
                    return
                }
            surface = surf

            val adapter = instance.requestAdapter(surf)
                ?: run {
                    completeBlocked(eventLoop, "wgpu-adapter-unavailable", "WGPU adapter request failed for Kadre surface")
                    return
                }
            try {
                adapterInfo = adapter.info.toString()
                surf.computeSurfaceCapabilities(adapter)
                val gpuDevice = runBlocking { adapter.requestDevice() }
                    .getOrElse { error ->
                        completeBlocked(eventLoop, "wgpu-device-unavailable", error.message ?: error.toString())
                        return
                    }
                device = gpuDevice
                surfaceFormat = surf.supportedFormats.firstOrNull { it == GPUTextureFormat.BGRA8Unorm }
                    ?: surf.supportedFormats.firstOrNull()
                    ?: GPUTextureFormat.BGRA8Unorm
                surfaceAlphaMode = surf.supportedAlphaMode.firstOrNull { it == CompositeAlphaMode.Opaque }
                    ?: CompositeAlphaMode.Auto
                configureSurface(win.innerSize)
            } finally {
                adapter.close()
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
            is WindowEvent.Resized -> configureSurface(event.size)
            is WindowEvent.ScaleFactorChanged -> window?.innerSize?.let(::configureSurface)
        }
    }

    override fun destroySurfaces(eventLoop: ActiveEventLoop) {
        releaseResources()
    }

    private fun configureSurface(size: PhysicalSize<Int>) {
        val surf = surface ?: return
        val gpuDevice = device ?: return
        val format = surfaceFormat ?: return
        if (size.width <= 0 || size.height <= 0) return
        surf.configure(
            SurfaceConfiguration(
                device = gpuDevice,
                format = format,
                usage = GPUTextureUsage.RenderAttachment,
                alphaMode = surfaceAlphaMode,
            ),
            size.width.toUInt(),
            size.height.toUInt(),
        )
    }

    private fun renderFrame(eventLoop: ActiveEventLoop) {
        if (completed) return
        val surf = surface ?: return
        val gpuDevice = device ?: return
        val format = surfaceFormat ?: return
        val surfaceTexture = surf.getCurrentTexture()
        when (surfaceTexture.status) {
            SurfaceTextureStatus.lost,
            SurfaceTextureStatus.outdated -> {
                surfaceTexture.texture.close()
                window?.innerSize?.let(::configureSurface)
                requestNextFrame(eventLoop)
                return
            }
            SurfaceTextureStatus.outOfMemory,
            SurfaceTextureStatus.deviceLost -> {
                surfaceTexture.texture.close()
                completeBlocked(eventLoop, "wgpu-surface-terminal-status", surfaceTexture.status.name)
                return
            }
            SurfaceTextureStatus.success,
            SurfaceTextureStatus.timeout -> Unit
        }

        val texture = surfaceTexture.texture
        GpuResourceScope().use { gpuResources ->
            val textureView = gpuResources.track(texture.createView(null)) { it.close() }
            val pipeline = gpuResources.track(createScenePipeline(gpuDevice, format)) { it.close() }
            val encoder = gpuResources.trackIfAutoCloseable(gpuDevice.createCommandEncoder())
            val renderPass = encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = textureView,
                            loadOp = GPULoadOp.Clear,
                            storeOp = GPUStoreOp.Store,
                            clearValue = WindowedRectOnlySceneShader.clearColor(scene).toWebGpuColor(),
                        ),
                    ),
                ),
            )
            renderPass.setPipeline(pipeline)
            renderPass.draw(3u, 1u, 0u, 0u)
            renderPass.end()
            val commandBuffer = gpuResources.trackIfAutoCloseable(encoder.finish())
            gpuDevice.queue.submit(listOf(commandBuffer))
            surf.present()
            presentedFrames++
        }

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

    private fun createScenePipeline(gpuDevice: GPUDevice, format: GPUTextureFormat): GPURenderPipeline {
        val shader = gpuDevice.createShaderModule(
            ShaderModuleDescriptor(code = WindowedRectOnlySceneShader.wgsl(scene)),
        )
        return try {
            gpuDevice.createRenderPipeline(
                RenderPipelineDescriptor(
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(ColorTargetState(format = format)),
                    ),
                ),
            )
        } finally {
            shader.close()
        }
    }

    private fun completePresented(eventLoop: ActiveEventLoop) {
        if (completed) return
        completed = true
        try {
            WindowedSceneSessionReport.presented(
                scene = scene,
                requestedFrames = requestedFrames,
                surfaceFormat = surfaceFormat?.name ?: "unknown",
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
                surfaceFormat = surfaceFormat?.name,
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
        device?.let { runCatching { it.close() } }
        surface?.let { runCatching { it.close() } }
        wgpu?.let { runCatching { it.close() } }
        device = null
        surface = null
        wgpu = null
        window = null
    }

    private class GpuResourceScope : AutoCloseable {
        private val closeActions = ArrayDeque<() -> Unit>()

        fun <T> track(resource: T, close: (T) -> Unit): T {
            closeActions.addFirst { close(resource) }
            return resource
        }

        fun <T> trackIfAutoCloseable(resource: T): T {
            if (resource is AutoCloseable) {
                track(resource) { it.close() }
            }
            return resource
        }

        override fun close() {
            var firstFailure: Throwable? = null
            while (closeActions.isNotEmpty()) {
                try {
                    closeActions.removeFirst().invoke()
                } catch (failure: Throwable) {
                    if (firstFailure == null) {
                        firstFailure = failure
                    } else {
                        firstFailure.addSuppressed(failure)
                    }
                }
            }
            firstFailure?.let { throw it }
        }
    }
}

private fun SceneColor.toWebGpuColor(): Color =
    Color(r.toDouble() * a.toDouble(), g.toDouble() * a.toDouble(), b.toDouble() * a.toDouble(), a.toDouble())

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
                is SceneCommand.FilterNode,
                is SceneCommand.RuntimeEffectTile -> null
                is SceneCommand -> command.family
                else -> command::class.simpleName ?: "unknown-command"
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only windowed render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, clip, fixture-backed bitmap-rect, fixture-backed filter-node, and fixture-backed runtime-effect command families: " +
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

    val runtimeEffectMarkers = commands.filterIsInstance<SceneCommand.RuntimeEffectTile>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (runtimeEffectMarkers.isNotEmpty()) {
        return "rect-only windowed render requires fixture-backed RuntimeEffectTile payloads: " +
            runtimeEffectMarkers.joinToString()
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
                it is SceneCommand.BitmapRect ||
                it is SceneCommand.RuntimeEffectTile
        }
    ) {
        return "rect-only windowed render requires at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, or RuntimeEffectTile command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only windowed render supports zero or one initial Clear before drawable commands"
    }

    return null
}
