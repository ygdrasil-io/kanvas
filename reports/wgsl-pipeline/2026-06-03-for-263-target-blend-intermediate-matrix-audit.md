# FOR-263 TargetColorSpaceBlend x IntermediateFormat Matrix Audit

Decision: `KEEP_DIAGNOSTIC`

FOR-263 compares the test-only matrix of `targetColorSpaceBlend` and
`intermediateFormat` without changing production defaults, shaders,
thresholds, Crop policy, fallback policy, or global renderer properties.

Preserved fallback reason:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Artifacts

- `reports/wgsl-pipeline/scenes/generated/artifacts/target-blend-intermediate-matrix-audit-for263/target-blend-intermediate-matrix-audit-for263.json`
- `reports/wgsl-pipeline/scenes/artifacts/target-blend-intermediate-matrix-audit-for263/target-blend-intermediate-matrix-audit-for263.json`

## Cases

| Case | Policy | Status | Exact similarity | Max delta | Matching pixels | Route diagnostics | Classification |
|---|---|---|---:|---:|---:|---|---|
| `positive-fixture.m60-target-colorspace-neutral-aa` | `targetBlend-false-rgba16float` | `rendered` | 50 | 13 | 2/4 | `webgpu.target-colorspace-blend.solid-coverage` | `targetColorSpaceBlend` |
| `positive-fixture.m60-target-colorspace-neutral-aa` | `targetBlend-false-rgba8unorm` | `rendered` | 50 | 13 | 2/4 | `webgpu.target-colorspace-blend.solid-coverage` | `targetColorSpaceBlend` |
| `positive-fixture.m60-target-colorspace-neutral-aa` | `targetBlend-true-rgba16float` | `rendered` | 100 | 0 | 4/4 | `webgpu.target-colorspace-blend.solid-coverage` | `targetColorSpaceBlend` |
| `positive-fixture.m60-target-colorspace-neutral-aa` | `targetBlend-true-rgba8unorm` | `rendered` | 100 | 0 | 4/4 | `webgpu.target-colorspace-blend.solid-coverage` | `targetColorSpaceBlend` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | `targetBlend-false-rgba16float` | `rendered` | 89.59554 | 39 | 22019/24576 | `webgpu.coverage.stroke-cap-join.experimental-render` | `targetColorSpaceBlend-improves-but-insufficient-parity` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | `targetBlend-false-rgba8unorm` | `rendered` | 89.59554 | 39 | 22019/24576 | `webgpu.coverage.stroke-cap-join.experimental-render` | `targetColorSpaceBlend-improves-but-insufficient-parity` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | `targetBlend-true-rgba16float` | `rendered` | 95.914714 | 48 | 23572/24576 | `webgpu.coverage.stroke-cap-join.experimental-render` | `targetColorSpaceBlend-improves-but-insufficient-parity` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | `targetBlend-true-rgba8unorm` | `rendered` | 95.784505 | 48 | 23540/24576 | `webgpu.coverage.stroke-cap-join.experimental-render` | `targetColorSpaceBlend-improves-but-insufficient-parity` |
| `exact-control.black-white-rect` | `targetBlend-false-rgba16float` | `rendered` | 100 | 0 | 64/64 | `webgpu.coverage.analytic-rect` | `none-needed` |
| `exact-control.black-white-rect` | `targetBlend-false-rgba8unorm` | `rendered` | 100 | 0 | 64/64 | `webgpu.coverage.analytic-rect` | `none-needed` |
| `exact-control.black-white-rect` | `targetBlend-true-rgba16float` | `rendered` | 100 | 0 | 64/64 | `webgpu.coverage.analytic-rect` | `none-needed` |
| `exact-control.black-white-rect` | `targetBlend-true-rgba8unorm` | `rendered` | 100 | 0 | 64/64 | `webgpu.coverage.analytic-rect` | `none-needed` |
| `for261-residual.simple-offsetimagefilter` | `targetBlend-false-rgba16float` | `rendered` | 94.826563 | 1 | 121378/128000 | `webgpu.image-filter.offset-crop-prepass-and-src-over` | `intermediateFormat-when-targetColorSpaceBlend-refused` |
| `for261-residual.simple-offsetimagefilter` | `targetBlend-false-rgba8unorm` | `rendered` | 96.83125 | 1 | 123944/128000 | `webgpu.image-filter.offset-crop-prepass-and-src-over` | `intermediateFormat-when-targetColorSpaceBlend-refused` |
| `for261-residual.simple-offsetimagefilter` | `targetBlend-true-rgba16float` | `refused-before-render` | refused | refused | refused/128000 | `color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw` | `intermediateFormat-when-targetColorSpaceBlend-refused` |
| `for261-residual.simple-offsetimagefilter` | `targetBlend-true-rgba8unorm` | `refused-before-render` | refused | refused | refused/128000 | `color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw` | `intermediateFormat-when-targetColorSpaceBlend-refused` |

## Case Decisions

| Case | Correction status | Best rendered policy | Admissibility | Dimension responsible |
|---|---|---|---|---|
| `positive-fixture.m60-target-colorspace-neutral-aa` | `CORRECTION_SIGNAL` | `targetBlend-true-rgba16float` | `ADMISSIBLE_DIAGNOSTIC_ONLY` | `targetColorSpaceBlend` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | `CORRECTION_SIGNAL_INSUFFICIENT` | `targetBlend-true-rgba16float` | `REFUSED_INSUFFICIENT_PARITY` | `targetColorSpaceBlend-improves-but-insufficient-parity` |
| `exact-control.black-white-rect` | `UNCHANGED` | `targetBlend-false-rgba16float` | `REFUSED_NOT_NEEDED` | `none-needed` |
| `for261-residual.simple-offsetimagefilter` | `REFUSED_FOR_TARGET_BLEND_POLICIES` | `targetBlend-false-rgba8unorm` | `REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND` | `intermediateFormat-when-targetColorSpaceBlend-refused` |

## Conclusion

none_applied: targetColorSpaceBlend remains diagnostic for an isolated and insufficient-parity family scope, RGBA8Unorm remains diagnostic for FOR-261 residual intermediate-boundary cases, and targetColorSpaceBlend refuses the image-filter residual route for both intermediate formats

Missing condition: `missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation`.

The remaining FOR-261 boundary stays `rgba16float-intermediate-store-to-present-byte-quantization-policy`.

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-263*'
rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py
rtk python3 scripts/validate_for262_target_colorspace_blend_scope_audit.py
rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
