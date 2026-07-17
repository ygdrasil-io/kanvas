package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.VertexState

internal data class GPUWgpu4kSurfaceBlitCacheCounters(
    val formatCreations: Long,
    val formatReuses: Long,
)

internal class GPUWgpu4kSurfaceBlitCacheLease(
    val sourceView: GPUTextureView,
    val pipeline: GPURenderPipeline,
    val bindGroup: GPUBindGroup,
)

/** Session-owned fullscreen blit resources keyed by the exact native surface format. */
internal class GPUWgpu4kSurfaceBlitSessionCache(
    private val device: GPUDevice,
    private val preparedSceneTarget: GPUWgpu4kPreparedSceneTarget,
) : AutoCloseable {
    private val entries = linkedMapOf<GPUTextureFormat, Entry>()
    private var creations = 0L
    private var reuses = 0L
    private var closed = false

    @Synchronized
    fun acquire(format: GPUTextureFormat): GPUWgpu4kSurfaceBlitCacheLease {
        check(!closed) { "The surface blit session cache is closed" }
        require(format in SUPPORTED_SURFACE_FORMATS) { "Unsupported surface blit format: $format" }
        entries[format]?.let { entry ->
            reuses += 1L
            return GPUWgpu4kSurfaceBlitCacheLease(entry.sourceView, entry.pipeline, entry.bindGroup)
        }
        val entry = createEntry(format)
        entries[format] = entry
        creations += 1L
        return GPUWgpu4kSurfaceBlitCacheLease(entry.sourceView, entry.pipeline, entry.bindGroup)
    }

    @Synchronized
    fun counters() = GPUWgpu4kSurfaceBlitCacheCounters(creations, reuses)

    @Synchronized
    override fun close() {
        if (closed && entries.isEmpty()) return
        closed = true
        var firstFailure: Throwable? = null
        entries.values.toList().asReversed().forEach { entry ->
            try {
                entry.close()
                entries.entries.removeIf { it.value === entry }
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }
    }

    private fun createEntry(format: GPUTextureFormat): Entry {
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also { pending += it }
        return try {
            val layout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.surfaceBlit.$format.bindGroupLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            ).track()
            val shader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.surfaceBlit.$format.shader",
                    code = SURFACE_BLIT_WGSL,
                ),
            ).track()
            val pipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.surfaceBlit.$format.pipelineLayout",
                    bindGroupLayouts = listOf(layout),
                ),
            ).track()
            val pipeline = device.createRenderPipeline(
                RenderPipelineDescriptor(
                    label = "Kanvas.session.surfaceBlit.$format.pipeline",
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    multisample = MultisampleState(count = 1u),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(ColorTargetState(format = format)),
                    ),
                ),
            ).track()
            val sampler = device.createSampler(
                SamplerDescriptor(
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                    label = "Kanvas.session.surfaceBlit.$format.sampler",
                ),
            ).track()
            val (_, sourceView) = preparedSceneTarget.borrow()
            val bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    label = "Kanvas.session.surfaceBlit.$format.bindGroup",
                    layout = layout,
                    entries = listOf(
                        BindGroupEntry(0u, sourceView),
                        BindGroupEntry(1u, sampler),
                    ),
                ),
            ).track()
            Entry(sourceView, pipeline, bindGroup, pending.toList())
        } catch (failure: Throwable) {
            pending.asReversed().forEach { runCatching { it.close() } }
            throw failure
        }
    }

    private class Entry(
        val sourceView: GPUTextureView,
        val pipeline: GPURenderPipeline,
        val bindGroup: GPUBindGroup,
        handles: List<AutoCloseable>,
    ) : AutoCloseable {
        private val pending = handles.asReversed().toMutableList()

        override fun close() {
            var firstFailure: Throwable? = null
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                try {
                    iterator.next().close()
                    iterator.remove()
                } catch (failure: Throwable) {
                    if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
                }
            }
            firstFailure?.let { throw it }
        }
    }

    private companion object {
        val SUPPORTED_SURFACE_FORMATS = setOf(
            GPUTextureFormat.RGBA8Unorm,
            GPUTextureFormat.RGBA8UnormSrgb,
            GPUTextureFormat.BGRA8Unorm,
            GPUTextureFormat.BGRA8UnormSrgb,
        )

        const val SURFACE_BLIT_WGSL = """
            struct VertexOutput {
                @builtin(position) position: vec4<f32>,
                @location(0) uv: vec2<f32>,
            }

            @group(0) @binding(0) var scene_texture: texture_2d<f32>;
            @group(0) @binding(1) var scene_sampler: sampler;

            @vertex
            fn vs_main(@builtin(vertex_index) vertex_index: u32) -> VertexOutput {
                let positions = array<vec2<f32>, 3>(
                    vec2<f32>(-1.0, -1.0),
                    vec2<f32>(3.0, -1.0),
                    vec2<f32>(-1.0, 3.0),
                );
                let position = positions[vertex_index];
                var output: VertexOutput;
                output.position = vec4<f32>(position, 0.0, 1.0);
                output.uv = vec2<f32>((position.x + 1.0) * 0.5, (1.0 - position.y) * 0.5);
                return output;
            }

            @fragment
            fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
                return textureSample(scene_texture, scene_sampler, input.uv);
            }
        """
    }
}
