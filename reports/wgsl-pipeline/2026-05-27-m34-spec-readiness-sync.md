# M34 Spec and Readiness Sync

Date: 2026-05-27
Linear: GRA-112
Branch: gra-112-image-filter-spec-sync

## Scope

Synchronize active docs with the GRA-110/GRA-111 decision: retain `SkImageFilters.Crop(input = nonNull)` as an accepted MVP limitation and keep the two `SimpleOffsetImageFilter*` fixtures out of required GPU smoke.

## Updated Files

| File | Update |
|---|---|
| `README.md` | MVP readiness moves from approximately 90% to 94%; M34 moves from Proposed to In Progress at 80% with the accepted limitation and smoke guard named. |
| `.upstream/specs/wgsl-pipeline/09-image-filter-mvp-lane.md` | Records the accepted M34 direction, exact affected rows, and the rule that no required smoke fixture may include those rows while the reason remains inventory-only. |
| `.upstream/specs/release-readiness-mvp.md` | Adds the M34 accepted limitation to release gates and lists the two current `SimpleOffsetImageFilter*` limitation rows. |

## Status Choice

`09-image-filter-mvp-lane.md` remains `Draft` for this ticket because GRA-113 still owns PM closeout and final M34 administrative evidence. The technical direction is final; the spec can move to Accepted during closeout once Linear and PM evidence are linked.

## Active Evidence

- GRA-109: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-inventory-reproduction.md`
- GRA-110: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-decision.md`
- GRA-111: `reports/wgsl-pipeline/2026-05-27-m34-crop-nonnull-limitation-hardening.md`

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineConformanceReport
```
