---
id: KGPU-M28-005
title: "Add secondary render target support"
status: done
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-004]
legacy_gate: null
---

# KGPU-M28-005 - Add secondary render target support

## PM Note

Les saveLayers utilisent encore un wrapper procedural parce que le backend ne
peut pas creer de render targets secondaires. Ce ticket ajoute la creation de
textures offscreen et le binding comme source de texture pour que le PM voie
le composite de layer reel.

## Problem

The offscreen backend (`WgpuRenderRecorder` / `GPUBackendRenderRecorder`) only
supports rendering to its single primary color attachment. The saveLayer family
(M25-004) wires `SaveLayerExecutor` and `LayerCompositeSnippet`, but children
cannot render to an offscreen target and the composite pass cannot sample a
previous render target as a texture source. Real saveLayer compositing is
blocked until the backend can create secondary render targets, render to them,
and then bind them as texture sources for the composite pass.

## Scope

- Add `createOffscreenTarget` to `WgpuRenderRecorder` (texture + optional depth-stencil)
- Allow binding a previous render target as a texture source at `@group(1)`
- Add render-to-texture pass support (begin/end offscreen render pass)
- Support texture sampling from a secondary target in the composite pass
- Keep existing primary render target and fullscreen pass support working

## Non-Goals

- No saveLayer composite wiring (KGPU-M28-006)
- No MRT (multiple render targets) in a single pass
- No mipmap generation for secondary targets
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/08-layer-and-filter-plans.md`
- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`
- `.upstream/specs/gpu-renderer/tickets/M18-savelayer-destination-read/README.md`
- `.upstream/specs/gpu-renderer/tickets/M25-missing-wiring/KGPU-M25-004-savelayer-composite.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/WgpuRenderRecorder.kt`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GPUBackendRenderRecorder.kt`

## Design Sketch

```kotlin
// WgpuRenderRecorder: add offscreen target creation
fun createOffscreenTarget(width: Int, height: Int, withDepthStencil: Boolean): GPUOffscreenTargetHandle

// GPUBackendRenderRecorder: add offscreen render pass
fun beginOffscreenRenderPass(targetHandle: GPUOffscreenTargetHandle, clearColor: Color?)
fun endOffscreenRenderPass()

// Bind previous render target as texture source (@group(1))
fun bindRenderTargetTexture(handle: GPUOffscreenTargetHandle)
fun bindRenderTargetSampler(handle: GPUOffscreenTargetHandle)

// Supporting type
data class GPUOffscreenTargetHandle(
    val textureHandle: GPUTextureHandle,
    val depthStencilHandle: GPUDepthStencilHandle?,
)
```

## Acceptance Criteria

- [x] Offscreen target creation — realised as target-level `createOffscreenTexture` + depth-stencil attachment on the offscreen pass
- [x] Render pass into the secondary target — realised via `encodeOffscreenTexture(label, clearColor, block)` (encapsulates begin+draw+end)
- [x] Offscreen render pass ends — encapsulated in `encodeOffscreenTexture`
- [x] Previous render target texture can be bound as a `@group(1)` texture source (`drawCompositePass`)
- [x] Texture sampling from a secondary target works in a fullscreen composite pass — proven: saveLayer parity 1.0000 vs CPU reference
- [x] Existing primary render target and fullscreen pass support continues to work — all other parity scenes still 1.0000
- [x] Offscreen target creation diagnostics are emitted (run.json: `childrenRendered`, `childContentSampled`)

> Note: the spec named `beginOffscreenRenderPass`/`endOffscreenRenderPass`; these were implemented as the encapsulated `encodeOffscreenTexture` recorder method (functionally equivalent).

## Required Evidence

- Offscreen target creation transcript (dimensions, format, depthStencil flag)
- Render-to-texture pass dispatch log (pass begin/end, draw calls within)
- Texture binding confirmation for secondary target as `@group(1)` sampler source
- Existing uniform-pass visual output unchanged (regression check)

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. If offscreen target creation fails, emit a
`target-creation-failed` diagnostic. No silent fallback to the procedural
`LAYER_COMPOSITE_WRAPPER` as a support claim.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.secondary-render-target`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (earlier; reopened below) — PARTIAL per 2026-06-25 review. Secondary offscreen texture creation is
  present (`createOffscreenTexture`/`encodeOffscreenTexture` contracts + saveLayer allocation in
  `renderToPixels`). But the acceptance criterion "texture sampling from a secondary target works
  in a fullscreen composite pass" is NOT demonstrated — the saveLayer composite (the only consumer)
  does not bind the secondary texture as a `@group(1)` sampler source. Recommend a follow-up that
  proves secondary-target sampling, or downgrade. See
  `reports/gpu-renderer/2026-06-25-m28-backend-stencil-vertices-targets.md`.
- `ready` (2026-06-25): reopened/downgraded from `done` — secondary-target sampling is not
  demonstrated. Ready to implement and prove secondary-target sampling in a composite pass.
- `done` (2026-06-25): `GPUBackendOffscreenTarget.createOffscreenTexture`/`encodeOffscreenTexture` added
  to contracts and implemented in `WgpuOffscreenTarget` (with depth-stencil attachment for pipeline
  compat). `WgpuOffscreenTarget.encodeOffscreenTexture` creates a separate command encoder + render
  pass into the offscreen texture and submits. Offscreen-texture-to-target saveLayer pre-rendering
  works in `renderToPixels` (viewport-sized secondary target, child fills + shadow + content card
  rendered into it, then composited via real `LayerCompositeWgsl` with `@group(1)` texture binding
  in `drawCompositePass`). Parity: savelayer-isolated similarity=1.0000 mismatch=0/64000 maxChannelDelta=1
  vs CPU reference. `:gpu-renderer:test` + `:gpu-renderer-scenes:test` BUILD SUCCESSFUL.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
