# M50-A Required CI Ownership

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Release Gate

`pipelineSceneDashboardGate` is now the release-visible WGSL scene dashboard
gate. The accepted CI path is:

- Workflow: `.github/workflows/test.yml`
- Job: `wgsl_scene_dashboard_release_gate`
- Command: `./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle`
- Uploaded artifact: `wgsl-scene-dashboard-release-gate`

The job uploads these report roots:

- `build/reports/wgsl-pipeline-scenes/`
- `build/reports/wgsl-pipeline-scene-gate/`
- `build/reports/wgsl-pipeline-front-qa/`
- `build/reports/wgsl-pipeline-performance-warnings/`
- `build/reports/wgsl-pipeline-pm-bundle/`

## Gate Invariants

The gate fails on duplicate scene ids, missing required support artifacts,
missing route diagnostics, missing stats, pass rows whose GPU fallback is not
`none`, expected-unsupported rows without stable non-`none` fallback reasons,
unexpected `tracked-gap`, and unexpected `fail`.

New M50 font/text refusal rows are allowlisted with stable fallback reasons:

- `font-emoji-color-glyph-refusal`: `font.color-glyph-emoji-unsupported`
- `font-complex-shaping-refusal`: `font.complex-shaping-requires-explicit-shaper`

The GPU inventory remains non-blocking and visible through the existing
`gpu_inventory` job in `.github/workflows/test.yml`; the owning release note is
the M50 checklist.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```
