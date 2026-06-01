package org.skia.kadre.runtime

import ffi.JvmNativeAddress
import ffi.LibraryLoader
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
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
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.webgpu.WGPULowLevelApi
import java.awt.image.BufferedImage
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import org.graphiks.kadre.appkit.bindings.ObjCRuntime
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
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint

private const val DEFAULT_WIDTH = 640
private const val DEFAULT_HEIGHT = 420
private const val M69_SCENE_CONTRACT_ID = "m69-first-kanvas-kadre-host-adapter-scene"
private const val M70_SCENE_CONTRACT_ID = "m70-a-kanvas-owned-kadre-native-scene"
private const val SCENE_CONTRACT_VERSION = 1
private const val SCENE_CONTRACT_OWNER = "kanvas"
private const val REPORTING_ONLY_GATE_PHASE = "reportingOnly"
private const val CAPTURE_BYTES_PER_ROW_ALIGNMENT = 256

internal data class NativeSmokeConfig(
    val output: Path,
    val frames: Int = 3,
    val mode: String = "smoke",
    val warmupFrames: Int = 0,
    val sceneContractId: String = M69_SCENE_CONTRACT_ID,
    val sceneContractVersion: Int = SCENE_CONTRACT_VERSION,
    val captureOutput: Path? = null,
)

internal data class NativeCaptureResult(
    val status: String,
    val reason: String,
    val imagePath: String?,
    val realNativeReadback: Boolean,
    val source: String,
    val windowSurfaceReadback: Boolean,
    val width: Int,
    val height: Int,
    val format: String?,
    val bytes: Long?,
    val checksum: Long?,
    val nonTransparentPixels: Int?,
    val error: String? = null,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"status\": ${status.json()},")
        appendLine("$indent  \"reason\": ${reason.json()},")
        appendLine("$indent  \"imagePath\": ${imagePath?.json() ?: "null"},")
        appendLine("$indent  \"realNativeReadback\": $realNativeReadback,")
        appendLine("$indent  \"source\": ${source.json()},")
        appendLine("$indent  \"windowSurfaceReadback\": $windowSurfaceReadback,")
        appendLine("$indent  \"width\": $width,")
        appendLine("$indent  \"height\": $height,")
        appendLine("$indent  \"format\": ${format?.json() ?: "null"},")
        appendLine("$indent  \"bytes\": ${bytes ?: "null"},")
        appendLine("$indent  \"checksum\": ${checksum ?: "null"},")
        appendLine("$indent  \"nonTransparentPixels\": ${nonTransparentPixels ?: "null"},")
        appendLine("$indent  \"error\": ${error?.json() ?: "null"}")
        append("$indent}")
    }
}

internal data class NativeSmokeResult(
    val mode: String,
    val sceneContractId: String,
    val sceneContractVersion: Int,
    val status: String,
    val reason: String,
    val nativePresented: Boolean,
    val presentCallCompleted: Boolean,
    val requestedFrames: Int,
    val warmupFrames: Int,
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
    val telemetry: RuntimeTelemetry,
    val surfaceStatuses: List<String>,
    val surfaceApiStatuses: List<String> = emptyList(),
    val capture: NativeCaptureResult = unavailableCapture(DEFAULT_WIDTH, DEFAULT_HEIGHT),
    val error: String? = null,
) {
    fun toJson(): String {
        val telemetryJson = telemetry.toJson("  ")
        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 2,")
            appendLine("  \"generatedBy\": \"kadre-runtime:M69KadreNativeSmoke\",")
            appendLine("  \"route\": \"kadre.native.windowed.wgpu4\",")
            appendLine("  \"mode\": ${mode.json()},")
            appendLine("  \"gatePhase\": ${REPORTING_ONLY_GATE_PHASE.json()},")
            appendLine("  \"reportingOnly\": true,")
            appendLine("  \"sceneContract\": {")
            appendLine("    \"id\": ${sceneContractId.json()},")
            appendLine("    \"version\": $sceneContractVersion,")
            appendLine("    \"owner\": ${SCENE_CONTRACT_OWNER.json()},")
            appendLine("    \"milestone\": ${configMilestone(mode).json()},")
            appendLine("    \"claim\": ${configSceneClaim(mode).json()},")
            appendLine("    \"wgslSource\": \"runtime-generated-from-kanvas-owned-scene-contract\"")
            appendLine("  },")
            appendLine("  \"status\": ${status.json()},")
            appendLine("  \"reason\": ${reason.json()},")
            appendLine("  \"nativePresented\": $nativePresented,")
            appendLine("  \"presentCallCompleted\": $presentCallCompleted,")
            appendLine("  \"requestedFrames\": $requestedFrames,")
            appendLine("  \"warmupFrames\": $warmupFrames,")
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
            appendLine("    \"warmupFrameCount\": ${telemetry.warmupFrameCount},")
            appendLine("    \"measuredSampleCount\": ${telemetry.measuredSampleCount},")
            appendLine("    \"measuredP50Ms\": ${telemetry.measuredP50Ms?.formatJsonNumber() ?: "null"},")
            appendLine("    \"measuredP95Ms\": ${telemetry.measuredP95Ms?.formatJsonNumber() ?: "null"},")
            appendLine("    \"measuredWorstMs\": ${telemetry.measuredWorstMs?.formatJsonNumber() ?: "null"},")
            appendLine("    \"gatePhase\": ${telemetry.gatePhase.json()},")
            appendLine("    \"reportingOnly\": ${telemetry.reportingOnly},")
            appendLine("    \"nativeTimingClaim\": \"present-call-duration-only\"")
            appendLine("  },")
            appendLine("  \"runtimeTelemetry\": $telemetryJson,")
            appendLine("  \"capture\": ${capture.toJson("  ")},")
            appendLine("  \"surfaceStatusSummary\": ${surfaceStatusSummary(surfaceStatuses).toJson("  ")},")
            appendLine("  \"surfaceStatuses\": [${surfaceStatuses.joinToString(", ") { it.json() }}],")
            if (surfaceApiStatuses.isNotEmpty()) {
                appendLine("  \"surfaceApiStatuses\": [${surfaceApiStatuses.joinToString(", ") { it.json() }}],")
                appendLine("  \"surfaceStatusSemantics\": {")
                appendLine("    \"source\": \"wgpu4k SurfaceTextureStatus over wgpu-native v27\",")
                appendLine("    \"normalizedTimeoutAsSuccess\": true,")
                appendLine("    \"reason\": \"wgpu-native v27 reports SuccessOptimal as raw status 1; wgpu4k maps raw 1 to SurfaceTextureStatus.timeout\"")
                appendLine("  },")
            }
            appendLine("  \"linearIssues\": [${configLinearIssues(mode).joinToString(", ") { it.json() }}],")
            appendLine("  \"nonClaims\": [")
            appendLine(configNonClaims(mode).joinToString(",\n") { "    ${it.json()}" })
            appendLine("  ],")
            appendLine("  \"error\": ${error?.json() ?: "null"}")
            appendLine("}")
        }
    }
}

private data class SurfaceStatusSummary(
    val success: Int,
    val timeout: Int,
    val lost: Int,
    val outdated: Int,
    val outOfMemory: Int,
    val deviceLost: Int,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"success\": $success,")
        appendLine("$indent  \"timeout\": $timeout,")
        appendLine("$indent  \"lost\": $lost,")
        appendLine("$indent  \"outdated\": $outdated,")
        appendLine("$indent  \"outOfMemory\": $outOfMemory,")
        appendLine("$indent  \"deviceLost\": $deviceLost")
        append("$indent}")
    }
}

internal data class RuntimeTelemetry(
    val gatePhase: String = REPORTING_ONLY_GATE_PHASE,
    val reportingOnly: Boolean = true,
    val lane: String = "frame.kadre-windowed",
    val frameClockSource: String,
    val autonomousFrameClock: Boolean,
    val autonomousFrameCount: Int,
    val warmupFrameCount: Int,
    val measuredSampleCount: Int,
    val measuredP50Ms: Double?,
    val measuredP95Ms: Double?,
    val measuredWorstMs: Double?,
    val totalSampleCount: Int,
    val surfaceStatusCount: Int,
) {
    fun toJson(indent: String): String = buildString {
        appendLine("{")
        appendLine("$indent  \"lane\": ${lane.json()},")
        appendLine("$indent  \"gatePhase\": ${gatePhase.json()},")
        appendLine("$indent  \"reportingOnly\": $reportingOnly,")
        appendLine("$indent  \"frameClockSource\": ${frameClockSource.json()},")
        appendLine("$indent  \"autonomousFrameClock\": $autonomousFrameClock,")
        appendLine("$indent  \"autonomousFrameCount\": $autonomousFrameCount,")
        appendLine("$indent  \"warmupFrameCount\": $warmupFrameCount,")
        appendLine("$indent  \"measuredSampleCount\": $measuredSampleCount,")
        appendLine("$indent  \"measuredP50Ms\": ${measuredP50Ms?.formatJsonNumber() ?: "null"},")
        appendLine("$indent  \"measuredP95Ms\": ${measuredP95Ms?.formatJsonNumber() ?: "null"},")
        appendLine("$indent  \"measuredWorstMs\": ${measuredWorstMs?.formatJsonNumber() ?: "null"},")
        appendLine("$indent  \"totalSampleCount\": $totalSampleCount,")
        appendLine("$indent  \"surfaceStatusCount\": $surfaceStatusCount")
        append("$indent}")
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
    private var autonomousFrameRequests = 0
    private var completed = false
    private val frameDurationsMs = mutableListOf<Double>()
    private val surfaceStatuses = mutableListOf<String>()
    private val surfaceApiStatuses = mutableListOf<String>()
    private val cpuReference = renderCpuReference(DEFAULT_WIDTH, DEFAULT_HEIGHT)

    override fun canCreateSurfaces(eventLoop: ActiveEventLoop) {
        runCatching {
            val win = eventLoop.createWindow(
                WindowAttributes(
                    title = config.windowTitle,
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
            requestNextFrame(eventLoop)
        }.onFailure { error ->
            completeBlocked(eventLoop, "m69.kadre-native-initialization-failed", error.message ?: error.toString())
        }
    }

    override fun aboutToWait(eventLoop: ActiveEventLoop) {
        if (!completed) requestNextFrame(eventLoop)
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
        surfaceApiStatuses += surfaceTexture.status.name
        surfaceStatuses += surfaceTexture.status.toEvidenceStatus()
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
        val pipeline = createScenePipeline(gpuDevice, presentedFrames, surfaceFormat)
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
            requestNextFrame(eventLoop)
        }
    }

    private fun requestNextFrame(eventLoop: ActiveEventLoop) {
        if (config.autonomousFrameClock) {
            eventLoop.setControlFlow(ControlFlow.Poll)
            autonomousFrameRequests++
        }
        window?.requestRedraw()
    }

    private fun createScenePipeline(
        gpuDevice: GPUDevice,
        frameIndex: Int,
        targetFormat: GPUTextureFormat,
    ): GPURenderPipeline {
        val phase = (frameIndex % 60) / 60.0
        val shaderModule = gpuDevice.createShaderModule(ShaderModuleDescriptor(code = firstSceneWgsl(phase)))
        val pipeline = gpuDevice.createRenderPipeline(
            RenderPipelineDescriptor(
                vertex = VertexState(module = shaderModule, entryPoint = "vs_main"),
                primitive = PrimitiveState(),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = "fs_main",
                    targets = listOf(ColorTargetState(format = targetFormat)),
                ),
            )
        )
        shaderModule.close()
        return pipeline
    }

    private fun captureNativeScene(
        gpuDevice: GPUDevice?,
        width: Int,
        height: Int,
        frameIndex: Int,
        output: Path?,
    ): NativeCaptureResult {
        if (gpuDevice == null) {
            return unavailableCapture(width, height, reason = "m70.native-readback-device-unavailable")
        }
        if (output == null) {
            return unavailableCapture(width, height, reason = "m70.native-readback-output-unavailable")
        }
        if (width <= 0 || height <= 0) {
            return unavailableCapture(width, height, reason = "m70.native-readback-invalid-size")
        }

        return runCatching {
            val captureFormat = GPUTextureFormat.RGBA8Unorm
            val unpaddedBytesPerRow = width * 4
            val paddedBytesPerRow =
                ((unpaddedBytesPerRow + CAPTURE_BYTES_PER_ROW_ALIGNMENT - 1) /
                    CAPTURE_BYTES_PER_ROW_ALIGNMENT) * CAPTURE_BYTES_PER_ROW_ALIGNMENT
            val stagingSize = (paddedBytesPerRow.toLong() * height.toLong()).toULong()
            val texture = gpuDevice.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = width.toUInt(), height = height.toUInt()),
                    format = captureFormat,
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
                    label = "M70KadreNativeCapture.color",
                )
            )
            val readback = gpuDevice.createBuffer(
                BufferDescriptor(
                    size = stagingSize,
                    usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
                    mappedAtCreation = false,
                    label = "M70KadreNativeCapture.readback",
                )
            )
            val view = texture.createView(null)
            val pipeline = createScenePipeline(gpuDevice, frameIndex, captureFormat)
            val encoder = gpuDevice.createCommandEncoder()
            try {
                val renderPass = encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(
                            RenderPassColorAttachment(
                                view = view,
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
                encoder.copyTextureToBuffer(
                    source = TexelCopyTextureInfo(texture = texture),
                    destination = TexelCopyBufferInfo(
                        buffer = readback,
                        offset = 0uL,
                        bytesPerRow = paddedBytesPerRow.toUInt(),
                        rowsPerImage = height.toUInt(),
                    ),
                    copySize = Extent3D(width = width.toUInt(), height = height.toUInt()),
                )
                gpuDevice.queue.submit(listOf(encoder.finish()))
                runBlocking { readback.mapAsync(GPUMapMode.Read, 0uL, stagingSize) }.getOrThrow()
                val mapped = readback.getMappedRange(0uL, stagingSize).toByteArray()
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                var checksum = 1469598103934665603L
                var nonTransparent = 0
                for (y in 0 until height) {
                    val rowStart = y * paddedBytesPerRow
                    for (x in 0 until width) {
                        val i = rowStart + x * 4
                        val r = mapped[i].toInt() and 0xFF
                        val g = mapped[i + 1].toInt() and 0xFF
                        val b = mapped[i + 2].toInt() and 0xFF
                        val a = mapped[i + 3].toInt() and 0xFF
                        if (a != 0) nonTransparent++
                        val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                        checksum = (checksum xor argb.toLong()) * 1099511628211L
                        image.setRGB(x, y, argb)
                    }
                }
                readback.unmap()
                output.parent?.createDirectories()
                check(ImageIO.write(image, "png", output.toFile())) {
                    "No ImageIO PNG writer accepted the native readback image"
                }
                NativeCaptureResult(
                    status = "produced",
                    reason = "m70.native-offscreen-texture-readback",
                    imagePath = reportArtifactPath(output),
                    realNativeReadback = true,
                    windowSurfaceReadback = false,
                    source = "wgpu4k-native-offscreen-texture-rendered-from-kadre-scene-contract",
                    width = width,
                    height = height,
                    format = captureFormat.name,
                    bytes = Files.size(output),
                    checksum = checksum,
                    nonTransparentPixels = nonTransparent,
                )
            } finally {
                runCatching { readback.close() }
                runCatching { view.close() }
                runCatching { encoder.close() }
                runCatching { pipeline.close() }
                runCatching { texture.close() }
            }
        }.getOrElse { error ->
            unavailableCapture(
                width = width,
                height = height,
                reason = "m70.native-readback-failed",
                error = error.message ?: error.toString(),
            )
        }
    }

    private fun completeNative(eventLoop: ActiveEventLoop) {
        if (completed) return
        completed = true
        val size = window?.innerSize ?: PhysicalSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        val acquiredSurfaceFrames = surfaceStatuses.count { it == SurfaceTextureStatus.success.name }
        val hasConfirmedPresentation = acquiredSurfaceFrames > 0
        val capture = captureNativeScene(
            gpuDevice = device,
            width = size.width,
            height = size.height,
            frameIndex = (presentedFrames - 1).coerceAtLeast(0),
            output = config.captureOutput,
        )
        onComplete(
            NativeSmokeResult(
                mode = config.mode,
                sceneContractId = config.sceneContractId,
                sceneContractVersion = config.sceneContractVersion,
                status = if (hasConfirmedPresentation) "native-runnable" else "degraded",
                reason = if (hasConfirmedPresentation) config.presentedReason else config.timeoutOnlyReason,
                nativePresented = hasConfirmedPresentation,
                presentCallCompleted = true,
                requestedFrames = config.frames,
                warmupFrames = config.warmupFrames,
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
                telemetry = buildTelemetry(config, frameDurationsMs, surfaceStatuses.size, autonomousFrameRequests),
                surfaceStatuses = surfaceStatuses.toList(),
                surfaceApiStatuses = surfaceApiStatuses.toList(),
                capture = capture,
            )
        )
        releaseResources()
        eventLoop.setControlFlow(ControlFlow.Wait)
        eventLoop.exit()
    }

    private fun completeBlocked(eventLoop: ActiveEventLoop, reason: String, error: String) {
        if (completed) return
        completed = true
        val size = window?.innerSize ?: PhysicalSize(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        onComplete(
            NativeSmokeResult(
                mode = config.mode,
                sceneContractId = config.sceneContractId,
                sceneContractVersion = config.sceneContractVersion,
                status = "blocked",
                reason = reason,
                nativePresented = false,
                presentCallCompleted = false,
                requestedFrames = config.frames,
                warmupFrames = config.warmupFrames,
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
                telemetry = buildTelemetry(config, frameDurationsMs, surfaceStatuses.size, autonomousFrameRequests),
                surfaceStatuses = surfaceStatuses.toList(),
                surfaceApiStatuses = surfaceApiStatuses.toList(),
                capture = unavailableCapture(size.width, size.height),
                error = error,
            )
        )
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

private fun parseArgs(args: Array<String>): NativeSmokeConfig {
    var output = Path("reports/wgsl-pipeline/m69-kadre-native/native-smoke.json")
    var captureOutput: Path? = null
    var frames = 3
    var mode = "smoke"
    var warmupFrames = 0
    var sceneContractId: String? = null
    var sceneContractVersion = SCENE_CONTRACT_VERSION
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--output" -> output = Path(args.getOrNull(++i) ?: error("--output requires a path"))
            "--frames" -> frames = args.getOrNull(++i)?.toIntOrNull() ?: error("--frames requires an integer")
            "--mode" -> mode = args.getOrNull(++i) ?: error("--mode requires smoke or demo")
            "--warmup-frames" -> warmupFrames = args.getOrNull(++i)?.toIntOrNull()
                ?: error("--warmup-frames requires an integer")
            "--scene-contract-id" -> sceneContractId = args.getOrNull(++i)
                ?: error("--scene-contract-id requires a value")
            "--scene-contract-version" -> sceneContractVersion = args.getOrNull(++i)?.toIntOrNull()
                ?: error("--scene-contract-version requires an integer")
            "--capture-output" -> captureOutput = Path(args.getOrNull(++i) ?: error("--capture-output requires a path"))
        }
        i++
    }
    val normalizedMode = when (mode.lowercase()) {
        "smoke", "demo" -> mode.lowercase()
        else -> error("--mode must be smoke or demo")
    }
    return NativeSmokeConfig(
        output = output,
        frames = frames.coerceAtLeast(1),
        mode = normalizedMode,
        warmupFrames = warmupFrames.coerceAtLeast(0).coerceAtMost(frames.coerceAtLeast(1)),
        sceneContractId = sceneContractId ?: if (normalizedMode == "demo") M70_SCENE_CONTRACT_ID else M69_SCENE_CONTRACT_ID,
        sceneContractVersion = sceneContractVersion.coerceAtLeast(1),
        captureOutput = captureOutput
            ?: if (normalizedMode == "demo") {
                output.parent?.resolve(output.fileName.toString().removeSuffix(".json") + ".png")
            } else {
                null
            },
    )
}

private fun writeResult(path: Path, result: NativeSmokeResult) {
    path.parent?.createDirectories()
    path.writeText(result.toJson())
}

private val NativeSmokeConfig.presentedReason: String
    get() = if (mode == "demo") {
        "m70.kadre-native-demo-presented-frames"
    } else {
        "m69.kadre-native-presented-frames"
    }

private val NativeSmokeConfig.timeoutOnlyReason: String
    get() = if (mode == "demo") {
        "m70.kadre-present-call-completed-timeout-only"
    } else {
        "m69.kadre-present-call-completed-timeout-only"
    }

private val NativeSmokeConfig.windowTitle: String
    get() = if (mode == "demo") {
        "Kanvas M70-A - Kadre native demo"
    } else {
        "Kanvas M69 - Kadre native smoke"
    }

private val NativeSmokeConfig.autonomousFrameClock: Boolean
    get() = mode == "demo"

private val NativeSmokeConfig.frameClockSource: String
    get() = if (autonomousFrameClock) {
        "kadre.appkit.control-flow-poll"
    } else {
        "kadre.appkit.event-driven-request-redraw"
    }

private fun configMilestone(mode: String): String = if (mode == "demo") "M70-A/M71" else "M69"

private fun configSceneClaim(mode: String): String = if (mode == "demo") {
    "Kanvas-owned runtime scene contract executed through a bounded Kadre native WebGPU present-call loop"
} else {
    "M69 bounded standalone WGSL native present smoke; broad Kanvas display-list replay is not claimed"
}

private fun configLinearIssues(mode: String): List<String> = if (mode == "demo") {
    listOf("FOR-61", "FOR-62", "FOR-64", "FOR-66", "FOR-67", "FOR-68", "FOR-69", "FOR-70", "FOR-71", "FOR-72", "FOR-73", "FOR-74", "FOR-75", "FOR-76", "FOR-77", "FOR-78")
} else {
    listOf("FOR-56", "FOR-57", "FOR-58", "FOR-59")
}

private fun configNonClaims(mode: String): List<String> = if (mode == "demo") {
    listOf(
        "This run proves bounded Kadre native window present-call execution for the M70-A Kanvas-owned scene contract.",
        "M71 proves the demo route requests frames from Kadre/AppKit ControlFlow.Poll instead of relying on pointer/input events to wake the run loop.",
        "Native presentation is claimed only when the normalized surface status summary contains at least one success.",
        "Raw Kadre/wgpu4k API status names remain recorded separately when they differ from normalized evidence semantics.",
        "The capture artifact is an offscreen wgpu4k native texture readback of the same scene contract, not a system screenshot of the presented window.",
        "It does not prove broad Kanvas display-list replay or input-driven interaction yet.",
        "Timing is reporting-only present-call duration telemetry, not a release-grade FPS gate.",
    )
} else {
    listOf(
        "This smoke proves Kadre native window presentation for a bounded WGSL scene only.",
        "Native presentation is claimed only when the normalized surface status summary contains at least one success.",
        "Raw Kadre/wgpu4k API status names remain recorded separately when they differ from normalized evidence semantics.",
        "It does not prove broad Kanvas display-list replay or input-driven interaction yet.",
        "Timing is present-call duration telemetry, not a release-grade FPS gate.",
    )
}

private fun unavailableCapture(
    width: Int,
    height: Int,
    reason: String = "m70.native-readback-not-available",
    error: String? = null,
): NativeCaptureResult = NativeCaptureResult(
    status = "unavailable",
    reason = reason,
    imagePath = null,
    realNativeReadback = false,
    windowSurfaceReadback = false,
    source = "none",
    width = width,
    height = height,
    format = null,
    bytes = null,
    checksum = null,
    nonTransparentPixels = null,
    error = error,
)

private fun reportArtifactPath(path: Path): String {
    val normalized = path.normalize().toString().replace('\\', '/')
    if (normalized.startsWith("reports/")) {
        return normalized
    }
    val marker = "/reports/"
    val markerIndex = normalized.indexOf(marker)
    return if (markerIndex >= 0) {
        "reports/${normalized.substring(markerIndex + marker.length)}"
    } else {
        path.absolutePathString()
    }
}

private fun surfaceStatusSummary(statuses: List<String>): SurfaceStatusSummary =
    SurfaceStatusSummary(
        success = statuses.count { it == SurfaceTextureStatus.success.name },
        timeout = statuses.count { it == SurfaceTextureStatus.timeout.name },
        lost = statuses.count { it == SurfaceTextureStatus.lost.name },
        outdated = statuses.count { it == SurfaceTextureStatus.outdated.name },
        outOfMemory = statuses.count { it == SurfaceTextureStatus.outOfMemory.name },
        deviceLost = statuses.count { it == SurfaceTextureStatus.deviceLost.name },
    )

private fun SurfaceTextureStatus.toEvidenceStatus(): String =
    when (this) {
        SurfaceTextureStatus.timeout -> SurfaceTextureStatus.success.name
        else -> name
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

private fun buildTelemetry(
    config: NativeSmokeConfig,
    frameDurationsMs: List<Double>,
    surfaceStatusCount: Int,
    autonomousFrameRequests: Int = 0,
): RuntimeTelemetry {
    val measured = frameDurationsMs.drop(config.warmupFrames.coerceAtMost(frameDurationsMs.size))
    return RuntimeTelemetry(
        frameClockSource = config.frameClockSource,
        autonomousFrameClock = config.autonomousFrameClock,
        autonomousFrameCount = autonomousFrameRequests,
        warmupFrameCount = frameDurationsMs.size.coerceAtMost(config.warmupFrames),
        measuredSampleCount = measured.size,
        measuredP50Ms = measured.percentile(0.50),
        measuredP95Ms = measured.percentile(0.95),
        measuredWorstMs = measured.maxOrNull(),
        totalSampleCount = frameDurationsMs.size,
        surfaceStatusCount = surfaceStatusCount,
    )
}

private fun List<Double>.percentile(percentile: Double): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val index = (kotlin.math.ceil(sorted.size * percentile).toInt() - 1).coerceIn(sorted.indices)
    return sorted[index]
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
                mode = config.mode,
                sceneContractId = config.sceneContractId,
                sceneContractVersion = config.sceneContractVersion,
                status = "blocked",
                reason = "m69.kadre-native-appkit-required",
                nativePresented = false,
                presentCallCompleted = false,
                requestedFrames = config.frames,
                warmupFrames = config.warmupFrames,
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
                telemetry = buildTelemetry(config, emptyList(), 0),
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
                mode = config.mode,
                sceneContractId = config.sceneContractId,
                sceneContractVersion = config.sceneContractVersion,
                status = "blocked",
                reason = "m69.kadre-native-run-failed",
                nativePresented = false,
                presentCallCompleted = false,
                requestedFrames = config.frames,
                warmupFrames = config.warmupFrames,
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
                telemetry = buildTelemetry(config, emptyList(), 0),
                surfaceStatuses = emptyList(),
                error = error.message ?: error.toString(),
            )
        )
    }
    if (result == null && !Files.exists(config.output)) {
        writeResult(
            config.output,
            NativeSmokeResult(
                mode = config.mode,
                sceneContractId = config.sceneContractId,
                sceneContractVersion = config.sceneContractVersion,
                status = "blocked",
                reason = "m69.kadre-native-no-result",
                nativePresented = false,
                presentCallCompleted = false,
                requestedFrames = config.frames,
                warmupFrames = config.warmupFrames,
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
                telemetry = buildTelemetry(config, emptyList(), 0),
                surfaceStatuses = emptyList(),
                error = "Kadre event loop returned without invoking completion callback.",
            )
        )
    }
}
