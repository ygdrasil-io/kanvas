# M46 Generated Evidence Expansion Closeout

Date: 2026-05-30
Issue: GRA-230
Parent epic: GRA-223

## Executive Summary

M46 is complete. The dashboard kept the same 13 public scene rows and moved five
high-value static rows to generated evidence without changing support claims,
thresholds, fallback policy, or route semantics.

Final merged dashboard status is clean: 11 pass rows, 0 tracked gaps, 2 expected
unsupported Path AA edge-budget rows, and 0 fail rows. Generated evidence now
covers 8 rows and static evidence is down to 5 rows, meeting the M46 target.

## Final Dashboard Counters

| Signal | Count | M46 target |
|---|---:|---:|
| Scene rows | 13 | 13 |
| `pass` | 11 | preserve |
| `tracked-gap` | 0 | 0 |
| `expected-unsupported` | 2 | explicit |
| `fail` | 0 | 0 |
| `maturity.generated-evidence` | 8 | >= 8 |
| `maturity.static-evidence` | 5 | <= 5 |
| `maturity.adapter-backed` | 2 | preserve concrete adapter rows |

## M46 Delivery Summary

| Issue | PR | Merge commit | Delivered row/report |
|---|---|---|---|
| GRA-224 | #1245 | `8371f87efbfbcccd36af7826cf08d0a1abd8f799` | Static-to-generated inventory lock: `reports/wgsl-pipeline/2026-05-30-m46-static-to-generated-inventory.md` |
| GRA-225 | #1246 | `a2e3fa364b6aedc64785445667da882f58d58a51` | `solid-rect` generated evidence |
| GRA-226 | #1247 | `b45e275186b3d825a72a86eedae492d629d412f7` | `analytic-aa-convex` generated evidence |
| GRA-227 | #1248 | `8870f486e42815ded9ec3308d9e72827ab6ee26b` | `path-aa-stroke-primitive` generated evidence |
| GRA-228 | #1249 | `a89cc097cd208dffe48cada5ddeee1cb5a0f3e8d` | `image-filter-compose-cf-matrix-transform` generated evidence |
| GRA-229 | #1250 | `15ce1abe736b0403387ca3cd832b848074835867` | `src-over-stack` measured-performance generated evidence |

## Generated Rows

| Row | Status | Owning command | Artifact root |
|---|---|---|---|
| `src-over-stack` | `pass` | `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance`; `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance`; `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BlendModeTest --tests org.skia.gpu.webgpu.TranslucentSrcOverTest` | `artifacts/src-over-stack` |
| `solid-rect` | `pass` | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest` | `artifacts/solid-rect` |
| `analytic-aa-convex` | `pass` | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest` | `artifacts/analytic-aa-convex` |
| `path-aa-stroke-primitive` | `pass` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'` | `artifacts/path-aa-stroke-primitive` |
| `image-filter-compose-cf-matrix-transform` | `pass` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest` | `artifacts/image-filter-compose-cf-matrix-transform` |
| `bitmap-rect-nearest` | `pass` | `rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --tests org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest` | `artifacts/bitmap-rect-nearest` |
| `crop-image-filter-nonnull-prepass` | `pass` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests '*SimpleOffsetImageFilterWebGpuTest'` | `artifacts/crop-image-filter-nonnull-prepass` |
| `linear-gradient-rect` | `pass` | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.LinearGradientRectTest --tests '*GeneratedLinearGradientWgslTest'` | `artifacts/linear-gradient-rect` |

## Remaining Static Rows

| Row | Status | Rationale | Recommended next conversion order |
|---|---|---|---:|
| `runtime-effect-simple` | `pass` | Runtime-effect compatibility facade remains static until registered Kotlin/WGSL implementations and generated descriptor evidence are promoted together. | 1 |
| `clip-rect-difference` | `pass` | Clip coverage row should move with dedicated generated clip-lowering diagnostics, not as a dashboard-only relabel. | 2 |
| `bitmap-shader-local-matrix` | `pass` | Kept static because GRA-229 selected the preferred `src-over-stack` measured row; this remains the measured-performance fallback candidate. | 3 |
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | Expected unsupported broad Path AA fallback row; should stay explicit until edge-budget support actually lands. | 4 |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | Expected unsupported edge-budget boundary row; should not be hidden or converted as support evidence. | 5 |

## Tag Aggregates

| Tag | Count |
|---|---:|
| `feature.blend.src-over` | 1 |
| `feature.clip` | 1 |
| `feature.color-filter` | 1 |
| `feature.coverage.aa` | 2 |
| `feature.coverage.analytic-rect` | 4 |
| `feature.coverage.clip` | 1 |
| `feature.crop` | 1 |
| `feature.gradient.linear` | 1 |
| `feature.image-filter` | 2 |
| `feature.image.bitmap` | 2 |
| `feature.matrix-transform` | 1 |
| `feature.path-aa` | 4 |
| `feature.runtime-effect` | 1 |
| `feature.sampling.nearest` | 1 |
| `feature.shader.local-matrix` | 1 |
| `feature.shape.solid` | 1 |
| `feature.stroke` | 2 |

| Tag | Count |
|---|---:|
| `maturity.adapter-backed` | 2 |
| `maturity.generated-evidence` | 8 |
| `maturity.static-evidence` | 5 |

| Tag | Count |
|---|---:|
| `risk.edge-budget` | 2 |
| `risk.expected-unsupported` | 2 |
| `risk.none` | 11 |

## Support Boundaries

M46 does not introduce broad runtime feature claims:

- `analytic-aa-convex` remains a narrow convex fan row, not broad Path AA.
- `path-aa-stroke-primitive` remains the selected `StrokeRectGM` / `StrokeCircleGM` primitive-stroke family, with broad Path AA rows still expected unsupported.
- `image-filter-compose-cf-matrix-transform` remains the bounded M45 DAG subset, not general Skia image-filter DAG support.
- `src-over-stack` remains reporting-only for measured CPU/GPU performance; no CI performance gate was introduced.

## Validation

Commands run during M46 closeout:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Owning conversion commands run during M46:

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*StrokeRectWebGpuTest' --tests '*StrokeCircleWebGpuTest' --tests '*StrokeRectCrossBackendTest' --tests '*StrokeCircleCrossBackendTest'
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
```

All listed commands passed in the M46 ticket sequence. GitHub did not report
configured PR checks for #1245-#1250, so each PR was merged after local
validation and a clean GitHub merge state.
