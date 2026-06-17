# GPU Renderer M5-003 Simple Filter Render Node Gate

Date: 2026-06-17
Branch: `codex/kgpu-m5-003-simple-filter-route`
Ticket: `KGPU-M5-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M5-003 | `done` | Added `GPUSimpleFilterRenderNodePlanner`, bounded simple-filter request/gate contracts, intermediate ownership and usage facts, accepted/refused diagnostics, canonical graph/bounds/intermediate/render-node/resource/non-claim dumps, and `SimpleFilterRenderNodeRouteTest`. Independent review accepted the contract-gate evidence on 2026-06-17 with no blocking findings. | Adapter-backed native filter execution, product activation, materialized WebGPU textures, arbitrary filter DAGs, runtime-effect filters, CPU-rendered filter texture fallback, and CPU/GPU/reference comparison remain unpromoted. |

## Evidence

- `SimpleFilterRenderNodeRouteTest` records the
  `gpu-renderer.filter.simple-node` row with `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`.
- The accepted bounded `ColorFilter` fixture dumps filter graph identity,
  node id/kind, descriptor hash, finite input/output bounds, crop, tile modes,
  sampling mode, provider-owned intermediate label/descriptor/generation,
  required `render_attachment`/`texture_binding` usages, lifetime, byte
  estimate, render-step label, pipeline/payload/binding hashes, resource
  aliasing facts, budget class, and non-terminal
  `accepted.filter.simple_node` diagnostic.
- Unsupported variants refuse with stable diagnostics:
  `unsupported.filter.bounds_unbounded`,
  `unsupported.filter.bounds_invalid`,
  `unsupported.filter.graph_node_limit`,
  `unsupported.filter.node_unimplemented`,
  `unsupported.filter.intermediate_unvalidated`,
  `unsupported.filter.read_write_aliasing`,
  `unsupported.filter.node_descriptor_invalid`,
  `unsupported.filter.cpu_rendered_texture_forbidden`, and
  `unsupported.filter.intermediate_budget_exceeded`.
- Intermediate validation requires both ownership acceptance and mandatory
  `render_attachment`/`texture_binding` usage facts.
- Read/write aliasing and active-attachment sampling refuse before accepted
  route evidence is emitted.
- CPU-rendered filter texture fallback is explicitly refused.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.filters.SimpleFilterRenderNodeRouteTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-raster:test --tests '*Layer*' --tests '*Filter*'
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

The targeted test first failed in RED state because the simple filter planner,
request, bounds, and gate plan contracts did not exist. After implementation,
the targeted test, full `:gpu-renderer:check`, M5 `gpu-raster` Layer/Filter
validation bundle, `rtk git diff --check`, and the status-count command passed.

Fresh `master` validation on 2026-06-17 also passed after review acceptance.
An earlier parallel run of `:gpu-renderer:check` and the `gpu-raster` bundle
produced a transient `:font:gpu-api:compileKotlin` classpath error; rerunning
the `gpu-raster` bundle by itself passed, and isolated
`:font:gpu-api:compileKotlin` showed the expected `:font:core` dependency.

Current status count after moving KGPU-M5-003 to `done`:

```text
blocked 9
done 36
review 1
```

## Review

Local pre-PR review scope:

- check that the accepted route is limited to a single bounded `ColorFilter`
  node and does not accept arbitrary DAGs;
- check that intermediate ownership, usage, generation, lifetime, and byte
  evidence are present in the dump;
- check that read/write aliasing and active-attachment sampling refuse;
- check that CPU-rendered filter texture fallback refuses;
- check that accepted diagnostics remain non-terminal and refused diagnostics
  remain terminal;
- check that non-claims prevent product activation or adapter-backed support
  claims.

Independent review on 2026-06-17 found no blocking P0/P1/P2 issues. The review
confirmed that the route remains contract-only with `promoted=false`,
`productActivation=false`, `materialized=false`, and explicit non-claims; that
the dumps cover graph, node, bounds, intermediate, render-node, resource, and
refusal evidence; and that stable refusals cover bounds, DAG shape,
unsupported node, ownership/usages, aliasing, binding, CPU fallback, and
budget cases. KGPU-M5-003 is `done` as accepted contract-gate evidence only.

## Non-Claims

- No product route activation.
- No adapter-backed native filter execution.
- No materialized WebGPU intermediate texture.
- No arbitrary filter DAG support.
- No runtime-effect filter support.
- No CPU-rendered filter texture fallback.
- No CPU/GPU/reference comparison evidence.
- No release-blocking or readiness movement.
