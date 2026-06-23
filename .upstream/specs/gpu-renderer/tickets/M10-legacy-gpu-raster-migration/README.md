# M10 - Legacy gpu-raster Migration

## Goal

Move legacy `gpu-raster` routes toward `:gpu-renderer` only through reviewed
shadow evidence, explicit activation gates, rollback policy, and route-specific
retirement tickets.

## Dependencies

Depends on M1 activation policy and feature-specific milestones. Legacy cleanup
must cite `06-legacy-adapter-cleanup.md` and preserve archived evidence
boundaries.

## Exit Criteria

- [x] Legacy route ownership and replacement status are inventoried per family.
- [x] Shadow parity evidence exists before any default route change.
- [x] Retirement gates are scoped to promoted replacement slices only.
- [x] Root PM packaging states whether evidence is adapter-independent or
      adapter-backed opt-in.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M10-001 - Inventory legacy `gpu-raster` route ownership](KGPU-M10-001-inventory-legacy-gpu-raster-route-ownership.md) | `done` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `legacy-adapter` | `KGPU-M1-001` | `gpu-raster legacy` |
| [KGPU-M10-002 - Add per-family shadow parity migration gates](KGPU-M10-002-add-per-family-shadow-parity-migration-gates.md) | `done` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `migration-validation` | `KGPU-M10-001` | `gpu-raster legacy` |
| [KGPU-M10-003 - Retire legacy routes after promoted replacements](KGPU-M10-003-retire-legacy-routes-after-promoted-replacements.md) | `done` | `P1` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `legacy-cleanup` | `KGPU-M10-002`, `KGPU-M1-004` | `gpu-raster legacy` |
| [KGPU-M10-004 - Add archived evidence hygiene for migrated routes](KGPU-M10-004-add-archived-evidence-hygiene-for-migrated-routes.md) | `done` | `P1` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `docs-evidence` | `KGPU-M10-001` | `archives` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
```

## Non-Claims

- This milestone does not delete or change default legacy routes by itself.
- Migration evidence is per-family and cannot imply broad renderer parity.
- Archived plans remain historical evidence only.

## Current Evidence

- `reports/gpu-renderer/2026-06-15-m10-legacy-inventory-hygiene.md`
  inventories legacy `gpu-raster` ownership per family and links each row to
  current `:gpu-renderer` tickets, blockers, or refusal gates.
- The inventory keeps the legacy default active for every family and records no
  route retirement.
- Root PM packaging is classified as adapter-independent for the root activation
  candidate, while executed R6 PM evidence remains adapter-backed opt-in and is
  not a root `pipelinePmBundle` dependency.
- KGPU-M10-002 is done: `GpuRendererShadowParityMigrationGate` now records
  per-family shadow parity requirements and refuses missing, duplicate,
  broad, non-adapter-backed, activated, release-blocking, or readiness-moving
  evidence while keeping legacy defaults active. Independent review
  `019ed714-fd15-72e2-a8f8-b1b0f9fbe2f5` accepted the implementation after
  remediation linked the review and added broad/shared evidence refusal plus
  explicit family coverage.
- KGPU-M10-003 is done: `GpuRendererLegacyRetirementGate` now requires
  each retirement row to be family-scoped and to name an accepted replacement
  ticket, activation decision, rollback evidence, rollback validation hash,
  old-path usage evidence, PM row, and M10-002 shadow parity result. Missing,
  duplicate, shared, generic, archived-only, broad deletion, activated,
  release-blocking, or readiness-moving evidence keeps the legacy route active;
  individually shared activation, rollback, or old-path evidence is refused as
  broad retirement evidence. Accepted rows authorize future scoped removal
  only; this ticket does not disable production routes. Evidence report:
  `reports/gpu-renderer/2026-06-17-m10-003-legacy-retirement-gates.md`.
  Independent review `019ed5fb-8292-7931-b494-9034a88e15e0` accepted the
  implementation after per-artifact shared evidence refusal and stale-report
  status fixes.
- Archive hygiene remains explicit: archived plans and root upstream snapshots
  are historical evidence only, not active backlog or acceptance criteria.
- Independent review `019ec878-7c64-7e42-ab70-bb80043e53d1` accepted
  KGPU-M10-001 and KGPU-M10-004 for `done` after remediation added explicit
  material/paint, rect/rrect stroke, and clear/discard inventory rows.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
