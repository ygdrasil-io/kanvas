---
id: KGPU-M34-005
title: "Font fallback chain"
status: ready
milestone: M34
priority: P1
owner_area: text
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "legacy drawText"
---

# KGPU-M34-005 - Font fallback chain

## PM Note

Quand la police primaire manque un glyph, la chaîne de fallback sélectionne
une police alternative. Le GPU renderer split les subruns par identité de
fallback.

## Problem

When a primary font lacks a glyph, the fallback chain selects an alternative
font. Each fallback font has a different atlas page, representation, and
atlas budget impact. The GPU renderer must split text runs into subruns by
fallback identity and emit stable diagnostics when the chain is exhausted.

## Scope

- `GPUFallbackGlyphPlan` — per-glyph provenance (original font, fallback font,
  fallback reason).
- `GPUFallbackBatchPolicy` — subrun splitting by fallback identity, atlas
  page, representation.
- Exhausted chain → `unsupported.text.fallback_exhausted` diagnostic with
  glyph index and font identity.

## Non-Goals

- No fallback selection logic in `:gpu-renderer`. The text stack performs
  fallback chain resolution and emits per-glyph provenance.
- No font loading, matching, or CMAP lookup in `:gpu-renderer`.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/README.md`

## Graphite Algorithm References

- [`GFX-SUBRUN-DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source [SubRunData.h:24](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/geom/SubRunData.h:24); Carry a subspan of an atlas subrun, mask bounds, mask-to-device matrix, glyph range, SDF/LCD metadata, and renderer data as geometry.
- [`GFX-TEXT-ATLAS-CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source [TextAtlasManager.cpp:47](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/text/TextAtlasManager.cpp:47); Derive atlas/plot dimensions from max texture size and glyph cache byte budget.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
enum class GPUFallbackReason {
    MissingGlyph,
    UnsupportedScript,
    ColorFontPreference,
}

data class GPUFallbackGlyphPlan(
    val glyphIndex: Int,
    val originalFont: GPUFontKey,
    val fallbackFont: GPUFontKey,
    val reason: GPUFallbackReason,
)

data class GPUFallbackBatchPolicy(
    val subruns: List<GPUFallbackSubrun>,
    val maxFallbackDepth: Int,
)

data class GPUFallbackSubrun(
    val fontKey: GPUFontKey,
    val glyphRange: IntRange,
    val atlasPage: GPUAtlasPageRef,
)
```

## Acceptance Criteria

- [ ] Text stack emits per-glyph fallback provenance.
- [ ] GPU splits subruns correctly when fallback fonts differ (at least 2
      fallback fonts in the same text run).
- [ ] Exhausted chain produces diagnostic with glyph index and font identity.

## Required Evidence

- `GPUFallbackGlyphPlan` dump (multi-font run with fallback chain).
- Subrun split fixture: text run requiring 2 fallback fonts → 3 subruns
  (primary + fallback A + fallback B).
- Refusal fixture: exhausted fallback chain with missing glyph diagnostic.

## Fallback / Refusal Behavior

- Exhausted fallback chain → `unsupported.text.fallback_exhausted`.
- No CPU texture fallback. Missing glyphs produce a diagnostic, not a hidden
  raster.

## Dashboard Impact

- Expected row: `gpu-renderer.text.font-fallback`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FontFallback*'
```

## Status Notes

- `proposed`: Initial ticket.
- `proposed → ready` (2026-06-28): milestone activated, autonomous implementation starting.

## Linear Labels

- `gpu-renderer`
- `milestone:M34`
- `area:text`
