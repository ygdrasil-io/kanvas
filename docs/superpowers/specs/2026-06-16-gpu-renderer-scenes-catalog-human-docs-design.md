# GPU Renderer Scenes Catalog Human Documentation Design

Date: 2026-06-16
Status: Approved for planning

## Context

`gpu-renderer-scenes` already owns a technical scene registry and generated
catalog reports. The current source of truth for executable scenes is
`GPURendererSceneRegistry.scenes`, where each scene has an ID, title,
English description, tags, roadmap links, expectation, and executable
`SceneCommand` list.

The generated `catalog.md` is useful as a compact technical inventory, but it
does not explain the scenes well for human review. It also does not separate
renderable evidence from roadmap candidates that are useful to list before
they can be launched by the offscreen or Kadre runners.

This design evolves the catalog for KGPU M0-M10 coverage. It does not switch
the catalog structure to the later M60-M88 roadmap, although future work may
reference that roadmap when selecting later candidates.

## Goals

- Keep executable scenes and runner routing unchanged.
- Add a compact French explanation for every existing executable scene.
- Add a separate candidate section for upstream/relevant scenes across KGPU
  M0-M10.
- Keep candidates impossible to run accidentally.
- Keep machine-readable output structured enough for tests and future tools.
- Preserve explicit non-claims so the catalog does not hide current pipeline
  gaps.

## Non-Goals

- Do not add new renderer support as part of this catalog design.
- Do not route candidates through offscreen or Kadre runners.
- Do not replace the existing technical `catalog.md`.
- Do not claim broad support from fixture-backed scenes.
- Do not turn the catalog into a full product backlog or ticket tracker.

## Chosen Approach

Use a separate documentation model instead of adding human documentation fields
directly to `GPURendererScene`.

`GPURendererScene` remains the executable technical model. A new documentation
layer named `SceneHumanDocs` is keyed by `sceneId` and stores the French
compact fiche for each existing scene. A second model named `CandidateScene`
stores roadmap candidates without any executable command payload. These names
are the planned implementation names unless a local naming conflict appears
during implementation.

This keeps the runner boundary clear:

- executable scenes live in `GPURendererSceneRegistry.scenes`;
- French documentation is indexed by existing executable scene IDs;
- candidates are structured data, but not renderable scenes.

## Executable Scene French Fiche

Every existing executable scene gets a compact French fiche with these fields:

- `intention`: what the scene represents for product or technical review;
- `validates`: what the scene actually exercises;
- `nonClaims`: what the scene explicitly does not claim;
- `evidence`: expected proof path, such as WebGPU offscreen, Kadre windowed,
  or expected product refusal;
- roadmap: derived from the scene's existing roadmap links.

Example Markdown shape:

```md
### Photo Contact Sheet (`photo-contact-sheet`)
M4 - Image, Clip, RRect - ShouldRender

Intention: verifier un contact sheet de photos deja decodees.
Valide: clips rectangulaires, rrect de fond, quatre `BitmapRect` fixture-backed.
Ne revendique pas: codec reel, upload texture arbitraire, color management.
Preuve: WebGPU offscreen + Kadre windowed.
```

The generated JSON should keep equivalent fields structured so tests and later
tools can assert completeness. English titles, tags, and IDs remain the
technical/canonical routing labels; the French fields are explanatory metadata
and must not drive runner behavior.

## Candidate Scenes

Candidate scenes cover the KGPU M0-M10 roadmap represented by the existing
catalog. They are not evidence that a route already works. They are a curated
preview of scenes that appear relevant to add or prove later.

Each candidate has:

- readable business `sceneId`;
- title;
- KGPU milestone(s);
- family or tags;
- pipeline status;
- French intention;
- validation target;
- required non-claims;
- reason the candidate is worth tracking.

Candidate status is a closed set:

- `candidate`: useful, but not yet ready to implement;
- `fixture-ready`: implementable with the current fixture/command model;
- `runner-gap`: relevant, but blocked by runner capability;
- `dependency-gated`: blocked by a real dependency such as font or codec work;
- `product-refusal-expected`: expected to emit a product-level refusal.

Initial candidate coverage should include at least one candidate across each
important KGPU M0-M10 family:

- M0/M1: order, rollback, product activation boundaries;
- M2: rrect, gradient, and scissor variations;
- M3: path, stroke, and coverage gates;
- M4: image, sampler, and codec provenance;
- M5: layer, filter, and destination-read boundaries;
- M6: text, glyph, and resource-binding boundaries;
- M7: runtime effect, blend, and color boundaries;
- M8: vertices and mesh routes;
- M9: cache and frame gates;
- M10: legacy comparison, parity, and retirement.

## Generated Outputs

The report generator should produce:

- `catalog.md`: existing technical English/compact table, kept stable where
  possible;
- `catalog.fr.md`: French human-readable catalog with executable scene fiches
  and a separate candidate section;
- `catalog.json`: structured machine-readable report with separate `scenes`
  and `candidateScenes` arrays.

`catalog.json.scenes` remains the executable scene inventory. `candidateScenes`
must not be consumed by offscreen or Kadre runners.

## Validation And Guardrails

Tests should enforce:

- every executable scene has one `SceneHumanDocs` entry;
- every `SceneHumanDocs.sceneId` points to an existing executable scene;
- documentation IDs are unique;
- French fields are non-blank for `intention`, `validates`, `nonClaims`, and
  `evidence`;
- every candidate has a valid closed-set status;
- every candidate has non-empty French explanation and non-claims;
- `catalog.fr.md` contains all executable scenes and a candidate section;
- `catalog.json` keeps `scenes` and `candidateScenes` separate;
- candidates do not appear in `GPURendererSceneRegistry.scenes`;
- the offscreen and Kadre runners still source their routable scenes only from
  the executable registry.

Implementation validation should include:

- targeted catalog/report tests;
- `:gpu-renderer-scenes:test`;
- catalog report generation;
- a quick check that no candidate scene can be launched accidentally.

## Risks

- Adding French docs for all scenes is a larger text change than recent
  scene-only PRs. The mitigation is to keep the docs compact and test their
  completeness rather than overfitting prose.
- `catalog.json` consumers may assume only the current top-level fields. The
  mitigation is to keep existing `scenes` semantics and add `candidateScenes`
  as an additive field.
- Candidate lists can become stale. The mitigation is to treat candidate
  coverage as curated KGPU M0-M10 documentation, with tests for shape and
  completeness rather than pretending it is an active backlog.

## Approval Summary

The approved choices are:

- approach B: separate documentation model indexed by `sceneId`;
- French documentation level B: compact fiche for every existing scene;
- candidate coverage B: KGPU M0-M10 roadmap coverage;
- source of truth C: structured Kotlin plus generated Markdown;
- bilingual output C: separate `catalog.fr.md`;
- JSON structure A: `scenes` and `candidateScenes` separated;
- candidate statuses B: pipeline-oriented status set.
