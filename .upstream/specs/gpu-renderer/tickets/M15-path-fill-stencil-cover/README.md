# M15 - Path Fill + Stencil-Cover

## Goal

Deliver path tessellation, stencil-cover execution, and convex fan paths with controlled product activation for arbitrary path fills.

## Dependencies

Depends on M12 (wgsl4k gate KGPU-M12-010). Wave 1 milestone, parallel with M13 and M17.

## Exit Criteria

- [x] Path tessellation flattens and triangulates paths within 256-edge budget
- [x] Stencil-cover correctly fills non-convex paths
- [x] Convex fan correctly fills convex paths in single pass
- [x] Path fill routes are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M15-001 - Add path tessellation: flatten + fan triangulation -> WebGPU vertex buffer](KGPU-M15-001-path-tessellation.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M12-010] | null |
| [KGPU-M15-002 - Add stencil-cover execution: two-pass stencil write + cover resolve with WGSL](KGPU-M15-002-stencil-cover-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M15-001] | null |
| [KGPU-M15-003 - Add convex fan execution: single-pass analytic AA with triangle list](KGPU-M15-003-convex-fan-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M15-001] | null |
| [KGPU-M15-004 - Activate M15 routes: Path fill native + stencil-cover default ON with rollback](KGPU-M15-004-route-activation.md) | `done` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M15-001, KGPU-M15-002, KGPU-M15-003] | legacy drawPath |
| [KGPU-M15-005 - Add gpu-renderer-scenes evidence: path-fill-stencil, convex-fan-mesh](KGPU-M15-005-scenes-evidence.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M15-001, KGPU-M15-002, KGPU-M15-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PathTessellat*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*StencilCover*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ConvexFan*'
```

## Non-Claims

- 256-edge budget; paths exceeding budget refused
- No GPU compute tessellation
- No path atlas
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
