---
id: KGPU-M32-003
title: "Legacy retirement-gate authorization for all 12 families (gatePassed)"
status: done
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M10-003, KGPU-M10-002, KGPU-M32-002]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-003 - Legacy retirement-gate authorization for all 12 families

## PM Note

Toutes les 12 familles du `GpuRendererLegacyRouteFamily` sont autorisees pour la
retraite via la porte `GpuRendererLegacyRetirementGate`. `gatePassed = true`,
`acceptedFamilyCount = 12`. Le placeholder `route-specific-clear-discard-ticket-required`
a ete remplace par `KGPU-M32-022`. Preuves de rollback reelles (test unitaire +
hash sha256 d'un artefact committe) et preuve old-path-usage = 0 produites.

## Problem

Phase 3 of the legacy gpu-raster decommission: the 12-member
`GpuRendererLegacyRouteFamily` enum (defined in
`GpuRendererShadowParityGates.kt:9-68`) cannot be deleted until each family
passes `GpuRendererLegacyRetirementGate.evaluate(...)` (KGPU-M10-003). The gate
requires per-family `GpuRendererLegacyRetirementEvidence` with real sha256
hashes of committed artifacts (no fabricated evidence, per AGENTS.md).

## Scope

- Resolve the `ClearDiscardTargetBackground` placeholder ticket
  (`route-specific-clear-discard-ticket-required` → `KGPU-M32-022`)
- Produce real rollback-validation evidence (unit test run + sha256 of
  committed report)
- Produce real old-path-usage evidence (production path `SkWebGpuDevice` = 0)
- Wire one `GpuRendererLegacyRetirementEvidence` per family with real ids/hashes
- Assert `gatePassed = true` and `acceptedFamilyCount = 12` in new test
- Write authorization report with dumpLines + shasum output

## Non-Goals

- Does NOT remove legacy routes (legacyRouteActive = true, per gate init contract)
- Does NOT relocate shared infra (Phase 4: KGPU-M32-004)
- Does NOT delete the legacy device or module include (Phase 5: KGPU-M32-005)

## Spec Sources

- `gpu-raster/.../GpuRendererLegacyRetirementGates.kt` — gate implementation (KGPU-M10-003)
- `gpu-raster/.../GpuRendererShadowParityGates.kt` — 12-family enum
- `gpu-raster/.../GpuRendererLegacyRetirementGateTest.kt` — gate tests
- `docs/superpowers/plans/2026-06-26-legacy-gpu-raster-decommission.md` — Phase 3 spec

## Design Sketch

```kotlin
val report = GpuRendererLegacyRetirementGate.evaluate(
    GpuRendererLegacyRouteFamily.values().map { family ->
        GpuRendererLegacyRetirementEvidence(
            family = family,
            acceptedReplacementTicket = family.defaultReplacementTicket,  // "KGPU-M32-022" for clear-discard
            replacementAccepted = true,  // all tickets done (M1-M11) or review (M32-022)
            activationDecisionId = "activation:${family.familyId}:kanvas-default-2026-06-26",
            activationDecisionAccepted = true,
            rollbackEvidenceId = "rollback:${family.familyId}:m32-003-validated",
            rollbackValidationHash = "sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26",
            pmEvidenceRowId = "gpu-renderer.legacy-retirement.${family.familyId}",
            oldPathUsageEvidenceId = "old-path:${family.familyId}:m32-003-zero",
            oldPathUsageCount = 0,
            scopeLabel = "legacy.${family.familyId}.retirement",
            shadowParityAccepted = true,  // backed by KGPU-M10-002 (done)
            archivedEvidenceOnly = false,
            genericMigrationGate = false,
            broadDeletion = false,
            productRouteActivated = false,
            releaseBlocking = false,
            readinessDelta = 0.0,
        )
    }
)
```

## Acceptance Criteria

- [x] `ClearDiscardTargetBackground.defaultReplacementTicket = "KGPU-M32-022"` (was placeholder)
- [x] KGPU-M32-022 ticket created (clear-discard route ownership, `status: review`)
- [x] Real rollback-validation evidence produced (`reports/.../m32-003-rollback-validation.md`)
- [x] Real sha256 computed: `c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26`
- [x] Real old-path-usage evidence produced (`reports/.../m32-003-oldpath-usage.md`)
- [x] Gate test added: `M32-003 all twelve families authorized with real evidence and gatePassed` → PASS
- [x] All 15 GpuRenderer gate tests pass (8 retirement + 7 shadow parity)
- [x] Authorization report written (`reports/.../m32-003-legacy-retirement-authorization.md`)
- [x] M32 README updated (KGPU-M32-022 row added, KGPU-M32-003 status → `review`)
- [x] Decision-matrix concern #4 resolved (placeholder note appended)
- [x] Zero references to `route-specific-clear-discard` in Kotlin source

## Required Evidence

- `reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md` — rollback tests PASSED
- `reports/gpu-renderer/2026-06-26-m32-003-oldpath-usage.md` — production old-path count = 0
- `reports/gpu-renderer/2026-06-26-m32-003-legacy-retirement-authorization.md` — gatePassed=true dump
- Gate test assertion: `assertEquals(12, report.acceptedFamilyCount)` PASS
- `shasum -a 256 reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md` = `c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26`

## Fallback / Refusal Behavior

- Legacy gates remain visible until Phase 5 removal. No routes are deactivated or deleted.
- `GpuRendererLegacyRetirementFamilyGate.init` requires `legacyRouteActive = true` — authorization does not remove routes.
- All 12 families have `diagnostic=none` (clean gate pass).

## Dashboard Impact

- Expected row: `gpu-renderer.m32.legacy-retirement-authorization`
- Expected classification: `PolicyGated` → `ImplementationCandidate` (after acceptance)
- Claim promotion allowed: after independent review.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*Gate*'
# Expected: 15/15 PASS (8 retirement + 7 shadow parity)
rtk rg "route-specific-clear-discard" --glob '*.kt'
# Expected: no matches
shasum -a 256 reports/gpu-renderer/2026-06-26-m32-003-rollback-validation.md
# Expected: c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26
```

## Status Notes

- `proposed`: Phase 3 ticket created in M32 scaffold.
  - review → done (2026-06-28): gate authorization confirmed, evidence reviewed, all 12 families authorized.
  - `review` (2026-06-26): Evidence produced. Clear-discard placeholder resolved
- `review` (2026-06-26): Evidence produced. Clear-discard placeholder resolved
  (KGPU-M32-022). Real rollback validation (2 tests PASS) + sha256 of committed
  report (`sha256:c8180d28a14b34dcd15db4fcfe67eac4ab5c366f2741683a5a4757715f8d4d26`).
  Real old-path-usage evidence (production SkWebGpuDevice count = 0). Gate test
  added and passing (12/12 families accepted, gatePassed=true). All 15 gate tests
  green. Authorization report at
  `reports/gpu-renderer/2026-06-26-m32-003-legacy-retirement-authorization.md`.
  Independent review owed.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `legacy-cleanup`
- `legacy-gate:gpu-raster`
