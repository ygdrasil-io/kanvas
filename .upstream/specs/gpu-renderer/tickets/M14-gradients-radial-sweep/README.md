# M14 - RadialGradient + SweepGradient

## Goal

Deliver RadialGradient and SweepGradient WGSL shaders with controlled product activation. Completes gradient family coverage alongside M13 LinearGradient.

## Dependencies

Depends on M13 gradient material execution (KGPU-M13-002). Wave 2 milestone.

## Exit Criteria

- [ ] RadialGradient renders with correct distance-based interpolation
- [ ] SweepGradient renders with correct angle-based interpolation
- [ ] All three tile modes work for both gradient types
- [ ] Both gradients are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M14-001 - Add RadialGradient WGSL: distance-from-center math + tile mode](KGPU-M14-001-radial-gradient.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | [KGPU-M13-002] | null |
| [KGPU-M14-002 - Add SweepGradient WGSL: atan2 angle interpolation + tile mode](KGPU-M14-002-sweep-gradient.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `materials-wgsl` | [KGPU-M13-002] | null |
| [KGPU-M14-003 - Activate M14 routes: Radial + Sweep gradients default ON with rollback](KGPU-M14-003-route-activation.md) | `proposed` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M14-001, KGPU-M14-002] | legacy drawPaint |
| [KGPU-M14-004 - Add gpu-renderer-scenes evidence: radial-swatch, sweep-disk](KGPU-M14-004-scenes-evidence.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M14-001, KGPU-M14-002] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RadialGradient*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SweepGradient*'
```

## Non-Claims

- No TwoPointConical gradient (deferred)
- No gradient dithering
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
