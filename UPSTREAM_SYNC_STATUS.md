# Upstream sync status

Snapshot date: 2026-05-25.

This file is the current source of truth for the rapprochement between
`kanvas-skia` and upstream Skia. The previous phase plans have been
archived under `archives/plan-snapshots-2026-05-24/` because they mix
delivered work, stale assumptions, and future ideas.

## Current posture

The project is no longer in a broad "port every planned phase" mode.
Raster/CPU coverage is high, WebGPU is the chosen GPU divergence path,
and the remaining work must be driven by observed upstream deltas,
GM gaps, and explicit dependency deliveries.

Hard decisions:

- Do not port Ganesh or Graphite.
- Do not rebuild the SkSL compiler, IR, or VM.
- Keep `SkRuntimeEffect` as a compatibility facade, but dispatch to
  registered Kotlin/WGSL implementations.
- Treat fonts and codecs as dependency-delivery tracks. Do not fill
  them with short-lived substitutes while the real deliveries are in
  progress.
- New planning must be small, dated, and evidence-backed. Avoid reviving
  the archived phase plans as active backlog.

## Baseline facts

Known from the current tree:

- First mechanical rebaseline: `UPSTREAM_REBASELINE_2026-05-24.md`.
- Current post-#1047 rebaseline:
  `reports/upstream-rebaseline/2026-05-25-post-1047.md`.
- Current generated TSV:
  `reports/upstream-rebaseline/2026-05-25-post-1047.tsv`.
- Gradle modules include `:kanvas-skia`, `:cpu-raster`, `:gpu-raster`,
  `:skia-integration-tests`, `:integration-tests`, `:math`, and the
  split codec family (`:codec-*`).
- The root no longer contains active `MIGRATION_PLAN_*.md` or
  `API_*PLAN.md` files. Historical copies are archived.
- GM status in the post-#1047 snapshot reports 342 `PORTED`, 49
  `TEST_DISABLED`, 40 `PARTIAL`, 4 `HELPER`, 1 `STUB`, and 1 `MISSING`
  row. The only remaining `MISSING` row, `hello_bazel_world`, is classified
  as a `build-example`, not active implementation work.
- Similarity reports exist in `kanvas-skia/`, `cpu-raster/`, and
  `skia-integration-tests/`. `gpu-raster` currently has ratchet
  properties but no generated markdown report in the tree.
- Runtime-effect code exists in `cpu-raster/src/main/kotlin/org/skia/effects/runtime/`;
  older documents that say D2 is green-field are stale.
- WebGPU code exists in `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/`
  and shader resources exist under `gpu-raster/src/main/resources/shaders/`.

## Active backlog classes

### 1. Rebaseline upstream delta

Goal: keep the current mechanical view of the gap fresh as upstream and
local deliveries land.

Inputs:

- Upstream Skia tree: `/Users/chaos/workspace/kanvas-forge/skia-main/`
- Kotlin source tree: this repository
- Existing symbol map: `.upstream/source/map/`
- GM references: legacy `original-888/` resources

Outputs:

- Current GM classification: ported, blocked, GPU-only, dependency-gated,
  missing reference, or false-positive.
- Current public API delta by header/package.
- Current `STUB.*` inventory grouped by owner track.
- Current WebGPU GM coverage and missing backend features.

Current actionable tickets from the post-#999 rebaseline have landed
through #1047. Open new implementation tickets only from fresh non-gated
rows in the post-#1047 TSV or from explicit dependency deliveries.

### 2. Single backlog, no phase-plan drift

Goal: replace the old documents with a short backlog that can stay true.

Rules:

- Each item must name the observed blocker, affected tests/GMs, owning
  module, and dependency status.
- Use "dependency-gated" for fonts/codecs until the deliveries land.
- Use "WGSL/parser-gated" for shader work that depends on the incoming
  WebGPU shader parser.
- Use "implementation" only when the blocker is actionable in this tree
  today.
- Retire or reclassify any item once a test proves the state changed.

### 3. WebGPU/WGSL convergence

Scope:

- Keep WebGPU as the GPU backend.
- Complete missing primitive behavior by cross-testing against raster and
  upstream references.
- Route runtime effects to WGSL templates/registered effects rather than
  SkSL compilation.
- Use the incoming parser to reduce handwritten boilerplate, not to
  recreate Skia's SkSL pipeline.

### 4. Fonts delivery integration

Dependency-gated:

- Fontations / raw OpenType name tables.
- COLR v1 / palette glyphs.
- Emoji tables and SVG-in-OT.
- Liberation font manager behavior.
- RSX text blob fidelity where font shaping or glyph transforms require
  delivered font infrastructure.

### 5. Codec delivery integration

Dependency-gated:

- Lossy WebP.
- FFmpeg/video decoding.
- Animated WebP and missing animated fixtures.
- Compressed texture formats if the codec delivery covers them.

Already split codec modules should be preserved; integration should land
behind their existing module boundaries.

### 6. Actionable raster/API gaps

The post-#999 actionable batch has landed:

- Vertices texOnly/scaled-gradient sampling: #1007 / #1038.
- Bounded LCH powerless-hue interpolation: #1008 / #1041.
- `custommesh` CPU subset activation: #1009 / #1033.
- RGBA `asyncRescaleAndReadPixels` CPU path: #1010 / #1035.
- YUVA channel flags and location metadata helpers: #1011 / #1039.
- WebGPU strict source-rect constraint sampling: #1012 / #1047.

Retired or non-actionable in this snapshot:

- `SkShader.makeWithColorFilter` is implemented and covered.
- Image-filter multi-filter spans are covered by the current merge/filter
  graph unless a future public API delta proves a separate overload gap.
- Compressed texture work remains codec/media-gated, not a raster/API
  implementation shortcut.
- Broad color-filter/color-space and edge-AA entries need fresh API-delta
  evidence before returning to the active backlog.
- `colrv1` and `palette` are `PORTED` in the post-#1047 GM snapshot.

## Archived plans

Archived on 2026-05-24:

- `archives/plan-snapshots-2026-05-24/API_FINALIZATION_PLAN.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_C1_IMAGE_FILTERS.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_D1_FOLLOWUPS.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_D2_RUNTIME_EFFECT.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_GM_PORT.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_GPU_WEBGPU.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_MATH_TOOLING.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_RASTER_COMPLETION.md`
- `archives/plan-snapshots-2026-05-24/MIGRATION_PLAN_SVG.md`

Use them only as historical evidence. Do not treat checkboxes or phase
labels in those files as current backlog.
