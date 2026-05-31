# M55 Quarantine, Rebaseline, And Rollback Policy

Result: pass.

This policy defines how the M55 performance gate candidate can become a future
release-blocking gate without hiding regressions. M55 itself remains
non-blocking.

Candidate report:
`build/reports/wgsl-pipeline-performance-warnings/m55-performance-gate-candidate.md`

Selection contract:
`reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json`

## Candidate Thresholds

| Metric | Candidate review threshold |
|---|---:|
| Median timing delta | 15% |
| P95 timing delta | 20% |
| Minimum sample count | 30 |

Thresholds are candidate review bands only. They do not fail Gradle or release
jobs in M55.

## Quarantine Criteria

A row is quarantined from score movement when:

- host, JDK, backend, or GPU adapter differs from the baseline eligibility;
- sample count or variance evidence is incomplete;
- the row is missing CPU or GPU/cache measurements required by its selection
  decision;
- a measured row reports `regression.label=regressed`;
- adapter access is missing for a GPU/cache lane.

Every quarantine requires an owner, an expiry or review condition, and a visible
candidate status of `warn`, `fail-candidate`, or `deferred`.

## Rebaseline Criteria

A baseline may be updated only when:

- the current and proposed baseline use the same eligible host/JDK/backend and
  GPU adapter lane;
- at least three consecutive stable runs exist for the intended lane;
- the baseline owner approves the update;
- the PM report records the before/after median and p95 values;
- the change does not mask a correctness regression.

Rebaseline is for stable platform drift or intentional performance work. It is
not the remedy for a noisy, missing, or regressed lane.

## Rollback Criteria

Rollback is distinct from rebaseline. Roll back the rendering or benchmark
change when:

- a correctness regression accompanies the performance change;
- a measured row has a confirmed repeatable regression beyond the candidate
  review band and no owner-approved exception;
- a benchmark change removes required metadata or hides an adapter mismatch;
- the candidate report would otherwise turn missing data into a hidden pass.

## Missing Lanes

Missing host or adapter lanes must use `deferred` or `warn`. They cannot be
silently treated as pass, and they cannot move a row from estimated to measured.

## Future Release-Blocking Activation

A later sprint may make the gate release-blocking only after it adds:

- explicit CI time budget and timeout;
- host/JDK/backend/adapter eligibility matrix;
- minimum sample count and variance policy;
- retry, flake, quarantine, and escalation rules;
- rollback process for noisy or regressed gates;
- named baseline owner review;
- PM evidence for at least three consecutive stable runs on the intended CI lane.

## Validation

- `rtk git diff --check`
