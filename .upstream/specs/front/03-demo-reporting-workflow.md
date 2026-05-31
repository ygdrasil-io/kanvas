# Demo And Reporting Workflow

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Define the front-facing workflow for PM demos, milestone closeouts, and release
readiness reviews. This spec owns presentation flow and artifact packaging, not
rendering acceptance rules.

## Current Workflow

The current dashboard workflow is local/static:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Output:

```text
build/reports/wgsl-pipeline-scenes/index.html
```

Milestone reports link the dashboard output and source evidence reports. The
M49 sprint review records the current promoted dashboard counters and the
release workflow:

```text
reports/wgsl-pipeline/2026-05-31-m49-sprint-review.md
```

Generated evidence rows are exported by:

```bash
rtk ./gradlew --no-daemon pipelineGeneratedSceneExport
```

`pipelineSceneDashboard` depends on that exporter and merges generated rows
with static rows before writing the dashboard.

M49 adds the portable PM package command:

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
```

Output:

```text
build/reports/wgsl-pipeline-pm-bundle/
```

The bundle is documented by:

```text
reports/wgsl-pipeline/2026-05-31-m49-portable-pm-bundle.md
```

## PM Demo Package

The current PM demo package is `pipelinePmBundle`. It should continue to
contain:

- dashboard export path or deployed URL;
- scene count summary;
- status count summary;
- generated/static evidence count summary;
- changed rows for the milestone;
- expected unsupported rows and reasons;
- validation commands and results;
- linked reports for each changed scene family;
- raw artifact root for reviewers.

The M49 bundle also includes dashboard data, copied artifacts, source reports,
gate output, a manifest, limitations, unavailable reference checks, and local
serve instructions.

The package should avoid rendering implementation details unless they explain a
support claim, gap, or refusal.

## Review Flow

1. Generate the dashboard or PM bundle.
2. Open the dashboard export or bundle dashboard.
3. Verify summary counters match the milestone report and manifest.
4. Filter to changed rows or relevant tags.
5. Inspect one pass row and one expected-unsupported row.
6. Confirm raw artifact links are reachable.
7. Capture screenshot or attach the static export or PM bundle path.
8. Record validation commands in the milestone closeout.

## Manifest And Front Summary

Do not introduce a competing `front-summary.json` while
`pipelinePmBundle/manifest.json` owns portable PM summary metadata. The manifest
is the current front/readiness summary and should remain the first integration
target for non-technical reviewers.

The M49 manifest includes or is expected to preserve:

```json
{
  "generatedBy": "pipelinePmBundle",
  "generationCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
  "serveCommand": "python3 -m http.server 8765 --bind 127.0.0.1 --directory build/reports/wgsl-pipeline-pm-bundle/dashboard",
  "dashboardEntry": "dashboard/index.html",
  "mergedSceneJson": "dashboard/data/scenes.json",
  "gateReport": "gate/scene-dashboard-gate.md",
  "counters": {
    "total": 23,
    "statuses": {
      "expected-unsupported": 5,
      "pass": 18
    },
    "maturity": {
      "maturity.generated-evidence": 21,
      "maturity.static-evidence": 2,
      "maturity.adapter-backed": 7
    },
    "adapterBacked": 7,
    "expectedUnsupported": 5,
    "unavailableReferences": 0
  },
  "expectedUnsupportedRows": [],
  "adapterBackedRows": [],
  "knownLimitations": [],
  "unavailableReferences": []
}
```

If a future front-specific summary is still needed, it must either be generated
from this manifest or state the fields it adds beyond the PM bundle contract.
Changed rows should be derived from explicit milestone metadata when present.
If milestone metadata is absent, they should be derived from scene tags or a
stored previous export diff, and the report should name the derivation method.
Changed rows must not be inferred from pass/fail status alone.

The manifest is a front/readiness summary. It does not replace raw scene
evidence, gate reports, or rendering reports.
The zero `tracked-gap` and zero `fail` promoted-dashboard guarantees are
verified by `pipelineSceneDashboardGate` and the M49 checklist; consumers should
not require absent zero-count status keys in the manifest.

## Deployable Static Artifact

The current release workflow publishes or archives the M49 PM bundle:

```text
build/reports/wgsl-pipeline-pm-bundle/
```

with:

- `dashboard/index.html`;
- `dashboard/data/scenes.json`;
- `dashboard/artifacts/`;
- `reports/`;
- `gate/`;
- `manifest.json`;
- `README.md`.

The artifact should be reviewable without running Gradle locally.

## Demo Script Shape

Each milestone demo should be short and repeatable:

1. Show top-level counts.
2. Filter to the milestone tag or changed rows.
3. Open a passing row and inspect reference/CPU/GPU/diff panels.
4. Open an expected-unsupported row and show the stable refusal reason.
5. Show raw artifact links.
6. State remaining gaps without hiding them.

## Non-Goals

- Do not make PM reports the source of truth for support claims.
- Do not require a live server for local review.
- Do not edit generated artifacts from the frontend.
- Do not add unsupported row suppression for cleaner demos.
- Do not fork the PM bundle manifest into a second summary format without a
  field-level reason.

## Acceptance Criteria

- A reviewer can reproduce the dashboard from one documented command.
- A PM can reproduce the portable bundle from `pipelinePmBundle`.
- A PM can understand readiness counters without reading raw JSON.
- Milestone reports link the dashboard and source reports.
- Expected unsupported rows are included in demo flow when relevant.
- The PM bundle can be archived as a release-review artifact.
