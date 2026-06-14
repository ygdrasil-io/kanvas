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
simple filter routes are not promoted in this branch. The filter DAG refusal
matrix is implemented as refusal-only evidence because it does not require an
adapter and must not imply simple filter node support.

## Ticket Gates

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M5-001 | `proposed` | Native WebGPU/adapter evidence for provider-owned offscreen layer targets, clear/load/store, child isolation, restore composite, active-attachment separation, resource generation, and CPU/GPU/reference comparison. |
| KGPU-M5-002 | `blocked` | KGPU-M5-001 plus native bounded destination-copy or validated intermediate strategy, pass split/copy-before-sample ordering, copy/texture usage validation, and CPU/GPU/reference comparison. |
| KGPU-M5-003 | `blocked` | KGPU-M5-001 plus native bounded filter render-node route, provider-owned intermediate texture ownership, bounds/crop/tile diagnostics, read/write aliasing refusal, WGSL/render-node binding validation, and CPU/GPU/reference comparison. |
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

## Non-Claims

- No `GPUNative` saveLayer support is claimed.
- No destination-read copy/intermediate route is claimed.
- No simple filter render-node route is claimed.
- No arbitrary image-filter DAG support is claimed.
- No framebuffer fetch, active-attachment sampling, CPU readback, or
  CPU-rendered layer/filter texture fallback is claimed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.filters.FilterDagRefusalMatrixTest
```
