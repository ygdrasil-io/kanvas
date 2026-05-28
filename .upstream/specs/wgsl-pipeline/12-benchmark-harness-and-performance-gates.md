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

## Gate Policy

No benchmark gate may be required in CI unless it has:

- explicit time budget;
- host/adapter eligibility;
- flake handling;
- rollback or quarantine process;
- owner for baseline updates.

Until those exist, measured values are reporting evidence rather than required
release gates.

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
