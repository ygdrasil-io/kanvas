# FOR-266 Stroke Cap/Join AA Residual Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

FOR-266 audits `m60-bounded-stroke-cap-join` with normal
`RGBA16Float` intermediate storage under `targetColorSpaceBlend=false`
and `targetColorSpaceBlend=true`. It is diagnostic/test-only and does
not change production defaults, shaders, thresholds, Crop policy,
fallback policy, quantization policy, or general stroke cap/join
support.

Preserved production refusal:

```text
SkWebGpuDevice.filled-path refused coverage selection: WebGpuCoverageSelection(v1)
drawKind=filled-path
strategy=RefuseDiagnostic
route=webgpu.coverage.refuse
coverage=PathCoverage(fillType=Winding,aa=true,inverse=false)
clip=DeviceRect(0,0,192,128)
pathAaBudgets=pathVerbCount=9/96;coverageEdgeCount=8/256;cubicMaxSegmentsPerCubic=n/a/16;dashIntervalCount=n/a/8;clipStackDepth=n/a/4;deviceBounds=14.464466,38.464466,93.53554,81.53554;deviceBoundsMaxSize=79.071075/2048.0;strokeWidth=10.0;strokeCaps=butt;strokeJoins=bevel
pipelineAxes=preimage=pipeline.key v=1 layout=[] code=[coverageKind=pathAaStrokeCapJoinBlocked] state=[pathFillRule=winding,topology=triangleList];hash=238697d06d385ff6fda449ad7ebf5d8c83514c411190f9544a549fbfff35dc6e;uniformFacts=[]
diagnostic=backend=GPU,reason=coverage.stroke-cap-join-visual-parity-below-threshold,action=RefuseDiagnostic(coverage.stroke-cap-join-visual-parity-below-threshold)
```

Preserved Crop fallback reason: `image-filter.crop-input-nonnull-prepass-required`.

## Artifacts

- `reports/wgsl-pipeline/scenes/generated/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json`
- `reports/wgsl-pipeline/scenes/artifacts/stroke-cap-join-aa-residual-for266/stroke-cap-join-aa-residual-for266.json`

## Policy Results

| Policy | Exact similarity | Max delta | Matching pixels | Boundary classification | Dominant region | Plausibility | Support |
|---|---:|---:|---:|---|---|---|---|
| `targetBlend-false-rgba16float` | 89.59554 | 39 | 22019/24576 | `color-space.target-blend-required-plus-coverage.stroke-cap-join-aa-residual` | `square-bevel` | `NOT_PROVEN` | `REFUSED_INSUFFICIENT_PARITY` |
| `targetBlend-true-rgba16float` | 95.914714 | 48 | 23572/24576 | `coverage.stroke-cap-join-aa-residual-after-targetColorSpaceBlend` | `round-round` | `PLAUSIBLE_BUT_NOT_PROVEN` | `REFUSED_INSUFFICIENT_PARITY` |

## Region Breakdown

| Policy | Region | Mismatches | One-unit | >8 | >32 | Max delta | Bounds |
|---|---|---:|---:|---:|---:|---:|---|
| `targetBlend-false-rgba16float` | `butt-bevel` | 734 | 387 | 345 | 1 | 38 | `left=9 top=27 right=47 bottom=92` |
| `targetBlend-false-rgba16float` | `round-round` | 879 | 407 | 453 | 7 | 39 | `left=48 top=27 right=95 bottom=92` |
| `targetBlend-false-rgba16float` | `square-bevel` | 944 | 0 | 940 | 0 | 22 | `left=96 top=27 right=191 bottom=92` |
| `targetBlend-true-rgba16float` | `butt-bevel` | 394 | 392 | 2 | 0 | 25 | `left=9 top=27 right=47 bottom=92` |
| `targetBlend-true-rgba16float` | `round-round` | 523 | 515 | 8 | 6 | 48 | `left=48 top=38 right=95 bottom=81` |
| `targetBlend-true-rgba16float` | `square-bevel` | 87 | 87 | 0 | 0 | 1 | `left=96 top=37 right=153 bottom=82` |

## High-Delta Samples

| Policy | Pixel | Region | Reference RGBA | WebGPU RGBA | Max delta |
|---|---|---|---|---|---:|
| `targetBlend-false-rgba16float` | `(92, 75)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(91, 76)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(90, 77)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(89, 78)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(88, 79)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(87, 80)` | `round-round` | `[133, 150, 214, 255]` | `[168, 189, 229, 255]` | 39 |
| `targetBlend-false-rgba16float` | `(21, 81)` | `butt-bevel` | `[206, 213, 239, 255]` | `[168, 189, 229, 255]` | 38 |
| `targetBlend-false-rgba16float` | `(69, 81)` | `round-round` | `[209, 222, 209, 255]` | `[171, 202, 182, 255]` | 38 |
| `targetBlend-false-rgba16float` | `(125, 59)` | `square-bevel` | `[111, 83, 33, 255]` | `[89, 76, 25, 255]` | 22 |
| `targetBlend-false-rgba16float` | `(124, 60)` | `square-bevel` | `[111, 83, 33, 255]` | `[89, 76, 25, 255]` | 22 |
| `targetBlend-true-rgba16float` | `(92, 75)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(91, 76)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(90, 77)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(89, 78)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(88, 79)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(87, 80)` | `round-round` | `[133, 150, 214, 255]` | `[181, 191, 230, 255]` | 48 |
| `targetBlend-true-rgba16float` | `(21, 81)` | `butt-bevel` | `[206, 213, 239, 255]` | `[181, 191, 230, 255]` | 25 |
| `targetBlend-true-rgba16float` | `(93, 74)` | `round-round` | `[182, 192, 231, 255]` | `[206, 213, 238, 255]` | 24 |
| `targetBlend-true-rgba16float` | `(17, 77)` | `butt-bevel` | `[133, 150, 214, 255]` | `[157, 170, 222, 255]` | 24 |
| `targetBlend-true-rgba16float` | `(69, 81)` | `round-round` | `[209, 222, 209, 255]` | `[185, 204, 185, 255]` | 24 |

## Conclusion

none_applied: bounded coverage correction is plausible only as a diagnostic hypothesis after targetColorSpaceBlend=true, but FOR-266 does not prove CPU/GPU coverage equivalence for the affected cap/join boundary cells; the scene remains refused

Missing condition: `missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells`.

Remaining boundary: `coverage.stroke-cap-join-aa-residual`.

## Validation

```text
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-266*'
rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py
rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py
rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
