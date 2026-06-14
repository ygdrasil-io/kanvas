---
id: "KFONT-M10-006"
title: "Promote PNG bitmap glyph artifacts"
status: "proposed"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M9-002"]
legacy_gate: ["scaledemoji_rendering"]
---

# KFONT-M10-006 - Promote PNG bitmap glyph artifacts

## PM Note

Ce ticket couvre les glyphes bitmap PNG embarqués sans ouvrir la porte aux codecs natifs ou formats non ciblés.

## Problem

Bitmap emoji and color glyphs need a typed artifact route, not a general image fallback. The target supports PNG payloads from CBDT/CBLC and sbix only, with deterministic strike selection, origin placement, alpha/premul policy, decode facts, CPU oracle hash, and refusals for non-PNG or malformed payloads. Without this ticket, `scaledemoji_rendering` can remain blocked by an imprecise "bitmap unavailable" label.

## Scope

- Build `BitmapGlyphPlan` for CBDT/CBLC PNG strikes and sbix PNG strikes using pure Kotlin decode behavior.
- Record strike selection policy, requested size, selected strike size, glyph origin, placement bounds, scaling policy, premul/alpha policy, source payload hash, decoded pixel hash, and diagnostics.
- Emit `bitmap-glyph-plan.json` with fixture provenance and expected GPU handoff artifact type.
- Refuse non-PNG CBDT/sbix payloads, unavailable strikes, malformed PNG payloads, and unsupported platform-specific bitmap formats.
- Keep M11 responsible for texture ownership, upload, sampling, and GPU evidence.

## Non-Goals

- Do not implement JPEG, TIFF, BGRA bitmap strikes, or platform bitmap payloads.
- Do not route embedded glyph images through a general image codec unless a future text spec accepts it.
- Do not claim emoji sequence support; KFONT-M10-009 owns sequence planning.
- Do not retire `scaledemoji_rendering` without GPU route evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class BitmapGlyphPlan(
    val glyphId: GlyphId,
    val typefaceId: TypefaceID,
    val tableFamily: BitmapGlyphTableFamily,
    val requestedSizePx: Float,
    val selectedStrike: BitmapStrikeRef,
    val origin: Vec2I,
    val bounds: RectI,
    val alphaPolicy: BitmapAlphaPolicy,
    val scalingPolicy: BitmapScalingPolicy,
    val sourcePayloadHash: StableHash,
    val decodedPixelHash: StableHash,
    val diagnostics: List<TextDiagnostic>,
)
```

## Acceptance Criteria

- [ ] CBDT/CBLC PNG and sbix PNG fixtures produce distinct `BitmapGlyphPlan` dumps.
- [ ] Strike selection records requested size, selected strike size, fallback/scaling decision, and unavailable-strike diagnostics.
- [ ] Non-PNG payloads emit `text.bitmap.payload-format-unsupported`.
- [ ] Malformed PNG payloads emit `text.bitmap.PNG-decode-failed` with payload hash and glyph ID.
- [ ] The plan can be handed to M11 without font table reads or native codec calls.

## Required Evidence

- `bitmap-glyph-plan.json` fixtures for CBDT/CBLC PNG, sbix PNG, unavailable strike, malformed PNG, and non-PNG payload refusal.
- CPU decoded pixel hash and source payload hash for positive fixtures.
- Diagnostic snapshots for `text.bitmap.strike-unavailable`, `text.bitmap.PNG-decode-failed`, and `text.bitmap.payload-format-unsupported`.

## Fallback / Refusal Behavior

- Unavailable bitmap strikes may fall back to COLR/SVG/outline only if route policy records the rejected bitmap route and accepted substitute.
- Non-PNG formats refuse rather than invoking platform codecs.
- Legacy gate `scaledemoji_rendering` remains open until bitmap glyph plans have M11 texture/upload evidence.

## Dashboard Impact

- Expected row: `PNG bitmap glyph artifacts`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless PNG fixture evidence and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*BitmapGlyph*'
```

## Status Notes

- `proposed`: Promotes the embedded PNG glyph artifact contract while keeping renderer proof gated.
- Move to `ready` only after PNG decode provenance, alpha policy, and strike-selection dumps are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
- `legacy:scaledemoji_rendering`
