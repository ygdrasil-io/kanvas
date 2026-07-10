# SaveLayer Variants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote bounded layers, supported composite paint, and bounded backdrop blur in the single WebGPU `saveLayer` dispatcher without weakening stable refusals.

**Architecture:** `GPURenderer` classifies every `SaveLayerRec` into an immutable layer plan before pushing a frame. The frame carries parent/child targets and the restore-time bounds, opacity, blend, and optional backdrop state. All draw helpers remain unaware of layer variants; only the `BeginLayer`/`EndLayer` section allocates, clips, filters, and composites.

**Tech Stack:** Kotlin, Kanvas DisplayList/Surface APIs, WebGPU runtime contracts, WGSL, JUnit 5, Gradle, Skia GM dashboard.

## Global Constraints

- Keep WebGPU as the GPU backend; do not port Ganesh or Graphite and do not add a CPU fallback.
- Preserve one semantic pipeline and explicit, stable unsupported diagnostics.
- Support is claimed only with red/green tests plus reference or CPU/GPU evidence; do not lower any similarity threshold.
- Implement variants in one `BeginLayer`/`EndLayer` dispatcher: `bounds`, then `paint`, then `backdrop`.
- Keep picture replay with embedded layer operations refused until picture replay is explicitly layer-aware.
- The first bounds promotion uses a full-surface texture plus device scissor; physical bounded textures are out of scope.
- First backdrop promotion is finite-bounds registered blur only. Other DAGs, crop/tile variants, runtime effects, and unsupported paint combinations stay refused.
- Every production change starts with a focused failing test and records its red command before the minimal green implementation.

---

### Task 1: Add a layer-plan boundary and promote finite bounds

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:109-113,530-590`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt`

**Interfaces:**
- Produces: private file-level `LayerPlan`, `LayerBounds`, `BackdropPlan`, and `SceneTargetFrame(label, hasContent, plan)` data types; their only behavioral use remains in `renderViaGpu`.
- Produces: `classifyLayerRequest(rec: SaveLayerRec): LayerPlan` local to `renderViaGpu`.
- Consumes: `GPUBackendRawUniformDraw.scissorX/scissorY/scissorWidth/scissorHeight` and `drawCompositePass`.

- [ ] **Step 1: Write the failing bounded-layer tests**

```kotlin
@Test
fun `bounded saveLayer clips child and composite to device bounds`() {
    val result = renderSurface(8, 8) {
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
        saveLayer(Rect(2f, 2f, 6f, 6f))
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
        restore()
    }
    assertPixelNear(result.pixels, 1, 1, white, 0)
    assertPixelNear(result.pixels, 3, 3, sourceOver(white, translucentRed), 2)
    assertEquals(0, result.diagnostics.fatalCount)
}

@Test
fun `empty bounded saveLayer leaves parent untouched`() {
    val result = renderSurface(8, 8) {
        drawCheckerboardRoot()
        saveLayer(Rect(20f, 20f, 21f, 21f))
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = translucentRed.toColor(), antiAlias = false))
        restore()
    }
    assertCheckerboard(result.pixels)
    assertEquals(0, result.diagnostics.fatalCount)
}
```

- [ ] **Step 2: Prove red**

Run:

```bash
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
```

Expected: the bounded case fails because current `BeginLayer` returns `unsupported.layer.bounds`; the empty case fails for the same reason.

- [ ] **Step 3: Implement a single classification and scissor route**

Add the following private file-level model next to the renderer helpers:

```kotlin
data class LayerBounds(val x: Int, val y: Int, val width: Int, val height: Int)
sealed interface LayerPlan {
    data class Supported(val bounds: LayerBounds?, val composite: LayerCompositePlan) : LayerPlan
    data class Refused(val reason: String) : LayerPlan
}
data class LayerCompositePlan(
    val opacity: Float = 1f,
    val blendMode: GPUBlendMode = GPUBlendMode.SRC_OVER,
    val backdrop: BackdropPlan? = null,
)
data class BackdropPlan(
    val sourceLabel: String,
    val filteredLabel: String,
    val bounds: LayerBounds,
)
```

Implement `classifyLayerRequest` so `rec.bounds` is mapped to finite device coordinates, intersected with `0 until width` and `0 until height`, and returned as `Supported(bounds, LayerCompositePlan())`. Return `Refused("unsupported.layer.bounds.non_finite")` for non-finite inputs. Replace the existing `bounds != null` refusal with this classifier.

Add a `layerScissor(plan)` helper that returns the plan bounds or the full surface. Use it for the child render target's composite draw and for the child-to-parent `drawCompositePass` at `EndLayer`. Keep the texture full surface; do not change geometry coordinate systems.

- [ ] **Step 4: Prove green and regressions**

Run:

```bash
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesRegressionTest --tests org.graphiks.kanvas.skia.GmCanvasCompatibilityTest --no-daemon
```

Expected: bounded and ordinary layer fixtures pass; AAX and compatibility tests remain green.

- [ ] **Step 5: Commit Task 1**

```bash
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt
rtk git commit -m "feat: support bounded GPU saveLayers"
```

### Task 2: Promote opacity and fixed-function layer composite paint

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:109-113,530-590`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt`

**Interfaces:**
- Consumes: `LayerCompositePlan(opacity, blendMode, backdrop)` from Task 1.
- Produces: `LAYER_COMPOSITE_WGSL` with one `f32 opacity` uniform and premultiplied source output.
- Produces: `classifyLayerPaint(paint: Paint?): LayerPlan` local to `renderViaGpu`.

- [ ] **Step 1: Write failing opacity and blend tests**

```kotlin
@Test
fun `saveLayer paint alpha is applied once at restore`() {
    val result = renderSurface(8, 8) {
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = white.toColor(), antiAlias = false))
        saveLayer(paint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 0.5f)))
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = red.toColor(), antiAlias = false))
        restore()
    }
    assertPixelNear(result.pixels, 3, 3, sourceOver(white, halfAlpha(red)), 2)
}

@Test
fun `layer paint image filter refuses without modifying parent`() {
    val result = renderSurface(8, 8) {
        drawCheckerboardRoot()
        saveLayer(paint = Paint(imageFilter = ImageFilter.Blur(2f, 2f)))
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = red.toColor(), antiAlias = false))
        restore()
    }
    assertCheckerboard(result.pixels)
    assertFatalReason(result, "unsupported.layer.paint.image_filter")
}
```

- [ ] **Step 2: Prove red**

Run the Task 1 focused command. Expected: alpha fixture is suppressed by `unsupported.layer.paint`; the field-specific diagnostic assertion fails.

- [ ] **Step 3: Implement the minimal composite shader and classifier**

Add a distinct shader in `GPUWgsl.kt`:

```wgsl
struct Uniforms { opacity: f32, _pad: vec3f };
@fragment fn fsMain(in: VertexOutput) -> @location(0) vec4f {
    let color = textureSample(srcTexture, srcSampler, in.uv);
    return vec4f(color.rgb * uniforms.opacity, color.a * uniforms.opacity);
}
```

In `classifyLayerRequest`, accept a layer paint only when it has no shader/filter/blender/path/stroke fields and its blend maps to an explicitly tested fixed-function `GPUBlendMode`. Populate `LayerCompositePlan(opacity = paint.color.alpha, blendMode = mappedMode)`. Reject each unsupported field with its own `unsupported.layer.paint.<field>` reason.

At `EndLayer`, encode the `opacity` float in a 16-byte uniform block, select `LAYER_COMPOSITE_WGSL` when opacity differs from 1, and pass the plan's blend mode to `drawCompositePass`. Do not multiply opacity in any other pass.

- [ ] **Step 4: Prove green and fixed-function regression**

Run:

```bash
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesRegressionTest --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesPortParityTest --no-daemon
```

Expected: opacity is applied once, field-specific refusal preserves the parent, and existing ordinary-layer/AAX behavior remains green.

- [ ] **Step 5: Commit Task 2**

```bash
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUWgsl.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt
rtk git commit -m "feat: composite GPU saveLayer paint"
```

### Task 3: Promote finite bounded backdrop blur

**Files:**
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUBackdropFilterDispatch.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt:109-113,505-520,530-590`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt`

**Interfaces:**
- Consumes: `BackdropPlan(sourceLabel, filteredLabel, bounds)` from Task 1.
- Produces: `GPUBackendOffscreenTarget.renderBackdropBlur(sourceLabel: String, destinationLabel: String, blur: ImageFilter.Blur, bounds: LayerBounds, colorFormat: String, diagnostics: Diagnostics): Boolean`.
- Consumes: `LayerCompositePlan.backdrop` at `EndLayer` and the registered blur route already used by `GPUImageFilterDispatch.kt`.

- [ ] **Step 1: Write failing backdrop tests**

```kotlin
@Test
fun `bounded backdrop blur changes only the layer region`() {
    val result = renderSurface(16, 16) {
        drawCheckerboardRoot()
        saveLayer(SaveLayerRec(
            bounds = Rect(4f, 4f, 12f, 12f),
            backdrop = ImageFilter.Blur(2f, 2f),
        ))
        restore()
    }
    assertPixelNear(result.pixels, 1, 1, checkerboardColorAt(1, 1), 0)
    assertNotEquals(checkerboardColorAt(6, 6), pixelAt(result.pixels, 6, 6))
}

@Test
fun `backdrop crop filter is refused without changing parent`() {
    val result = renderSurface(8, 8) {
        drawCheckerboardRoot()
        saveLayer(SaveLayerRec(backdrop = ImageFilter.Crop(Rect(1f, 1f, 7f, 7f))))
        drawRect(Rect(0f, 0f, 8f, 8f), Paint(color = red.toColor(), antiAlias = false))
        restore()
    }
    assertCheckerboard(result.pixels)
    assertFatalReason(result, "unsupported.layer.backdrop.crop")
}
```

- [ ] **Step 2: Prove red**

Run the focused GPU layer test. Expected: blur is refused by `unsupported.layer.backdrop_filter`; the blur-region assertion fails.

- [ ] **Step 3: Implement explicit backdrop texture ownership**

Add `GPUBackdropFilterDispatch.kt` with the exact entry point:

```kotlin
internal fun GPUBackendOffscreenTarget.renderBackdropBlur(
    sourceLabel: String,
    destinationLabel: String,
    blur: ImageFilter.Blur,
    bounds: LayerBounds,
    colorFormat: String,
    diagnostics: Diagnostics,
): Boolean
```

It creates/uses no shared global labels, clips its passes to `bounds`, and returns `false` after recording `unsupported.layer.backdrop.blur` when the registered blur route cannot materialize the request.

In `classifyLayerRequest`, accept only `ImageFilter.Blur` with finite sigmas and finite bounds. At `BeginLayer`, snapshot the active parent into a unique `kanvas:saveLayer:backdrop:<ordinal>` texture before allocating the child, filter it into a distinct target, and seed the child layer from that filtered target. Store both labels in `BackdropPlan`. Keep all other filter subclasses refused with `unsupported.layer.backdrop.<kind>`.

- [ ] **Step 4: Prove green, GM route, and isolation**

Run:

```bash
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUSaveLayerCompositeRegressionTest --no-daemon
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.gm.composite.BackdropImagefilterCroprectGm --tests org.graphiks.kanvas.skia.gm.composite.AAXfermodesRegressionTest --no-daemon
```

Expected: blur affects only its bounded region; crop refusal leaves parent intact; nested layer tests and AAX remain green.

- [ ] **Step 5: Commit Task 3**

```bash
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUBackdropFilterDispatch.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUSaveLayerCompositeRegressionTest.kt
rtk git commit -m "feat: support bounded GPU backdrop blur layers"
```

### Task 4: Capture GM evidence and preserve residual refusals

**Files:**
- Modify: `integration-tests/skia/src/test/resources/generated-renders/**` only when regenerated by Gradle
- Modify: `integration-tests/skia/test-similarity-scores.properties` only through the Skia runner
- Create: `reports/savelayer-variants-2026-07-11.md`

**Interfaces:**
- Consumes: completed Tasks 1-3 and `RenderConfig` OP diagnostics.
- Produces: before/after similarity, route/refusal counts, and artifact paths for every promoted variant.

- [ ] **Step 1: Capture focused OP diagnostics before regeneration**

Run:

```bash
rtk ./gradlew :integration-tests:skia:test --tests org.graphiks.kanvas.skia.SkiaGmRunner -Dkanvas.gm.name=backdrop_imagefilter_croprect -Dkanvas.render.debugLevel=OP --no-daemon
```

Record the manifest path, similarity, dispatch/refusal count, and exact remaining `unsupported.layer.*` reasons in the report.

- [ ] **Step 2: Regenerate the affected evidence**

Run:

```bash
rtk ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.family=composite --no-daemon
rtk ./gradlew :integration-tests:skia:test --no-daemon
rtk mkdir -p integration-tests/skia/build/reports/skia-gm-dashboard/data
rtk ./gradlew :integration-tests:skia:generateSkiaDashboard -x :integration-tests:skia:generateSkiaRenders --no-daemon
```

Expected: generated renders and score properties update; dashboard includes the new composite results. Document unrelated global runner failures separately instead of hiding them.

- [ ] **Step 3: Write the evidence report**

The report must list every promoted plan, its CPU/GPU/reference evidence, before/after score, remaining refusals, dashboard path, and whether full-surface layer allocation was retained. It must explicitly state that physical bounded targets and non-blur filter DAGs remain unsupported.

- [ ] **Step 4: Commit Task 4**

```bash
rtk git add reports/savelayer-variants-2026-07-11.md integration-tests/skia/src/test/resources/generated-renders integration-tests/skia/test-similarity-scores.properties
rtk git commit -m "gm: record saveLayer variant evidence"
```
