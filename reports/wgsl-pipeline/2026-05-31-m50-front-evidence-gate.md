# M50-B Front Evidence Gate

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Dashboard Changes

The PM dashboard now keeps image inspection in-page. Reference, CPU, GPU, CPU
diff, and GPU diff panels are buttons that open the preview dialog; there is no
raw image navigation control in the scene card or dialog.

The layout remains two columns on desktop through `.scene-grid` and becomes one
column below 760px. Artifact lists use closed `<details>` blocks by default.

Filters now cover:

- status;
- priority;
- reference source;
- maturity generated/static;
- adapter-backed evidence;
- fallback reason;
- performance status;
- tag and search.

Reference/oracle and route diagnostics notices remain above the filters so PM
reviewers can distinguish Skia/upstream reference, CPU oracle, route selection,
fallback policy, tracked gaps, and expected unsupported rows.

## QA Artifacts

`pipelineDashboardFrontQa` writes:

- `build/reports/wgsl-pipeline-front-qa/front-qa.md`
- `build/reports/wgsl-pipeline-front-qa/front-qa.json`

`pipelinePmBundle` includes that report under `front-qa/` and records the
desktop/mobile screenshot paths in `manifest.json`.

The static QA threshold is 0 critical issues. Browser screenshots for the PM
bundle are:

- `build/reports/wgsl-pipeline-front-qa/screenshots/desktop.png`
- `build/reports/wgsl-pipeline-front-qa/screenshots/mobile.png`

## Validation

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelinePmBundle
```
