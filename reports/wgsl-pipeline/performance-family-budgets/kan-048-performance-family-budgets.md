# KAN-048 Performance Family Budgets

Status: `pass`

KAN-048 reports family-level performance payloads without creating a
release-blocking gate or requiring slow benchmarks in CI.

## Summary

| Metric | Value |
| --- | ---: |
| Family rows | `3` |
| Measured families | `1` |
| Unavailable families | `2` |
| Estimated payloads counted as measured | `0` |
| Release-blocking rows | `0` |

## Family Budgets

| Family | Status | Measured | Lane | p50 / p95 | Reason / gate |
| --- | --- | --- | --- | --- | --- |
| filters | unavailable | False | family.image-filter | unavailable / unavailable | performance.image-filter.intermediate-benchmark-unavailable |
| text | unavailable | False | family.text-glyph | unavailable / unavailable | performance.text-glyph.production-sampling-route-unavailable |
| bitmap/color | measured | True | family.bitmap-color | 0.015771 / 0.168417 | KAN-048 aggregates existing local M43/M59 payloads; no CI-owned baseline, variance policy, owner matrix, and negative fixture are promoted here. |

## Gate Policy

- Family gates remain `reporting-only`.
- Promotion requires CI-owned baseline, host/JDK/backend/adapter eligibility,
  variance policy, negative fixture, and baseline owner.
- Unavailable rows keep stable reasons instead of being estimated.

## Quarantine Rationale

- `image-filter`: missing_whole_scene_intermediate_rgba8_candidate_evidence_for_exact_and_precision_sensitive_routes; root-cause blocker must be solved before filter timing can be promoted
- `text-glyph`: requires-production-glyph-atlas-sampling-route; outline-path text evidence is not a glyph atlas sampling performance payload
- `bitmap-color`: local measured payloads are PM trend evidence, not release gates; family-level baseline/variance/owner/negative fixture is not complete; adapter-specific GPU rows stay reporting-only and cannot be compared without eligibility policy

## Non-Claims

- KAN-048 does not add a release-blocking performance threshold.
- KAN-048 does not run slow benchmarks as required CI gates.
- KAN-048 does not count estimated, missing, derived, or unavailable payloads as measured.
- KAN-048 does not claim GPU timing when adapter identity is missing.
- KAN-048 does not change renderer, shader, cache runtime, or correctness thresholds.
