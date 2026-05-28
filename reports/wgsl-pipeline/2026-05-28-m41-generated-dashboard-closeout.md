# M41 Generated Conformance Dashboard Closeout

Date: 2026-05-28
Milestone: M41 -- Generated Conformance Dashboard
Epic: GRA-191
Closeout: GRA-201

## Outcome

M41 is complete. The scene dashboard now supports mixed static and generated evidence.

Delivered outcome:

- generated scene result schema is defined;
- `pipelineGeneratedSceneExport` materializes generated rows and artifacts;
- `pipelineSceneDashboard` merges generated rows with static rows and validates the combined support claims;
- three rows are generated from report/test evidence;
- static rows remain readable and explicitly tracked for future owners.

## Child Ticket Summary

| Ticket | Result | PR | Evidence |
|---|---|---|---|
| GRA-196 | Defined generated scene result schema and scene contract metadata. | #1215 | `reports/wgsl-pipeline/2026-05-28-m41-generated-scene-result-schema.md` |
| GRA-197 | Added generated artifact exporter and dashboard merge path. | #1216 | `reports/wgsl-pipeline/2026-05-28-m41-dashboard-artifact-exporter.md` |
| GRA-198 | Converted `bitmap-rect-nearest` to generated evidence. | #1217 | `reports/wgsl-pipeline/2026-05-28-m41-bitmap-rect-nearest-generated-evidence.md` |
| GRA-199 | Converted `crop-image-filter-nonnull-prepass` to generated evidence. | #1218 | `reports/wgsl-pipeline/2026-05-28-m41-crop-image-filter-generated-evidence.md` |
| GRA-200 | Converted `linear-gradient-rect` to generated evidence. | #1219 | `reports/wgsl-pipeline/2026-05-28-m41-linear-gradient-generated-evidence.md` |

## Dashboard Counts

Final dashboard output: `build/reports/wgsl-pipeline-scenes/index.html`

| Signal | Count |
|---|---:|
| Total scene rows | 11 |
| Generated rows | 3 |
| Static rows | 8 |
| `pass` | 7 |
| `tracked-gap` | 2 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

Generated rows:

| Scene | Status | Evidence level | Owner command |
|---|---|---|---|
| `bitmap-rect-nearest` | `pass` | Generated conformance | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` |
| `crop-image-filter-nonnull-prepass` | `pass` | Generated conformance | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest` |
| `linear-gradient-rect` | `pass` | Generated conformance | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'` |

Generated artifact input root:

```text
reports/wgsl-pipeline/scenes/generated/
```

Generated dashboard export root:

```text
build/reports/wgsl-pipeline-generated-scenes/
```

## Remaining Static Rows

| Scene | Status | Reason still static | Future owner |
|---|---|---|---|
| `solid-rect` | `tracked-gap` | Has route evidence but lacks adapter-backed GPU capture artifact. | M42 / GRA-202 |
| `analytic-aa-convex` | `tracked-gap` | Has route evidence but lacks adapter-backed GPU capture artifact. | M42 / GRA-203 |
| `src-over-stack` | `pass` | Static M39 blend route evidence; not required for the M41 minimum three generated rows. | Future generated-dashboard conversion after M42/M43 priority work |
| `runtime-effect-simple` | `pass` | Static M39 runtime-effect descriptor route evidence. | Future runtime-effect generated report task |
| `clip-rect-difference` | `pass` | Static M39 clip/coverage route evidence. | Future geometry/coverage generated report task |
| `bitmap-shader-local-matrix` | `pass` | Static M39 image shader local-matrix route evidence. | Future bitmap shader generated report task |
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | Stable Path AA refusal: `coverage.stroke-outline-edge-count-exceeded`. | M44 Path AA family promotion |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | Stable Path AA refusal: `coverage.edge-count-exceeded`. | M44 Path AA family promotion |

Static rows remain allowed by the M41 scope. They are not counted as generated conformance evidence.

## M42 Readiness

M42 is ready to start. The remaining P0 tracked gaps are explicit and isolated:

- `solid-rect` -> adapter-backed GPU capture needed;
- `analytic-aa-convex` -> adapter-backed GPU capture needed.

The generated exporter and dashboard merge path are already available for M42 to attach adapter-backed artifacts without changing the dashboard schema again.

## Validation

Commands run during M41 ticket execution:

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'
```

Closeout validation reruns the dashboard and generated-scene owner commands before merge.

## Residual Risks

- M41 generates three representative rows, not the full dashboard.
- `performanceTrend` fields for `linear-gradient-rect` remain estimated, not measured; M43 owns measured benchmark output.
- P0 GPU support claims still need adapter-backed captures in M42.
- Path AA breadth remains expected unsupported until M44 promotes one narrow family with rendered evidence.
