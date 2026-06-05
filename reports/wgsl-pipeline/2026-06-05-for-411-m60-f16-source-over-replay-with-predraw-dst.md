# FOR-411 M60 F16 source-over replay with predraw dst

Date: 2026-06-05

## Result

Classification: `source-over-replay-differs-post-pass`.

FOR-411 consumes the `dstBeforeRgbaFloat` captured by FOR-410 and replays the
FOR-408 source-over subdraws in `drawIndex` then `subdrawOrdinal` order. The
replay is no longer blocked by the initial destination state, but it does not
reproduce the FOR-405 post-pass colors for the 16 FOR-401 pixels.

This is diagnostic evidence only. Rendering, scoring, thresholds, and scene
promotion are unchanged.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-over-replay-with-predraw-dst-for411/m60-f16-source-over-replay-with-predraw-dst-for411.json`

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-408 per-subdraw hook | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json` |
| FOR-409 previous source-over replay | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-source-over-replay-for409/m60-f16-aa-stencil-cover-source-over-replay-for409.json` |
| FOR-410 predraw dst readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json` |
| FOR-410 memory finding | `global/kanvas/findings/for-410-capture-letat-destination-predraw-m60-f16-avant-aa-stencil-cover` |
| FOR-411 draft memory | `global/kanvas/tickets/drafts/brouillon-ticket-for-411-m60-f16-rejouer-source-over-avec-dst-before-predraw-capture` |

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `16` |
| Predraw dst consumed pixels | `16` |
| Observed replay-input subdraws | `44` |
| Used subdraws | `44` |
| Excluded subdraws | `52` |
| Post-pass observed pixels | `16` |
| Matches post-pass | `0` |
| Differs from post-pass | `16` |
| Still insufficient inputs | `0` |
| Not applicable | `0` |

## Pixel Replay

| Coordinate | dstBefore | Used subdraws | Replayed RGBA float | Post-pass RGBA float | Max delta | Classification |
|---|---|---:|---|---|---:|---|
| (92, 75) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (91, 76) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (90, 77) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (89, 78) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (88, 79) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (87, 80) | `[1.0, 1.0, 1.0, 1.0]` | `4` | `[1.0, 1.0, 1.0, 1.0]` | `[0.709472656, 0.748535156, 0.901855469, 1.0]` | `0.290527344` | `source-over-replay-differs-post-pass` |
| (101, 37) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (102, 37) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (99, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (100, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (101, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (102, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (103, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (104, 38) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (98, 39) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |
| (99, 39) | `[1.0, 1.0, 1.0, 1.0]` | `2` | `[1.0, 1.0, 1.0, 1.0]` | `[0.0, 0.541015625, 0.297851563, 1.0]` | `1.0` | `source-over-replay-differs-post-pass` |

## Interpretation

The replay starts from the real FOR-410 destination state. The replayed
FOR-408 source/coverage inputs still leave the selected pixels at white, while
the FOR-405 post-pass samples are colored. The next diagnostic step should
inspect why the observed AA stencil-cover source/coverage inputs do not explain
the post-pass write.

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

- `rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py`
- `rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py`
- `rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py`
- `rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
