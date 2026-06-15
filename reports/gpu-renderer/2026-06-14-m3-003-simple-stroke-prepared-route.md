# GPU Renderer M3-003 Simple Stroke Prepared Route

Date: 2026-06-14
Branch: `codex/gpu-renderer-m3-stroke-prepared`
Base: stacked on `codex/gpu-renderer-m3-atlas-refusal`, which is stacked on
`codex/gpu-renderer-m3-path-prepared`.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M3-003 | `done` | Added `GPUSimpleStrokePreparedPlanner`, deterministic `GPUStrokeDescriptor` facts including miter-specific prepared keys, `GPUStrokeExpansionPlan` bounds, `GPUPreparedGeometryPlan` consumer `stroke-strip.render-step`, prepared artifact key evidence excluding handles, and stable refusal dumps including path-effect refusal. | Accepted by post-remediation independent review `019ec7e4-77c7-7ec3-ae53-571b6086fbcd`; no product route, adapter-backed execution, broad stroke parity, hairline, dash, round cap/join, path-effect support, or hidden CPU-rendered texture fallback is implied. |

## Evidence

- Accepted bounded stroke emits:
  `geometry:stroke.prepared routeKind=CPUPreparedGPU consumer=stroke-strip.render-step`.
- Artifact evidence uses
  `prepared.stroke.path_segment_v1.width2.butt.miter4.identity.edges4` and keeps
  lifetime/budget facts as `recording-local` and `stroke-simple`.
- Accepted miter values are part of the prepared key; `miter2` and `miter4`
  produce distinct artifact keys.
- Expansion evidence records `cpu-prepared-stroke-strip`, deterministic
  descriptor hash, output bounds, and `joinsFallback=false`.
- Refusal coverage includes invalid width, non-finite width, hairline policy,
  unsupported cap, unsupported join, miter limit, dash, path-effect,
  non-uniform transform, expansion budget overflow, nondeterministic path key,
  and non-finite path bounds.
- Dumps include:
  `nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-stroke-parity no-hairline no-dash no-round-cap-join`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

The targeted test passed after RED/GREEN implementation. Independent review
`019ec7dd-5430-7551-8720-f602d65a4415` then found two blockers: accepted miter
values could collide in the prepared key, and `path-effect` refusal evidence was
missing. The remediating test update failed first on the old key/collision, then
passed after adding the miter value to the prepared stroke descriptor hash.
Shared path-fill/stroke validation, `:gpu-renderer:check`, and `git diff --check`
passed after remediation. Post-remediation independent review
`019ec7e4-77c7-7ec3-ae53-571b6086fbcd` accepted the evidence for `done`.

## Non-Claims

- No product route activation.
- No adapter-backed execution evidence.
- No broad stroke parity.
- No hairline, dash, path-effect, or round cap/join support.
- No hidden CPU-rendered draw/layer/scene texture fallback.
