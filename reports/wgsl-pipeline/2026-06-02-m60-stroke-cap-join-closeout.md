# M60 Stroke Cap/Join Closeout - 2026-06-02

Linear: `FOR-231`

## Decision

`m60-bounded-stroke-cap-join` remains `expected-unsupported`.

No readiness percentage is increased and no support threshold is weakened.
The exact adapter-backed score is still below the `99.95%` support floor, so
the dashboard row keeps the stable fallback reason
`coverage.stroke-cap-join-visual-parity-below-threshold`.

## Evidence Summary

| Lane | Status | Similarity | Threshold | Max channel delta |
|---|---|---:|---:|---:|
| CPU oracle vs reference | `pass` | `100.00%` | `100.00%` | `0` |
| WebGPU normal route | `expected-unsupported` | `0.00%` | `0.00%` | `255` |
| WebGPU diagnostic render | `diagnostic-only` | `89.60%` | `99.95%` | `39` |

The diagnostic render is intentionally behind
`kanvas.webgpu.strokeCapJoin.experimentalRender=true`. It is not a support
path and does not change `WebGpuCoveragePlanSelector` default behavior.

## Tolerance Profile

| Tolerance | Similarity |
|---:|---:|
| `0` | `89.60%` |
| `8` | `92.93%` |
| `16` | `99.37%` |
| `32` | `99.97%` |

The `32`-channel score crosses the support floor, but the exact score does
not. The remaining blocker is therefore recorded as
`color-space.target-blend-required`: the current WebGPU path blends in the
sRGB-coded intermediate and only converts to the DM Rec.2020 reference space
in the present pass, while the CPU F16 reference blends in the target color
space.

## Artifacts

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json`

## Non-Claims

- No stroke cap/join WebGPU support is claimed.
- No broad stroke, dash, hairline, cubic stroke, arbitrary Path AA, Ganesh, or
  Graphite support is claimed.
- No dynamic SkSL compilation, SkSL IR, or SkSL VM is introduced.
- No dashboard readiness rate is increased from diagnostic-only evidence.

## Follow-Up

`FOR-232` tracks the target-colorspace blending follow-up. It must define the
WebGPU working-space convention, BlendPlan impact, shader-family coverage,
present-pass policy, and CPU/GPU fixtures before switching any supported scene
to `fallbackReason=none`.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
rtk git diff --check
```
