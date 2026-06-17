---
id: "KFONT-M10-001"
title: "Complete COLRv0 plan to artifact path"
status: "done"
milestone: "M10"
priority: "P0"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M6-001", "KFONT-M9-002"]
legacy_gate: null
---

# KFONT-M10-001 - Complete COLRv0 plan to artifact path

## PM Note

Ce ticket transforme les glyphes COLRv0 en plans de couches vérifiables, sans promettre encore leur rendu GPU.

## Problem

COLRv0 support is not just table discovery. Kanvas needs a deterministic `ColorGlyphPlan` that resolves CPAL palette selection, base glyph records, ordered layer glyphs, colors, bounds, and monochrome fallback decisions. Without this path, dashboard rows can mistake color metadata parsing for real color glyph support.

## Scope

- Consume M2 COLR/CPAL table facts for COLRv0 base glyph and layer records.
- Resolve palette identity, palette overrides, layer order, layer glyph IDs, resolved colors, variation-insensitive facts, and color glyph bounds.
- Create `ColorGlyphPlan` entries that reference M9 `GlyphArtifactPlan` keys and can be registered later by M11.
- Emit `color-glyph-plan.json` with COLRv0 layer list, CPAL palette facts, source typeface ID, glyph ID, route diagnostics, and fallback policy.
- Add malformed COLR/CPAL diagnostics while preserving outline fallback only when a valid outline exists and style permits monochrome fallback.

## Non-Goals

- Do not implement COLRv1 paint graphs in this ticket.
- Do not rasterize, composite, or GPU-render the color glyph plan.
- Do not decode bitmap emoji or SVG glyph payloads.
- Do not count COLRv0 metadata parsing as rendering support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class COLRv0LayerPlan(
    val layerIndex: Int,
    val glyphId: GlyphId,
    val paletteEntry: PaletteEntryRef,
    val resolvedColor: ResolvedGlyphColor,
    val outlinePlanRef: OutlineGlyphPlanRef,
)

data class ColorGlyphPlan(
    val glyphId: GlyphId,
    val typefaceId: TypefaceID,
    val palette: FontPaletteID,
    val route: ColorGlyphRoute = ColorGlyphRoute.COLRv0,
    val layers: List<COLRv0LayerPlan>,
    val bounds: RectF,
    val fallbackPolicy: ColorGlyphFallbackPolicy,
    val diagnostics: List<TextDiagnostic>,
)
```

## Acceptance Criteria

- [x] COLRv0 fixtures produce ordered layer plans with palette identity, resolved colors, layer glyph IDs, and bounds.
- [x] Malformed CPAL or COLR data emits `text.color.CPAL-malformed` or `text.color.COLR-malformed` with glyph and table offsets where available.
- [x] Monochrome outline fallback is recorded only when the route policy accepts it and the dump states that COLRv0 was not used.
- [x] `color-glyph-plan.json` is deterministic for repeated runs with the same font bytes and palette selection.
- [x] M11 can consume the plan as a typed `ColorGlyphPlan` without reading COLR/CPAL tables.

## Required Evidence

- `color-glyph-plan.json` fixture for a base glyph with multiple COLRv0 layers and a non-default palette.
- Malformed COLR/CPAL refusal fixtures with route diagnostics.
- Fallback fixture showing a color glyph route refusal and accepted monochrome outline fallback.

## Fallback / Refusal Behavior

- Missing or malformed optional color tables refuse the color route and preserve outline glyph support only when valid and explicitly allowed.
- Unsupported palette selection emits a stable color diagnostic instead of substituting a host palette.
- The ticket remains `tracked-gap` until layer plan fixtures and diagnostics are attached.

## Dashboard Impact

- Expected row: `COLRv0 color glyph plan`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless `ColorGlyphPlan` evidence and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.parsesCOLRV0BaseGlyphsFromRawTableBytes --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV0PaintGraphWithPaletteIndexes --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV0ColorGlyphPlanWithPaletteOverridesArtifactKeysAndDeterministicDump --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.fallsBackToOutlineWhenCOLRPaletteResolutionFailsAndFallbackIsAllowed
```

## Status Notes

- `proposed`: Establishes the first color glyph plan shape consumed by later COLRv1 and GPU handoff work.
- Move to `ready` only after COLRv0 dump fields and palette diagnostic names are reviewed.
- `done`: `ColorGlyphPlan` now carries typed COLRv0 layer order, palette facts, bounds, fallback policy, and M9-derived artifact-key hashes with checked-in deterministic dump evidence and fresh refusal/fallback validations; COLRv1, bitmap, SVG, emoji sequence planning, and GPU route registration remain separate non-claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
