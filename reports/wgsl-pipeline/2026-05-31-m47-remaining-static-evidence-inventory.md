# M47 Remaining Static Evidence Inventory

Date: 2026-05-31
Issue: GRA-273
Parent epic: GRA-272

## Outcome

M47 starts from the post-M46 dashboard state and does not change any scene
support claim in this inventory ticket. The purpose of this report is to lock
which remaining static rows are eligible for conversion and which rows must stay
visible as expected-unsupported policy evidence.

## Baseline Counts

| Signal | Count |
|---|---:|
| Scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| Generated rows | 8 |
| Static rows | 5 |

This matches the M46 closeout and sprint review baseline. No row was added,
removed, relabelled, or converted by GRA-273.

## Static Row Inventory

| Row | Status | Reference | CPU route | GPU route / fallback | Threshold | Tags |
|---|---|---|---|---|---:|---|
| `runtime-effect-simple` | `pass` | `test-oracle` | `cpu.runtime-effect.descriptor.simple_rt` | `webgpu.runtime-effect.descriptor.simple_rt`, `fallbackReason=none` | 99.95 | `feature.runtime-effect`, `feature.coverage.analytic-rect`, `risk.none` |
| `clip-rect-difference` | `pass` | `skia-upstream` | `cpu.coverage.clip-rect-difference` | `webgpu.coverage.clip-difference.analytic-rrect-mask`, `fallbackReason=none` | 80.0 | `feature.clip`, `feature.coverage.clip`, `risk.none` |
| `bitmap-shader-local-matrix` | `pass` | `test-oracle` | `cpu.shader.bitmap.local-matrix` | `webgpu.shader.bitmap.local-matrix`, `fallbackReason=none` | 99.95 | `feature.image.bitmap`, `feature.shader.local-matrix`, `risk.none` |
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | `cpu-oracle` | `cpu.path-coverage.stroke-outline-oracle` | `webgpu.coverage.refuse`, `coverage.stroke-outline-edge-count-exceeded` | 95.91 | `feature.path-aa`, `feature.stroke`, `risk.expected-unsupported`, `risk.edge-budget` |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | `cpu-oracle` | `cpu.path-coverage.raster-oracle` | `webgpu.coverage.refuse`, `coverage.edge-count-exceeded` | 99.85 | `feature.path-aa`, `feature.coverage.aa`, `risk.expected-unsupported`, `risk.edge-budget` |

All five rows currently carry `source.static` and `maturity.static-evidence`.
Only the three `pass` rows below are support-evidence conversion candidates for
M47.

## M47 Target Conversions

| Row | Ticket | Proposed generating command or owner | Treatment |
|---|---|---|---|
| `runtime-effect-simple` | GRA-274 | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest` | Convert to generated evidence only if the registered runtime-effect descriptor boundary, `SimpleRT` route, `fallbackReason=none`, and 99.95 threshold remain intact. |
| `clip-rect-difference` | GRA-275 | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest` | Convert to generated evidence with explicit clip-lowering diagnostics and no broad clip-stack support claim. |
| `bitmap-shader-local-matrix` | GRA-276 | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.BitmapShaderRotatedTest`; preserve M43 measured CPU/GPU payload links if retained in the row. | Convert to generated evidence only if the local-matrix shader route, affine-inverse pipeline key, thresholds, and reporting-only performance payload semantics remain intact. |

## Expected Unsupported Policy Rows

| Row | Ticket | Policy treatment |
|---|---|---|
| `path-aa-stroke-outline-fallback` | GRA-277 | Keep visible as `expected-unsupported` unless a future implementation proves stroke-outline support within the WebGPU edge-budget policy. It is not a support conversion target in M47. |
| `path-aa-edge-budget-boundary` | GRA-277 | Keep visible as `expected-unsupported` with stable `coverage.edge-count-exceeded`. It is not a support conversion target in M47. |

These rows can be converted only to generated refusal evidence if GRA-277
chooses that route. They must not be hidden, relabelled as pass, or removed to
improve generated/static counters.

## Blocker Assessment

No blocker prevents starting the three M47 pass-row conversions. The known
boundaries are semantic rather than blocking:

- `runtime-effect-simple` must preserve the compatibility facade and registered
  Kotlin/WGSL implementation boundary; do not imply arbitrary SkSL support.
- `clip-rect-difference` must preserve its selected clip-difference route; do
  not imply full clip-stack breadth.
- `bitmap-shader-local-matrix` must preserve bitmap shader local-matrix routing
  and any measured-performance links; do not use it to introduce performance
  gates.
- The two Path AA rows remain expected unsupported policy evidence unless GRA-277
  explicitly creates generated refusal evidence with the same stable diagnostics.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Both commands must pass before closing GRA-273.
