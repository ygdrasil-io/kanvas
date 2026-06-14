# GPU Renderer M8 Vertices Blockers

## Scope

Reviewed the GPU renderer ticket catalog, `tickets/STATUS.md`, M8 milestone
tickets, and the cited `DrawVertices`, payload, and draw-layer/sort specs.
No M8 implementation ticket is currently actionable without bypassing a
declared dependency or adapter-backed evidence gate.

## Ticket Status

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M8-001 | `blocked` | Depends on KGPU-M7-003, which is blocked on KGPU-M5-002 and native destination-read strategy evidence. Needs accepted primitive blend/color route decisions, deterministic descriptor/key/refusal dumps, adapter-backed layout/WGSL/route evidence, and explicit skipped/refused unsupported lanes. |
| KGPU-M8-002 | `blocked` | Depends on KGPU-M8-001 plus adapter-backed vertex/index buffer ownership, upload-before-draw ordering, resource-generation, layout/WGSL ABI, budget, and invalid/stale buffer refusal evidence. |
| KGPU-M8-003 | `blocked` | Depends on KGPU-M8-001 and KGPU-M8-002. Needs route and buffer facts before batching/sort/split evidence can be meaningful. |

## Evidence

- `26-draw-vertices-mesh-pipeline.md` requires promoted vertices behavior to
  include descriptor, topology, attribute layout, primitive blend/color,
  vertex/index buffer, WGSL ABI, CPU/refusal, GPU, route, and PM evidence.
- `15-draw-layer-planner-and-sort-policy.md` requires batching and sort windows
  to preserve clip, layer, destination-read, barrier, upload-generation, and
  ordering boundaries.
- M7-003 is blocked on KGPU-M5-002/native destination-read strategy, so
  M8-001's dependency chain is not ready.

## Validation

```bash
rtk git diff --check
```

Result: passed.

## Review

Independent review `019ec859-5cca-78a3-9196-9e46dd136eec` found no
P0/P1/P2/P3 issues and accepted the blocker-only wave for commit/PR. The
review confirmed there is no ready hidden subset in the scoped M8 tickets and
that a smaller refusal/boundary-only vertices slice would need a separate
ticket.

## Non-Claims

- No `DrawVertices` support.
- No mesh support.
- No primitive blender support.
- No vertex/index buffer upload support.
- No batching or performance readiness claim.
- No GPU-native vertices route.
- No CPU-rasterized mesh texture fallback.
