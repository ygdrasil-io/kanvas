# M1 - First Route Product Activation

## Goal

Decide whether the reviewed solid `FillRect` route can move from
evidence-only diagnostics to a controlled product activation path, and define
the rollback policy if activation is accepted.

## Dependencies

Depends on M0 review completion and an explicit product/release decision. The
opt-in adapter-backed lane may inform this milestone, but must not silently
become a release-blocking dependency.

The 2026-06-14 M1 promotion policy decision accepts launching the controlled
promotion path in the current state. Independent review is sufficient for this
policy gate for now, and must be repeated at each future milestone boundary.

## Exit Criteria

- [x] Product activation policy is explicitly accepted or rejected.
- [ ] If accepted, a feature flag and rollback path keep legacy behavior
      available.
- [ ] Root PM packaging states whether adapter-backed evidence is required.
- [ ] No readiness movement is claimed without a reviewed activation decision.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M1-001 - Decide first-route product activation policy](KGPU-M1-001-decide-first-route-product-activation-policy.md) | `done` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `product-validation` | `KGPU-M0-007` | `pipelinePmBundle` |
| [KGPU-M1-002 - Promote root PM bundle to activation candidate](KGPU-M1-002-promote-root-pm-bundle-to-activation-candidate.md) | `ready` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `validation-pm` | `KGPU-M1-001` | `pipelinePmBundle` |
| [KGPU-M1-003 - Add controlled first-route product flag](KGPU-M1-003-add-controlled-first-route-product-flag.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `gpu-raster-adapter` | `KGPU-M1-001`, `KGPU-M1-002` | `legacy drawRect` |
| [KGPU-M1-004 - Add first-route rollback and parity validation](KGPU-M1-004-add-first-route-rollback-and-parity-validation.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `validation-adapter` | `KGPU-M1-003` | `legacy drawRect` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
```

## Non-Claims

- This milestone does not activate product routing by being created.
- `FillRect` activation does not imply rrect, gradient, path, image, text,
  filter, runtime-effect, or broad Skia parity.
- A local adapter-backed pass does not by itself make the route release
  blocking.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
