# M49 CI Dashboard Validation Task

Date: 2026-05-31
Linear: GRA-289
Parent epic: GRA-287
Depends on: GRA-288

## Scope

GRA-289 adds a CI-friendly release-gate candidate for the merged WGSL scene
dashboard.

New Gradle tasks:

| Task | Purpose | Output |
|---|---|---|
| `pipelineSceneDashboardGate` | Validates the merged static/generated scene dashboard against the M49-A must-fail and must-warn invariants. | `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.md`, `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.json` |
| `pipelineSceneDashboardGateNegativeFixture` | Focused negative fixture proving the `fallback.support` rule catches a pass-row fallback regression. | `build/reports/wgsl-pipeline-scene-gate-negative/negative-fixture.md` |

The gate runs without a GPU adapter. It validates generated files and checked-in
artifacts after `pipelineSceneDashboard` materializes the merged export.

## Current Result

`pipelineSceneDashboardGate` passes on the current dashboard:

| Signal | Count |
|---|---:|
| Total rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| Adapter-backed rows | 2 |
| `maturity.generated-evidence` | 21 |
| `maturity.static-evidence` | 2 |
| Failures | 0 |

Warnings are limited to estimated performance fields on existing rows. These are
expected and non-blocking until M49-E defines the performance trend gate policy.

## CI Recommendation

Add `pipelineSceneDashboardGate` to CI immediately after `pipelineSceneDashboard`
or as a replacement release-gate command once M49-C confirms the portable bundle
uses the same merged dashboard output.

Keep `pipelineSceneDashboardGateNegativeFixture` as a local/focused verification
task, not a normal CI step, unless CI wants explicit regression-fixture coverage
for every build.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelineSceneDashboardGateNegativeFixture
```
