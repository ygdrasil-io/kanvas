---
id: KGPU-M28-006
title: "Wire saveLayer real composite rendering"
status: done
milestone: M28
priority: P0
owner_area: execution-backend
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M28-005]
legacy_gate: null
---

# KGPU-M28-006 - Wire saveLayer real composite rendering

## PM Note

Les saveLayers utilisent encore `LAYER_COMPOSITE_WRAPPER` comme shader procedural
parce que le backend n'a pas de render targets secondaires. Avec les render targets
disponibles (M28-005), ce ticket branche le vrai composite srcOver en utilisant la
texture du layer enfant comme source pour que le PM voie l'isolation de layer reelle.

## Problem

KGPU-M25-004 wired `SaveLayerExecutor` and `LayerCompositeSnippet` identity into
the offscreen renderer, but the saveLayer composite pass still uses the procedural
`LAYER_COMPOSITE_WRAPPER_WGSL` shader because the backend could not create secondary
render targets or sample them as texture sources. Now that M28-005 delivers
secondary render target creation and texture binding, the saveLayer path must be
upgraded to render children to an offscreen target and composite back using the
real `LayerCompositeWgsl` shader with the offscreen target texture bound as the
source.

## Scope

- Route saveLayer children to render into a secondary offscreen target (via M28-005)
- Restore composite using real `LayerCompositeWgsl` + texture binding instead of `LAYER_COMPOSITE_WRAPPER_WGSL`
- Bind the offscreen target texture as `@group(1)` source for the composite pass
- Use real srcOver blend in the composite shader
- Regenerate savelayer-isolated scene PNG with real layer isolation output
- Regenerate dst-read-strategy scene PNG with real composite output
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No backdrop filter support (filter DAG deferred)
- No nested saveLayer beyond what the current executor supports
- No layer elision optimization changes
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/08-layer-and-filter-plans.md`
- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`
- `.upstream/specs/gpu-renderer/tickets/M18-savelayer-destination-read/README.md`
- `.upstream/specs/gpu-renderer/tickets/M25-missing-wiring/KGPU-M25-004-savelayer-composite.md`
- `gpu-renderer/src/commonMain/kotlin/.../gpu/GpuNativeOffscreenRenderer.kt`
- `gpu-renderer/src/commonMain/kotlin/.../shaders/LayerCompositeWgsl.kt`

## Design Sketch

```kotlin
is SceneCommand.SaveLayer -> {
    // 1. Create offscreen target for layer children
    val offscreenTarget = createOffscreenTarget(width, height, withDepthStencil = false)

    // 2. Render children to offscreen target
    beginOffscreenRenderPass(offscreenTarget, clearColor = transparent)
    for (child in command.children) {
        renderDrawCommand(child) // uses offscreen target as current render target
    }
    endOffscreenRenderPass()

    // 3. Composite back to primary target using real LayerCompositeWgsl
    bindRenderTargetTexture(offscreenTarget) // @group(1) binding
    drawFullscreenUniformPass(
        pipeline = resolvePipeline(LayerCompositeWgsl),
        uniforms = gatherCompositeUniforms(command),
    )
}
```

## Acceptance Criteria

- [x] SaveLayer children render to a secondary offscreen target, not the primary (`encodeOffscreenTexture`; run.json `childrenRendered>0`)
- [x] Composite pass uses real `LayerCompositeWgsl` instead of `LAYER_COMPOSITE_WRAPPER_WGSL` (dead wrapper removed)
- [x] Offscreen target texture is bound as `@group(1)` source for the composite pass
- [x] Composite pass uses srcOver blend with correct premul alpha handling
- [x] `savelayer-isolated` scene PNG shows real layer isolation output (parity 1.0000; isolation further proven by the `savelayer-group-alpha` group-alpha overlap parity)
- [ ] `dst-read-strategy` scene PNG shows real composite output — **N/A**: `dst-read-strategy` contains no SaveLayer, so there is no layer composite to validate; layer isolation is proven instead by `savelayer-isolated` + `savelayer-group-alpha`
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Offscreen render pass dispatch log for saveLayer children (draw call count, pass duration)
- Composite pass dispatch log (pipeline identity, texture binding @group(1), blend state)
- Offscreen render output for savelayer-isolated scene (not a procedural wrapper visual)
- Offscreen render output for dst-read-strategy scene (not a procedural wrapper visual)
- SaveLayer diagnostics: target dimensions, child draw count, composite blend mode

## Fallback / Refusal Behavior

If secondary target creation failed (M28-005), the renderer emits a
`target-unavailable` diagnostic and keeps the `LAYER_COMPOSITE_WRAPPER` visual as
diagnostic output. If the GPU is unavailable, emit `gpu-unavailable`. No silent
fallback to the procedural wrapper as a support claim.

## Dashboard Impact

- Expected row: `gpu-renderer.m28.savelayer-composite`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=savelayer-isolated
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=dst-read-strategy
```

## Status Notes

- `proposed`: Initial ticket.
- `done` (earlier; reopened below) — ACCEPTANCE GAP found in 2026-06-25 review. The composite now uses the
  real `LayerCompositeWgsl` snippet (the procedural `LAYER_COMPOSITE_WRAPPER_WGSL` was removed
  2026-06-25, satisfying that one criterion). Remaining criteria are NOT met: saveLayer children
  do not render into the secondary target (`childrenRendered=0`), and the secondary target is not
  bound/sampled as `@group(1)` in the composite WGSL (`composeSaveLayerCompositeWgsl` declares only
  a uniform, no texture binding), so the scene does not demonstrate real layer isolation. Scene
  diagnostics corrected 2026-06-25 (`saveLayer:secondaryTargetAllocated=true childContentSampled=false`).
  Recommend reopen/downgrade or a follow-up to render children into the secondary target and sample
  it. See `reports/gpu-renderer/2026-06-25-m28-backend-stencil-vertices-targets.md`.
- `ready` (2026-06-25): reopened/downgraded from `done` — children are not rendered into the
  secondary target and the target is not sampled. Ready to implement child render into the
  secondary target + `@group(1)` sampling in the composite pass.
- `done` (2026-06-25): saveLayer children now render into a viewport-sized secondary offscreen target
  via `target.encodeOffscreenTexture` pre-pass (shadow + content card + child fills with srcOver).
  Composite uses real `LayerCompositeWgsl` with `drawCompositePass` binding the offscreen texture
  at `@group(1)` binding 1 + sampler at binding 2. LayerCompositeWgsl blend formula fixed for
  premultiplied layer texture (layer_color.rgb directly, not multiplied by layer_color.a again).
  `savelayer-isolated` diagnostics: `childrenRendered=1 childContentSampled=true`. Parity:
  similarity=1.0000 mismatch=0/64000 maxChannelDelta=1 vs CPU reference. `:gpu-renderer:test` +
  `:gpu-renderer-scenes:test` BUILD SUCCESSFUL. `OffscreenScenePngParityTest` 5 tests 0 failures.

## Linear Labels

- `gpu-renderer`
- `milestone:M28`
- `area:execution-backend`
