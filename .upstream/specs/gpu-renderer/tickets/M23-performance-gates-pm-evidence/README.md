# M23 - Performance Gates + PM Evidence

## Goal

Deliver per-family performance budgets, pipeline cache telemetry, frame gate policy, final PM evidence bundle, and complete scene catalog. Final milestone closing M12-M23 production activation wave.

## Dependencies

Depends on all preceding milestones (M12-M22) for route activation evidence. Final milestone - sequential after all wave milestones complete.

## Exit Criteria

- [ ] Per-family performance budgets defined and measured
- [ ] Pipeline cache telemetry shows healthy hit rates
- [ ] Frame gate policy enforces 60fps target with regression quarantine
- [ ] Final PM evidence bundle proves all families activated with green gates
- [ ] All 45+ scenes render correctly in offscreen and windowed modes

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M23-001 - Add per-family performance budgets: measured FPS/ms for each draw family](KGPU-M23-001-per-family-budgets.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `performance-validation` | [] | null |
| [KGPU-M23-002 - Add pipeline cache telemetry: hit rate, eviction, module count per scene](KGPU-M23-002-pipeline-cache-telemetry.md) | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `performance-validation` | [KGPU-M12-010] | null |
| [KGPU-M23-003 - Add frame gate policy: 60fps target, 30fps warning, quarantine on regression](KGPU-M23-003-frame-gate-policy.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `performance-validation` | [KGPU-M23-001] | null |
| [KGPU-M23-004 - Add final PM evidence bundle: all families activated, gates green, rollback tested](KGPU-M23-004-pm-evidence-bundle.md) | `proposed` | `P0` | `PolicyGated` | `CPUReferenceOnly` | `false` | `true` | `validation-pm` | [KGPU-M13-004, KGPU-M14-003, KGPU-M15-004, KGPU-M16-004, KGPU-M17-005, KGPU-M18-005, KGPU-M19-004, KGPU-M20-005, KGPU-M21-004, KGPU-M22-004] | pipelinePmBundle |
| [KGPU-M23-005 - Add gpu-renderer-scenes final catalog: all 45+ scenes render offscreen + windowed](KGPU-M23-005-scenes-catalog.md) | `proposed` | `P0` | `ImplementationCandidate` | `GPUNative` | `false` | `true` | `scenes-evidence` | [KGPU-M23-004] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerformanceBudget*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PipelineCacheTelemetry*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FrameGate*'
rtk ./gradlew --no-daemon pipelinePmBundle
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Non-Claims

- Not a release-blocking gate on non-Apple platforms
- Hardware baseline: Apple M-series only
- No cross-platform performance evidence
- No interactive scene editing

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
