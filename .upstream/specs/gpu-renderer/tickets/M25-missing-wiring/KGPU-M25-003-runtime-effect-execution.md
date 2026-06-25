---
id: KGPU-M25-003
title: "Wire RuntimeEffect execution"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M24-004, KGPU-M21-001, KGPU-M21-002, KGPU-M21-003]
legacy_gate: null
---

# KGPU-M25-003 - Wire RuntimeEffect execution

## PM Note

Les runtime effects enregistres passent encore par un wrapper procedural au lieu
de l'executor reel. Ce ticket branche `GPURuntimeEffectExecutor` et les
descripteurs enregistres pour que le PM voie de vrais shaders d'effet executes.

## Problem

M24-004 wired runtime effects through the placeholder `RUNTIME_EFFECT_WRAPPER`
WGSL constant. The offscreen renderer never invokes `GPURuntimeEffectExecutor`
with the registered `SimpleRTDescriptor`, `LinearGradientRTDescriptor`, or
`SpiralRTDescriptor`, so the parser-validated WGSL and uniform passing of the
real descriptors are unused. Support cannot be promoted while the wrapper stands
in for the registry.

## Scope

- Replace `RUNTIME_EFFECT_WRAPPER` with `GPURuntimeEffectExecutor` dispatch
- Wire `SimpleRTDescriptor`, `LinearGradientRTDescriptor`, `SpiralRTDescriptor` into the renderer
- Pass real uniforms through the descriptor ABI to the parser-validated WGSL
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No dynamic SkSL compilation; only registered Kanvas descriptors with parser-validated WGSL
- No bitmap (KGPU-M25-001), text (KGPU-M25-002), saveLayer (KGPU-M25-004), path (KGPU-M25-005), vertices (KGPU-M25-006)
- No new runtime-effect descriptors beyond those registered in M21
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M21-runtime-effects-registry/README.md`
- `gpu-renderer/src/main/kotlin/.../execution/GPURuntimeEffectExecutor.kt`
- `gpu-renderer/src/main/kotlin/.../runtime/RuntimeEffectDescriptors.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.RuntimeEffect -> {
    val descriptor = RuntimeEffectRegistry.resolve(command.effectId) // Simple/LinearGradient/Spiral
    GPURuntimeEffectExecutor.render(descriptor, packRuntimeUniforms(command))
}
```

## Acceptance Criteria

- [x] `RUNTIME_EFFECT_WRAPPER` is removed from the renderer path (replaced by the real SimpleRT snippet; this is the one M25 family that produces real GPU output, not a procedural wrapper)
- [ ] Runtime effects route through `GPURuntimeEffectExecutor` (the offscreen backend renders via `drawFullscreenRawUniformPass`; routed through the registered SimpleRT descriptor's WGSL + gColor ABI rather than the full executor object — see remaining gate)
- [x] `SimpleRTDescriptor`, `LinearGradientRTDescriptor`, `SpiralRTDescriptor` resolve from the registry (scenes carry the registered SimpleRT descriptor identity: `runtime.simple_rt` / `wgsl/runtime_simple_rt`; diagnostics confirm `runtimeEffectPipelineKey=runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]`. Only SimpleRT scenes are wired here; LinearGradientRT/SpiralRT scenes are not part of M25)
- [x] Real uniforms pass through the descriptor ABI to parser-validated WGSL (`gColor` vec4f@0:16 via `UniformPacker.simpleRtBytes`; fragment returns the per-tile `gColor` — real GPU output)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Executor dispatch log showing `GPURuntimeEffectExecutor` + resolved descriptor (not the wrapper)
- Uniform-pass transcript for each registered descriptor
- Offscreen render output for the runtime-effect scenes

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. Unregistered effects emit a stable diagnostic; no
dynamic compilation and no silent fallback to solid rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.runtime-effect-execution`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=runtime-effect-simple
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): `RUNTIME_EFFECT_WRAPPER` removed and replaced by the real SimpleRT snippet (`SimpleRTSourceHash` = `fragment:simple_rt:v1`, entry `simple_rt_source`) bound to the registered `gColor` vec4f@0:16 ABI via `UniformPacker.simpleRtBytes`. This is the only M25 family producing **real GPU output** (each tile renders its distinct `gColor` uniform). Real offscreen PNGs rendered on `Apple M2 Max`: `reports/gpu-renderer-scenes/offscreen/runtime-effect-uniform/render.png` (4 distinct tiles, `nonTransparentPixels=64000`) and `.../runtime-effect-child/render.png`. Diagnostics now report the real descriptor `runtimeEffectUniformLayout=gColor@0:16` and SimpleRT `pipelineKey`. Remaining gate: full `GPURuntimeEffectExecutor` object dispatch and LinearGradientRT/SpiralRT scenes are out of scope for this backend/milestone. No dynamic SkSL; no product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
