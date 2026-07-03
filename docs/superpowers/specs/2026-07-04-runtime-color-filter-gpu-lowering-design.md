# GPU Lowering for Runtime Color Filters

Status: Draft
Date: 2026-07-04

## Purpose

Implement GPU lowering for `ColorFilter.RuntimeEffect` in the renderer so that
runtime color filter GMs produce meaningful output instead of 0% similarity.
Corrects the WGSL signature mismatch in all 8 runtime color filter sources
(shader-style `(uv: vec2<f32>)` → color-filter-style `(inColor: vec4<f32>)`).

## Context

Currently, `RuntimeEffect.makeColorFilter()` creates a `ColorFilter.RuntimeEffect`
sealed type, but the GPU renderer's paint node walker and shader graph assembler
ignore it. The 3 GMs that use `makeColorFilter` (AlternateLumaGm, ComposeColorFilterGm,
RuntimeColorFilterGm) produce 0% similarity scores. The 8 WGSL sources for runtime
color filters use a shader-style function signature and are compiled via `makeShader`
as a workaround.

### Skia Reference Architecture (Graphite)

In Skia Graphite (`KeyHelpers.cpp:1612`), runtime color filters are handled
identically to runtime shaders — both go through `RuntimeEffectBlock::BeginBlock()`
with a different code snippet ID. Children are resolved recursively by
`add_children_to_key()`. There is no separate color filter stage or chained
fragment processor — just another block in the paint key.

## Design

### Architecture

A `ColorFilter.RuntimeEffect` becomes an additional node in the
`GPURuntimeEffectShaderGraph`, chained after the material source node. The existing
`GPURuntimeEffectShaderGraphAssembler` handles combining both into a single WGSL
module.

```
Paint { shader: Gradient, colorFilter: ColorFilter.RuntimeEffect(GChannelSplat) }
  │
  ▼
GPURuntimeEffectShaderGraph {
  nodes: [GradientMaterialNode, GChannelSplatNode(COLOR_FILTER)],
  edges: [GradientMaterialNode → GChannelSplatNode]
}
  │
  ▼
Assembler → combined WGSL: gradient_source(uv) → g_channel_splat(inColor)
```

### WGSL Signature Correction

All 8 runtime color filter WGSL sources change from shader-style to
color-filter-style:

```
// Before (shader):
fn color_filter_noop(uv: vec2<f32>) -> vec4<f32> { ... }

// After (color filter):
fn color_filter_noop(inColor: vec4<f32>) -> vec4<f32> { return inColor; }
```

Affected files:
- `ColorFilterNoopWgsl.kt` — passthrough (was returning black)
- `ColorFilterLumaToAlphaWgsl.kt` — `inColor` from parameter, luma→alpha
- `ColorFilterTernaryWgsl.kt` — `inColor` from parameter
- `ColorFilterIfsWgsl.kt` — `inColor` from parameter
- `ColorFilterEarlyReturnWgsl.kt` — `inColor` from parameter
- `GChannelSplatWgsl.kt` — `inColor.ggga`
- `ComposeColorFilterWgsl.kt` — `outer.eval(inner.eval(inColor))`
- (1 new if needed for RuntimeColorFilterGm reference)

### Shader Graph Node Kind

New enum value: `GPURuntimeEffectShaderGraphNodeKind.COLOR_FILTER`

When the assembler encounters a `COLOR_FILTER` node, it generates a call where
the parent node's output is passed as the `inColor` parameter:

```wgsl
fn combined_main(uv: vec2<f32>) -> vec4<f32> {
    let materialColor = gradient_source(uv);
    return g_channel_splat(materialColor);
}
```

### Paint Node Walker

Detect `ColorFilter.RuntimeEffect` in the Paint processing (same location where
`Shader.RuntimeEffect` is detected) and inject a `COLOR_FILTER` node into the
shader graph, with an edge from the last material source node.

### Descriptor Registration

Add 2 new descriptors to `KanvasRuntimeEffectRegistry.kt`:
- `GChannelSplatDescriptor` — effectId `runtime.g_channel_splat`, 0 children
- `ComposeColorFilterDescriptor` — effectId `runtime.compose_cf`, 2 color filter children

Placement: `MaterialSource` (same as existing runtime effect shaders).

The 5 pre-existing runtime color filter descriptors (Noop, LumaToAlpha, Ternary,
Ifs, EarlyReturn) already exist — only their `acceptedPlacements` may need updating.

### Hook Wiring

`RuntimeEffectWgsl4kWiring.makeColorFilterHook` is updated to route through the
shader graph (same mechanism as `makeShader`). The `makeColorFilter` method in
`RuntimeEffect.kt` already delegates to this hook.

### GM Updates

- `AlternateLumaGm.kt`: `makeColorFilter` now produces non-trivial output; remove
  the `ColorFilter.Compose(SRGB/Linear)` wrapper (the renderer chain handles it)
- `ComposeColorFilterGm.kt`: `makeColorFilter` with children now works via graph
- `RuntimeColorFilterGm.kt`: change from `makeShader` to `makeColorFilter`; add
  missing reference PNG

### Files Summary

| File | Change |
|------|--------|
| 8 WGSL source files in `gpu-renderer/.../wgsl/` | Fix signatures |
| `GPURuntimeEffectShaderGraph.kt` | Add `COLOR_FILTER` node kind |
| `GPURuntimeEffectShaderGraphAssembler.kt` | Chain parent→child for CF nodes |
| Paint node walker (file TBD) | Detect `ColorFilter.RuntimeEffect` |
| `KanvasRuntimeEffectRegistry.kt` | 2 new descriptors |
| `RuntimeEffectWgsl4kWiring.kt` | Update `makeColorFilterHook` |
| 3 GM files in `runtime_effect/` | Fix to use real `makeColorFilter` |
| Reference images | Add missing `runtimecolorfilter.png` |

## Non-Goals

- Do not implement a separate `MaterialColorFilter` pipeline stage
- Do not change the existing `GPUPaintStagePlan` hierarchy
- Do not modify the blender path

## Acceptance Criteria

- `./gradlew :gpu-renderer:compileKotlin` passes
- `./gradlew :integration-tests:skia:compileTestKotlin` passes
- `AlternateLumaGm` renders non-black output (similarity > 0%)
- `ComposeColorFilterGm` renders with composed filter applied (similarity > 0%)
- `RuntimeColorFilterGm` renders with reference PNG found
- All 8 WGSL color filter sources compile through `RuntimeEffect.compile(wgsl)`
- No regression on existing runtime shader GMs
