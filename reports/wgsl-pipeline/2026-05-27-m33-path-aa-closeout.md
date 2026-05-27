# M33 Path AA MVP Boundary Closeout

Date: 2026-05-27
Linear: GRA-108
Parent epic: GRA-101
Milestone: M33 -- Path AA MVP Boundary

## PM Summary

M33 is complete for the MVP Path AA boundary. The remaining broad Path AA
inventory failures are explicit expected unsupported edge-budget refusals, not
similarity regressions or unclassified exceptions. One rendered analytic-AA
fixture is now required GPU smoke.

## Final Inventory Status

Fresh post-M32 inventory from GRA-105:

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

Path AA/coverage scope:

| Slice | Count | Final classification |
|---|---:|---|
| `coverage.edge-count-exceeded` WebGPU GM rows | 21 | Expected unsupported Path AA breadth |
| `coverage.edge-count-exceeded` cross-backend rows | 28 | Expected unsupported Path AA breadth |
| `coverage.edge-count-exceeded` benchmark methodology row | 1 | Expected unsupported Path AA breadth |
| Rendered-but-below-floor AA rows | 0 | None observed |
| Unclassified AA rows | 0 | None observed |

## Edge-Budget Decision

`coverage.edge-count-exceeded` remains an MVP limitation and inventory-only
expected unsupported diagnostic.

GRA-106 hardened the fail-closed behavior:

- the selector emits the stable `coverage.edge-count-exceeded` reason;
- the inventory classifier maps only that exact known code to expected
  unsupported;
- unknown future `coverage.*` codes remain visible as `unexpected-exception`;
- refused edge-budget fixtures are not smoke candidates.

## Smoke Decision

M33 promotes one rendered Path AA fixture to required GPU smoke:

```text
org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest
```

This fixture is separate from the 50 edge-budget refusal rows. It exercises
the analytic-AA convex-fill path and passed local and CI smoke validation.

Required GPU smoke after M33 includes:

- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`
- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
- `org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest`

## PR And CI Links

| Ticket | PR | Merge commit | Required CI |
|---|---|---|---|
| GRA-105 | https://github.com/ygdrasil-io/kanvas/pull/1177 | `ee710a2479741d91e41a0213cd80e4dc2a6449b2` | No required checks for report-only PR |
| GRA-106 | https://github.com/ygdrasil-io/kanvas/pull/1178 | `d1031ad2020358ba1dd25760ec447dfcfdd7c087` | Raster success: https://github.com/ygdrasil-io/kanvas/actions/runs/26538390733/job/78173240880; GPU success: https://github.com/ygdrasil-io/kanvas/actions/runs/26538390733/job/78173240881 |
| GRA-107 | https://github.com/ygdrasil-io/kanvas/pull/1179 | `0fdfbb43553903ece9abdcbbace231ae25f29b67` | Raster success: https://github.com/ygdrasil-io/kanvas/actions/runs/26538679617/job/78174277258; GPU success: https://github.com/ygdrasil-io/kanvas/actions/runs/26538679617/job/78174277233 |

## Evidence Reports

- `reports/wgsl-pipeline/2026-05-27-m33-path-aa-inventory-audit.md`
- `reports/wgsl-pipeline/2026-05-27-m33-edge-budget-classification-hardening.md`
- `reports/wgsl-pipeline/2026-05-27-m33-path-aa-smoke-promotion.md`

## Spec And README Status

- `.upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md` is Accepted.
- README MVP readiness moves from approximately 80 percent to approximately
  90 percent.
- README marks M33 Done / 100 percent.

## Remaining Path AA Limitations

The remaining limitation is broad edge-budget overflow support. It is already
represented as the explicit MVP limitation `coverage.edge-count-exceeded` and
tracked as existing WebGPU coverage breadth follow-up work (`GRA-70` /
post-MVP breadth).

No additional unclassified Path AA follow-up is required from M33.

## M34/M35 Handoff

M34 can consume M33 as a closed boundary:

- Path AA has one required smoke fixture.
- Edge-budget refusals are expected unsupported and fail-closed.
- No Path AA similarity regression or unexpected exception remains.

M35 can consume this report without rerunning Path AA triage.
