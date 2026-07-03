# Textured Vertices GPU Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement GPU pipeline for `drawVertices` with `texCoords`, replacing the current `gpu_textured_vertices_unimplemented` degrade, unblocking 4 MeshWithShaders* GMs.

**Architecture:** Add 3 WGSL shader snippets, extend `GPUBackendRenderRecorder` with position+UV vertex buffer support, create `GPUDispatchVertices.kt` dispatch, and wire into `GPURenderer.kt`.

**Tech Stack:** Kotlin, WGSL, WebGPU backend (`gpu-renderer` module), Kanvas surface/gpu

**Spec reference:** `docs/superpowers/specs/2026-07-03-textured-vertices-gpu-design.md`

---

### Task 1: Add GPUBackendVertexPositionUVData and backend contracts

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt:161-177`

- [ ] **Step 1: Add GPUBackendVertexPositionUVData**

After `GPUBackendVertexColorData` (line 177), add:

```kotlin
/** Holds interleaved vertex data (position + uv) for textured vertex buffer passes. */
data class GPUBackendVertexPositionUVData(
    val vertexData: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = vertexData.size / 4
    val strideFloats: Int = 4

    init {
        require(vertexData.size >= 4) { "GPUBackendVertexPositionUVData.vertexData must have at least 4 floats" }
        require(vertexData.size % strideFloats == 0) {
            "GPUBackendVertexPositionUVData.vertexData must be a multiple of $strideFloats"
        }
        require(indices.isNotEmpty()) { "GPUBackendVertexPositionUVData.indices must not be empty" }
        require(indices.all { it in 0 until vertexCount }) { "GPUBackendVertexPositionUVData.indices out of range" }
    }
}
```

- [ ] **Step 2: Add backend methods to GPUBackendRenderRecorder interface**

After `drawVertexColorIndexed` (around line 236), add:

```kotlin
/** Creates a GPU vertex buffer from interleaved position + uv float data. Returns a stable label. */
fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String

/** Draws an indexed mesh using a previously-created position+uv vertex buffer with a bound texture. */
fun drawVertexPositionUVIndexed(
    vertexBufferLabel: String,
    indexCount: Int,
    uniformDraw: GPUBackendRawUniformDraw,
    textureRgba: ByteArray,
    textureWidth: Int,
    textureHeight: Int,
    textureFormat: String,
    blendMode: GPUBlendMode? = null,
)
```

- [ ] **Step 3: Add dual-UV backend method**

After the new `drawVertexPositionUVIndexed`, add:

```kotlin
/** Draws an indexed mesh with dual UV channels and two bound textures. */
fun drawVertexPositionDualUVIndexed(
    vertexBufferLabel: String,
    indexCount: Int,
    uniformDraw: GPUBackendRawUniformDraw,
    texture1Rgba: ByteArray,
    texture1Width: Int, texture1Height: Int,
    texture2Rgba: ByteArray,
    texture2Width: Int, texture2Height: Int,
    textureFormat: String,
    blendMode: GPUBlendMode? = null,
)
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :gpu-renderer:compileKotlin`

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt
git commit -m "gpu: add GPUBackendVertexPositionUVData and textured vertex draw contracts"
```

---

### Task 2: Implement backend methods in GPUBackendRuntimeWgpu

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:821-860`

- [ ] **Step 1: Read current createVertexColorBuffer implementation**

Read the `GPUBackendRuntimeWgpu.kt` around line 821 to understand the vertex buffer creation pattern.

- [ ] **Step 2: Implement createVertexPositionUVBuffer**

After `createVertexColorBuffer`, add:

```kotlin
override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String {
    val label = "vertexUV:${vertexBufferCounter++}"
    val stride = data.strideFloats * 4 // each float = 4 bytes
    val bufferSize = data.vertexData.size * 4

    val vertexBuffer = device.createBuffer(
        descriptor = BufferDescriptor(
            label = "$label:vb",
            size = bufferSize.toULong(),
            usage = BufferUsage.VERTEX or BufferUsage.COPY_DST,
            mappedAtCreation = false,
        )
    )
    device.queue.writeBuffer(vertexBuffer, 0uL, data.vertexData, 0, data.vertexData.size)

    val indexBuffer = device.createBuffer(
        descriptor = BufferDescriptor(
            label = "$label:ib",
            size = (data.indices.size * 4).toULong(), // u32 indices
            usage = BufferUsage.INDEX or BufferUsage.COPY_DST,
            mappedAtCreation = false,
        )
    )
    device.queue.writeBuffer(indexBuffer, 0uL, data.indices, 0, data.indices.size)

    vertexBuffers[label] = vertexBuffer
    indexBuffers[label] = indexBuffer
    vertexCounts[label] = data.vertexCount

    return label
}
```

- [ ] **Step 3: Implement drawVertexPositionUVIndexed**

After `drawVertexColorIndexed`, add:

```kotlin
override fun drawVertexPositionUVIndexed(
    vertexBufferLabel: String,
    indexCount: Int,
    uniformDraw: GPUBackendRawUniformDraw,
    textureRgba: ByteArray,
    textureWidth: Int,
    textureHeight: Int,
    textureFormat: String,
    blendMode: GPUBlendMode?,
) {
    val vertexBuffer = vertexBuffers[vertexBufferLabel]
        ?: throw IllegalStateException("Vertex buffer not found: $vertexBufferLabel")
    val indexBuffer = indexBuffers[vertexBufferLabel]
        ?: throw IllegalStateException("Index buffer not found: $vertexBufferLabel")

    val texture = createTexture(textureRgba, textureWidth, textureHeight, textureFormat)
    val sampler = device.createSampler(SamplerDescriptor(
        addressModeU = AddressMode.CLAMP_TO_EDGE,
        addressModeV = AddressMode.CLAMP_TO_EDGE,
        magFilter = FilterMode.LINEAR,
        minFilter = FilterMode.LINEAR,
    ))

    val uniformBuffer = createUniformBuffer(uniformDraw.uniformBytes)

    val bindGroupLayout = createTexturedVertexBindGroupLayout(device)
    val bindGroup = device.createBindGroup(BindGroupDescriptor(
        label = "texturedVertex:$vertexBufferLabel",
        layout = bindGroupLayout,
        entries = listOf(
            BindGroupEntry(0u, uniformBuffer),
            BindGroupEntry(1u, texture.createView()),
            BindGroupEntry(2u, sampler),
        ),
    ))

    val pipeline = getOrCreateTexturedVertexPipeline(textureFormat as String, blendMode)
    val passEncoder = currentRenderPassEncoder
        ?: throw IllegalStateException("No active render pass")

    passEncoder.setPipeline(pipeline)
    passEncoder.setBindGroup(0u, bindGroup)
    passEncoder.setVertexBuffer(0u, vertexBuffer, 0uL, (vertexCounts[vertexBufferLabel]!! * 4 * 4).toULong())
    passEncoder.setIndexBuffer(indexBuffer, IndexFormat.UINT32, 0uL)
    passEncoder.setScissorRect(uniformDraw.scissorX, uniformDraw.scissorY, uniformDraw.scissorWidth.toUInt(), uniformDraw.scissorHeight.toUInt())
    passEncoder.drawIndexed(indexCount.toUInt(), 1u, 0u, 0, 0u)
}
```

- [ ] **Step 4: Implement drawVertexPositionDualUVIndexed**

Follow same pattern with dual texture binding (entries 0=uniform, 1=tex1, 2=samp1, 3=tex2, 4=samp2):

```kotlin
override fun drawVertexPositionDualUVIndexed(
    vertexBufferLabel: String,
    indexCount: Int,
    uniformDraw: GPUBackendRawUniformDraw,
    texture1Rgba: ByteArray,
    texture1Width: Int, texture1Height: Int,
    texture2Rgba: ByteArray,
    texture2Width: Int, texture2Height: Int,
    textureFormat: String,
    blendMode: GPUBlendMode?,
) {
    val vertexBuffer = vertexBuffers[vertexBufferLabel]
        ?: throw IllegalStateException("Vertex buffer not found: $vertexBufferLabel")
    val indexBuffer = indexBuffers[vertexBufferLabel]
        ?: throw IllegalStateException("Index buffer not found: $vertexBufferLabel")

    val tex1 = createTexture(texture1Rgba, texture1Width, texture1Height, textureFormat)
    val tex2 = createTexture(texture2Rgba, texture2Width, texture2Height, textureFormat)
    val sampler = device.createSampler(SamplerDescriptor(
        addressModeU = AddressMode.CLAMP_TO_EDGE,
        addressModeV = AddressMode.CLAMP_TO_EDGE,
        magFilter = FilterMode.LINEAR,
        minFilter = FilterMode.LINEAR,
    ))

    val uniformBuffer = createUniformBuffer(uniformDraw.uniformBytes)

    val pipeline = getOrCreateDualUVVertexPipeline(textureFormat as String, blendMode)
    // Build bind group with 2 textures + 2 samplers
    // ...
    passEncoder.drawIndexed(indexCount.toUInt(), 1u, 0u, 0, 0u)
}
```

- [ ] **Step 5: Add helper methods for pipeline creation**

Add private methods `createTexturedVertexBindGroupLayout`, `getOrCreateTexturedVertexPipeline`, `getOrCreateDualUVVertexPipeline` that cache pipelines keyed by color format + blend mode.

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :gpu-renderer:compileKotlin`

- [ ] **Step 7: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt
git commit -m "gpu: implement textured vertex buffer creation and indexed draw"
```

---

### Task 3: Create TexturedVerticesSnippet.kt

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesSnippet.kt`

- [ ] **Step 1: Create the file**

Following the pattern of `VerticesSnippet.kt`:

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) @interpolate(flat) alpha: f32,
};

struct Uniforms {
    alpha: f32,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var texture_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture_sampler: sampler;

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        vec4<f32>(input.position, 0.0, 1.0),
        input.uv,
        uniforms.alpha,
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let color = textureSample(texture_sampled, texture_sampler, input.uv);
    return vec4<f32>(color.rgb, color.a * input.alpha);
}
"""

const val TexturedVerticesSnippetSourceHash: String = "vertex:textured_vertices:v1"
const val TexturedVerticesShaderEntryPoint: String = "vs_main"
const val TexturedVerticesFragmentEntryPoint: String = "fs_main"
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gpu-renderer:compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesSnippet.kt
git commit -m "wgsl: add TexturedVerticesSnippet for position+uv textured draws"
```

---

### Task 4: Create TexturedVerticesColorFilterSnippet.kt

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesColorFilterSnippet.kt`

- [ ] **Step 1: Create the file**

Same as Task 3 but with a 4×4 color matrix uniform:

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesColorFilterWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
    @location(1) @interpolate(flat) alpha: f32,
    @location(2) @interpolate(flat) matrixRow0: vec4<f32>,
    @location(3) @interpolate(flat) matrixRow1: vec4<f32>,
    @location(4) @interpolate(flat) matrixRow2: vec4<f32>,
    @location(5) @interpolate(flat) matrixRow3: vec4<f32>,
};

struct Uniforms {
    alpha: f32,
    colorMatrix: mat4x4<f32>,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var texture_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture_sampler: sampler;

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        vec4<f32>(input.position, 0.0, 1.0),
        input.uv,
        uniforms.alpha,
        uniforms.colorMatrix[0],
        uniforms.colorMatrix[1],
        uniforms.colorMatrix[2],
        uniforms.colorMatrix[3],
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let color = textureSample(texture_sampled, texture_sampler, input.uv);
    let m = mat4x4<f32>(input.matrixRow0, input.matrixRow1, input.matrixRow2, input.matrixRow3);
    let filtered = m * color;
    return vec4<f32>(filtered.rgb, filtered.a * input.alpha);
}
"""

const val TexturedVerticesColorFilterSnippetSourceHash: String = "vertex:textured_vertices_cf:v1"
const val TexturedVerticesColorFilterShaderEntryPoint: String = "vs_main"
const val TexturedVerticesColorFilterFragmentEntryPoint: String = "fs_main"
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gpu-renderer:compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesColorFilterSnippet.kt
git commit -m "wgsl: add TexturedVerticesColorFilterSnippet with 4x4 matrix"
```

---

### Task 5: Create TexturedVerticesDualBlendSnippet.kt

**Files:**
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesDualBlendSnippet.kt`

- [ ] **Step 1: Create the file**

Dual UV channels, two textures, blend mode uniform:

```kotlin
package org.graphiks.kanvas.gpu.renderer.wgsl

const val TexturedVerticesDualBlendWgsl: String = """
struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) uv1: vec2<f32>,
    @location(2) uv2: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv1: vec2<f32>,
    @location(1) uv2: vec2<f32>,
    @location(2) @interpolate(flat) alpha: f32,
    @location(3) @interpolate(flat) blendModeIdx: i32,
};

struct Uniforms {
    alpha: f32,
    blendMode: i32,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var texture1_sampled: texture_2d<f32>;
@group(1) @binding(2) var texture1_sampler: sampler;
@group(1) @binding(3) var texture2_sampled: texture_2d<f32>;
@group(1) @binding(4) var texture2_sampler: sampler;

@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    return VertexOutput(
        vec4<f32>(input.position, 0.0, 1.0),
        input.uv1,
        input.uv2,
        uniforms.alpha,
        uniforms.blendMode,
    );
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    let c1 = textureSample(texture1_sampled, texture1_sampler, input.uv1);
    let c2 = textureSample(texture2_sampled, texture2_sampler, input.uv2);
    // blendMode: 0=srcOver, 1=dstOver, 2=src, 3=dst, 4=multiply
    var result: vec4<f32>;
    let srcAlpha = c1.a;
    let invSrcAlpha = 1.0 - srcAlpha;
    switch input.blendModeIdx {
        default { result = c1 + c2 * invSrcAlpha; }          // srcOver
        case 1  { result = c2 + c1 * (1.0 - c2.a); }         // dstOver
        case 2  { result = c1; }                              // src
        case 3  { result = c2; }                              // dst
        case 4  { result = c1 * c2; }                         // multiply
        case 5  { result = c1 * invSrcAlpha + c2 * srcAlpha; }// difference-like
    }
    return vec4<f32>(result.rgb, result.a * input.alpha);
}
"""

const val TexturedVerticesDualBlendSnippetSourceHash: String = "vertex:textured_vertices_dual:v1"
const val TexturedVerticesDualBlendShaderEntryPoint: String = "vs_main"
const val TexturedVerticesDualBlendFragmentEntryPoint: String = "fs_main"
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gpu-renderer:compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/TexturedVerticesDualBlendSnippet.kt
git commit -m "wgsl: add TexturedVerticesDualBlendSnippet with dual texture + blender"
```

---

### Task 6: Create GPUDispatchVertices.kt

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt`

- [ ] **Step 1: Create the dispatch file**

```kotlin
package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesColorFilterWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesDualBlendWgsl
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.Diagnostics
import org.graphiks.kanvas.surface.RenderConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun GPUBackendRenderRecorder.dispatchTexturedVertices(
    positions: FloatArray,
    uvs: FloatArray,
    uvs2: FloatArray?,
    indices: IntArray,
    paint: Paint,
    textureCache: Map<String, ByteArray>,
    textureSourceId: String?,
    textureWidth: Int,
    textureHeight: Int,
    diagnostics: Diagnostics,
    surfaceWidth: Int, surfaceHeight: Int,
    config: RenderConfig,
    diagnosticName: String,
) {
    fun refuse(reason: String) {
        diagnostics.degrade("refuse:texturedVertices:$diagnosticName", diagnosticName, reason)
    }

    // Validate inputs
    if (positions.size < 6 || positions.size % 2 != 0) {
        refuse("invalid_positions:${positions.size}")
        return
    }
    if (uvs.size != positions.size) {
        refuse("uv_position_mismatch:${uvs.size}vs${positions.size}")
        return
    }
    val vertexCount = positions.size / 2

    // Determine which shader variant to use
    val hasColorFilter = paint.colorFilter != null
    val hasDualUV = uvs2 != null && uvs2.size == uvs.size
    val hasTextureShader = paint.shader is Shader.Image && textureSourceId != null

    if (!hasTextureShader) {
        refuse("no_image_shader")
        return
    }

    // Build interleaved vertex data
    val vertexData: FloatArray
    val wgsl: String

    if (hasDualUV) {
        // position + uv1 + uv2 = 6 floats per vertex
        vertexData = FloatArray(vertexCount * 6)
        for (i in 0 until vertexCount) {
            vertexData[i * 6 + 0] = positions[i * 2]
            vertexData[i * 6 + 1] = positions[i * 2 + 1]
            vertexData[i * 6 + 2] = uvs[i * 2]
            vertexData[i * 6 + 3] = uvs[i * 2 + 1]
            vertexData[i * 6 + 4] = uvs2[i * 2]
            vertexData[i * 6 + 5] = uvs2[i * 2 + 1]
        }
        wgsl = TexturedVerticesDualBlendWgsl
    } else if (hasColorFilter && paint.colorFilter is ColorFilter.Matrix) {
        vertexData = FloatArray(vertexCount * 4)
        for (i in 0 until vertexCount) {
            vertexData[i * 4 + 0] = positions[i * 2]
            vertexData[i * 4 + 1] = positions[i * 2 + 1]
            vertexData[i * 4 + 2] = uvs[i * 2]
            vertexData[i * 4 + 3] = uvs[i * 2 + 1]
        }
        wgsl = TexturedVerticesColorFilterWgsl
    } else {
        vertexData = FloatArray(vertexCount * 4)
        for (i in 0 until vertexCount) {
            vertexData[i * 4 + 0] = positions[i * 2]
            vertexData[i * 4 + 1] = positions[i * 2 + 1]
            vertexData[i * 4 + 2] = uvs[i * 2]
            vertexData[i * 4 + 3] = uvs[i * 2 + 1]
        }
        wgsl = TexturedVerticesWgsl
    }

    val vertexBuffer = GPUBackendVertexPositionUVData(vertexData, indices)
    val label = createVertexPositionUVBuffer(vertexBuffer)

    // Pack uniforms
    val alpha = paint.color.a
    val blendModeOrdinal = paint.blendMode.ordinal
    val colorMatrix = (paint.colorFilter as? ColorFilter.Matrix)?.values ?: FloatArray(16)
    val uniformBytes = ByteBuffer.allocate(if (hasDualUV) 8 else if (hasColorFilter) 68 else 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putFloat(alpha)
    if (hasDualUV) {
        uniformBytes.putInt(blendModeOrdinal)
    }
    if (hasColorFilter) {
        for (f in colorMatrix.take(16)) uniformBytes.putFloat(f)
    }

    val uniformDraw = GPUBackendRawUniformDraw(
        uniformBytes = uniformBytes.array(),
        scissorX = 0, scissorY = 0,
        scissorWidth = surfaceWidth, scissorHeight = surfaceHeight,
    )

    val textureRgba = textureCache[textureSourceId]!!
    val gpuBlendMode = GPUBlendMode.values().getOrNull(paint.blendMode.ordinal)

    if (hasDualUV) {
        // Second texture — use a separate source or default to first texture
        // TODO: proper second texture source from Shader.Blend
        drawVertexPositionDualUVIndexed(
            vertexBufferLabel = label,
            indexCount = indices.size,
            uniformDraw = uniformDraw,
            texture1Rgba = textureRgba,
            texture1Width = textureWidth, texture1Height = textureHeight,
            texture2Rgba = textureRgba,
            texture2Width = textureWidth, texture2Height = textureHeight,
            textureFormat = "rgba8unorm",
            blendMode = gpuBlendMode,
        )
    } else {
        drawVertexPositionUVIndexed(
            vertexBufferLabel = label,
            indexCount = indices.size,
            uniformDraw = uniformDraw,
            textureRgba = textureRgba,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            textureFormat = "rgba8unorm",
            blendMode = gpuBlendMode,
        )
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchVertices.kt
git commit -m "gpu: add dispatchTexturedVertices with single/CF/dual routing"
```

---

### Task 7: Wire dispatch into GPURenderer

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:604-658`

- [ ] **Step 1: Replace the degrade block**

Find the `if (verts.texCoords != null)` block (around line 606) and replace:

```kotlin
// Before:
if (verts.texCoords != null) {
    diagnostics.degrade("unimplemented:drawVertices:textured:${cmdId.value}", "drawVertices", "gpu_textured_vertices_unimplemented")
}

// After:
if (verts.texCoords != null) {
    val tex = verts.texCoords
    val indices = verts.indices?.toIntArray() ?: (0 until verts.positions.size).toList().toIntArray()
    val posFlat = FloatArray(verts.positions.size * 2) {
        if (it % 2 == 0) verts.positions[it / 2].x else verts.positions[it / 2].y
    }
    val uvFlat = FloatArray(tex.size * 2) {
        if (it % 2 == 0) tex[it / 2].x else tex[it / 2].y
    }
    val imageShader = op.paint.shader as? Shader.Image ?: return
    dispatchTexturedVertices(
        positions = posFlat, uvs = uvFlat, uvs2 = null,
        indices = indices, paint = op.paint,
        textureCache = textureCache,
        textureSourceId = imageShader.image.sourceId,
        textureWidth = imageShader.image.width,
        textureHeight = imageShader.image.height,
        diagnostics = diagnostics,
        surfaceWidth = width, surfaceHeight = height,
        config = config, diagnosticName = op.paint.shader.toString(),
    )
    sceneHasContent = true
    return
}
```

- [ ] **Step 2: Add import to GPURenderer.kt**

```kotlin
import org.graphiks.kanvas.surface.gpu.dispatchTexturedVertices
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: wire dispatchTexturedVertices into drawVertices path"
```

---

### Task 8: Wire into DrawMesh handler similarly

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt` (DrawMesh block)

- [ ] **Step 1: Update DrawMesh textured path**

In the `is DisplayOp.DrawMesh` block, replace the textured dispatch stub with the same `dispatchTexturedVertices` call, using `op.mesh.vertices` instead of `op.vertices`.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :kanvas:compileKotlin`

- [ ] **Step 3: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "gpu: wire dispatchTexturedVertices into DrawMesh handler"
```

---

### Task 9: Final verification

- [ ] **Step 1: Compile all relevant modules**

```bash
./gradlew :gpu-renderer:compileKotlin :kanvas:compileKotlin :integration-tests:skia:compileTestKotlin
```

Expected: BUILD SUCCESSFUL (warnings OK, no errors)

- [ ] **Step 2: Verify degrade is gone**

Run: `grep -n "gpu_textured_vertices_unimplemented" kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`

Expected: No matches (degrade removed or replaced with dispatch)

- [ ] **Step 3: Commit any final fixes**

```bash
git status
git add ...
git commit -m "gpu: final textured vertices integration fixes"
```

---

## Summary

| Task | What | Files |
|---|---|---|
| 1 | Backend contracts | `GPUBackendRuntimeContracts.kt` |
| 2 | Backend implementation | `GPUBackendRuntimeWgpu.kt` |
| 3 | Single texture WGSL | `TexturedVerticesSnippet.kt` (new) |
| 4 | Color filter WGSL | `TexturedVerticesColorFilterSnippet.kt` (new) |
| 5 | Dual blend WGSL | `TexturedVerticesDualBlendSnippet.kt` (new) |
| 6 | Dispatch logic | `GPUDispatchVertices.kt` (new) |
| 7 | Wire into GPURenderer | `GPURenderer.kt` |
| 8 | Wire into DrawMesh | `GPURenderer.kt` |
| 9 | Final verification | All modules compile |
