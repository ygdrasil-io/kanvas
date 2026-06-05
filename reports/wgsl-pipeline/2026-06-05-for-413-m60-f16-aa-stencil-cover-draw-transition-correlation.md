# FOR-413 M60 F16 AA stencil-cover draw-transition correlation

Date: 2026-06-05

## Result

Global classification: `draw-mutates-despite-zero-shader-return`.

FOR-413 correlates existing FOR-401, FOR-405, FOR-410, and FOR-412 artifacts only. It does not change rendering code, default behavior, thresholds, scoring, or promotion state.

## Evidence

- Source draft memory: `global/kanvas/tickets/drafts/brouillon-ticket-for-413-m60-f16-correler-les-transitions-de-draw-aa-stencil-cover-avec-le-retour-shader`
- Source finding: `global/kanvas/findings/for-412-capture-la-source-shader-retourne-au-blend-et-classe-les-16-pixels-comme-zero-shader-return-post-pass-colore-1`
- FOR-401 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-405 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json`
- FOR-410 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json`
- FOR-412 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json`

For each of the 16 FOR-401 pixels, the derived transitions are:

- draw 1 before = FOR-410 predraw draw 1; after = FOR-410 predraw draw 3
- draw 3 before = FOR-410 predraw draw 3; after = FOR-410 predraw draw 5
- draw 5 before = FOR-410 predraw draw 5; after = FOR-405 post-pass sample

## Summary

- Total transitions: 48
- `draw-mutates-despite-zero-shader-return`: 16
- `draw-unchanged-after-zero-shader-return`: 6
- `draw-change-unattributed-shader-unavailable`: 1
- `draw-boundary-unavailable`: 25
- Draw 1 mutates despite zero shader return for 6 pixels.
- Draw 3 mutates despite zero shader return for 10 pixels.
- First state-change draw counts: {1: 7, 3: 9}.

## Interpretation

The strongest classification is distributed across draw 1 and draw 3. FOR-410 provides the before/after boundaries between draw 1, draw 3, draw 5, and the FOR-405 post-pass sample. FOR-412 shows the real non-synthetic `sourceColorSentToBlend` values observed for the mutating zero-return transitions are zero, and the premultiplied source-over replay over the before state does not reproduce the after state.

Six pixels mutate on draw 1 despite zero shader return. Ten pixels mutate on draw 3 despite zero shader return. One additional draw 1 transition mutates while shader returns are unavailable, so it is kept as `draw-change-unattributed-shader-unavailable` and not converted into a zero-source claim. Draw 5 has state boundaries available and remains stable, but FOR-412 does not observe shader returns for draw 5, so the artifact does not fabricate zero sources for it.

## Next decision

Open the next diagnostic below the shader-return boundary for the mutating draw transitions, separating draw 1 and draw 3. Inspect WebGPU blend, render-pass, attachment store/load, or the immediate post-draw texture state around those draws. The current artifacts are sufficient to say the color can appear between predraw boundaries despite zero observed shader returns.

## Validation

- `rtk python3 scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py`
- `rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py`
- `rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for413-pycache python3 -m py_compile scripts/validate_for413_m60_f16_aa_stencil_cover_draw_transition_correlation.py`
