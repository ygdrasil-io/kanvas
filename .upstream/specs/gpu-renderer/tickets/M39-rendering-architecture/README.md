# M39 - Rendering Architecture

**Status:** active (2026-06-28) — Wave C Track 4

## Goal

Promote MSAA resolve, instanced draw batching, subpass merging, and deferred
display list from TargetNative specs to accepted routes.

## Dependencies

Depends on M0-M1 review and product activation policy completion. All M39
tickets are `TargetNative` / `GPUNative` routes that assume the first-route
product activation path is operational and that M0 R6 boundary review gates
are satisfied.

## Exit Criteria

- [ ] `KGPU-M39-001` MSAA resolve passes with 4x PSNR evidence, 8x acceptance/
      refusal per adapter, and alpha-to-coverage coverage.
- [ ] `KGPU-M39-002` Instanced draw batching groups compatible packets and
      produces identical pixel output with telemetry stats.
- [ ] `KGPU-M39-003` Subpass merging fuses compatible producer-consumer passes
      with GPU evidence; incompatible cases produce stable refusal.
- [ ] `KGPU-M39-004` Deferred display list replays recorded commands with
      different CTM/clip; incompatible replays (format/capability/device change)
      produce stable refusal.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M39-001 - MSAA resolve](KGPU-M39-001-msaa-resolve.md) | `ready` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `state` | `KGPU-M1-001` | — |
| [KGPU-M39-002 - Instanced draw batching](KGPU-M39-002-instanced-draw-batching.md) | `ready` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `passes` | `KGPU-M1-001` | — |
| [KGPU-M39-003 - Subpass merging](KGPU-M39-003-subpass-merging.md) | `ready` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `passes` | `KGPU-M1-001` | — |
| [KGPU-M39-004 - Deferred display list](KGPU-M39-004-deferred-display-list.md) | `ready` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `recording` | `KGPU-M1-001` | — |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MSAA*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*InstancedBatch*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SubpassMerge*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DeferredDL*'
```

## Non-Claims

- This milestone does not activate product routing by being created.
- MSAA, instanced batching, subpass merging, and deferred display list
  activation does not imply tile-deferred rendering, multi-threaded recording,
  or Hi-Z occlusion culling (M40).
- A local adapter-backed pass does not by itself make any route release
  blocking.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
