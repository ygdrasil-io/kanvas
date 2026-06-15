---
id: KGPU-M6-001
title: "Accept typed pure Kotlin `DrawTextRun` payloads"
status: done
milestone: M6
priority: P0
owner_area: text-handoff
claim_impact: DependencyGated
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KFONT-M11-003]
legacy_gate: dftext
---

# KGPU-M6-001 - Accept typed pure Kotlin `DrawTextRun` payloads

## PM Note

Ce ticket connecte le renderer GPU aux artifacts texte sans reshaper ni relire
les fontes.

## Problem

Text rendering must consume typed pure Kotlin artifacts and must not carry
mutable Skia-like objects, font bytes, or complete CPU-rendered text textures.

## Scope

- Accept immutable `DrawTextRun` payload facts and diagnostics.
- Add leakage/refusal checks for nondumpable or forbidden payload fields.

## Non-Goals

- Do not implement atlas sampling.
- Do not shape text inside `:gpu-renderer`.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`

## Graphite Algorithm References

- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Use typed glyph subrun geometry as the handoff shape from pure-Kotlin text.
- [`GFX-TEXT-ATLAS-CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source [TextAtlasManager.cpp:47](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:47); Reference atlas sizing and multitexture policy for text resource planning.
- [`GFX-BITMAP-TEXT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-bitmap-text-step) - source [BitmapTextRenderStep.cpp:59](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/BitmapTextRenderStep.cpp:59); Study how subrun data becomes atlas sampling instances.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class DrawTextRunHandoff(val artifactRefs: List<String>, val diagnostics: List<String>)
```

## Acceptance Criteria

- [ ] Payload contains only dumpable value objects.
- [ ] Missing artifacts refuse with stable diagnostics.
- [ ] No `Sk*` leakage is possible.

## Required Evidence

- Payload dumps, no-leakage report, and refusal fixtures.

## Fallback / Refusal Behavior

Missing text artifacts keep the route dependency-gated and refused.

## Dashboard Impact

- Expected row: `gpu-renderer.text.drawtextrun-handoff`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: The renderer consumes the accepted pure Kotlin text handoff surface
  through `:font:gpu-api` value objects and maps `GPUTextArtifactReference`
  into dumpable `GPUTextArtifactRef` payload facts. Fresh validation covered
  `GPUTextCommandHandoffTest`, `GPURecorderTest`, `:font:gpu-api:test`, and
  `:gpu-renderer:test`; independent review
  `019ec8d9-9b04-71b0-8d7d-83ce1ffdd94e` returned `ACCEPT`. `DrawTextRun`
  remains a refused route and now emits stable terminal diagnostics for
  Skia-like payload leakage and CPU-rendered text texture leakage. No shaping,
  font parsing, font byte reads, GPU text route promotion, adapter-backed
  execution, or CPU-rendered full text texture fallback is implied. Evidence:
  `reports/gpu-renderer/2026-06-15-m6-001-text-handoff.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text`
