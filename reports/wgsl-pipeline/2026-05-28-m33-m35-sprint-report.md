# M33-M35 MVP Tail Sprint Report

Date: 2026-05-28
Verified commit: `743f20743763790e89141dc91187f639762107e3`
Linear project: Kanvas - WGSL Pipeline Target

## Verdict

The M33-M35 MVP tail backlog is complete. Linear reports every child task,
parent epic, and milestone in the sprint scope as `Done`, and the repository
state on `master` records MVP readiness at 100%.

This is an MVP release-candidate closeout, not a claim of full Skia parity.
The remaining breadth is visible as accepted limitations or dependency-gated
follow-up work.

## Linear Verification

Checked on 2026-05-28:

| Scope | Linear items | State |
|---|---|---|
| M33 Path AA MVP boundary | `GRA-101`, `GRA-104` through `GRA-108` | Done |
| M34 Image-filter MVP lane | `GRA-102`, `GRA-109` through `GRA-113` | Done |
| M35 MVP release candidate | `GRA-103`, `GRA-114` through `GRA-118` | Done |
| Milestones | M33, M34, M35 | 100% |

No open child issue remains in the MVP tail backlog.

## Repository Evidence

| Area | Evidence |
|---|---|
| README roadmap | `README.md` reports MVP readiness at 100% and M33-M35 as Done. |
| Release readiness | `.upstream/specs/release-readiness-mvp.md` is Accepted. |
| Path AA boundary | `reports/wgsl-pipeline/2026-05-27-m33-path-aa-closeout.md` |
| Image-filter boundary | `reports/wgsl-pipeline/2026-05-27-m34-image-filter-closeout.md` |
| CI/conformance/smoke | `reports/wgsl-pipeline/2026-05-27-m35-ci-conformance-evidence.md` |
| Full inventory | `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md` |
| PM evidence | `reports/wgsl-pipeline/2026-05-27-m35-pm-evidence-package.md` |
| Final closeout | `reports/wgsl-pipeline/2026-05-27-m35-closeout.md` |

## Sprint Outcomes

M33 made the Path AA boundary release-safe: the dominant
`coverage.edge-count-exceeded` rows remain stable expected unsupported
inventory, and `AnalyticAntialiasConvexWebGpuTest` is promoted to required
GPU smoke.

M34 made the image-filter boundary explicit: `Crop(input = nonNull)` remains
an accepted MVP limitation under
`image-filter.crop-input-nonnull-prepass-required`, and `SimpleOffsetImageFilter*`
fixtures stay out of required GPU smoke.

M35 closed the release candidate: required raster/GPU CI, conformance,
adapter-backed smoke, full inventory classification, README/spec readiness,
and PM closeout evidence are all documented.

## Final Inventory Interpretation

Source: `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md`.

| Category | Count | Decision |
|---|---:|---|
| `expected-unsupported-diagnostic` | 50 | Accepted Path AA / coverage breadth boundary. |
| `unsupported-image-filter` | 2 | Accepted `Crop(input = nonNull)` MVP limitation. |
| `adapter-skip` | 48 | Adapter/dependency placeholder families outside required smoke. |
| `similarity-regression` | 0 | No unresolved similarity regression. |
| `adapter-missing` | 0 | Adapter-backed evidence exists for required smoke. |
| `unexpected-exception` | 0 | No unowned blocker category remains. |

## Accepted Limitations

| Limitation | Stable reason | Post-MVP follow-up |
|---|---|---|
| Broad Path AA / coverage edge-budget overflow | `coverage.edge-count-exceeded` | Expand coverage strategy support or fallback policy with measured evidence. |
| `SkImageFilters.Crop(input = nonNull)` child pre-pass | `image-filter.crop-input-nonnull-prepass-required` | Implement render-to-texture child pre-pass with WebGPU and cross-backend tests. |
| Font/codec placeholder families | adapter/dependency skip | Keep dependency-gated until real font/codec deliveries land. |

## Validation

Commands run on 2026-05-28 for this sprint closeout:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineConformanceReport
```

Result: both passed. `pipelineConformanceReport` also ran the required
`pipelineConformance` task and regenerated
`build/reports/pipeline-conformance/m24-pipeline-conformance-report.md`.

The heavier adapter-backed smoke and full inventory evidence are captured in
the M35 reports listed above.
