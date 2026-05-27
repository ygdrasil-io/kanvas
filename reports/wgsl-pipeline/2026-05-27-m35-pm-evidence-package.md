# M35 MVP PM Evidence Package

Date: 2026-05-27
Linear: GRA-117
Branch: gra-117-m35-pm-evidence
Base commit: `8b952109b7ee006246103cdabeb03ea638cce244`
Project: Kanvas - WGSL Pipeline Target

## PM Capability Statement

Kanvas has an MVP release-candidate WebGPU path whose required CI, conformance, smoke, and full inventory gates are documented. Remaining GPU inventory failures are classified as accepted unsupported breadth, accepted image-filter limitation, or adapter-dependent placeholders; no similarity regression or unexpected exception remains.

## Milestone Evidence Timeline

| Milestone | PM-visible outcome | Primary evidence |
|---|---|---|
| M32 Bitmap/ImageRect | All original bitmap/image-rect GPU similarity regressions are fixed without floor lowering; one image-rect fixture is required smoke. | `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-closeout.md` |
| M33 Path AA boundary | Broad Path AA edge-budget failures are stable expected unsupported diagnostics; one rendered AA fixture is required smoke. | `reports/wgsl-pipeline/2026-05-27-m33-path-aa-closeout.md` |
| M34 Image-filter MVP lane | `Crop(input = nonNull)` stays an accepted MVP limitation with smoke guard coverage. | `reports/wgsl-pipeline/2026-05-27-m34-image-filter-closeout.md` |
| M35 CI/conformance | Required raster CI, GPU CI, conformance, conformance report, and adapter-backed smoke evidence are green. | `reports/wgsl-pipeline/2026-05-27-m35-ci-conformance-evidence.md` |
| M35 full inventory | Full GPU inventory is classified with no unowned blocker category. | `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md` |
| M35 readiness sync | README and active release-readiness specs match the technical gate state. | `reports/wgsl-pipeline/2026-05-27-m35-readiness-sync.md` |

## M35 PR Evidence

| Ticket | PR | Merge commit | Result |
|---|---|---|---|
| GRA-114 | https://github.com/ygdrasil-io/kanvas/pull/1186 | `7d92391490e59746257297de962f7a6474922307` | Collected final CI, conformance, and smoke evidence. |
| GRA-115 | https://github.com/ygdrasil-io/kanvas/pull/1187 | `eb8fa2b2af1f4bde38e90ab91e1464d44201dd3d` | Fixed SaveLayer unexpected rows and finalized full GPU inventory audit. |
| GRA-116 | https://github.com/ygdrasil-io/kanvas/pull/1188 | `8b952109b7ee006246103cdabeb03ea638cce244` | Synced README/spec readiness state after technical gates turned green. |
| GRA-117 | https://github.com/ygdrasil-io/kanvas/pull/1189 | `41a1d55032797c2e74a6ba1b6f517c16c99ffdee` | Added the PM evidence package. |
| GRA-118 | https://github.com/ygdrasil-io/kanvas/pull/1190 | `743f20743763790e89141dc91187f639762107e3` | Closed the MVP release candidate and project state. |

## Required CI Evidence

Latest code-affecting M35 PR: #1187.

| Gate | Status | Link |
|---|---|---|
| `Raster tests (ubuntu)` | Pass | https://github.com/ygdrasil-io/kanvas/actions/runs/26540746979/job/78181317676 |
| `GPU tests (macos)` | Pass | https://github.com/ygdrasil-io/kanvas/actions/runs/26540746979/job/78181317681 |
| `GPU inventory (macos, non-blocking)` | Expected non-blocking failure | https://github.com/ygdrasil-io/kanvas/actions/runs/26540746979/job/78181501536 |

The non-blocking inventory job runs the classified breadth suite and is expected to fail while inventory-only expected unsupported rows remain. The release gate is the generated classification, not a green non-blocking inventory job.

## Final Inventory Summary

Source: `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md`.

| Category | Count | PM interpretation |
|---|---:|---|
| `expected-unsupported-diagnostic` | 50 | Accepted Path AA / coverage breadth boundary, all `coverage.edge-count-exceeded`. |
| `unsupported-image-filter` | 2 | Accepted M34 `Crop(input = nonNull)` limitation. |
| `adapter-skip` | 48 | Adapter-dependent placeholder families excluded from required smoke. |
| `similarity-regression` | 0 | No unresolved similarity regression. |
| `adapter-missing` | 0 | Adapter-backed local evidence existed for the audit. |
| `unexpected-exception` | 0 | No unowned blocker category remains. |

## Required Smoke Scope

Required GPU smoke remains intentionally narrower than the full inventory:

- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`
- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
- `org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest`

The smoke lane excludes known inventory-only limitation rows until implementation evidence exists.

## Accepted MVP Limitations

| Limitation | Stable reason | Release condition | Follow-up dependency |
|---|---|---|---|
| Broad Path AA / coverage edge-budget overflow | `coverage.edge-count-exceeded` | Inventory-only expected unsupported; not required smoke. | Post-MVP coverage strategy promotion/fallback breadth. |
| `SkImageFilters.Crop(input = nonNull)` child pre-pass | `image-filter.crop-input-nonnull-prepass-required` | Exactly two `SimpleOffsetImageFilter*` rows; blocked from required smoke. | Render-to-texture child pre-pass with WebGPU and cross-backend evidence. |
| Font/codec placeholder families | adapter/dependency skipped | Treated as dependency-gated, not implemented. | Real font/codec dependency deliveries. |

## Demo Script

Use this as the PM closeout walkthrough:

1. Open `README.md` and show MVP readiness at 100% with M32, M33, M34, and M35 done.
2. Open `reports/wgsl-pipeline/2026-05-27-m35-ci-conformance-evidence.md` and point to required CI, conformance, and adapter-backed smoke evidence.
3. Open `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md` and point to the final inventory summary: `similarity-regression=0`, `unexpected-exception=0`, `adapter-missing=0`.
4. Open `.upstream/specs/release-readiness-mvp.md` and show that the release-readiness spec is accepted and linked to final PM/Linear closeout evidence.
5. State the accepted limitations without implying full Skia coverage: edge-budget breadth and `Crop(input = nonNull)` pre-pass remain post-MVP work.

## Closeout Handoff

GRA-118 performed the final administrative closeout:

- README M35 is Done / 100%;
- `.upstream/specs/release-readiness-mvp.md` is Accepted;
- final closeout note exists under `reports/wgsl-pipeline/`;
- M35 and parent GRA-103 are Done in Linear.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineConformanceReport
```
