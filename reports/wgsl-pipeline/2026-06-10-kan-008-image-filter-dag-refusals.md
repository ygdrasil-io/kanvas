# KAN-008 Image Filter DAG Refusals

Date: 2026-06-10
Ticket: KAN-008 - Refus DAG image filters non supportes

## Decision

KAN-008 is closed as a refusal/evidence guard. It does not add image-filter
runtime support. It keeps three unsupported graph shapes visible and validated
with stable reason codes, while preserving the boundary against the bounded
support already delivered by KAN-005/KAN-006/KAN-007.

No broad DAG support is claimed.

## Refusal Rows

| Row | Source | Status | Stable reason | PM message |
|---|---|---|---|---|
| `m52-big-tile-image-filter-dag-refusal` | `reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json` | `expected-unsupported` | `image-filter.dag-or-picture-prepass-required` | BigTile still needs picture/layer prepass ownership before a WebGPU route can render it. |
| `m54-imagefilters-graph-boundary` | `reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json` | `expected-unsupported` | `image-filter.dag-or-picture-prepass-required` | ImageFiltersGraph-style broad graphs remain refused until pass ordering and intermediate ownership are explicitly implemented. |
| `image-filter-crop-nonnull-prepass-required` | `reports/wgsl-pipeline/scenes/generated/results.json` and `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/` | `expected-unsupported` | `image-filter.crop-input-nonnull-prepass-required` | Non-selected `Crop(input = nonNull)` graph shapes remain visible instead of being silently treated as supported. |

## Boundary Against Supported Slices

The refusal rows are intentionally compared with the bounded supported rows:

- KAN-005/KAN-006: `m61-compose-cf-matrix-transform-dag-v2` remains `pass`,
  has `fallbackReason = none`, allocates exactly one intermediate texture, and
  exposes pass order `matrix-transform-prepass -> color-filter-final-composite`.
- KAN-007: `save-layer.image-filter.color-filter-matrix.v1` remains a simple
  SaveLayer + `SkImageFilters.ColorFilter(Matrix, input = null)` support slice
  through `webgpu.image-filter.color-filter.layer-composite`, with no DAG
  materialisation stage.

Those rows do not make BigTile, ImageFiltersGraph, picture/layer prepass,
recursive scheduling, or non-selected Crop(input non-null) graph support
available.

## Diagnostics Required

The KAN-008 validator requires:

- each refusal row to stay `expected-unsupported`;
- the stable fallback reason to match the row's unsupported family;
- graph refusals to keep `graphDiagnostics` with node count, budget,
  empty pass order, zero intermediate textures, ownership notes, and an
  explicit non-claim;
- the Crop refusal to keep CPU oracle, WebGPU refusal route, stats, and the
  `webgpu.image-filter.refuse` route artifact;
- the bounded M61 support row to remain a separate `pass` row with one
  intermediate texture and explicit pass order.

## Limits

- No arbitrary image-filter DAG scheduler is claimed.
- No picture/layer prepass materialisation is claimed.
- No BigTile GPU route is claimed.
- No ImageFiltersGraphGM broad parity is claimed.
- No recursive Crop(input non-null) prepass support is claimed.
- No CPU readback fallback is added.
- No global threshold is changed.

## Validation

```text
rtk python3 scripts/validate_kan008_image_filter_dag_refusals.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan008ImageFilterDagRefusals
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
