# FOR-260 Intermediate Quantization Candidate Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

## Scope

FOR-260 audits the FOR-259 candidate before/after:

- current policy: normal `RGBA16Float` intermediate store/load before present;
- diagnostic candidate: bounded test-side `RGBA8Unorm` store/load, or equivalent
  intermediate quantization, before present reconstruction.

This is not a production policy switch. The audit adds no renderer property, no
normal shader change, no threshold change, no Crop correction, no fallback-policy
change, and does not globally enable `targetColorSpaceBlend`.

The existing unsupported reason remains preserved:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Evidence

Artifact:

- `reports/wgsl-pipeline/scenes/generated/artifacts/intermediate-quantization-candidate-audit-for260/intermediate-quantization-candidate-audit-for260.json`
- `reports/wgsl-pipeline/scenes/artifacts/intermediate-quantization-candidate-audit-for260/intermediate-quantization-candidate-audit-for260.json`

Coverage:

| Case | Scope | Current exact similarity | Candidate exact similarity | Current max delta | Candidate max delta | Route |
|---|---:|---:|---:|---:|---:|---|
| `legacy-source-color-uniform.simple-offset-row1-col0` | 1 representative FOR-259 sample | 0.0 | 100.0 | 1 | 0 | `webgpu.canvas.draw-rect.src-over` |
| `bitmap-texel-upload-sample.bitmap-rect-nearest` | 1 representative FOR-259 sample | 0.0 | 100.0 | 1 | 0 | `webgpu.image-rect.strict-nearest` |
| `generated-solid-control.solid-rect` | 64 whole-scene artifact pixels | 100.0 | 100.0 | 0 | 0 | `webgpu.coverage.analytic-rect` |
| `generated-gradient-control.linear-gradient-rect` | 4096 whole-scene artifact pixels | 100.0 | 100.0 | 0 | 0 | `webgpu.generated.linear-gradient.rect` |
| `precision-fixture.m60-target-colorspace-neutral-aa` | 1 precision fixture pixel | 0.0 | 0.0 | 13 | 13 | `webgpu.present-pass.srgb-to-rec2020-after-blend` |

Route diagnostics:

- `solid-rect` uses
  `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`.
- `linear-gradient-rect` uses
  `reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/route-gpu.json`.
- `m60-target-colorspace-neutral-aa` has no `route-gpu.json`; its
  `stats.json` records `sourceRoute` and `targetRoute`, so the JSON records
  that rationale explicitly.

## Finding

The candidate reproduces the reference/oracle bytes for the two FOR-259
representative residual samples:

- `[157,90,138,255] -> [158,90,139,255]`
- `[148,193,207,255] -> [149,193,207,255]`

The two exact controls do not regress under the bounded final-byte diagnostic
proxy. That is useful no-regression evidence, but it is not equivalent to a
whole-scene render where the real intermediate is stored and loaded as
`RGBA8Unorm`.

The precision fixture remains uncorrected by intermediate byte quantization:
post-present output stays `[115,115,115,255]` against reference
`[128,128,128,255]`. The known matching diagnostic path for this fixture is
`targetColorSpaceBlend`, which FOR-260 deliberately does not enable globally.

Safe correction is therefore not proven. The missing condition is:

```text
missing_whole_scene_intermediate_rgba8_candidate_evidence_for_exact_and_precision_sensitive_routes
```

The remaining boundary stays:

```text
rgba16float-intermediate-store-to-present-byte-quantization-policy
```

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-260*'`
- `rtk python3 scripts/validate_for260_intermediate_quantization_candidate_audit.py`
- `rtk python3 scripts/validate_for259_intermediate_store_present_audit.py`
- `rtk python3 scripts/validate_for258_shader_side_diagnostic_probe.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
