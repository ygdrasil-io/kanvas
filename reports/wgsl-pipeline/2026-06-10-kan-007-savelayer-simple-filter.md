# KAN-007 SaveLayer Simple Filter Evidence

Date: 2026-06-10
Ticket: KAN-007 - SaveLayer filtre simple visible

## Decision

KAN-007 is closed as a bounded visible support slice for
SaveLayer + `SkImageFilters.ColorFilter(Matrix, input = null)`.

The selected route is intentionally simple: the layer pixels are rendered into
the existing layer texture, then the final WebGPU layer composite applies the
packed ColorFilter uniform through `layer_composite.wgsl`.

## Scene

| Field | Value |
|---|---|
| Scene id | `save-layer.image-filter.color-filter-matrix.v1` |
| Scope id | `kan-007.save-layer.simple-color-filter.v1` |
| Filter | `SkImageFilters.ColorFilter(Matrix, input = null)` |
| Layer | One bounded `SkCanvas.saveLayer` over a 64 x 40 px integer rect |
| CPU route | `cpu.save-layer.image-filter.color-filter-matrix` |
| WebGPU route | `webgpu.image-filter.color-filter.layer-composite` |
| Fallback | `none` |
| Materialise stages | `0` |
| Scratch texture | none |
| Threshold | `99` |

## Artifacts

- Reference: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/reference.png`
- CPU: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/cpu.png`
- WebGPU: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/webgpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/cpu-diff.png`
- WebGPU diff: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/webgpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/route-cpu.json`
- WebGPU route: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/route-webgpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter/stats.json`

## Route Diagnostics

The WebGPU route JSON records:

- `selectedRoute = webgpu.image-filter.color-filter.layer-composite`;
- `prepassRoute = null`;
- `scratchOwner = null`;
- `scratchLifetime = null`;
- `materialiseStages = 0`;
- `fallbackReason = none`.

This keeps KAN-007 separate from the KAN-005/KAN-006 M61 DAG route, which
materialises one intermediate texture for `Compose(ColorFilter, MatrixTransform)`.

## Limits

- Aucune layer stack arbitraire n'est revendiquee.
- Aucun DAG multi-node n'est revendique par cette scene.
- Aucun support general de Blur, Offset, Crop, MatrixTransform, Tile,
  Magnifier, DropShadow ou Blend image-filter n'est revendique ici.
- Aucun picture prepass n'est revendique.
- Aucun CPU readback fallback n'est ajoute.
- Aucun seuil global n'est abaisse.

## Validation

```text
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.SimpleSaveLayerImageFilterSceneEvidenceTest
rtk python3 scripts/validate_kan007_savelayer_simple_filter.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan007SaveLayerSimpleFilter
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
