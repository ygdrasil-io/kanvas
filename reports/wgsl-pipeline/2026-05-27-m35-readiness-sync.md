# M35 Readiness Sync

Date: 2026-05-27
Linear: GRA-116
Branch: gra-116-m35-readiness-sync
Base commit: `eb8fa2b2af1f4bde38e90ab91e1464d44201dd3d`

## Goal

Synchronize README and active release-readiness specs after the final M35 CI/conformance and full GPU inventory gates landed.

## Inputs

| Evidence | Source |
|---|---|
| Final CI/conformance/smoke gates | `reports/wgsl-pipeline/2026-05-27-m35-ci-conformance-evidence.md` |
| Final full GPU inventory audit | `reports/wgsl-pipeline/2026-05-27-m35-full-gpu-inventory.md` |
| GRA-115 merge | PR #1187, commit `eb8fa2b2af1f4bde38e90ab91e1464d44201dd3d` |

## Sync Changes

| File | Change |
|---|---|
| `README.md` | Moved MVP readiness from 95% to 98%; moved M35 from `Proposed` / 0% to `In Progress` / 80%. |
| `.upstream/specs/release-readiness-mvp.md` | Recorded current gate evidence and marked the spec as technical-gates-green while PM evidence and Linear closeout remain pending. |
| `.upstream/specs/README.md` | Updated the M35 spec-pack status to `Draft (technical gates green, PM closeout pending)`. |

## Gate State After Sync

| Gate | Status | Evidence |
|---|---|---|
| Required raster CI | Pass | PR #1187 `Raster tests (ubuntu)` job `78181317676` |
| Required GPU CI | Pass | PR #1187 `GPU tests (macos)` job `78181317681` |
| `pipelineConformance` | Pass | GRA-114 report |
| `pipelineConformanceReport` | Pass | GRA-114 report |
| Required `gpuSmokeTest` | Pass | GRA-114 local adapter-backed evidence |
| Full GPU inventory | Pass | GRA-115 report: total 100, similarity 0, unexpected 0 |
| M32 similarity regressions | Pass | final inventory `similarity-regression=0` |
| GRA-100 SaveLayer unexpected exception | Pass | final inventory `unexpected-exception=0`; `SaveLayerTest` targeted run passed |
| M33 Path AA boundary | Pass | 50 `coverage.edge-count-exceeded` rows remain expected unsupported |
| M34 image-filter limitation | Pass | exactly two `SimpleOffsetImageFilter*` rows remain `image-filter.crop-input-nonnull-prepass-required` |
| PM evidence package | Pending | GRA-117 |
| Linear/project closeout | Pending | GRA-118 |

## Interpretation

M35 is no longer a proposed block. The technical readiness gates are complete and documented, but the milestone is not marked Done until PM evidence and project closeout are linked. The release-readiness spec remains Draft by policy until those final administrative artifacts land.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineConformanceReport
```
