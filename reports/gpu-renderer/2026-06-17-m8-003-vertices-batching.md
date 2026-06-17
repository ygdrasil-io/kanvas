# GPU Renderer M8-003 Vertices Batching

Date: 2026-06-17
Branch: `codex/kgpu-m8-003-vertices-batching`
Ticket: `KGPU-M8-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M8-003 | `done` | Added `GPUVerticesBatchingPlanner`, adjacent batch-key/sort-window evidence, split/refusal diagnostics, telemetry dumps, and `VerticesBatchingPlanTest`. | Independent final re-review `019ed5ec-2289-7d53-8778-7948635b5e06` found no remaining P0/P1/P2 blockers. Executable batching, product `DrawVertices` support, adapter-backed execution, cross-layer batching, destination-read batching, performance readiness, mesh support, and CPU-rasterized mesh texture fallback remain unpromoted. |

## Evidence

- `VerticesBatchingPlanTest` records the
  `gpu-renderer.vertices-batching` row with
  `classification=ImplementationCandidate`, `routeKind=GPUNative` for
  contract-only accepted batching evidence, `routeKind=RefuseDiagnostic` for
  invalid inputs, `promoted=false`, `productActivation=false`, and
  `materialized=false`.
- The accepted adjacent fixture consumes accepted KGPU-M8-001 route decisions
  and KGPU-M8-002 buffer plans, emits one deterministic batch key, preserves
  original paint order in the sort dump, and records telemetry as reporting
  evidence only.
- Split fixtures cover sort-window, topology, render-step, pipeline/layout,
  material, blend, clip, layer, destination-read, barrier, upload-generation, and
  unknown-overlap boundaries. Each split records the previous and next
  invocation IDs.
- Refusal fixtures cover empty inputs, refused route decisions, refused buffer
  plans, and ambiguous paint-order regressions.
- Batch-key dumps name only planner-visible compatibility axes: layer,
  order band, sort window, pipeline key, material key, layout hash, clip key,
  destination-read class, barrier generation, and upload generation. They do
  not include vertex contents, raw handles, object addresses, cache state, or
  transient buffer offsets.

## Validations

```bash
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.vertices.VerticesBatchingPlanTest'
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests '*Vertices*'
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:check
rtk ./gradlew --no-daemon --rerun-tasks :gpu-raster:test --tests '*Vertices*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The initial targeted RED failed because `GPUVerticesBatchingRequest`,
`GPUVerticesBatchInvocation`, and `GPUVerticesBatchingPlanner` did not exist.
After implementation, the targeted class passed with four tests. The review
RED for `sortWindowId` failed because adjacent cross-window invocations merged;
after the fix, the dedicated
`adjacentVerticesSplitAtSortWindowBoundaryAndExposeWindowInDump` test passed.

Final status count after independent review:

```text
blocked 5
done 41
proposed 9
review 0
```

## Review

Local pre-review scope:

- check that batch-key and sort dumps are deterministic;
- check that adjacent batching evidence is contract-only and preserves paint
  order;
- check that topology, render-step, pipeline/layout, material, blend, clip,
  layer, sort-window, destination-read, barrier, upload-generation, and
  unknown-overlap boundaries split with stable reasons;
- check that refused route or buffer evidence cannot enter a batch;
- check that non-claims prevent product activation, executable batching, GPU
  evidence, performance readiness, cross-layer batching, destination-read
  batching, and CPU-rendered fallback claims.

Independent review `019ed5ec-2289-7d53-8778-7948635b5e06` found the original
implementation did not split or expose cross-window batches. That code/test
issue is fixed; the evidence now records the sort-window boundary. Final
re-review `019ed5ec-2289-7d53-8778-7948635b5e06` found no remaining P0/P1/P2
blockers.

## Non-Claims

- No `DrawVertices` product support.
- No executable vertices batching.
- No adapter-backed vertices execution.
- No vertex or index buffer upload support.
- No cross-layer batching.
- No destination-read batching.
- No performance readiness.
- No mesh support.
- No CPU-rasterized mesh texture fallback.
- No product activation.
- No release-blocking or readiness movement.
