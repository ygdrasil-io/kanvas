---
id: "KFONT-M8-001"
title: "Expand `TextStyle` and paragraph style contracts"
status: "proposed"
milestone: "M8"
priority: "P0"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001", "KFONT-M6-001"]
legacy_gate: null
---

# KFONT-M8-001 - Expand `TextStyle` and paragraph style contracts

## PM Note

Ce ticket clarifie les options de texte riche que Kanvas pourra promettre sans dépendre d'un moteur natif.

## Problem

The paragraph target needs immutable, dumpable style contracts before rich layout can be validated. Current placeholder style language is too small to carry font families, fallback preference, OpenType features, variation coordinates, palette choices, decoration facts, height behavior, and placeholder interactions into shaping and layout. Without this contract, later paragraph tickets cannot prove which style facts affected shaping, line metrics, glyph artifact planning, or refusal diagnostics.

## Scope

- Define the value-object contract for `TextStyle`, `ParagraphStyle`, `PlaceholderStyle`, `TextHeightBehavior`, `TextAlign`, `TextDirection`, and `EllipsisPolicy`.
- Record stable UTF-16 text ranges for style spans and placeholders in the immutable `ParagraphInput` snapshot emitted by `ParagraphBuilder`.
- Include font families, fallback preference, size, weight, width, slant, synthetic style policy, locale/script hints, OpenType features, variation coordinates, palette and color-font options, color or material reference, decorations, spacing, height, and baseline facts.
- Add validation diagnostics for non-finite width/height, negative font size, invalid variation coordinates, invalid placeholder ranges, and unsupported baseline or strut policy.
- Emit a deterministic `paragraph-input.json` dump with input hash, Unicode data version, style span list, placeholder span list, and validation diagnostics.

## Non-Goals

- Do not implement line breaking, shaping segmentation, hit testing, placeholders, or GPU handoff in this ticket.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.
- Do not promote paragraph support until downstream layout and evidence tickets consume this contract.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class TextStyle(
    val fontFamilies: List<FontFamilyRef>,
    val fallbackPreference: FallbackPreference,
    val fontSizePx: Float,
    val weight: FontWeight,
    val width: FontWidth,
    val slant: FontSlant,
    val locale: LocaleTag?,
    val scriptHint: UnicodeScript?,
    val features: List<OpenTypeFeatureSetting>,
    val variations: VariationCoordinates,
    val palette: FontPaletteSelection?,
    val foreground: TextForeground,
    val decoration: TextDecorationSpec?,
    val letterSpacingPx: Float,
    val wordSpacingPx: Float,
    val height: TextHeightBehavior,
)

data class ParagraphInput(
    val text: String,
    val paragraphStyle: ParagraphStyle,
    val styleRuns: List<StyleRange<TextStyle>>,
    val placeholders: List<StyleRange<PlaceholderStyle>>,
    val unicodeVersion: UnicodeVersion,
    val inputHash: StableHash,
)
```

## Acceptance Criteria

- [ ] `ParagraphBuilder` produces an immutable `ParagraphInput` snapshot; mutating the builder after snapshot creation cannot change the dump.
- [ ] `TextStyle` carries all shaping-affecting and layout-affecting fields listed in scope, and each field appears in the dump preimage.
- [ ] Style and placeholder ranges are stable, ordered, non-overlapping where required, and validated against UTF-16 text bounds.
- [ ] Invalid numeric constraints return `text.paragraph.invalid-constraint` or a narrower diagnostic with the field name and offending range.
- [ ] `paragraph-input.json` is deterministic for repeated runs with the same text, style values, and Unicode data version.

## Required Evidence

- `paragraph-input.json` fixture covering multiple style spans, OpenType features, variation coordinates, palette selection, decoration, and placeholder ranges.
- Negative fixture for non-finite layout width, negative font size, invalid range, and unsupported baseline/strut policy.
- Diagnostic snapshot using `text.paragraph.invalid-constraint`, `text.paragraph.invalid-style-range`, or a narrower accepted reason code.

## Fallback / Refusal Behavior

- Invalid style contracts refuse before shaping with a stable `text.paragraph.*` diagnostic.
- Unsupported style fields remain explicit in `ParagraphInput` and cannot be silently dropped to host/platform defaults.
- The ticket remains `tracked-gap` until the contract and dump evidence are reviewed.

## Dashboard Impact

- Expected row: `Paragraph style contracts`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, because this ticket only establishes paragraph input contracts and evidence shape.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*ParagraphStyle*'
```

## Status Notes

- `proposed`: Contract ticket is ready for review against the paragraph API target before implementation starts.
- Move to `ready` only after the field list, diagnostic names, and dump schema are accepted.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
