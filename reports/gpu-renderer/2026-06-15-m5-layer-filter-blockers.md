# GPU Renderer M5 Layer/Filter Status And Refusal Matrix

Date: 2026-06-15

## Scope

Reviewed the GPU renderer ticket catalog, `STATUS.md`, M5 milestone tickets,
and the cited specs:

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`
- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`
- `.upstream/specs/gpu-renderer/28-layer-savelayer-execution.md`

## Decision

M5 is partially actionable. The `GPUNative` saveLayer, destination-read, and
simple filter contract gates now have non-promoted evidence, but product
support remains gated on adapter-backed execution and reference comparison.
The filter DAG refusal matrix is implemented as refusal-only evidence because
it does not require an adapter and must not imply arbitrary filter DAG support.

## Ticket Gates

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M5-001 | `done` | Contract-gate accepted after independent review. Native adapter-backed saveLayer execution, product activation, materialized WebGPU target, and CPU/GPU/reference comparison remain unpromoted. |
| KGPU-M5-002 | `done` | Contract-gate accepted after independent review. Native adapter-backed destination-copy execution, framebuffer fetch/input attachments, product activation, and CPU/GPU/reference comparison remain unpromoted. |
| KGPU-M5-003 | `review` | Contract-gate evidence exists for one bounded `ColorFilter` render-node route. Independent acceptance, adapter-backed native filter execution, product activation, materialized WebGPU texture, arbitrary DAG/runtime-effect support, and CPU/GPU/reference comparison remain unpromoted. |
| KGPU-M5-004 | `done` | Refusal-only `GPUFilterDagRefusalMatrix` evidence independently reviewed after non-promotion remediation. KGPU-M5-003 support remains unpromoted. |

## Evidence

- `GPUFilterDagRefusalMatrix` maps unsupported node variants to stable
  diagnostics.
- The matrix separates PM supportable-bounded rows from refused variants
  without promoting those rows as product routes.
- Missing finite bounds or filter intermediate ownership keeps the report
  non-promotable.
- Even a matrix containing only supportable-bounded rows remains
  non-promotable; KGPU-M5-004 is refusal-only evidence and cannot promote
  KGPU-M5-003 support.
- `FilterDagRefusalMatrixTest` covers unsupported nodes, PM row separation,
  unbounded bounds, missing intermediate ownership, and the all-supportable
  non-promotion guard.
- Independent re-review reported no findings and accepted KGPU-M5-004 for
  `done` after confirming no hidden `GPUNative` or product activation claim.
- `SimpleFilterRenderNodeRouteTest` covers the KGPU-M5-003 contract gate for a
  bounded `ColorFilter`, including graph/node/bounds/intermediate/render-node
  dumps, accepted diagnostics, read-write aliasing refusal, CPU-rendered
  texture fallback refusal, budget refusal, and non-claims. KGPU-M5-003 is in
  `review` pending independent acceptance.

## Non-Claims

- No product `GPUNative` saveLayer support is claimed.
- No product destination-read copy/intermediate support is claimed.
- No product simple filter render-node support is claimed.
- No arbitrary image-filter DAG support is claimed.
- No framebuffer fetch, active-attachment sampling, CPU readback, or
  CPU-rendered layer/filter texture fallback is claimed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.filters.FilterDagRefusalMatrixTest
```
