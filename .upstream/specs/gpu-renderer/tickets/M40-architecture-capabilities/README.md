# M40 - Architecture Capabilities

**Status:** active (2026-06-28) — Wave D

## Goal

Promote tile-deferred rendering, multi-threaded recording, and hi-z occlusion culling from TargetNative specs to accepted routes.

## Dependencies

Depends on M0 (R0-R6 boundary review) and M1 (first-route product activation) for baseline contract shapes, route taxonomy, and diagnostics. Full R0-R6 completion (command to submission chain proven) is required. M40-002 depends on M40-001 for tile-parallel recording strategy. M40-003 depends on M40-001 for per-tile pyramid interaction.

## Exit Criteria

- [ ] Tile-deferred rendering subdivides large targets into fixed-size tiles with bounded intermediate memory, empty-tile culling, and pixel-exact composite parity against single-pass.
- [ ] Multi-threaded recording produces deterministic merged fragments with thread-bound arenas and validated ordering tokens.
- [ ] Hi-Z occlusion culling eliminates 40%+ of occluded draws with zero false positives and enforced memory budget.
- [ ] All accepted routes have WGSL validation through wgsl4k, pipeline key evidence, and route diagnostics.
- [ ] No CPU-rendered texture fallback for any promoted route.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M40-001 - Tile-deferred rendering](KGPU-M40-001-tile-deferred-rendering.md) | `review` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `passes` | `KGPU-M1-001` | `null` |
| [KGPU-M40-002 - Multi-threaded recording](KGPU-M40-002-multithreaded-recording.md) | `review` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `recording` | `KGPU-M1-001`, `KGPU-M40-001` | `null` |
| [KGPU-M40-003 - Hi-Z occlusion culling](KGPU-M40-003-hi-z-occlusion-culling.md) | `review` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `analysis` | `KGPU-M1-001`, `KGPU-M40-001` | `null` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TileDeferred*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MultiThreaded*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HiZ*'
```

## Non-Claims

- This milestone does not activate product routing by being created.
- Tile-deferred rendering does not imply GPU-native tiled rendering for non-rect geometry types.
- Multi-threaded recording does not imply thread-safe unguarded GPUResource access or concurrent GPUExecutionContext mutation.
- Hi-Z occlusion does not imply GPU-native occlusion for translucent draws or depth-unavailable targets.
- No readiness movement is claimed without reviewed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
