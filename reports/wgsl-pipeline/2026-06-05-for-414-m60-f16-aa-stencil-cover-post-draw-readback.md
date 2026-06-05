# FOR-414 M60 F16 AA stencil-cover post-draw readback

Date: 2026-06-05

## Result

Global classification: `post-draw-matches-next-predraw`.

FOR-414 reuses the existing FOR-405 opt-in runtime readback because it already captures the requested boundary: after each M60 F16 `StencilCoverAaPolygonDraw` render pass and before the following draw. No new rendering code, default behavior, threshold, scoring, or promotion state changes.

## Evidence

- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-for-414-m60-f16-capturer-letat-texture-immediatement-apres-draw-aa-stencil-cover`
- Source finding: `global/kanvas/findings/for-413-correle-les-transitions-de-draw-aa-stencil-cover-et-localise-des-mutations-malgre-retour-shader-zero`
- FOR-401 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-405 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json`
- FOR-410 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json`
- FOR-412 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json`
- FOR-413 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-draw-transition-correlation-for413/m60-f16-aa-stencil-cover-draw-transition-correlation-for413.json`

## Runtime boundary

`SkWebGpuDevice.encodePendingDrawsToIntermediate` encodes each M60 F16 AA stencil-cover draw in its own render pass. The FOR-405 readback is encoded after that pass ends and before the loop continues to the next pending draw. That is the immediate post-draw texture boundary requested by FOR-414, so this ticket adds a dedicated correlation artifact instead of a duplicate runtime hook.

## Summary

- Selected pixels: 16
- Post-draw records: 48
- Draws inspected: [1, 3, 5]
- Classification counts: {'post-draw-matches-next-predraw': 47, 'post-draw-still-before': 1}
- Per-draw classification counts: {'1': {'post-draw-matches-next-predraw': 15, 'post-draw-still-before': 1}, '3': {'post-draw-matches-next-predraw': 16}, '5': {'post-draw-matches-next-predraw': 16}}
- Zero-return mutating transitions matching the next boundary: 16
- Post-draw unavailable records: 0

## Interpretation

For every FOR-413 transition classified `draw-mutates-despite-zero-shader-return`, the immediate post-draw readback is already equal to the next FOR-410/FOR-405 boundary under `1e-06` tolerance. This localizes the mutation to the draw render pass itself, below the shader-return boundary captured by FOR-412.

One draw-1 transition is classified `post-draw-still-before`; its FOR-413 transition had unavailable shader returns, so FOR-414 keeps it outside the zero-return proof and does not synthesize a zero source.

## Next decision

The next ticket should inspect the blend/render-pass/store path inside draw 1 and draw 3 rather than looking later between draw boundaries. The texture state has already changed immediately after the mutating draw render pass.

## Validation

- `rtk python3 scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py`
- `rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py`
- `rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py`
- `rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for414-pycache python3 -m py_compile scripts/validate_for414_m60_f16_aa_stencil_cover_post_draw_readback.py`
