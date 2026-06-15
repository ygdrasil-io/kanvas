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
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.exists
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

private const val SOLID_CARD_STACK_SCENE_ID = "solid-card-stack"

class KadreWindowedSceneRunner(private val scene: GPURendererScene<*>) {
    fun run(frames: Int, output: Path) {
        require(frames > 0) { "Kadre windowed runner requires frames > 0" }
        require(scene.sceneId.value == SOLID_CARD_STACK_SCENE_ID) {
            "Kadre windowed runner only supports $SOLID_CARD_STACK_SCENE_ID"
        }

        val osName = System.getProperty("os.name", "").lowercase(Locale.US)
        if (!osName.contains("mac")) {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "blocked",
                reason = "kadre-windowed-runner-currently-macos-appkit",
                requestedFrames = frames,
                presentedFrames = 0,
                surface = scene.windowedSurface(format = null),
                adapterInfo = null,
                error = "Kadre windowed runner currently supports macOS AppKit + Metal only: os.name=$osName",
            ).writeTo(output)
            return
        }

        var completed = false
        runCatching {
            EventLoop().runApp(
                SolidCardStackKadreApp(scene, frames, output) {
                    completed = true
                },
            )
        }.onFailure { failure ->
            if (!output.exists()) {
                WindowedSceneSessionReport(
                    sceneId = scene.sceneId.value,
                    status = "blocked",
                    reason = "kadre-windowed-initialization-failed",
                    requestedFrames = frames,
                    presentedFrames = 0,
                    surface = scene.windowedSurface(format = null),
                    adapterInfo = null,
                    error = failure.message ?: failure.toString(),
                ).writeTo(output)
            }
        }
        if (!completed && !Files.exists(output)) {
            WindowedSceneSessionReport(
                sceneId = scene.sceneId.value,
                status = "blocked",
                reason = "kadre-windowed-initialization-failed",
                requestedFrames = frames,
                presentedFrames = 0,
                surface = scene.windowedSurface(format = null),
                adapterInfo = null,
                error = "Kadre event loop returned without writing a session report.",
            ).writeTo(output)
        }
    }
}

@OptIn(WGPULowLevelApi::class)
private class SolidCardStackKadreApp(
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
                    title = "Kanvas GPU Renderer - Solid Card Stack",
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
        val textureView = texture.createView(null)
        val pipeline = createScenePipeline(gpuDevice, format)
        val encoder = gpuDevice.createCommandEncoder()
        try {
            val renderPass = encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = textureView,
                            loadOp = GPULoadOp.Clear,
                            storeOp = GPUStoreOp.Store,
                            clearValue = scene.clearColor().toWebGpuColor(),
                        ),
                    ),
                ),
            )
            renderPass.setPipeline(pipeline)
            renderPass.draw(3u, 1u, 0u, 0u)
            renderPass.end()
            val commandBuffer = encoder.finish()
            gpuDevice.queue.submit(listOf(commandBuffer))
            surf.present()
            presentedFrames++
        } finally {
            textureView.close()
            encoder.close()
            pipeline.close()
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
            ShaderModuleDescriptor(code = solidCardStackWgsl(scene)),
        )
        val pipeline = gpuDevice.createRenderPipeline(
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
        shader.close()
        return pipeline
    }

    private fun completePresented(eventLoop: ActiveEventLoop) {
        if (completed) return
        completed = true
        onComplete()
        WindowedSceneSessionReport.presented(
            scene = scene,
            requestedFrames = requestedFrames,
            surfaceFormat = surfaceFormat?.name ?: "unknown",
            adapterInfo = adapterInfo ?: "unknown-adapter",
        ).writeTo(output)
        releaseResources()
        eventLoop.setControlFlow(ControlFlow.Wait)
        eventLoop.exit()
    }

    private fun completeBlocked(eventLoop: ActiveEventLoop, reason: String, error: String) {
        if (completed) return
        completed = true
        onComplete()
        WindowedSceneSessionReport(
            sceneId = scene.sceneId.value,
            status = "blocked",
            reason = reason,
            requestedFrames = requestedFrames,
            presentedFrames = presentedFrames,
            surface = scene.windowedSurface(format = surfaceFormat?.name),
            adapterInfo = adapterInfo,
            error = error,
        ).writeTo(output)
        releaseResources()
        eventLoop.setControlFlow(ControlFlow.Wait)
        eventLoop.exit()
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
}

private fun GPURendererScene<*>.windowedSurface(format: String?): WindowedSceneSurface =
    WindowedSceneSurface(
        width = dimensions.width,
        height = dimensions.height,
        format = format,
    )

private fun GPURendererScene<*>.clearColor(): SceneColor =
    commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
        ?: SceneColor(0.035f, 0.04f, 0.05f, 1f)

private fun GPURendererScene<*>.fills(): List<SceneCommand.FillRect> =
    commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.FillRect }
        .sortedWith(
            compareBy<IndexedValue<Any?>> { (_, command) ->
                (command as SceneCommand.FillRect).paintOrder
            }.thenBy { it.index },
        )
        .map { (_, command) -> command as SceneCommand.FillRect }

private fun solidCardStackWgsl(scene: GPURendererScene<*>): String {
    val clear = scene.clearColor()
    val fillBranches = scene.fills().joinToString(separator = "\n") { fill ->
        """
            if (pixel.x >= ${fill.rect.left.wgslFloat()} && pixel.x < ${fill.rect.right.wgslFloat()} &&
                pixel.y >= ${fill.rect.top.wgslFloat()} && pixel.y < ${fill.rect.bottom.wgslFloat()}) {
                color = src_over(color, vec4<f32>(
                    ${fill.color.r.wgslFloat()},
                    ${fill.color.g.wgslFloat()},
                    ${fill.color.b.wgslFloat()},
                    ${fill.color.a.wgslFloat()}
                ));
            }
        """.trimIndent()
    }

    return """
        @vertex
        fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4<f32> {
            let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
            let y = f32(idx & 2u) * 2.0 - 1.0;
            return vec4<f32>(x, y, 0.0, 1.0);
        }

        fn src_over(dst: vec4<f32>, src: vec4<f32>) -> vec4<f32> {
            let out_alpha = src.a + dst.a * (1.0 - src.a);
            let out_rgb = (src.rgb * src.a + dst.rgb * dst.a * (1.0 - src.a)) / max(out_alpha, 0.0001);
            return vec4<f32>(out_rgb, out_alpha);
        }

        @fragment
        fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
            let pixel = position.xy;
            var color = vec4<f32>(
                ${clear.r.wgslFloat()},
                ${clear.g.wgslFloat()},
                ${clear.b.wgslFloat()},
                ${clear.a.wgslFloat()}
            );
        ${fillBranches.prependIndent("    ")}
            return color;
        }
    """.trimIndent()
}

private fun Float.wgslFloat(): String = String.format(Locale.US, "%.6f", this)

private fun SceneColor.toWebGpuColor(): Color =
    Color(r.toDouble() * a.toDouble(), g.toDouble() * a.toDouble(), b.toDouble() * a.toDouble(), a.toDouble())
