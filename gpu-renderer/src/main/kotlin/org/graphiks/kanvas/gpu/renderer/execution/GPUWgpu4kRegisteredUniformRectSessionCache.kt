package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorMatrixWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

internal data class GPUWgpu4kRegisteredUniformRectPipelineCacheKey(
    val program: GPURegisteredUniformProgram,
    val targetFormat: String,
    val sampleCount: Int,
)

internal data class GPURegisteredUniformRectNativeCacheCounters(
    val invariantCreations: Long = 0L,
    val invariantReuses: Long = 0L,
)

internal class GPUWgpu4kRegisteredUniformRectInvariantHandles(
    val bindGroupLayout: GPUBindGroupLayout,
    val pipeline: GPURenderPipeline,
    private val owned: GPURegisteredUniformRectCachedHandleSet,
) : AutoCloseable by owned

/** Multi-entry session cache keyed by closed program identity and attachment state. */
internal class GPUWgpu4kRegisteredUniformRectSessionCache(
    private val device: GPUDevice,
) : AutoCloseable {
    private val preRegistrationHandles = GPUPreRegistrationNativeHandleLedger()
    private val entries = linkedMapOf<
        GPUWgpu4kRegisteredUniformRectPipelineCacheKey,
        GPUWgpu4kRegisteredUniformRectInvariantHandles
    >()
    private var closed = false
    private var creations = 0L
    private var reuses = 0L

    @Synchronized
    fun acquire(
        key: GPUWgpu4kRegisteredUniformRectPipelineCacheKey,
    ): GPUWgpu4kRegisteredUniformRectInvariantHandles {
        check(!closed) { "The registered uniform native session cache is closed" }
        entries[key]?.let {
            reuses += 1L
            return it
        }
        requireCleanSetupLedger()
        return try {
            val created = createInvariants(key)
            entries[key] = created
            preRegistrationHandles.transferAll()
            creations += 1L
            created
        } catch (failure: Throwable) {
            preRegistrationHandles.closeRetainingFailures()
            throw failure
        }
    }

    @Synchronized
    fun counters(): GPURegisteredUniformRectNativeCacheCounters =
        GPURegisteredUniformRectNativeCacheCounters(creations, reuses)

    @Synchronized
    override fun close() {
        if (closed && entries.isEmpty() && preRegistrationHandles.pendingHandleCount == 0) return
        closed = true
        var firstFailure: Throwable? = null
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            try {
                entry.value.close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
            }
        }
        if (!preRegistrationHandles.closeRetainingFailures()) {
            val failure = IllegalStateException(
                "Registered uniform session cache retained " +
                    "${preRegistrationHandles.pendingHandleCount} failed setup handle(s)",
            )
            if (firstFailure == null) firstFailure = failure else firstFailure.addSuppressed(failure)
        }
        firstFailure?.let { throw it }
    }

    private fun createInvariants(
        key: GPUWgpu4kRegisteredUniformRectPipelineCacheKey,
    ): GPUWgpu4kRegisteredUniformRectInvariantHandles {
        require(key.targetFormat == REGISTERED_UNIFORM_RGBA8_UNORM) {
            "Registered uniform cache accepts only rgba8unorm"
        }
        require(key.sampleCount == 1) {
            "Registered uniform first slice accepts only single-sample rendering"
        }
        val wgsl = registeredUniformRectWgsl(key.program)
        val pending = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> T.track(): T = also {
            pending += it
            preRegistrationHandles.track(it)
        }
        return try {
            val bindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "Kanvas.session.registeredUniform.${key.program.wireId}.bindGroupLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                    ),
                ),
            ).track()
            val shader = device.createShaderModule(
                ShaderModuleDescriptor(
                    label = "Kanvas.session.registeredUniform.${key.program.wireId}.shader",
                    code = wgsl,
                ),
            ).track()
            val pipelineLayout = device.createPipelineLayout(
                PipelineLayoutDescriptor(
                    label = "Kanvas.session.registeredUniform.${key.program.wireId}.pipelineLayout",
                    bindGroupLayouts = listOf(bindGroupLayout),
                ),
            ).track()
            val pipeline = device.createRenderPipeline(
                RenderPipelineDescriptor(
                    label = "Kanvas.session.registeredUniform.${key.program.wireId}.pipeline",
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    multisample = MultisampleState(count = 1u),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = GPUTextureFormat.RGBA8Unorm,
                                blend = BlendState(
                                    color = BlendComponent(
                                        GPUBlendOperation.Add,
                                        GPUBlendFactor.One,
                                        GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                    alpha = BlendComponent(
                                        GPUBlendOperation.Add,
                                        GPUBlendFactor.One,
                                        GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ).track()
            GPUWgpu4kRegisteredUniformRectInvariantHandles(
                bindGroupLayout = bindGroupLayout,
                pipeline = pipeline,
                owned = GPURegisteredUniformRectCachedHandleSet(pending.toList()),
            )
        } catch (failure: Throwable) {
            throw failure
        }
    }

    private fun requireCleanSetupLedger() {
        check(preRegistrationHandles.closeRetainingFailures()) {
            "Registered uniform cache cannot allocate while failed setup handles remain quarantined"
        }
    }
}

internal class GPURegisteredUniformRectCachedHandleSet(handles: List<AutoCloseable>) : AutoCloseable {
    private val pending = handles.asReversed().toMutableList()

    @Synchronized
    override fun close() {
        var firstFailure: Throwable? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { throw IllegalStateException("Registered uniform cached handles remain live", it) }
    }
}

internal fun registeredUniformRectWgsl(program: GPURegisteredUniformProgram): String = when (program) {
    GPURegisteredUniformProgram.SolidColor -> REGISTERED_UNIFORM_SOLID_COLOR_WGSL
    GPURegisteredUniformProgram.LinearGradient -> REGISTERED_UNIFORM_LINEAR_GRADIENT_WGSL
    GPURegisteredUniformProgram.RadialGradient -> REGISTERED_UNIFORM_RADIAL_GRADIENT_WGSL
    GPURegisteredUniformProgram.SweepGradient -> REGISTERED_UNIFORM_SWEEP_GRADIENT_WGSL
    GPURegisteredUniformProgram.ColorMatrix -> ColorMatrixWgsl
    GPURegisteredUniformProgram.SimpleRuntimeEffect -> REGISTERED_UNIFORM_SIMPLE_RT_WGSL
    else -> error("Registered uniform program ${program.wireId} has no native implementation in this slice")
}

private val REGISTERED_UNIFORM_VERTEX_WGSL = """
@vertex
fn vs_main(@builtin(vertex_index) vertex_index: u32) -> @builtin(position) vec4f {
    var positions = array<vec2f, 3>(
        vec2f(-1.0, -1.0),
        vec2f(3.0, -1.0),
        vec2f(-1.0, 3.0),
    );
    return vec4f(positions[vertex_index], 0.0, 1.0);
}
""".trimIndent()

private val REGISTERED_UNIFORM_SOLID_COLOR_WGSL = """
struct Uniforms { color: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$REGISTERED_UNIFORM_VERTEX_WGSL

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(uniforms.color.rgb * uniforms.color.a, uniforms.color.a);
}
""".trimIndent()

private val REGISTERED_UNIFORM_LINEAR_GRADIENT_WGSL = """
struct Uniforms { start: vec4f, end: vec4f, startColor: vec4f, endColor: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$REGISTERED_UNIFORM_VERTEX_WGSL

$LinearGradientWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    var positions: array<vec4f, 16>;
    var colors: array<vec4f, 16>;
    positions[0] = vec4f(0.0, 0.0, 0.0, 0.0);
    positions[1] = vec4f(1.0, 0.0, 0.0, 0.0);
    colors[0] = uniforms.startColor;
    colors[1] = uniforms.endColor;
    let color = $LinearGradientEntryPoint(
        pos,
        uniforms.start.xy,
        uniforms.end.xy,
        2u,
        &positions,
        &colors,
    );
    return vec4f(color.rgb * color.a, color.a);
}
""".trimIndent()

private val REGISTERED_UNIFORM_RADIAL_GRADIENT_WGSL = """
struct Uniforms { center: vec4f, startColor: vec4f, endColor: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$REGISTERED_UNIFORM_VERTEX_WGSL

$RadialGradientWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    var positions: array<vec4f, 16>;
    var colors: array<vec4f, 16>;
    positions[0] = vec4f(0.0, 0.0, 0.0, 0.0);
    positions[1] = vec4f(1.0, 0.0, 0.0, 0.0);
    colors[0] = uniforms.startColor;
    colors[1] = uniforms.endColor;
    let color = $RadialGradientEntryPoint(
        pos,
        uniforms.center.xy,
        uniforms.center.z,
        2u,
        &positions,
        &colors,
    );
    return vec4f(color.rgb * color.a, color.a);
}
""".trimIndent()

private val REGISTERED_UNIFORM_SWEEP_GRADIENT_WGSL = """
struct Uniforms { center: vec4f, angles: vec4f, startColor: vec4f, endColor: vec4f }
@group(0) @binding(0) var<uniform> uniforms: Uniforms;

$REGISTERED_UNIFORM_VERTEX_WGSL

$SweepGradientWgsl

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    var positions: array<vec4f, 16>;
    var colors: array<vec4f, 16>;
    positions[0] = vec4f(0.0, 0.0, 0.0, 0.0);
    positions[1] = vec4f(1.0, 0.0, 0.0, 0.0);
    colors[0] = uniforms.startColor;
    colors[1] = uniforms.endColor;
    let color = $SweepGradientEntryPoint(
        pos,
        uniforms.center.xy,
        uniforms.angles.x,
        uniforms.angles.y,
        2u,
        &positions,
        &colors,
    );
    return vec4f(color.rgb * color.a, color.a);
}
""".trimIndent()

private val REGISTERED_UNIFORM_SIMPLE_RT_WGSL = """
${SimpleRTWgsl.replace("@group(1)", "@group(0)")}

$REGISTERED_UNIFORM_VERTEX_WGSL

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let color = $SimpleRTEntryPoint(pos.xy);
    return vec4f(color.rgb * color.a, color.a);
}
""".trimIndent()

private const val REGISTERED_UNIFORM_RGBA8_UNORM = "rgba8unorm"
