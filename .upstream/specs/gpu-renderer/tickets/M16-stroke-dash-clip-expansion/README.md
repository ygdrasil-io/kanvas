# M16 - Stroke + Dash + Clip Expansion

## Goal

Deliver stroke expansion, dash path effects, and bounded clip stack support with controlled product activation. Completes Wave 2 geometry coverage.

## Dependencies

Depends on M13 scissor clip (KGPU-M13-003), M15 path fill and stencil-cover (KGPU-M15-001, KGPU-M15-002). Wave 2 milestone.

## Exit Criteria

- [ ] Stroke expansion produces correct join/cap geometry
- [ ] Dash path effect decomposes paths correctly
- [ ] Bounded clip stacks support rrect and path clips
- [ ] All routes are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M16-001 - Add stroke expansion: stroke path -> fillable contour with join/cap geometry](KGPU-M16-001-stroke-expansion.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M15-001] | null |
| [KGPU-M16-002 - Add dash path effect: dash interval decomposition -> stroke sub-paths](KGPU-M16-002-dash-path-effect.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | [KGPU-M16-001] | null |
| [KGPU-M16-003 - Add bounded clip expansion: rrect/path clip stacks beyond simple scissor](KGPU-M16-003-bounded-clip-expansion.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `clips-passes` | [KGPU-M13-003, KGPU-M15-002] | null |
| [KGPU-M16-004 - Activate M16 routes: Stroke + Dash + bounded clips default ON with rollback](KGPU-M16-004-route-activation.md) | `proposed` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M16-001, KGPU-M16-002, KGPU-M16-003] | legacy drawPath stroke |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Stroke*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Dash*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ClipStack*'
```

## Non-Claims

- 128-edge stroke budget
- No arbitrary path effects beyond dash
- No unbounded or inverse-fill clips
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
