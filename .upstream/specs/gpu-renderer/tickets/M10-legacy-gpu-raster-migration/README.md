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

- [ ] Legacy route ownership and replacement status are inventoried per family.
- [ ] Shadow parity evidence exists before any default route change.
- [ ] Retirement gates are scoped to promoted replacement slices only.
- [ ] Root PM packaging states whether evidence is adapter-independent or
      adapter-backed opt-in.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M10-001 - Inventory legacy `gpu-raster` route ownership](KGPU-M10-001-inventory-legacy-gpu-raster-route-ownership.md) | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `legacy-adapter` | `KGPU-M1-001` | `gpu-raster legacy` |
| [KGPU-M10-002 - Add per-family shadow parity migration gates](KGPU-M10-002-add-per-family-shadow-parity-migration-gates.md) | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `migration-validation` | `KGPU-M10-001` | `gpu-raster legacy` |
| [KGPU-M10-003 - Retire legacy routes after promoted replacements](KGPU-M10-003-retire-legacy-routes-after-promoted-replacements.md) | `proposed` | `P1` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `legacy-cleanup` | `KGPU-M10-002`, `KGPU-M1-004` | `gpu-raster legacy` |
| [KGPU-M10-004 - Add archived evidence hygiene for migrated routes](KGPU-M10-004-add-archived-evidence-hygiene-for-migrated-routes.md) | `proposed` | `P1` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `docs-evidence` | `KGPU-M10-001` | `archives` |

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

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
