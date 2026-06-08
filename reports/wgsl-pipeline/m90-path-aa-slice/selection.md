# M90 Path AA Backlog Slice

Status: generated evidence

This slice turns the M89 registry closeout into the first M90 Path AA backlog contract. It does not promote support, change thresholds, or change the 256-edge WebGPU AA budget.

## Counters

- Path AA rows: `18`
- Existing pass baseline rows: `5`
- Edge-budget refusal rows: `2`
- Row-specific refusal rows: `2`
- Grouped policy refusal rows: `9`
- New support claims: `0`
- Readiness delta: `0.0`

## Clusters

### existingBoundedSupportBaseline

- `analytic-aa-convex`: `pass`, fallback `none`, supportClaim `True`
- `clip-rect-difference`: `pass`, fallback `none`, supportClaim `True`
- `draw-paint-clipped-rect`: `pass`, fallback `none`, supportClaim `True`
- `draw-paint-full-clip`: `pass`, fallback `none`, supportClaim `True`
- `path-aa-stroke-primitive`: `pass`, fallback `none`, supportClaim `True`

### edgeBudgetRefusals

- `path-aa-convexpaths-edge-budget`: `expected-unsupported`, fallback `coverage.edge-count-exceeded`, supportClaim `False`
- `path-aa-dashing-edge-budget`: `expected-unsupported`, fallback `coverage.edge-count-exceeded`, supportClaim `False`

### rowSpecificRefusals

- `skia-gm-pathfill`: `expected-unsupported`, fallback `path.pathfill.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-rectpolystroke`: `expected-unsupported`, fallback `coverage.rectpolystroke.row-specific-artifacts-required`, supportClaim `False`

### groupedDashHairlineStrokePolicyRefusals

- `skia-gm-dashcubics`: `expected-unsupported`, fallback `coverage.dash-cubic.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-dashing`: `expected-unsupported`, fallback `coverage.dashing.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-hairlines`: `expected-unsupported`, fallback `coverage.hairline.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-hairmodes`: `expected-unsupported`, fallback `coverage.hairmode.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-scaledstrokes`: `expected-unsupported`, fallback `coverage.scaled-stroke.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-strokedlines`: `expected-unsupported`, fallback `coverage.stroked-lines.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-strokerect`: `expected-unsupported`, fallback `coverage.stroke-rect.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-strokerects`: `expected-unsupported`, fallback `coverage.stroke-rects.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-thinstrokedrects`: `expected-unsupported`, fallback `coverage.thin-stroked-rects.row-specific-artifacts-required`, supportClaim `False`

## Next Tickets

### M90-PAA-1

- Type: `policy-visibility`
- Scope: Add route diagnostics for dash, hairline, scaled-stroke, and stroke-rect GM policy rows without changing support claims.
- Rows: `skia-gm-dashcubics`, `skia-gm-dashing`, `skia-gm-hairlines`, `skia-gm-hairmodes`, `skia-gm-scaledstrokes`, `skia-gm-strokedlines`, `skia-gm-strokerect`, `skia-gm-strokerects`, `skia-gm-thinstrokedrects`
- Support claim allowed: `False`

### M90-PAA-2

- Type: `implementation-or-refusal-proof`
- Scope: Refresh edge-budget refusal proof for ConvexPathsGM and DashingGM; keep 256-edge budget unless benchmark/ADR evidence justifies change.
- Rows: `path-aa-convexpaths-edge-budget`, `path-aa-dashing-edge-budget`
- Support claim allowed: `False`

### M90-PAA-3

- Type: `bounded-promotion-candidate`
- Scope: Only after row-specific Skia reference, CPU/GPU route, diff/stat, and performance impact evidence exists, evaluate one bounded dash or hairline slice for support.
- Rows: `skia-gm-dashcubics`, `skia-gm-dashing`, `skia-gm-hairlines`, `skia-gm-hairmodes`, `skia-gm-scaledstrokes`, `skia-gm-strokedlines`, `skia-gm-strokerect`, `skia-gm-strokerects`, `skia-gm-thinstrokedrects`
- Support claim allowed: `False`

## Support Guard

- supportClaimsChanged: `False`
- thresholdsChanged: `False`
- edgeBudgetChanged: `False`
- policyOnlyRowsPromoted: `False`
- belowThresholdCountedAsProductionGap: `False`
- broadPathAASupportClaimed: `False`
- ganeshPort: `False`
- graphitePort: `False`

## Validation Commands

- `rtk python3 scripts/m90_path_aa_slice.py`
- `rtk ./gradlew --no-daemon pipelineM90PathAaSlice`
- `rtk git diff --check`
