# M44-D Path AA inventory before/after

GRA-214 publishes the M44 Path AA inventory delta after promoting the selected
primitive-stroke family through `webgpu.coverage.path-aa-stroke-primitive`.

## Inventory summary

`rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` is expected to exit
non-zero while remaining unsupported inventory rows fail with stable diagnostic
reasons. The command produced
`gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`
and `.md`.

| Signal | Before M44 promotion | After GRA-212/GRA-213 | Delta |
| --- | ---: | ---: | ---: |
| `coverage.edge-count-exceeded` | 50 | 46 | -4 |
| `coverage.stroke-outline-edge-count-exceeded` | 0 | 0 | 0 |
| `expected-unsupported-diagnostic` | 50 | 46 | -4 |
| `adapter-skip` | 48 | 48 | 0 |
| `unexpected-exception` | 0 | 0 | 0 |
| `similarity-regression` | 0 | 0 | 0 |

The four-row reduction is exactly the selected M44 primitive-stroke family:

- `StrokeRectWebGpuTest`
- `StrokeCircleWebGpuTest`
- `StrokeRectCrossBackendTest`
- `StrokeCircleCrossBackendTest`

Those rows now render through the bounded route and are no longer counted as
unsupported Path AA edge-budget rows.

## Rendered evidence

GRA-212 validated the promoted route on `Apple M2 Max` without raising the global
AA edge budget:

| Test | Result | Similarity |
| --- | --- | ---: |
| `StrokeRectWebGpuTest` | pass | 95.96% |
| `StrokeCircleWebGpuTest` | pass | 91.81% |
| `StrokeRectCrossBackendTest` GPU | pass | 95.96% |
| `StrokeRectCrossBackendTest` raster | pass | 93.64% |
| `StrokeCircleCrossBackendTest` GPU | pass | 91.81% |
| `StrokeCircleCrossBackendTest` raster | pass | 90.21% |

GRA-213 added dashboard evidence under
`reports/wgsl-pipeline/scenes/artifacts/path-aa-stroke-primitive/` with reference,
CPU, GPU, diff, stats, and route artifacts for the promoted family. The dashboard
row remains `pass` and records the route as
`webgpu.coverage.path-aa-stroke-primitive`.

## Remaining Path AA inventory

The remaining 46 rows stay intentionally unsupported through
`coverage.edge-count-exceeded`. M44 did not weaken broad fallback diagnostics or
raise `SkWebGpuDevice.MAX_AA_EDGES` / `WEBGPU_PATH_AA_EDGE_BUDGET`.

| Family from M37 selection | Status after M44-D | Follow-up recommendation |
| --- | --- | --- |
| Primitive strokes | Selected four rows promoted; 0 selected rows remain in inventory. | Keep route bounded to rect/circle primitive strokes unless a new ticket expands scope. |
| Arc stroke/hairline | Still expected unsupported. | Split cap, hairline, and curve subdivision behavior before promotion. |
| General stroke/dash | Still expected unsupported. | Scope dash/cap/join expansion separately from simple primitive strokes. |
| Fill/convex/path packs | Still expected unsupported. | Avoid broad promotion until fill-rule and shape-family evidence is isolated. |
| Filter/shader over path | Still expected unsupported. | Promote only after base coverage route and composition/layer semantics are stable. |
| Benchmark stress | Still expected unsupported as a performance signal, not a support claim. | Keep as benchmark evidence until a rendering feature ticket owns support. |

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest
rtk git diff --check
```

Inventory command result: expected non-zero test task with generated inventory
classification artifacts. Final generated summary:

```text
total=94
expected-unsupported-diagnostic=46
adapter-skip=48
coverage.edge-count-exceeded=46
coverage.stroke-outline-edge-count-exceeded=0
unexpected-exception=0
similarity-regression=0
```
