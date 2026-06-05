# FOR-409 M60 F16 AA stencil-cover source-over replay

Date: 2026-06-05

## Result

Classification: `source-over-replay-insufficient-inputs`.

The diagnostic is opt-in behind `kanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled`, disabled by default. It is bounded
to M60 F16, `StencilCoverAaPolygonDraw`, `kSrcOver`, and the 16 FOR-401
coordinates. It reuses FOR-408 per-subdraw source/coverage data and FOR-405
post-pass colors, but it does not synthesize an initial destination color.

The replay cannot classify `matches` or `differs` because the exact
premultiplied destination RGBA float before the first observed subdraw is not
available. The correct conservative result is therefore
`source-over-replay-insufficient-inputs`.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-source-over-replay-for409/m60-f16-aa-stencil-cover-source-over-replay-for409.json`

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-406 post-pass/reference comparison | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-post-pass-reference-comparison-for406/m60-f16-post-pass-reference-comparison-for406.json` |
| FOR-408 per-subdraw hook | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json` |
| FOR-408 memory finding | `global/kanvas/findings/for-408-ajoute-le-hook-per-subdraw-aa-stencil-cover-mais-confirme-le-blocage-framebuffer-m60-f16` |
| FOR-409 draft memory | `global/kanvas/tickets/drafts/brouillon-ticket-for-409-m60-f16-replay-diagnostique-source-over-hors-fixed-function-blend` |

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `16` |
| Observed replay-input subdraws | `44` |
| Used subdraws | `0` |
| Excluded subdraws | `96` |
| Post-pass observed pixels | `16` |
| Initial-state missing pixels | `16` |
| Insufficient-input pixels | `16` |
| No-observed-subdraw pixels | `0` |

## Pixel Replay

| Coordinate | Observed subdraws | Used | Excluded | Initial state | Replayed RGBA float | Post-pass RGBA float | Delta | Classification |
|---|---:|---:|---:|---|---|---|---|---|
| (92, 75) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (91, 76) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (90, 77) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (89, 78) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (88, 79) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (87, 80) | `4` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (101, 37) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (102, 37) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (99, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (100, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (101, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (102, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (103, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (104, 38) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (98, 39) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |
| (99, 39) | `2` | `0` | `6` | `source-over-replay-initial-state-unavailable` | `None` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `None` | `source-over-replay-insufficient-inputs` |

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `false` |
| promoted=false | `false` |
| correctionAppliedByDefault=false | `false` |
| defaultRenderingChanged=false | `false` |
| thresholdChanged=false | `false` |
| scoringChanged=false | `false` |

## Validations

- `rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py`
- `rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py`
- `rtk python3 scripts/validate_for407_m60_f16_aa_stencil_cover_contribution_isolation.py`
- `rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
