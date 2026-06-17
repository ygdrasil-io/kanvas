# GPU Renderer M11-008 Runtime-Effect Execution Lane

Date: 2026-06-17
Branch: `codex/kgpu-m11-008-runtime-effect-execution`
Ticket: `KGPU-M11-008`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-008 | `done` | Added contract-only registered runtime-effect execution materialization for the accepted `runtime.simple.color` descriptor gate; materialized payload upload, bind-group, render-pipeline operand, and command-stream evidence; preserved stable refusal diagnostics for stale or mismatched descriptor, registry, WGSL, uniform, route, and payload facts. | Successful adapter readback/reference diff, product route activation, runtime-effect children, filters, blenders, arbitrary SkSL/WGSL source, live editing, and broad runtime-effect support remain unpromoted. |

## Evidence

- `GPURuntimeEffectExecutionRequest` consumes the KGPU-M7-001 descriptor gate
  plus expected descriptor ID/version, registry generation, route placement,
  WGSL module/reflection hashes, uniform schema hash, CPU oracle hash, payload
  request, pipeline cache key, and pass command labels.
- `ValidatingRuntimeEffectExecutionMaterializer` refuses before materialization
  when descriptor ID/version, registry generation, route placement, WGSL module,
  reflection hash, uniform schema, uniform block size, payload binding layout,
  CPU oracle hash, child slots, live editing, or uniform-key boundaries are not
  aligned.
- Accepted execution delegates upload and bind-group validation to
  `ValidatingPayloadResourceProvider`, then adds a provider-owned
  `RenderPipeline` command operand with descriptor, entry point, registry
  generation, route, schema, WGSL module, and `uniformValuesInKey=false`
  evidence.
- Render pipeline identity is derived through `GPUPipelineKeyPreimage.Render`
  and `GPUPipelineKeys`: the command stream uses the compact `render:*` key,
  while materialization evidence carries the separate render-pipeline cache key.
  A mismatched or stale cache key refuses before materialization.
- Uniform payload fields are compared against the descriptor uniform schema
  before provider materialization; byte-size-only payloads are not enough to
  enter the execution lane.
- Command evidence preserves the Dawn-style order:
  `beginRenderPass`, `setRenderPipeline`, `setBindGroup`, `draw`,
  `endRenderPass`.
- The runtime-effect payload fingerprint appears only in payload/upload facts;
  pipeline cache evidence uses descriptor/WGSL/schema/registry identity and
  does not include uniform values.
- Adapter readback evidence is explicitly skipped with
  `gpuReadback=skipped reason=headless-contract-only promoted=false`; CPU oracle
  evidence remains validation-only and is not a product fallback.
- Refusal fixtures cover unregistered descriptors, stale registry generation,
  descriptor version mismatch, route placement mismatch, WGSL module mismatch,
  uniform schema mismatch, and propagated payload provider refusals.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.runtimeeffects.RegisteredRuntimeEffectExecutionLaneTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Non-Claims

- No product route activation.
- No adapter-backed successful readback claim.
- No arbitrary SkSL or WGSL compilation.
- No runtime-effect child, filter, blender, primitive blender, clip shader, or
  live-editing support.
- No CPU-rendered runtime-effect fallback.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
