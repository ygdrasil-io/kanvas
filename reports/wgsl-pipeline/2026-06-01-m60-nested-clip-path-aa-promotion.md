# M60 Nested Clip Path AA Promotion

Linear: `FOR-9`

Result: one bounded nested rect/rrect clip row is promoted as generated
dashboard evidence, but remains `expected-unsupported` on WebGPU until route
diagnostics and visual parity are strong enough for a support claim. Broad
complex-clip and edge-budget refusals remain visible.

## Added Row

| Row | Status | Base evidence | GPU route | Fallback |
|---|---|---|---|---|
| `m60-bounded-nested-rrect-clip` | `expected-unsupported` | `NestedClipSceneCaptureTest` row-specific artifacts | `webgpu.coverage.nested-rrect-clip.expected-unsupported` | `coverage.nested-clip-visual-parity-below-threshold` |

The row is generated from
`reports/wgsl-pipeline/scenes/generated/m60-nested-clip-path-aa-promotion.json`
by `pipelineM60NestedClipPathAaPromotionPack`. The source render evidence is
captured by `org.skia.gpu.webgpu.NestedClipSceneCaptureTest` from
`BlurredClippedCircleGM`, which uses a bounded clip stack of `clipRect`,
`clipRect`, and `clipRRect(kDifference)` before drawing the blurred rrect.
The generated row adds M60 scene-budget diagnostics for:

- `clipDepth=3`, under the M60 budget of 4;
- `edgeCount=72`, under the current WebGPU Path AA budget of 256;
- `deviceBounds=(0,0,1164,802)`, under the M60 2048 x 2048 bound.

These values are explicit scene-contract facts, not a WebGPU selector route
dump. FOR-9 therefore does not claim `fallbackReason=none`.

The captured GPU image reaches 71.22% similarity against the Skia reference,
below the 99.95% support floor used for this row. The row stays visible as PM
evidence and as the next target for route-diagnostics and visual-diff burn-down.

## Artifacts

The materialized row carries:

- `skia.png`
- `cpu.png`
- `gpu.png`
- `cpu-diff.png`
- `gpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `stats.json`

## Boundary Policy

These existing rows remain expected unsupported:

- `path-aa-stroke-outline-fallback`
- `path-aa-edge-budget-boundary`
- `path-aa-convexpaths-edge-budget`
- `path-aa-dashing-edge-budget`
- `m52-closed-capped-hairlines-edge-budget`
- `m53-complexclip-boundary-refusal`
- `m54-dash-circle-boundary`

## Non-Claims

FOR-9 does not claim nested clip WebGPU support, arbitrary nested clip stacks,
complex clips, shader clips, inverse Path AA clips, perspective clips, large
clipped paths, or a higher WebGPU edge budget.

## Validation

```text
rtk ./gradlew --no-daemon pipelineM60NestedClipPathAaPromotionPack pipelineSceneDashboardGate
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk git diff --check
```
