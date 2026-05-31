# M60 Stroke Cap/Join Path AA Promotion

Linear: `FOR-8`

Result: one bounded stroke cap/join Path AA row is promoted as generated
dashboard evidence, but remains `expected-unsupported` on WebGPU. The current
selector can report M60 path budgets such as verb, edge, dash, clip, and
bounds facts, but it does not carry `strokeWidth`, `strokeCap`, or
`strokeJoin` facts. FOR-8 therefore records the scene as a refusal instead of
claiming GPU support.

## Added Row

| Row | Status | Base evidence | GPU route | Fallback |
|---|---|---|---|---|
| `m60-bounded-stroke-cap-join` | `expected-unsupported` | `StrokeCapJoinSceneCaptureTest` CPU-oracle artifacts | `webgpu.coverage.stroke-cap-join.expected-unsupported` | `coverage.stroke-cap-join-selector-diagnostics-unavailable` |

The row is generated from
`reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json`
by `pipelineM60NestedClipPathAaPromotionPack`. The source scene is a small
LinePath/LineClosePath-derived CPU oracle: three AA stroked open polylines
with stroke width 10 and the cap/join matrix `butt/bevel`, `round/round`, and
`square/bevel`.

## M60 Diagnostics

The generated route JSON includes:

- `pathVerbCount=9`, under the M60 budget of 96;
- `edgeCount=18`, under the current WebGPU Path AA budget of 256;
- `strokeWidth=10.0`, inside the M60 0.5 px to 64 px range;
- `strokeCaps=[butt, round, square]`;
- `strokeJoins=[bevel, round, bevel]`;
- `dashIntervalCount=0`, under the M60 budget of 8;
- `deviceBounds=(0,0,192,128)`, under the M60 2048 x 2048 bound.

These values are CPU scene-contract facts, not a WebGPU selector route dump.
Because `WebGpuCoveragePlanSelector.WebGpuPathCoverageFacts` does not yet
emit stroke width/cap/join facts, the row uses a stable expected-unsupported
reason and does not set `fallbackReason=none`.

## Artifacts

The materialized row carries:

- `skia.png`
- `cpu.png`
- `cpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `stats.json`

No GPU image is required for this row because the WebGPU lane is an explicit
refusal.

## Non-Claims

FOR-8 does not claim stroke cap/join WebGPU support, broad stroke support,
hairline support, dash support, cubic stroke parity, or that
`WebGpuCoveragePlanSelector` carries stroke style facts.

## Validation

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon pipelineM60NestedClipPathAaPromotionPack pipelineSceneDashboard pipelineSceneDashboardGate
rtk git diff --check
```
