# M66 GM promotion wave report

Date: 2026-06-01
Linear: FOR-39, FOR-40, FOR-41 under FOR-32
Contract: `reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json`

## Result

The M66 pack materializes 19 cumulative generated dashboard rows:

| Counter | Value |
|---|---:|
| Selected rows | 19 |
| Support rows | 16 |
| Expected-unsupported rows | 3 |
| `skia-upstream` rows | 6 |
| `test-oracle` rows | 6 |
| `cpu-oracle` rows | 7 |

## Family counters

| Family | Rows | Support | Expected unsupported |
|---|---:|---:|---:|
| Path AA / coverage | 5 | 4 | 1 |
| Bitmap/image sampling | 2 | 2 | 0 |
| Transforms/layers | 1 | 1 | 0 |
| Image filters | 3 | 2 | 1 |
| Paint/blend/color | 3 | 3 | 0 |
| Runtime effects | 1 | 1 | 0 |
| Text/glyphs | 4 | 3 | 1 |

## Promotion policy

Support rows require generated artifacts, CPU/GPU routes, stats, diffs, and
`gpu.route.fallbackReason=none`. Expected-unsupported rows stay visible with
stable fallback reasons:

| Row | Fallback |
|---|---|
| `m66-path-aa-dashing-edge-budget-refusal` | `coverage.edge-count-exceeded` |
| `m66-image-filter-crop-prepass-refusal` | `image-filter.crop-input-nonnull-prepass-required` |
| `m66-font-complex-shaping-refusal` | `font.complex-shaping-requires-explicit-shaper` |

## FOR-41 gate evidence

`pipelineM66GmPromotionWave` materializes `data/m66-generated-scenes.json`
and is wired into `pipelineGeneratedSceneExport`, `pipelineSceneDashboardGate`,
and `pipelinePmBundle`. The dashboard gate counts M66 rows separately and
validates M66 `referenceKind` presence so inventory rows cannot masquerade as
support evidence.

## Non-claims

M66 does not update README readiness or `.upstream/target/*`. CPU-oracle text
and simple AA rows move breadth/operability evidence only; they are not counted
as Skia-like fidelity unless a comparable Skia reference exists.
