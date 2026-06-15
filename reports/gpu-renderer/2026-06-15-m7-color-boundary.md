# GPU Renderer M7 Color Boundary

## Scope

Reviewed the GPU renderer ticket catalog, `tickets/STATUS.md`, M7 milestone
tickets, and the cited runtime-effect, color-management, blend, and
destination-read specs. This wave takes only KGPU-M7-004 as actionably ready.

## Ticket Status

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M7-001 | `proposed` | Registered runtime-effect descriptor with Kotlin/CPU oracle, complete parser-validated WGSL/reflection through `wgsl4k`, route integration, adapter-backed execution/readback evidence, and unregistered-descriptor refusals. |
| KGPU-M7-002 | `blocked` | Depends on KGPU-M7-001; source/child refusals must be anchored to the registered descriptor route boundary. |
| KGPU-M7-003 | `blocked` | Depends on KGPU-M5-002 and native destination-read target-copy/intermediate evidence. |
| KGPU-M7-004 | `done` | Independent review accepted the bounded SDR color boundary evidence with no findings. |

## Evidence

- Added `GPUSDRColorBoundaryPlanner`, `GPUSDRColorBoundaryRequest`, and
  `GPUSDRColorBoundaryReport`.
- Finite sRGB input produces deterministic SDR value, working-space, store, and
  behavior-key-fact dumps.
- Behavior key facts exclude source provenance and profile identity.
- HDR, gainmap, ICC v4, CICP, untagged, and extended-range cases emit terminal
  `unsupported.color.*` diagnostics.
- `GPUSDRColorBoundaryReport.promotable` is always `false`; the evidence is
  contract/refusal-only.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.color.SDRColorBoundaryTest
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.color.SDRColorBoundaryTest --tests org.graphiks.kanvas.gpu.renderer.GPURendererLayoutSurfaceTest
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:check
rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests '*Runtime*' --tests '*Blend*' --tests '*Color*'
rtk git diff --check
```

Result: passed after RED failure on missing M7-004 API. The `gpu-raster`
Runtime/Blend/Color lane is context validation only; this wave does not change
`gpu-raster` routing or claim GPU-native color support.

## Review

Independent review `019ec850-9390-7240-9313-1f9af4b9a77d` found no P0/P1/P2
issues and accepted KGPU-M7-004 for `done` as contract/refusal-only evidence.
Residual gates remain broad color management, HDR/gainmap/profile conversion,
untagged policy, platform conversion, and any GPU-native color route.

## Non-Claims

- No product activation.
- No GPU-native color route.
- No broad color management.
- No ICC/CICP transform support.
- No HDR or gainmap support.
- No untagged-source policy decision.
- No platform color conversion as normative evidence.
