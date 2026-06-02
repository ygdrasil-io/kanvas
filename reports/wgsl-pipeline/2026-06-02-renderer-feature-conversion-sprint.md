# Renderer Feature Conversion Sprint Closeout - 2026-06-02

## Outcome

The sprint did not meet the minimum conversion threshold. It produced descriptor,
WGSL-parser, adapter, and scene evidence for attempted runtime-effect and Path AA
conversions, but no new row is promoted to supported in this closeout because
the adapter-backed scene parity gates did not reach the required support floor.

Readiness remains 67.75%. No PM readiness percentage is moved by this sprint.

## Attempted Rows

| Row | Family | Result | Evidence |
|---|---|---|---|
| `m60-bounded-stroke-cap-join` | Clip/RRect/Path AA | `expected-unsupported` | Stroke width/cap/join facts are captured, but WebGPU refuses before rendering with `coverage.stroke-cap-join-visual-parity-below-threshold`. |
| `m64-spiral-rt-descriptor-backed` | Registered runtime effects | `expected-unsupported` | Descriptor registry and WGSL parser evidence exist, but WebGPU execution remains unpromoted with `runtime-effect.spiral-visual-parity-below-threshold`. |
| `m64-linear-gradient-rt-descriptor-backed` | Registered runtime effects | `expected-unsupported` | Descriptor registry and WGSL parser evidence exist, and diagnostic WebGPU rendering reaches 99.22% strict parity (4064/4096 exact pixels, max delta 1; tolerance=1 parity 100.00%). This remains below the 99.95 support floor with mismatch pattern `x=43 y=32..63 green channel -1 vs CPU`. |

## Evidence

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-linear-gradient/`
- `reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json`
- `reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json`

## Validation

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorSceneCaptureTest --tests org.skia.gpu.webgpu.tools.WgslStrictValidationReportTest`
- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest --tests org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `rtk ./gradlew --no-daemon pipelineM64RegisteredRuntimeEffectsPack pipelineSceneDashboardGate`

## Non-Claims

- No dynamic SkSL compilation, SkSL IR, SkSL VM, Ganesh, or Graphite support is
  claimed.
- Runtime effects remain limited to registered Kotlin/WGSL descriptors.
- M60 stroke cap/join and the new runtime-effect WGSL rows remain explicit
  blockers until adapter-backed scene parity reaches the support threshold.
