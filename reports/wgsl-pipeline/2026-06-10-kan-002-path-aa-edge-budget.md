# KAN-002 Path AA Edge Budget Evidence

KAN-002 records the Path AA edge budget as `256` and ties that budget to
existing bounded support and refusal evidence. The result is an auditable PM
slice, not a new renderer capability.

## Result

The evidence pack lives at
`reports/wgsl-pipeline/scenes/artifacts/kan-002-path-aa-edge-budget/kan-002-path-aa-edge-budget.json`.

| Role | Scene | Status | Evidence |
|---|---|---|---|
| Bounded positive Path AA row | `path-aa-stroke-primitive` | `pass` | Reference, CPU, GPU, diff, stat, and route artifacts exist for the primitive stroke route. |
| Explicit budget diagnostics | `m57-aaclip-bounded-grid` | `pass` | CPU and WebGPU route files expose `edgeBudget=256` and `edgeBudgetReason=not coverage.edge-count-exceeded`. |
| Stable over-budget refusal | `path-aa-convexpaths-edge-budget` | `expected-unsupported` | WebGPU route refuses with `coverage.edge-count-exceeded`; CPU/reference/diff artifacts remain present. |
| Inventory boundary | `path-aa-edge-budget-boundary` | `expected-unsupported` | `gpuInventoryTest` keeps 46 edge-count exceeded rows, 50 expected unsupported rows, and zero unexpected exceptions. |
| Under-budget non-edge refusal | `m60-bounded-stroke-cap-join` | `expected-unsupported` | Edge count is 18/256, but the row is refused for `coverage.stroke-cap-join-visual-parity-below-threshold`. |

## Budget Policy

The active WebGPU Path AA budget remains `256` edges:

- `.upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md`
  says to use `256`.
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
  lists `Coverage edge count | <= 256`.
- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/WebGpuCoveragePlanSelector.kt`
  exposes `WEBGPU_PATH_AA_EDGE_BUDGET: Int = 256`.
- `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`
  keeps `MAX_AA_EDGES: Int = 256`.
- `render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt`
  keeps `EdgeCountExceeded("coverage.edge-count-exceeded")`.

When a WebGPU coverage row exceeds the budget, the stable route is
`webgpu.coverage.refuse` with `coverage.edge-count-exceeded`. It is not a
similarity regression, an unclassified exception, a smoke candidate, or an
implicit CPU readback path.

## Non-Claims

No broad Path AA support claim is added.

No arbitrary complex-path support claim is added.

No edge budget or dashboard threshold is changed.

No Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM work is introduced.

## Validation

```bash
rtk python3 scripts/validate_kan002_path_aa_edge_budget.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan002PathAaEdgeBudget
rtk python3 scripts/validate_for307_path_aa_edge_budget_candidate_selection.py
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
