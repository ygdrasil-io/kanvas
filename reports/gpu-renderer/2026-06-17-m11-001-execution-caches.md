# GPU Renderer M11-001 WGPU Execution Caches

Date: 2026-06-17
Branch: `codex/kgpu-m11-001-execution-caches`
Ticket: `KGPU-M11-001`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-001 | `done` | Added execution-owned WGPU shader module, bind-group-layout, pipeline-layout, and render-pipeline caches for the backend runtime helper, with adapter-backed cache telemetry, deterministic preimage dumps, disposer-backed eviction, and independent review acceptance. | No remaining gate for this runtime-helper execution-cache lane. Product route activation, release-blocking performance readiness, broad renderer support, and non-helper execution lanes remain unpromoted. |

## Evidence

- `GPUExecutionObjectCache` records hit, miss, create, failure,
  stale-generation, and eviction events without dumping backend handles.
- Cache eviction now disposes the stored backend handle through a caller-owned
  disposer before emitting the eviction decision.
- `GPUCacheTelemetryEvent` and `GPUTelemetryLedger` now include
  `bind-group-layout`, `pipeline-layout`, create, failure, and stale-generation
  facts.
- `WgpuBackendSession` exposes execution cache telemetry and deterministic dump
  lines without handle identities.
- `WgpuRenderRecorder` reuses session-owned WGPU shader module,
  bind-group-layout, pipeline-layout, and render-pipeline handles instead of
  recreating them per fullscreen pass.
- Cache keys are derived from stable WGSL module identity, bind-group layout
  identity, pipeline-layout identity, `GPUPipelineKeys` render preimage facts,
  target color format, blend/sample state, and device generation.
- Runtime cache dump lines include `execution.cache.preimage` rows for module,
  bind-group-layout, pipeline-layout, and render-pipeline keys.
- Invalid render-pipeline materialization uses WebGPU validation error scopes
  so adapter-backed failures emit stable cache failure telemetry instead of
  becoming uncaptured native validation errors.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetryEventTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionCacheContractsTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionCacheContractsTest' --tests 'org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeWgpuSmokeTest'
rtk ./gradlew --no-daemon :gpu-renderer:check
```

The first RED run failed because `pipeline-layout`, runtime cache telemetry,
and preimage dumps did not exist. The eviction RED failed because
`GPUExecutionObjectCache` had no disposer. A direct invalid-pipeline smoke test
initially exposed that uncaptured WGPU validation errors abort the test process;
the runtime now wraps render-pipeline creation in a WebGPU validation error
scope and the adapter-backed failure fixture passes.

## Review

Independent review `019ed639-8c27-7da3-9e8c-ee4e4ce951ed` found three P2
issues before re-review:

- cache-key preimage dumps were missing;
- WGPU adapter-backed failure telemetry was missing;
- cache eviction removed entries without disposing backend handles.

All three findings were fixed and revalidated with targeted execution-cache
tests and full `:gpu-renderer:check`. Re-review found no remaining P0/P1/P2
blockers.

Residual non-blocking risks from re-review:

- the failure fixture covers normal render-pipeline validation, not real device
  loss or `popErrorScope()` failure;
- adapter-backed evidence is offscreen; `WgpuWindowSurface` telemetry remains
  internal;
- these caches cover only the runtime-helper fullscreen lane, not later M11
  payload/resource lanes.

## Non-Claims

- No product route activation.
- No release-blocking performance gate.
- No broad renderer support claim beyond this WGPU runtime-helper cache lane.
- No Ganesh, Graphite, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
- No CPU-rendered compatibility fallback.
- No M11 payload upload, bind-group materialization lane, resource bridge,
  texture/sampler, destination-read, saveLayer, stencil-cover,
  runtime-effect, paint dictionary, or blend-plan execution support.
