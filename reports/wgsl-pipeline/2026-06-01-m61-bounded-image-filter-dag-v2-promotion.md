# M61 Bounded Image Filter DAG V2 Promotion

Linear: `FOR-15`

## Decision

Promote `m61-compose-cf-matrix-transform-dag-v2` as the M61 bounded image-filter DAG V2 pass row.

The row is derived from the generated `image-filter-compose-cf-matrix-transform` test-oracle evidence and stays deliberately narrow: three nodes, one bounded intermediate texture, affine MatrixTransform only, and final ColorFilter composite.

This is not an inventory-derived Skia GM promotion. In particular, it does not claim `skia-gm-imagefiltersgraph` support or broad `ImageFiltersGraphGM` parity.

## Scene

| Field | Value |
|---|---|
| Scene id | `m61-compose-cf-matrix-transform-dag-v2` |
| Base evidence | `image-filter-compose-cf-matrix-transform` |
| Evidence kind | Generated test oracle, not Skia GM inventory |
| Status | `pass` |
| CPU route | `cpu.image-filter.compose.cf-matrix-transform-oracle` |
| GPU route | `webgpu.image-filter.compose.cf-matrix-transform.dag-v2` |
| Fallback | `none` |
| Threshold | `99` |
| Similarity | `100` |

## Graph

```mermaid
flowchart LR
  A["source-image"] --> B["matrix-transform<br/>affine prepass"]
  B --> C["color-filter-compose<br/>final composite"]
```

The generated `graph-diagnostics.json` records:

- `nodeCount=3`, `nodeBudget=4`;
- `intermediateTextureCount=1`, `intermediateTextureBudget=4`;
- input/output bounds of `32x32`;
- pass order `matrix-transform-prepass`, then `color-filter-final-composite`;
- scratch owner `LayerCompositeDraw.materializeTargetTexture`;
- explicit non-claim for arbitrary DAGs, picture/layer prepass, perspective transforms, and recursive scheduling.

## Artifacts

- Reference: `artifacts/m61-compose-cf-matrix-transform-dag-v2/skia.png`
- CPU: `artifacts/m61-compose-cf-matrix-transform-dag-v2/cpu.png`
- GPU: `artifacts/m61-compose-cf-matrix-transform-dag-v2/gpu.png`
- CPU diff: `artifacts/m61-compose-cf-matrix-transform-dag-v2/cpu-diff.png`
- GPU diff: `artifacts/m61-compose-cf-matrix-transform-dag-v2/gpu-diff.png`
- CPU route: `artifacts/m61-compose-cf-matrix-transform-dag-v2/route-cpu.json`
- GPU route: `artifacts/m61-compose-cf-matrix-transform-dag-v2/route-gpu.json`
- Graph diagnostics: `artifacts/m61-compose-cf-matrix-transform-dag-v2/graph-diagnostics.json`
- Stats: `artifacts/m61-compose-cf-matrix-transform-dag-v2/stats.json`

## Validation

```text
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk git diff --check
```

## Non-Claims

- No arbitrary image-filter DAG scheduler is claimed.
- No picture/layer prepass support is claimed.
- No perspective MatrixTransform support is claimed.
- No broad Skia `ImageFiltersGraphGM` parity is claimed.
