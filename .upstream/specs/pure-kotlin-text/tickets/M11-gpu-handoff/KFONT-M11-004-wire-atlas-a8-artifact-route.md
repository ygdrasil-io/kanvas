---
id: "KFONT-M11-004"
title: "Wire atlas A8 artifact route"
status: "done"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M9-003", "KFONT-M9-005", "KFONT-M11-003"]
legacy_gate: null
---

# KFONT-M11-004 - Wire atlas A8 artifact route

## PM Note

Ce ticket prouve le premier chemin GPU texte borné: échantillonner un atlas A8 préparé par le stack texte.

## Problem

A8 artifacts from M9 are CPU-prepared, but the GPU renderer still needs a typed route from `GlyphAtlasArtifact` to `AtlasMaskSample`. The route must validate atlas descriptor, page generation, entry refs, instance layout, A8 WGSL coverage sampling, material/color modulation, clip compatibility, and upload dependency facts. Without this ticket, simple atlas text evidence remains unconnected to the target GPU pipeline.

## Scope

- Map `GlyphAtlasArtifact` with A8 masks to `GPUTextRepresentation.A8MaskAtlas` and `GPUTextRoute.AtlasMaskSample`.
- Define `A8TextMaskStep` route facts: atlas texture format, sampler policy, per-glyph instance fields, material/color uniform refs, clip facts, and coverage output value spec.
- Build `GPUTextAtlasPlan`, `GPUTextAtlasEntryRef`, `GPUTextInstanceLayout`, and binding refs needed for A8 sampling.
- Emit `gpu-text-a8-route-plan.json` with route selection, atlas refs, instance layout hash, upload dependency refs, and diagnostics.
- Add focused GPU/WGSL evidence before any A8 route support claim is promoted.

## Non-Goals

- Do not implement SDF, color glyph, bitmap, SVG, or outline routes in this ticket.
- Do not generate glyph masks or pack atlases; M9 owns those artifacts.
- Do not support LCD subpixel text.
- Do not allow a CPU-rendered full text texture as fallback.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class A8TextMaskRoutePlan(
    val commandId: DrawCommandId,
    val atlasPlan: GPUTextAtlasPlan,
    val subRuns: List<GPUTextSubRunPlan>,
    val instanceLayout: GPUTextInstanceLayout,
    val renderStep: GPUTextRenderStep = GPUTextRenderStep.A8TextMaskStep,
    val wgslModule: TextWgslModuleRef,
    val diagnostics: List<GPUTextDiagnostic>,
)
```

## Acceptance Criteria

- [x] An A8 `GlyphAtlasArtifact` fixture routes to `AtlasMaskSample` without font parsing or shaping in the renderer.
- [x] Atlas entry refs validate generation, page, UV rect, source bounds, and source mask hash.
- [x] `A8TextMaskStep` WGSL samples R8/A8 coverage and modulates text material/color without putting atlas refs in `MaterialKey`.
- [x] Missing atlas entry, stale generation, unsupported texture format, or missing upload plan refuses with stable `unsupported.text.*` diagnostics.
- [x] GPU evidence is focused on A8 atlas sampling and does not imply broad shaping or color glyph support.

## Required Evidence

- `gpu-text-a8-route-plan.json` fixture with accepted `GlyphAtlasArtifact`, atlas entry refs, instance layout, and binding facts.
- WGSL parser/reflection dump for the A8 text mask module or snippet.
- GPU evidence artifact for a bounded A8 text fixture, plus refusal snapshots for missing entry and stale generation.

## Fallback / Refusal Behavior

- Stale or missing atlas entries refuse or request an explicit rebuild within budget; glyphs must not be dropped.
- Unsupported A8 route emits `unsupported.text.a8_atlas_route_unavailable`.
- CPU-rendered full text texture fallback remains forbidden.

## Dashboard Impact

- Expected row: `A8 atlas text GPU route`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless A8 route plan, WGSL validation, and GPU evidence are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*A8Text*'
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest --tests '*A8Text*'
```

## Status Notes

- `done`: `gpu-text-a8-route-plan.json` now captures a bounded accepted
  `GlyphAtlasArtifact` -> `AtlasMaskSample` route with stable atlas page/entry
  refs, `A8TextMaskStep` binding facts, and checked-in refusal snapshots in
  `gpu-text-a8-route-refusals.json` for missing atlas entry / stale generation
  plus stable diagnostics for missing upload plan / unsupported texture format,
  reviewed WGSL reflection linkage via
  `reports/wgsl4k-evolution/generated/text-wgsl-reflection.json`, and focused
  simple-Latin GPU evidence from
  `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/`.
  Remaining gates stay explicit: this ticket does not land subrun splitting,
  resource/upload/instance/binding expansion, upload-before-sample ordering,
  broad WGSL text validation, `MaterialKey` leakage validation, SDF/outline/
  color/bitmap/SVG routes, or `dftext` retirement.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
