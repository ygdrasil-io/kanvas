# FOR-401 M60 F16 final residual origin map

## Scope

FOR-401 adds a diagnostic-only, opt-in final residual origin map for
`non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`.

The diagnostic is disabled by default behind
`kanvas.webgpu.m60F16FinalResidualOriginMap.enabled`. It writes only the FOR-401
artifact during scene-evidence runs and does not change default rendering,
similarity thresholds, support policy, or promotion state.

## Historical evidence reused

- Basic Memory source:
  `global/kanvas/findings/for-400-prouve-que-la-fenetre-coverage-stencil-m60-f16-autour-des-pixels-for-397-ne-contribue-pas`
- FOR-400 classification: `predicate-window-zero-contribution`
- FOR-400 window: radius `1`, `48` unique pixels around the 8 FOR-397 pixels
- FOR-400 effective contribution count: `0`
- FOR-400 useful neighbors: `0`
- Residual preserved by FOR-400: `62748 -> 62748`
- `supportClaim=false`, `promoted=false`

## FOR-401 result

- Linear: `FOR-401`
- Artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- Classification: `residual-visible-only-at-final-readback`
- Closed classifications:
  `residual-carried-outside-for400-window`,
  `residual-carried-by-other-draw-path`,
  `residual-visible-only-at-final-readback`,
  `residual-origin-inconclusive`
- Current total residual: `62748`
- Current mismatch pixels: `1615`
- Selection policy: final pixels with non-zero total residual, sorted by
  `residualTotal desc`, `y asc`, `x asc`, bounded to `16` samples
- Selected residual total: `1560`
- Selected pixels in FOR-397 predicate: `6`
- Selected pixels in FOR-400 window: `6`
- Selected pixels outside FOR-400 window: `10`
- `writtenByM60AaStencilCover`: `0`
- `writtenByOtherPath`: `0`
- `readbackOnlyUnknown`: `16`

The 6 selected pixels that overlap the FOR-397/FOR-400 window are still
`readbackOnlyUnknown` because FOR-400 exported the same coordinates with zero
effective contribution: coverage `0`, source alpha after coverage `0`, and no
blend input. The remaining 10 selected pixels are outside the FOR-400 window.

## Interpretation

FOR-401 does not find an effective M60 AA stencil-cover writer for any selected
top-residual final pixel using the available FOR-400 writer evidence. The
residual is visible in the final GPU/reference readback, but the current
bounded shader-side contribution evidence cannot attribute it to the inspected
FOR-400 stencil-cover window.

## Non-goals preserved

- No correction was applied.
- M60 F16 remains unsupported: `supportClaim=false`.
- M60 F16 remains unpromoted: `promoted=false`.
- No similarity threshold or scoring rule changed.
- FOR-380 was not reintroduced.
- The diagnostic is not generalized beyond the M60 F16 scene.

## Next step

Instrument the actual selected final residual coordinates with the smallest
draw/pass write trace that can distinguish:

1. a later M60 AA stencil-cover write outside the FOR-400 radius-1 window;
2. another draw path carrying the residual;
3. final readback or packing-only visibility.

Do not attempt another shader correction until that writer boundary is known.

## Validation commands

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for401_m60_f16_final_residual_origin_map.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for401-pycache-agent python3 -m py_compile scripts/validate_for401_m60_f16_final_residual_origin_map.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
