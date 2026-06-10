# KAN-020 Performance Proof Minimum

Date: 2026-06-10
Ticket: KAN-020 - Perf minimale par slice visible

## Decision

KAN-020 defines the minimum performance proof required for future PM-visible
slices. It does not add a new release-blocking threshold. It makes each slice
declare one of these outcomes:

- measured release gate already selected by policy;
- measured candidate or candidate/reporting-only payload;
- reporting-only payload with a clear non-gating rationale;
- derived resource/cache ledger that stays non-gating;
- expected-unsupported refusal where performance is not applicable because no
  support path is claimed.

Estimated, missing, derived, or diagnostic-only metrics must not be counted as
measured gates.

## Policy Artifact

Structured policy:

- `reports/wgsl-pipeline/performance/kan-020-slice-performance-minimum.json`

Validator:

- `scripts/validate_kan020_performance_proof_minimum.py`

Gradle task:

- `:validateKan020PerformanceProofMinimum`

## Existing Evidence Anchors

| Evidence | Classification | Gate treatment | PM meaning |
|---|---|---|---|
| `reports/wgsl-pipeline/performance/m59-performance-release-gate.json` | release-blocking measured gate | release-blocking only for the already selected M59 rows | Existing selected gate policy; KAN-020 does not expand it. |
| `reports/wgsl-pipeline/performance/m67-performance-tiering/m67-frame-gate-candidate.json` | measured candidate | `releaseBlocking=false` | Headless frame metrics are measured candidate evidence, not a release gate. |
| `reports/wgsl-pipeline/performance/m67-performance-tiering/m67-family-budgets.json` | mixed candidate/reporting-only family budget | 1 measured family, 6 reporting-only families | Families without isolated timing payloads stay reporting-only. |
| `reports/wgsl-pipeline/m84-native-frame-timing/evidence.json` | measured candidate/reporting-only | `gatePhase=candidate-reporting-only`, `countedAsMeasuredGate=false` | Native Kadre timing is visible but not release-grade FPS. |
| `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json` | derived selected-scene ledger | `observedRuntimeCounters=false`, `countedAsCacheReadinessGate=false` | Resource/cache behavior is auditable, but not observed broad WebGPU cache telemetry. |

## Minimum Per Slice Type

| Slice type | Minimum performance proof |
|---|---|
| Bounded rendering support | Link an existing selected release gate, add measured candidate/reporting evidence, or state a non-gating rationale. Correctness artifacts alone are not performance measurements. |
| Expected-unsupported refusal | Keep the stable refusal reason visible and state that no performance gate applies because no support path is claimed. |
| Runtime frame lane | Provide warmup/measured frame samples and host/backend metadata, or keep the lane candidate/reporting-only with quarantine rationale. |
| Resource/cache lane | Classify counters as observed, observed-partial, derived, or unavailable; derived ledgers remain non-gating. |
| Release readiness | Reference selected gates and preserve the distinction between release-blocking, candidate, reporting-only, and non-gating refusal rows. |

## Forbidden Promotions

- Do not count estimated metrics as measured.
- Do not count missing metrics as measured.
- Do not promote reporting-only rows to release-blocking without selected
  policy, measured payload, owner, host/backend eligibility, and negative
  fixture evidence.
- Do not count M85 derived cache ledgers as observed WebGPU runtime cache
  telemetry.
- Do not add a new release-blocking threshold from KAN-020.

## How Future Tickets Should Use This

Each future visible ticket should include a short performance section in its
report or evidence JSON:

```text
Performance class: measured-candidate | reporting-only | derived-ledger-reporting | non-gating-refusal | release-blocking-measured-gate
Release blocking: true/false
Counted as measured gate: true/false
Source evidence: <path>
Rationale: <one sentence>
Non-claims: <what this does not prove>
```

The default for a new support slice without a real performance payload is
`reporting-only`, not `measured`. The default for a refusal-only slice is
`non-gating-refusal`.

## Non-Claims

- KAN-020 does not create a new release-blocking performance threshold.
- KAN-020 does not promote `frame.kadre-windowed` to release-grade FPS.
- KAN-020 does not count derived M85 cache ledgers as observed runtime cache
  telemetry.
- KAN-020 does not count estimated or missing metrics as measured evidence.
- KAN-020 does not claim broad Skia performance parity.

## Validation

```text
rtk python3 scripts/validate_kan020_performance_proof_minimum.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan020PerformanceProofMinimum
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
