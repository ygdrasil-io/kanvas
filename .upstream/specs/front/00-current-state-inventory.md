# Front Current State Inventory

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`

## Purpose

Record the front-facing evidence surface that already exists and the remaining
front gaps. This file includes completed work only when it is part of the
target front experience.

Rendering support status, route correctness, benchmark rules, and scene
promotion policy remain owned by the rendering specs.

## Existing Front Surfaces

The current dashboard source is committed as:

```text
reports/wgsl-pipeline/scenes/index.html
```

It is exported by:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

to the generated review artifact:

```text
build/reports/wgsl-pipeline-scenes/index.html
```

The dashboard reads:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

and linked scene artifacts under:

```text
reports/wgsl-pipeline/scenes/artifacts/
reports/wgsl-pipeline/scenes/generated/artifacts/
```

Generated rows are materialized by `pipelineGeneratedSceneExport` before the
merged dashboard export.

M49 also adds two front-facing release surfaces:

| Surface | Command | Output |
|---|---|---|
| Dashboard release gate | `rtk ./gradlew --no-daemon pipelineSceneDashboardGate` | `build/reports/wgsl-pipeline-scene-gate/` |
| Portable PM bundle | `rtk ./gradlew --no-daemon pipelinePmBundle` | `build/reports/wgsl-pipeline-pm-bundle/` |

The PM bundle contains a copied dashboard, data, artifacts, source reports,
gate reports, a manifest, and local serve instructions.

## Current Data Volume

As of the M49 sprint review
(`reports/wgsl-pipeline/2026-05-31-m49-sprint-review.md`) and release
readiness checklist
(`reports/wgsl-pipeline/2026-05-31-m49-mep-release-readiness-checklist.md`),
the promoted dashboard has:

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 21 |
| Static evidence rows | 2 |
| Adapter-backed rows | 7 |
| PM bundle unavailable dashboard references | 0 |

These are front display counters. They do not broaden rendering support claims.
The M48 readiness sync
(`reports/wgsl-pipeline/2026-05-31-m48-dashboard-readiness-sync.md`) remains
historical provenance for the 23/18/5/0/0 dashboard posture.

## Already Implemented Front Behavior

The static dashboard currently provides:

- a PM-readable summary area with source, export path, and validation command;
- reference guidance explaining Skia/upstream, CPU oracle, and diff panels;
- route diagnostic guidance explaining CPU route, GPU route, fallback, tracked
  gap, and expected unsupported;
- filters for status, priority, reference kind, GPU fallback, performance
  state, tag, and text search;
- exact tag aggregate display for visible scenes;
- scene cards with status, reference kind, CPU/GPU route summaries, fallback
  reason, blocker/follow-up fields, performance trend text, and evidence links;
- visual panels for reference, CPU image, GPU image, CPU diff, and GPU diff;
- missing-artifact panels that show absence explicitly instead of collapsing the
  UI;
- responsive single-column behavior on narrow screens;
- static operation with no backend service requirement.

The M49 release workflow additionally provides:

- `pipelineSceneDashboardGate` with machine-checkable dashboard invariants;
- `pipelineSceneDashboardGateNegativeFixture` to prove unsafe support-claim
  regressions fail;
- `pipelinePmBundle` with dashboard, data, artifacts, reports, gate output,
  manifest, limitations, and serve instructions.

## Existing Front Inputs

The front consumes these source concepts:

| Input | Front responsibility |
|---|---|
| `schemaVersion` | Detect and adapt known scene registry shapes. |
| `scenes[]` | Render cards, filters, aggregates, visual panels, and links. |
| `status` | Display exactly; do not reinterpret support state. |
| `priority` | Filter and sort context only. |
| `referenceKind` | Explain whether the row uses upstream/Skia or CPU oracle evidence. |
| `cpu`, `gpu` | Render lane summaries and artifact links. |
| `routeDiagnostics` | Link raw diagnostics and display selected summaries. |
| `performanceTrend` | Display measured, estimated, or unavailable trend state. |
| `tags` | Exact filtering and aggregate summaries. |
| `evidence` | PM/reviewer traceability links. |

The front does not define the allowed scene statuses, route strings, fallback
reason catalog, thresholds, or tag taxonomy. It only displays them and validates
that required display fields are present.

## Gaps

The current front is useful but not final:

- no dedicated scene detail page or deep link per scene;
- no side-by-side zoom/pan/diff inspection tool;
- no persisted filter URL state;
- no formal keyboard-navigation pass;
- no automated browser screenshot or accessibility audit;
- no explicit visual regression snapshots for the dashboard itself;
- no separate front view-model adapter module; the HTML currently reads the
  scene registry directly;
- no front-owned changelog that summarizes UI changes separately from rendering
  evidence;
- no front-specific summary beyond the M49 PM bundle manifest.

## Non-Goals

- Do not replace `scenes.json` with a front-owned data format.
- Do not move generated evidence production into the frontend.
- Do not make the dashboard a rendering editor.
- Do not hide expected unsupported rows by default.
- Do not add a marketing/landing page in front of the evidence surface.

## Acceptance Baseline

The current front baseline remains valid when:

- `pipelineSceneDashboard` exports `build/reports/wgsl-pipeline-scenes/index.html`;
- `pipelineSceneDashboardGate` validates the promoted dashboard without
  failures;
- `pipelinePmBundle` exports `build/reports/wgsl-pipeline-pm-bundle/` with a
  manifest and no unavailable dashboard references;
- scene rows render without script errors;
- expected unsupported rows are visible by default;
- filters update the visible set and aggregate counts;
- artifact links are rendered as inspectable paths;
- missing artifacts are displayed as missing, not silently omitted.
