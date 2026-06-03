# FOR-267 Round Cap/Join Coverage Equivalence Audit

Date: 2026-06-03

Decision: `KEEP_DIAGNOSTIC`

FOR-267 compares the `:kanvas-skia` CPU oracle and the test-only
WebGPU stroke cap/join experimental route on representative boundary
cells from `m60-bounded-stroke-cap-join`. It keeps
`targetColorSpaceBlend=false` as the production default, uses normal
`RGBA16Float` intermediate storage, leaves thresholds unchanged, and
does not change Crop, quantization, fallback policy, or production
stroke/cap/join support.

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

- `reports/wgsl-pipeline/scenes/generated/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json`
- `reports/wgsl-pipeline/scenes/artifacts/round-cap-join-coverage-equivalence-for267/round-cap-join-coverage-equivalence-for267.json`

## Cell Results

| Cell | Classification | Bounds | Matching | Max delta | Avg CPU coverage proxy | Avg GPU coverage proxy | Decision |
|---|---|---|---:|---:|---:|---:|---|
| `round-left-cap-boundary` | `round-cap-boundary` | `left=62 top=74 right=70 bottom=82` | 67/81 | 24 | 0.675948 | 0.677398 | `NOT_EQUIVALENT` |
| `round-join-apex` | `round-join-boundary` | `left=99 top=39 right=105 bottom=45` | 49/49 | 0 | 1 | 1 | `EQUIVALENT_BYTE_EXACT` |
| `round-right-cap-boundary` | `round-cap-boundary` | `left=134 top=74 right=142 bottom=82` | 76/81 | 1 | 0.868581 | 0.868954 | `EQUIVALENT_WITH_BYTE_ROUNDING` |
| `for266-high-delta-round-bin-overlap` | `round-region-overlap-with-butt-cap-boundary` | `left=87 top=74 right=93 bottom=80` | 21/49 | 48 | 0.510071 | 0.480043 | `NOT_EQUIVALENT` |

## Conclusion

Correction de couverture bornée: refusée pour ce ticket.

none_applied: FOR-267 observes byte-derived coverage proxies for bounded round cap/join cells, but at least one true round-cap boundary cell is not byte-equivalent and the FOR-266 high-delta cell is geometrically ambiguous; raw CPU/GPU coverage-plane equivalence is not proven

Missing condition: `missing_cpu_gpu_coverage_equivalence_for_round_cap_join_boundary_cells`.

Remaining boundary: `coverage.stroke-cap-join-aa-residual`.

## Validation

```text
rtk python3 scripts/validate_for267_round_cap_join_coverage_equivalence.py
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-267*'
rtk python3 scripts/validate_for266_stroke_cap_join_aa_residual.py
rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py
rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```
