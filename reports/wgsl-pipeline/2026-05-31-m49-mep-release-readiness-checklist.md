# M49 MEP Release Readiness Checklist

Date: 2026-05-31
Linear: GRA-293
Parent epic: GRA-287
Milestone: M49 -- MEP Readiness Gate Toward 60%

## Decision Rule

M49 may claim 60% Post-MVP Big Target readiness only if every required lane below
is `pass` at sprint closeout. If any required lane is `fail` or `blocked`, the
M49-G sprint review must reject or lower the 60% claim.

Current checklist result after M49-F: `pass` for M49-B/C/D/E/F evidence. M49-G
must still update README/target/backlog and close the sprint before the final
score is official.

## Linear Scope

| Item | Status | Link / evidence |
|---|---|---|
| Epic | `open until M49-G` | `GRA-287` |
| M49-A gate invariant spec | `Done` | `GRA-288`, `reports/wgsl-pipeline/2026-05-31-m49-dashboard-gate-invariants.md` |
| M49-B CI validation task | `Done` | `GRA-289`, `reports/wgsl-pipeline/2026-05-31-m49-ci-dashboard-validation-task.md` |
| M49-C portable PM bundle | `Done` | `GRA-290`, `reports/wgsl-pipeline/2026-05-31-m49-portable-pm-bundle.md` |
| M49-D adapter-backed expansion | `Done` | `GRA-291`, `reports/wgsl-pipeline/2026-05-31-m49-adapter-backed-expansion.md` |
| M49-E performance trend contract | `Done` | `GRA-292`, `reports/wgsl-pipeline/2026-05-31-m49-performance-trend-gate-contract.md` |
| M49-F release checklist | `Done after this PR` | `GRA-293`, this report |
| M49-G sprint review / score update | `required next` | `GRA-294` |

## Lane Checklist

| Lane | Required evidence | Current result | Decision |
|---|---|---|---|
| Evidence foundation | Dashboard keeps 0 `tracked-gap`, 0 `fail`, stable generated/static evidence semantics. | 23 rows, 18 pass, 5 expected-unsupported, 0 tracked-gap, 0 fail. | `pass` |
| CI/release gate | `pipelineSceneDashboardGate` exists, writes report, fails on support regressions, current dashboard passes. | Task and negative fixture added by GRA-289; gate report has 0 failures. | `pass` |
| PM bundle | `pipelinePmBundle` writes portable dashboard bundle with manifest, artifacts, reports, gate output, and serve command. | Bundle path `build/reports/wgsl-pipeline-pm-bundle/`, manifest has 0 unavailable references. | `pass` |
| Adapter-backed proof | At least 6 adapter-backed rows with adapter metadata, commands, stats, routes, and artifact links. | 7 adapter-backed rows after GRA-291. | `pass` |
| Performance trend readiness | Non-blocking measured trend contract exists; estimated metrics remain non-gates. | Contract added by GRA-292 for `src-over-stack` and `bitmap-shader-local-matrix`. | `pass` |
| Release checklist | Single PM/maintainer checklist exists and refuses 60% if a lane is missing. | This report. | `pass` |
| Final score sync | README, target doc, backlog, and sprint review agree. | Pending GRA-294. | `blocked until M49-G` |

## Required Commands

| Purpose | Command | Required for 60% claim |
|---|---|---|
| Dashboard generation | `rtk ./gradlew --no-daemon pipelineSceneDashboard` | Yes |
| CI gate candidate | `rtk ./gradlew --no-daemon pipelineSceneDashboardGate` | Yes |
| Portable PM bundle | `rtk ./gradlew --no-daemon pipelinePmBundle` | Yes |
| Local PM serve | `python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard` | Yes for demo repeatability |

## Current Counters

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| `maturity.generated-evidence` | 21 |
| `maturity.static-evidence` | 2 |
| `maturity.adapter-backed` | 7 |
| PM bundle unavailable references | 0 |

## Allowed Expected-Unsupported Rows

| Scene id | Stable fallback reason | Claim boundary |
|---|---|---|
| `path-aa-stroke-outline-fallback` | `coverage.stroke-outline-edge-count-exceeded` | Static Path AA stroke-outline policy sentinel; not support. |
| `path-aa-edge-budget-boundary` | `coverage.edge-count-exceeded` | Static broad Path AA edge-budget policy sentinel; not support. |
| `path-aa-convexpaths-edge-budget` | `coverage.edge-count-exceeded` | ConvexPaths breadth refusal; not broad Path AA support. |
| `path-aa-dashing-edge-budget` | `coverage.edge-count-exceeded` | Dash/stroke breadth refusal; not dash/cap/join support. |
| `image-filter-crop-nonnull-prepass-required` | `image-filter.crop-input-nonnull-prepass-required` | Out-of-scope Crop(input=nonNull) breadth refusal; not arbitrary image-filter DAG support. |

## Adapter-Backed Rows

| Scene id | Adapter | Evidence command |
|---|---|---|
| `solid-rect` | `Apple M2 Max` | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest` |
| `analytic-aa-convex` | `Apple M2 Max` | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest` |
| `bitmap-rect-nearest` | `Apple M2 Max` | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` |
| `linear-gradient-rect` | `Apple M2 Max` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'` |
| `src-over-stack` | `Apple M2 Max` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest` |
| `bitmap-shader-local-matrix` | `Apple M2 Max` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest` |
| `clip-rect-difference` | `Apple M2 Max` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest` |

## Performance Trend Status

| Row | CPU trend | GPU trend | Gate status |
|---|---|---|---|
| `src-over-stack` | measured local baseline `m43-cpu-measured-local` | measured local baseline `m43-gpu-cache-measured-local` on `Apple M2 Max` | `reporting-only` |
| `bitmap-shader-local-matrix` | measured local baseline `m43-cpu-measured-local` | measured local baseline `m43-gpu-cache-measured-local` on `Apple M2 Max` | `reporting-only` |

Performance readiness may move to 35% because the trend contract is explicit,
but no performance regression is release-blocking until a future ticket adds
CI-owned baselines, owner, rollback, quarantine, and stable-run evidence.

## Non-Claims

- No Ganesh or Graphite port.
- No SkSL compiler, SkSL IR, or VM rebuild.
- No broad Path AA support beyond promoted subsets.
- No arbitrary image-filter DAG support.
- No text/font/emoji/codec readiness claim.
- No perspective/3D transform readiness claim.
- No required performance threshold or release-blocking benchmark gate.
- No claim that expected-unsupported rows are support evidence.

## Dependency-Gated Gaps

| Gap | Gate status |
|---|---|
| Text/font/glyph masks/emoji | Dependency-gated; no substitute accepted for score movement. |
| Codec breadth | Dependency-gated; no short-lived substitute accepted for score movement. |
| Broad Path AA atlas/mask strategy | Future implementation and benchmark-backed policy required. |
| Arbitrary image-filter DAGs | Future bounded DAG milestones required. |
| Required performance gates | Future CI-owned baseline and rollback/quarantine policy required. |

## Final 60% Score Rule

M49-G may claim 60% only if:

1. This checklist remains `pass` except the M49-G row, which must be completed.
2. `pipelineSceneDashboard`, `pipelineSceneDashboardGate`, and `pipelinePmBundle` pass at closeout.
3. Adapter-backed count remains at least 6.
4. PM bundle has 0 unavailable dashboard references or explicitly justified unavailable non-dashboard references.
5. README, target doc, backlog, and M49 sprint review agree on the same score.

If any condition fails, M49-G must lower the score and state the exact blocked
lane.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```
