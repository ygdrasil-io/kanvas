---
id: "KFONT-M9-001"
title: "Complete `GlyphStrikeKey`"
status: "proposed"
milestone: "M9"
priority: "P0"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M3-001", "KFONT-M6-001"]
legacy_gate: null
---

# KFONT-M9-001 - Complete `GlyphStrikeKey`

## PM Note

Ce ticket rend l'identité d'un glyph reproductible, condition nécessaire pour comparer les caches et les atlas.

## Problem

Glyph artifacts cannot be cached or invalidated safely unless every rendering-affecting fact is part of the key. The current ticket text does not name the facts that distinguish A8, SDF, outline, color, bitmap, SVG, palette, variation, transform, and Unicode-sensitive emoji output. Without a complete `GlyphStrikeKey`, later atlas and GPU handoff tickets can pass accidentally with stale or nondeterministic artifacts.

## Scope

- Define `GlyphStrikeKey` as a stable value object with deterministic serialization and compact hash.
- Include `TypefaceID`, glyph ID, source cluster facts when relevant, text size, variation coordinates, palette identity, representation route, mask format, transform bucket, subpixel bucket, edging/antialiasing mode, SDF spread/resolution, renderer descriptor version, and Unicode data version.
- Add key preimage dumps for A8, SDF, outline, COLR, bitmap PNG, SVG, and unsupported routes.
- Emit diagnostics for missing typeface identity, nondeterministic key fields, unsupported LCD requests, and route-specific key gaps.
- Keep GPU resource identity, atlas coordinates, live handles, and upload tokens out of the strike key.

## Non-Goals

- Do not implement A8 rasterization, SDF generation, atlas packing, or GPU upload here.
- Do not claim color/emoji rendering support; M10 owns those plan details.
- Do not use object identity, mutable cache addresses, or platform font handles in the key.
- Do not retire `dftext` or any GPU text gate from this contract-only work.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class GlyphStrikeKey(
    val typefaceId: TypefaceID,
    val glyphId: GlyphId,
    val clusterFingerprint: ClusterFingerprint?,
    val textSizePx: Float,
    val variations: VariationCoordinates,
    val palette: FontPaletteID?,
    val route: GlyphRepresentationRoute,
    val maskFormat: GlyphMaskFormat?,
    val transformBucket: TransformBucket,
    val subpixelBucket: SubpixelBucket,
    val edging: GlyphEdging,
    val sdf: SDFStrikeParams?,
    val rendererDescriptorVersion: RendererDescriptorVersion,
    val unicodeVersion: UnicodeVersion,
) {
    fun preimage(): GlyphStrikeKeyPreimage
    fun stableHash(): StableHash
}
```

## Acceptance Criteria

- [ ] Changing any rendering-affecting fact listed in scope changes the key preimage and compact hash.
- [ ] Repeated construction from deterministic font sources produces byte-identical key dumps.
- [ ] The key rejects nondeterministic object identity, host font handles, atlas coordinates, GPU handles, and upload tokens.
- [ ] Unsupported LCD requests are keyed only as refused requests and emit `text.glyph.LCD-future-research`.
- [ ] A `glyph-strike-key.json` fixture covers A8, SDF, outline, COLR, bitmap PNG, SVG, variation, palette, and Unicode-sensitive routes.

## Required Evidence

- `glyph-strike-key.json` fixtures with full preimage and compact hash for every target route family.
- Negative dump for missing `TypefaceID`, nondeterministic host source, and forbidden live-handle fields.
- Diagnostic snapshot using `text.glyph.cache-key-nondeterministic`, `text.glyph.LCD-future-research`, or a narrower accepted reason.

## Fallback / Refusal Behavior

- A glyph request without a deterministic strike key refuses before artifact generation.
- Unsupported representation requests remain explicit refused key records rather than falling back silently to A8 or outline.
- Host-dependent system font facts must be marked in the preimage and cannot become normative evidence without captured bytes.

## Dashboard Impact

- Expected row: `GlyphStrikeKey completeness`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, because this ticket only establishes glyph artifact identity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphStrikeKey*'
```

## Status Notes

- `proposed`: Blocks artifact planning, atlas invalidation, and GPU text handoff identity.
- Move to `ready` only after key fields, hash preimage format, and forbidden-field checks are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
