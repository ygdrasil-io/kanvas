# FOR-400 M60 F16 coverage/stencil contribution map

Date: 2026-06-05

## Summary

FOR-400 adds a diagnostic-only, opt-in coverage/stencil contribution map around the 8 FOR-397
M60 F16 AA stencil-cover pixels. The diagnostic is disabled by default through
`kanvas.webgpu.m60F16CoverageStencilContributionMap.enabled` and only runs with the bounded
FOR-398 runtime correction path enabled.

The bounded radius-1 window exports 48 unique pixel coordinates. The raw WebGPU readback observed
144 per-draw slots, then the report producer collapsed them to the strict 48-coordinate sample
limit for the artifact.

## Result

- Linear: `FOR-400`
- Source finding: `global/kanvas/findings/for-399-prouve-que-la-correction-m60-f16-bornee-atteint-le-shader-mais-ne-contribue-pas-aux-pixels-finaux`
- Classification: `predicate-window-zero-contribution`
- Window radius: `1`
- Strict exported sample limit: `48`
- Raw readback samples: `144`
- Observed exported samples: `48`
- FOR-397 predicate samples observed: `8 / 8`
- FOR-397 candidate branch reached: `8 / 8`
- Effective contribution count: `0`
- Predicate effective contribution: `0`
- neighbor effective contribution: `0`
- Dominant useful side: `none`
- Current residual: `62748`
- Corrected residual: `62748`
- FOR-398 changed pixels: `0`
- FOR-400 diagnostic changed pixels: `0`
- `supportClaim=false`
- `promoted=false`

## Evidence

- Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-stencil-contribution-map-for400/m60-f16-coverage-stencil-contribution-map-for400.json`
- Producer: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/StrokeCapJoinSceneCaptureTest.kt`
- Renderer diagnostic: `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
- Validator: `scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py`

## Interpretation

The FOR-397 pixels still reach the bounded correction branch, but the bounded radius-1
coverage/stencil window does not expose any neighboring sample with non-zero coverage, non-zero
source alpha after coverage, and non-zero blend input. The local FOR-397 window should therefore be
refused as a correction target. The next minimal probe should move to the stencil/cover pass that
actually contributes source color instead of retesting the same FOR-397 coordinates.

## Non-goals preserved

- No new correction was applied.
- No default rendering path was changed.
- No support claim was raised.
- M60 F16 was not promoted.
- No similarity threshold was changed.
- The broad FOR-380 correction was not reintroduced.

## Validation commands

- `rtk python3 scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for400-pycache-parent python3 -m py_compile scripts/validate_for400_m60_f16_coverage_stencil_contribution_map.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
