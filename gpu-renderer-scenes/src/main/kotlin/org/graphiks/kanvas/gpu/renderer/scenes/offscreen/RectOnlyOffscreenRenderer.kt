package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

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
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.skia.gpu.webgpu.HeadlessTarget
import org.skia.gpu.webgpu.WebGpuContext

private const val BYTES_PER_PIXEL: Int = 4

class RectOnlyOffscreenRenderer {
    fun render(scene: GPURendererScene<SceneCommand>, outputDir: Path): OffscreenRunReport {
        val sceneId = scene.sceneId.value
        outputDir.createDirectories()
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = sceneId,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )

        val context = WebGpuContext.createOrNull()
            ?: return OffscreenRunReport.failed(sceneId, "webgpu-context-unavailable")

        context.use { ctx ->
            HeadlessTarget(
                context = ctx,
                width = scene.dimensions.width,
                height = scene.dimensions.height,
                format = GPUTextureFormat.RGBA8Unorm,
            ).use { target ->
                val pixels = renderToPixels(ctx, target, drawPlan)
                val nonTransparentPixels = pixels.countNonTransparentPixels()
                val imagePath = outputDir.resolve(RENDER_FILE_NAME)
                writePng(pixels, target.width, target.height, imagePath)
                return OffscreenRunReport.rendered(
                    sceneId = sceneId,
                    imagePath = RENDER_FILE_NAME,
                    width = target.width,
                    height = target.height,
                    byteCount = rectOnlyRawRgbaByteCount(pixels, target.width, target.height),
                    nonTransparentPixels = nonTransparentPixels,
                    diagnostics = rectOnlyRenderedDiagnostics(
                        sceneId = sceneId,
                        adapterInfo = ctx.adapterInfo,
                        fillRectCount = drawPlan.fills.size,
                    ),
                )
            }
        }
    }

    private fun renderToPixels(
        context: WebGpuContext,
        target: HeadlessTarget,
        drawPlan: RectOnlyDrawPlan,
    ): ByteArray {
        GpuResourceScope().use { gpuResources ->
            val bindGroupLayout = gpuResources.track(
                context.device.createBindGroupLayout(
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
            val shader = gpuResources.track(
                context.device.createShaderModule(ShaderModuleDescriptor(code = SOLID_RECT_WGSL)),
            ) { it.close() }
            val pipelineLayout = gpuResources.track(
                context.device.createPipelineLayout(
                    PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
                ),
            ) { it.close() }
            val pipeline = gpuResources.track(
                context.device.createRenderPipeline(
                    RenderPipelineDescriptor(
                        layout = pipelineLayout,
                        vertex = VertexState(module = shader, entryPoint = "vs_main"),
                        fragment = FragmentState(
                            module = shader,
                            entryPoint = "fs_main",
                            targets = listOf(
                                ColorTargetState(
                                    format = target.format,
                                    blend = srcOverBlendState(),
                                ),
                            ),
                        ),
                    ),
                ),
            ) { it.close() }
            val view = gpuResources.track(target.colorTexture.createView()) { it.close() }
            val drawResources = drawPlan.fills.map { fill ->
                createDrawResource(context, bindGroupLayout, gpuResources, fill)
            }
            val encoder = gpuResources.trackIfAutoCloseable(context.device.createCommandEncoder())
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = drawPlan.clearColor.toWebGpuColor(),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                setPipeline(pipeline)
                drawResources.forEach { resource ->
                    setBindGroup(0u, resource.bindGroup)
                    setScissorRect(
                        x = resource.scissorX.toUInt(),
                        y = resource.scissorY.toUInt(),
                        width = resource.scissorWidth.toUInt(),
                        height = resource.scissorHeight.toUInt(),
                    )
                    draw(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
                }
                end()
            }
            target.encodeCopyToStaging(encoder)
            val commandBuffer = gpuResources.trackIfAutoCloseable(encoder.finish())
            context.queue.submit(listOf(commandBuffer))
            return runBlocking { target.readPixels() }
        }
    }

    private fun createDrawResource(
        context: WebGpuContext,
        layout: GPUBindGroupLayout,
        gpuResources: GpuResourceScope,
        fill: RectOnlyFillDraw,
    ): DrawResource {
        val uniform = gpuResources.track(
            context.device.createBuffer(
                BufferDescriptor(
                    size = COLOR_UNIFORM_SIZE_BYTES,
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "RectOnlyOffscreenRenderer.color",
                ),
            ),
        ) { it.close() }
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(fill.color.toFloatArray()))
        val bindGroup = gpuResources.track(
            context.device.createBindGroup(
                BindGroupDescriptor(
                    layout = layout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    ),
                ),
            ),
        ) { it.close() }
        return DrawResource(
            bindGroup = bindGroup,
            scissorX = fill.scissorX,
            scissorY = fill.scissorY,
            scissorWidth = fill.scissorWidth,
            scissorHeight = fill.scissorHeight,
        )
    }

    private fun writePng(pixels: ByteArray, width: Int, height: Int, path: Path) {
        require(pixels.size == width * height * BYTES_PER_PIXEL) {
            "RGBA buffer size mismatch: expected ${width * height * BYTES_PER_PIXEL}, got ${pixels.size}"
        }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (pixelIndex in 0 until width * height) {
            val base = pixelIndex * BYTES_PER_PIXEL
            val r = pixels[base].toInt() and 0xFF
            val g = pixels[base + 1].toInt() and 0xFF
            val b = pixels[base + 2].toInt() and 0xFF
            val a = pixels[base + 3].toInt() and 0xFF
            image.setRGB(pixelIndex % width, pixelIndex / width, (a shl 24) or (r shl 16) or (g shl 8) or b)
        }
        require(ImageIO.write(image, "png", path.toFile())) {
            "No PNG writer available for $RENDER_FILE_NAME"
        }
    }

    private data class DrawResource(
        val bindGroup: GPUBindGroup,
        val scissorX: Int,
        val scissorY: Int,
        val scissorWidth: Int,
        val scissorHeight: Int,
    )

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

    private companion object {
        const val RENDER_FILE_NAME: String = "render.png"
        const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u
        const val COLOR_UNIFORM_SIZE_BYTES: ULong = 16uL

        val SOLID_RECT_WGSL: String = """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return vec4f(uniforms.color.rgb * uniforms.color.a, uniforms.color.a);
            }
        """.trimIndent()

        fun srcOverBlendState(): BlendState {
            val component = BlendComponent(
                operation = GPUBlendOperation.Add,
                srcFactor = GPUBlendFactor.One,
                dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
            )
            return BlendState(color = component, alpha = component)
        }

        fun SceneColor.toFloatArray(): FloatArray = floatArrayOf(r, g, b, a)

        fun SceneColor.toWebGpuColor(): Color =
            Color(r.toDouble() * a.toDouble(), g.toDouble() * a.toDouble(), b.toDouble() * a.toDouble(), a.toDouble())

        fun ByteArray.countNonTransparentPixels(): Int {
            require(size % BYTES_PER_PIXEL == 0) { "RGBA buffer size must be a multiple of $BYTES_PER_PIXEL" }
            var count = 0
            for (base in 3 until size step BYTES_PER_PIXEL) {
                if ((this[base].toInt() and 0xFF) > 0) count += 1
            }
            return count
        }
    }
}

internal data class RectOnlyDrawPlan(
    val sceneId: String,
    val clearColor: SceneColor,
    val fills: List<RectOnlyFillDraw>,
)

internal data class RectOnlyFillDraw(
    val label: String,
    val color: SceneColor,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
)

internal fun rectOnlyRawRgbaByteCount(pixels: ByteArray, width: Int, height: Int): Long {
    require(width > 0) { "rect-only raw byte count width must be positive" }
    require(height > 0) { "rect-only raw byte count height must be positive" }
    val expectedByteCount = width.toLong() * height.toLong() * BYTES_PER_PIXEL.toLong()
    require(pixels.size.toLong() == expectedByteCount) {
        "RGBA buffer size mismatch: expected $expectedByteCount, got ${pixels.size}"
    }
    return pixels.size.toLong()
}

internal fun prepareRectOnlyDrawPlan(
    sceneId: String,
    commands: List<SceneCommand>,
    width: Int,
    height: Int,
): RectOnlyDrawPlan {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(width > 0) { "$sceneId rect-only target width must be positive" }
    require(height > 0) { "$sceneId rect-only target height must be positive" }
    val unsupportedReason = rectOnlyCommandSequenceUnsupportedReason(commands)
    require(unsupportedReason == null) { "$sceneId $unsupportedReason" }

    val fills = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.FillRect }
        .sortedWith(
            compareBy<IndexedValue<SceneCommand>> { (_, command) ->
                (command as SceneCommand.FillRect).paintOrder
            }.thenBy { it.index },
        )
        .map { (_, command) ->
            val fill = command as SceneCommand.FillRect
            val left = floor(fill.rect.left).toInt()
            val top = floor(fill.rect.top).toInt()
            val right = ceil(fill.rect.right).toInt()
            val bottom = ceil(fill.rect.bottom).toInt()
            require(
                left >= 0 &&
                    top >= 0 &&
                    right <= width &&
                    bottom <= height &&
                    right > left &&
                    bottom > top,
            ) {
                "$sceneId rect-only fill rect must be inside positive bounds: ${fill.label}"
            }
            RectOnlyFillDraw(
                label = fill.label,
                color = fill.color,
                scissorX = left,
                scissorY = top,
                scissorWidth = right - left,
                scissorHeight = bottom - top,
            )
        }
    require(fills.isNotEmpty()) {
        "$sceneId rect-only offscreen render requires at least one FillRect command"
    }

    return RectOnlyDrawPlan(
        sceneId = sceneId,
        clearColor = commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f),
        fills = fills,
    )
}

internal fun rectOnlyCommandSequenceUnsupportedReason(commands: List<SceneCommand>): String? {
    val unsupportedFamilies = commands
        .mapNotNull { command ->
            if (command is SceneCommand.Clear || command is SceneCommand.FillRect) null else command.family
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only offscreen render supports only clear and fill-rect command families: " +
            unsupportedFamilies.joinToString()
    }

    if (commands.none { it is SceneCommand.FillRect }) {
        return "rect-only offscreen render requires at least one FillRect command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only offscreen render supports zero or one initial Clear before FillRect commands"
    }

    return null
}

internal fun rectOnlyRenderedDiagnostics(
    sceneId: String,
    adapterInfo: String?,
    fillRectCount: Int,
): List<String> {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(fillRectCount > 0) { "$sceneId rect-only diagnostics require at least one FillRect command" }
    return listOf(
        "rendered $sceneId via WebGPU offscreen",
        "adapter=${adapterInfo ?: "unknown-adapter"}",
        "fillRectCommands=$fillRectCount",
    )
}
