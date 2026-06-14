---
id: "KFONT-M5-002"
title: "Replace basic grapheme segmenter"
status: "proposed"
milestone: "M5"
priority: "P0"
owner_area: "unicode"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001"]
legacy_gate: null
---

# KFONT-M5-002 - Replace basic grapheme segmenter

## PM Note

Ce ticket empeche Kanvas de couper les accents, emoji ZWJ ou marques indic en morceaux invisibles pour l'utilisateur.

## Problem

The current basic segmentation boundary is too narrow for the target script matrix. Combining marks, emoji modifiers, ZWJ sequences, regional indicators, Indic virama sequences, and variation selectors must remain in stable grapheme clusters before shaping, fallback, paragraph layout, and hit testing consume text ranges.

## Scope

- Implement UAX #29 extended grapheme cluster segmentation from the pinned `UnicodeDataSet`.
- Cover CR/LF/control boundaries, Hangul syllable rules, Extend/SpacingMark/Prepend, ZWJ and Extended_Pictographic, regional indicator pairs, emoji modifiers, variation selectors, and Indic virama-related cluster behavior required by the script matrix.
- Produce cluster records with UTF-16 range, code point range, cluster level, source text hash, and Unicode data version.
- Emit `unicode-segments.json` for positive and refusal fixtures.
- Provide deterministic diagnostics when segmentation cannot run because required Unicode data is absent or version-mismatched.

## Non-Goals

- Do not implement word, sentence, or line breaking policy here.
- Do not perform GSUB/GPOS shaping or glyph fallback.
- Do not render emoji or color glyphs.
- Do not call ICU, platform segmenters, browser APIs, or regex engines as the normative cluster oracle.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class GraphemeCluster(
    val clusterIndex: Int,
    val utf16Range: IntRange,
    val codePointRange: IntRange,
    val breakBefore: GraphemeBreakDecision,
    val properties: List<GraphemeBreakClass>,
)

class GraphemeClusterer(
    private val unicode: UnicodeDataSet,
    private val rules: GraphemeRuleTable,
) {
    fun segment(input: TextInput): GraphemeSegmentationResult
}

data class GraphemeSegmentationResult(
    val unicodeVersion: UnicodeVersion,
    val clusters: List<GraphemeCluster>,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] UAX #29 fixture strings produce stable cluster boundaries for ASCII, accented Latin, Hangul, combining marks, emoji modifier, emoji ZWJ, regional indicator, and Indic virama cases.
- [ ] Cluster dumps record Unicode version, original UTF-16 ranges, code point ranges, and per-boundary rule decisions.
- [ ] Variation selector sequences stay inside one cluster when the pinned data marks the selector appropriately.
- [ ] Malformed input with isolated surrogate code units refuses or records a precise diagnostic without corrupting following cluster ranges.
- [ ] Repeated runs on the same input produce byte-identical `unicode-segments.json`.

## Required Evidence

- `unicode-segments.json` containing input text hash, Unicode version, clusters, per-boundary rule IDs, and diagnostics.
- Fixtures: `grapheme-latin-combining.txt`, `grapheme-emoji-zwj.txt`, `grapheme-regional-indicators.txt`, `grapheme-devanagari-virama.txt`, `grapheme-variation-selector.txt`, `grapheme-isolated-surrogate.txt`.
- Diagnostics asserted in tests: `text.shaping.unicode-data-version-mismatch`, `text.shaping.cluster-invariant-failed`, `text.unicode.invalid-scalar`.
- Diff evidence that cluster boundaries are not derived from the JDK Unicode version.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.unicode.grapheme-rule-unsupported`, `text.unicode.cluster-boundary-invalid`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Replace basic grapheme segmenter`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Grapheme*'
```

## Status Notes

- `proposed`: Grapheme cluster behavior depends on the pinned Unicode data ticket.
- Move to `ready` only after target fixture strings and dump fields are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:tracked-gap`
