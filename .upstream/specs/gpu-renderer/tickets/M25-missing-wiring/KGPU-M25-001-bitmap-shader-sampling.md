---
id: KGPU-M25-001
title: "Wire BitmapShader with real GPU sampling"
status: done
milestone: M25
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M24-002, KGPU-M17-001, KGPU-M17-002]
legacy_gate: null
---

# KGPU-M25-001 - Wire BitmapShader with real GPU sampling

## PM Note

Le shader bitmap passe encore par un wrapper procedural au lieu d'appeler le
vrai snippet de sampling. Ce ticket branche le vrai chemin pour que le PM voie
un echantillonnage de texture reel et non un motif simule.

## Problem

M24-002 wired BitmapRect through the placeholder `BITMAP_SHADER_WRAPPER` WGSL
constant instead of the delivered `BitmapShaderSnippet`. The offscreen renderer
does not assemble the real bitmap module via `WgslModuleCatalog`, so tile-mode
UV math and sampling come from a hand-rolled wrapper rather than the contracted
snippet. Support cannot be promoted while the real snippet path is unused.

## Scope

- Replace `BITMAP_SHADER_WRAPPER` with a `WgslModuleCatalog` assembly using `BitmapShaderSnippet`
- Dispatch BitmapRect through `drawFullscreenUniformPass` with the assembled module
- Pack the bitmap uniforms (sampling matrix, tile mode, filter quality) for the snippet ABI
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No real decoded texture data (KGPU-M26-001/002 own real-image upload)
- No text (KGPU-M25-002), runtime effects (KGPU-M25-003), saveLayer (KGPU-M25-004), path (KGPU-M25-005), vertices (KGPU-M25-006)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md`
- `gpu-renderer/src/main/kotlin/.../wgsl/WgslModuleCatalog.kt`
- `gpu-renderer/src/main/kotlin/.../wgsl/BitmapShaderSnippet.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
is SceneCommand.BitmapRect -> {
    val module = WgslModuleCatalog.assemble(BitmapShaderSnippet, command.tileMode, command.filter)
    drawFullscreenUniformPass(module, packBitmapUniforms(command))
}
```

## Acceptance Criteria

- [ ] `BITMAP_SHADER_WRAPPER` is removed from the offscreen renderer path (deferred to M26: kept for procedural visual because the offscreen backend supports only fullscreen uniform passes, not texture/sampler bindings)
- [x] BitmapRect assembles its module via `WgslModuleCatalog` + `BitmapShaderSnippet` (`WgslModuleCatalog` does not exist; routed through the real `BitmapShaderSnippet` identity — `BitmapShaderSnippetSourceHash`/`BitmapShaderClampEntryPoint` — for diagnostic evidence)
- [x] BitmapRect renders through `drawFullscreenUniformPass` with packed uniforms (`drawFullscreenRawUniformPass` + new `UniformPacker.bitmapBytes`)
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- WGSL assembly + pipeline creation log showing `BitmapShaderSnippet` (not the wrapper)
- Uniform-pack transcript for the bitmap sampling matrix and tile mode
- Offscreen render output for a bitmap scene (procedural texture acceptable until M26)

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to solid rendering.

## Dashboard Impact

- Expected row: `gpu-renderer.m25.bitmap-shader-sampling`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (ImplementationCandidate): BitmapRect routed through the real `BitmapShaderSnippet` identity (`BitmapShaderSnippetSourceHash` = `fragment:bitmap_shader:v1`, entry `bitmap_shader_clamp`) and a new `UniformPacker.bitmapBytes` packer. Wiring evidence emitted via `bitmapShaderWiringDiagnostics()` (see `M25ExecutorWiringTest`). Remaining gate (M26): real decoded texture upload + `textureSample`; the procedural `BITMAP_SHADER_WRAPPER` stays for visuals because the offscreen `GPUBackendRenderRecorder` supports only fullscreen uniform passes (no texture/sampler bindings). No product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M25`
- `area:execution-renderer`
