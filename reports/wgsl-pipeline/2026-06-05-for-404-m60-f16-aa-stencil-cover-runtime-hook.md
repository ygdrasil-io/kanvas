# FOR-404 M60 F16 AA stencil-cover runtime hook

## Scope

FOR-404 adds a bounded opt-in runtime hook at the
`StencilCoverAaPolygonDraw` M60 F16 AA stencil-cover path. The hook is guarded
by `kanvas.webgpu.m60F16DirectPassWriteHook.enabled`, disabled by default, and
does not change default rendering, thresholds, support policy, promotion state,
or correction behavior.

## Source evidence reused

- FOR-403 finding:
  `global/kanvas/findings/for-403-refuse-le-hook-direct-pass-write-m60-f16-sans-frontiere-post-draw-pre-readback`
- FOR-403 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-pass-write-hook-for403/m60-f16-direct-pass-write-hook-for403.json`
- FOR-403 classification: `direct-pass-write-hook-inconclusive`
- FOR-401 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-401 current total residual: `62748`
- FOR-401 selected residual total: `1560`
- FOR-401 selected pixel count: `16`
- FOR-402 classification: `pass-write-probe-inconclusive`
- FOR-400 remains context only and is not used as direct writer proof.

## FOR-404 result

- Linear: `FOR-404`
- Artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json`
- Runtime API:
  `SkWebGpuDevice.m60F16AaStencilCoverPostPassRuntimeHookSnapshot()`
- Classification: `aa-stencil-cover-post-pass-readback-blocked`
- Taxonomy:
  `aa-stencil-cover-post-pass-observed`,
  `aa-stencil-cover-post-pass-readback-blocked`,
  `aa-stencil-cover-pass-not-targeting-coordinate`,
  `aa-stencil-cover-runtime-hook-inconclusive`
- Runtime events: `3`
- Selected pixels inspected: `16`
- Post-pass readback blocked pixels: `16`
- Post-pass observed pixels: `0`
- Current total residual preserved from FOR-401: `62748`
- `supportClaim=false`, `promoted=false`, `correctionAppliedByDefault=false`

## Interpretation

FOR-403 refused a global direct pass-write claim because Kanvas did not expose
a post-draw/pre-readback boundary with draw id and pipeline family. FOR-404
narrows that refusal to the concrete `StencilCoverAaPolygonDraw` branch:
after encoding the stencil write and the inside/outside AA cover sub-draws,
the guarded hook records the draw index, pipeline family, scissor, fill type,
blend mode, and the exact 16 FOR-401 coordinates.

The hook proves that the AA stencil-cover runtime boundary was reached and
that the coordinates were targeted by the pass scissor. It still cannot sample
the color texture between that render pass and the final present/readback, so
the result remains a runtime-local refusal:
`aa-stencil-cover-post-pass-readback-blocked`.

FOR-400 contribution samples remain contextual only. They are not converted
into proof of a direct pass write.

## Non-goals preserved

- No M60 F16 correction was applied.
- M60 F16 remains unsupported: `supportClaim=false`.
- M60 F16 remains unpromoted: `promoted=false`.
- No similarity threshold or scoring rule changed.
- FOR-380 was not reintroduced.
- No default rendering behavior changed.
- The hook is bounded to M60 F16 `StencilCoverAaPolygonDraw` evidence.

## Validation commands

- `rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py`
- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk git diff --check`
