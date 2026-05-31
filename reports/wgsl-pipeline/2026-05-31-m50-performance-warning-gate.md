# M50-E Performance Warning Gate

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Task

`pipelinePerformanceTrendWarnings` emits warning-only trend evidence from the
merged dashboard:

- Markdown: `build/reports/wgsl-pipeline-performance-warnings/performance-warnings.md`
- JSON: `build/reports/wgsl-pipeline-performance-warnings/performance-warnings.json`
- PM bundle path: `build/reports/wgsl-pipeline-pm-bundle/performance/`

## Policy

- Owner: Kanvas rendering release owner.
- Baseline owner: Kanvas rendering release owner.
- Mode: warning-only.
- Variance review band: 15% median or 20% p95 across matching baselines.
- Quarantine: adapter, backend, host, JDK, or variance mismatches remain visible
  and do not move readiness until rerun under matching conditions.
- Rollback: correctness regressions roll back the rendering change; performance
  warnings need owner triage before becoming release-blocking.

No performance threshold is release-blocking in M50.

## Refreshed Rows

The refreshed measured rows are:

- CPU: `src-over-stack`, `bitmap-shader-local-matrix`
- GPU/cache: `src-over-stack`, `bitmap-shader-local-matrix`

Each measured payload records host, OS, JDK, backend, adapter when applicable,
warm/cold class, sample count, baseline id/owner, gate mode, quarantine policy,
rollback policy, and variance policy.

## Validation

```bash
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
