# Scene Dashboard UI

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`
Current implementation: `reports/wgsl-pipeline/scenes/index.html`

## Purpose

Specify the frontend behavior of the scene evidence dashboard. This file owns
the user experience, not the scene evidence schema or rendering support rules.

The dashboard must remain dependency-light and static-artifact friendly. New UI
controls should be implemented with internal TypeScript or plain JavaScript and
CSS unless an existing repository helper already covers the exact behavior.

## Layout Contract

The dashboard should be organized in this order:

1. Evidence summary.
2. How-to-read notices.
3. Filter toolbar.
4. Visible aggregates.
5. Scene cards or scene detail panels.
6. Empty state.

The first viewport should make it clear that this is a review tool for Kanvas
evidence.

## Summary Area

The summary area should show:

- dashboard name;
- source path;
- export path;
- validation task;
- optional readiness counters when available.

The current dashboard already shows source, export, and task cards.

## Guidance Notices

The dashboard should include short notices for:

- reference panels;
- route diagnostics;
- support status meanings;
- performance trend state.

These notices are frontend explanation only. They must not introduce new
support policy.

## Filter Toolbar

The filter toolbar should support:

| Filter | Current status | Target behavior |
|---|---|---|
| Status | Implemented | Exact status match, with `All statuses` default. |
| Priority | Implemented | Exact priority match. |
| Reference | Implemented | Exact `referenceKind` match. |
| GPU fallback | Implemented | Exact fallback reason match. |
| Performance | Implemented | `measured`, `estimated`, or `unavailable`. |
| Tag | Implemented | Exact tag match. |
| Search | Implemented | Case-insensitive search over scene id, title, status, priority, routes, fallback, blocker, follow-up, and tags. |

Future additions:

- URL-persisted filter state;
- clear-all control;
- changed-in-milestone filter;
- adapter-backed filter;
- evidence mode filter: static, generated, mixed.

## Aggregates

Visible aggregates should update after filtering. Current aggregates include
tag groups such as:

- `feature.*`;
- `maturity.*`;
- `risk.*`.

Target aggregates should also expose:

- status counts;
- evidence mode counts;
- reference kind counts;
- GPU fallback reason counts.

Aggregates are navigation aids and summary aids. They are not support claims.

## Scene Card

Each scene card should show:

- title;
- scene id;
- status badge;
- priority;
- reference kind;
- CPU route summary;
- GPU route summary;
- GPU fallback reason;
- blocker and follow-up when present;
- CPU and GPU performance trend text;
- visual panels;
- tags;
- evidence/artifact details.

The current static dashboard implements the core card behavior. The target
should add deep-linkable expanded cards or a scene detail view.

## Visual Panels

Each scene should display panels for:

- reference;
- CPU image;
- GPU image;
- CPU diff;
- GPU diff.

Panel rules:

- image sizing must be stable across scenes;
- pixel-art fixtures should use crisp rendering where useful;
- missing image paths render a visible missing state;
- image links open the artifact;
- alt text identifies the panel.

The UI must not collapse a missing GPU image for `tracked-gap` or
`expected-unsupported` rows. The absence is part of the evidence.

## Artifact Details

Each scene should expose raw links for:

- source report;
- reference image;
- CPU image and diff;
- GPU image and diff;
- CPU route JSON;
- GPU route JSON;
- stats JSON;
- performance raw metrics when present;
- generation evidence when present.

The current dashboard exposes a raw artifact list. The target should group
links by lane and artifact type.

## Empty State

An empty registry should render a deliberate empty state that says the shell is
ready. It should not look like a broken dashboard.

The current dashboard has an empty state. The target should update the wording
so it does not reference obsolete milestone placeholders once the scene pack is
non-empty.

## Error And Missing States

Frontend behavior:

- missing registry: show a front error state with the expected path;
- invalid scene row: the Gradle task should fail before export;
- missing image path in a valid expected-unsupported row: show `not produced`;
- missing image path in a pass row: this should be caught by export validation;
- missing performance trend: show `unavailable`.

## Visual Design Direction

The dashboard is an operational review tool. It should be dense, readable, and
quiet:

- avoid decorative hero treatment that hides data;
- avoid marketing-style cards for sections;
- keep status colors consistent and restrained;
- keep monospace for raw ids, routes, tags, and paths;
- keep body copy short and explanatory;
- make artifact links scannable.

## Acceptance Criteria

- Filters and search compose together.
- Visible aggregate counts update after every filter change.
- Expected unsupported rows are visible by default.
- Missing artifacts are visible and named.
- Every scene has an inspectable evidence trail.
- The dashboard works from a static file export without server-side code.
