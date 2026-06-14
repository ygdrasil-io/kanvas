---
id: "KFONT-M5-003"
title: "Replace basic bidi resolver"
status: "proposed"
milestone: "M5"
priority: "P0"
owner_area: "unicode"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001"]
legacy_gate: null
---

# KFONT-M5-003 - Replace basic bidi resolver

## PM Note

Ce ticket donne des runs RTL/LTR fiables au shaping, sans laisser le rendu dependre d'un moteur texte de la plateforme.

## Problem

Complex scripts such as Hebrew and Arabic require bidi levels, run direction, and isolate/embedding behavior before shaping and paragraph layout can make correct decisions. A basic resolver cannot safely handle mixed LTR/RTL text, numbers, neutrals, isolates, or paragraph-level diagnostics.

## Scope

- Implement the UAX #9 run-level bidi resolver needed by shaping, using pinned Bidi_Class data.
- Resolve paragraph base direction, explicit embeddings/overrides/isolates, weak and neutral types, paired bracket facts when available, and stable run boundaries.
- Serialize logical ranges, embedding levels, resolved direction, paragraph direction, isolate state, and diagnostics in `bidi-runs.json`.
- Expose diagnostics when paragraph-level visual ordering is required but the caller requested only single-run shaping.
- Keep paragraph line ordering as a later M8 responsibility while providing enough run facts for M6 shaping fixtures.

## Non-Goals

- Do not implement paragraph visual line layout, line breaking, ellipsis, hit testing, or selection boxes.
- Do not shape Arabic joining forms or Hebrew marks; M6 owns GSUB/GPOS behavior.
- Do not call ICU, Java Bidi, browser layout, CoreText, DirectWrite, or platform shapers as normative bidi behavior.
- Do not silently strip bidi control characters from evidence dumps.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class BidiResolver(
    private val unicode: UnicodeDataSet,
    private val options: BidiResolverOptions,
) {
    fun resolve(input: TextInput, clusters: List<GraphemeCluster>): BidiResolution
}

data class BidiRun(
    val logicalUtf16Range: IntRange,
    val clusterRange: IntRange,
    val embeddingLevel: Int,
    val direction: TextDirection,
    val paragraphDirection: TextDirection,
    val sourceControls: List<BidiControl>,
)

data class BidiTraceEvent(
    val rule: String,
    val range: IntRange,
    val before: BidiClass,
    val after: BidiClass,
)
```

## Acceptance Criteria

- [ ] Mixed Latin/Hebrew, Latin/Arabic, numbers inside RTL text, neutral punctuation, and isolate-control fixtures produce stable logical bidi runs.
- [ ] `bidi-runs.json` records Unicode version, paragraph direction, embedding levels, source controls, and per-rule trace facts.
- [ ] Single-run shaping requests for text that needs paragraph-level ordering emit a diagnostic without pretending paragraph layout was performed.
- [ ] Bidi run ranges align to grapheme cluster boundaries from KFONT-M5-002.
- [ ] Invalid or unbalanced bidi controls produce stable diagnostics and do not corrupt following text ranges.

## Required Evidence

- `bidi-runs.json` with input hash, Unicode version, cluster references, resolved bidi classes, embedding levels, run directions, paragraph direction, and diagnostics.
- Fixtures: `bidi-hebrew-latin.txt`, `bidi-arabic-number-neutral.txt`, `bidi-isolate-controls.txt`, `bidi-unbalanced-controls.txt`, `bidi-single-run-needs-paragraph.txt`.
- Diagnostics asserted in tests: `text.shaping.unicode-data-version-mismatch`, `text.shaping.paragraph-bidi-required`, `text.unicode.bidi-control-unbalanced`.
- Review note labeling any external UAX #9 conformance comparison as drift-only.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.bidi-context-required`, `text.shaping.bidi-data-malformed`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Replace basic bidi resolver`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Bidi*'
```

## Status Notes

- `proposed`: Bidi run facts are owned by M5; visual line ordering remains M8.
- Move to `ready` only after bidi control handling and fixture texts are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:tracked-gap`
