# M45-E Image-filter DAG subset closeout

M45 closes the first bounded post-M38 image-filter DAG subset. The supported
subset is intentionally narrow:

```text
Compose(
  outer = ColorFilter(Matrix|Blend, input = null),
  inner = MatrixTransform(affine 2x3, input = null)
)
```

This is not a full Skia image-filter DAG compiler.

## Outcome

| Signal | Result |
| --- | --- |
| Selected subset | `Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine))` |
| Dashboard scene | `image-filter-compose-cf-matrix-transform` |
| Dashboard status | `pass` |
| GPU pre-pass route | `webgpu.image-filter.compose.cf-matrix-transform.materialize-matrix` |
| GPU final route | `webgpu.image-filter.compose.cf-matrix-transform.final-color-filter-composite` |
| Scratch owner | `LayerCompositeDraw.materializeTargetTexture` |
| Scratch lifetime | `per-composite-dispatch` |
| Fallback reason | `none` |

M45 adds an auditable route claim for the selected two-node DAG and keeps broad
DAG shapes explicitly scoped out.

## Ticket evidence

| Ticket | Evidence | PR |
| --- | --- | --- |
| GRA-216 | Selected the bounded DAG subset, source test, route names, and fallback policy. | #1235 |
| GRA-217 | Defined intermediate texture/layer ownership and lifetime. | #1236 |
| GRA-218 | Exposed deterministic route diagnostics and added a focused route test. | #1237 |
| GRA-219 | Added dashboard row, artifacts, route JSON, stats, and PM report. | #1238 |
| GRA-220 | Closeout summary and residual backlog. | This report |

## Visual and route artifacts

Dashboard row:

```text
reports/wgsl-pipeline/scenes/data/scenes.json#image-filter-compose-cf-matrix-transform
```

Artifacts:

- Reference: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/skia.png`
- CPU: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu.png`
- GPU: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/cpu-diff.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/gpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-cpu.json`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-gpu.json`
- Pre-pass route: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/route-prepass.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/image-filter-compose-cf-matrix-transform/stats.json`

## Validation evidence

Local validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SaveLayerImageFilterTest
```

GRA-218 GitHub validation before merge:

| Check | Result |
| --- | --- |
| GPU tests (macos) | pass |
| Raster tests (ubuntu) | pass |
| GPU inventory (macos, non-blocking) | pending at merge |

## Remaining image-filter DAG order

| Order | Family | Current status | Recommendation |
| ---: | --- | --- | --- |
| 1 | Perspective `MatrixTransform` | Expected unsupported / stable refusal | Add homogeneous divide and sampling proof before promotion. |
| 2 | `MatrixTransform(input != null)` outside selected Compose shape | Expected unsupported / stable refusal | Promote only with explicit child materialise contract and route JSON. |
| 3 | `ColorFilter(input != null)` outside selected Compose shape | Expected unsupported / stable refusal | Keep single-occupancy color-filter rules visible. |
| 4 | Duplicate `ColorFilter` or duplicate `Blur` chains | Expected unsupported / stable refusal | Needs filter folding math and threshold evidence. |
| 5 | Crop/Tile/Magnifier nested DAGs beyond M38 | Expected unsupported / stable refusal | Split each UV-remap family separately. |
| 6 | Image/Blend/morphology/lighting/displacement DAGs | Out of scope | Do not implement without separate feature tickets and reference evidence. |
| 7 | General Skia image-filter DAG compiler | Non-goal | Do not port Skia internals or SkSL. |

## Residual risks

- The dashboard artifact is representative and tied to the focused route test;
  broader DAGs remain non-claims.
- The current selected route supports affine MatrixTransform only; perspective
  remains explicitly unsupported.
- Route diagnostics are test-facing evidence; future generated evidence can
  replace the static dashboard row when a generator owns this scene.

## Acceptance summary

M45 is complete because it provides a selected subset, explicit intermediate
ownership, deterministic route diagnostics, dashboard artifacts, validation
commands, and a scoped remaining backlog without claiming general DAG support.
