# M48 Expected-Unsupported Breadth Evidence

Date: 2026-05-31
Linear: GRA-284
Parent epic: GRA-279
Depends on: GRA-281

## Scope

GRA-284 adds the three M48-E rows selected by `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md` as explicit `expected-unsupported` generated dashboard evidence:

| Scene id | Family | Stable GPU route | Stable fallback reason | Value for MEP planning |
|---|---|---|---|---|
| `path-aa-convexpaths-edge-budget` | Path AA | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Keeps ConvexPaths breadth visible without claiming broad Path AA support. |
| `path-aa-dashing-edge-budget` | Path AA / stroke / dash | `webgpu.coverage.refuse` | `coverage.edge-count-exceeded` | Keeps dash/stroke AA breadth visible without lowering edge-budget policy. |
| `image-filter-crop-nonnull-prepass-required` | image-filter / crop | `webgpu.image-filter.refuse` | `image-filter.crop-input-nonnull-prepass-required` | Keeps non-selected Crop(input=nonNull) graph shapes visible without claiming a general image-filter DAG compiler. |

## Artifact Roots

Each row has CPU/reference diagnostic artifacts and explicit GPU refusal route diagnostics:

| Scene id | Artifacts |
|---|---|
| `path-aa-convexpaths-edge-budget` | `reports/wgsl-pipeline/scenes/artifacts/path-aa-convexpaths-edge-budget/` |
| `path-aa-dashing-edge-budget` | `reports/wgsl-pipeline/scenes/artifacts/path-aa-dashing-edge-budget/` |
| `image-filter-crop-nonnull-prepass-required` | `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/` |

Each artifact root contains:

- `skia.png`: reference/CPU-oracle diagnostic thumbnail.
- `cpu.png`: CPU oracle diagnostic thumbnail.
- `cpu-diff.png`: expected-unsupported diagnostic thumbnail for dashboard diff linking.
- `route-cpu.json`: CPU oracle route facts.
- `route-gpu.json`: WebGPU refusal route facts with stable fallback reason.
- `stats.json`: inventory-oriented stats and owning command.

## Non-Claim Boundaries

- These rows are not support evidence and must not be relabelled `pass` to improve counters.
- `path-aa-convexpaths-edge-budget` and `path-aa-dashing-edge-budget` do not remove or expand the WebGPU 256-edge Path AA budget.
- `image-filter-crop-nonnull-prepass-required` does not claim recursive crop pre-passes, arbitrary image-filter DAG support, or general layer scheduling.
- No CPU readback, silent no-op, or raster fallback route is introduced.

## Counter Impact

Expected dashboard counters after GRA-284:

| Counter | Value |
|---|---:|
| Total rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated rows | 21 |
| Static rows | 2 |

## Validation

Commands for this change:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.tools.GpuInventoryFailureReportTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

`gpuInventoryTest` is not required by this patch because inventory classification policy is not changed; the new rows cite existing stable Path AA and image-filter refusal policy.
