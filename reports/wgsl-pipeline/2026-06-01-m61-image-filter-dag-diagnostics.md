# M61 Image Filter DAG Diagnostics

Linear: `FOR-14`

## Scope

This report closes the M61 diagnostics hardening step for bounded image-filter DAG evidence.
The goal is not to claim new GPU support. It is to ensure every generated image-filter DAG or graph refusal has an explicit graph artifact that a PM, reviewer, or executor can inspect.

## Rows Covered

| Scene | Status | Graph artifact | Decision |
|---|---|---|---|
| `m52-big-tile-image-filter-dag-refusal` | `expected-unsupported` | `artifacts/m52-big-tile-image-filter-dag-refusal/graph-diagnostics.json` | BigTile depends on picture/layer prepass ownership, so GPU rendering remains refused. |
| `m54-imagefilters-graph-boundary` | `expected-unsupported` | `artifacts/m54-imagefilters-graph-boundary/graph-diagnostics.json` | Broad image-filter graph shapes remain refused until bounded pass ordering and intermediate ownership are implemented. |

## Diagnostic Contract

Each artifact records:

- the M61 owner milestone;
- the stable fallback reason;
- node count and node budget;
- intermediate texture count and budget;
- input/output bounds;
- per-node support/refusal reasons;
- pass order;
- ownership notes;
- an explicit non-claim for arbitrary image-filter DAG support.

## Gate

`pipelineSceneDashboardGate` now fails generated image-filter DAG rows when `graphDiagnostics` is missing or points to a non-existent dashboard artifact.

Validation:

```text
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
```

Result: success, with no scene gate failures.

## Non-Claims

- This does not implement arbitrary image-filter DAG scheduling.
- This does not implement picture/layer prepass materialization.
- This does not promote BigTile or broad graph image filters to GPU pass rows.
- This only makes the refusal evidence inspectable and mechanically required for generated DAG rows.
