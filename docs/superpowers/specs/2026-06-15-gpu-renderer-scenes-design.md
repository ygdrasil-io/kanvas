# GPU Renderer Scenes Design

Date: 2026-06-15
Status: Approved for implementation planning

## Context

The active GPU renderer target is a GPU-first, WGSL-only renderer under
`:gpu-renderer`. It has typed contracts and evidence gates for roadmap stages
R0-R6 and KGPU milestones M0-M10, while product activation and many visual
routes remain intentionally gated by evidence. The existing `:kadre-runtime`
module contains historical milestone evidence and replay helpers, but it is not
the intended dependency boundary for a new scene workbench.

This design creates a new module, `:gpu-renderer-scenes`, for simple business
named scenes that exercise the new GPU renderer roadmap. The module owns the
scene catalog, offscreen WebGPU validation runner, and windowed Kadre visual
runner. The first implementation is infrastructure-first: it defines scenes and
runner contracts without requiring all scenes to render successfully.

## Goals

- Add a separate Gradle module, `:gpu-renderer-scenes`.
- Define scenes in Kotlin with readable business names, not opaque roadmap IDs.
- Link each scene to KGPU M0-M10 tickets and R0-R6 roadmap stages where
  applicable through an explicit correspondence table.
- Provide an offscreen WebGPU runner for technical validation and artifact
  capture.
- Provide a Kadre windowed runner for manual visual inspection.
- Keep WebGPU/Kadre execution opt-in. `:gpu-renderer-scenes:check` must not
  create a WebGPU context, open a window, or require a local adapter.
- Preserve product expectations. A scene should describe what the final product
  is expected to render unless the final product is explicitly supposed to
  refuse that case.

## Non-Goals

- Do not depend directly on `:kadre-runtime`.
- Do not use expected refusals to hide current renderer gaps.
- Do not add a required CI lane that renders all scenes.
- Do not activate product routes in `:gpu-raster`.
- Do not port Ganesh, Graphite, SkSL, or Skia runtime shader compilation.
- Do not claim visual correctness from the Kadre manual runner alone.

## Architecture

`:gpu-renderer-scenes` is a single module with separated internal packages:

```text
org.graphiks.kanvas.gpu.renderer.scenes
  catalog      business scene IDs, registry, tags, dimensions, roadmap links
  commands     bounded scene command model aligned with :gpu-renderer concepts
  expectations product expectation and final-product refusal policy
  reports      catalog and run report serialization
  offscreen    WebGPU render-to-texture runner and readback artifacts
  windowed     Kadre windowed runner for manual visual review
```

The catalog is the source of truth. Runners consume the catalog; they do not
define scene identity or roadmap coverage. This keeps scene meaning stable even
when the current runner can only execute a subset.

## Dependencies

The module may depend on:

- `:gpu-renderer` for normalized command, route, telemetry, diagnostics, and
  renderer contract vocabulary.
- WebGPU/wgpu4k and public helper classes from `:gpu-raster` when useful for
  offscreen target setup or readback.
- Kadre and wgpu4k for the windowed runner.

The module must not depend on `:kadre-runtime`. Historical replay contracts may
inform scene selection, but this module owns its own scene model and runners.

## Scene Model

Each scene has:

- `sceneId`: stable business identifier such as `solid-card-stack`.
- `title`: human-readable name for reports and manual selection.
- `description`: short PM/review description of what the scene is proving.
- `dimensions`: default width and height.
- `tags`: feature tags such as `rect`, `gradient`, `clip`, `text`, `cache`.
- `roadmapLinks`: KGPU milestones/tickets plus R0-R6 stages when applicable.
- `expectation`: final product expectation.
- `commands`: bounded Kotlin scene commands.

Expected shape:

```kotlin
data class GPURendererScene(
    val sceneId: SceneId,
    val title: String,
    val description: String,
    val dimensions: SceneDimensions,
    val tags: Set<SceneTag>,
    val roadmapLinks: List<SceneRoadmapLink>,
    val expectation: SceneExpectation,
    val commands: List<SceneCommand>,
)
```

`SceneExpectation` separates final product behavior from current runner status:

```kotlin
sealed interface SceneExpectation {
    data object ShouldRender : SceneExpectation
    data class ProductRefusal(val reason: ProductRefusalReason) : SceneExpectation
}
```

`ProductRefusal` is only valid for final product limits, such as an accepted
budget overflow, unsupported target format, or arbitrary SkSL source that Kanvas
will not compile. Temporary renderer gaps must be reported as runner outcomes
such as `not-yet-rendered` or `render-failed`, not as scene expectations.

## Command Model

The first scene command model is intentionally bounded and Kotlin-typed. It
should be close to `:gpu-renderer` concepts without exposing Skia-like API
objects or replaying a Canvas stack. Initial command families:

- clear/background;
- fill rect and rounded rect;
- linear gradient rect/rrect;
- scissor and bounded clip commands;
- bitmap/image rect from deterministic fixtures;
- saveLayer-like isolated layer boundary;
- simple filter node command;
- text run command from typed text artifacts;
- registered runtime-effect material command;
- draw vertices / mesh command.

Commands that the current runner cannot execute still remain in the catalog if
they represent product expectations. Their run report records the current
runner gap.

## Runners

### Offscreen WebGPU Runner

Task: `:gpu-renderer-scenes:renderGpuRendererSceneOffscreen`

Inputs:

- `-PsceneId=<business-scene-id>` selects one scene.
- Optional `-PsceneOutput=<path>` overrides the default output root.
- Optional `-PsceneWidth=<px>` and `-PsceneHeight=<px>` override scene defaults
  for local diagnostics.

Outputs:

```text
reports/gpu-renderer-scenes/offscreen/<sceneId>/
  render.png
  run.json
  diagnostics.txt
```

A successful technical run requires:

- a PNG was written;
- output dimensions match the requested dimensions;
- readback byte count matches the texture size;
- pixels are nonblank when the scene expectation requires visible output;
- `run.json` records backend, adapter facts when available, scene ID, roadmap
  links, runner status, artifact paths, and diagnostics.

If setup or rendering fails, the task should still write a report when it has
an output directory. The failure is a runner result, not a product refusal.

### Windowed Kadre Runner

Task: `:gpu-renderer-scenes:runGpuRendererSceneKadre`

Inputs:

- `-PsceneId=<business-scene-id>` selects one scene.
- Optional `-Pframes=<n>` exits after a bounded frame count.
- If no frame count is supplied, the runner may stay open until the window is
  closed.

Purpose:

- manual visual validation by the user;
- selected scene display;
- simple animation or parameter cycling when useful for the scene;
- optional session JSON for traceability.

The Kadre runner is not an automated correctness oracle. It can confirm that a
scene is visible to the user, but correctness must be supported by offscreen
artifacts or later reference/diff evidence.

## Gradle Tasks

- `:gpu-renderer-scenes:check`
  Runs catalog and invariant tests only. It must not create a WebGPU context or
  open a Kadre window.

- `:gpu-renderer-scenes:gpuRendererScenesCatalogReport`
  Writes Markdown and JSON catalog reports with scene names, descriptions,
  tags, KGPU links, R0-R6 links, product expectations, and current runner
  status metadata.

- `:gpu-renderer-scenes:renderGpuRendererSceneOffscreen`
  Opt-in WebGPU offscreen rendering for one scene.

- `:gpu-renderer-scenes:runGpuRendererSceneKadre`
  Opt-in Kadre window for one scene.

No required first pass should render all scenes. A batch render task can be
added later, but it must remain opt-in unless a future release gate explicitly
accepts it.

## Initial Scene Catalog

| Scene ID | Business Purpose | Roadmap Links |
|---|---|---|
| `solid-card-stack` | Rectangles, `SrcOver`, alpha, and draw order. | KGPU M0/M1, R1-R6 |
| `rounded-panel-gradient` | Rounded rect, linear gradient, and scissor. | KGPU M2, R1-R3 |
| `path-badge-and-stroke` | Simple path fill and simple stroke. | KGPU M3 |
| `clipped-avatar-grid` | Bounded clip variants over repeated content. | KGPU M3/M5 |
| `texture-swatch-board` | Already-decoded image/bitmap sampling, nearest and linear. | KGPU M4 |
| `layered-shadow-card` | Isolated layer target and restore composite. | KGPU M5 |
| `filtered-photo-chip` | Bounded simple filter over image or layer content. | KGPU M5 |
| `receipt-text-run` | Simple typed text-run path and future A8 atlas route. | KGPU M6 |
| `runtime-effect-color-tile` | Registered runtime effect with bounded editable parameters. | KGPU M7 |
| `blend-mode-strip` | Supported blend family strip and final-product blend limits. | KGPU M7 |
| `mesh-ribbon` | Draw vertices / mesh-like geometry and batching visibility. | KGPU M8 |
| `cache-pressure-deck` | Repeated scenes for cache/resource telemetry observation. | KGPU M9 |
| `legacy-route-comparison` | New-route intent compared with legacy `gpu-raster` ownership. | KGPU M10 |

Policy and evidence-only tickets should be represented in catalog metadata or
generated correspondence tables. They should not force artificial visual scenes.

## Reports And Ticket Correspondence

The catalog report should generate a correspondence table suitable for copying
or linking from KGPU ticket markdown:

```text
business scene -> KGPU milestone -> ticket IDs -> R stage -> tags -> expectation
```

The scene ID remains the business name. KGPU and R-stage identifiers are
metadata for traceability, not user-facing scene names.

## Testing Policy

Catalog tests must assert:

- scene IDs are unique, stable, lowercase, and readable;
- each scene has title, description, tags, dimensions, roadmap links, and a
  final product expectation;
- KGPU links reference existing milestone or ticket IDs where practical;
- R0-R6 links use known roadmap stage names;
- no `ProductRefusal` appears without an accepted final-product reason;
- generated reports are deterministic;
- `check` does not invoke WebGPU or Kadre setup paths.

Runner tests may cover pure planning, serialization, and failure report
generation without creating a real adapter. Adapter-backed rendering remains
opt-in.

## Error Handling

Offscreen and windowed tasks should fail clearly when:

- the selected scene ID is unknown;
- no WebGPU adapter is available;
- Kadre or native windowing setup is missing;
- a runner reaches an unimplemented current capability;
- readback or PNG writing fails.

Error reports must distinguish:

- final product expectation;
- current runner support;
- environment setup failure;
- renderer execution failure;
- artifact writing failure.

This distinction is required so current pipeline failures stay visible instead
of becoming expected unsupported outcomes.

## First Implementation Boundary

The first implementation should include:

- the Gradle module and settings wiring;
- the scene catalog API and initial business-named catalog;
- catalog validation tests;
- deterministic catalog Markdown/JSON export;
- offscreen and windowed task entry points;
- at least one offscreen-rendered scene path for `solid-card-stack`;
- a Kadre windowed path that can display the same selected scene or fail with a
  precise environment/setup report.

Other scenes may initially report runner status `not-yet-rendered`. Their
product expectation remains `ShouldRender` unless the final product is intended
to refuse the scene.

## Acceptance Criteria

- `:gpu-renderer-scenes:check` passes without creating WebGPU or Kadre runtime
  state.
- The catalog report lists every initial scene with business ID, tags, KGPU
  mapping, R-stage mapping, and expectation.
- The offscreen task can be invoked for a selected scene and writes a structured
  report whether it succeeds or fails.
- The Kadre task can be invoked for a selected scene and opens the manual visual
  path or reports missing environment requirements explicitly.
- No scene uses a temporary renderer gap as final expected unsupported behavior.
