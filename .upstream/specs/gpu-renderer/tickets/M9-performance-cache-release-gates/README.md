# M9 - Performance, Cache, And Release Gates

## Goal

Promote observed performance and cache telemetry into release-gate candidates
only after correctness support and product activation policy are accepted.

## Dependencies

Depends on M1 for activation policy and on the feature milestones whose routes
will be measured. Cache and telemetry contracts must cite
`13-performance-telemetry-cache-gates.md`.

## Exit Criteria

- [x] Telemetry distinguishes observed, observed-partial, derived, unavailable,
      and reporting-only counters.
- [x] Cache counters are tied to named source artifacts and cannot be inferred
      from comments or synthetic ledgers.
- [x] Release gates include warmup, variance, quarantine, and rebaseline policy.
- [ ] PM bundles separate correctness support from realtime/performance
      readiness.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M9-001 - Add observed cache telemetry source map](KGPU-M9-001-add-observed-cache-telemetry-source-map.md) | `done` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `telemetry-cache` | `KGPU-M1-001` | `cache reporting-only` |
| [KGPU-M9-002 - Add release-blocking frame gate policy](KGPU-M9-002-add-release-blocking-frame-gate-policy.md) | `done` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `performance` | `KGPU-M9-001` | `frame reporting-only` |
| [KGPU-M9-003 - Add PM readiness dashboard integration for GPU renderer](KGPU-M9-003-add-pm-readiness-dashboard-integration-for-gpu-renderer.md) | `blocked` | `P1` | `PolicyGated` | `CPUReferenceOnly` | `false` | `false` | `pm-evidence` | `KGPU-M9-001`, `KGPU-M9-002` | `pipelinePmBundle` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py .
```

## Current Evidence

- `GPUCacheTelemetrySourceMapTest` records one source-map report with observed,
  observed-partial, derived, unavailable, and reporting-only classifications.
- `GPUCacheTelemetrySourceMapper` only counts complete runtime-artifact sources
  with a named hash and required fields as observed readiness inputs.
- Comment, report-text, and synthetic-ledger source kinds are classified as
  derived and cannot count as observed readiness evidence.
- Missing runtime hashes and unavailable sources remain visible as unavailable
  counters.
- The PM dump line keeps `classification=PolicyGated`, `readinessDelta=0.0`,
  `releaseBlocking=false`, and the non-claim
  `no-release-blocking-gate no-readiness-delta no-product-activation
  no-derived-as-observed no-synthetic-comment-counters`.
- Independent review `019ec866-9980-7fe1-bc04-a9806b1d30c3` accepted
  KGPU-M9-001 for `done` with no blocking findings.
- `GPUFrameGatePolicyTest` covers KGPU-M9-002 policy evidence:
  candidate/release-blocking states, warmup/variance policy, reporting-only
  lanes, quarantine, skipped-lane diagnostics, and a negative threshold fixture
  that fails closed without release-blocking authority.
- `sampleGpuRendererSceneFrames` generated owned WebGPU offscreen raw samples
  for `frame-gate-blocker-board` with 60 samples, 3 warmup frames, 57 stable
  frames, adapter `Apple M2 Max`, metric source
  `wall-clock-offscreen-render-readback`, and artifact SHA-256
  `sha256:aacd64f3f65ae87feeaca7600434e2425ff44a7d3e0ddde5a1c66de57021530a`.
  The observed coefficient of variation is `0.1472`, so the lane remains
  candidate/non-release-blocking. Independent review
  `019ed26f-3531-7fd0-8e5d-61f9a15d5a9a` accepted KGPU-M9-002 for `done`
  with no blocking findings.
- KGPU-M9-003 remains `blocked` until PM dashboard and manifest integration is
  implemented without moving readiness.

## Non-Claims

- Correctness evidence is not performance readiness.
- Derived cache ledgers do not count as observed runtime cache telemetry.
- No release-blocking gate is added by writing this milestone.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
