# Coverage Compute Shader AA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace stencil-cover for simple strokes (2-point BUTT) with a compute shader that writes coverage into an R8 texture, then a fragment shader samples it as alpha.

**Architecture:** Compute shader rasterizes stroke → R8Unorm coverage texture → fragment shader samples coverage as alpha → SRC_OVER blend into framebuffer. No stencil, no MSAA.

**Tech Stack:** Kotlin, wgpu4k WebGPU (compute pipeline, storage texture), WGSL

**Key wgpu4k APIs:**
- `device.createComputePipeline(GPUComputePipelineDescriptor(...))`
- `encoder.beginComputePass(null)` → `ComputePassEncoder`
- `ComputePassEncoder.dispatchWorkgroups(x, y, z)`, `setPipeline`, `setBindGroup`
- `GPUBindGroupLayoutEntry` with `storageTexture: GPUStorageTextureBindingLayout`
- Wgpu4k types follow WebGPU spec: `GPUComputePipelineDescriptor`, `GPUProgrammableStage`, `GPUShaderModule`, etc.

---

### Task 1: Add COVERAGE_STROKE_WGSL and COVERAGE_FILL_WGSL shaders

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt` — append after `STROKE_AA_WGSL`

- [ ] **Step 1: Add compute shader**

Append after the `STROKE_AA_WGSL` block:

```kotlin
internal val COVERAGE_STROKE_WGSL: String = """
    struct StrokeParams {
        p0: vec2f,
        p1: vec2f,
        halfWidth: f32,
        aaWidth: f32,
    };

    @group(0) @binding(0) var<uniform> params: StrokeParams;
    @group(0) @binding(1) var coverage: texture_storage_2d<r8unorm, write>;

    @compute @workgroup_size(8, 8)
    fn main(@builtin(global_invocation_id) gid: vec3u) {
        let p = vec2f(gid.xy);
        let v = params.p1 - params.p0;
        let w = p - params.p0;
        let t = clamp(dot(w, v) / max(dot(v, v), 0.001), 0.0, 1.0);
        let closest = params.p0 + t * v;
        let dist = length(p - closest);
        let inner = params.halfWidth;
        let outer = inner + params.aaWidth;
        let alpha = 1.0 - smoothstep(inner, outer, dist);
        textureStore(coverage, vec2u(gid.xy), vec4f(alpha, 0.0, 0.0, 0.0));
    }
""".trimIndent()
```

- [ ] **Step 2: Add coverage fill fragment shader**

```kotlin
internal val COVERAGE_FILL_WGSL: String = """
    struct FillUniforms {
        color: vec4f,
    };

    @group(0) @binding(0) var<uniform> fill: FillUniforms;
    @group(0) @binding(1) var covTex: texture_2d<f32>;
    @group(0) @binding(2) var covSampler: sampler;

    struct VertexOut {
        @builtin(position) pos: vec4f,
        @location(0) uv: vec2f,
    };

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> VertexOut {
        var out: VertexOut;
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        out.pos = vec4f(x, y, 0.0, 1.0);
        out.uv = vec2f(x * 0.5 + 0.5, 0.5 - y * 0.5);
        return out;
    }

    @fragment
    fn fs_main(in: VertexOut) -> @location(0) vec4f {
        let coverage = textureSample(covTex, covSampler, in.uv).r;
        return vec4f(fill.color.rgb * coverage, coverage);
    }
""".trimIndent()
```

- [ ] **Step 3: Compile**

```bash
./gradlew :kanvas:compileKotlin --no-daemon
```

- [ ] **Step 4: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt
git commit -m "feat: add COVERAGE_STROKE_WGSL compute shader and COVERAGE_FILL_WGSL"
```

---

### Task 2: Add compute pipeline + coverage fill pipeline to backend

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt` — add `recordCoverageStroke` and `recordCoverageFill` to `GPUBackendRenderRecorder`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt` — implement both

- [ ] **Step 1: Add contract methods to GPUBackendRenderRecorder**

In `GPUBackendRuntimeContracts.kt`, find `interface GPUBackendRenderRecorder` and add:

```kotlin
fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTexture: String,  // label of the R8 coverage texture
    workgroupCountX: Int,
    workgroupCountY: Int,
)

fun recordCoverageFill(
    wgsl: String,
    colorUniforms: ByteArray,
    coverageTexture: String,
    blendMode: GPUBlendMode? = null,
)
```

- [ ] **Step 2: Implement recordCoverageStroke in WgpuRenderRecorder**

In `GPUBackendRuntimeWgpu.kt`, find `class WgpuRenderRecorder` and add:

```kotlin
override fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTexture: String,
    workgroupCountX: Int,
    workgroupCountY: Int,
) {
    val targetFmt = targetFormat
    val cacheKeys = coverageComputeCacheKeys(wgsl = wgsl, targetFormat = targetFmt)

    val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = cacheKeys)
    val bindGroupLayout = executionCaches.coverageComputeBindGroupLayout(device = device, keys = cacheKeys)
    val pipelineLayout = executionCaches.coverageComputePipelineLayout(device = device, bindGroupLayout = bindGroupLayout, keys = cacheKeys)

    val pipeline = device.createComputePipeline(
        GPUComputePipelineDescriptor(
            layout = pipelineLayout,
            compute = GPUProgrammableStage(
                module = shader,
                entryPoint = "main",
            ),
        ),
    )

    val uniformBuffer = resourceScope.track(
        device.createBuffer(
            BufferDescriptor(
                size = uniforms.size.toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                label = "coverageStroke.uniform",
            ),
        ),
    ) { it.close() }
    queue.writeBuffer(uniformBuffer, 0uL, ArrayBuffer.of(uniforms))

    val covTex = offscreenTextureStore[coverageTexture]
        ?: error("Coverage texture not found: $coverageTexture")
    val covView = resourceScope.track(covTex.createView()) { it.close() }

    val bindGroup = device.createBindGroup(
        GPUBindGroupDescriptor(
            layout = bindGroupLayout,
            entries = listOf(
                GPUBindGroupEntry(0u, GPUBindingResource.Buffer(GPUBufferBinding(uniformBuffer))),
                GPUBindGroupEntry(1u, GPUBindingResource.TextureView(covView)),
            ),
        ),
    )

    // Begin compute pass on the current command encoder
    val computePass = computeEncoder ?: error("No compute encoder active")
    computePass.setPipeline(pipeline)
    computePass.setBindGroup(0u, bindGroup)
    computePass.dispatchWorkgroups(workgroupCountX.toUInt(), workgroupCountY.toUInt(), 1u)
}
```

**Note:** The `computeEncoder` field needs to be added to `WgpuRenderRecorder` or the compute pass needs to be created before the render pass in `encode()`. Since compute passes must be outside render passes, the architecture needs adjustment.

**Architecture adjustment:** The compute pass runs BEFORE the render pass in `encode()`. Add a `computeEncoder` field to `WgpuRenderRecorder` that's created in the `encode()` method.

- [ ] **Step 3: Implement coverage compute pipeline caches**

In `WgpuExecutionCaches` (same file), add:

```kotlin
fun coverageComputeBindGroupLayout(device: GPUDevice, keys: FullscreenExecutionCacheKeys): GPUBindGroupLayout {
    return bindGroupLayoutCache.getOrCreate(keys.bindGroupLayoutKeyHash, keys.bindGroupLayoutSubjectHash) {
        device.createBindGroupLayout(
            GPUBindGroupLayoutDescriptor(
                entries = listOf(
                    GPUBindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Compute,
                        buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.Uniform),
                    ),
                    GPUBindGroupLayoutEntry(
                        binding = 1u,
                        visibility = GPUShaderStage.Compute,
                        storageTexture = GPUStorageTextureBindingLayout(
                            format = GPUTextureFormat.R8Unorm,
                            access = GPUStorageTextureAccess.WriteOnly,
                        ),
                    ),
                ),
            ),
        )
    }
}
```

- [ ] **Step 4: Add compute encoder support in encode()**

In `WgpuOffscreenTarget.encode()`, BEFORE the render pass, create a compute pass:

```kotlin
// Compute pass (before render pass, if needed)
val computePassEncoder = if (needsComputePass) {
    val cp = encoder.beginComputePass(null)
    computeEncoder = cp  // store so recorder can use it
    cp
} else null
```

This requires adding a `computeEncoder` field to `WgpuRenderRecorder` and setting it. For the POC, we need a flag or just always create the compute pass.

**Simpler POC approach:** For the POC, skip the recorder-level compute pass and instead add a method to `WgpuRenderRecorder` that does everything inline. The compute pass is created in the recorder's method, not in `encode()`.

```kotlin
override fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTexture: String,
    workgroupCountX: Int,
    workgroupCountY: Int,
) {
    // Create compute pipeline, bind group, dispatch
    val covTex = offscreenTextureStore[coverageTexture]!!

    val computeEncoder = commandEncoder.beginComputePass(null)
    computeEncoder.setPipeline(computePipeline)
    computeEncoder.setBindGroup(0u, bindGroup)
    computeEncoder.dispatchWorkgroups(workgroupCountX.toUInt(), workgroupCountY.toUInt(), 1u)
    computeEncoder.end()
}
```

**But:** This creates a compute pass inside the render pass recorder, which is invalid (can't have compute pass inside render pass). The recorder is called from within a `beginRenderPass` block.

**Final architecture decision:** The coverage stroke dispatch happens OUTSIDE the render pass, BEFORE the encode call. Add a new method to `GPUBackendOffscreenTarget` that handles the compute pass separately.

- [ ] **Step 5: Add recordCoverageStroke to GPUBackendOffscreenTarget**

In `GPUBackendRuntimeContracts.kt`, add to `GPUBackendOffscreenTarget`:

```kotlin
fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTextureLabel: String,
    workgroupCountX: Int,
    workgroupCountY: Int,
)
```

Implement in `WgpuOffscreenTarget`:

```kotlin
override fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTextureLabel: String,
    workgroupCountX: Int,
    workgroupCountY: Int,
) {
    val covTex = offscreenTextures[coverageTextureLabel]
        ?: error("Coverage texture not found: $coverageTextureLabel")

    GPUResourceScope().use { resources ->
        val shader = resources.track(device.createShaderModule(
            GPUShaderModuleDescriptor(code = wgsl)
        )) { it.close() }

        val bindGroupLayout = device.createBindGroupLayout(
            GPUBindGroupLayoutDescriptor(
                entries = listOf(
                    GPUBindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Compute,
                        buffer = GPUBufferBindingLayout(type = GPUBufferBindingType.Uniform),
                    ),
                    GPUBindGroupLayoutEntry(
                        binding = 1u,
                        visibility = GPUShaderStage.Compute,
                        storageTexture = GPUStorageTextureBindingLayout(
                            format = GPUTextureFormat.R8Unorm,
                            access = GPUStorageTextureAccess.WriteOnly,
                        ),
                    ),
                ),
            ),
        )

        val pipelineLayout = device.createPipelineLayout(
            GPUPipelineLayoutDescriptor(
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )

        val pipeline = device.createComputePipeline(
            GPUComputePipelineDescriptor(
                layout = pipelineLayout,
                compute = GPUProgrammableStage(
                    module = shader,
                    entryPoint = "main",
                ),
            ),
        )

        val uniformBuffer = resources.track(
            device.createBuffer(
                BufferDescriptor(
                    size = uniforms.size.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(uniformBuffer, 0uL, ArrayBuffer.of(uniforms))

        val covView = resources.track(covTex.createView()) { it.close() }

        val bindGroup = device.createBindGroup(
            GPUBindGroupDescriptor(
                layout = bindGroupLayout,
                entries = listOf(
                    GPUBindGroupEntry(0u, GPUBindingResource.Buffer(GPUBufferBinding(uniformBuffer))),
                    GPUBindGroupEntry(1u, GPUBindingResource.TextureView(covView)),
                ),
            ),
        )

        val encoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
        val computePass = encoder.beginComputePass(null)
        computePass.setPipeline(pipeline)
        computePass.setBindGroup(0u, bindGroup)
        computePass.dispatchWorkgroups(workgroupCountX.toUInt(), workgroupCountY.toUInt(), 1u)
        computePass.end()
        val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
        queue.submit(listOf(commandBuffer))
    }
}
```

- [ ] **Step 6: Add recordCoverageFill to the render recorder**

In `WgpuRenderRecorder`, add a method that renders a fullscreen quad sampling the coverage texture. This runs inside an existing render pass:

```kotlin
override fun recordCoverageFill(
    wgsl: String,
    colorUniforms: ByteArray,
    coverageTexture: String,
    blendMode: GPUBlendMode?,
) {
    val covTex = offscreenTextureStore[coverageTexture]!!
    val sampler = device.createSampler(GPUSamplerDescriptor())

    // Standard fullscreen pipeline with texture + sampler bindings
    val cacheKeys = fullscreenTextureExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, textureFormat = GPUTextureFormat.R8Unorm, blendMode = blendMode, sampleCount = sampleCount)
    // ... create pipeline, bind group, draw fullscreen quad
}
```

**Simplification for POC:** Reuse the existing `drawFullscreenPass` infrastructure. The `COPY_WGSL` shader already samples a texture. Create a variant that also applies color modulation.

Actually, the SIMPLEST approach for the POC: don't create a separate fill pass. Instead, after the compute shader writes coverage, use the existing composite pass (`drawCompositePass` with `COPY_WGSL`) but with a custom shader that multiplies coverage by color. Or even simpler: just use the existing stencil-cover TEST pass but replace `SOLID_RECT_WGSL` with `COVERAGE_FILL_WGSL`.

**Wait — for the POC we're replacing stencil-cover entirely.** So we need a way to render the coverage texture with color. The simplest approach: use the existing `drawFullscreenPass` infrastructure but with a new shader that samples coverage and applies color.

Let me redesign Task 2 to use this simpler approach.

### Task 2 v2: Minimal compute + fill integration

**Files:**
- Modify: `gpu-renderer/.../GPUBackendRuntimeContracts.kt` — add `recordCoverageStroke` to `GPUBackendOffscreenTarget`, add `recordCoverageFill` to `GPUBackendRenderRecorder`
- Modify: `gpu-renderer/.../GPUBackendRuntimeWgpu.kt` — implement both

- [ ] **Step 1: Add contract methods**

In `GPUBackendRuntimeContracts.kt`, add to `GPUBackendOffscreenTarget`:

```kotlin
fun createCoverageTexture(width: Int, height: Int): String
fun recordCoverageStroke(
    wgsl: String,
    uniforms: ByteArray,
    coverageTextureLabel: String,
    workgroupCountX: Int,
    workgroupCountY: Int,
)
```

In `GPUBackendRenderRecorder`, add:

```kotlin
fun recordCoverageFill(
    wgsl: String,
    colorUniforms: ByteArray,
    coverageTextureLabel: String,
)
```

- [ ] **Step 2: Implement createCoverageTexture in WgpuOffscreenTarget**

```kotlin
override fun createCoverageTexture(width: Int, height: Int): String {
    val label = "coverageTex:${width}x${height}:r8"
    if (label in offscreenTextures) return label
    val tex = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
            format = GPUTextureFormat.R8Unorm,
            usage = GPUTextureUsage.StorageBinding or GPUTextureUsage.TextureBinding,
            label = label,
        ),
    )
    offscreenTextures[label] = tex
    return label
}
```

- [ ] **Step 3: Implement recordCoverageStroke (compute pass)**

Paste the full implementation from Task 2 Step 5 above.

- [ ] **Step 4: Implement recordCoverageFill (render pass)**

In `WgpuRenderRecorder`:

```kotlin
override fun recordCoverageFill(
    wgsl: String,
    colorUniforms: ByteArray,
    coverageTextureLabel: String,
) {
    val covTex = offscreenTextureStore[coverageTextureLabel]!!
    val covView = resourceScope.track(covTex.createView()) { it.close() }
    val sampler = resourceScope.track(
        device.createSampler(GPUSamplerDescriptor())
    ) { it.close() }

    val targetFmt = targetFormat
    val cacheKeys = fullscreenTextureExecutionCacheKeys(
        wgsl = wgsl, targetFormat = targetFmt,
        textureFormat = GPUTextureFormat.R8Unorm,
        blendMode = GPUBlendMode.SRC_OVER, sampleCount = sampleCount,
    )

    val shader = executionCaches.shaderModule(device, wgsl, cacheKeys)
    val bindGroupLayout = executionCaches.textureBindGroupLayout(device, cacheKeys)
    val pipelineLayout = executionCaches.pipelineLayout(device, bindGroupLayout, cacheKeys)
    val pipeline = executionCaches.renderPipeline(device, shader, pipelineLayout, targetFmt, cacheKeys, GPUBlendMode.SRC_OVER, sampleCount)

    val colorBuf = resourceScope.track(
        device.createBuffer(
            BufferDescriptor(size = colorUniforms.size.toULong(), usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst)
        ),
    ) { it.close() }
    queue.writeBuffer(colorBuf, 0uL, ArrayBuffer.of(colorUniforms))

    val bindGroup = device.createBindGroup(
        GPUBindGroupDescriptor(
            layout = bindGroupLayout,
            entries = listOf(
                GPUBindGroupEntry(0u, GPUBindingResource.Buffer(GPUBufferBinding(colorBuf))),
                GPUBindGroupEntry(1u, GPUBindingResource.TextureView(covView)),
                GPUBindGroupEntry(2u, GPUBindingResource.Sampler(sampler)),
            ),
        ),
    )

    setPipelineAction(pipeline)
    setBindGroupAction(0u, bindGroup)
    drawAction(6u)
}
```

- [ ] **Step 5: Compile**

```bash
./gradlew :gpu-renderer:compileKotlin --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
        gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt
git commit -m "feat: compute pipeline + coverage fill support"
```

---

### Task 3: dispatchCoverageStroke in GPUDispatchPath.kt

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchPath.kt` — add stroke coverage branch

- [ ] **Step 1: Add coverage dispatch branch**

In `dispatchFillPath`, in the `SolidColor` case, replace the existing stroke AA branch with coverage dispatch:

In the SolidColor when block (around line 122), the STROKE_AA_WGSL branch should become:

```kotlin
if (cmd.stroke && cmd.tessellatedVertices.size >= 4) {
    // Coverage compute shader AA for strokes
    val p0x = cmd.tessellatedVertices[0]
    val p0y = cmd.tessellatedVertices[1]
    val p1x = cmd.tessellatedVertices[2]
    val p1y = cmd.tessellatedVertices[3]
    val halfW = cmd.strokeWidth / 2f
    val aaW = 2.0f

    // Compute coverage texture bounds (inflated by aaW for AA zone)
    val ox = halfW + aaW + 4f  // extra padding
    val minX = (minOf(p0x, p1x) - ox).toInt().coerceAtLeast(0)
    val minY = (minOf(p0y, p1y) - ox).toInt().coerceAtLeast(0)
    val maxX = (maxOf(p0x, p1x) + ox).toInt().coerceAtMost(surfaceWidth)
    val maxY = (maxOf(p0y, p1y) + ox).toInt().coerceAtMost(surfaceHeight)
    val covW = maxX - minX
    val covH = maxY - minY

    // Create coverage texture (R8)
    val covLabel = t.createCoverageTexture(covW, covH)

    // Pack compute uniforms: p0, p1 (in coverage-local coords), halfW, aaW
    val compUniforms = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.nativeOrder())
    compUniforms.putFloat(p0x - minX); compUniforms.putFloat(p0y - minY)
    compUniforms.putFloat(p1x - minX); compUniforms.putFloat(p1y - minY)
    compUniforms.putFloat(halfW)
    compUniforms.putFloat(aaW)

    // Dispatch compute shader → writes coverage to texture
    val wgX = (covW + 7) / 8
    val wgY = (covH + 7) / 8
    t.recordCoverageStroke(COVERAGE_STROKE_WGSL, compUniforms.array(), covLabel, wgX, wgY)

    // Pack color uniforms
    val colorBb = java.nio.ByteBuffer.allocate(16).order(java.nio.ByteOrder.nativeOrder())
    colorBb.putFloat(srgbToLinear(material.r) * material.a)
    colorBb.putFloat(srgbToLinear(material.g) * material.a)
    colorBb.putFloat(srgbToLinear(material.b) * material.a)
    colorBb.putFloat(material.a)

    // Render coverage fill (inside the encodeOffscreenTexture render pass)
    recordCoverageFill(COVERAGE_FILL_WGSL, colorBb.array(), covLabel)
}
```

**Note:** The code above uses `t.recordCoverageStroke(...)` which is called on the `WgpuOffscreenTarget` (outside the render pass). But `recordCoverageFill` is called on the recorder (inside the render pass). The dispatch function `dispatchFillPath` receives a `GPUBackendRenderRecorder`, not the `WgpuOffscreenTarget`.

**Architecture fix:** Pass the `WgpuOffscreenTarget` (or `GPUBackendOffscreenTarget`) to `dispatchFillPath` so it can call `recordCoverageStroke`.

Looking at the current signature:
```kotlin
internal fun GPUBackendRenderRecorder.dispatchFillPath(
    cmd: NormalizedDrawCommand.FillPath,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
)
```

I need to add the target parameter:
```kotlin
internal fun GPUBackendRenderRecorder.dispatchFillPath(
    cmd: NormalizedDrawCommand.FillPath,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
    target: GPUBackendOffscreenTarget,  // NEW
)
```

And update all call sites in `GPURenderer.kt` to pass `t` (the target).

- [ ] **Step 2: Add target parameter to dispatchFillPath**

In `GPUDispatchPath.kt`, change function signature:

```kotlin
internal fun GPUBackendRenderRecorder.dispatchFillPath(
    cmd: NormalizedDrawCommand.FillPath,
    dispatched: MutableList<String>,
    diagnostics: Diagnostics,
    surfaceWidth: Int,
    surfaceHeight: Int,
    config: RenderConfig,
    target: GPUBackendOffscreenTarget,
)
```

- [ ] **Step 3: Update all call sites in GPURenderer.kt**

Search for `dispatchFillPath(cmd,` in GPURenderer.kt and add `, t` to each call.

- [ ] **Step 4: Implement coverage branch in dispatchFillPath**

Replace the STROKE_AA_WGSL block (around line 123) with the coverage compute branch from Step 1 above.

- [ ] **Step 5: Compile**

```bash
./gradlew :kanvas:compileKotlin --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUDispatchPath.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "feat: coverage compute AA dispatch for strokes"
```

---

### Task 4: End-to-end test

- [ ] **Step 1: Render ctmpatheffect with coverage AA**

```bash
rm -rf integration-tests/skia/build
./gradlew :integration-tests:skia:renderCtmp --no-daemon --no-build-cache --rerun-tasks
```

- [ ] **Step 2: Check pixel quality**

```bash
python3 -c "
from PIL import Image
gen = Image.open('integration-tests/skia/src/test/resources/generated-renders/path/ctmpatheffect.png').convert('RGBA')
ref = Image.open('integration-tests/skia/src/test/resources/reference/ctmpatheffect.png').convert('RGBA')
total = ref.width * ref.height
match = sum(1 for y in range(ref.height) for x in range(ref.width) if ref.getpixel((x,y))[:3] == gen.getpixel((x,y))[:3])
print(f'Similarity: {100*match/total:.2f}%')
print(f'(145,145): gen={gen.getpixel((145,145))} ref={ref.getpixel((145,145))}')
"
```

Expected: similarity > 98.5%, visible anti-aliased edges on green stroke.

- [ ] **Step 3: Commit updated render**

```bash
git add integration-tests/skia/src/test/resources/generated-renders/path/ctmpatheffect.png
git commit -m "test: update ctmpatheffect render with coverage compute AA"
```
