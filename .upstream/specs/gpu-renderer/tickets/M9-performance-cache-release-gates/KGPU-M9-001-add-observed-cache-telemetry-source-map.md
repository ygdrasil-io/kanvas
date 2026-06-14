---
id: KGPU-M9-001
title: "Add observed cache telemetry source map"
status: proposed
milestone: M9
priority: P0
owner_area: telemetry-cache
claim_impact: PolicyGated
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "cache reporting-only"
---

# KGPU-M9-001 - Add observed cache telemetry source map

## PM Note

Ce ticket sépare les compteurs observés des ledgers dérivés.

## Problem

Cache readiness cannot move from derived or observed-partial counters.

## Scope

- Map telemetry counters to source artifacts.
- Classify observed, observed-partial, derived, unavailable, and reporting-only.

## Non-Goals

- Do not create release-blocking gates.
- Do not synthesize counters from comments or reports.

## Spec Sources

- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`

## Design Sketch

```kotlin
data class CacheTelemetrySource(val counter: String, val classification: String)
```

## Acceptance Criteria

- [ ] Every counter has a named source.
- [ ] Derived counters cannot count as observed readiness.
- [ ] Unavailable counters remain visible.

## Required Evidence

- Source-map report, PM manifest row, and validator output.

## Fallback / Refusal Behavior

Missing observed source keeps the gate reporting-only.

## Dashboard Impact

- Expected row: `gpu-renderer.cache-telemetry-source-map`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Telemetry classification first.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:telemetry`
