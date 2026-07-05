# Paragraph Engine

Status: Draft
Date: 2026-06-13

## Purpose

Define the complete pure Kotlin paragraph layout target. The engine is inspired
by Skia Paragraph concepts, adapted for Kanvas value-object contracts and the
new GPU renderer handoff.

This spec owns rich text layout, line breaking, bidi paragraph ordering,
wrapping, truncation, metrics, selection, placeholders, and hit testing. It
uses the font resolver and shaping engine; it does not parse fonts or produce
GPU artifacts directly.

## API Shape

The final target exposes a Kanvas-native paragraph API with Skia Paragraph
inspired concepts:

- `ParagraphStyle`;
- `TextStyle`;
- `ParagraphBuilder`;
- `Paragraph`;
- `ParagraphLayoutResult`;
- `LineMetrics`;
- `TextBox`;
- `GlyphPosition`;
- `PlaceholderStyle`;
- `FontCollection`;
- `TextHeightBehavior`;
- `TextAlign`;
- `TextDirection`;
- `EllipsisPolicy`.

The `:kanvas` compatibility facade may expose compatibility builders, but the native
model lives under `org.graphiks.kanvas.text.paragraph`.

## Builder Contract

The builder supports:

- push/pop text styles;
- append text;
- append placeholders;
- paragraph-level direction, locale, max lines, alignment, ellipsis, and width
  constraints;
- style spans with stable text ranges;
- font feature and variation settings;
- color and decoration metadata needed by rendering;
- baseline and strut settings when accepted by the implementation.

Builder output is immutable after layout starts.

## Text Style

`TextStyle` target fields:

- font families and fallback preference;
- font size;
- font weight, width, slant, and synthetic style policy;
- locale and script hints;
- color or foreground material reference;
- decoration, decoration thickness, and decoration style;
- letter spacing and word spacing;
- height multiplier and baseline behavior;
- OpenType features;
- variation coordinates;
- palette and color font options;
- placeholder interaction facts.

Paint-like effects that are not text-specific are normalized later by the
adapter into renderer material descriptors.

## Layout Pipeline

Target pipeline:

```text
ParagraphBuilder
  -> immutable paragraph input
  -> style run resolution
  -> font fallback resolution
  -> Unicode paragraph segmentation
  -> bidi paragraph analysis
  -> shaping requests
  -> line break opportunity discovery
  -> line fitting and wrapping
  -> visual run ordering
  -> line metrics and boxes
  -> ParagraphLayoutResult
```

The paragraph engine owns paragraph-level bidi resolution and visual line
ordering. The shaping engine owns glyph substitution and positioning inside
resolved runs.

## Line Breaking And Wrapping

The complete target supports:

- Unicode line break rules for the pinned Unicode data version;
- grapheme-cluster-safe breaks;
- word boundary behavior needed by Kanvas UI text;
- explicit newline handling;
- width-constrained line fitting;
- soft wrap enable/disable;
- max lines;
- ellipsis/truncation;
- alignment after shaping and line fitting.

Dictionary-based or language-specific break refinement may be added per locale.
When absent for a locale that needs it, diagnostics must say so rather than
silently claiming full language-specific line breaking.

## Bidi Paragraph Layout

The paragraph engine supports:

- paragraph base direction;
- embedding levels;
- mixed LTR/RTL runs;
- neutral resolution for common UI text;
- visual run ordering per line;
- hit testing across bidi boundaries;
- selection boxes across bidi lines.

The engine must record enough bidi facts for evidence dumps and debugging.

## Placeholders

Placeholders support:

- inline object dimensions;
- baseline alignment;
- above/below/baseline metrics;
- range mapping in text;
- line height participation;
- hit testing and selection boxes.

The renderer later consumes placeholders as separate draw commands or layer
facts; paragraph layout only reserves and reports geometry.

## Metrics And Boxes

`ParagraphLayoutResult` must expose:

- width and height;
- min and max intrinsic widths;
- alphabetic and ideographic baselines where available;
- per-line ascent, descent, leading, baseline, width, hard-break flag, and
  ellipsis state;
- glyph bounds and run bounds;
- selection boxes for text ranges;
- word and grapheme boundary queries;
- hit testing by point and by text position.

Metrics must be deterministic for a fixed font source set and Unicode data
version.

## Decorations

The paragraph engine computes decoration geometry facts:

- underline;
- overline;
- line-through;
- decoration style classification;
- decoration thickness;
- decoration skip policy when accepted.

The GPU renderer owns the final draw route for decoration geometry.

## Output Contracts

`ParagraphLayoutResult` contains:

- immutable input hash;
- Unicode data version;
- line list;
- visual run list;
- `GlyphRunDescriptor` references or embedded descriptors;
- placeholder boxes;
- selection/hit-test data;
- layout diagnostics;
- shaping diagnostics merged by text range;
- fallback diagnostics.

The result is the semantic text oracle used by glyph artifact planning and
validation.

## Error Handling

The paragraph engine must return structured diagnostics for:

- missing font fallback;
- unsupported script in a shaped range;
- unsupported locale-specific line breaking;
- invalid placeholder metrics;
- max-line ellipsis failure;
- glyph cluster invariant failures;
- non-finite layout constraints;
- host-dependent fallback.

Invalid numeric constraints fail early. Unsupported text behavior refuses or
diagnoses by range; it must not draw a different string silently.

## Acceptance Criteria

- Paragraph layout works without native text engines.
- Builder output is immutable and dumpable.
- Rich text, fallback, shaping, bidi, wrapping, ellipsis, placeholders, metrics,
  selection, and hit testing have fixtures.
- `ParagraphLayoutResult` can be consumed by glyph artifact planning without
  re-shaping text.
- Skia Paragraph drift reports are optional and non-normative.
