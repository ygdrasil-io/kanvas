# GPU Renderer M7 Color Boundary

## Scope

Reviewed the GPU renderer ticket catalog, `tickets/STATUS.md`, M7 milestone
tickets, and the cited runtime-effect, color-management, blend, and
destination-read specs. KGPU-M7-004 is accepted contract/refusal evidence;
KGPU-M7-001 now has non-promoted descriptor-route evidence in review.

## Ticket Status

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M7-001 | `review` | Contract-gate evidence exists for registered `runtime.simple.color` descriptor lookup, uniform schema/packing, parser-validated wgsl4k reflection linkage, canonical 64-hex `sha256:` CPU oracle hash, material route dumps, material-key boundary, and stable refusals. Independent acceptance, adapter-backed execution/readback, product activation, arbitrary SkSL/WGSL input, children, blenders, filters, and live editing remain unpromoted. |
| KGPU-M7-002 | `blocked` | Depends on independent acceptance of KGPU-M7-001; source/child refusals must be anchored to the registered descriptor route boundary. |
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
- `RegisteredRuntimeEffectRouteTest` covers KGPU-M7-001 contract-gate evidence
  for `runtime.simple.color`, including descriptor dumps, registry snapshot,
  uniform schema/packing, wgsl4k reflection/schema linkage, canonical 64-hex
  `sha256:` CPU oracle hash, route dump, material-key boundary, unregistered
  descriptor refusal, descriptor-collision refusal, dynamic SkSL refusal,
  wrong-placement and missing-placement-opt-in refusal, WGSL
  reflection/descriptor mismatch refusal, and missing or non-canonical CPU
  oracle refusal. KGPU-M7-001 is in `review` pending independent acceptance.

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
- No product runtime-effect route activation.
- No arbitrary SkSL or dynamic shader compilation.
- No arbitrary WGSL descriptor input.
- No GPU-native color route.
- No broad color management.
- No ICC/CICP transform support.
- No HDR or gainmap support.
- No untagged-source policy decision.
- No platform color conversion as normative evidence.
