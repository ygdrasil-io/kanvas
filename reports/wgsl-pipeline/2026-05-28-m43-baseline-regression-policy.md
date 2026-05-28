# M43-D baseline and non-gating regression policy

GRA-209 keeps M43 benchmark output measurable but non-blocking. CPU and GPU/cache
rows now have explicit baseline names and regression labels, but no required CI
performance gate is added.

## Current measured baselines

| Lane | Baseline | Producer | Rows | Gate |
| --- | --- | --- | --- | --- |
| CPU | `m43-cpu-measured-local` | `pipelineMeasuredCpuPerformance` | `src-over-stack`, `bitmap-shader-local-matrix` | `reporting-only` |
| GPU/cache | `m43-gpu-cache-measured-local` | `pipelineMeasuredGpuPerformance` | `src-over-stack`, `bitmap-shader-local-matrix` | `reporting-only` |

All measured rows currently use `regression.label=unknown` because M43 created
initial local baselines rather than an approved CI comparison set.

## Policy

- `estimated` rows remain static informational seeds and cannot be regression gates.
- `measured` rows are reportable evidence only while `gate.mode=reporting-only`.
- GPU rows require a named adapter; adapter-missing runs must report
  `status=unavailable` and `reason=gpu.adapter-missing`.
- Baseline names starting with `m43-` are local milestone evidence, not CI-owned
  release gates.
- Required gates need a separate ticket with budget, host/adapter eligibility,
  variance threshold, flake handling, quarantine, rollback, and baseline owner.

## Future gate ticket criteria

A future gate ticket must define:

- exact CI lane and timeout budget;
- allowed host, JDK, backend, and GPU adapter set;
- sample count and threshold calculation;
- retry and quarantine rules for noisy measurements;
- rollback criteria if the gate blocks unrelated changes;
- baseline update ownership and review process;
- evidence from at least three consecutive stable CI runs.

## Validation

```bash
rtk git diff --check
```

Documentation/spec review:

- `.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`
- `reports/wgsl-pipeline/2026-05-28-m43-baseline-regression-policy.md`
