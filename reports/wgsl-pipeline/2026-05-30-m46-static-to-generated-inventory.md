# M46 Static-To-Generated Conversion Inventory

Date: 2026-05-30
Issue: GRA-224
Milestone: M46 -- Generated Evidence Expansion

## Purpose

Lock the exact M46 conversion inventory before implementation. M46 converts
already-supported dashboard rows from static evidence to generated evidence; it
must not add rendering feature scope, weaken thresholds, hide expected
unsupported rows, or change support semantics without equivalent generated
artifacts and route diagnostics.

## Baseline Counts

Source command:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Merged export: `build/reports/wgsl-pipeline-scenes/data/scenes.json`.

| Signal | Count |
|---|---:|
| Merged scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| `maturity.generated-evidence` | 3 |
| `maturity.static-evidence` | 10 |
| `maturity.adapter-backed` | 2 |

Existing generated rows:

| Scene | Status | Owner evidence |
|---|---|---|
| `bitmap-rect-nearest` | `pass` | M41 generated evidence, `pipelineGeneratedSceneExport` |
| `crop-image-filter-nonnull-prepass` | `pass` | M41 generated evidence, `pipelineGeneratedSceneExport` |
| `linear-gradient-rect` | `pass` | M41 generated evidence, `pipelineGeneratedSceneExport` |

## Static Row Inventory

| Scene | Status | Priority | Reference | CPU route family | GPU route family | Threshold | Performance payload | Tags |
|---|---|---|---|---|---|---:|---|---|
| `solid-rect` | `pass` | P0 | `cpu-oracle` | `cpu.descriptor.coverage-plan.solid-rect` | `webgpu.coverage.analytic-rect` | 99.95 | none | `feature.shape.solid`, `feature.coverage.analytic-rect`, `maturity.adapter-backed`, `risk.none` |
| `analytic-aa-convex` | `pass` | P0 | `cpu-oracle` | `cpu.path-coverage.analytic-aa-convex-composited-oracle` | `webgpu.coverage.path-convex-fan` | 99.85 | none | `feature.coverage.aa`, `feature.path-aa`, `maturity.adapter-backed`, `risk.none` |
| `src-over-stack` | `pass` | P1 | `test-oracle` | `cpu.blend.src-over-stack` | `webgpu.blend.src-over.fixed-function` | 99.95 | CPU+GPU measured | `feature.blend.src-over`, `feature.coverage.analytic-rect`, `risk.none` |
| `runtime-effect-simple` | `pass` | P1 | `test-oracle` | `cpu.runtime-effect.descriptor.simple_rt` | `webgpu.runtime-effect.descriptor.simple_rt` | 99.95 | CPU+GPU measured | `feature.runtime-effect`, `feature.coverage.analytic-rect`, `risk.none` |
| `clip-rect-difference` | `pass` | P1 | `skia-upstream` | `cpu.coverage.clip-rect-difference` | `webgpu.coverage.clip-difference.analytic-rrect-mask` | 80.0 | CPU+GPU measured | `feature.clip`, `feature.coverage.clip`, `risk.none` |
| `bitmap-shader-local-matrix` | `pass` | P1 | `test-oracle` | `cpu.shader.bitmap.local-matrix` | `webgpu.shader.bitmap.local-matrix` | 99.95 | CPU+GPU measured | `feature.image.bitmap`, `feature.shader.local-matrix`, `risk.none` |
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | P1 | `cpu-oracle` | `cpu.path-coverage.stroke-outline-oracle` | `webgpu.coverage.refuse` | 95.91 | none | `feature.path-aa`, `feature.stroke`, `risk.expected-unsupported`, `risk.edge-budget` |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | P1 | `cpu-oracle` | `cpu.path-coverage.raster-oracle` | `webgpu.coverage.refuse` | 99.85 | none | `feature.path-aa`, `feature.coverage.aa`, `risk.expected-unsupported`, `risk.edge-budget` |
| `path-aa-stroke-primitive` | `pass` | P1 | `test-oracle` | `cpu.raster.path-aa-stroke-primitive-oracle` | `webgpu.coverage.path-aa-stroke-primitive` | 91.76 | none | `feature.path-aa`, `feature.stroke`, `risk.none` |
| `image-filter-compose-cf-matrix-transform` | `pass` | P1 | `test-oracle` | `cpu.image-filter.compose.cf-matrix-transform-oracle` | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` | 99.0 | none | `feature.image-filter`, `feature.color-filter`, `feature.matrix-transform`, `risk.none` |

## Locked M46 Target Rows

These five static rows are the only rows M46 agents should convert unless the
implementation discovers a blocker and records it in Linear before proceeding.
The generated replacement must preserve scene id, status, priority, reference
kind, threshold, route family, fallback semantics, relevant tags, and evidence
links. Static rows must be removed only after the generated replacement validates
through `pipelineSceneDashboard`.

| Target row | Ticket | Generated owner | Regeneration command / owner |
|---|---|---|---|
| `solid-rect` | GRA-225 | `pipelineGeneratedSceneExport` generated manifest entry | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest` |
| `analytic-aa-convex` | GRA-226 | `pipelineGeneratedSceneExport` generated manifest entry | `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.AnalyticAaConvexSceneCaptureTest` |
| `path-aa-stroke-primitive` | GRA-227 | `pipelineGeneratedSceneExport` generated manifest entry | Existing M44 stroke rect/circle WebGPU/cross-backend tests plus generated dashboard artifact writer owned by GRA-227. |
| `image-filter-compose-cf-matrix-transform` | GRA-228 | `pipelineGeneratedSceneExport` generated manifest entry | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest` plus generated dashboard artifact writer owned by GRA-228. |
| `src-over-stack` | GRA-229 | `pipelineGeneratedSceneExport` generated manifest entry preserving M43 measured metrics | Existing `pipelineMeasuredCpuPerformance` / `pipelineMeasuredGpuPerformance` payloads plus generated row conversion owned by GRA-229. |

`src-over-stack` is selected as the measured-performance row because it already
has CPU and GPU performance payloads, has a simple blend route family, and is
not blocked by the inventory. `bitmap-shader-local-matrix` remains the fallback
measured-performance candidate if `src-over-stack` is blocked during GRA-229.

## Rows Intentionally Left Static In M46

| Row | Rationale / owner |
|---|---|
| `runtime-effect-simple` | Leave static in M46 to avoid coupling runtime-effect descriptor generated evidence with the M46 core route/evidence migration. Candidate owner: future runtime-effect generated-evidence milestone. |
| `clip-rect-difference` | Leave static because the threshold is intentionally broad (`80.0`) and the clip route should get a dedicated generated evidence ticket rather than being bundled into M46 conversion quota. |
| `bitmap-shader-local-matrix` | Leave static unless `src-over-stack` is blocked; it remains the fallback measured-performance conversion candidate for GRA-229. |
| `path-aa-stroke-outline-fallback` | Leave static because it is expected unsupported edge-budget evidence; M46 target is supported pass rows, not unsupported breadth conversion. |
| `path-aa-edge-budget-boundary` | Leave static because it is expected unsupported edge-budget evidence; broad Path AA refusal inventory must remain visible and stable. |

## Guardrails For Conversion Tickets

- Do not change runtime support claims without generated artifacts meeting the
  existing threshold and fallback policy.
- Do not delete static rows until the generated replacement has the same scene
  id and passes dashboard validation.
- Adapter-backed rows keep `maturity.adapter-backed` only if generated GPU stats
  include a concrete adapter name.
- Measured performance rows must preserve links to raw measured CPU/GPU payloads.
- Expected unsupported rows remain visible and are not part of the M46 generated
  quota.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Both commands passed for this inventory lock. No scene data was changed.
