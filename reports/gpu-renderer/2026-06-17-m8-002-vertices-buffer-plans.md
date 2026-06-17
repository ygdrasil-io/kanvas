# GPU Renderer M8-002 Vertices Buffer Plans

Date: 2026-06-17
Branch: `codex/kgpu-m8-002-vertices-buffer-plans`
Ticket: `KGPU-M8-002`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M8-002 | `done` | Added `GPUVerticesBufferPlanPlanner`, vertex/index buffer payload contracts, deterministic upload/resource dumps, stable refusal facts, and `VerticesBufferPlanTest`. | Independent re-review accepted the evidence with no remaining P0/P1/P2 blockers. `DrawVertices` product support, adapter-backed vertex/index upload, live resource handles, mesh support, batching support, product activation, and CPU-rasterized mesh texture fallback remain unpromoted. |

## Evidence

- `VerticesBufferPlanTest` records the `gpu-renderer.vertices.buffers` row
  with `classification=TargetPrepared`, `routeKind=CPUPreparedGPU` for accepted
  planning evidence, `routeKind=RefuseDiagnostic` for invalid buffer facts,
  `promoted=false`, `productActivation=false`, and `materialized=false`.
- The accepted fixture consumes an accepted KGPU-M8-001 route decision for
  indexed `Triangles` with per-vertex color and emits vertex buffer, index
  buffer, upload plan, resource plan, material-key, diagnostic, and non-claim
  dumps.
- Vertex/index byte counts, alignments, layout hash, usage flags, owner scope,
  staging scope, upload-before-draw dependency, device generation, buffer
  generation, and invalidation facts are dumpable and deterministic.
- Material-key facts are inherited from the route decision:
  `localCoords`, `primitiveBlend`, and `primitiveColor`. Concrete payload
  bytes, content hashes, upload offsets, buffer generations, and resource
  handles are excluded from durable material-key facts.
- Unsupported or invalid variants refuse with stable diagnostics:
  `unsupported.vertices.route_decision_required`,
  `unsupported.vertices.index_out_of_range`,
  `unsupported.vertices.upload_unavailable`,
  `unsupported.vertices.buffer_budget_exceeded`,
  `unsupported.payload.upload_budget_exceeded`,
  `unsupported.payload.resource_stale_generation`, and
  `unsupported.vertices.resource_handle_leak`.
- Refusal facts include route decision kind, vertex/index/total upload bytes,
  index format and range, vertex/index counts, upload-before-draw state,
  required and available usage flags, device/buffer generation facts,
  live-handle exposure, and budget policy.
- Review remediation added explicit coverage for both vertex and index byte
  budgets, plus device and buffer generation staleness.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.vertices.VerticesBufferPlanTest'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Vertices*'
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Vertices*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted RED failed because `GPUVerticesBufferPlanRequest` and
`GPUVerticesBufferPlanPlanner` did not exist. After implementation, the
targeted test passed with three tests. The broader `*Vertices*` test run passed
with the existing KGPU-M8-001 route-decision tests and the new KGPU-M8-002
buffer-plan tests. Full `:gpu-renderer:check --rerun-tasks` passed.
`gpu-raster` `*Vertices*` passed with the existing placeholder/skipped vertices
tests, confirming this branch did not activate raster-side vertices support.
`rtk git diff --check` passed.

Current status count after moving KGPU-M8-002 to `done`:

```text
blocked 6
done 40
proposed 9
review 0
```

## Review

Local pre-review scope:

- check that the buffer, upload, and resource dumps are deterministic;
- check that `CPUPreparedGPU` remains contract-only and does not claim product
  upload or adapter-backed execution;
- check that material-key facts exclude concrete buffer contents, upload
  offsets, resource handles, and generations;
- check that invalid route decisions, out-of-range indices, missing
  upload-before-draw ordering, missing usage flags, budget pressure, stale
  generations, and live-handle leakage refuse with stable diagnostics;
- check that M8-003 remains blocked until this evidence is accepted.

Independent review `019ed5da-a8f2-7f90-8fba-6e29fc8116b4` found no P0/P1
issues and one P2 evidence gap: missing test coverage for `maxIndexBufferBytes`
and stale observed buffer generation. Both cases were added to
`VerticesBufferPlanTest` and the targeted test passed afterward. The review
also noted the status count omitted `proposed 9`; this report now includes it.
Independent re-review `019ed5dd-8e76-78a0-8e8d-646398e40e90` found no
remaining P0/P1/P2 blockers.

## Non-Claims

- No `DrawVertices` product support.
- No adapter-backed vertices execution.
- No vertex or index buffer upload support.
- No materialized live resource handles.
- No primitive blender support.
- No texcoord-driven material support.
- No mesh support.
- No vertices batching support.
- No CPU-rasterized mesh texture fallback.
- No product activation.
- No release-blocking or readiness movement.
