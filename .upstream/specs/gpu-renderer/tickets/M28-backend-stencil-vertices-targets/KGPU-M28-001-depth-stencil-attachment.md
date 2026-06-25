---
id: KGPU-M28-001
title: "Add depth-stencil attachment to offscreen backend"
status: done
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-005]
legacy_gate: null
---

# KGPU-M28-001 - Add depth-stencil attachment to offscreen backend

## PM Note

Le backend offscreen actuel ne gere que le rendu de passes uniformes plein ecran.
Sans attachement de stencil, les remplissages de path restent des rectangles englobants
au lieu de vraies formes. Ce ticket ajoute le support du depth-stencil pour que le PM
voie le stencil-cover avancer.

## Problem

The offscreen backend (`WgpuOffscreenTarget` / `GPUBackendRenderRecorder`) only
supports fullscreen uniform passes with a single color attachment. The path fill
family (M25-005) wires tessellation and stencil-cover executors, but the stencil
passes cannot run because there is no depth-stencil attachment and no stencil
state management. Real stencil-cover rendering is blocked until the backend can
create, clear, write to, and test against a stencil buffer.

## Scope

- Create a depth-stencil texture in `WgpuOffscreenTarget` alongside the color attachment
- Add stencil state management (clear, writeMode, testMode) to `WgpuRenderRecorder`
- Add `drawFullscreenStencilPass` to `GPUBackendRenderRecorder` interface and impl
- Keep existing fullscreen uniform pass support working

## Non-Goals

- No vertex/index buffer support (KGPU-M28-003)
- No secondary render targets (KGPU-M28-005)
- No path fill wiring (KGPU-M28-002)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M15-path-fill-stencil-cover/README.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/WgpuOffscreenTarget.kt`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/WgpuRenderRecorder.kt`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GPUBackendRenderRecorder.kt`

## Design Sketch

```kotlin
// WgpuOffscreenTarget: add depth-stencil texture
val depthStencilTexture = device.createTexture(
    TextureDescriptor(
        size = Extent3D(width, height, 1),
        format = TextureFormat.DEPTH24PLUS_STENCIL8,
        usage = TextureUsage.RENDER_ATTACHMENT,
    )
)

// WgpuRenderRecorder: add stencil state
fun beginStencilPass(clear: Boolean)
fun setStencilWriteMode(mode: StencilWriteMode)
fun setStencilTestMode(mode: StencilTestMode)
fun endStencilPass()

// GPUBackendRenderRecorder: add stencil pass interface
fun drawFullscreenStencilPass(
    pipeline: GPURenderPipelineHandle,
    uniforms: Float32Array,
)
```

## Acceptance Criteria

- [ ] `WgpuOffscreenTarget` creates a `DEPTH24PLUS_STENCIL8` texture at target resolution
- [ ] `WgpuRenderRecorder` supports stencil clear, writeMode, and testMode state transitions
- [ ] `GPUBackendRenderRecorder.drawFullscreenStencilPass` dispatches a fullscreen quad with stencil write
- [ ] Existing fullscreen uniform pass support continues to work
- [ ] Stencil state diagnostics are emitted for each pass transition

## Required Evidence

- Depth-stencil texture creation transcript (format, dimensions, usage flags)
- Stencil clear/write/test state transition log for a two-pass stencil-then-cover sequence
- Fullscreen stencil pass dispatch confirmation (pipeline bind + draw call)
- Existing uniform-pass visual output unchanged (regression check)

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. If the depth-stencil format is unsupported, emit
a `depth-stencil-format-unsupported` diagnostic. No silent fallback to
bounding-rect fills.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.depth-stencil-attachment`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (2026-06-25 review): backend depth-stencil capability delivered. `GPUBackendRuntimeWgpu`
  creates a `Depth24PlusStencil8` texture at target resolution and attaches it via
  `RenderPassDepthStencilAttachment` (stencil clear/store + stencil-reference action); the
  `GPUBackendRenderRecorder.drawFullscreenStencilPass` contract is defined. Evidence:
  `gpu-renderer/.../execution/GPUBackendRuntimeWgpu.kt`, `GPUBackendRuntimeContracts.kt`;
  `reports/gpu-renderer/2026-06-25-m28-backend-stencil-vertices-targets.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
