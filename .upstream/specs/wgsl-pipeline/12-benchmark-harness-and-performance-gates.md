# Benchmark Harness And Performance Gates

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`
Milestone: M43 -- Real Benchmark Harness

## Purpose

Replace static `estimated` dashboard performance seeds with measured CPU/GPU
benchmark output when a reproducible harness owns the environment, samples,
baselines, and regression labels.

M40 made performance fields visible. M43 makes selected fields measurable.

## Performance Status

`performanceTrend.status` values:

- `unavailable`: no timing data exists; requires a reason.
- `estimated`: static or derived value; informational only.
- `measured`: produced by an approved benchmark harness with environment
  metadata and baseline context.

Only `measured` values can participate in regression gates.

## Required Measured Payload

Measured CPU/GPU performance entries must include:

- command or benchmark task;
- commit or baseline name;
- host OS and architecture;
- JDK version;
- backend name;
- adapter identity for GPU rows;
- warm/cold phase when relevant;
- sample count;
- median and p95 timings;
- counters relevant to the route;
- regression label;
- rollback or quarantine policy for gates.

The dashboard-level `performanceTrend` payload is the stable interchange
contract. Benchmark exporters may write extra raw fields, but the scene row must
map to this shape:

```json
{
  "status": "measured",
  "command": "rtk ./gradlew --no-daemon <benchmark-task>",
  "phase": "warm",
  "sampleCount": 30,
  "timing": {
    "medianMs": 0.42,
    "p95Ms": 0.58,
    "minMs": 0.39,
    "maxMs": 0.66
  },
  "environment": {
    "host": "macOS 15.5 arm64",
    "jdk": "25.0.1",
    "commit": "<git-sha>",
    "backend": "CPU"
  },
  "counters": {
    "frames": 30,
    "pixels": 4096
  },
  "baseline": {
    "name": "m43-initial",
    "commit": "<git-sha>",
    "medianMs": 0.42,
    "p95Ms": 0.58
  },
  "regression": {
    "label": "none",
    "thresholdPct": 15.0
  },
  "gate": {
    "status": "reporting-only",
    "reason": "No CI budget or quarantine policy yet."
  },
  "rawMetrics": "artifacts/<scene>/cpu-performance.json"
}
```

Required `measured` fields:

- `status=measured`;
- non-empty `command`;
- `phase` set to `cold`, `warm`, or `mixed`;
- `sampleCount > 0`;
- numeric `timing.medianMs` and `timing.p95Ms`;
- `environment.host`, `environment.jdk`, `environment.commit`, and
  `environment.backend`;
- non-empty `counters`;
- `baseline.name` and `baseline.commit`;
- `regression.label` set to `none`, `improved`, `regressed`, or `unknown`;
- `gate.status` set to `reporting-only`, `candidate`, or `required`;
- `rawMetrics` path to the raw JSON artifact.

## CPU Metrics

CPU measurements should identify:

- scalar or Java 25 Vector plan;
- route identity;
- image dimensions or pixel count;
- allocation or buffer reuse counters when available;
- whether Java 25 Vector is enabled.

Java 25 Vector remains optional and must never become the correctness path.

## GPU Metrics

GPU measurements should identify:

- adapter;
- backend;
- pipeline key or module identity;
- shader/module cache hit and miss counts;
- bind group/resource counters where available;
- warm pipeline-cache behavior.

Adapter-missing environments must remain valid for dashboard generation but
cannot claim measured GPU performance.

Measured GPU rows additionally require:

- `environment.adapter` with the user-visible adapter name;
- `environment.backend=WebGPU`;
- route-specific counters for pipeline cache or module cache when available;
- a raw metrics path that was written by an adapter-backed run.

If adapter identity is missing, the row must use:

```json
{
  "status": "unavailable",
  "reason": "gpu.adapter-missing"
}
```

It must not use `status=measured`.

## Gate Policy

No benchmark gate may be required in CI unless it has:

- explicit time budget;
- host/adapter eligibility;
- flake handling;
- rollback or quarantine process;
- owner for baseline updates.

Until those exist, measured values are reporting evidence rather than required
release gates.

`estimated`, `measured`, and `unavailable` may coexist in the same dashboard:

- `estimated` rows remain static informational seeds and cannot be compared
  against measured rows for gate decisions.
- `measured` rows are produced by a benchmark command and may be reported in
  PM evidence.
- `unavailable` rows must include a stable `reason` and may not carry timing
  fields.

M43 gate status is `reporting-only` for all measured rows unless a follow-up
ticket explicitly adds host/adapter eligibility, budget, flake handling,
rollback, and baseline-owner rules.

## Validation

At minimum:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Benchmark-producing tickets must also run the owning benchmark command and link
the raw metric JSON files.

## Non-Goals

- Do not turn M40 `estimated` values into gates.
- Do not compare GPU numbers across unnamed adapters.
- Do not make Java 25 Vector a correctness dependency.
- Do not add slow benchmark tasks to required CI without explicit budget.

## Baseline And Regression Policy

M43 measured payloads use explicit baseline names so reviewers can distinguish
local milestone evidence from future CI-owned gates:

- `m43-cpu-measured-local`: first local measured CPU dashboard payloads written
  by `pipelineMeasuredCpuPerformance`.
- `m43-gpu-cache-measured-local`: first local measured GPU/cache dashboard
  payloads written by `pipelineMeasuredGpuPerformance` on a named adapter.
- Future CI-owned baselines must use a new name, for example
  `ci-macos-m2max-webgpu-v1`, and must not silently overwrite M43 local
  evidence.

Regression labels are dashboard evidence labels, not release decisions by
themselves:

| Label | Meaning | Gate effect in M43 |
| --- | --- | --- |
| `unknown` | No approved comparable baseline exists, or the metric was collected under a different host/JDK/adapter eligibility set. | Reporting-only. |
| `none` | Comparable measured value is within the approved threshold. | Candidate only after CI gate criteria are met. |
| `improved` | Comparable measured value is better than the approved threshold. | Candidate only after CI gate criteria are met. |
| `regressed` | Comparable measured value exceeds the approved threshold. | Reporting-only in M43; future gates may fail once criteria below are satisfied. |

A measured row is informational when any of these is true:

- `gate.mode` is `reporting-only`;
- baseline name starts with `m43-` and was produced by a local/manual run;
- GPU adapter identity is absent or differs from the baseline eligibility;
- sample count, host class, JDK, or backend differ from the approved baseline;
- no owner exists for baseline updates and quarantine decisions.

A measured row may become a required CI gate only after a follow-up ticket adds
all of the following:

- explicit per-task time budget and timeout;
- host/JDK/backend/adapter eligibility matrix;
- minimum sample count and allowed variance threshold;
- flake retry, quarantine, and escalation rules;
- rollback rule for removing or relaxing a noisy gate;
- named owner for baseline update review;
- PM-facing evidence showing at least three consecutive stable runs on the
  intended CI lane.

Until those criteria are implemented, M43 measured rows remain PM/engineering
trend evidence and must not block required CI.
