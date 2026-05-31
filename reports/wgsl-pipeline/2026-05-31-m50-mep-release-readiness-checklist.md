# M50 MEP Release Readiness Checklist

Date: 2026-05-31
Milestone: M50 -- MEP Readiness Acceleration Toward 80%

## Required Release Paths

| Purpose | Path |
|---|---|
| CI workflow | `.github/workflows/test.yml` |
| Required job | `wgsl_scene_dashboard_release_gate` |
| Job command | `./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle` |
| Uploaded artifact | `wgsl-scene-dashboard-release-gate` |
| Gate report | `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.md` |
| Gate JSON | `build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.json` |
| Dashboard | `build/reports/wgsl-pipeline-scenes/index.html` |
| PM bundle | `build/reports/wgsl-pipeline-pm-bundle/` |
| PM manifest | `build/reports/wgsl-pipeline-pm-bundle/manifest.json` |
| Front QA | `build/reports/wgsl-pipeline-pm-bundle/front-qa/front-qa.md` |
| Performance warnings | `build/reports/wgsl-pipeline-pm-bundle/performance/performance-warnings.md` |

## Lane Checklist

| Lane | Result | Evidence |
|---|---|---|
| M50-A Required CI ownership | `pass` | `.github/workflows/test.yml`, `reports/wgsl-pipeline/2026-05-31-m50-ci-release-gate.md` |
| M50-B Front evidence gate | `pass` | Dashboard HTML, `pipelineDashboardFrontQa`, `reports/wgsl-pipeline/2026-05-31-m50-front-evidence-gate.md` |
| M50-C Adapter-backed scene expansion V2 | `pass` | 17 adapter-backed rows, `reports/wgsl-pipeline/2026-05-31-m50-adapter-backed-expansion-v2.md` |
| M50-D First font/text evidence pack | `pass` | 3 font pass rows, 2 font expected-unsupported rows, `reports/wgsl-pipeline/2026-05-31-m50-font-text-evidence-pack.md` |
| M50-E Performance warning gate | `pass` | `pipelinePerformanceTrendWarnings`, `reports/wgsl-pipeline/2026-05-31-m50-performance-warning-gate.md` |
| M50-F Closeout and score update | `pending validation` | `reports/wgsl-pipeline/2026-05-31-m50-sprint-review.md` |

## Score Rule

M50 can claim 80% only if closeout validation keeps:

- 28 merged rows;
- 21 pass rows;
- 7 expected-unsupported rows;
- 0 tracked-gap;
- 0 fail;
- at least 14 adapter-backed rows;
- PM bundle with gate, front QA, screenshot paths, performance warnings, and
  no unavailable dashboard references.

If validation lowers any lane, the sprint review must publish the lower score.
