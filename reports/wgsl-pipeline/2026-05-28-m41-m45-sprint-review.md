# M41-M45 Sprint Review

Date: 2026-05-28
Scope: Generated conformance dashboard, adapter-backed P0 captures, benchmark
harness, first Path AA promotion, image-filter DAG subset, and scene tag
taxonomy.

## Executive Summary

The post-MVP backlog M41-M45 is complete. The dashboard moved from static
evidence with P0 tracked gaps to a merged static/generated PM evidence view with
no tracked gaps, no failing support claims, and explicit expected unsupported
rows for remaining Path AA breadth.

Linear milestones M41, M42, M43, M44, and M45 are 100%. GRA-221 is also done
and adds the tag taxonomy needed for filtering, search, and aggregate reporting.

## PM Demo Entry Point

Run:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-scenes
```

Open:

```text
http://127.0.0.1:8765/index.html
```

The page shows reference, CPU, GPU, CPU diff, GPU diff, route diagnostics,
performance summary, tags, and artifact links for each scene.

## Final Dashboard Signals

Merged export: `build/reports/wgsl-pipeline-scenes/index.html`

| Signal | Count |
|---|---:|
| Scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| Generated evidence rows | 3 |
| Static evidence rows | 10 |
| Adapter-backed rows | 2 |

Tag aggregates:

| Tag family | Highlights |
|---|---|
| `feature.*` | `feature.coverage.analytic-rect=4`, `feature.path-aa=4`, `feature.image.bitmap=2`, `feature.image-filter=2`, `feature.stroke=2` |
| `maturity.*` | `maturity.static-evidence=10`, `maturity.generated-evidence=3`, `maturity.adapter-backed=2` |
| `risk.*` | `risk.none=11`, `risk.expected-unsupported=2`, `risk.edge-budget=2` |

## Milestone Outcomes

| Milestone | Outcome | Evidence |
|---|---|---|
| M41 | Generated three dashboard rows from report/test evidence and kept static rows labelled. | `reports/wgsl-pipeline/2026-05-28-m41-generated-dashboard-closeout.md` |
| M42 | Closed P0 tracked gaps for `solid-rect` and `analytic-aa-convex` with adapter-backed GPU captures and oracle reconciliation. | `reports/wgsl-pipeline/2026-05-28-m42-adapter-backed-p0-capture-closeout.md` |
| M43 | Added measured CPU and GPU/cache benchmark payloads for two stable scenes, reporting-only. | `reports/wgsl-pipeline/2026-05-28-m43-real-benchmark-harness-closeout.md` |
| M44 | Promoted primitive Path AA strokes as the first rendered Path AA family and reduced edge-budget inventory from 50 to 46. | `reports/wgsl-pipeline/2026-05-28-m44-path-aa-family-promotion-closeout.md` |
| M45 | Promoted bounded `Compose(ColorFilter, MatrixTransform)` image-filter DAG subset with explicit intermediate texture ownership. | `reports/wgsl-pipeline/2026-05-28-m45-image-filter-dag-subset-closeout.md` |
| GRA-221 | Added scene tag taxonomy, validation, exact-tag filtering, search, and feature/maturity/risk aggregates. | `reports/wgsl-pipeline/2026-05-28-m41-scene-tag-taxonomy.md` |

## What Improved

- P0 dashboard gaps are closed.
- Generated evidence exists for representative bitmap, image-filter, and
  gradient rows.
- Performance payloads are now measured for selected CPU/GPU rows instead of
  only estimated.
- Path AA has one real promoted family without broadening the global edge
  budget.
- Image-filter support now includes one bounded multi-node DAG subset without
  claiming a general Skia DAG compiler.
- Tags make the scene corpus filterable by feature, route, reference, maturity,
  and risk.

## Remaining Boundaries

- Most rows are still static evidence rather than generated evidence.
- Performance metrics are reporting-only; CI gates need a separate policy and
  baseline owner.
- Broad Path AA suites remain expected unsupported through stable edge-budget
  diagnostics.
- General image-filter DAG support remains out of scope.
- The PM dashboard is static; native live editing/export remains future work.

## Validation

Verified on `origin/master` at `8367f0df`:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Both commands passed. The generated dashboard contains 13 scenes: 11 pass,
0 tracked-gap, 2 expected-unsupported, and 0 fail.
