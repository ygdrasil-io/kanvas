---
id: KGPU-M12-001
title: "Finalize SFNT parser + glyf/CFF/CFF2 scaler with deterministic output"
status: done
milestone: M12
priority: P0
owner_area: text-shaper
claim_impact: TargetNative
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M12-001 - Finalize SFNT parser + glyf/CFF/CFF2 scaler with deterministic output

## PM Note

Le parseur SFNT et le scaler sont la fondation du texte GPU. Sans sortie déterministe, les atlas de glyphes seront invalides et la route texte ne pourra jamais être activée.

## Problem

The pure-Kotlin text stack needs a finalized SFNT parser with deterministic glyf/CFF/CFF2 scaling before any GPU text route can accept glyph payloads. Without this, GPU text atlas uploads would receive unreliable glyph bitmaps.

## Scope

- Finalize SFNT table parsing (head, hhea, maxp, cmap, loca, glyf, CFF, CFF2)
- Deliver deterministic glyph outline scaler with fixed-point math
- Add cache-keyed glyph metrics lookup with strike identity
- Produce contract fixture dumps for Latin glyph set validation

## Non-Goals

- No bidi or complex text shaping
- No COLRv1, SVG glyphs, or color fonts
- No variable font axis support

## Spec Sources

- .upstream/specs/pure-kotlin-text/

## Graphite Algorithm References

- [`GFX-GFX_TEXT_ATLAS_CONFIG`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-text-atlas-config) - source src/gpu/graphite/text/TextAtlasManager.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class SfntParser { fun parse(bytes: ByteArray): FontTables }\nclass GlyphScaler { fun scale(glyphId: Int, size: Float): GlyphBitmap }
```

## Acceptance Criteria

- [ ] SFNT tables parse correctly for Latin TrueType/OpenType fonts
- [ ] Glyph scaler produces identical output for same glyph+size+strike inputs
- [ ] Cache invalidation triggers on strike or font change
- [ ] Contract fixture dumps pass deterministic comparison

## Required Evidence

- SFNT parser unit test output for TTF and OTF
- Glyph scaler deterministic diff transcript
- Latin glyph set contract fixture dump

## Fallback / Refusal Behavior

Unparseable fonts emit stable diagnostic and refuse GPU text route.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.text.sfnt-parser-scaler`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :font:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:text-shaper`
