# KFONT-M11-004 A8 Route Plan Evidence

Date: 2026-06-19
Status: implemented, locally revalidated.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-004-wire-atlas-a8-artifact-route.md`

## Scope

This slice adds the first accepted bounded A8 atlas handoff route for typed
text artifacts. It keeps the route explicit, non-promoted, and limited to
`GlyphAtlasArtifact` -> `AtlasMaskSample` without broadening SDF, outline,
color glyph, bitmap glyph, SVG glyph, emoji, LCD, or `dftext` claims.

## Files

- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextA8RoutePlan.kt`
- `font/gpu-api/src/main/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextRouteRefusals.kt`
- `font/gpu-api/src/test/kotlin/org/graphiks/kanvas/glyph/gpu/GPUTextA8RoutePlanTest.kt`
- `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleLatinLineSceneEvidenceTest.kt`
- `reports/pure-kotlin-text/gpu-text-a8-route-plan.json`
- `reports/pure-kotlin-text/gpu-text-a8-route-refusals.json`

## Evidence

- `defaultGPUTextA8RoutePlan()` emits the checked-in
  `gpu-text-a8-route-plan.json` dump with:
  - accepted `GlyphAtlasArtifact` route selection to `AtlasMaskSample`;
  - `A8TextMaskStep` render step and `text.a8-mask` WGSL module ID;
  - atlas page facts (`R8Unorm`, `128x64`, row stride `128`);
  - stable atlas entry refs with generation, rect, UV, and source mask hashes;
  - binding facts for `glyphAtlas`, `glyphSampler`, and `textParams`;
  - explicit non-claims and `routePromotion=not-promoted`.
- `defaultGPUTextA8RouteRefusalReport()` emits the checked-in
  `gpu-text-a8-route-refusals.json` dump with bounded refusal snapshots for:
  - missing atlas entry via `text.gpu.atlas-entry-missing` /
    `unsupported.text.atlas_entry_missing`;
  - stale generations via `text.gpu.atlas-generation-stale` /
    `unsupported.text.atlas_generation_stale`.
- `planGPUTextA8Route()` refuses:
  - missing upload plans via `text.gpu.upload-plan-missing` /
    `unsupported.text.upload_plan_missing`;
  - missing atlas entries via `text.gpu.atlas-entry-missing` /
    `unsupported.text.atlas_entry_missing`;
  - stale generations via `text.gpu.atlas-generation-stale` /
    `unsupported.text.atlas_generation_stale`;
  - unsupported atlas texture formats via
    `text.gpu.atlas-descriptor-unaccepted` /
    `unsupported.text.atlas_descriptor_unaccepted`.
- WGSL reflection evidence stays tied to the reviewed text module fixture:
  `reports/wgsl4k-evolution/generated/text-wgsl-reflection.json`.
- Focused GPU evidence stays bounded to the simple Latin route:
  - `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json`
  - `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json`
  - `reports/wgsl-pipeline/webgpu-glyph-atlas-sampling-route/kan-054-webgpu-glyph-atlas-sampling-route.json`

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextA8RoutePlanTest*'
```

Result: failed as expected at `:font:gpu-api:compileTestKotlin` because
`defaultGPUTextA8RoutePlan`, `planGPUTextA8Route`, fixture types, and the
`ATLAS_DESCRIPTOR` blocker did not exist.

Green result:

```bash
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*GPUTextA8RoutePlanTest*'
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest --tests '*A8Text*'
```

Result: both targeted suites passed. The GPU conformance slice proved the
bounded simple Latin atlas route with no CPU-texture fallback.

## Remaining Gate

KFONT-M11-004 lands only the first bounded A8 atlas route plan. It does not
close subrun splitting, resource/upload/instance/binding contract expansion,
upload-before-sample ordering, broad WGSL text validation, `MaterialKey`
leakage validation, SDF/outline/color/bitmap/SVG routes, or `dftext`
retirement. Those remain owned by `KFONT-M11-006` through `KFONT-M11-010`.
