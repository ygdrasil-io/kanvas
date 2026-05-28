# M42 P0 Dashboard Status Policy Update

Date: 2026-05-28
Linear: GRA-204

## Outcome

M42 attempted adapter-backed captures for both P0 tracked-gap rows.

| Scene | Before | After | Reason |
|---|---|---|---|
| `solid-rect` | `tracked-gap` | `pass` | Adapter-backed WebGPU capture matches CPU oracle at 100.0%, `fallbackReason=none`. |
| `analytic-aa-convex` | `tracked-gap` | `tracked-gap` | Adapter-backed WebGPU capture exists and route is supported, but the current CPU oracle stores AA edge alpha differently from WebGPU `SrcOver` output. |

## Current P0 Status

- `solid-rect`: `pass`, backend `WebGPU`, adapter `Apple M2 Max`, GPU similarity `100.0%`.
- `analytic-aa-convex`: `tracked-gap`, backend `WebGPU`, adapter `Apple M2 Max`, GPU similarity `90.6%`, blocker `gpu.analytic-aa-convex-edge-alpha-oracle-mismatch`, follow-up `GRA-222`.

## Dashboard Policy

The dashboard now keeps blocker and follow-up fields visible for tracked gaps. A `tracked-gap` row is not limited to missing image artifacts: it also covers a route with real adapter-backed evidence that fails the support threshold or has an unresolved policy/oracle requirement.

The `analytic-aa-convex` row remains visible with:

- GPU image: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu.png`
- GPU diff: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/gpu-diff.png`
- GPU route: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/route-gpu.json`
- GPU stats: `reports/wgsl-pipeline/scenes/artifacts/analytic-aa-convex/stats.json`
- Follow-up: `GRA-222`

No tracked gap or expected unsupported row was removed from the registry.

## GRA-222 Resolution

GRA-222 regenerated the `analytic-aa-convex` CPU oracle as composited `SrcOver`
AA edge pixels over the opaque scene background. The row is now `pass` with
adapter-backed WebGPU evidence on Apple M2 Max:

- GPU similarity: `100.0%`
- Matching pixels: `256 / 256`
- Max channel delta: `0`
- GPU route: `webgpu.coverage.path-convex-fan`
- Fallback reason: `none`
- Edge budget reason: `not coverage.edge-count-exceeded`

Resolution report:
`reports/wgsl-pipeline/2026-05-28-m42-analytic-aa-convex-aa-edge-oracle-reconciliation.md`.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
