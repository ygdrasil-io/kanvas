package org.skia.kadre.runtime

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
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.ActiveEventLoop
import org.graphiks.kadre.ApplicationHandler
import org.graphiks.kadre.EventLoop
import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.WindowId
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.Window
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint

private const val DEFAULT_WIDTH = 640
private const val DEFAULT_HEIGHT = 420

internal data class NativeSmokeConfig(
    val output: Path,
    val frames: Int = 3,
)

internal data class NativeSmokeResult(
    val status: String,
    val reason: String,
    val nativePresented: Boolean,
    val requestedFrames: Int,
    val presentedFrames: Int,
    val redrawEvents: Int,
    val width: Int,
    val height: Int,
    val surfaceFormat: String?,
    val adapterInfo: String?,
    val cpuReferenceChecksum: Long,
    val cpuReferenceNonTransparentPixels: Int,
    val firstFrameMs: Double?,
    val averageFrameMs: Double?,
    val surfaceStatuses: List<String>,
    val error: String? = null,
) {
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"generatedBy\": \"kadre-runtime:M69KadreNativeSmoke\",")
        appendLine("  \"route\": \"kadre.native.windowed.wgpu4\",")
        appendLine("  \"status\": ${status.json()},")
        appendLine("  \"reason\": ${reason.json()},")
        appendLine("  \"nativePresented\": $nativePresented,")
        appendLine("  \"requestedFrames\": $requestedFrames,")
        appendLine("  \"presentedFrames\": $presentedFrames,")
        appendLine("  \"redrawEvents\": $redrawEvents,")
        appendLine("  \"surface\": {")
        appendLine("    \"width\": $width,")
        appendLine("    \"height\": $height,")
        appendLine("    \"format\": ${surfaceFormat?.json() ?: "null"}")
        appendLine("  },")
        appendLine("  \"adapterInfo\": ${adapterInfo?.json() ?: "null"},")
        appendLine("  \"cpuReference\": {")
        appendLine("    \"source\": \"kanvas-skia SkBitmap/SkCanvas\",")
        appendLine("    \"checksum\": $cpuReferenceChecksum,")
        appendLine("    \"nonTransparentPixels\": $cpuReferenceNonTransparentPixels")
        appendLine("  },")
        appendLine("  \"frameTiming\": {")
        appendLine("    \"firstFrameMs\": ${firstFrameMs?.formatJsonNumber() ?: "null"},")
        appendLine("    \"averageFrameMs\": ${averageFrameMs?.formatJsonNumber() ?: "null"},")
        appendLine("    \"nativeTimingClaim\": \"present-call-duration-only\"")
        appendLine("  },")
        appendLine("  \"surfaceStatuses\": [${surfaceStatuses.joinToString(", ") { it.json() }}],")
        appendLine("  \"linearIssues\": [\"FOR-56\", \"FOR-57\", \"FOR-58\", \"FOR-59\"],")
        appendLine("  \"nonClaims\": [")
        appendLine("    \"This smoke proves Kadre native window presentation for a bounded WGSL scene only.\",")
        appendLine("    \"It does not prove broad Kanvas display-list replay or input-driven interaction yet.\"")
        appendLine("  ],")
        appendLine("  \"error\": ${error?.json() ?: "null"}")
        appendLine("}")
    }
}

@OptIn(WGPULowLevelApi::class)
internal class M69KadreNativeSmokeApp(
    private val config: NativeSmokeConfig,
    private val onComplete: (NativeSmokeResult) -> Unit,
) : ApplicationHandler {
    private var wgpu: WGPU? = null
    private var surface: NativeSurface? = null
    private var device: GPUDevice? = null
    private var window: Window? = null
    private var surfaceFormat: GPUTextureFormat = GPUTextureFormat.BGRA8Unorm
    private var surfaceAlphaMode: CompositeAlphaMode = CompositeAlphaMode.Auto
    private var adapterInfo: String? = null
    private var redrawEvents = 0
    private var presentedFrames = 0
    private var completed = false
    private val frameDurationsMs = mutableListOf<Double>()
    private val surfaceStatuses = mutableListOf<String>()
    private val cpuReference = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT)

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        runCatching {
            val win = eventLoop.createWindow(
                WindowAttributes(
                    title = "Kanvas M69 - Kadre native smoke",
                    size = PhysicalSize(DEFAULT_WIDTH, DEFAULT_HEIGHT),
                    visible = true,
                    resizable = true,
                )
            )
            window = win
            val handle = win.rawWindowHandle
            if (handle !is RawWindowHandle.AppKit) {
                completeBlocked(eventLoop, "m69.kadre-native-appkit-required", "Unsupported Kadre window handle: $handle")
                return
            }

            LibraryLoader.load()
            val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
                ?: run {
                    completeBlocked(eventLoop, "m69.wgpu-instance-unavailable", "WGPU Metal instance creation returned null")
                    return
                }
            wgpu = instance

            val layerAddress = handle.nsLayer.takeIf { it != 0L }
                ?: run {
                    completeBlocked(eventLoop, "m69.kadre-metal-layer-missing", "Kadre AppKit handle did not expose nsLayer")
                    return
                }
            val surf = instance.getSurfaceFromMetalLayer(JvmNativeAddress(MemorySegment.ofAddress(layerAddress)))
                ?: run {
                    completeBlocked(eventLoop, "m69.wgpu-surface-unavailable", "WGPU surface creation from Kadre CAMetalLayer returned null")
                    return
                }
            surface = surf

            val adapter = instance.requestAdapter(surf)
                ?: run {
                    completeBlocked(eventLoop, "m69.wgpu-adapter-unavailable", "WGPU adapter request failed for Kadre surface")
                    return
                }
            adapterInfo = adapter.info.toString()
            surf.computeSurfaceCapabilities(adapter)
            val gpuDevice = runBlocking { adapter.requestDevice() }
                .getOrElse { err ->
                    completeBlocked(eventLoop, "m69.wgpu-device-unavailable", err.message ?: err.toString())
                    return
                }
            device = gpuDevice
            surfaceFormat = surf.supportedFormats.firstOrNull { it == GPUTextureFormat.BGRA8Unorm }
                ?: surf.supportedFormats.firstOrNull()
                ?: GPUTextureFormat.BGRA8Unorm
            surfaceAlphaMode = surf.supportedAlphaMode.firstOrNull { it == CompositeAlphaMode.Opaque }
                ?: CompositeAlphaMode.Auto
            configureSurface(win.innerSize)
            adapter.close()
            win.requestRedraw()
        }.onFailure { error ->
            completeBlocked(eventLoop, "m69.kadre-native-initialization-failed", error.message ?: error.toString())
        }
    }

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        if (!completed) window?.requestRedraw()
    }

    override fun windowEvent(eventLoop: ActiveEventLoop, windowId: WindowId, event: Any) {
        when (event) {
            WindowEvent.RedrawRequested -> renderFrame(eventLoop)
            WindowEvent.CloseRequested -> {
                releaseResources()
                eventLoop.exit()
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
        if (size.width <= 0 || size.height <= 0) return
        surf.configure(
            SurfaceConfiguration(
                device = gpuDevice,
                format = surfaceFormat,
                usage = GPUTextureUsage.RenderAttachment,
                alphaMode = surfaceAlphaMode,
            ),
            size.width.toUInt(),
            size.height.toUInt(),
        )
    }

    private fun renderFrame(eventLoop: ActiveEventLoop) {
        if (completed) return
        redrawEvents++
        val surf = surface ?: return
        val gpuDevice = device ?: return
        val started = System.nanoTime()
        val surfaceTexture = surf.getCurrentTexture()
        surfaceStatuses += surfaceTexture.status.name
        when (surfaceTexture.status) {
            SurfaceTextureStatus.lost,
            SurfaceTextureStatus.outdated -> {
                surfaceTexture.texture.close()
                window?.innerSize?.let(::configureSurface)
                return
            }
            SurfaceTextureStatus.outOfMemory,
            SurfaceTextureStatus.deviceLost -> {
                surfaceTexture.texture.close()
                completeBlocked(eventLoop, "m69.wgpu-surface-terminal-status", surfaceTexture.status.name)
                return
            }
            SurfaceTextureStatus.success,
            SurfaceTextureStatus.timeout -> Unit
        }

        val texture = surfaceTexture.texture
        val textureView = texture.createView(null)
        val pipeline = createScenePipeline(gpuDevice, presentedFrames)
        val encoder = gpuDevice.createCommandEncoder()
        val renderPass = encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = textureView,
                        loadOp = GPULoadOp.Clear,
                        storeOp = GPUStoreOp.Store,
                        clearValue = Color(r = 0.03, g = 0.035, b = 0.045, a = 1.0),
                    )
                )
            )
        )
        renderPass.setPipeline(pipeline)
        renderPass.draw(6u, 1u, 0u, 0u)
        renderPass.end()
        val commandBuffer = encoder.finish()
        gpuDevice.queue.submit(listOf(commandBuffer))
        surf.present()
        textureView.close()
        encoder.close()
        pipeline.close()
        presentedFrames++
        frameDurationsMs += (System.nanoTime() - started) / 1_000_000.0
        if (presentedFrames >= config.frames) {
            completeNative(eventLoop)
        } else {
            window?.requestRedraw()
        }
    }

    private fun createScenePipeline(gpuDevice: GPUDevice, frameIndex: Int): GPURenderPipeline {
        val phase = (frameIndex % 60) / 60.0
        val shaderModule = gpuDevice.createShaderModule(ShaderModuleDescriptor(code = firstSceneWgsl(phase)))
        val pipeline = gpuDevice.createRenderPipeline(
            RenderPipelineDescriptor(
                vertex = VertexState(module = shaderModule, entryPoint = "vs_main"),
                primitive = PrimitiveState(),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = "fs_main",
                    targets = listOf(ColorTargetState(format = surfaceFormat)),
                ),
            )
        )
        shaderModule.close()
        return pipeline
    }

    private fun completeNative(eventLoop: ActiveEventLoop) {
        if (completed) return
        completed = true
        val size = window?.innerSize ?: PhysicalSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        onComplete(
            NativeSmokeResult(
                status = "native-runnable",
                reason = "m69.kadre-native-presented-frames",
                nativePresented = true,
                requestedFrames = config.frames,
                presentedFrames = presentedFrames,
                redrawEvents = redrawEvents,
                width = size.width,
                height = size.height,
                surfaceFormat = surfaceFormat.name,
                adapterInfo = adapterInfo,
                cpuReferenceChecksum = cpuReference.first,
                cpuReferenceNonTransparentPixels = cpuReference.second,
                firstFrameMs = frameDurationsMs.firstOrNull(),
                averageFrameMs = frameDurationsMs.takeIf { it.isNotEmpty() }?.average(),
                surfaceStatuses = surfaceStatuses.toList(),
            )
        )
        releaseResources()
        eventLoop.exit()
    }

    private fun completeBlocked(eventLoop: ActiveEventLoop, reason: String, error: String) {
        if (completed) return
        completed = true
        val size = window?.innerSize ?: PhysicalSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        onComplete(
            NativeSmokeResult(
                status = "blocked",
                reason = reason,
                nativePresented = false,
                requestedFrames = config.frames,
                presentedFrames = presentedFrames,
                redrawEvents = redrawEvents,
                width = size.width,
                height = size.height,
                surfaceFormat = surfaceFormat.name,
                adapterInfo = adapterInfo,
                cpuReferenceChecksum = cpuReference.first,
                cpuReferenceNonTransparentPixels = cpuReference.second,
                firstFrameMs = frameDurationsMs.firstOrNull(),
                averageFrameMs = frameDurationsMs.takeIf { it.isNotEmpty() }?.average(),
                surfaceStatuses = surfaceStatuses.toList(),
                error = error,
            )
        )
        releaseResources()
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

private fun parseArgs(args: Array<String>): NativeSmokeConfig {
    var output = Path("reports/wgsl-pipeline/m69-kadre-native/native-smoke.json")
    var frames = 3
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--output" -> output = Path(args.getOrNull(++i) ?: error("--output requires a path"))
            "--frames" -> frames = args.getOrNull(++i)?.toIntOrNull() ?: error("--frames requires an integer")
        }
        i++
    }
    return NativeSmokeConfig(output = output, frames = frames.coerceAtLeast(1))
}

private fun writeResult(path: Path, result: NativeSmokeResult) {
    path.parent?.createDirectories()
    path.writeText(result.toJson())
}

private fun renderCpuReference(width: Int, height: Int): Pair<Long, Int> {
    val bitmap = SkBitmap(width, height)
    bitmap.eraseColor(0xFF0A0D12.toInt())
    val canvas = SkCanvas(bitmap)
    val blue = SkPaint().apply { color = 0xFF2C7BE5.toInt(); isAntiAlias = true }
    val green = SkPaint().apply { color = 0xFF38B000.toInt(); isAntiAlias = true }
    val red = SkPaint().apply { color = 0xFFE84A5F.toInt(); isAntiAlias = true }
    canvas.drawRect(SkRect.MakeXYWH(36f, 42f, width * 0.62f, height * 0.32f), blue)
    canvas.drawRect(SkRect.MakeXYWH(width * 0.38f, height * 0.35f, width * 0.46f, height * 0.42f), green)
    canvas.drawRect(SkRect.MakeXYWH(width * 0.12f, height * 0.62f, width * 0.32f, height * 0.22f), red)
    var checksum = 1469598103934665603L
    var nonTransparent = 0
    for (y in 0 until height step 7) {
        for (x in 0 until width step 7) {
            val argb = bitmap.getPixel(x, y)
            if ((argb ushr 24) != 0) nonTransparent++
            checksum = (checksum xor argb.toLong()) * 1099511628211L
        }
    }
    return checksum to nonTransparent
}

private fun firstSceneWgsl(phase: Double): String = """
struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> VertexOutput {
    var positions = array<vec2<f32>, 6>(
        vec2<f32>(-1.0, -1.0),
        vec2<f32>( 1.0, -1.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>(-1.0,  1.0),
        vec2<f32>( 1.0, -1.0),
        vec2<f32>( 1.0,  1.0),
    );
    let p = positions[vertexIndex];
    var out: VertexOutput;
    out.position = vec4<f32>(p, 0.0, 1.0);
    out.uv = p * 0.5 + vec2<f32>(0.5, 0.5);
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    let phase: f32 = ${"%.6f".format(java.util.Locale.US, phase)}f;
    let gradient = mix(vec3<f32>(0.10, 0.22, 0.42), vec3<f32>(0.07, 0.58, 0.42), in.uv.x);
    let center = vec2<f32>(0.30 + 0.36 * phase, 0.48);
    let circle = select(0.0, 1.0, distance(in.uv, center) < 0.18);
    let rect = select(0.0, 1.0, in.uv.x > 0.58 && in.uv.x < 0.88 && in.uv.y > 0.18 && in.uv.y < 0.72);
    let color = gradient + circle * vec3<f32>(0.90, 0.20, 0.32) + rect * vec3<f32>(0.10, 0.38, 0.95);
    return vec4<f32>(min(color, vec3<f32>(1.0)), 1.0);
}
""".trimIndent()

private fun String.json(): String =
    buildString {
        append('"')
        for (ch in this@json) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

private fun Double.formatJsonNumber(): String = "%.4f".format(java.util.Locale.US, this)

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val os = System.getProperty("os.name", "").lowercase()
    if (!os.contains("mac")) {
        writeResult(
            config.output,
            NativeSmokeResult(
                status = "blocked",
                reason = "m69.kadre-native-appkit-required",
                nativePresented = false,
                requestedFrames = config.frames,
                presentedFrames = 0,
                redrawEvents = 0,
                width = DEFAULT_WIDTH,
                height = DEFAULT_HEIGHT,
                surfaceFormat = null,
                adapterInfo = null,
                cpuReferenceChecksum = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).first,
                cpuReferenceNonTransparentPixels = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).second,
                firstFrameMs = null,
                averageFrameMs = null,
                surfaceStatuses = emptyList(),
                error = "M69 native smoke currently supports Kadre AppKit + Metal only.",
            )
        )
        return
    }

    var result: NativeSmokeResult? = null
    runCatching {
        EventLoop().runApp(
            M69KadreNativeSmokeApp(config) { completed ->
                result = completed
                writeResult(config.output, completed)
            }
        )
    }.onFailure { error ->
        writeResult(
            config.output,
            NativeSmokeResult(
                status = "blocked",
                reason = "m69.kadre-native-run-failed",
                nativePresented = false,
                requestedFrames = config.frames,
                presentedFrames = 0,
                redrawEvents = 0,
                width = DEFAULT_WIDTH,
                height = DEFAULT_HEIGHT,
                surfaceFormat = null,
                adapterInfo = null,
                cpuReferenceChecksum = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).first,
                cpuReferenceNonTransparentPixels = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).second,
                firstFrameMs = null,
                averageFrameMs = null,
                surfaceStatuses = emptyList(),
                error = error.message ?: error.toString(),
            )
        )
    }
    if (result == null && !Files.exists(config.output)) {
        writeResult(
            config.output,
            NativeSmokeResult(
                status = "blocked",
                reason = "m69.kadre-native-no-result",
                nativePresented = false,
                requestedFrames = config.frames,
                presentedFrames = 0,
                redrawEvents = 0,
                width = DEFAULT_WIDTH,
                height = DEFAULT_HEIGHT,
                surfaceFormat = null,
                adapterInfo = null,
                cpuReferenceChecksum = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).first,
                cpuReferenceNonTransparentPixels = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT).second,
                firstFrameMs = null,
                averageFrameMs = null,
                surfaceStatuses = emptyList(),
                error = "Kadre event loop returned without invoking completion callback.",
            )
        )
    }
}
