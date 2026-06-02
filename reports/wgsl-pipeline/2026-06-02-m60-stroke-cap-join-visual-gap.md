# M60 Stroke Cap Join Visual Gap Diagnostic - 2026-06-02

## Scope

This diagnostic covers only `m60-bounded-stroke-cap-join`.

The normal WebGPU route remains refused:

- route: `webgpu.coverage.refuse`
- fallback reason: `coverage.stroke-cap-join-visual-parity-below-threshold`
- support status: `expected-unsupported`

The diagnostic render uses the existing contour-fill path behind the
test-only property `kanvas.webgpu.strokeCapJoin.experimentalRender=true`.
It does not claim support and does not change the default selector behavior.

## Result

The experimental WebGPU render reaches `89.60%`, below the required `99.95%`
support threshold.

| Region | Case | Similarity | Max channel delta |
|---|---|---:|---:|
| `butt-bevel` | butt cap + bevel join | `88.05%` | `38` |
| `round-round` | round cap + round join | `85.69%` | `39` |
| `square-bevel` | square cap + bevel join | `92.32%` | `22` |

Dominant mismatch by matching-pixel ratio: `round-round`.

The shader-side 4x4 coverage update reduced the largest channel delta from
`98` to `39`, but exact matching improved only from `89.49%` to `89.60%`.
The tolerance profile now records:

| Tolerance | Similarity |
|---:|---:|
| `0` | `89.60%` |
| `8` | `92.93%` |
| `16` | `99.37%` |
| `32` | `99.97%` |

## Evidence

- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/gpu-experimental-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/experimental-gpu-diagnostic.json`
- `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/stats.json`

## Interpretation

The current WebGPU contour-fill path is close enough to be useful for
diagnosis, but it is not a support path. The remaining exact mismatch is not
only stroke geometry: the dominant repeated deltas include neutral AA pixels
such as CPU `128` vs WebGPU `115`, which matches the current WebGPU model of
blending in sRGB-coded intermediate values and applying the sRGB-to-Rec.2020
present transform after blending. The CPU `RasterSinkF16` reference blends in
the DM reference color space. Promoting the row to the exact `99.95%` threshold
therefore requires a follow-up target-colorspace blending slice, not just more
cap/join tessellation.

## Validation

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
