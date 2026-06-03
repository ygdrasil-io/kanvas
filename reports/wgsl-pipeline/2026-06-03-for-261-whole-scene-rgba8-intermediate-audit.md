# FOR-261 Whole-Scene RGBA8 Intermediate Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-261 replaces the FOR-260 final-byte proxy evidence with live whole-scene
WebGPU renders for:

- current policy: normal `RGBA16Float` intermediate store/load before present;
- diagnostic candidate: constructor-scoped `RGBA8Unorm` intermediate store/load
  before present.

This is not a production default switch. The audit adds no renderer property,
no normal shader change, no threshold change, no Crop correction, no fallback
policy change, and does not globally enable `targetColorSpaceBlend`.

The existing unsupported reason remains preserved:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/whole-scene-rgba8-intermediate-audit-for261/whole-scene-rgba8-intermediate-audit-for261.json`
- `reports/wgsl-pipeline/scenes/artifacts/whole-scene-rgba8-intermediate-audit-for261/whole-scene-rgba8-intermediate-audit-for261.json`

Coverage:

| Case | Scope | Current exact similarity | Candidate exact similarity | Current max delta | Candidate max delta | Status | Route |
|---|---:|---:|---:|---:|---:|---|---|
| `residual-route.simple-offsetimagefilter` | 128000 whole-scene pixels | 94.8265625 | 96.83125 | 1 | 1 | `CORRECTION_SIGNAL` | `webgpu.image-filter.offset-crop-prepass-and-src-over` |
| `residual-route.bitmap-rect-nearest` | 4096 whole-scene pixels | 92.578125 | 100.0 | 1 | 0 | `CORRECTION_SIGNAL` | `webgpu.image-rect.strict-nearest` |
| `exact-control.solid-rect` | 64 whole-scene pixels | 100.0 | 100.0 | 0 | 0 | `UNCHANGED` | `webgpu.coverage.analytic-rect` |
| `exact-control.linear-gradient-rect` | 4096 whole-scene pixels | 100.0 | 100.0 | 0 | 0 | `UNCHANGED` | `webgpu.generated.linear-gradient.rect` |
| `precision-fixture.m60-target-colorspace-neutral-aa` | 4 whole-scene pixels | 50.0 | 50.0 | 13 | 13 | `UNCHANGED` | `webgpu.present-pass.srgb-to-rec2020-after-blend` |

Route diagnostics:

- `bitmap-rect-nearest` uses
  `reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/route-gpu.json`.
- `solid-rect` uses
  `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`.
- `linear-gradient-rect` uses
  `reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/route-gpu.json`.
- `m60-target-colorspace-neutral-aa` uses
  `reports/wgsl-pipeline/scenes/artifacts/m60-target-colorspace-neutral-aa/stats.json`
  because that fixture records `sourceRoute` and `targetRoute` there.

## Finding

The candidate improves the two residual whole-scene equivalents:

- `simple-offsetimagefilter`: 121378/128000 exact pixels to 123944/128000;
- `bitmap-rect-nearest`: 3792/4096 exact pixels to 4096/4096.

The two exact FOR-260 controls stay byte-exact with the real `RGBA8Unorm`
intermediate candidate.

Safe correction is still not proven because the precision-sensitive neutral AA
fixture is unchanged: both policies produce `[115,115,115,255]` against
reference `[128,128,128,255]` at the residual sample, with max delta 13. The
known matching diagnostic condition for this fixture remains
`targetColorSpaceBlend`, which FOR-261 deliberately does not enable globally.

The missing condition is:

```text
missing_precision_sensitive_whole_scene_rgba8_intermediate_correction_without_targetColorSpaceBlend
```

The remaining boundary stays:

```text
rgba16float-intermediate-store-to-present-byte-quantization-policy
```

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-261*'`
- `rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py`
- `rtk python3 scripts/validate_for260_intermediate_quantization_candidate_audit.py`
- `rtk python3 scripts/validate_for259_intermediate_store_present_audit.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
