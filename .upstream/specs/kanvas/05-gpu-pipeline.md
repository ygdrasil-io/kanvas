# GPU Pipeline Interface

Status: Draft
Date: 2026-07-01

## Purpose

Defines the programmable GPU pipeline interface: `GPUContext`, `RenderPass`, `RenderPipeline`, `ShaderModule`, `RuntimeEffect`, and all supporting configuration types. This is the public API surface for GPU-level interaction.

## Contracts

### GPUContext

```kotlin
interface GPUContext {
    fun createShaderModule(source: String): ShaderModule
    fun createPipeline(desc: RenderPipeline): GPUHandle
    fun createUniformBuffer(data: UniformBlock): GPUHandle
    fun beginRenderPass(desc: RenderPassDescriptor): RenderPass
}
```

- The entry point for GPU resource creation
- Implemented by `:gpu-renderer` backend
- Accessible via `Surface.gpu: GPUContext`

### GPUHandle

```kotlin
@JvmInline
value class GPUHandle(val id: Long)
```

- Opaque handle to a GPU resource (pipeline, buffer, texture)
- Value class — zero allocation

### RenderPass

```kotlin
interface RenderPass {
    val pipeline: GPUHandle
    fun bindVertexBuffer(slot: Int, buffer: GPUHandle)
    fun bindUniform(group: Int, binding: Int, buffer: GPUHandle)
    fun bindTexture(group: Int, binding: Int, texture: GPUHandle)
    fun draw(vertexCount: Int, instanceCount: Int = 1)
    fun drawIndexed(indexCount: Int)
    fun end()
}
```

- Maps to WebGPU `GPURenderPassEncoder`
- Bind-then-draw model

### RenderPipeline

```kotlin
data class RenderPipeline(
    val vertexShader: ShaderModule,
    val fragmentShader: ShaderModule,
    val blend: BlendConfig = BlendConfig.SRC_OVER,
    val depthStencil: DepthStencilConfig? = null,
    val topology: PrimitiveTopology = PrimitiveTopology.TRIANGLE_LIST,
) {
    companion object {
        val SOLID_COLOR_FILL: RenderPipeline
        val LINEAR_GRADIENT_FILL: RenderPipeline
        val ROUNDED_RECT_SDF: RenderPipeline
        val TEXT_ATLAS_GLYPH: RenderPipeline
        val STENCIL_COVER: RenderPipeline
        val IMAGE_DRAW: RenderPipeline
        val BLUR_PASS: RenderPipeline
    }
}
```

- Built-in companion constants provide the standard pipelines used by the high-level API
- Each pipeline ties a vertex + fragment shader pair to blend/depth/topology state

### ShaderModule

```kotlin
class ShaderModule private constructor(
    val source: String,
    val entryPoint: String,
    val uniforms: List<UniformSlot>,
    val textures: List<TextureSlot>,
    val vertexLayout: VertexLayout,
) {
    companion object {
        fun fromSource(wgsl: String, entry: String = "main"): ShaderModule
        fun fromResource(path: String, entry: String = "main"): ShaderModule
    }
}
```

- Wraps validated WGSL source with reflection metadata
- `fromSource`: inline WGSL string → compiled module
- `fromResource`: classpath resource → compiled module

### RuntimeEffect

```kotlin
class RuntimeEffect private constructor(
    val id: String,
    val module: ShaderModule,
    val uniformLayout: UniformLayout,
    val children: List<ChildSlot>,
) {
    fun makeShader(uniforms: UniformBlock): Shader.RuntimeEffect
    fun makeColorFilter(uniforms: UniformBlock): ColorFilter
    fun makeBlender(uniforms: UniformBlock): Blender

    companion object {
        fun compile(wgsl: String): Result<RuntimeEffect>
        fun registered(id: String): RuntimeEffect?
    }
}
```

- First-class custom shader — can be used as a `Shader`, `ColorFilter`, or `Blender`
- `compile()` validates WGSL and extracts uniform/child metadata
- `registered()` looks up pre-registered effects by ID

### UniformBlock

```kotlin
class UniformBlock private constructor(val entries: Map<String, UniformValue>)

// DSL builder
UniformBlock {
    float2("offset", 10f, 20f)
    float4("color", 1f, 0f, 0f, 1f)
    mat3x3("transform", Matrix33.identity())
}

sealed interface UniformValue {
    data class F1(val v: Float) : UniformValue
    data class F2(val x: Float, val y: Float) : UniformValue
    data class F3(val x: Float, val y: Float, val z: Float) : UniformValue
    data class F4(val x: Float, val y: Float, val z: Float, val w: Float) : UniformValue
    data class M3(val m: Matrix33) : UniformValue
    data class M4(val values: FloatArray) : UniformValue
}
```

### Supporting Config Types

```kotlin
data class BlendConfig(
    val colorSrc: BlendFactor = BlendFactor.SRC_ALPHA,
    val colorDst: BlendFactor = BlendFactor.ONE_MINUS_SRC_ALPHA,
    val colorOp: BlendOp = BlendOp.ADD,
)
data class DepthStencilConfig(val depthCompare: CompareOp, val depthWrite: Boolean)
data class VertexLayout(val attributes: List<VertexAttribute>, val stride: Int, val stepMode: VertexStepMode)
data class VertexAttribute(val format: VertexFormat, val offset: Int, val shaderLocation: Int)
data class RenderPassDescriptor(val colorAttachments: List<ColorAttachment>, val depthStencilAttachment: DepthStencilAttachment?)
data class ColorAttachment(val texture: GPUHandle, val loadOp: LoadOp, val storeOp: StoreOp, val clearColor: Color)

data class UniformSlot(val name: String, val binding: Int, val type: UniformType, val size: Int)
data class TextureSlot(val name: String, val binding: Int)
data class UniformLayout(val slots: List<UniformSlot>)
data class ChildSlot(val name: String, val type: ChildType)

enum class BlendFactor { ZERO, ONE, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ... }
enum class BlendOp { ADD, SUBTRACT, REVERSE_SUBTRACT, MIN, MAX }
enum class CompareOp { NEVER, LESS, EQUAL, LESS_EQUAL, GREATER, NOT_EQUAL, GREATER_EQUAL, ALWAYS }
enum class VertexFormat { FLOAT32, FLOAT32x2, FLOAT32x3, FLOAT32x4, UINT8x4, ... }
enum class VertexStepMode { VERTEX, INSTANCE }
enum class PrimitiveTopology { TRIANGLE_LIST, TRIANGLE_STRIP, LINE_STRIP, LINE_LIST, POINT_LIST }
enum class LoadOp { CLEAR, LOAD }
enum class StoreOp { STORE, DISCARD }
enum class UniformType { FLOAT, FLOAT2, FLOAT3, FLOAT4, MAT3X3, MAT4X4 }
enum class ChildType { SHADER, COLOR_FILTER, BLENDER }
enum class ClipOp { INTERSECT, DIFFERENCE }
```

### PipelineCompiler (internal)

```kotlin
class PipelineCompiler(private val gpu: GPUContext) {
    fun compile(ops: List<DisplayOp>): CompiledFrame
}

data class CompiledFrame(val passes: List<RenderPass>, val diagnostics: Diagnostics)
```

- Internal to `Surface.render()` — not instantiated by users
- Exhaustive `when` on `DisplayOp` → maps each op to a `RenderPipeline` + bindings
- Produces `RenderPass[]` for execution + `Diagnostics` for refusals

## Non-Goals

- Compute pipeline interface — deferred
- Multi-pass render graph DAG — current model is linear pass list
- GPU buffer/texture lifecycle management (create/destroy) — delegated to `:gpu-renderer`
- WGSL parser/reflection implementation — delegated to `wgsl4k` library
