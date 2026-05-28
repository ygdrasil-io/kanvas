# M43 Measured Benchmark Payload

Date: 2026-05-28
Linear: GRA-206

## Purpose

M43 measured performance rows use the existing dashboard `performanceTrend` field with a stricter `status=measured` contract. Static `estimated` rows remain informational, and `unavailable` rows remain valid when no benchmark output exists.

## Required Measured Fields

A measured row must include:

- `status=measured`
- `command`
- `phase`: `cold`, `warm`, or `mixed`
- `sampleCount > 0`
- `timing.medianMs` and `timing.p95Ms`
- `environment.host`, `environment.jdk`, `environment.commit`, `environment.backend`
- `counters`
- `baseline.name` and `baseline.commit`
- `regression.label`: `none`, `improved`, `regressed`, or `unknown`
- `gate.status`: `reporting-only`, `candidate`, or `required`
- `rawMetrics`

Measured GPU rows additionally require `environment.adapter`. If adapter identity is missing, the row must be `unavailable` with reason `gpu.adapter-missing`, not `measured`.

## Coexistence Policy

- `estimated`: static seed data; visible but non-gating.
- `measured`: benchmark-produced evidence; reportable, but not automatically a CI gate.
- `unavailable`: no timing data; must include a stable reason.

## Gate Policy

All M43 measured rows are `gate.status=reporting-only` until a later ticket defines CI budget, host/adapter eligibility, flake handling, rollback/quarantine behavior, and baseline ownership.

## Validation

```bash
rtk git diff --check
```
