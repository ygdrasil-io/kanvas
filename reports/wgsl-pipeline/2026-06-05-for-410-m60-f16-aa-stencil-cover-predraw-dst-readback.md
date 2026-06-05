# FOR-410 M60 F16 AA stencil-cover predraw dst readback

Date: 2026-06-05

## Result

Classification: `predraw-dst-captured`.

The diagnostic is opt-in behind
`kanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled`, disabled by
default. It is bounded to the M60 F16 `StencilCoverAaPolygonDraw` path by the
existing M60 band metadata transport and samples only the 16 FOR-401
coordinates.

The hook performs a compute/copy readback from the RGBA16Float intermediate
immediately before each inspected AA stencil-cover render pass. It does not
modify the render pass, blend state, thresholds, scoring, or default route.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-predraw-dst-readback-for410/m60-f16-aa-stencil-cover-predraw-dst-readback-for410.json`

| Source | Path |
|---|---|
| FOR-401 selected residual coordinates | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-408 per-subdraw hook | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-per-subdraw-hook-for408/m60-f16-aa-stencil-cover-per-subdraw-hook-for408.json` |
| FOR-409 source-over replay | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-source-over-replay-for409/m60-f16-aa-stencil-cover-source-over-replay-for409.json` |
| FOR-410 draft memory | `global/kanvas/tickets/drafts/brouillon-ticket-for-410-m60-f16-capturer-letat-destination-avant-aa-stencil-cover-pour-le-replay-source-over` |
| FOR-409 finding memory | `global/kanvas/findings/for-409-confirme-que-le-replay-source-over-m60-f16-manque-encore-letat-initial-destination` |

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `16` |
| Predraw captured pixels | `16` |
| Predraw unavailable pixels | `0` |
| Inspected AA stencil-cover draws | `3` |
| FOR-408 observed replay-input subdraws | `44` |
| FOR-409 replay-possible pixels after FOR-410 input | `16` |
| Post-pass observed pixels | `16` |

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `false` |
| promoted=false | `false` |
| correctionAppliedByDefault=false | `false` |
| defaultRenderingChanged=false | `false` |
| thresholdChanged=false | `false` |
| scoringChanged=false | `false` |

## Notes

The captured predraw destination is the real intermediate texture state read at
the boundary before the AA stencil-cover render pass. The artifact preserves
`dstBeforeRgbaFloat = null` semantics for unavailable samples; this run did not
need that fallback because all 16 FOR-401 pixels were captured.

FOR-409 remains a diagnostic replay artifact and is not changed here to claim
`matches` or `differs`; FOR-410 only supplies the missing predraw input for a
future replay update.

## Validations

- `rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py`
- `rtk python3 scripts/validate_for409_m60_f16_aa_stencil_cover_source_over_replay.py`
- `rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverSourceOverReplay.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
