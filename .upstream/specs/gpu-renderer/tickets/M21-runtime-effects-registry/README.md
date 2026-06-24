# M21 - Runtime Effects Registry

## Goal

Deliver the runtime effects registry with SimpleRT, LinearGradientRT, and SpiralRT, an execution lane, and controlled product activation with refusal for unregistered effects.

## Dependencies

Depends on M12 (wgsl4k gate KGPU-M12-010). Wave 3 milestone.

## Exit Criteria

- [ ] SimpleRT, LinearGradientRT, and SpiralRT registered with validated WGSL
- [ ] Execution lane performs descriptor lookup, WGSL injection, and GPU submit
- [ ] Registered effects render correctly on GPU
- [ ] Unregistered effects refused with stable diagnostic
- [ ] Runtime effects routes are product-activated with rollback

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M21-001 - Register SimpleRT: Kotlin CPU oracle + parser-validated WGSL + reflected uniforms](KGPU-M21-001-simple-rt-registration.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `runtime-effects` | [KGPU-M12-010] | null |
| [KGPU-M21-002 - Register LinearGradientRT + SpiralRT: same pattern, validated WGSL](KGPU-M21-002-linear-spiral-rt-registration.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `runtime-effects` | [KGPU-M21-001] | null |
| [KGPU-M21-003 - Add RuntimeEffect execution lane: descriptor lookup -> WGSL snippet -> GPU submit](KGPU-M21-003-runtime-effect-execution.md) | `done` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `runtime-effects` | [KGPU-M21-001, KGPU-M21-002] | null |
| [KGPU-M21-004 - Activate M21 routes: registered effects default ON, unregistered -> refusal](KGPU-M21-004-route-activation.md) | `done` | `P0` | `PolicyGated` | `GPUNative` | `false` | `true` | `product-validation` | [KGPU-M21-001, KGPU-M21-002, KGPU-M21-003] | legacy drawRuntimeEffect |
| [KGPU-M21-005 - Add gpu-renderer-scenes evidence: runtime-effect-uniform, runtime-effect-child](KGPU-M21-005-scenes-evidence.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M21-001, KGPU-M21-002, KGPU-M21-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SimpleRT*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RuntimeEffect*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*RuntimeEffectExec*'
```

## Non-Claims

- No arbitrary SkSL compilation
- Existing hand-written WGSL only (SimpleRT, SpiralRT, LinearGradientRT)
- No user-defined effect registration
- No performance readiness claims

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
