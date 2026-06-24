# M13 - RRect + LinearGradient + Scissor Execution

## Goal

Deliver FillRRect, LinearGradient, and Scissor clip execution with controlled product activation. First wave 1 milestone after M12 dependencies.

## Dependencies

Depends on M12 completion (wgsl4k evolution gate KGPU-M12-010). Parallel with M15 and M17 in wave 1.

## Exit Criteria

- [x] FillRRect renders with analytic AA on GPU
- [x] LinearGradient material executes with correct color interpolation
- [x] Scissor clip correctly constrains rendering
- [x] All three routes are product-activated with rollback
- [x] Scene evidence dumps are produced

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M13-001 - Add FillRRect execution: analytic rrect coverage WGSL + GPU command stream](KGPU-M13-001-fill-rrect-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `true` | `true` | `geometry-passes` | [KGPU-M12-010] | null |
| [KGPU-M13-002 - Add LinearGradient material execution: WGSL snippet + uniform layout + payload](KGPU-M13-002-linear-gradient-material.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `true` | `true` | `materials-wgsl` | [KGPU-M12-010] | null |
| [KGPU-M13-003 - Add scissor clip execution: device-rect clip -> WebGPU setScissor + uniform](KGPU-M13-003-scissor-clip-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `true` | `true` | `clips-passes` | [KGPU-M12-010] | null |
| [KGPU-M13-004 - Activate M13 routes: FillRRect + LinearGradient + Scissor default ON with rollback](KGPU-M13-004-route-activation.md) | `done` | `P0` | `PolicyGated` | `GPUNative` | `true` | `true` | `product-validation` | [KGPU-M13-001, KGPU-M13-002, KGPU-M13-003] | legacy drawRect/drawRRect/drawPaint |
| [KGPU-M13-005 - Add gpu-renderer-scenes evidence: rrect-card, gradient-swatch, clipped-stack](KGPU-M13-005-scenes-evidence.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `true` | `true` | `scenes-evidence` | [KGPU-M13-001, KGPU-M13-002, KGPU-M13-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FillRRect*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LinearGradient*'
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests '*M13Scene*'
```

## Non-Claims

- No stroked rrects
- No TwoPointConical gradient
- No complex clip stacks
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
