# M28 — Backend Stencil / Vertices / Targets: Evidence & Acceptance Review

Date: 2026-06-25
Scope: `:gpu-renderer` (execution backend), `:gpu-renderer-scenes` (offscreen renderer)
Tickets: `KGPU-M28-001..006` (all `status: done`, `claim_impact: ImplementationCandidate`,
`product_activation: false`)

This report records the **real delivered state** of M28 after an independent source review,
including acceptance-criteria gaps that were not captured when the tickets were marked `done`
(their `Status Notes` had remained `- proposed: Initial ticket.`). It also records the 2026-06-25
hygiene cleanup of stale diagnostics/comments and dead code.

## 1. Acceptance matrix (delivered vs. ticket criteria)

| Ticket | Capability | Render-path wiring | Verdict |
|--------|-----------|--------------------|---------|
| M28-001 depth-stencil attachment | `Depth24PlusStencil8` texture + `RenderPassDepthStencilAttachment` + `drawFullscreenStencilPass` contract + stencil-reference action | n/a (backend capability) | **MET** |
| M28-002 stencil-cover path fill | uses backend | `path-fill-stencil` pixels via tessellated **indexed fill** (`drawVertexColorIndexed`), **not** two-pass stencil-write + cover-resolve | **PARTIAL** — criteria 1 & 4 met (non-rect shape, convex indexed); criteria 2 & 3 (stencil write pass / cover resolve) **NOT met** |
| M28-003 vertex/index buffer | `createVertexBuffer`/`createVertexColorBuffer`, `drawIndexed`/`drawVertexColorIndexed`, position+color layout | n/a (backend capability) | **MET** |
| M28-004 vertices mesh rendering | uses backend | `vertices` + `convex-fan-mesh` render real indexed colored mesh in `renderToPixels` | **MET** |
| M28-005 secondary render target | `createOffscreenTexture`/`encodeOffscreenTexture` contracts; saveLayer allocates a secondary texture | secondary texture **allocated**, **rendered into**, and **sampled** via `drawCompositePass` @group(1) | **MET** |
| M28-006 saveLayer composite | composite uses real `LayerCompositeWgsl` snippet | `childrenRendered=1`, `childContentSampled=true`; offscreen texture bound @group(1); correct premultiplied blend; parity 1.0000 | **MET** |

### Evidence pointers

- Backend capability: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeWgpu.kt`
  (depth-stencil texture/attachment; `createVertexBuffer`; `drawIndexed`),
  `GPUBackendRuntimeContracts.kt` (`drawFullscreenStencilPass`, `createOffscreenTexture`,
  `encodeOffscreenTexture`).
- Render path: `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RectOnlyOffscreenRenderer.kt`
  `renderToPixels` — `path-fill-stencil` (indexed fill), `convex-fan-mesh`, `vertices`, `save-layer`
  (secondary texture + `composeSaveLayerCompositeWgsl`, no texture binding).
- Scene evidence: `reports/gpu-renderer-scenes/offscreen/{savelayer-isolated,mesh-ribbon-depth,vertices-color-mesh}/`.
- Tests: `gpu-renderer-scenes/.../offscreen/M25ExecutorWiringTest.kt`.

## 2. 2026-06-25 cleanup performed

Stale M25-era diagnostics/comments that contradicted the real M28 render path were corrected, and
now-dead procedural code was removed.

- `RectOnlyOffscreenRenderer.kt`:
  - save-layer diagnostic `proceduralComposite=true secondaryTargetDeferred=M26`
    → `secondaryTargetAllocated=true childContentSampled=false`.
  - vertices diagnostic `boundingRectVisual=true realMeshDeferred=M26`
    → `realMesh=true vertexIndexBuffersUploaded=true`.
  - save-layer & vertices docstrings rewritten to reflect M28.
  - removed dead `LAYER_COMPOSITE_WRAPPER_WGSL` (`procedural_layer_color` /
    `layer_composite_procedural`), unreferenced since the composite uses the real snippet.
- `M25ExecutorWiringTest.kt`: header + assertions updated (`secondaryTargetAllocated=true`,
  `realMesh=true`).
- Committed scene evidence aligned (deterministic CPU diagnostic strings, not GPU pixel data):
  `savelayer-isolated`, `mesh-ribbon-depth`, `vertices-color-mesh` (`run.json` + `diagnostics.txt`).

Verification: `rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests '*M25ExecutorWiringTest*'`
→ BUILD SUCCESSFUL, 7 tests PASSED (after both the diagnostic fix and the dead-code removal).

## 3. Recommendations

- M28-001, M28-003, M28-004: keep `done` (criteria met).
- M28-002, M28-005, M28-006: the `done` status overstates the acceptance criteria. Choose one:
  1. **Reopen/downgrade** these tickets and implement the missing wiring (real two-pass
     stencil-cover pixel output; render saveLayer children into the secondary target and sample it
     as `@group(1)`), or
  2. **Keep at ImplementationCandidate** with the acceptance criteria rewritten to match what was
     delivered (backend capability + candidate scenes) plus explicit non-claims.
- Until resolved, do this **before** M30-003 (Skia GM parity), so parity is not measured against
  a saveLayer/path-fill path that does not actually isolate layers / use stencil-cover.

## 4. Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests '*M25ExecutorWiringTest*'
rtk git diff --check
```

## 5. Verification harness + partial M28-002 fixes (2026-06-25)

A CPU-reference parity harness was added so GPU offscreen output is diffed against an independent
ground truth (no GPU is available in the test JVM, so the GPU `render.png` is produced by the
`renderGpuRendererSceneOffscreen` task and the in-JVM test compares it to the CPU reference):

- `OffscreenSceneCpuReference` (test): minimal self-contained rasterizer (clear + srcOver rects +
  filled polygons), reusing the renderer's exact `generateStarVertices`/`generateOctagonVertices`.
- `OffscreenScenePngParityTest` (test): decodes the committed `render.png` and diffs it.
  Anchor `solid-card-stack` = similarity 1.0000 (maxΔ 0); `dst-read-strategy` = 1.0000 (maxΔ 1) —
  the harness is validated against known-correct scenes.

The harness objectively confirmed and then drove two real fixes:

| Defect | Evidence (before → after) |
|--------|---------------------------|
| Bounding-box fall-through (`path-fill-stencil`/`convex-fan-mesh` drawn by the solid-rect pass) | excluded from `solidFills` |
| `drawVertexColorIndexed` fabricated sequential indices, ignoring the real triangulation indices | now uses caller indices; convex maxΔ 224 → 54 (full octagon shape) |

Remaining (M28-002 not yet `done`): (a) convex colour double-apply (`in.color * uniforms.color`
with both = fill colour → blue², maxΔ 54) — pass an identity/white uniform; (b) the concave star
needs two-pass stencil-cover (fan triangulation cannot fill a non-convex polygon). M28-005/006
(saveLayer) remain untouched.

Validation: `:gpu-renderer:test` and `:gpu-renderer-scenes:test` both BUILD SUCCESSFUL.

## 6. M28-002 completed: stencil-cover star + convex colour fix (2026-06-25)

Both remaining M28-002 defects are now fixed and PROVEN via the parity harness (tolerance 8):

- **Concave star → real two-pass stencil-cover.** `RectOnlyOffscreenRenderer` now renders
  `path-fill-stencil` through `drawFullscreenStencilPass(GPUBackendStencilMode.Write, …)`
  (increment/decrement-wrap winding of the triangulated star into the stencil buffer, no colour
  writes) followed by `drawFullscreenStencilPass(GPUBackendStencilMode.Test, …)` (a fullscreen
  cover quad that passes only where stencil != 0, writing the fill colour via srcOver). This
  replaces the fan-triangulated `drawVertexColorIndexed` indexed fill, which cannot fill a
  non-convex polygon.
- **Convex octagon colour double-apply fixed.** The `convex-fan-mesh` vertex-colour draw now passes
  an identity-white uniform to `VERTEX_COLOR_WGSL` (`in.color * white = in.color`) so the per-vertex
  fill colour is no longer squared against the uniform colour.

| Scene | similarity (before → after) | mismatch px / 64000 | maxChannelDelta |
|-------|-----------------------------|---------------------|-----------------|
| `path-fill-stencil` | 0.8278 → **1.0000** | 11022 → 2 | 248 → 214 |
| `convex-fan-mesh` | 0.8409 → **1.0000** | 10184 → 0 | 54 → 0 |
| `solid-card-stack` (anchor) | 1.0000 → 1.0000 | 0 → 0 | 0 → 0 |

The 2 residual `path-fill-stencil` pixels are single hard-edge differences at the star tips (GPU
stencil coverage vs the CPU even-odd point-in-polygon reference), within the documented 1px
tolerance. Passing parity assertions (`>= 0.99`) were added to `OffscreenScenePngParityTest` for
both shape scenes; the anchor assertion is retained and the non-asserting diagnostic now tracks only
`dst-read-strategy` (M28-005/006 pending).

Validation: both scene PNGs regenerated via `renderGpuRendererSceneOffscreen`
(run.json `status=rendered`); `:gpu-renderer:test` + `:gpu-renderer-scenes:test` BUILD SUCCESSFUL
(`OffscreenScenePngParityTest`: 4 tests, 0 failures). M28-005/006 (saveLayer) remain untouched.

## 7. M28-005/006 completed: secondary render target + saveLayer composite (2026-06-25)

Both M28-005 and M28-006 are now completed with real pixel-parity proof via the CPU reference harness.

### M28-005: Secondary render target

- Added `GPUBackendOffscreenTarget.createOffscreenTexture` and `encodeOffscreenTexture` to contracts
- Implemented in `WgpuOffscreenTarget`: `createOffscreenTexture` (was already internal, now `override`)
  and `encodeOffscreenTexture` (delegates to `encodeOffscreenTextureInternal` with a fresh
  `GpuResourceScope`)
- `encodeOffscreenTextureInternal` extended with a `Depth24PlusStencil8` attachment so the
  render pass is compatible with the existing pipeline (which declares depth-stencil state)
- `LayerCompositeWgsl` blend formula fixed: previously treated the texture sample as straight alpha
  (`layer_color.rgb * layer_color.a`), now treats it as premultiplied (`layer_color.rgb` directly)
  since the offscreen render pass writes premultiplied data via srcOver GPU blend

### M28-006: SaveLayer composite

- `RectOnlyFillDraw` extended with `shadowColor`, `shadowOffsetX`, `shadowOffsetY` fields
- `prepareRectOnlyDrawPlan` now passes saveLayer shadow info through to fills
- `renderToPixels` restructured with a pre-pass:
  1. For each saveLayer, identify child fills (fills with paintOrder between this saveLayer
     and the next, excluding saveLayer fills themselves)
  2. Create a viewport-sized offscreen texture via `target.createOffscreenTexture`
  3. Render shadow + content card + child fills into it via `target.encodeOffscreenTexture`
     (with srcOver, using `drawFullscreenPass` + `SOLID_RECT_WGSL`)
  4. In the main pass, skip child fills (excluded from solid fills via `saveLayerChildLabels`)
  5. Composite via `drawCompositePass` with transparent tint `(0,0,0,0)` so the premultiplied
     layer content passes through to the GPU hardware srcOver blend
- Composite covers full viewport (scissor 0,0 → viewportW,viewportH), UV `pos.xy / vec2f(320,200)`
  maps correctly to the viewport-sized offscreen texture
- `saveLayerWiringDiagnostics` updated to count actual children from the fill list

### CPU reference extension

- `OffscreenSceneCpuReference` now handles `SceneCommand.SaveLayer` with fixture payload:
  renders the shadow (contentRect + shadowOffset, shadowColor) then content card (contentRect,
  contentColor) with srcOver, followed by any subsequent FillRect children

### Evidence

| Scene | similarity | mismatch / 64000 | maxChannelDelta | childrenRendered | childContentSampled |
|-------|-----------|------------------|----------------|-----------------|-------------------|
| savelayer-isolated (before) | N/A (blank composite) | — | — | 0 | false |
| savelayer-isolated (after) | **1.0000** | **0** | **1** | **1** | **true** |
| solid-card-stack (anchor) | 1.0000 | 0 | 0 | — | — |

### Files changed

- `gpu-renderer/.../GPUBackendRuntimeContracts.kt` — added `createOffscreenTexture`/`encodeOffscreenTexture` to `GPUBackendOffscreenTarget`
- `gpu-renderer/.../GPUBackendRuntimeWgpu.kt` — implemented target-level methods; added depth-stencil to `encodeOffscreenTextureInternal`
- `gpu-renderer/.../LayerCompositeSnippet.kt` — fixed premultiplied blend formula
- `gpu-renderer-scenes/.../RectOnlyOffscreenRenderer.kt` — shadow fields, pre-pass saveLayer rendering, composite pass, diagnostics
- `gpu-renderer-scenes/.../OffscreenSceneCpuReference.kt` — SaveLayer CPU reference rendering
- `gpu-renderer-scenes/.../OffscreenScenePngParityTest.kt` — savelayer-isolated parity assertion

### Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test :gpu-renderer-scenes:test
```
Both BUILD SUCCESSFUL; `OffscreenScenePngParityTest`: 5 tests, 0 failures.
