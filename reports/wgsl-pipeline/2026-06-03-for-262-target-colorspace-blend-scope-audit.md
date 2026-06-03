# FOR-262 TargetColorSpaceBlend Scope Audit

Decision: `KEEP_DIAGNOSTIC`

FOR-262 compares whole-scene `targetColorSpaceBlend=false` and
`targetColorSpaceBlend=true` without changing production defaults,
normal shaders, thresholds, Crop policy, fallback policy, or the
normal `RGBA16Float` intermediate policy.

Preserved fallback reason:

```text
image-filter.crop-input-nonnull-prepass-required
```

## Artifacts

- `reports/wgsl-pipeline/scenes/generated/artifacts/target-colorspace-blend-scope-audit-for262/target-colorspace-blend-scope-audit-for262.json`
- `reports/wgsl-pipeline/scenes/artifacts/target-colorspace-blend-scope-audit-for262/target-colorspace-blend-scope-audit-for262.json`

## Cases

| Case | Current exact | Target exact | Current max delta | Target max delta | Matching pixels | Status | Route diagnostics | Admissibility |
|---|---:|---:|---:|---:|---:|---|---|---|
| `positive-fixture.m60-target-colorspace-neutral-aa` | 50 | 100 | 13 | 0 | 2/4 -> 4/4 | `CORRECTION_SIGNAL` | `webgpu.target-colorspace-blend.solid-coverage` | `ADMISSIBLE_DIAGNOSTIC_ONLY` |
| `insufficient-scope.m60-bounded-stroke-cap-join` | 89.59554 | 95.914714 | 39 | 48 | 22019/24576 -> 23572/24576 | `CORRECTION_SIGNAL_INSUFFICIENT` | `webgpu.coverage.stroke-cap-join.experimental-render` | `REFUSED_INSUFFICIENT_PARITY` |
| `exact-control.black-white-rect` | 100 | 100 | 0 | 0 | 64/64 -> 64/64 | `UNCHANGED` | `webgpu.coverage.analytic-rect` | `REFUSED_NOT_NEEDED` |
| `for261-residual.simple-offsetimagefilter` | 94.826563 | refused | 1 | refused | 121378/128000 -> refused | `REFUSED` | `color-space.target-blend-unsupported-draw-kind:LayerCompositeDraw` | `REFUSED_UNSUPPORTED_TARGET_BLEND_DRAW_KIND` |

## Conclusion

none_applied: targetColorSpaceBlend remains diagnostic because the only exact correction is isolated, the bounded stroke cap/join scene still misses exact parity, an exact control does not need the mode, and the FOR-261 residual image-filter route is outside the target-blend draw-family scope

Missing condition: `missing_family_bound_proof_for_target_colorspace_blend_and_intermediate_boundary_separation`.

The remaining FOR-261 boundary stays `rgba16float-intermediate-store-to-present-byte-quantization-policy`.

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-262*'
rtk python3 scripts/validate_for262_target_colorspace_blend_scope_audit.py
rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py
rtk python3 scripts/validate_for260_intermediate_quantization_candidate_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
