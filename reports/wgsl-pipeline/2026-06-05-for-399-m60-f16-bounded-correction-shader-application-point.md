# FOR-399 M60 F16 bounded correction shader application point

Date: 2026-06-05

## Decision

Classification: `correction-overwritten-by-stencil-cover-composition`

The FOR-399 opt-in shader diagnostic proves that the bounded correction branch is reached for the
8 FOR-397 pixels, but every captured application-point sample has zero coverage contribution:

- candidate branch hit: `8 / 8`
- effective contribution hints: `0 / 8`
- non-zero pre-blend inputs: `0 / 8`
- FOR-398 changed pixels: `0`
- FOR-399 diagnostic changed pixels: `0`
- current residual: `62748`
- corrected residual: `62748`
- gain: `0`

This keeps the FOR-398 refusal intact. The correction is not promoted because the reached shader
branch does not contribute to the final pixels at the captured AA stencil-cover application point.

## Scope

Guard: `kanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled`

The diagnostic remains disabled by default and is allocated only for the M60 F16 AA stencil-cover
path when the bounded FOR-398 correction is also enabled. It records only the 8 FOR-397 predicate
pixels.

Captured fields:

- side: `fs_inside` or `fs_outside`
- candidate branch reached
- color after `apply_color_filter`
- color after `apply_target_colorspace_if_needed`
- color sent to blend before quantization
- coverage alpha used
- source alpha after coverage
- quantized color sent to blend
- final-pixel contribution hint

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-bounded-correction-shader-application-point-for399/m60-f16-bounded-correction-shader-application-point-for399.json`

Source finding:
`global/kanvas/findings/for-398-applique-une-sonde-de-correction-m60-f16-bornee-mais-refuse-la-promotion-faute-de-gain`

Non-goals preserved:

- supportClaim remains `false`
- promoted remains `false`
- default rendering remains unchanged
- no threshold or scoring change
- no M60 F16 promotion
- no FOR-380 broad correction reintroduction

## Validation

- `rtk python3 scripts/validate_for399_m60_f16_bounded_correction_shader_application_point.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for399-pycache-parent python3 -m py_compile scripts/validate_for399_m60_f16_bounded_correction_shader_application_point.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16BoundedCorrectionApplicationPointDiagnostic.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
