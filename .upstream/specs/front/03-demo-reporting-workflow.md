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

The historical portable PM package, its manifest, and the associated M49 report
were retired from the working tree. Recover them from Git history only when
reviewing historical evidence; they are not current commands or acceptance
criteria.

## PM Demo Inputs

Current PM review uses the dashboard export plus the focused, headless
evidence validators. The review inputs should contain or link:

- dashboard export path or deployed URL;
- scene count summary;
- status count summary;
- generated/static evidence count summary;
- changed rows for the milestone;
- expected unsupported rows and reasons;
- validation commands and results;
- linked reports for each changed scene family;
- raw artifact paths for reviewers.

The package should avoid rendering implementation details unless they explain a
support claim, gap, or refusal.

## Review Flow

1. Generate the dashboard and relevant focused validators.
2. Open the dashboard export.
3. Verify summary counters match the milestone report and source evidence.
4. Filter to changed rows or relevant tags.
5. Inspect one pass row and one expected-unsupported row.
6. Confirm raw artifact links are reachable.
7. Capture a screenshot or attach the static export path.
8. Record validation commands in the milestone closeout.

## Dashboard And Focused Evidence Summary

There is no root PM manifest contract. The dashboard export is the front
summary, while each focused headless validator owns its own evidence schema and
generation command. A future front-specific summary must state which dashboard
and evidence fields it adds; changed rows must come from explicit milestone
metadata, scene tags, or a stored export diff, never pass/fail status alone.

The zero `tracked-gap` and zero `fail` promoted-dashboard guarantees remain
owned by `pipelineSceneDashboardGate`; raw scene evidence and focused validator
reports remain the source for their respective claims.

## Deployable Static Artifact

The dashboard export is available at:

```text
build/reports/wgsl-pipeline-scenes/
```

It should be reviewed alongside the focused, headless validator output rather
than copied into a root package.

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
- Do not reintroduce a root PM manifest without an approved architecture
  decision.

## Acceptance Criteria

- A reviewer can reproduce the dashboard from one documented command.
- A PM can reproduce the dashboard and focused evidence from their documented
  commands.
- A PM can understand readiness counters without reading raw JSON.
- Milestone reports link the dashboard and source reports.
- Expected unsupported rows are included in demo flow when relevant.
- The dashboard export and focused evidence may be archived independently as
  release-review artifacts.
