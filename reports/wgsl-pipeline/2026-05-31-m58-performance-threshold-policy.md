# M58 Performance Threshold Policy

Result: pass.

M58 uses the M55 candidate thresholds as release-blocking thresholds only for
selected measured lanes.

## Thresholds

| Metric | Threshold |
|---|---:|
| Median timing | measured baseline plus 15% |
| P95 timing | measured baseline plus 20% |
| Minimum sample count | 30 |

The selected measured thresholds are encoded as explicit `thresholdMedianMs`
and `thresholdP95Ms` values in
`reports/wgsl-pipeline/performance/m58-performance-release-gate.json`.

## Blocking Eligibility

A lane is eligible to block release only when all of these are true:

- the M58 contract marks the lane `releaseBlocking=true`;
- the dashboard `performanceTrend.status` is `measured`.

## Blocking Rules

An eligible measured lane fails the release gate when either condition is true:

- required host, JDK, backend, adapter for GPU/cache, baseline, sample count,
  median, p95, or threshold metadata is missing;
- median or p95 exceeds the explicit threshold.

## Negative Fixture

The deterministic failure path is:

```bash
rtk ./gradlew --no-daemon -Pkanvas.performance.releaseGate.negativeFixture=true pipelinePerformanceReleaseGate
```

The fixture lowers the `src-over-stack` CPU median threshold below the measured
median during the task run. The task must fail and write the same JSON/Markdown
report shape with `negativeFixture=true` and at least one blocking failure.

## Non-Blocking Rules

Estimated, missing, and intentionally deferred lanes are reported as
`not-measured`. They do not become release-blocking measured evidence and do
not satisfy measured readiness claims.

## Rebaseline

A threshold change requires owner approval, matching host/JDK/backend/adapter
eligibility, at least three consecutive stable runs, and a PM report with
before/after median and p95 values.
