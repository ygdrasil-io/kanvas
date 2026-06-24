# M24 - GPU-Native Rendering

## Goal

Wire real GPU rendering for all families that currently use the
`RectOnlyOffscreenRenderer` solid-color fallback. M14-005 delivered a
GPU-native offscreen renderer for gradients and solids; the remaining families
(filters, bitmap shaders, text, runtime effects, strokes, saveLayer) still emit
diagnostic solid-color PNGs. This milestone executes the real WGSL material
shaders for those families so the scenes evidence proves the shaders run, not
just that the command infrastructure works.

## Dependencies

Depends on M12-M23 completion. Each ticket extends `GpuNativeOffscreenRenderer`
(KGPU-M14-005) and depends on the contract/stub milestone for its family.

## Exit Criteria

- [x] Blur and colorMatrix filters render via real WGSL (no solid-color PNGs)
- [x] Bitmap shader + all four tile modes render via real WGSL
- [x] Text A8 + SDF atlas glyphs render via real WGSL
- [x] Registered runtime effects render via real WGSL (parser-validated)
- [x] Stroke paths render via tessellated geometry with cap/join
- [x] SaveLayer + destination-read composite via real WGSL blend
- [x] All replaced scene PNGs are committed and show real shader output
- [x] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M24-001 - GPU-native blur + colorMatrix filter rendering](KGPU-M24-001-gpu-native-filter.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M19-001, KGPU-M19-002] | null |
| [KGPU-M24-002 - GPU-native bitmap shader + tile mode rendering](KGPU-M24-002-gpu-native-bitmap.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M17-001, KGPU-M17-002, KGPU-M17-004] | null |
| [KGPU-M24-003 - GPU-native text A8 + SDF atlas rendering](KGPU-M24-003-gpu-native-text.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M20-001, KGPU-M20-002, KGPU-M20-003] | null |
| [KGPU-M24-004 - GPU-native runtime effect rendering](KGPU-M24-004-gpu-native-runtime-effect.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M21-001, KGPU-M21-002, KGPU-M21-003] | null |
| [KGPU-M24-005 - GPU-native stroke rendering](KGPU-M24-005-gpu-native-stroke.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M16-001, KGPU-M16-002] | null |
| [KGPU-M24-006 - GPU-native saveLayer + destination-read compositing](KGPU-M24-006-gpu-native-savelayer.md) | `done` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `execution-renderer` | [KGPU-M14-005, KGPU-M18-001, KGPU-M18-003] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Non-Claims

- No product activation: these tickets render evidence, they do not flip routes ON
- No new family contracts: M24 only executes shaders for families already
  contracted in M16-M21
- No performance readiness claims (M23 owns performance gates)
- No dynamic SkSL compilation; runtime effects use registered Kanvas
  descriptors with parser-validated WGSL

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
