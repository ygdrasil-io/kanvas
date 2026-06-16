package org.graphiks.kanvas.gpu.renderer.execution

import ffi.JvmNativeAddress
import ffi.LibraryLoader
import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GLFWContext
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
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
import io.ygdrasil.webgpu.beginRenderPass
import io.ygdrasil.webgpu.glfwContextRenderer
import java.lang.foreign.MemorySegment
import kotlinx.coroutines.runBlocking

private const val COPY_BYTES_PER_ROW_ALIGNMENT: Int = 256
private const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u
private const val RGBA_BYTES_PER_PIXEL: Int = 4
private const val RECT_COLOR_UNIFORM_SIZE_BYTES: ULong = 16uL

internal fun alignCopyBytesPerRow(unpaddedBytesPerRow: Int): Int {
    require(unpaddedBytesPerRow > 0) { "unpaddedBytesPerRow must be positive" }
    val alignment = COPY_BYTES_PER_ROW_ALIGNMENT
    return ((unpaddedBytesPerRow + alignment - 1) / alignment) * alignment
}

internal fun stripRowPadding(
    bytes: ByteArray,
    width: Int,
    height: Int,
    bytesPerPixel: Int,
    paddedBytesPerRow: Int,
): ByteArray {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(bytesPerPixel > 0) { "bytesPerPixel must be positive" }
    require(paddedBytesPerRow > 0) { "paddedBytesPerRow must be positive" }

    val tightBytesPerRow = width * bytesPerPixel
    require(paddedBytesPerRow >= tightBytesPerRow) {
        "paddedBytesPerRow $paddedBytesPerRow must be >= tight row size $tightBytesPerRow"
    }
    require(bytes.size >= paddedBytesPerRow * height) {
        "Byte array is too small for $height padded rows of $paddedBytesPerRow bytes"
    }

    if (paddedBytesPerRow == tightBytesPerRow) {
        return bytes.copyOf(tightBytesPerRow * height)
    }

    val stripped = ByteArray(tightBytesPerRow * height)
    for (row in 0 until height) {
        System.arraycopy(
            bytes,
            row * paddedBytesPerRow,
            stripped,
            row * tightBytesPerRow,
            tightBytesPerRow,
        )
    }
    return stripped
}

object WgpuBackendRuntimeFactory {
    fun createOrNull(): GPUBackendSession? = try {
        LibraryLoader.load()
        val glfw = runBlocking {
            glfwContextRenderer(
                width = 1,
                height = 1,
                title = "kanvas-gpu-renderer-wgpu-runtime",
                deferredRendering = true,
            )
        }
        WgpuBackendSession(glfw)
    } catch (_: Throwable) {
        null
    }
}

private class WgpuBackendSession(
    private val glfw: GLFWContext,
) : GPUBackendSession {
    private val deviceGeneration = GPUDeviceGeneration(1L)

    override val adapterInfo: GPUBackendAdapterSummary? =
        GPUBackendAdapterSummary(adapterSummary(glfw))

    override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
        WgpuOffscreenTarget(
            deviceGeneration = deviceGeneration,
            device = glfw.wgpuContext.device,
            queue = glfw.wgpuContext.device.queue,
            request = request,
        )

    override fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface =
        WgpuWindowSurface(
            binding = binding,
            deviceGeneration = deviceGeneration,
        )

    override fun close() {
        glfw.close()
    }
}

private class WgpuOffscreenTarget(
    private val deviceGeneration: GPUDeviceGeneration,
    private val device: io.ygdrasil.webgpu.GPUDevice,
    private val queue: GPUQueue,
    private val request: GPUOffscreenTargetRequest,
) : GPUBackendOffscreenTarget {
    private val format = request.colorFormat.toWgpuTextureFormat()
    private val bytesPerPixel = format.bytesPerPixel()
    private val tightBytesPerRow = request.width * bytesPerPixel
    private val paddedBytesPerRow = alignCopyBytesPerRow(tightBytesPerRow)
    private val stagingSize = (paddedBytesPerRow.toLong() * request.height.toLong()).toULong()
    private val texture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = request.width.toUInt(), height = request.height.toUInt()),
            format = format,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            label = "GPUBackend.offscreen.color",
        ),
    )
    private val stagingBuffer = device.createBuffer(
        BufferDescriptor(
            size = stagingSize,
            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = "GPUBackend.offscreen.staging",
        ),
    )

    override val target: GPUSurfaceTarget =
        GPUSurfaceTarget(
            targetId = "wgpu-offscreen-${request.width}x${request.height}",
            descriptor = GPUSurfaceTargetDescriptor(
                width = request.width,
                height = request.height,
                colorFormat = request.colorFormat.normalizedColorFormat(),
                surfaceBacked = false,
                targetGeneration = 0L,
                usageLabels = setOf("render_attachment", "copy_src"),
                readbackAvailable = true,
            ),
            deviceGeneration = deviceGeneration,
        )

    override fun encode(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        GpuResourceScope().use { resources ->
            val view = resources.track(texture.createView()) { it.close() }
            val encoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColor.toWgpuColor(),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                val recorder = WgpuRenderRecorder(
                    device = device,
                    queue = queue,
                    targetFormat = format,
                    resourceScope = resources,
                    setPipelineAction = { pipeline -> setPipeline(pipeline) },
                    setBindGroupAction = { index, bindGroup -> setBindGroup(index, bindGroup) },
                    setScissorAction = { x, y, width, height -> setScissorRect(x, y, width, height) },
                    drawAction = { vertexCount -> draw(vertexCount) },
                )
                recorder.block()
                end()
            }
            encoder.copyTextureToBuffer(
                source = TexelCopyTextureInfo(texture = texture),
                destination = TexelCopyBufferInfo(
                    buffer = stagingBuffer,
                    offset = 0uL,
                    bytesPerRow = paddedBytesPerRow.toUInt(),
                    rowsPerImage = request.height.toUInt(),
                ),
                copySize = Extent3D(width = request.width.toUInt(), height = request.height.toUInt()),
            )
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            queue.submit(listOf(commandBuffer))
        }
    }

    override fun readRgba(): ByteArray {
        runBlocking {
            stagingBuffer.mapAsync(GPUMapMode.Read, 0uL, stagingSize).getOrThrow()
        }
        try {
            val mapped = stagingBuffer.getMappedRange(0uL, stagingSize).toByteArray()
            return stripRowPadding(
                bytes = mapped,
                width = request.width,
                height = request.height,
                bytesPerPixel = bytesPerPixel,
                paddedBytesPerRow = paddedBytesPerRow,
            )
        } finally {
            stagingBuffer.unmap()
        }
    }

    override fun close() {
        stagingBuffer.close()
        texture.close()
    }
}

private class WgpuWindowSurface(
    binding: GPUNativeSurfaceBinding,
    private val deviceGeneration: GPUDeviceGeneration,
) : GPUBackendWindowSurface {
    private val runtime = createNativeWindowRuntime(binding)
    private var width = binding.width
    private var height = binding.height
    private var targetGeneration = 0L

    override val target: GPUSurfaceTarget
        get() = GPUSurfaceTarget(
            targetId = "wgpu-window-surface",
            descriptor = GPUSurfaceTargetDescriptor(
                width = width,
                height = height,
                colorFormat = runtime.format.toBackendColorFormat(),
                surfaceBacked = true,
                targetGeneration = targetGeneration,
                usageLabels = setOf("render_attachment"),
                readbackAvailable = false,
            ),
            deviceGeneration = deviceGeneration,
        )

    override fun resize(width: Int, height: Int) {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        this.width = width
        this.height = height
        runtime.configure(width = width, height = height)
        targetGeneration += 1L
    }

    override fun encodeAndPresent(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        val surfaceTexture = runtime.surface.getCurrentTexture()
        when (surfaceTexture.status) {
            SurfaceTextureStatus.lost,
            SurfaceTextureStatus.outdated -> {
                surfaceTexture.texture.close()
                runtime.configure(width = width, height = height)
                return
            }
            SurfaceTextureStatus.outOfMemory,
            SurfaceTextureStatus.deviceLost -> {
                surfaceTexture.texture.close()
                error("Surface texture acquisition failed with terminal status ${surfaceTexture.status.name}")
            }
            SurfaceTextureStatus.success,
            SurfaceTextureStatus.timeout -> Unit
        }

        GpuResourceScope().use { resources ->
            val view = resources.track(surfaceTexture.texture.createView(null)) { it.close() }
            val encoder = resources.trackIfAutoCloseable(runtime.device.createCommandEncoder())
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColor.toWgpuColor(),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                val recorder = WgpuRenderRecorder(
                    device = runtime.device,
                    queue = runtime.device.queue,
                    targetFormat = runtime.format,
                    resourceScope = resources,
                    setPipelineAction = { pipeline -> setPipeline(pipeline) },
                    setBindGroupAction = { index, bindGroup -> setBindGroup(index, bindGroup) },
                    setScissorAction = { x, y, surfaceWidth, surfaceHeight -> setScissorRect(x, y, surfaceWidth, surfaceHeight) },
                    drawAction = { vertexCount -> draw(vertexCount) },
                )
                recorder.block()
                end()
            }
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            runtime.device.queue.submit(listOf(commandBuffer))
            runtime.surface.present()
        }
    }

    override fun close() {
        runtime.close()
    }
}

private class WgpuRenderRecorder(
    private val device: io.ygdrasil.webgpu.GPUDevice,
    private val queue: GPUQueue,
    private val targetFormat: GPUTextureFormat,
    private val resourceScope: GpuResourceScope,
    private val setPipelineAction: (io.ygdrasil.webgpu.GPURenderPipeline) -> Unit,
    private val setBindGroupAction: (UInt, io.ygdrasil.webgpu.GPUBindGroup) -> Unit,
    private val setScissorAction: (UInt, UInt, UInt, UInt) -> Unit,
    private val drawAction: (UInt) -> Unit,
) : GPUBackendRenderRecorder {
    override fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        if (draws.isEmpty()) return

        val bindGroupLayout = resourceScope.track(
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                    ),
                ),
            ),
        ) { it.close() }
        val shader = resourceScope.track(
            device.createShaderModule(ShaderModuleDescriptor(code = wgsl)),
        ) { it.close() }
        val pipelineLayout = resourceScope.track(
            device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
            ),
        ) { it.close() }
        val pipeline = resourceScope.track(
            device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = srcOverBlendState(),
                            ),
                        ),
                    ),
                ),
            ),
        ) { it.close() }

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = RECT_COLOR_UNIFORM_SIZE_BYTES,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.rect.color",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.rgbaPremul))
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }
}

private data class NativeWindowRuntime(
    val instance: WGPU,
    val surface: NativeSurface,
    val device: io.ygdrasil.webgpu.GPUDevice,
    val format: GPUTextureFormat,
    val alphaMode: CompositeAlphaMode,
) : AutoCloseable {
    fun configure(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surface.configure(
            SurfaceConfiguration(
                device = device,
                format = format,
                usage = GPUTextureUsage.RenderAttachment,
                alphaMode = alphaMode,
            ),
            width.toUInt(),
            height.toUInt(),
        )
    }

    override fun close() {
        device.close()
        surface.close()
        instance.close()
    }
}

private fun createNativeWindowRuntime(binding: GPUNativeSurfaceBinding): NativeWindowRuntime {
    require(binding.platform == GPUNativePlatform.AppKitMetalLayer) {
        "Unsupported native platform ${binding.platform}"
    }

    LibraryLoader.load()
    val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
        ?: error("WGPU Metal instance creation returned null")
    try {
        val layerAddress = binding.pointerLabels.firstNonZeroPointer("layerHandle", "nsLayer", "metalLayer")
            ?: error("GPUNativeSurfaceBinding.pointerLabels must provide a non-zero AppKit Metal layer pointer")
        val surface = instance.getSurfaceFromMetalLayer(JvmNativeAddress(MemorySegment.ofAddress(layerAddress)))
            ?: error("WGPU surface creation from Metal layer returned null")
        try {
            val adapter = instance.requestAdapter(surface)
                ?: error("WGPU adapter request failed for native surface")
            try {
                surface.computeSurfaceCapabilities(adapter)
                val device = runBlocking { adapter.requestDevice() }
                    .getOrElse { error -> error(error.message ?: error.toString()) }
                try {
                    val format = surface.supportedFormats.firstOrNull { it == GPUTextureFormat.BGRA8Unorm }
                        ?: surface.supportedFormats.firstOrNull()
                        ?: GPUTextureFormat.BGRA8Unorm
                    val alphaMode = surface.supportedAlphaMode.firstOrNull { it == CompositeAlphaMode.Opaque }
                        ?: CompositeAlphaMode.Auto
                    return NativeWindowRuntime(
                        instance = instance,
                        surface = surface,
                        device = device,
                        format = format,
                        alphaMode = alphaMode,
                    ).also { runtime ->
                        runtime.configure(width = binding.width, height = binding.height)
                    }
                } catch (failure: Throwable) {
                    device.close()
                    throw failure
                }
            } finally {
                adapter.close()
            }
        } catch (failure: Throwable) {
            surface.close()
            throw failure
        }
    } catch (failure: Throwable) {
        instance.close()
        throw failure
    }
}

private fun adapterSummary(glfw: GLFWContext): String {
    val info = glfw.wgpuContext.adapter.info
    val parts = buildList {
        if (info.vendor.isNotBlank()) add(info.vendor)
        if (info.device.isNotBlank()) add(info.device)
    }
    val head = if (parts.isEmpty()) "unknown-adapter" else parts.joinToString("/")
    val detail = buildList {
        if (info.architecture.isNotBlank()) add("arch=${info.architecture}")
        if (info.description.isNotBlank()) add("desc=${info.description}")
    }
    return if (detail.isEmpty()) head else "$head ${detail.joinToString(" ")}"
}

private fun Map<String, Long>.firstNonZeroPointer(vararg keys: String): Long? =
    keys.asSequence()
        .mapNotNull { key -> this[key] }
        .firstOrNull { pointer -> pointer != 0L }

private fun GPUClearColor.toWgpuColor(): Color =
    Color(r = red, g = green, b = blue, a = alpha)

private fun srcOverBlendState(): BlendState =
    BlendState(
        color = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
        ),
        alpha = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
        ),
    )

private fun GPUTextureFormat.bytesPerPixel(): Int =
    when (this) {
        GPUTextureFormat.RGBA8Unorm,
        GPUTextureFormat.RGBA8UnormSrgb,
        GPUTextureFormat.BGRA8Unorm,
        GPUTextureFormat.BGRA8UnormSrgb -> RGBA_BYTES_PER_PIXEL
        else -> error("Unsupported bytes-per-pixel mapping for texture format $this")
    }

private fun String.toWgpuTextureFormat(): GPUTextureFormat =
    when (normalizedColorFormat()) {
        "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
        "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
        "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
        "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
        else -> error("Unsupported GPU color format $this")
    }

private fun GPUTextureFormat.toBackendColorFormat(): String =
    when (this) {
        GPUTextureFormat.RGBA8Unorm -> "rgba8unorm"
        GPUTextureFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
        GPUTextureFormat.BGRA8Unorm -> "bgra8unorm"
        GPUTextureFormat.BGRA8UnormSrgb -> "bgra8unorm-srgb"
        else -> name.lowercase()
    }

private fun String.normalizedColorFormat(): String = lowercase()

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
