# M38 - Runtime Effects V2

**Status:** active (2026-06-28) — Wave C Track 3

## Goal

Promote live parameter editing V2, extended effect kinds (Blender/ClipShader/Compute),
and dynamic shader graph assembly from TargetNative specs to accepted routes.

## Dependencies

Depends on M0-M1 as minimum evidence baseline. M38-003 depends on `wgsl4k`
supporting multi-fragment module assembly.

## Exit Criteria

- [ ] Live parameter editing V2 with dirty-tracking and preset round-trip accepted as GPUNative route.
- [ ] Blender, ClipShader, and Compute effect kinds registered with GPU evidence.
- [ ] Dynamic shader graph assembly with cycle detection and deterministic WGSL output.
- [ ] All refusal diagnostics stable and dumpable.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M38-001 - Live parameter editing V2](KGPU-M38-001-live-parameter-editing-v2.md) | `ready` | `P0` | `TargetNative` | `GPUNative` | `false` | `false` | `runtimeeffects` | `KGPU-M1-001` | `null` |
| [KGPU-M38-002 - Extended effect kinds](KGPU-M38-002-extended-effect-kinds.md) | `ready` | `P0` | `TargetNative` | `GPUNative` | `false` | `false` | `runtimeeffects` | `KGPU-M1-001` | `null` |
| [KGPU-M38-003 - Dynamic shader graph assembly](KGPU-M38-003-dynamic-shader-graph-assembly.md) | `blocked` | `P1` | `TargetNative` | `GPUNative` | `false` | `false` | `runtimeeffects` | `KGPU-M1-001` | `null` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LiveEdit*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*EffectKinds*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ShaderGraph*'
```

## Non-Claims

- This milestone does not activate product routing for runtime effects.
- Live parameter editing does not imply general-purpose shader authoring tools.
- Extended effect kinds do not include PixelLocal or tile-shading compute.
- Shader graph assembly does not cover general-purpose WGSL linker/combiner.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
