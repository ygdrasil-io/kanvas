# M9 - Performance, Cache, And Release Gates

## Goal

Promote observed performance and cache telemetry into release-gate candidates
only after correctness support and product activation policy are accepted.

## Dependencies

Depends on M1 for activation policy and on the feature milestones whose routes
will be measured. Cache and telemetry contracts must cite
`13-performance-telemetry-cache-gates.md`.

## Exit Criteria

- [ ] Telemetry distinguishes observed, observed-partial, derived, unavailable,
      and reporting-only counters.
- [ ] Cache counters are tied to named source artifacts and cannot be inferred
      from comments or synthetic ledgers.
- [ ] Release gates include warmup, variance, quarantine, and rebaseline policy.
- [ ] PM bundles separate correctness support from realtime/performance
      readiness.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M9-001 - Add observed cache telemetry source map](KGPU-M9-001-add-observed-cache-telemetry-source-map.md) | `proposed` | `P0` | `PolicyGated` | `mixed` | `false` | `true` | `telemetry-cache` | `KGPU-M1-001` | `cache reporting-only` |
| [KGPU-M9-002 - Add release-blocking frame gate policy](KGPU-M9-002-add-release-blocking-frame-gate-policy.md) | `proposed` | `P0` | `PolicyGated` | `mixed` | `false` | `true` | `performance` | `KGPU-M9-001` | `frame reporting-only` |
| [KGPU-M9-003 - Add PM readiness dashboard integration for GPU renderer](KGPU-M9-003-add-pm-readiness-dashboard-integration-for-gpu-renderer.md) | `proposed` | `P1` | `PolicyGated` | `mixed` | `false` | `false` | `pm-evidence` | `KGPU-M9-001`, `KGPU-M9-002` | `pipelinePmBundle` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py .
```

## Non-Claims

- Correctness evidence is not performance readiness.
- Derived cache ledgers do not count as observed runtime cache telemetry.
- No release-blocking gate is added by writing this milestone.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
