# MSAA Stencil-Cover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 4x MSAA to the scene layer texture so stencil-cover strokes get hardware multisample anti-aliased edges instead of binary ON/OFF coverage.

**Architecture:** Scene layer texture becomes multisampled (4x). A 1x resolve texture stores the resolved result. After all draws complete, one resolve pass resolves the MSAA scene. Composite remains single-sample. `RenderConfig.sampleCount` defaults to 1 (off), change to 4 for MSAA.

**Tech Stack:** Kotlin, wgpu4k WebGPU bindings, WGSL shaders unchanged (stencil-cover shaders stay `texture_2d`, no `texture_multisampled_2d` needed)

**Key design decision:** Resolve happens via an empty render pass with `resolveTarget`. The composite pass reads the RESOLVED (1x) texture, avoiding multisampled shader reads.

---

### Task 1: Add `sampleCount` to `RenderConfig` + `GPUOffscreenTargetRequest`

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt:3-10,15-33`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt:9-19`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:63-69`

- [ ] **Step 1: Add `sampleCount` to RenderConfig**

In `RenderConfig.kt`, replace the data class declaration (line 3) with:

```kotlin
data class RenderConfig(
    val gpuColorFormat: GPUColorFormat = GPUColorFormat.RGBA8_UNORM_SRGB,
    val sampleCount: Int = 1,
    val maxPathVertices: UInt = 131072u,
    val curveTolerance: Float = 0.25f,
    val maxImagePixels: UInt = 67_108_864u,
    val diagnosticLevel: DiagnosticLevel = DiagnosticLevel.WARN,
    val debugLevel: DebugLevel = DebugLevel.OFF,
) {
    init {
        require(sampleCount in setOf(1, 4)) { "sampleCount must be 1 or 4, got $sampleCount" }
    }
```

In `fromEnvironment()` (line 15), add after `gpuColorFormat` parsing:

```kotlin
sampleCount = p.getProperty("kanvas.render.sampleCount")?.toIntOrNull() ?: DEFAULT.sampleCount,
```

- [ ] **Step 2: Add `sampleCount` to `GPUOffscreenTargetRequest`**

In `GPUBackendRuntimeContracts.kt` line 9:

```kotlin
data class GPUOffscreenTargetRequest(
    val width: Int,
    val height: Int,
    val colorFormat: String = "rgba8unorm",
    val sampleCount: Int = 1,
) {
    init {
        require(width > 0) { "GPUOffscreenTargetRequest.width must be positive" }
        require(height > 0) { "GPUOffscreenTargetRequest.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUOffscreenTargetRequest.colorFormat must not be blank" }
        require(sampleCount >= 1) { "GPUOffscreenTargetRequest.sampleCount must be >= 1" }
    }
}
```

- [ ] **Step 3: Wire `sampleCount` in `renderViaGpu`**

In `GPURenderer.kt` around line 63, find `s.createOffscreenTarget(GPUOffscreenTargetRequest(...))` and add `sampleCount`:

```kotlin
val target = s.createOffscreenTarget(
    GPUOffscreenTargetRequest(
        width = width,
        height = height,
        colorFormat = config.gpuColorFormat.wgpuLabel,
        sampleCount = config.sampleCount,
    ),
)
```

- [ ] **Step 4: Compile**

```bash
./gradlew :kanvas:compileKotlin :gpu-renderer:compileKotlin --no-daemon
```

- [ ] **Step 5: Commit**

```bash
git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt \
        gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "feat: add sampleCount to RenderConfig and GPUOffscreenTargetRequest"
```

---

### Task 2: MSAA textures in `WgpuOffscreenTarget`

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt:255-318`

- [ ] **Step 1: Add `sampleCount` field and MSAA textures**

After line 267 (`private val safeHeight = ...`), add:

```kotlin
private val sampleCount = request.sampleCount
```

Replace the `texture` creation (lines 273-280):

```kotlin
private val texture = device.createTexture(
    TextureDescriptor(
        size = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
        format = format,
        usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
        sampleCount = sampleCount,
        label = "GPUBackend.offscreen.color",
    ),
)
```

Replace `depthStencilTexture` (lines 281-288):

```kotlin
private val depthStencilTexture = device.createTexture(
    TextureDescriptor(
        size = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
        format = GPUTextureFormat.Depth24PlusStencil8,
        usage = GPUTextureUsage.RenderAttachment,
        sampleCount = sampleCount,
        label = "GPUBackend.offscreen.depthStencil",
    ),
)
```

**BUT**: WebGPU does not allow `CopySrc` on multisampled textures. When `sampleCount > 1`, the main texture cannot be used as `CopySrc`. The `readRgba()` reads from this texture via `copyTextureToBuffer`, which requires `CopySrc`. 

**Fix**: When `sampleCount > 1`, create a separate 1x resolve texture for readback, and resolve into it before reading.

Add a resolve texture (after line 280):

```kotlin
private val resolveTexture = if (sampleCount > 1) {
    device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
            format = format,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            sampleCount = 1,
            label = "GPUBackend.offscreen.color.resolve",
        ),
    )
} else null
```

- [ ] **Step 2: Add resolve before readback in `encode()`**

In `encode()` (line 320), after `end()` and before `copyTextureToBuffer`, add resolve:

```kotlin
end()
// Resolve MSAA before copy to staging buffer
if (resolveTexture != null) {
    val resolveView = resources.track(resolveTexture.createView()) { it.close() }
    val resolveEncoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
    resolveEncoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = view,  // MSAA color attachment
                    resolveTarget = resolveView,
                    loadOp = GPULoadOp.Load,
                    storeOp = GPUStoreOp.Store,
                ),
            ),
        ),
    ) {
        end()  // empty pass triggers resolve
    }
    val resolveCommandBuffer = resources.trackIfAutoCloseable(resolveEncoder.finish())
    queue.submit(listOf(resolveCommandBuffer))
}
```

And change `copyTextureToBuffer` source to use resolve texture when MSAA:

```kotlin
encoder.copyTextureToBuffer(
    source = TexelCopyTextureInfo(texture = resolveTexture ?: texture),
    ...
)
```

- [ ] **Step 3: Compile**

```bash
./gradlew :gpu-renderer:compileKotlin --no-daemon
```

- [ ] **Step 4: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt
git commit -m "feat: MSAA textures + resolve in WgpuOffscreenTarget"
```

---

### Task 3: Multisample state in render pipelines + cache keys

**Files:**
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
  - Lines 3092, 3175, 3248, 3328, 3411, 3486 (cache key `sampleStateHash`)
  - ~7 `RenderPipelineDescriptor` creation sites (search `RenderPipelineDescriptor(`)

- [ ] **Step 1: Update cache key preimages**

Replace all 6 instances of:
```kotlin
sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
```

With a computed value. At each cache key site, the `sampleCount` must be accessible. Add it as a parameter to the cache key computation functions.

Simplest approach: pass `sampleCount` as a constructor parameter to `WgpuRenderRecorder`, then the cache functions access `recorder.sampleCount`:

In `WgpuRenderRecorder` class (search `class WgpuRenderRecorder`), add:

```kotlin
val sampleCount: Int = 1,
```

Then at each cache key site, replace the hardcoded string with:

```kotlin
sampleStateHash = stableSha256("sample-state:count=$sampleCount:mask=all"),
```

Where `sampleCount` comes from the recorder.

- [ ] **Step 2: Add `multisample` to all `RenderPipelineDescriptor` constructions**

Search for all `RenderPipelineDescriptor(` in the file. After each `primitive = PrimitiveState(),` line, add:

```kotlin
multisample = io.ygdrasil.webgpu.MultisampleState(
    count = sampleCount.toUInt(),
    mask = 0xFFFFFFFFu,
    alphaToCoverageEnabled = false,
),
```

**Note**: The exact class name for multisample state in wgpu4k might be `MultisampleState`, check the import if needed.

- [ ] **Step 3: Pass `sampleCount` to all `WgpuRenderRecorder` construction sites**

Find all `WgpuRenderRecorder(` constructor calls (search for the class name), and add `sampleCount = sampleCount,` to each.

- [ ] **Step 4: Compile**

```bash
./gradlew :gpu-renderer:compileKotlin --no-daemon
```

- [ ] **Step 5: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt
git commit -m "feat: multisample state in pipelines, cache keys, recorder"
```

---

### Task 4: Scene layer MSAA resolve after draws

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:71-78, ~1170` (scene texture creation, composite pass)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt` (add `resolveOffscreenTexture`)
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt` (implement resolve)

- [ ] **Step 1: Create resolved companion texture for scene**

In `renderViaGpu` (GPURenderer.kt), after `sceneLabel` creation (~line 73):

```kotlin
val sceneLabel = t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
val sceneResolvedLabel = if (config.sampleCount > 1) {
    t.createOffscreenTexture(GPUBackendOffscreenTexture(width, height, texFormat))
} else sceneLabel
```

- [ ] **Step 2: Add `resolveOffscreenTexture` contract**

In `GPUBackendRuntimeContracts.kt`, find `GPUBackendOffscreenTarget` interface and add:

```kotlin
fun resolveOffscreenTexture(msaaLabel: String, resolvedLabel: String)
```

- [ ] **Step 3: Implement `resolveOffscreenTexture` in wgpu backend**

In `GPUBackendRuntimeWgpu.kt`, in the `WgpuOffscreenTarget` class, add:

```kotlin
override fun resolveOffscreenTexture(msaaLabel: String, resolvedLabel: String) {
    val msaaTex = offscreenTexture(msaaLabel)
    val resolvedTex = offscreenTexture(resolvedLabel)
    GPUResourceScope().use { resources ->
        val msaaView = resources.track(msaaTex.createView()) { it.close() }
        val resolvedView = resources.track(resolvedTex.createView()) { it.close() }
        val encoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = msaaView,
                        resolveTarget = resolvedView,
                        loadOp = GPULoadOp.Load,
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        ) {
            end()  // empty pass triggers hardware MSAA resolve
        }
        val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
        queue.submit(listOf(commandBuffer))
    }
}
```

- [ ] **Step 4: Wire resolve in `renderViaGpu`**

After the main draw loop and before compositing (~line 1170 in GPURenderer.kt):

```kotlin
// Resolve MSAA scene before compositing
if (config.sampleCount > 1 && sceneHasContent) {
    t.resolveOffscreenTexture(sceneLabel, sceneResolvedLabel)
}
val compositeSource = if (config.sampleCount > 1) sceneResolvedLabel else sceneLabel
```

Update the composite pass (~line 1173) to use `compositeSource` instead of `sceneLabel`.

- [ ] **Step 5: Compile**

```bash
./gradlew :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt \
        gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeContracts.kt \
        kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt
git commit -m "feat: scene layer MSAA resolve after draws, before composite"
```

---

### Task 5: End-to-end test

- [ ] **Step 1: Full rebuild with MSAA enabled**

```bash
rm -rf kanvas/build gpu-renderer/build integration-tests/skia/build
./gradlew :integration-tests:skia:generateSkiaRenders --no-daemon --no-build-cache -Dkanvas.render.sampleCount=4
```

- [ ] **Step 2: Compare stroke GM scores**

Check that stroke-heavy GMs improved significantly vs their previous scores (see spec for baselines).

- [ ] **Step 3: Verify backward compatibility (sampleCount=1)**

```bash
./gradlew :integration-tests:skia:generateSkiaRenders --no-daemon --no-build-cache -Dkanvas.render.sampleCount=1
```

Expected: identical renders as before MSAA changes.

- [ ] **Step 4: Commit updated renders and scores**

```bash
git add integration-tests/skia/src/test/resources/generated-renders/
git commit -m "test: regenerate renders with MSAA 4x"
```
