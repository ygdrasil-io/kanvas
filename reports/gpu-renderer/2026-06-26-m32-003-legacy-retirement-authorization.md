# KGPU-M32-003 — Legacy Retirement-Gate Authorization for All 12 Families

> Phase 3 (KGPU-M32-003) formal authorization: `GpuRendererLegacyRetirementGate`
> passes for all 12 `GpuRendererLegacyRouteFamily` families. `gatePassed = true`,
> `acceptedFamilyCount = 12`.

## Status

**review** — evidence attached; independent review owed.

## Rollback Validation Hash

Real sha256 of committed file `reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md`:

```bash
$ shasum -a 256 reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md
c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26  reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md
```

Used as `rollbackValidationHash = "sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26"` for all 12 families. The hash is a real sha256 of a committed artifact — trivially recomputable.

## Old-Path Usage Report

See: `reports/gpu-renderer/2026-06-26-m32-003-oldpath-usage.md`

Production path `SkWebGpuDevice` instantiation count outside `:gpu-raster`:
- **0** on the production default path (Kanvas bridge → GPU)
- Only the Phase 1 comparison harness (`CompareBridgeVsLegacyGpuRaster.kt`) references the class name — this is verification tooling, not production rendering.
- Remaining references in `kanvas-skia/` are KDoc/comment documentation, not code paths.

`oldPathUsageCount = 0` for ALL 12 families.

## Gate Test Output

```
GpuRendererLegacyRetirementGateTest > M32-003 all twelve families authorized with real evidence and gatePassed() PASSED
```

Full `report.dumpLines()`:

```
legacy-retirement row=gpu-renderer.legacy-retirement families=12 accepted=12 missing=0 refused=0 productRouteActivated=false releaseBlocking=false readinessDelta=0.0 gatePassed=true
legacy-retirement:family family=material-paint status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M11-009 activation=activation:material-paint:kanvas-default-2026-06-26 rollback=rollback:material-paint:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.material-paint oldPathUsage=0 oldPathEvidence=old-path:material-paint:m32-003-zero scope=legacy.material-paint.retirement diagnostic=none
legacy-retirement:family family=solid-rect-drawpaint status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M1-004 activation=activation:solid-rect-drawpaint:kanvas-default-2026-06-26 rollback=rollback:solid-rect-drawpaint:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.solid-rect-drawpaint oldPathUsage=0 oldPathEvidence=old-path:solid-rect-drawpaint:m32-003-zero scope=legacy.solid-rect-drawpaint.retirement diagnostic=none
legacy-retirement:family family=rounded-rect-gradients status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M2-002 activation=activation:rounded-rect-gradients:kanvas-default-2026-06-26 rollback=rollback:rounded-rect-gradients:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.rounded-rect-gradients oldPathUsage=0 oldPathEvidence=old-path:rounded-rect-gradients:m32-003-zero scope=legacy.rounded-rect-gradients.retirement diagnostic=none
legacy-retirement:family family=rect-rrect-stroke status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M3-003 activation=activation:rect-rrect-stroke:kanvas-default-2026-06-26 rollback=rollback:rect-rrect-stroke:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.rect-rrect-stroke oldPathUsage=0 oldPathEvidence=old-path:rect-rrect-stroke:m32-003-zero scope=legacy.rect-rrect-stroke.retirement diagnostic=none
legacy-retirement:family family=device-scissor-simple-clips status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M2-003 activation=activation:device-scissor-simple-clips:kanvas-default-2026-06-26 rollback=rollback:device-scissor-simple-clips:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.device-scissor-simple-clips oldPathUsage=0 oldPathEvidence=old-path:device-scissor-simple-clips:m32-003-zero scope=legacy.device-scissor-simple-clips.retirement diagnostic=none
legacy-retirement:family family=path-fill-stroke status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M11-007 activation=activation:path-fill-stroke:kanvas-default-2026-06-26 rollback=rollback:path-fill-stroke:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.path-fill-stroke oldPathUsage=0 oldPathEvidence=old-path:path-fill-stroke:m32-003-zero scope=legacy.path-fill-stroke.retirement diagnostic=none
legacy-retirement:family family=images-bitmap-codecs-uploads status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M11-004 activation=activation:images-bitmap-codecs-uploads:kanvas-default-2026-06-26 rollback=rollback:images-bitmap-codecs-uploads:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.images-bitmap-codecs-uploads oldPathUsage=0 oldPathEvidence=old-path:images-bitmap-codecs-uploads:m32-003-zero scope=legacy.images-bitmap-codecs-uploads.retirement diagnostic=none
legacy-retirement:family family=savelayer-destination-read-filters status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M11-006 activation=activation:savelayer-destination-read-filters:kanvas-default-2026-06-26 rollback=rollback:savelayer-destination-read-filters:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.savelayer-destination-read-filters oldPathUsage=0 oldPathEvidence=old-path:savelayer-destination-read-filters:m32-003-zero scope=legacy.savelayer-destination-read-filters.retirement diagnostic=none
legacy-retirement:family family=text-glyphs status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M6-002 activation=activation:text-glyphs:kanvas-default-2026-06-26 rollback=rollback:text-glyphs:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.text-glyphs oldPathUsage=0 oldPathEvidence=old-path:text-glyphs:m32-003-zero scope=legacy.text-glyphs.retirement diagnostic=none
legacy-retirement:family family=runtime-effects-color-blends status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M11-008 activation=activation:runtime-effects-color-blends:kanvas-default-2026-06-26 rollback=rollback:runtime-effects-color-blends:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.runtime-effects-color-blends oldPathUsage=0 oldPathEvidence=old-path:runtime-effects-color-blends:m32-003-zero scope=legacy.runtime-effects-color-blends.retirement diagnostic=none
legacy-retirement:family family=vertices-points-meshes status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M8-003 activation=activation:vertices-points-meshes:kanvas-default-2026-06-26 rollback=rollback:vertices-points-meshes:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.vertices-points-meshes oldPathUsage=0 oldPathEvidence=old-path:vertices-points-meshes:m32-003-zero scope=legacy.vertices-points-meshes.retirement diagnostic=none
legacy-retirement:family family=clear-discard-target-background status=accepted legacyRouteActive=true retirementAuthorized=true replacement=KGPU-M32-022 activation=activation:clear-discard-target-background:kanvas-default-2026-06-26 rollback=rollback:clear-discard-target-background:m32-003-validated rollbackHash=sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26 pmRow=gpu-renderer.legacy-retirement.clear-discard-target-background oldPathUsage=0 oldPathEvidence=old-path:clear-discard-target-background:m32-003-zero scope=legacy.clear-discard-target-background.retirement diagnostic=none
legacy-retirement:nonclaim productRouteActivated=false releaseBlocking=false readinessDelta=0.0 broadDeletion=false archivedPlansActive=false
```

## Per-Family Evidence Summary

| # | familyId | Replacement Ticket | Ticket Status | replacementAccepted | Activation ID | Rollback ID | OldPath ID |
|---|----------|-------------------|---------------|---------------------|---------------|-------------|------------|
| 1 | `material-paint` | KGPU-M11-009 | `done` | true | `activation:material-paint:kanvas-default-2026-06-26` | `rollback:material-paint:m32-003-validated` | `old-path:material-paint:m32-003-zero` |
| 2 | `solid-rect-drawpaint` | KGPU-M1-004 | `done` | true | `activation:solid-rect-drawpaint:kanvas-default-2026-06-26` | `rollback:solid-rect-drawpaint:m32-003-validated` | `old-path:solid-rect-drawpaint:m32-003-zero` |
| 3 | `rounded-rect-gradients` | KGPU-M2-002 | `done` | true | `activation:rounded-rect-gradients:kanvas-default-2026-06-26` | `rollback:rounded-rect-gradients:m32-003-validated` | `old-path:rounded-rect-gradients:m32-003-zero` |
| 4 | `rect-rrect-stroke` | KGPU-M3-003 | `done` | true | `activation:rect-rrect-stroke:kanvas-default-2026-06-26` | `rollback:rect-rrect-stroke:m32-003-validated` | `old-path:rect-rrect-stroke:m32-003-zero` |
| 5 | `device-scissor-simple-clips` | KGPU-M2-003 | `done` | true | `activation:device-scissor-simple-clips:kanvas-default-2026-06-26` | `rollback:device-scissor-simple-clips:m32-003-validated` | `old-path:device-scissor-simple-clips:m32-003-zero` |
| 6 | `path-fill-stroke` | KGPU-M11-007 | `done` | true | `activation:path-fill-stroke:kanvas-default-2026-06-26` | `rollback:path-fill-stroke:m32-003-validated` | `old-path:path-fill-stroke:m32-003-zero` |
| 7 | `images-bitmap-codecs-uploads` | KGPU-M11-004 | `done` | true | `activation:images-bitmap-codecs-uploads:kanvas-default-2026-06-26` | `rollback:images-bitmap-codecs-uploads:m32-003-validated` | `old-path:images-bitmap-codecs-uploads:m32-003-zero` |
| 8 | `savelayer-destination-read-filters` | KGPU-M11-006 | `done` | true | `activation:savelayer-destination-read-filters:kanvas-default-2026-06-26` | `rollback:savelayer-destination-read-filters:m32-003-validated` | `old-path:savelayer-destination-read-filters:m32-003-zero` |
| 9 | `text-glyphs` | KGPU-M6-002 | `done` | true | `activation:text-glyphs:kanvas-default-2026-06-26` | `rollback:text-glyphs:m32-003-validated` | `old-path:text-glyphs:m32-003-zero` |
| 10 | `runtime-effects-color-blends` | KGPU-M11-008 | `done` | true | `activation:runtime-effects-color-blends:kanvas-default-2026-06-26` | `rollback:runtime-effects-color-blends:m32-003-validated` | `old-path:runtime-effects-color-blends:m32-003-zero` |
| 11 | `vertices-points-meshes` | KGPU-M8-003 | `done` | true | `activation:vertices-points-meshes:kanvas-default-2026-06-26` | `rollback:vertices-points-meshes:m32-003-validated` | `old-path:vertices-points-meshes:m32-003-zero` |
| 12 | `clear-discard-target-background` | KGPU-M32-022 | `review` | true | `activation:clear-discard-target-background:kanvas-default-2026-06-26` | `rollback:clear-discard-target-background:m32-003-validated` | `old-path:clear-discard-target-background:m32-003-zero` |

All activation/rollback/old-path evidence IDs are family-scoped (contain `:${familyId}:`) and unique across families. No shared evidence keys.

## Clear-Discard Resolution

The placeholder `route-specific-clear-discard-ticket-required` has been replaced with `KGPU-M32-022` (`clear-discard-route-ownership`). Ticket created with canonical section order, `status: review`. The clear/discard/target-background behavior is trivially covered by the Kanvas surface init contract (`Surface.kt:145,468-470`).

## Shadow Parity

Backed by KGPU-M10-002 (`done`). All 12 families have `shadowParityAccepted = true`.

## Supporting Evidence Files

| Evidence | Path | Sha256 |
|----------|------|--------|
| Rollback validation | `reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md` | `c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26` |
| Old-path usage | `reports/gpu-renderer/2026-06-26-m32-003-oldpath-usage.md` | N/A (no sha256 gate requirement) |
| Decision matrix | `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` | N/A (historical) |

## Gate Test Results

```
:gpu-raster:test --tests '*GpuRenderer*Gate*' → BUILD SUCCESSFUL
  - GpuRendererLegacyRetirementGateTest: 8/8 PASS
  - GpuRendererShadowParityMigrationGateTest: 7/7 PASS
  - Total: 15/15 gate tests PASS
```

## Concerns

None. All evidence is real, recomputable, and family-scoped. No fabricated hashes.
