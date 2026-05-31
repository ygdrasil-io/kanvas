# Front Boundaries And Information Architecture

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Define what the front layer owns and how humans should navigate Kanvas evidence.
This spec separates frontend structure from rendering implementation details.

## Audiences

| Audience | Needs |
|---|---|
| PM / release owner | See readiness, pass/gap/unsupported state, and demo evidence without reading Gradle logs. |
| Rendering engineer | Inspect the scene, raw artifacts, route diagnostics, and source report quickly. |
| Reviewer | Verify that a support claim has visible reference, CPU, GPU, diff, route, and stats artifacts. |
| Future contributor | Understand what frontend surface to extend without reopening rendering specs. |

## Ownership Boundary

The front layer owns:

- information hierarchy and navigation;
- layout and responsive behavior;
- visible status language;
- filters, search, and visible aggregates;
- empty/loading/error/missing-artifact states;
- artifact link presentation;
- PM demo package flow;
- accessibility and browser-level quality gates.

The front layer does not own:

- scene promotion rules;
- CPU/GPU route selection;
- pixel similarity thresholds;
- shader, coverage, geometry, or image-filter behavior;
- fallback reason catalogs;
- benchmark threshold policy;
- generated evidence production.

## Target Views

### Evidence Overview

The first screen is the evidence overview. It must show the current state of
the scene corpus immediately:

- total rows;
- counts by support state;
- generated/static evidence mix;
- adapter-backed availability when present;
- prominent validation command or source build task.

The overview must not be a landing page. It is a working evidence surface.

### Scene List

The scene list is the primary review surface. It shows one row/card per scene
with:

- title and id;
- support status;
- priority;
- reference kind;
- CPU lane summary;
- GPU lane summary;
- fallback reason;
- performance trend state;
- tags;
- links to raw artifacts.

Expected unsupported rows remain visible unless the user filters them out.

### Scene Detail

The target front should support a detail view or deep-linkable expanded state
for each scene. The detail view should show:

- reference, CPU, GPU, and diff images with consistent sizing;
- raw route diagnostic links;
- stats summary;
- generation/source evidence links;
- support state explanation;
- blocker/follow-up text when present.

This view is not required to define rendering behavior. It only makes the
existing evidence inspectable.

### Artifact Browser

The target front should expose a small artifact browser for each scene:

- image artifacts;
- JSON route and stats artifacts;
- performance raw metrics;
- source reports;
- generated evidence payloads.

Missing artifacts are first-class states and should say which lane or field is
missing.

### PM Report Package

The target front should produce or point to a PM-readable report package. M49
currently provides this through `pipelinePmBundle`:

- milestone summary;
- dashboard URL or local export path;
- scene counts;
- changed rows;
- remaining expected unsupported rows;
- validation commands;
- links to source reports and raw artifacts.
- manifest with generation metadata, counters, limitations, and unavailable
  reference checks.

## Navigation Model

The static dashboard can implement navigation through a single HTML page:

- summary at top;
- filter toolbar;
- aggregate cards;
- scene cards;
- expandable artifact details.

A future app can add real routes:

```text
/evidence
/evidence/scenes
/evidence/scenes/<scene-id>
/evidence/reports/<milestone>
/evidence/artifacts/<scene-id>
```

Route names are frontend navigation routes. They are not rendering route
identifiers.

## View-Model Boundary

The frontend should eventually map `scenes.json` into a view model before
rendering. The adapter should normalize:

- absent image paths into `missing` panels;
- absent performance trends into `unavailable`;
- absent fallback reasons into `none` only when the source lane is present;
- tags into sorted exact-match filter values;
- evidence links into displayable artifact/link groups.

The adapter must not change support status or synthesize pass/fail decisions.

## Human-Readable Language

Frontend copy should use stable terms:

- `pass`: required evidence exists and the source contract says it passed;
- `tracked-gap`: the row is visible but incomplete by policy;
- `expected-unsupported`: an intentional refusal with a stable reason;
- `fail`: support claim or required evidence failed.

The UI should avoid internal-only shorthand in primary labels. Raw diagnostic
codes may appear in monospace in details.

## Acceptance Criteria

- The front surface starts on evidence, not marketing content.
- Every status and fallback reason is visible without opening raw JSON.
- A reviewer can reach raw artifacts from each scene.
- A PM can read the dashboard without knowing module names.
- Expected unsupported rows are visible by default.
- Front navigation terms cannot be confused with rendering route identifiers.
