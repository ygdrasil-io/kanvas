# Shaping And Layout Boundary

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`
Current implementation: `font/text/src/main/kotlin/org/graphiks/kanvas/text/shaping/`

The target portable shaping contract lives in the pure Kotlin `:font:text`
implementation. Compatibility APIs in `:kanvas` must adapt to that contract
rather than define a second shaping engine.

## Purpose

Define how Kanvas separates simple text drawing from explicit shaping. This
spec owns the font-side shaping boundary, glyph runs, clusters, fallback runs,
and diagnostics.

It does not own paragraph layout, line breaking, general text UI, or native
shaping integration.

## Current Policy

`SkCanvas.drawString` remains simple by default:

```text
Unicode code points
  -> cmap glyph ids
  -> advance-based positions
  -> glyph paths
  -> drawPath
```

It must not silently run complex shaping, ligature substitution, bidi paragraph
layout, or multi-font fallback.

Callers that need shaping must use `SkShaper` or a future explicit text-layout
entry point.

## Current `SkShaper` Scope

The current portable shaper provides:

- explicit `shape(text, font, features)` entry point;
- bidi direction segmentation;
- script segmentation;
- glyph runs with original UTF-16 clusters;
- text-local positions;
- missing glyph diagnostics;
- optional fallback font provider;
- optional glyph positioning provider;
- conservative feature flags.

This is an internal deterministic shaper boundary. It is not full HarfBuzz
behavior.

## Feature Policy

| Feature | Current target behavior |
|---|---|
| Standard ligatures | Opt-in and conservative; no implicit `drawString` use. |
| Arabic joining | Opt-in presentation-form approximation only when gated by feature and script. |
| Indic reordering | Opt-in limited pre-base vowel reordering; not broad Indic shaping. |
| Mark positioning | Requires an explicit positioning provider; missing provider must diagnose. |
| Cursive attachment | Requires an explicit positioning provider; missing provider must diagnose. |
| Script/language gating | Feature requests must match segment script/language or emit diagnostics. |
| Multi-font fallback | Opt-in provider; no hidden platform fallback. |

## Target Shaping Route

Each shaped run should expose:

```text
ShapingRoute {
  mode: simple | portable-shaper | unsupported
  direction
  script
  language
  featureSet
  fallbackPolicy
  diagnostics
}
```

The route must be serializable for GM/dashboard evidence.

## Cluster Contract

Clusters must:

- map glyphs back to original UTF-16 text offsets;
- remain stable for deterministic fixtures;
- preserve enough data for future hit-testing and text blob evidence;
- not claim full paragraph layout support.

If a feature changes glyph count, the resulting clusters must be documented in
the test expectation.

## Fallback Runs

Fallback font use must be explicit:

- a fallback provider must be supplied;
- fallback events emit diagnostics;
- missing fallback glyphs emit diagnostics;
- fallback font identity appears in the glyph run descriptor.

Fallback cannot call platform font APIs implicitly.

## Unsupported Shaping

The following remain unsupported until internal implementation evidence lands:

- full GSUB feature interpretation;
- full GPOS mark/cursive positioning;
- bidirectional paragraph layout beyond current run segmentation;
- full Arabic shaping;
- full Indic shaping;
- emoji ZWJ sequence shaping;
- grapheme cluster tailoring for all scripts;
- HarfBuzz parity.

Recommended reason codes:

| Code | Use |
|---|---|
| `font.shaping-feature-unsupported` | Requested shaping feature is outside internal support. |
| `font.shaping-provider-required` | Mark/cursive or equivalent positioning needs an explicit provider. |
| `font.shaping-fallback-missing` | Fallback was requested but no fallback font covered the code point. |
| `font.shaping-native-engine-unavailable` | Test requires HarfBuzz/CoreText/DirectWrite behavior outside this target. |

## Relationship To OpenType

`OpenTypeTypeface` owns table data, glyph lookup, advances, paths, and pair
positioning primitives. `SkShaper` owns text segmentation, feature policy,
fallback run splitting, clusters, and external positioning provider hooks.

Do not move full shaping into the OpenType parser.

## Relationship To Text Blobs

`SkTextBlob` and `SkTextBlobBuilder` should be the stable interchange for
explicit glyph runs when callers want to bypass string shaping. The font layer
must preserve:

- glyph ids;
- per-glyph positions;
- font identity;
- bounds where available.

Text blob rendering may use the same glyph representation routes as string
text, but it should not reshape glyph ids.

## Acceptance Criteria

- Simple text draws remain deterministic and unshaped by default.
- Shaped text has explicit run, cluster, direction, script, fallback, and
  diagnostic evidence.
- Unsupported shaping requests fail visibly or emit diagnostics.
- No external shaping library is required for the default target.
- New shaping support must include fixtures that prove cluster and glyph output.
