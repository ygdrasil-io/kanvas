---
id: KGPU-M20-004
title: "Add text shaper integration: SkShaper -> GlyphRunDescriptor -> GPU route"
status: proposed
milestone: M20
priority: P0
owner_area: text-shaper
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-001, KGPU-M12-004]
legacy_gate: null
---

# KGPU-M20-004 - Add text shaper integration: SkShaper -> GlyphRunDescriptor -> GPU route

## PM Note

L'intégration du shaper connecte le shaping de texte (SkShaper) au pipeline GPU. Sans cela, le texte n'a ni crénage ni ligatures ni mise en page.

## Problem

The text shaping pipeline (SkShaper or equivalent) must be integrated to produce GlyphRunDescriptors from Unicode text strings. Without shaping, glyph positioning, kerning, and ligatures are absent.

## Scope

- Integrate text shaper (SkShaper or Kotlin equivalent) into GPU text pipeline
- Produce GlyphRunDescriptors from shaped text runs
- Add glyph positioning (kerning, advance) in instance data
- Produce shaped text rendering fixture dumps

## Non-Goals

- Latin text only
- No bidi or complex script shaping
- No vertical text layout

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_SUBRUN_DATA`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-subrun-data) - source src/gpu/graphite/text/TextUtils.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class TextShaper { fun shape(text: String, font: Font, size: Float): GlyphRun }\n// GlyphRun: glyph IDs + positions + advances
```

## Acceptance Criteria

- [ ] Text shaper produces correct glyph positions for Latin text
- [ ] Kerning pairs are applied correctly in glyph positions
- [ ] GlyphRunDescriptor carries all needed data for GPU rendering

## Required Evidence

- Shaped text GPU rendering fixture dump for Latin text sample
- Glyph position validation vs reference shaping output
- Kerning pair test dumps

## Fallback / Refusal Behavior

Text shaping failure emits stable diagnostic; unshaped text refused by GPU route.

## Dashboard Impact

- Expected row: `gpu-renderer.m20.text-shaper-integration`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TextShaper*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M20`
- `area:text-shaper`
