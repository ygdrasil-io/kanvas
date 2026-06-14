# M0 - R6 Boundary And Evidence Review

## Goal

Backfill the already implemented or reported R0-R6 slices as review-required
tickets. This milestone exists to prevent the ticket catalog from claiming that
the first-route work is accepted merely because implementation and reports
exist.

## Dependencies

Depends on the current `:gpu-renderer` roadmap progress report, R6 promotion
boundary report, and validation scripts generated on 2026-06-14.

## Exit Criteria

- [ ] Every R0-R6 slice has an independent review ticket with linked evidence.
- [ ] No reviewed ticket claims product route activation.
- [ ] The root refusal-first PM bundle remains separate from the opt-in
      adapter-backed executed diagnostic lane.
- [ ] `STATUS.md` keeps these tickets in `review` until review evidence is
      explicitly accepted.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M0-001 - Review R0 structure guard evidence](KGPU-M0-001-review-r0-structure-guard-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `validation` | - | - |
| [KGPU-M0-002 - Review R1 first native draw route evidence](KGPU-M0-002-review-r1-first-native-draw-route-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `analysis-routing-passes` | `KGPU-M0-001` | - |
| [KGPU-M0-003 - Review R2 WGSL module and ABI evidence](KGPU-M0-003-review-r2-wgsl-module-and-abi-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `wgsl-materials` | `KGPU-M0-002` | - |
| [KGPU-M0-004 - Review R3 material payload and pipeline key evidence](KGPU-M0-004-review-r3-material-payload-and-pipeline-key-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `payloads-pipelines` | `KGPU-M0-003` | - |
| [KGPU-M0-005 - Review R4 resource execution and readback evidence](KGPU-M0-005-review-r4-resource-execution-and-readback-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `resources-execution` | `KGPU-M0-004` | - |
| [KGPU-M0-006 - Review R5 recording task graph evidence](KGPU-M0-006-review-r5-recording-task-graph-evidence.md) | `review` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `false` | `recording` | `KGPU-M0-005` | - |
| [KGPU-M0-007 - Review R6 PM evidence and promotion boundary](KGPU-M0-007-review-r6-pm-evidence-and-promotion-boundary.md) | `review` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `validation-pm` | `KGPU-M0-001`, `KGPU-M0-002`, `KGPU-M0-003`, `KGPU-M0-004`, `KGPU-M0-005`, `KGPU-M0-006` | `pipelinePmBundle` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon pipelinePmBundle --dry-run
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
```

## Non-Claims

- `review` does not mean accepted support.
- The first route is not activated as a product route by this milestone.
- Adapter-backed executed evidence remains opt-in and does not become a root
  `pipelinePmBundle` dependency.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
