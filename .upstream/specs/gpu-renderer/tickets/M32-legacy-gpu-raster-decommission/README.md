# M32 - Legacy gpu-raster Decommission

## Goal

Remove the legacy gpu-raster device (`SkWebGpuDevice` + rollback path) after
every legacy route family is either ported to the Kanvas-native bridge with
real pixel parity or formally refused with a stable diagnostic, the 13
`GpuRendererLegacyRouteFamily` rows are authorized through
`GpuRendererLegacyRetirementGate` (KGPU-M10-003), and the shared
release-blocking infra hosted in `:gpu-raster` is relocated.

## Dependencies

Depends on M10 (Legacy gpu-raster Migration) for the per-family migration
decisions (KGPU-M10-001, KGPU-M10-002) and the
`GpuRendererLegacyRetirementGate` authorization (KGPU-M10-003). Depends on M31
(Production Activation) for the bridge ↔ legacy gpu-raster pixel/GM parity work
(KGPU-M31-005) and the real bridge GPU execution (KGPU-M31-006).

## Exit Criteria

- [ ] Per-family decommission decision matrix classifies all 13 `GpuRendererLegacyRouteFamily` rows as `port` or `refuse` (KGPU-M32-001)
- [ ] Every legacy route family is ported to the Kanvas-native bridge with real pixel parity or formally refused with a stable diagnostic, closing KGPU-M31-005 (KGPU-M32-002)
- [ ] All 13 families authorized through `GpuRendererLegacyRetirementGate` (KGPU-M10-003) (KGPU-M32-003)
- [ ] Shared WGSL / conformance / runtime-shader / gate infra relocated out of `:gpu-raster` (KGPU-M32-004)
- [ ] Legacy `SkWebGpuDevice`, the `useLegacyGpuRaster` rollback branch, and the `:gpu-raster` module include removed (KGPU-M32-005)
- [ ] Final decommission validation, evidence bundle, and PR delivered (KGPU-M32-006)

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| KGPU-M32-001 - Per-family decommission decision matrix | `proposed` | `P1` | `ReferenceOnly` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M10-001] | `gpu-raster legacy` |
| KGPU-M32-002 - Close M31-005: bridge ↔ legacy gpu-raster pixel parity or formal refusal | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M31-005, KGPU-M31-006] | `gpu-raster-legacy-path` |
| KGPU-M32-003 - Legacy retirement-gate authorization for all 13 families | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M10-003, KGPU-M10-002, KGPU-M32-002] | `gpu-raster legacy` |
| KGPU-M32-004 - Relocate shared WGSL/conformance/runtime-shader/gate infra out of :gpu-raster | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-003] | `gpu-raster legacy` |
| KGPU-M32-005 - Remove legacy device, rollback branch, and module include | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-004] | `gpu-raster legacy` |
| KGPU-M32-006 - Final decommission validation + evidence bundle + PR | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-005] | `gpu-raster legacy` |

Per-family port-or-refuse tickets (Phase 2) are enumerated as `KGPU-M32-010`
onward after `KGPU-M32-001` (the decision matrix) determines which
`GpuRendererLegacyRouteFamily` rows are `port` vs `refuse`.

Release-blocking tickets in this milestone: KGPU-M32-002, KGPU-M32-004,
KGPU-M32-005, and KGPU-M32-006. KGPU-M32-001 and KGPU-M32-003 are not
release-blocking.

## Validation Bundle

This scaffold milestone is documentation-only; module, parity, gate, and
removal validation commands are attached to each ticket as it is implemented.

```bash
rtk git diff --check
```

## Non-Claims

- Does not remove `:gpu-raster` until per-family port-or-refuse, retirement-gate authorization, and shared-infra relocation are complete and reviewed
- Introduces no new GPU route; scaffold route kinds are `CPUReferenceOnly` oracle/evidence only
- Claims no evidence yet; per-ticket evidence links are added as each ticket lands
- No Ganesh, Graphite, or SkSL compiler support

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
