# M37 Path AA Breadth Strategy Closeout

Date: 2026-05-28
Linear: GRA-180
Milestone: M37 -- Path AA Breadth Strategy

## Outcome

M37 is closed as a diagnostic-breadth improvement, not a new rendered WebGPU Path AA support claim.

The selected `StrokeRectGM` / `StrokeCircleGM` family was promoted from the generic edge-budget refusal into a narrower stable fallback diagnostic:

```text
coverage.stroke-outline-edge-count-exceeded
```

The global WebGPU AA edge budget remains unchanged at 256. Broad Path AA rows continue to refuse through:

```text
coverage.edge-count-exceeded
```

## Before / After Counts

Baseline from GRA-173 / GRA-177:

| Classification | Rows |
|---|---:|
| `coverage.edge-count-exceeded` | 50 |
| `coverage.stroke-outline-edge-count-exceeded` | 0 |
| total expected unsupported Path AA rows | 50 |
| unexpected exceptions | 0 |

Closeout inventory from GRA-180 validation:

| Classification | Rows |
|---|---:|
| `coverage.edge-count-exceeded` | 46 |
| `coverage.stroke-outline-edge-count-exceeded` | 4 |
| total expected unsupported Path AA rows | 50 |
| unexpected exceptions | 0 |

Overall inventory categories from `gpu-inventory-failure-classification.json`:

| Category | Rows |
|---|---:|
| `expected-unsupported-diagnostic` | 50 |
| `unsupported-image-filter` | 2 |
| `adapter-skip` | 48 |
| `adapter-missing` | 0 |
| `similarity-regression` | 0 |
| `unexpected-exception` | 0 |

## Rows Moved To Narrow Diagnostic

These four selected rows now classify as `expected-unsupported-diagnostic` with reason `coverage.stroke-outline-edge-count-exceeded`:

- `org.skia.gpu.webgpu.StrokeCircleWebGpuTest#StrokeCircleGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.StrokeRectWebGpuTest#StrokeRectGM renders close to reference PNG on the GPU backend()`
- `org.skia.gpu.webgpu.crossbackend.StrokeCircleCrossBackendTest#StrokeCircleGM matches reference on raster and GPU backends()`
- `org.skia.gpu.webgpu.crossbackend.StrokeRectCrossBackendTest#StrokeRectGM matches reference on raster and GPU backends()`

This is intentionally a bounded fallback lane. It prevents the selected stroke-outline family from being hidden inside the broad edge-budget class while avoiding an unsupported claim that WebGPU renders those scenes correctly.

## Dashboard Evidence

The scene dashboard includes two M37 Path AA rows:

| Scene row | Status | GPU route / fallback |
|---|---|---|
| `path-aa-stroke-outline-fallback` | expected unsupported | `coverage.stroke-outline-edge-count-exceeded` |
| `path-aa-edge-budget-boundary` | expected unsupported | `coverage.edge-count-exceeded` |

Evidence files:

- Dashboard source: `reports/wgsl-pipeline/scenes/data/scenes.json`
- Dashboard export: `build/reports/wgsl-pipeline-scenes/index.html`
- Scene artifacts: `reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-outline-fallback/`
- Boundary artifacts: `reports/wgsl-pipeline/scenes/artifacts/path-aa-edge-budget-boundary/`

## Remaining Path AA Backlog

The remaining 46 `coverage.edge-count-exceeded` rows should stay expected unsupported until scoped by narrower follow-up tickets. Recommended order:

| Family | Remaining signal | Recommendation |
|---|---:|---|
| Arc stroke/hairline | 9 rows from GRA-173 | Next after primitive strokes; requires curve subdivision and cap/hairline acceptance bounds. |
| General stroke/dash | 13 rows from GRA-173 | Split dash, cap, join, and overstroke behavior before any support claim. |
| Fill/convex/path packs | 19 rows from GRA-173 | Keep as broad conformance signal; choose one bounded fixture before implementation. |
| Filter/shader over path | 4 rows from GRA-173 | Defer until base Path AA coverage route is stable. |
| Benchmark stress | 1 row from GRA-173 | Keep inventory-only until a rendered route exists; not a support claim. |

No undocumented Path AA failure class was introduced. The closeout inventory contains only the two stable Path AA fallback reasons above.

## Validation

Commands run for closeout:

```text
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk git diff --check
```

Inventory command result: expected non-zero because the inventory suite intentionally contains classified unsupported rows. It generated:

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`

Dashboard result: passed and wrote `build/reports/wgsl-pipeline-scenes/index.html`.
