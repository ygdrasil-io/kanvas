# M33 - Geometry Hardening

**Status:** active (2026-06-28) — Wave A Track 2

## Goal

Promote GPU compute tessellation, advanced stroke expansion (complex dash, path effects), and perspective transform acceptance from TargetNative specs to accepted GPUNative routes with evidence.

## Dependencies

Depends on M0 (R0-R6 boundary review) and M1 (first-route product activation) for baseline contract shapes, route taxonomy, and diagnostics. M1 solid FillRect evidence is the minimum proven route pattern.

## Exit Criteria

- [ ] GPU compute tessellation produces accepted GPUNative path fill and stroke routes with CPU oracle parity.
- [ ] Advanced stroke expansion (complex dash, path-effect chain) is accepted or refused with stable diagnostics.
- [ ] Perspective transform acceptance is proven for rect/rrect geometry with solid color material; path and text remain refused.
- [ ] All accepted routes have WGSL validation through wgsl4k, pipeline key evidence, and route diagnostics.
- [ ] No CPU-rendered texture fallback for any promoted route.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M33-001 - GPU compute tessellation](KGPU-M33-001-gpu-compute-tessellation.md) | `ready` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry` | `KGPU-M1-001` | `legacy path fill` |
| [KGPU-M33-002 - Advanced stroke expansion](KGPU-M33-002-advanced-stroke-path-effects.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry` | `KGPU-M1-001` | `legacy stroke` |
| [KGPU-M33-003 - Perspective transform acceptance](KGPU-M33-003-perspective-transform-acceptance.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `coordinates` | `KGPU-M1-001` | `legacy drawRect` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
```

## Non-Claims

- This milestone does not activate product routing by being created.
- Compute tessellation does not imply GPU-native path tessellation for all path types or stroke styles.
- Perspective acceptance does not extend to text, image, filter, or layer routes.
- No readiness movement is claimed without reviewed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and ../STATUS.md in the same change.
