# M40 Performance Trend Schema

Date: 2026-05-28
Linear: GRA-176
Milestone: M40 Performance And Regression Dashboard

## Scope

GRA-176 extends the scene evidence dashboard so later M40 tickets can add CPU
and GPU performance data without invalidating existing M36-M39 rows.

Existing scene entries remain valid because `cpu.performanceTrend` and
`gpu.performanceTrend` are optional in M40-A. The static dashboard labels absent
trend data as `unavailable`.

## Schema Contract

Optional lane field:

```json
"performanceTrend": {
  "status": "measured",
  "sampleCount": 30,
  "timing": {
    "medianMs": 0.42,
    "p95Ms": 0.58
  },
  "counters": {
    "routeInvocations": 1,
    "pipelineCacheHits": 1,
    "pipelineCacheMisses": 0,
    "resourceBytes": 0
  },
  "baseline": {
    "name": "m40-initial",
    "commit": "<git-sha>"
  },
  "regression": {
    "label": "none"
  }
}
```

Allowed `status` values:

- `unavailable`: requires `reason` and no timing data.
- `measured`: requires `sampleCount`, `timing.medianMs`, `timing.p95Ms`,
  non-empty `counters`, `baseline.name`, `baseline.commit`, and
  `regression.label`.
- `estimated`: same required fields as `measured`, but labels the data as not a
  release gate.

Allowed regression labels:

- `none`
- `improved`
- `regressed`
- `unknown`

## Validator

`pipelineSceneDashboard` now validates optional `performanceTrend` fields when
present. Missing fields are allowed for current rows. Invalid present fields fail
the dashboard task.

## Static Dashboard

`reports/wgsl-pipeline/scenes/index.html` now displays CPU and GPU performance
trend status per row. Existing rows render as `unavailable` until GRA-188 and
GRA-189 populate measurements.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
