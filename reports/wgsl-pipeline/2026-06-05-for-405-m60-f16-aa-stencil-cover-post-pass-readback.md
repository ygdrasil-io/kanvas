# FOR-405 M60 F16 AA stencil-cover post-pass readback

## Scope

FOR-405 adds a diagnostic-only post-pass readback for the M60 F16
`StencilCoverAaPolygonDraw` AA stencil-cover path. The readback is guarded by
`kanvas.webgpu.m60F16DirectPassWriteHook.enabled`, disabled by default, and
samples only the 16 FOR-401 residual coordinates after the AA stencil-cover
render pass and before the final present/readback pass.

No default rendering path, support policy, promotion state, threshold, scoring,
or correction behavior changes.

## Source evidence reused

- FOR-404 report:
  `reports/wgsl-pipeline/2026-06-05-for-404-m60-f16-aa-stencil-cover-runtime-hook.md`
- FOR-404 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json`
- FOR-404 classification: `aa-stencil-cover-post-pass-readback-blocked`
- FOR-401 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-401 current total residual: `62748`
- FOR-401 selected residual total: `1560`
- FOR-401 selected pixel count: `16`
- FOR-400 remains context only and is not used as direct writer proof.

## FOR-405 result

- Linear: `FOR-405`
- Artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json`
- Runtime API:
  `SkWebGpuDevice.m60F16AaStencilCoverPostPassReadbackSnapshot()`
- Diagnostic shader:
  `shaders/m60_f16_aa_stencil_cover_post_pass_readback_for405_diagnostic_only.wgsl`
- Classification: `aa-stencil-cover-post-pass-color-observed`
- Taxonomy:
  `aa-stencil-cover-post-pass-color-observed`,
  `aa-stencil-cover-post-pass-format-unsupported`,
  `aa-stencil-cover-post-pass-copy-blocked`,
  `aa-stencil-cover-post-pass-readback-inconclusive`
- Runtime events: `3`
- Selected pixels inspected: `16`
- Post-pass observed pixels: `16`
- Intermediate format: `RGBA16Float`
- Current total residual preserved from FOR-401: `62748`
- `supportClaim=false`, `promoted=false`, `correctionAppliedByDefault=false`

## Interpretation

FOR-405 turns the FOR-404 runtime boundary into a concrete color readback.
After each bounded M60 F16 `StencilCoverAaPolygonDraw` pass, a diagnostic
compute pass samples the existing intermediate texture with `textureLoad` at
the 16 FOR-401 coordinates, writes those values to a storage buffer, copies the
buffer to staging, and maps it after queue submission.

The local run on `Apple M2 Max` observed post-pass colors for all 16 selected
pixels. The first six pixels read `[181, 191, 230, 255]` after quantization;
the remaining ten read the relevant post-pass color for their draw boundary,
with later draw events showing `[0, 138, 76, 255]` for the green residual
cluster. The artifact keeps every draw event, the selected-pixel collapse, and
the float RGBA values.

This is diagnostic evidence only. It does not identify a correction, does not
promote M60 F16, and does not convert FOR-400 contribution samples into direct
write proof.

## Non-goals preserved

- No M60 F16 correction was applied.
- M60 F16 remains unsupported: `supportClaim=false`.
- M60 F16 remains unpromoted: `promoted=false`.
- No similarity threshold or scoring rule changed.
- FOR-380 was not reintroduced.
- No default rendering behavior changed.
- The readback is bounded to M60 F16 `StencilCoverAaPolygonDraw` evidence.
- FOR-400 remains `context-only-not-direct-write-proof`.

## Validation commands

- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py`
- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
