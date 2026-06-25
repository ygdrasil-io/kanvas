---
id: KGPU-M28-005
title: "Add secondary render target support"
status: proposed
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

- [ ] `WgpuRenderRecorder.createOffscreenTarget` creates a texture + optional depth-stencil
- [ ] `GPUBackendRenderRecorder.beginOffscreenRenderPass` starts a render pass into the secondary target
- [ ] `GPUBackendRenderRecorder.endOffscreenRenderPass` ends the offscreen render pass
- [ ] Previous render target texture can be bound as a `@group(1)` texture source
- [ ] Texture sampling from a secondary target works in a fullscreen composite pass
- [ ] Existing primary render target and fullscreen pass support continues to work
- [ ] Offscreen target creation diagnostics are emitted (dimensions, format, depth-stencil presence)

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

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
