# FOR-416 M60 F16 AA stencil-cover isolated color-target

Date: 2026-06-05

## Result

Global classification: `isolated-color-target-diagnostic-unavailable`.

FOR-416 keeps the evidence derived-only. It does not add a runtime hook, does not change rendering, and does not infer a no-blend color-target value from the FOR-412 shader-return channel.

## Evidence

- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-for-416-m60-f16-isoler-la-sortie-color-target-sans-blend-des-cover-subdraws-mutateurs`
- Source finding: `global/kanvas/findings/for-415-capture-letat-blend-render-pass-et-ecarte-le-descriptor-state-comme-suspect-principal`
- FOR-401 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-412 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json`
- FOR-413 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-draw-transition-correlation-for413/m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json`
- FOR-414 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-draw-readback-for414/m60-f16-aa-stencil-cover-post-draw-readback-for414.json`
- FOR-415 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-blend-render-pass-state-for415/m60-f16-aa-stencil-cover-blend-render-pass-state-for415.json`
- Source owner audited: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`

## Scope

- Selected pixels: 16
- Zero-return mutating transitions covered: 16
- Mutating draw counts: {'1': 6, '3': 10}
- Pipeline family: `StencilCoverAaPolygonDraw`
- Blend mode: `kSrcOver`

## FOR-412 vs isolated color-target

FOR-412 observes `sourceColorSentToBlend` through a storage-buffer side channel immediately before the fragment shader returns `@location(0)`. FOR-414 observes the already updated RGBA16Float intermediate immediately after the draw render pass. FOR-415 audits the descriptor state and finds no mismatch.

The missing observation is the actual color attachment output with destination blending removed. The current `StencilCoverAaPolygonDraw` branch targets the main `colorView`, the AA cover pipelines keep `blendStateFor(mode)`, and the post-draw readback samples `intermediateView`. There is no scratch color target for FOR-416, so the isolated output remains unavailable rather than synthesized.

## Summary

- Non-synthetic FOR-412 source subdraws: 32
- Isolated color-target samples available: 0
- Isolated color-target samples unavailable: 16
- Source-vs-isolated comparisons unavailable: 32
- Isolated-vs-mutation comparisons unavailable: 16

## Interpretation

The 16 mutating transitions remain real: draw 1 accounts for 6 records and draw 3 for 10. Each included record has real, non-synthetic FOR-412 zero shader returns and a FOR-414 immediate post-draw mutation. This ticket refuses to promote that to a direct no-blend color-target result because no such target was encoded.

To turn this into a direct observation, the next slice needs a narrowly gated runtime diagnostic that replays the two cover subdraws into a separate RGBA16Float scratch target with `blend = null`, then reads the same 16 FOR-401 pixels.

## Non-goals preserved

- No rendering correction.
- No default behavior, threshold, score, route, fallback, or promotion change.
- No extension outside M60 F16 / `StencilCoverAaPolygonDraw` / `kSrcOver`.
- No synthetic zero source.
- No Ganesh, Graphite, or SkSL compiler work.

## Validation

Expected local commands:

```text
rtk python3 scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py
rtk python3 scripts/validate_for415_m60_f16_aa_stencil_cover_blend_render_pass_state.py
rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py
rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py
rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for416-pycache python3 -m py_compile scripts/validate_for416_m60_f16_aa_stencil_cover_isolated_color_target.py
```
