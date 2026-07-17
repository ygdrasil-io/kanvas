package org.graphiks.kanvas.gpu.renderer.scenes.windowed

import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
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
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUOffscreenTargetRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneCompletedFrameResult
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSceneFrameSession
import org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedWindowOutput
import org.graphiks.kanvas.gpu.renderer.execution.GPUSceneFrameOutputRequest
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedSceneFrameRecorder
import org.graphiks.kanvas.gpu.renderer.scenes.offscreen.PreparedSceneFrameResult
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome

class KadreWindowedSceneRunner(private val scene: GPURendererScene<SceneCommand>) {
    fun run(frames: Int, output: Path) {
        require(frames > 0) { "Kadre windowed runner requires frames > 0" }
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
            EventLoop().runApp(PreparedSceneKadreApp(scene, frames, output) { completed = true })
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
        }
    }
}

private class PreparedSceneKadreApp(
    private val scene: GPURendererScene<SceneCommand>,
    private val requestedFrames: Int,
    private val output: Path,
    private val onComplete: () -> Unit,
) : ApplicationHandler {
    private val completionExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kanvas-kadre-frame-completion").apply { isDaemon = true }
    }
    private val pendingCompletion = AtomicReference<ObservedCompletion?>(null)
    private var runtimeSession: GPUBackendSession? = null
    private var preparedSession: GPUPreparedSceneFrameSession? = null
    private var preparedOutput: GPUPreparedWindowOutput? = null
    private var window: Window? = null
    private var adapterInfo: String? = null
    private var surfaceFormat: String? = null
    private var presentedFrames = 0
    private var frameInFlight = false
    private val frameTimingSamplesNanos = mutableListOf<Long>()
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
            if (handle !is RawWindowHandle.AppKit || handle.nsLayer == 0L) {
                completeBlocked(eventLoop, "kadre-appkit-required", "Kadre AppKit handle did not expose nsLayer: $handle")
                return
            }
            val session = GPUBackendRuntimeFactory.createOrNull() ?: run {
                completeBlocked(eventLoop, "wgpu-runtime-unavailable", "GPU backend runtime creation returned null")
                return
            }
            runtimeSession = session
            val capabilities = session.capabilities ?: run {
                completeBlocked(eventLoop, "wgpu-capabilities-unavailable", "GPU backend capabilities are unavailable")
                return
            }
            val generation = capabilities.snapshotId.substringAfterLast('-').toLongOrNull()
                ?.let(::GPUDeviceGenerationID) ?: run {
                completeBlocked(eventLoop, "wgpu-device-generation-unavailable", "GPU device generation is unavailable")
                return
            }
            preparedSession = session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    "rgba8unorm",
                ),
            )
            preparedOutput = session.prepareWindowOutput(
                appKitMetalLayerBinding(win.innerSize.width, win.innerSize.height, handle.nsLayer),
            ).also { windowOutput ->
                adapterInfo = windowOutput.adapterInfo?.summary ?: session.adapterInfo?.summary
                surfaceFormat = windowOutput.colorFormat.value.toSessionSurfaceFormat()
            }
            deviceGeneration = generation
            requestNextFrame(eventLoop)
        }.onFailure { failure ->
            completeBlocked(
                eventLoop,
                initializationFailureReason(failure.message ?: failure.toString()),
                failure.message ?: failure.toString(),
            )
        }
    }

    private var deviceGeneration: GPUDeviceGenerationID? = null

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        pendingCompletion.getAndSet(null)?.let { completion ->
            frameInFlight = false
            if (completion.failure != null) {
                completeBlocked(
                    eventLoop,
                    "kadre-windowed-frame-completion-failed",
                    completion.failure.message ?: completion.failure.toString(),
                )
                return
            }
            val result = requireNotNull(completion.result)
            if (result.outcome != GPUFrameStructuralOutcome.Succeeded) {
                val diagnostic = result.diagnostic
                completeBlocked(
                    eventLoop,
                    diagnostic?.code?.value ?: "kadre-windowed-frame-refused",
                    diagnostic?.message ?: "Prepared scene frame did not succeed.",
                )
                return
            }
            frameTimingSamplesNanos += (System.nanoTime() - completion.startedNanos).coerceAtLeast(1L)
            presentedFrames += 1
            if (presentedFrames >= requestedFrames) {
                completePresented(eventLoop)
                return
            }
        }
        if (!completed && !frameInFlight) requestNextFrame(eventLoop)
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        when (event) {
            WindowEvent.RedrawRequested -> renderFrame(eventLoop)
            WindowEvent.CloseRequested -> completeBlocked(
                eventLoop,
                "kadre-windowed-run-failed",
                "Window closed before $requestedFrames requested frames were presented.",
            )
            is WindowEvent.Resized -> resizeSurfaceIfDrawable(event.size)
            is WindowEvent.ScaleFactorChanged -> window?.innerSize?.let(::resizeSurfaceIfDrawable)
        }
    }

    override fun destroySurfaces(eventLoop: ActiveEventLoop) = releaseResources()

    private fun resizeSurfaceIfDrawable(size: PhysicalSize<Int>) {
        if (size.width <= 0 || size.height <= 0) return
        preparedOutput?.resize(size.width, size.height)
    }

    private fun renderFrame(eventLoop: ActiveEventLoop) {
        if (completed || frameInFlight) return
        val session = preparedSession ?: return
        val windowOutput = preparedOutput ?: return
        val capabilities = runtimeSession?.capabilities ?: return
        val generation = deviceGeneration ?: return
        val recorded = when (
            val result = PreparedSceneFrameRecorder().record(
                scene = scene,
                capabilities = capabilities,
                deviceGeneration = generation,
                frameOrdinal = presentedFrames + 1L,
                withReadback = false,
            )
        ) {
            is PreparedSceneFrameResult.Recorded -> result
            is PreparedSceneFrameResult.Refused -> {
                completeBlocked(eventLoop, result.code, result.message)
                return
            }
        }
        frameInFlight = true
        val started = System.nanoTime()
        session.renderFrame(
            recorded.taskList,
            GPUSceneFrameOutputRequest.PresentToWindow(windowOutput),
        ).completion.whenCompleteAsync({ result, failure ->
            pendingCompletion.compareAndSet(null, ObservedCompletion(result, failure, started))
        }, completionExecutor)
    }

    private fun requestNextFrame(eventLoop: ActiveEventLoop) {
        eventLoop.setControlFlow(ControlFlow.Poll)
        window?.requestRedraw()
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
                frameTiming = WindowedFrameTimingReport.wallClockEncodePresent(
                    warmupFrames = frameTimingWarmupFrames(frameTimingSamplesNanos.size),
                    samples = frameTimingSamplesNanos,
                ),
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
        completionExecutor.shutdownNow()
        preparedSession?.let { runCatching { it.close() } }
        preparedOutput?.let { runCatching { it.close() } }
        runtimeSession?.let { runCatching { it.close() } }
        preparedSession = null
        preparedOutput = null
        runtimeSession = null
        window = null
    }

    private fun initializationFailureReason(error: String): String = when {
        "instance creation" in error.lowercase(Locale.US) -> "wgpu-instance-unavailable"
        "surface creation" in error.lowercase(Locale.US) -> "wgpu-surface-unavailable"
        "adapter request" in error.lowercase(Locale.US) -> "wgpu-adapter-unavailable"
        "device" in error.lowercase(Locale.US) -> "wgpu-device-unavailable"
        else -> "kadre-windowed-initialization-failed"
    }
}

private data class ObservedCompletion(
    val result: GPUPreparedSceneCompletedFrameResult?,
    val failure: Throwable?,
    val startedNanos: Long,
)

private fun frameTimingWarmupFrames(sampleCount: Int): Int =
    if (sampleCount <= 1) 0 else minOf(3, sampleCount - 1)

private fun String.toSessionSurfaceFormat(): String = when (lowercase(Locale.US)) {
    "bgra8unorm" -> "BGRA8Unorm"
    "bgra8unorm-srgb" -> "BGRA8UnormSrgb"
    "rgba8unorm" -> "RGBA8Unorm"
    "rgba8unorm-srgb" -> "RGBA8UnormSrgb"
    else -> this
}
