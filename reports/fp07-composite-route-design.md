# FP-07 — Prepared Composite Route Design

**Date:** 2026-07-31
**Branch:** `fp-07` (based on `codex/graphite-dawn-frame-plan-design`)
**Status:** Design approved, pending implementation plan

## Overview

FP-07 migrates composite operations (`DrawPicture`, `BeginLayer`, `EndLayer`) from the legacy immediate path to the prepared WebGPU frame route. It is the last family migration before FP-08 retires the legacy adapter entirely.

### Architecture Philosophy

Inspired by Skia Graphite's backend architecture, FP-07 does **not** create a dedicated "composite pass." Instead:

- Each `saveLayer` creates a scratch offscreen texture (like Graphite's scratch Device)
- Child content is rendered into the scratch texture via existing draw routes
- The result is sampled back as a textured quad in the parent with the appropriate blend mode
- Filters are decomposed into scratch textures per materialization boundary (the `GPUPreparedFilterNormalizer` already marks these boundaries)

### What Exists (pre-FP-07 scaffolding)

| Component | File | Status |
|---|---|---|
| Composite capture engine | `GPUPreparedCompositeCapture.kt` (885 lines) | Complete |
| Composite contracts | `GPUPreparedCompositeContracts.kt` (192 lines) | Complete |
| SaveLayer isolated target planner | `LayerContracts.kt` (1030 lines) | Complete |
| Filter descriptors (22 kinds) | `GPUPreparedFilterDescriptors.kt` (1005 lines) | Complete |
| Filter normalizer | `GPUPreparedFilterNormalizer.kt` (370 lines) | Complete |
| Blend planner (29 modes) | `GPUBlendPlanning.kt` (597 lines) | Complete |
| Blend formula library (WGSL) | `GPUBlendFormulaLibrary.kt` (221 lines) | Complete |
| Pass commands (23 types) | `PassContracts.kt` (1922 lines) | Complete |
| Product router + frame gate | `GPUPreparedSurfaceProductRouter.kt`, `GPUPreparedSurfaceFrameGate.kt` | Complete |

### What Must Be Built

Organized into 3 cycles, each producing a self-contained deliverable.

---

## Cycle 1 — Foundation: Lowering + CPU Oracles

Independent of all other cycles. Establishes the planning backbone and correctness references.

### A — Composite Lowering (`GPUPreparedCompositeLowerer`)

**File:** `gpu-renderer/src/main/kotlin/.../layers/GPUPreparedCompositeLowerer.kt` (~250 lines)

Takes a `GPUPreparedCompositeCapture` and produces a `GPUPreparedCompositeLowering` (Ready or Refused).

**Algorithm:**
```
lower(capture):
  1. Validate scope hierarchy (no orphan scopes, parent-child integrity)
  2. Traverse scopes in topological order (parents before children)
  3. For each SaveLayer scope:
     a. Call GPUSaveLayerIsolatedTargetPlanner.plan() → LayerPlan or Refusal
     b. If refused, propagate as CompositeLowering.Refused
     c. Collect child draws and sub-scopes → RenderLayerChildren command list
  4. For Root/PaintedPicture scopes:
     a. Draws are already captured as flat entries (Draw/SetTransform/SetClip)
     b. No offscreen target needed — direct rendering into parent
  5. Assemble GPUPreparedCompositePlan:
     - layerPlans: Map<ScopeId, GPULayerPlan>
     - normalizedFilters: Map<ScopeId, GPUPreparedFilterNormalization> (empty in Cycle 1)
     - rootScopeId, captureIdentity
  6. Return GPUPreparedCompositeLowering.Ready(plan)
```

**Key design decisions:**
- The lowerer does NOT render — it only plans
- Reuses `GPUSaveLayerIsolatedTargetPlanner` for individual saveLayer scope planning
- For scopes without children (Root with only draws), no layer plan needed
- Filters are deferred to Cycle 2 (`normalizedFilters` map is empty in Cycle 1)

**Tests:** `GPUPreparedCompositeLowererTest.kt` — verify plan structure for empty frame, saveLayer with children, nested saveLayers, painted picture, refusal propagation.

### D — CPU Blend Oracles

**File:** `gpu-renderer/src/main/kotlin/.../materials/GPUBlendOracle.kt` (~400 lines)

Independent module with no dependency on the lowering pipeline. Reference implementations for all 29 `GPUBlendMode` values.

**API:**
```kotlin
object GPUBlendOracle {
    fun blend(src: RGBA8, dst: RGBA8, mode: GPUBlendMode): RGBA8
}
```

**Implementation:**
- Each mode is a pure Kotlin function matching the WGSL formula from `GPUBlendFormulaLibrary.allModeBlendDispatcherWgsl()`
- Operations on float RGBA in [0,1], result clamped to [0,1]
- Porter-Duff modes: src-over, dst-over, src-in, dst-in, src-out, dst-out, src-atop, dst-atop, xor, plus, modulate
- Separable modes: multiply, screen, overlay, darken, lighten, color-dodge, color-burn, hard-light, soft-light, difference, exclusion
- Non-separable modes: hue, saturation, color, luminosity

**Tests:** `GPUBlendOracleTest.kt` — for each mode, 100 random (src, dst) pairs, verify against hand-computed reference values. Also verify that oracle matches WGSL dispatcher semantics.

### E — CPU Filter Oracles

**File:** `gpu-renderer/src/main/kotlin/.../filters/GPUFilterOracle.kt` (~350 lines)

Independent module. CPU reference implementations for the filter kinds that will be supported natively in FP-07.

**API:**
```kotlin
object GPUFilterOracle {
    fun apply(
        sourceBitmap: Bitmap<RGBA8>,
        filter: GPUPreparedFilterNode,
        inputBitmaps: Map<GPUPreparedFilterNodeId, Bitmap<RGBA8>>
    ): Bitmap<RGBA8>
}
```

**Initial filter coverage (Cycle 1):**

| Filter | Algorithm |
|---|---|
| Blur | Separable Gaussian, sigma→kernel size, 2D convolution |
| ColorFilter | 4x5 matrix multiply on RGBA |
| DropShadow | Blur + offset + composite over black background |
| Offset | Bitmap translation with edge policy |
| Crop | Rect intersection clipping |

**Filters deferred to later cycles:** lighting (distant/point/spot diffuse/specular), displacement map, matrix convolution, magnifier, runtime effect.

**Tests:** `GPUFilterOracleTest.kt` — for each supported filter kind, verify oracle output dimensions, edge behavior, and pixel correctness against known inputs.

---

## Cycle 2 — Materialization: Native Execution + Filter DAG + Preflight

Depends on Cycle 1 (needs `GPUPreparedCompositeLowerer` and oracles for testing).

### B — SaveLayer Native Materialization

**New file:** `gpu-renderer/src/main/kotlin/.../layers/GPUSaveLayerNativeExecutor.kt` (~200 lines)

Bridges the `ValidatingSaveLayerMaterializer` (which produces resource decisions and command streams) with the actual GPU execution pipeline.

**Algorithm:**
```
execute(scope, plan):
  1. Allocate offscreen texture via resource provider
     (dimensions from scope.bounds, format from layer plan)
  2. Record render pass on offscreen target:
     a. BeginRenderPass(offscreenTarget, LoadOp.Clear)
     b. For each child entry in scope.entries:
        - Draw → route to existing draw pipeline (image/text/primitive)
        - Scope → recursive execute(childScope, plan)
     c. EndRenderPass
  3. Record composite-back in parent pass:
     a. Bind offscreen texture as sampled source
     b. Bind blend mode from scope.state.paint.blendMode
     c. Draw textured quad sampling offscreen texture
     d. The blend planner handles fixed-function vs shader blend
```

**Modifications to existing files:**
- `GPUPreparedSurfaceFrameTaskListBuilder.kt` — add `handleSaveLayer()` method that inserts `PrepareLayerTarget`, `RenderLayerChildren`, `CompositeLayer` tasks in the correct order
- `GPUFirstRoutePassBuilder.kt` — connect existing `acceptedDrawLayer()` / `refusedDrawLayer()` to the composite pipeline

**Tests:** `GPUSaveLayerNativeExecutorTest.kt` — verify that a saveLayer with a solid fill child produces a sampled texture in the parent, matching the CPU oracle.

### C — Filter DAG Route Planning

**New file:** `gpu-renderer/src/main/kotlin/.../filters/GPUPreparedFilterDAGPlanner.kt` (~200 lines)

Transforms the normalized filter graph (output of `GPUPreparedFilterNormalizer`) into an executable route plan.

**Algorithm:**
```
plan(normalizedGraph):
  1. For each node in topological order:
     a. If node is a materialization boundary → assign "render-to-texture" route
        (allocate intermediate texture, render filtered result into it)
     b. If node is foldable (Offset, Crop, ColorFilter with identity matrix) →
        assign "folded" route (inline in parent shader)
     c. If node is Identity → assign "elided" route
     d. Otherwise → assign NativeRender or NativeCompute route
  2. Produce GPUFilterDAGPlan:
     - nodeRoutes: Map<NodeId, GPUFilterNodeRoute>
     - intermediateTextures: List<(textureLabel, descriptor, nodeId)>
     - executionOrder: List<NodeId>
```

**Output integration:** The `GPUFilterDAGPlan` is attached to the scope in `GPUPreparedCompositePlan.normalizedFilters`. The task list builder uses it to schedule filter render passes between child rendering and composite-back.

**Tests:** `GPUPreparedFilterDAGPlannerTest.kt` — verify route assignment for simple blur, color filter folding, multi-node DAGs, identity elision.

### I — Composite Preflight

**New file:** `gpu-renderer/src/main/kotlin/.../layers/GPUPreparedCompositePreflight.kt` (~150 lines)

Validates the composite plan before GPU resource allocation.

**Checks:**
```
preflight(plan):
  1. Layer budget: total offscreen textures ≤ device limit
  2. Filter budget: total intermediate textures ≤ device limit
  3. Texture dimensions: each target ≤ maxTextureSize
  4. Format compatibility: target format supports required blend operations
  5. Cycle detection: no circular scope dependencies (safety net)
  → Ready or GPUPreparedCompositeLowering.Refused(PREFLIGHT, facts)
```

**Tests:** `GPUPreparedCompositePreflightTest.kt` — budget exceeded, oversized target, format incompatibility.

---

## Cycle 3 — Advanced Features

Depends on Cycles 1+2.

### F — Backdrop Filter

**Scope:** Remove the `LAYER_DESTINATION_READ` refusal from the capture pipeline and implement backdrop texture read + filter.

**Algorithm:**
```
processBackdropLayer(scope, plan):
  1. Before rendering children:
     a. Copy current parent render target → backdropTexture
     b. If scope has backdrop filter(s):
        - Apply filter DAG to backdropTexture → filteredBackdrop
  2. Render children into offscreen target (normal saveLayer path)
  3. CompositeLayer with both source (children) and destination (filteredBackdrop):
     a. Bind both textures in the composite shader
     b. Apply the saveLayer paint's blend mode between source and filteredBackdrop
```

**Modifications:**
- `GPUPreparedCompositeCapture.kt` — remove `LAYER_DESTINATION_READ` refusal; capture backdrop filters as filter descriptors on the scope
- `LayerContracts.kt` — extend `GPULayerBackdropPlan` to carry the backdrop texture label and filter references
- `GPUBlendPlanning.kt` — `LayerCompositeBlend` must accept an optional backdrop texture binding
- `GPUPreparedCompositeLowerer.kt` — produce backdrop plans for scopes with backdrop filters

**Tests:** `GPUBackdropFilterTest.kt` — verify backdrop blur produces correct sampling in parent.

### G — Mask Filter Route

**New files:**
- `gpu-renderer/src/main/kotlin/.../filters/GPUPreparedMaskFilterLowerer.kt` (~100 lines)
- `gpu-renderer/src/main/kotlin/.../filters/GPUMaskFilterMaterializer.kt` (~150 lines)

**Algorithm:**
```
lowerMaskFilter(maskFilter, bounds):
  If maskFilter is Blur(sigma):
    1. Compute coverage bounds = bounds + padding(sigma * 3)
    2. Produce GPUPreparedMaskFilterPlan(Blur, A8, dimensions, sigma)
  Else (Shader, Table):
    → GPUPreparedMaskFilterLowering.Refused(NATIVE_CAPABILITY)

materializeMask(plan):
  1. Allocate A8 texture at plan dimensions
  2. Render or compute the blur mask into the A8 texture
  3. Return the mask texture ready for sampling
```

**Capture integration:**
- In `GPUPreparedCompositeCapture`, when a paint has a maskFilter, capture it as `GPUPreparedMaskFilterPlan` instead of refusing with `PAINT`
- The plan is attached to the relevant draw within the scope

**Tests:** `GPUMaskFilterBlurTest.kt` — verify A8 mask dimensions, blur correctness.

### H — Picture Filter-Source

**Scope:** Handle `DrawPicture` operations that carry an image filter.

**Algorithm:**
```
processPicture(picture):
  If picture has imageFilter:
    1. Create scope with kind = FilterPictureSource (not PaintedPicture)
    2. Capture picture content WITHOUT applying the filter
    3. Attach the filter as normalizedFilter on the scope
    4. Lowering: render picture content → offscreen texture → apply filter → sample
  Else:
    Existing behavior (PaintedPicture or inline expansion)
```

**Modifications:**
- `GPUPreparedCompositeCapture.kt` — in `processPicture()`, detect image filter on picture paint, create `FilterPictureSource` scope
- `GPUPreparedCompositeLowerer.kt` — treat `FilterPictureSource` like a SaveLayer with a filter attached

**Tests:** `GPUPreparedCompositeCaptureSemanticTest.kt` — add test cases for picture with image filter producing FilterPictureSource scope.

---

## Integration Points

### Frame Gate Override (across all cycles)

Once all composite operations are handled by the prepared route:
- `GPUPreparedSurfaceFrameGate.kt` — remove `DrawPicture`, `BeginLayer`, `EndLayer` from legacy classification
- `GPUPreparedSurfaceProductRouter.kt` — add Composite family to `hasTerminalPreparedFamily()`
- `GPULegacyImmediatePathAdapter.kt` — remove `LegacyDisplayOpFamily.Composites`

This is the atomic cutover, gated by all Cycle 1-3 deliverables passing their tests.

### Task List Builder Integration (J — deferred to Cycle 4)

The `GPUPreparedSurfaceFrameTaskListBuilder` must be extended to handle:
- Layer target creation (texture allocation)
- Child rendering (recursive draw dispatch)
- Layer compositing (textured quad with blend)
- Filter intermediate rendering (per-node render passes)
- Ordering guarantees: children before composite-back, filter nodes in topological order

This is deferred to a separate Cycle 4 because it depends on all materialization primitives being stable.

---

## File Inventory

### New Files

| File | Cycle | Lines (est.) |
|---|---|---|
| `gpu-renderer/.../layers/GPUPreparedCompositeLowerer.kt` | 1A | 250 |
| `gpu-renderer/.../materials/GPUBlendOracle.kt` | 1D | 400 |
| `gpu-renderer/.../filters/GPUFilterOracle.kt` | 1E | 350 |
| `gpu-renderer/.../layers/GPUSaveLayerNativeExecutor.kt` | 2B | 200 |
| `gpu-renderer/.../filters/GPUPreparedFilterDAGPlanner.kt` | 2C | 200 |
| `gpu-renderer/.../layers/GPUPreparedCompositePreflight.kt` | 2I | 150 |
| `gpu-renderer/.../filters/GPUPreparedMaskFilterLowerer.kt` | 3G | 100 |
| `gpu-renderer/.../filters/GPUMaskFilterMaterializer.kt` | 3G | 150 |

### Modified Files

| File | Cycles | Changes |
|---|---|---|
| `GPUPreparedCompositeCapture.kt` | 3F, 3G, 3H | Remove backdrop refusal, mask filter capture, FilterPictureSource |
| `LayerContracts.kt` | 3F | Extend GPULayerBackdropPlan |
| `GPUBlendPlanning.kt` | 3F | Backdrop binding in LayerCompositeBlend |
| `GPUPreparedSurfaceFrameTaskListBuilder.kt` | 2B | handleSaveLayer, layer task ordering |
| `GPUFirstRoutePassBuilder.kt` | 2B | Connect acceptedDrawLayer/refusedDrawLayer |
| `GPUPreparedSurfaceFrameGate.kt` | Integration | Remove Composites from legacy |
| `GPUPreparedSurfaceProductRouter.kt` | Integration | Add Composites to hasTerminalPreparedFamily |
| `GPULegacyImmediatePathAdapter.kt` | Integration | Remove Composites family |

### Test Files (all new)

| File | Cycle |
|---|---|
| `GPUPreparedCompositeLowererTest.kt` | 1A |
| `GPUBlendOracleTest.kt` | 1D |
| `GPUFilterOracleTest.kt` | 1E |
| `GPUSaveLayerNativeExecutorTest.kt` | 2B |
| `GPUPreparedFilterDAGPlannerTest.kt` | 2C |
| `GPUPreparedCompositePreflightTest.kt` | 2I |
| `GPUBackdropFilterTest.kt` | 3F |
| `GPUMaskFilterBlurTest.kt` | 3G |

---

## Non-Goals (Explicitly Out of Scope)

- Full native GPU filter execution for all 22 filter kinds (only blur, color filter, drop shadow, offset, crop in initial scope)
- Runtime effect WGSL compilation and execution (deferred to FP-10)
- MSAA resolve for layer targets (single-sample only in initial scope)
- LCD text coverage in composite layers
- `DrawAtlas` migration (not a composite operation)
- Performance optimization of layer target reuse (deferred to FP-09)
- Shader-based mask filters (Shader, Table) — only Blur in initial scope
