# GPU Renderer M8-001 Vertices Route Decisions

Date: 2026-06-17
Branch: `codex/kgpu-m8-001-vertices-route-decisions`
Ticket: `KGPU-M8-001`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M8-001 | `done` | Added typed `GPUVertexMode`, `GPUVerticesRouteDecisionPlanner`, deterministic descriptor/layout/key/route dumps, stable refusal facts, and `VerticesRouteDecisionTest`. | Independent review accepted the evidence with no remaining P0/P1/P2 blockers. `DrawVertices` product support, vertex/index upload, primitive blender support, texcoord material support, mesh support, batching support, product activation, adapter-backed execution, and CPU-rasterized mesh texture fallback remain unpromoted. |

## Evidence

- `VerticesRouteDecisionTest` records the
  `gpu-renderer.vertices.descriptor` row with `classification=TargetNative`,
  `routeKind=GPUNative` for descriptor-only native route decisions,
  `routeKind=RefuseDiagnostic` for unsupported inputs, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted fixture is position-only `Triangles` with no indices, colors,
  texcoords, vertex/index upload, primitive blender, or product route
  activation. It emits descriptor, layout, route, material-key, pipeline-key,
  and diagnostic dumps.
- The descriptor uses typed `GPUVertexMode` values for `Triangles`,
  `TriangleStrip`, `TriangleFan`, and future unsupported labels. Dumps use
  stable source labels instead of raw mutable source objects.
- Material-key facts include only semantic requirements:
  `localCoords`, `primitiveBlend`, and `primitiveColor`. Concrete vertex source
  keys, source provenance, buffer handles, uploads, and draw offsets are not
  durable material-key facts.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.vertices.topology`,
  `unsupported.vertices.triangle_fan_unprepared`,
  `unsupported.vertices.key_nondeterministic`,
  `unsupported.vertices.positions_nonfinite`,
  `unsupported.vertices.vertex_count_budget`,
  `unsupported.vertices.index_count_budget`,
  `unsupported.vertices.attribute_format`,
  `unsupported.vertices.color_format`,
  `unsupported.vertices.local_coords_unproven`,
  `unsupported.vertices.primitive_blender_unregistered`,
  `unsupported.vertices.primitive_blend_destination_read`, and
  `unsupported.vertices.wgsl_abi_unvalidated`.
- Refusal facts include mode, vertex/index counts, position/color/texcoord
  formats, local-coordinate policy, source mutability, finite-position proof,
  adapter/WGSL evidence labels, primitive blend, and whether primitive blending
  would require destination read.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.vertices.VerticesRouteDecisionTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Vertices*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The first targeted RED failed because the route decision planner, request, and
expanded descriptor facts did not exist. A follow-up RED added typed
`GPUVertexMode`, and another RED added the
`primitiveBlendDestinationRead=true` refusal fact. The targeted test then
passed with four tests. Full `:gpu-renderer:check --rerun-tasks` passed.
`gpu-raster` `*Vertices*` passed with the existing placeholder/skipped vertices
tests, confirming this branch did not activate raster-side vertices support.

Current status count after moving KGPU-M8-001 to `done`:

```text
blocked 7
done 39
review 0
```

## Review

Local pre-PR review scope:

- check that descriptor and route dumps are deterministic;
- check that typed topology labels do not imply broad topology support;
- check that material-key facts exclude concrete vertices source identity;
- check that unsupported topology, colors, texcoords, primitive blenders,
  destination-read primitive blending, budgets, and missing WGSL/layout evidence
  refuse with stable diagnostics;
- check that no vertex/index upload, mesh, batching, adapter-backed execution,
  or product activation claim was introduced.

Independent review `019ed5c8-898d-7923-83b6-f8c82775d12e` found no remaining
P0/P1/P2 blockers, confirmed the non-claims and material-key boundary, and found
no hidden product support or status-count issue.

## Non-Claims

- No `DrawVertices` product support.
- No adapter-backed vertices execution.
- No vertex or index buffer upload support.
- No primitive blender support.
- No texcoord-driven material support.
- No mesh support.
- No vertices batching support.
- No CPU-rasterized mesh texture fallback.
- No product activation.
- No release-blocking or readiness movement.
