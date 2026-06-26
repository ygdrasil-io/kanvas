# M32 - Legacy gpu-raster Decommission

## Goal

Remove the legacy gpu-raster device (`SkWebGpuDevice` + rollback path) after
every legacy route family is either ported to the Kanvas-native bridge with
real pixel parity or formally refused with a stable diagnostic, the 12
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

- [ ] Per-family decommission decision matrix classifies all 12 `GpuRendererLegacyRouteFamily` rows as `port` or `refuse` (KGPU-M32-001)
- [ ] Every legacy route family is ported to the Kanvas-native bridge with real pixel parity or formally refused with a stable diagnostic, closing KGPU-M31-005 (KGPU-M32-002)
- [ ] All 12 families authorized through `GpuRendererLegacyRetirementGate` (KGPU-M10-003) (KGPU-M32-003)
- [ ] Shared WGSL / conformance / runtime-shader / gate infra relocated out of `:gpu-raster` (KGPU-M32-004)
- [ ] Legacy `SkWebGpuDevice`, the `useLegacyGpuRaster` rollback branch, and the `:gpu-raster` module include removed (KGPU-M32-005)
- [ ] Final decommission validation, evidence bundle, and PR delivered (KGPU-M32-006)

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| KGPU-M32-001 - Per-family decommission decision matrix | `done` | `P1` | `ReferenceOnly` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M10-001] | `gpu-raster legacy` |
| KGPU-M32-002 - Close M31-005: bridge ↔ legacy gpu-raster pixel parity or formal refusal | `done` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | [KGPU-M31-005, KGPU-M31-006] | `gpu-raster-legacy-path` |
| KGPU-M32-003 - Legacy retirement-gate authorization for all 12 families | `review` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M10-003, KGPU-M10-002, KGPU-M32-002] | `gpu-raster legacy` |
| KGPU-M32-004 - Relocate shared WGSL/conformance/runtime-shader/gate infra out of :gpu-raster | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-003] | `gpu-raster legacy` |
| KGPU-M32-005 - Remove legacy device, rollback branch, and module include | `review` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-004] | `gpu-raster legacy` |
| KGPU-M32-006 - Final decommission validation + evidence bundle + PR | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `legacy-cleanup` | [KGPU-M32-005] | `gpu-raster legacy` |

### Per-family port-or-refuse tickets (Phase 2)

Created from the accepted `KGPU-M32-001` decision matrix
(`reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md`); one
ticket per `GpuRendererLegacyRouteFamily` row. All are `status: review`.

| Ticket | Status | familyId | decision | route_kind |
|---|---|---|---|
| KGPU-M32-010 | `review` | `material-paint` | port (SolidColor) / refuse (gradients + shader pipeline) | `GPUNative` |
| KGPU-M32-011 | `review` | `solid-rect-drawpaint` | port | `GPUNative` |
| KGPU-M32-012 | `review` | `rounded-rect-gradients` | port (solid uniform rrect) / refuse (gradients + non-uniform radii) | `GPUNative` |
| KGPU-M32-013 | `review` | `rect-rrect-stroke` | refuse | `RefuseDiagnostic` |
| KGPU-M32-014 | `review` | `device-scissor-simple-clips` | port (WideOpen/DeviceRect) / refuse (complex clips) | `GPUNative` |
| KGPU-M32-015 | `review` | `path-fill-stroke` | port (path fill) / refuse (path stroke) | `GPUNative` |
| KGPU-M32-016 | `review` | `images-bitmap-codecs-uploads` | refuse (dependency-gated) | `RefuseDiagnostic` |
| KGPU-M32-017 | `review` | `savelayer-destination-read-filters` | refuse (dependency-gated) | `RefuseDiagnostic` |
| KGPU-M32-018 | `review` | `text-glyphs` | port (A8 text) / refuse (color/SDF/emoji) | `GPUNative` |
| KGPU-M32-019 | `review` | `runtime-effects-color-blends` | port (SrcOver) / refuse (other blends, color filters, runtime effects, color management) | `GPUNative` |
| KGPU-M32-020 | `review` | `vertices-points-meshes` | refuse (dependency-gated) | `RefuseDiagnostic` |
| KGPU-M32-021 | `review` | `clear-discard-target-background` | port (trivial — surface init) | `GPUNative` |
| KGPU-M32-022 | `review` | `clear-discard-target-background` | route ownership assignment (replacement ticket) | `GPUNative` |

Release-blocking tickets in this milestone: KGPU-M32-002, KGPU-M32-004,
KGPU-M32-005, and KGPU-M32-006. KGPU-M32-001, KGPU-M32-003, and
KGPU-M32-022 are not release-blocking.

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
