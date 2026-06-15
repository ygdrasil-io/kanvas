# M5 - Layer, Destination Read, And Filter Routes

## Goal

Introduce saveLayer, destination-read, and filter render-node routes with
explicit intermediate ownership and stable refusals for unsupported DAGs.

## Dependencies

Depends on M2 resources/passes and M4 texture ownership. Runtime-effect filter
nodes depend on M7 descriptor work.

## Exit Criteria

- [ ] saveLayer routes allocate and composite explicit GPU intermediates.
- [ ] Destination reads use accepted copy/intermediate/layer-isolation
      strategies or refuse.
- [ ] Simple filter nodes have bounds/intermediate diagnostics.
- [ ] Arbitrary filter DAGs and CPU-rendered layer compatibility remain refused.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M5-001 - Add saveLayer isolated target route](KGPU-M5-001-add-savelayer-isolated-target-route.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `layers-resources` | `KGPU-M2-003`, `KGPU-M4-002` | `saveLayer legacy` |
| [KGPU-M5-002 - Add destination-read copy and intermediate strategy](KGPU-M5-002-add-destination-read-copy-and-intermediate-strategy.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `destination-read` | `KGPU-M5-001` | `blend legacy` |
| [KGPU-M5-003 - Add simple filter render node route](KGPU-M5-003-add-simple-filter-render-node-route.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `filters` | `KGPU-M5-001` | `filter legacy` |
| [KGPU-M5-004 - Add filter DAG refusal matrix](KGPU-M5-004-add-filter-dag-refusal-matrix.md) | `done` | `P1` | `RefuseRequired` | `RefuseDiagnostic` | `false` | `false` | `filters-validation` | `KGPU-M5-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Layer*' --tests '*Filter*'
```

## Non-Claims

- No arbitrary image-filter DAG, broad backdrop read, framebuffer fetch, or
  active-attachment sampling support.
- No CPU-rendered full-layer fallback.
- No M5 `GPUNative` route is promoted from contract-only layer,
  destination-read, or filter diagnostics. saveLayer,
  destination-copy/intermediate, and filter render-node claims remain gated on
  native WebGPU/adapter evidence.
- The M5-004 filter DAG matrix is refusal-only evidence; it does not promote
  KGPU-M5-003 simple filter node support.

## Current Evidence

- KGPU-M5-001 is `blocked` on native WebGPU/adapter evidence for provider-owned
  offscreen targets, clear/load/store policy, child draw isolation, restore
  composite, active-attachment separation, resource generation, and
  CPU/GPU/reference comparison.
- KGPU-M5-002 remains `blocked` on KGPU-M5-001 and native destination-read copy
  or intermediate strategy evidence.
- KGPU-M5-003 remains `blocked` on KGPU-M5-001 and simple filter render-node
  evidence.
- KGPU-M5-004 remains `done` as refusal-only evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
