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
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSampling
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBitmapSource
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneFilterKind
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect
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
                        fillRectCount = drawPlan.fillRectCount,
                        fillRRectCount = drawPlan.fillRRectCount,
                        linearGradientRectCount = drawPlan.linearGradientRectCount,
                        clipCount = drawPlan.clipCount,
                        bitmapRectCount = drawPlan.bitmapRectCount,
                        filters = drawPlan.filters,
                        saveLayers = drawPlan.saveLayers,
                        runtimeEffects = drawPlan.runtimeEffects,
                        meshRibbons = drawPlan.meshRibbons,
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
                    label = "RectOnlyOffscreenRenderer.shape",
                ),
            ),
        ) { it.close() }
        context.queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(fill.toUniformFloatArray()))
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
        const val COLOR_UNIFORM_SIZE_BYTES: ULong = 96uL

        val SOLID_RECT_WGSL: String = """
            struct Uniforms {
                color0: vec4f,
                color1: vec4f,
                color2: vec4f,
                color3: vec4f,
                rect: vec4f,
                radius_and_kind: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            fn rounded_rect_coverage(pixel: vec2f, rect: vec4f, radius: f32) -> f32 {
                if (pixel.x < rect.x || pixel.x >= rect.z || pixel.y < rect.y || pixel.y >= rect.w) {
                    return 0.0;
                }
                if (radius <= 0.0) {
                    return 1.0;
                }
                let clamped_radius = min(radius, min((rect.z - rect.x) * 0.5, (rect.w - rect.y) * 0.5));
                let center = clamp(pixel, rect.xy + vec2f(clamped_radius), rect.zw - vec2f(clamped_radius));
                let edge_distance = length(pixel - center) - clamped_radius;
                return clamp(0.5 - edge_distance, 0.0, 1.0);
            }

            fn mesh_ribbon_coverage(pixel: vec2f, rect: vec4f, half_thickness: f32) -> f32 {
                if (pixel.x < rect.x || pixel.x >= rect.z || pixel.y < rect.y || pixel.y >= rect.w) {
                    return 0.0;
                }
                let start = vec2f(rect.x, rect.w);
                let end = vec2f(rect.z, rect.y);
                let line = end - start;
                let t = clamp(dot(pixel - start, line) / max(dot(line, line), 0.0001), 0.0, 1.0);
                let closest = start + line * t;
                let edge_distance = length(pixel - closest) - max(half_thickness, 0.0);
                return clamp(0.5 - edge_distance, 0.0, 1.0);
            }

            fn shape_coverage(pixel: vec2f, rect: vec4f, radius: f32, paint_kind: f32) -> f32 {
                if (paint_kind >= 4.5) {
                    return mesh_ribbon_coverage(pixel, rect, radius);
                }
                return rounded_rect_coverage(pixel, rect, radius);
            }

            @fragment
            fn fs_main(@builtin(position) position: vec4f) -> @location(0) vec4f {
                let coverage = shape_coverage(
                    position.xy,
                    uniforms.rect,
                    uniforms.radius_and_kind.x,
                    uniforms.radius_and_kind.y
                );
                var color = uniforms.color0;
                if (uniforms.radius_and_kind.y >= 4.5) {
                    let uv = clamp(
                        (position.xy - uniforms.rect.xy) / max(uniforms.rect.zw - uniforms.rect.xy, vec2f(0.0001)),
                        vec2f(0.0),
                        vec2f(1.0)
                    );
                    color = mix(uniforms.color0, uniforms.color1, uv.x);
                } else if (uniforms.radius_and_kind.y >= 3.5) {
                    color = vec4f(
                        position.x * (1.0 / 255.0),
                        position.y * (1.0 / 255.0),
                        uniforms.color0.b,
                        1.0
                    );
                } else if (uniforms.radius_and_kind.y >= 2.5) {
                    let uv = clamp(
                        (position.xy - uniforms.rect.xy) / max(uniforms.rect.zw - uniforms.rect.xy, vec2f(0.0001)),
                        vec2f(0.0),
                        vec2f(1.0)
                    );
                    let top = mix(uniforms.color0, uniforms.color1, uv.x);
                    let bottom = mix(uniforms.color2, uniforms.color3, uv.x);
                    color = mix(top, bottom, uv.y);
                } else if (uniforms.radius_and_kind.y >= 1.5) {
                    let uv = clamp(
                        (position.xy - uniforms.rect.xy) / max(uniforms.rect.zw - uniforms.rect.xy, vec2f(0.0001)),
                        vec2f(0.0),
                        vec2f(1.0)
                    );
                    if (uv.y >= 0.5) {
                        if (uv.x >= 0.5) {
                            color = uniforms.color3;
                        } else {
                            color = uniforms.color2;
                        }
                    } else if (uv.x >= 0.5) {
                        color = uniforms.color1;
                    }
                } else if (uniforms.radius_and_kind.y >= 0.5) {
                    let t = clamp(
                        (position.y - uniforms.rect.y) / max(uniforms.rect.w - uniforms.rect.y, 0.0001),
                        0.0,
                        1.0
                    );
                    color = mix(uniforms.color0, uniforms.color1, t);
                }
                if (uniforms.radius_and_kind.z >= 0.5) {
                    let luma = dot(color.rgb, vec3f(0.2126, 0.7152, 0.0722));
                    let luma_tint = clamp(vec3f(luma * 1.08, luma * 0.78, luma * 0.42), vec3f(0.0), vec3f(1.0));
                    color = vec4f(
                        mix(color.rgb, luma_tint, clamp(uniforms.radius_and_kind.w, 0.0, 1.0)),
                        color.a
                    );
                }
                let alpha = color.a * coverage;
                return vec4f(color.rgb * alpha, alpha);
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

        fun SceneColor.toWebGpuColor(): Color =
            Color(r.toDouble() * a.toDouble(), g.toDouble() * a.toDouble(), b.toDouble() * a.toDouble(), a.toDouble())

        fun RectOnlyFillDraw.toUniformFloatArray(): FloatArray =
            floatArrayOf(
                startColor.r,
                startColor.g,
                startColor.b,
                startColor.a,
                endColor.r,
                endColor.g,
                endColor.b,
                endColor.a,
                bottomLeftColor.r,
                bottomLeftColor.g,
                bottomLeftColor.b,
                bottomLeftColor.a,
                bottomRightColor.r,
                bottomRightColor.g,
                bottomRightColor.b,
                bottomRightColor.a,
                left,
                top,
                right,
                bottom,
                radius,
                paintKind,
                filterKind,
                filterStrength,
            )

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
    val clipCount: Int = 0,
    val filters: List<RectOnlyFilterNode> = emptyList(),
    val saveLayers: List<RectOnlySaveLayer> = emptyList(),
) {
    val fillRectCount: Int = fills.count { it.family == "fill-rect" }
    val fillRRectCount: Int = fills.count { it.family == "fill-rrect" }
    val linearGradientRectCount: Int = fills.count { it.family == "linear-gradient-rect" }
    val bitmapRectCount: Int = fills.count { it.family == "bitmap-rect" }
    val filterNodeCount: Int = filters.size
    val runtimeEffects: List<RectOnlyRuntimeEffectTile> = fills
        .filter { it.family == "runtime-effect" }
        .map { fill ->
            RectOnlyRuntimeEffectTile(
                label = fill.label,
                stableId = fill.runtimeEffectStableId
                    ?: error("RuntimeEffectTile draw requires stableId: ${fill.label}"),
                wgslImplementationId = fill.runtimeEffectWgslImplementationId
                    ?: error("RuntimeEffectTile draw requires wgslImplementationId: ${fill.label}"),
                uniformLayout = fill.runtimeEffectUniformLayout
                    ?: error("RuntimeEffectTile draw requires uniform layout: ${fill.label}"),
                pipelineKey = fill.runtimeEffectPipelineKey
                    ?: error("RuntimeEffectTile draw requires pipeline key: ${fill.label}"),
            )
        }
    val runtimeEffectCount: Int = runtimeEffects.size
    val meshRibbons: List<RectOnlyMeshRibbon> = fills
        .filter { it.family == "vertices" }
        .map { fill ->
            RectOnlyMeshRibbon(
                label = fill.label,
                meshKind = fill.meshRibbonKind
                    ?: error("MeshRibbon draw requires mesh kind: ${fill.label}"),
            )
        }
    val meshRibbonCount: Int = meshRibbons.size
}

internal data class RectOnlyFilterNode(
    val label: String,
    val inputLabel: String,
    val kind: SceneFilterKind,
    val strength: Float,
)

internal data class RectOnlyRuntimeEffectTile(
    val label: String,
    val stableId: String,
    val wgslImplementationId: String,
    val uniformLayout: String,
    val pipelineKey: String,
)

internal data class RectOnlySaveLayer(
    val label: String,
    val layerKind: String,
    val filterLabel: String,
    val filterKind: SceneFilterKind,
    val filterStrength: Float,
)

internal data class RectOnlyMeshRibbon(
    val label: String,
    val meshKind: String,
)

internal data class RectOnlyFillDraw(
    val label: String,
    val family: String,
    val startColor: SceneColor,
    val endColor: SceneColor,
    val bottomLeftColor: SceneColor,
    val bottomRightColor: SceneColor,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    val paintKind: Float,
    val filterKind: Float,
    val filterStrength: Float,
    val scissorX: Int,
    val scissorY: Int,
    val scissorWidth: Int,
    val scissorHeight: Int,
    val runtimeEffectStableId: String? = null,
    val runtimeEffectWgslImplementationId: String? = null,
    val runtimeEffectUniformLayout: String? = null,
    val runtimeEffectPipelineKey: String? = null,
    val meshRibbonKind: String? = null,
)

private data class RectOnlyIndexedDraw(
    val index: Int,
    val command: SceneCommand,
    val clip: SceneCommand.Clip?,
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
    val outOfBoundsMeshRibbons = commands.filterIsInstance<SceneCommand.MeshRibbon>()
        .filter { it.hasFixturePayload }
        .filter { ribbon ->
            val bounds = ribbon.bounds
            bounds == null || !bounds.isInsideTarget(width, height)
        }
        .map { it.label }
    require(outOfBoundsMeshRibbons.isEmpty()) {
        "$sceneId rect-only offscreen render requires MeshRibbon bounds inside positive target: " +
            outOfBoundsMeshRibbons.joinToString()
    }

    val filters = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filter { it.hasFixturePayload }
        .map { filter ->
            RectOnlyFilterNode(
                label = filter.label,
                inputLabel = filter.inputLabel ?: error("FilterNode requires input fixture payload: ${filter.label}"),
                kind = filter.kind ?: error("FilterNode requires kind fixture payload: ${filter.label}"),
                strength = filter.strength,
            )
        }
    val filtersByInput = filters.associateBy { it.inputLabel }
    val saveLayers = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filter { it.hasFixturePayload }
        .map { layer ->
            val filter = filtersByInput[layer.label]
                ?: error("SaveLayer requires DropShadow FilterNode fixture payload: ${layer.label}")
            RectOnlySaveLayer(
                label = layer.label,
                layerKind = layer.layerKind,
                filterLabel = filter.label,
                filterKind = filter.kind,
                filterStrength = filter.strength,
            )
        }

    var activeClip: SceneCommand.Clip? = null
    val indexedDraws = buildList {
        commands.withIndex().forEach { (index, command) ->
            when (command) {
                is SceneCommand.Clip -> activeClip = command
                is SceneCommand.FillRect,
                is SceneCommand.FillRRect,
                is SceneCommand.LinearGradientRect,
                is SceneCommand.SaveLayer,
                is SceneCommand.RuntimeEffectTile,
                is SceneCommand.MeshRibbon -> add(RectOnlyIndexedDraw(index, command, activeClip))
                is SceneCommand.BitmapRect -> if (command.hasFixturePayload) {
                    add(RectOnlyIndexedDraw(index, command, activeClip))
                }
                else -> Unit
            }
        }
    }

    val fills = indexedDraws
        .sortedWith(
            compareBy<RectOnlyIndexedDraw> { (_, command) ->
                command.paintOrder()
            }.thenBy { it.index },
        )
        .flatMap { (_, command, clip) ->
            val rect = command.shapeRect()
            val filter = if (command is SceneCommand.BitmapRect) filtersByInput[command.label] else null
            if (command is SceneCommand.SaveLayer) {
                val layer = saveLayers.singleOrNull { it.label == command.label }
                    ?: error("SaveLayer requires bounded shadow layer contract: ${command.label}")
                listOf(
                    rectOnlyFillDraw(
                        sceneId = sceneId,
                        label = "${command.label}-shadow",
                        family = command.family,
                        rect = command.fixtureShadowRect(),
                        radius = command.radius,
                        startColor = command.fixtureShadowColor().withAlpha(command.fixtureShadowColor().a * layer.filterStrength),
                        endColor = command.fixtureShadowColor().withAlpha(command.fixtureShadowColor().a * layer.filterStrength),
                        bottomLeftColor = command.fixtureShadowColor().withAlpha(command.fixtureShadowColor().a * layer.filterStrength),
                        bottomRightColor = command.fixtureShadowColor().withAlpha(command.fixtureShadowColor().a * layer.filterStrength),
                        paintKind = 0f,
                        filterKind = 0f,
                        filterStrength = 0f,
                        clip = clip,
                        width = width,
                        height = height,
                    ),
                    rectOnlyFillDraw(
                        sceneId = sceneId,
                        label = "${command.label}-content",
                        family = command.family,
                        rect = rect,
                        radius = command.radius,
                        startColor = command.fixtureContentColor(),
                        endColor = command.fixtureContentColor(),
                        bottomLeftColor = command.fixtureContentColor(),
                        bottomRightColor = command.fixtureContentColor(),
                        paintKind = 0f,
                        filterKind = 0f,
                        filterStrength = 0f,
                        clip = clip,
                        width = width,
                        height = height,
                    ),
                )
            } else {
                listOf(
                    rectOnlyFillDraw(
                        sceneId = sceneId,
                        label = command.label,
                        family = command.family,
                        rect = rect,
                        radius = command.shapeRadius(),
                        startColor = command.shapeStartColor(),
                        endColor = command.shapeEndColor(),
                        bottomLeftColor = command.shapeBottomLeftColor(),
                        bottomRightColor = command.shapeBottomRightColor(),
                        paintKind = command.shapePaintKind(),
                        filterKind = filter?.kind?.filterPaintKind() ?: 0f,
                        filterStrength = filter?.strength ?: 0f,
                        clip = clip,
                        width = width,
                        height = height,
                        runtimeEffectStableId = (command as? SceneCommand.RuntimeEffectTile)?.stableId,
                        runtimeEffectWgslImplementationId =
                            (command as? SceneCommand.RuntimeEffectTile)?.wgslImplementationId,
                        runtimeEffectUniformLayout = (command as? SceneCommand.RuntimeEffectTile)?.uniformLayout,
                        runtimeEffectPipelineKey = (command as? SceneCommand.RuntimeEffectTile)?.pipelineKey,
                        meshRibbonKind = (command as? SceneCommand.MeshRibbon)?.meshKind,
                    ),
                )
            }
    }
    require(fills.isNotEmpty()) {
        "$sceneId rect-only offscreen render requires at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command"
    }

    return RectOnlyDrawPlan(
        sceneId = sceneId,
        clearColor = commands.filterIsInstance<SceneCommand.Clear>().firstOrNull()?.color
            ?: SceneColor(0f, 0f, 0f, 0f),
        fills = fills,
        clipCount = commands.count { it is SceneCommand.Clip },
        filters = filters,
        saveLayers = saveLayers,
    )
}

private fun rectOnlyFillDraw(
    sceneId: String,
    label: String,
    family: String,
    rect: SceneRect,
    radius: Float,
    startColor: SceneColor,
    endColor: SceneColor,
    bottomLeftColor: SceneColor,
    bottomRightColor: SceneColor,
    paintKind: Float,
    filterKind: Float,
    filterStrength: Float,
    clip: SceneCommand.Clip?,
    width: Int,
    height: Int,
    runtimeEffectStableId: String? = null,
    runtimeEffectWgslImplementationId: String? = null,
    runtimeEffectUniformLayout: String? = null,
    runtimeEffectPipelineKey: String? = null,
    meshRibbonKind: String? = null,
): RectOnlyFillDraw {
    requireInsideTarget(sceneId, label, rect, width, height, "fill shape")
    clip?.let { requireInsideTarget(sceneId, it.label, it.rect, width, height, "clip") }
    val scissorRect = rect.intersect(clip?.rect)
    require(scissorRect != null) {
        "$sceneId rect-only fill shape must intersect active clip: $label"
    }
    val left = floor(scissorRect.left).toInt()
    val top = floor(scissorRect.top).toInt()
    val right = ceil(scissorRect.right).toInt()
    val bottom = ceil(scissorRect.bottom).toInt()
    val widthPx = rect.right - rect.left
    val heightPx = rect.bottom - rect.top
    return RectOnlyFillDraw(
        label = label,
        family = family,
        startColor = startColor,
        endColor = endColor,
        bottomLeftColor = bottomLeftColor,
        bottomRightColor = bottomRightColor,
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
        radius = minOf(radius, widthPx * 0.5f, heightPx * 0.5f),
        paintKind = paintKind,
        filterKind = filterKind,
        filterStrength = filterStrength,
        scissorX = left,
        scissorY = top,
        scissorWidth = right - left,
        scissorHeight = bottom - top,
        runtimeEffectStableId = runtimeEffectStableId,
        runtimeEffectWgslImplementationId = runtimeEffectWgslImplementationId,
        runtimeEffectUniformLayout = runtimeEffectUniformLayout,
        runtimeEffectPipelineKey = runtimeEffectPipelineKey,
        meshRibbonKind = meshRibbonKind,
    )
}

internal fun rectOnlyCommandSequenceUnsupportedReason(commands: List<SceneCommand>): String? {
    val unsupportedFamilies = commands
        .mapNotNull { command ->
            if (
                command is SceneCommand.Clear ||
                command is SceneCommand.FillRect ||
                command is SceneCommand.FillRRect ||
                command is SceneCommand.LinearGradientRect ||
                command is SceneCommand.Clip ||
                command is SceneCommand.BitmapRect ||
                command is SceneCommand.SaveLayer ||
                command is SceneCommand.FilterNode ||
                command is SceneCommand.RuntimeEffectTile ||
                command is SceneCommand.MeshRibbon
            ) {
                null
            } else {
                command.family
            }
        }
        .distinct()
    if (unsupportedFamilies.isNotEmpty()) {
        return "rect-only offscreen render supports only clear, fill-rect, fill-rrect, linear-gradient-rect, clip, fixture-backed bitmap-rect, fixture-backed save-layer, fixture-backed filter-node, fixture-backed runtime-effect, and fixture-backed mesh-ribbon command families: " +
            unsupportedFamilies.joinToString()
    }

    val bitmapMarkers = commands.filterIsInstance<SceneCommand.BitmapRect>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (bitmapMarkers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed BitmapRect payloads: " +
            bitmapMarkers.joinToString()
    }

    val saveLayerMarkers = commands.filterIsInstance<SceneCommand.SaveLayer>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (saveLayerMarkers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed SaveLayer payloads: " +
            saveLayerMarkers.joinToString()
    }

    val filterMarkers = commands.filterIsInstance<SceneCommand.FilterNode>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (filterMarkers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed FilterNode payloads: " +
            filterMarkers.joinToString()
    }

    val runtimeEffectMarkers = commands.filterIsInstance<SceneCommand.RuntimeEffectTile>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (runtimeEffectMarkers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed RuntimeEffectTile payloads: " +
            runtimeEffectMarkers.joinToString()
    }

    val meshRibbonMarkers = commands.filterIsInstance<SceneCommand.MeshRibbon>()
        .filterNot { it.hasFixturePayload }
        .map { it.label }
    if (meshRibbonMarkers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed MeshRibbon payloads: " +
            meshRibbonMarkers.joinToString()
    }

    val unsupportedRuntimeEffects = commands.filterIsInstance<SceneCommand.RuntimeEffectTile>()
        .filter { it.hasFixturePayload && !it.isRegisteredSimpleRt }
        .map { it.label }
    if (unsupportedRuntimeEffects.isNotEmpty()) {
        return "rect-only offscreen render supports only registered runtime.simple_rt RuntimeEffectTile payloads: " +
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
        return "rect-only offscreen render requires FilterNode inputs to reference compatible fixture-backed labels: " +
            invalidFilterInputs.joinToString()
    }

    val missingDropShadowLayers = fixtureSaveLayerLabels
        .filter { label ->
            filters.none { filter -> filter.inputLabel == label && filter.kind == SceneFilterKind.DropShadow }
        }
    if (missingDropShadowLayers.isNotEmpty()) {
        return "rect-only offscreen render requires fixture-backed SaveLayer inputs to have one DropShadow FilterNode: " +
            missingDropShadowLayers.joinToString()
    }

    val duplicateFilterInputs = filters
        .mapNotNull { it.inputLabel }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys
    if (duplicateFilterInputs.isNotEmpty()) {
        return "rect-only offscreen render supports at most one FilterNode per fixture input: " +
            duplicateFilterInputs.joinToString()
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
        return "rect-only offscreen render requires at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command"
    }

    val clearIndices = commands.withIndex()
        .filter { (_, command) -> command is SceneCommand.Clear }
        .map { it.index }
    if (clearIndices.size > 1 || clearIndices.any { it != 0 }) {
        return "rect-only offscreen render supports zero or one initial Clear before drawable commands"
    }

    return null
}

internal fun rectOnlyRenderedDiagnostics(
    sceneId: String,
    adapterInfo: String?,
    fillRectCount: Int,
    fillRRectCount: Int,
    linearGradientRectCount: Int = 0,
    clipCount: Int = 0,
    bitmapRectCount: Int = 0,
    filters: List<RectOnlyFilterNode> = emptyList(),
    saveLayers: List<RectOnlySaveLayer> = emptyList(),
    runtimeEffects: List<RectOnlyRuntimeEffectTile> = emptyList(),
    meshRibbons: List<RectOnlyMeshRibbon> = emptyList(),
): List<String> {
    require(sceneId.isNotBlank()) { "rect-only sceneId must not be blank" }
    require(
        fillRectCount +
            fillRRectCount +
            linearGradientRectCount +
            bitmapRectCount +
            saveLayers.size +
            runtimeEffects.size +
            meshRibbons.size > 0,
    ) {
        "$sceneId rect-only diagnostics require at least one FillRect, FillRRect, LinearGradientRect, BitmapRect, SaveLayer, RuntimeEffectTile, or MeshRibbon command"
    }
    return buildList {
        add("rendered $sceneId via WebGPU offscreen")
        add("adapter=${adapterInfo ?: "unknown-adapter"}")
        add("fillRectCommands=$fillRectCount")
        add("fillRRectCommands=$fillRRectCount")
        add("linearGradientRectCommands=$linearGradientRectCount")
        add("clipCommands=$clipCount")
        add("bitmapRectCommands=$bitmapRectCount")
        if (saveLayers.isNotEmpty()) {
            add("saveLayerCommands=${saveLayers.size}")
            add("saveLayerKinds=${saveLayers.joinToString { it.layerKind }}")
            add("saveLayerRoute=scene-fixture.bounded-shadow-card")
            add("saveLayerMaterializedDraws=${saveLayers.size * 2}")
            add("saveLayerFilterKinds=${saveLayers.joinToString { it.filterKind.wireName }}")
            add("saveLayerFallbackReason=none")
            add("filterRoutes=scene-fixture.bounded-drop-shadow")
            add("generalSaveLayerSupport=false")
            add("imageFilterDagSupport=false")
        }
        if (filters.isNotEmpty()) {
            add("filterNodeCommands=${filters.size}")
            add("filterKinds=${filters.joinToString { it.kind.wireName }}")
            add("filterInputs=${filters.joinToString { it.inputLabel }}")
        }
        if (runtimeEffects.isNotEmpty()) {
            add("runtimeEffectCommands=${runtimeEffects.size}")
            add("runtimeEffectStableIds=${runtimeEffects.joinToString { it.stableId }}")
            add("runtimeEffectWgslImplementationIds=${runtimeEffects.joinToString { it.wgslImplementationId }}")
            add("runtimeEffectUniformLayout=${runtimeEffects.joinToString { it.uniformLayout }}")
            add("runtimeEffectPipelineKey=${runtimeEffects.joinToString { it.pipelineKey }}")
            add("runtimeEffectDescriptorEvidence=reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json")
            add(
                "runtimeEffectParserEvidence=" +
                    "RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms",
            )
            add("fallbackReason=none")
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

private fun SceneCommand.paintOrder(): Int =
    when (this) {
        is SceneCommand.FillRect -> paintOrder
        is SceneCommand.FillRRect -> paintOrder
        is SceneCommand.LinearGradientRect -> paintOrder
        is SceneCommand.BitmapRect -> paintOrder
        is SceneCommand.SaveLayer -> paintOrder
        is SceneCommand.RuntimeEffectTile -> paintOrder
        is SceneCommand.MeshRibbon -> paintOrder
        else -> 0
    }

private fun SceneCommand.shapeRect() =
    when (this) {
        is SceneCommand.FillRect -> rect
        is SceneCommand.FillRRect -> rect
        is SceneCommand.LinearGradientRect -> rect
        is SceneCommand.BitmapRect -> fixtureRect()
        is SceneCommand.SaveLayer -> fixtureContentRect()
        is SceneCommand.RuntimeEffectTile -> fixtureRect()
        is SceneCommand.MeshRibbon -> fixtureBounds()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeStartColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().topLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeEndColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().topRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomLeftColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> startColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomLeft
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureStartColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeBottomRightColor() =
    when (this) {
        is SceneCommand.FillRect -> color
        is SceneCommand.FillRRect -> color
        is SceneCommand.LinearGradientRect -> endColor
        is SceneCommand.BitmapRect -> fixtureSource().bottomRight
        is SceneCommand.SaveLayer -> fixtureContentColor()
        is SceneCommand.RuntimeEffectTile -> fixtureUniformColor()
        is SceneCommand.MeshRibbon -> fixtureEndColor()
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapeRadius(): Float =
    when (this) {
        is SceneCommand.FillRect -> 0f
        is SceneCommand.FillRRect -> radius
        is SceneCommand.LinearGradientRect -> 0f
        is SceneCommand.BitmapRect -> 0f
        is SceneCommand.SaveLayer -> radius
        is SceneCommand.RuntimeEffectTile -> 0f
        is SceneCommand.MeshRibbon -> thickness * 0.5f
        else -> error("Unsupported shape command: $family")
    }

private fun SceneCommand.shapePaintKind(): Float =
    when (this) {
        is SceneCommand.MeshRibbon -> 5f
        is SceneCommand.RuntimeEffectTile -> 4f
        is SceneCommand.LinearGradientRect -> 1f
        is SceneCommand.BitmapRect -> when (sampling) {
            SceneBitmapSampling.Nearest -> 2f
            SceneBitmapSampling.Linear -> 3f
        }
        else -> 0f
    }

private fun SceneFilterKind.filterPaintKind(): Float =
    when (this) {
        SceneFilterKind.LumaTint -> 1f
        SceneFilterKind.DropShadow -> 0f
    }

private fun SceneCommand.BitmapRect.fixtureRect(): SceneRect =
    rect ?: error("BitmapRect requires rect fixture payload: $label")

private fun SceneCommand.BitmapRect.fixtureSource(): SceneBitmapSource =
    source ?: error("BitmapRect requires source fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentRect(): SceneRect =
    contentRect ?: error("SaveLayer requires contentRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowRect(): SceneRect =
    shadowRect ?: error("SaveLayer requires shadowRect fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureContentColor(): SceneColor =
    contentColor ?: error("SaveLayer requires contentColor fixture payload: $label")

private fun SceneCommand.SaveLayer.fixtureShadowColor(): SceneColor =
    shadowColor ?: error("SaveLayer requires shadowColor fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureRect(): SceneRect =
    rect ?: error("RuntimeEffectTile requires rect fixture payload: $label")

private fun SceneCommand.RuntimeEffectTile.fixtureUniformColor(): SceneColor =
    uniformColor ?: error("RuntimeEffectTile requires uniform color fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureBounds(): SceneRect =
    bounds ?: error("MeshRibbon requires bounds fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureStartColor(): SceneColor =
    startColor ?: error("MeshRibbon requires startColor fixture payload: $label")

private fun SceneCommand.MeshRibbon.fixtureEndColor(): SceneColor =
    endColor ?: error("MeshRibbon requires endColor fixture payload: $label")

private fun SceneColor.withAlpha(alpha: Float): SceneColor =
    SceneColor(r = r, g = g, b = b, a = alpha.coerceIn(0f, 1f))

private fun requireInsideTarget(
    sceneId: String,
    label: String,
    rect: SceneRect,
    width: Int,
    height: Int,
    kind: String,
) {
    val left = floor(rect.left).toInt()
    val top = floor(rect.top).toInt()
    val right = ceil(rect.right).toInt()
    val bottom = ceil(rect.bottom).toInt()
    require(
        left >= 0 &&
            top >= 0 &&
            right <= width &&
            bottom <= height &&
            right > left &&
            bottom > top,
    ) {
        "$sceneId rect-only $kind must be inside positive bounds: $label"
    }
}

private fun SceneRect.intersect(other: SceneRect?): SceneRect? {
    if (other == null) return this
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right > left && bottom > top) SceneRect(left, top, right, bottom) else null
}

private fun SceneRect.isInsideTarget(width: Int, height: Int): Boolean =
    left >= 0f &&
        top >= 0f &&
        right <= width.toFloat() &&
        bottom <= height.toFloat() &&
        right > left &&
        bottom > top
