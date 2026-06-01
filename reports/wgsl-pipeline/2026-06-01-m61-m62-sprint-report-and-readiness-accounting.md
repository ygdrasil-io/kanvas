# M61/M62 Sprint Report And Readiness Accounting

Linear: `FOR-19`

Milestones:

- M61 Image Filter DAG V2 and evidence truth hardening
- M62 Text & Glyph Rendering V1

## Executive Summary

M61 and M62 are ready to close as evidence milestones.

M61 added inspectable image-filter DAG diagnostics and one bounded GPU `pass` row for `Compose(ColorFilter, MatrixTransform)` DAG V2. It does not claim arbitrary recursive image-filter DAGs or `ImageFiltersGraphGM` parity.

M62 clarified current text support: Kanvas renders selected simple text through outline/path glyph routes. It does not yet claim glyph atlas, fallback-family selection, emoji/color glyph, or complex shaping support. The dashboard now makes that answer visible row-by-row.

## Ticket Outcomes

| Ticket | Outcome | PR | Evidence |
|---|---|---|---|
| `FOR-13` M61-1 split artifact status from CPU/GPU reference parity | Done. Dashboard lanes now distinguish produced artifacts from reference parity. | [#1290](https://github.com/ygdrasil-io/kanvas/pull/1290) | `pipelineSceneDashboardGate`, lane `artifactStatus` / `referenceParity` |
| `FOR-14` M61-2 graph diagnostics and ownership artifact | Done. Generated image-filter DAG rows now require materialized graph diagnostics. | [#1291](https://github.com/ygdrasil-io/kanvas/pull/1291) | `reports/wgsl-pipeline/2026-06-01-m61-image-filter-dag-diagnostics.md` |
| `FOR-15` M61-3 bounded DAG V2 promotion | Done. Added `m61-compose-cf-matrix-transform-dag-v2` as a bounded GPU `pass` row. | [#1292](https://github.com/ygdrasil-io/kanvas/pull/1292) | `reports/wgsl-pipeline/2026-06-01-m61-bounded-image-filter-dag-v2-promotion.md` |
| `FOR-16` M62-1 font/text baseline audit | Done. Existing text rows are documented as outline/path rendering, not atlas. | [#1293](https://github.com/ygdrasil-io/kanvas/pull/1293) | `reports/wgsl-pipeline/2026-06-01-m62-font-text-baseline-audit.md` |
| `FOR-17` M62-2 missing-glyph/fallback row | Done. Added `m62-missing-glyph-fallback-refusal` with stable fallback reason. | [#1294](https://github.com/ygdrasil-io/kanvas/pull/1294) | `reports/wgsl-pipeline/2026-06-01-m62-missing-glyph-fallback-evidence.md` |
| `FOR-18` M62-3 glyph route diagnostics and atlas non-claim | Done. Dashboard exposes glyph route, font diagnostics, atlas policy, and fallback policy. | [#1295](https://github.com/ygdrasil-io/kanvas/pull/1295) | `reports/wgsl-pipeline/2026-06-01-m62-glyph-route-dashboard-diagnostics.md` |

## Dashboard State

Validation source: `rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle`

The post-M62 dashboard has:

- 65 total scenes;
- 48 `pass` rows;
- 17 `expected-unsupported` rows;
- 0 `tracked-gap` rows;
- 0 `fail` rows;
- M61 row counter: 1;
- M62 row counter: 1;
- no scene gate failures.

## Readiness Accounting

Published active-target readiness moves from 25% to 31%.

| Area | Weight | Before | After M61/M62 | Decision |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 2/10 | 4/10 | Count M61 bounded image-filter DAG V2 and M62 text/font route diagnostics as selected generated contracts. |
| Skia-like fidelity | 20% | 25/100 | 27/100 | Count only the bounded M61 pass row and the M62 missing-glyph/fallback evidence row; no broad GM parity claim. |
| Real-time runtime | 20% | 1/10 | 1/10 | No Kadre/frame-loop runtime capability landed. |
| Performance and cache readiness | 15% | 7/20 | 7/20 | No new measured performance/cache gate landed. |
| PM/demo operability | 15% | 7/20 | 9/20 | Count graph diagnostics and glyph-route diagnostics as PM-operability artifacts. |

Weighted score:

```text
(0.30 * 0.40) + (0.20 * 0.27) + (0.20 * 0.10) + (0.15 * 0.35) + (0.15 * 0.45) = 0.314
```

Rounded PM readiness: 31%.

## PM Demo Notes

Suggested dashboard filters:

- `feature.image-filter-dag` to show `m61-compose-cf-matrix-transform-dag-v2`;
- `feature.missing-glyph` to show `m62-missing-glyph-fallback-refusal`;
- `feature.font` or `feature.text` to explain the outline/path glyph route and atlas non-claim;
- `expected-unsupported` to show stable refusals that are intentional boundaries.

The PM answer for glyph atlas is: not yet. Current text pass rows are outline/path rendering, and the dashboard says so explicitly.

## Non-Claims

- No arbitrary image-filter DAG scheduler.
- No `ImageFiltersGraphGM` parity.
- No glyph atlas, glyph mask, SDF, or LCD text support.
- No fallback-family selection support.
- No emoji/color glyph or complex shaping support.
- No new real-time Kadre runtime capability.
- No new measured performance/cache release gate.

## Validation

```text
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```
