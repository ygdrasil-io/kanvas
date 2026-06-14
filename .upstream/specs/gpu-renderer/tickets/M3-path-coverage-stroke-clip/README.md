# M3 - Path, Coverage, Stroke, And Clip Routes

## Goal

Introduce path, stroke, coverage, and clip routes without collapsing
`GPUNative` and `CPUPreparedGPU` semantics or hiding CPU-rendered texture
fallbacks.

## Dependencies

Depends on M2 for simple geometry/material/scissor foundations. Prepared path
or coverage artifact routes must cite `19-path-coverage-atlas-strategy.md`,
`24-clip-stencil-mask-pipeline.md`, and `25-path-stroke-geometry-pipeline.md`.

## Exit Criteria

- [ ] Path and stroke routes have explicit geometry plans and stable refusals.
- [ ] Prepared artifacts use typed keys, lifetime, budget, and GPU consumer
      facts.
- [ ] Clip rrect/path stacks preserve ordering and refuse unsupported
      interactions.
- [ ] No broad Path AA or arbitrary clip-stack support is implied.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M3-001 - Add basic path fill prepared route](KGPU-M3-001-add-basic-path-fill-prepared-route.md) | `proposed` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `geometry-artifacts` | `KGPU-M2-003` | `path fill legacy` |
| [KGPU-M3-002 - Add stencil-cover path route candidate](KGPU-M3-002-add-stencil-cover-path-route-candidate.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `geometry-passes` | `KGPU-M3-001` | `path fill legacy` |
| [KGPU-M3-003 - Add simple stroke route candidate](KGPU-M3-003-add-simple-stroke-route-candidate.md) | `proposed` | `P0` | `TargetPrepared` | `mixed` | `false` | `true` | `geometry-stroke` | `KGPU-M3-001` | `stroke legacy` |
| [KGPU-M3-004 - Add bounded clip rrect and path route candidate](KGPU-M3-004-add-bounded-clip-rrect-and-path-route-candidate.md) | `proposed` | `P0` | `TargetPrepared` | `mixed` | `false` | `true` | `clips-atlas` | `KGPU-M3-001` | `clip legacy` |
| [KGPU-M3-005 - Add path and coverage atlas refusal policy gates](KGPU-M3-005-add-path-and-coverage-atlas-refusal-policy-gates.md) | `proposed` | `P1` | `RefuseRequired` | `RefuseDiagnostic` | `false` | `false` | `atlas-policy` | `KGPU-M3-001` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Coverage*' --tests '*Path*'
```

## Non-Claims

- No arbitrary path boolean, unbounded clip, broad stroke parity, or hidden CPU
  texture fallback.
- Prepared artifacts are not product support until consumed by an accepted GPU
  route.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
