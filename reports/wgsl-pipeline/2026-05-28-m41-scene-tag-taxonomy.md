# M41 Scene Tag Taxonomy

Date: 2026-05-28
Issue: GRA-221

## Outcome

The WGSL scene dashboard now carries a stable tag taxonomy across the mixed
static/generated scene set. Tags are exported for review tooling and rendered in
the dashboard as compact metadata for filtering, search, and aggregates.

This does not change support semantics: `status`, `gpu.status`, rendered
artifacts, diffs, stats, route diagnostics, thresholds, and fallback reasons
remain the source of support evidence.

## Implemented Contract

- Added `.upstream/specs/wgsl-pipeline/13-scene-tag-taxonomy.md`.
- Added `tags[]` to all 10 static rows in
  `reports/wgsl-pipeline/scenes/data/scenes.json`.
- Added required generated-row namespaces to all 3 generated rows in
  `reports/wgsl-pipeline/scenes/generated/results.json`.
- Added dashboard export validation for:
  - missing or empty `tags`;
  - duplicate tags;
  - uppercase, whitespace, slash, or otherwise invalid tag format;
  - generated/mixed rows missing `source.*`, `feature.*`, `route.*`,
    `reference.*`, or `maturity.*`;
  - `maturity.adapter-backed` without `gpu.stats.adapter`;
  - `route.gpu.expected-unsupported` without a stable non-`none`
    `gpu.route.fallbackReason`;
  - `gpu.status=expected-unsupported` without `route.gpu.expected-unsupported`.
- Added exported `tagAggregates` for `feature.*`, `maturity.*`, and `risk.*`.
- Added dashboard exact-tag filtering, tag-inclusive search, compact per-card
  tags, and visible aggregate counts over the currently visible scene set.

## Exported Counts

The current merged dashboard export contains 13 scenes.

Maturity aggregate:

| Tag | Count |
|---|---:|
| `maturity.static-evidence` | 10 |
| `maturity.generated-evidence` | 3 |
| `maturity.adapter-backed` | 2 |

Risk aggregate:

| Tag | Count |
|---|---:|
| `risk.none` | 10 |
| `risk.expected-unsupported` | 2 |
| `risk.edge-budget` | 2 |
| `risk.tracked-gap` | 1 |
| `risk.oracle-mismatch` | 1 |

Feature highlights:

| Tag | Count |
|---|---:|
| `feature.coverage.analytic-rect` | 4 |
| `feature.path-aa` | 4 |
| `feature.image.bitmap` | 2 |
| `feature.image-filter` | 2 |
| `feature.stroke` | 2 |

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Both commands passed. The dashboard export wrote:

```text
build/reports/wgsl-pipeline-scenes/index.html
build/reports/wgsl-pipeline-scenes/data/scenes.json
```
