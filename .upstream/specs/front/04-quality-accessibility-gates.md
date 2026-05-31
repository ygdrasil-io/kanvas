# Front Quality And Accessibility Gates

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`

## Purpose

Define quality gates for the front-facing evidence surface. These gates ensure
the dashboard remains readable, navigable, and reviewable as evidence grows.

## Required Local Validation

Every front change must run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Release-gate or promoted-dashboard changes must also run:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```

PM packaging changes must also run:

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
```

If generated evidence behavior is touched, also run:

```bash
rtk ./gradlew --no-daemon pipelineGeneratedSceneExport pipelineSceneDashboard
```

## Manual Browser Check

Until browser automation is added, front changes should manually check:

- dashboard opens from `build/reports/wgsl-pipeline-scenes/index.html`;
- no obvious script errors;
- filters work independently and together;
- image panels keep stable layout;
- missing panels are visible;
- expected unsupported rows are visible by default;
- artifact links are clickable;
- narrow viewport collapses into one column without overlapping text.

## Accessibility Requirements

The dashboard should satisfy:

- one logical `h1`;
- section headings in order;
- form controls have visible labels;
- images have useful alt text;
- status is not conveyed by color alone;
- keyboard focus can reach filters, links, and details;
- contrast is sufficient for text, badges, and paths;
- dynamic updates use a polite live region or equivalent;
- long paths and diagnostic codes wrap instead of overflowing.

## Responsive Requirements

The dashboard must remain readable at:

- desktop width;
- tablet width;
- mobile width near 360px.

Rules:

- no horizontal scrolling for primary content;
- image panels maintain stable dimensions;
- filter controls stack cleanly;
- long scene ids, routes, and paths wrap;
- status badges do not overlap scene titles.

## Artifact QA

The front export and PM bundle are valid only when:

- every required pass-row artifact exists;
- expected unsupported rows explain missing GPU artifacts when absent;
- raw JSON links are present for route diagnostics when source data provides
  them;
- image links resolve inside the exported artifact directory;
- source reports are linked as relative repository paths;
- the PM bundle manifest reports no unavailable dashboard references;
- the PM bundle includes dashboard data, artifacts, reports, gate output, and
  local open instructions.

Artifact existence validation is owned by the Gradle export tasks. The frontend
must still render missing states defensively.

## Current Release Gate

M49 implements the current release gate through:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```

The gate is documented by:

```text
reports/wgsl-pipeline/2026-05-31-m49-dashboard-gate-invariants.md
reports/wgsl-pipeline/2026-05-31-m49-ci-dashboard-validation-task.md
```

The gate owns machine-checkable dashboard invariants such as:

- allowed row statuses;
- 0 promoted `tracked-gap` rows;
- 0 promoted `fail` rows;
- required artifacts for `pass` rows;
- stable fallback reasons for `expected-unsupported` rows;
- duplicate id prevention;
- parseable tag and route diagnostic requirements.

Performance trend data remains non-blocking under the M49 contract:

```text
reports/wgsl-pipeline/2026-05-31-m49-performance-trend-gate-contract.md
```

The front must display performance trend state without presenting reporting-only
measurements as release-blocking thresholds.

## Future Automated Gates

Recommended future gates beyond the current M49 gate:

- browser screenshot test for desktop and mobile;
- accessibility scan with a no-critical-violations threshold;
- UI regression snapshots for status badges, filters, visual panels, and empty
  state;
- JavaScript unit test for view-model adapter and filter composition.

## Release Gate

A release-ready front export must prove:

- `pipelineSceneDashboard` passed;
- `pipelineSceneDashboardGate` passed;
- `pipelinePmBundle` passed when producing PM/release artifacts;
- dashboard artifact is archived or linked;
- summary counters match the PM closeout;
- filters and expected unsupported visibility were manually or automatically
  checked;
- no pass row has a missing required visual or raw artifact;
- no frontend copy overclaims rendering support.

## Non-Goals

- Do not block rendering implementation on unavailable browser automation.
- Do not make visual polish override evidence clarity.
- Do not introduce a frontend build system until the static dashboard outgrows
  a single-file artifact.
- Do not add third-party UI packages, accessibility widgets, charting
  frameworks, or client-side routers for this front scope.
