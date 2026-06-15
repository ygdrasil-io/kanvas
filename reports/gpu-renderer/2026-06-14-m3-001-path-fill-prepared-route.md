# GPU Renderer M3-001 Path Fill Prepared Route

Date: 2026-06-14
Branch: `codex/gpu-renderer-m3-path-prepared`
Base: stacked on `codex/gpu-renderer-m1-wave` because M2 is accepted in PR
[#1634](https://github.com/ygdrasil-io/kanvas/pull/1634) but not yet merged
to `origin/master`.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M3-001 | `done` | Added `GPUBasicPathFillPreparedPlanner`, deterministic `GPUPathDescriptor` path-fill facts, `GPUPreparedGeometryPlan` consumer `coverage-mask.sample.path-fill`, prepared artifact key evidence excluding handles, and stable refusal dumps. Independent review `019ec7c5-ae98-7382-b5e2-865bd4734a59` accepted the evidence with no findings. | Downstream M3 tickets still need their own GPU route, stroke, clip, and atlas-refusal evidence; no product route, adapter-backed execution, broad Path AA, or hidden CPU-rendered texture fallback is implied. |

## Evidence

- Accepted bounded path fill emits:
  `geometry:path-fill.prepared routeKind=CPUPreparedGPU consumer=coverage-mask.sample.path-fill`.
- Artifact evidence uses `prepared.path-fill.path_triangle_v1.nonzero.identity.edges3`
  and keeps lifetime/budget facts as `recording-local` and `path-fill-small`.
- Refusal coverage includes noncanonical keys, unsupported fill rule, inverse
  fill, perspective transform, edge-budget overflow, non-finite bounds, missing
  bounds, and volatile paths.
- All dumps include:
  `nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-path-aa`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:check
```

Both commands passed after RED/GREEN implementation.

Milestone broad validation was also sampled:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Coverage*' --tests '*Path*'
```

Result on this branch: failed with `124 tests completed, 21 failed, 2 skipped`.
The same command was rerun on a clean detached worktree at
`origin/codex/gpu-renderer-m1-wave` and failed identically with
`124 tests completed, 21 failed, 2 skipped`. The generated
`gpu-inventory-failure-classification.md` files had no diff and both reported
`23` classified records: `13` expected unsupported diagnostics, `6`
unexpected exceptions, `2` similarity regressions, and `2` adapter skips.
This is recorded as a pre-existing stacked-base validation gate, not as M3-001
completion evidence.

## Non-Claims

- No product route activation.
- No adapter-backed execution evidence.
- No broad Path AA, arbitrary path boolean, stroke, path effect, or perspective
  support.
- No hidden CPU-rendered draw/layer/scene texture fallback.
- Prepared artifacts are not support by themselves until consumed by an
  accepted GPU route.
