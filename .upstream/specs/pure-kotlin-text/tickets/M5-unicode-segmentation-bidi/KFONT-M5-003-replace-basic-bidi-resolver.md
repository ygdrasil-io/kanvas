---
id: "KFONT-M5-003"
title: "Replace basic bidi resolver"
status: "done"
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

- [x] Mixed Latin/Hebrew, Latin/Arabic, numbers inside RTL text, neutral punctuation, and isolate-control fixtures produce stable logical bidi runs.
- [x] `bidi-runs.json` records Unicode version, paragraph direction, embedding levels, source controls, and per-rule trace facts.
- [x] Single-run shaping requests for text that needs paragraph-level ordering emit a diagnostic without pretending paragraph layout was performed.
- [x] Bidi run ranges align to grapheme cluster boundaries from KFONT-M5-002.
- [x] Invalid or unbalanced bidi controls produce stable diagnostics and do not corrupt following text ranges.

## Required Evidence

- `bidi-runs.json` with input hash, Unicode version, cluster references, resolved bidi classes, embedding levels, run directions, paragraph direction, and diagnostics.
- Fixtures: `bidi-hebrew-latin.txt`, `bidi-arabic-number-neutral.txt`, `bidi-isolate-controls.txt`, `bidi-embedding-override-controls.txt`, `bidi-unbalanced-controls.txt`, `bidi-single-run-needs-paragraph.txt`.
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

- `done`: `DefaultBidiResolver` and the default `BasicBidiResolver()` path now
  emit bounded M5 run-level bidi facts backed by
  `reports/font/fixtures/expected/unicode/bidi-runs.json`.
- The default shaping path propagates
  `text.shaping.paragraph-bidi-required` when detailed bidi resolution detects
  mixed-direction text that still requires M8 paragraph ordering.
- The bounded fixture matrix includes isolates plus explicit
  `LRE`/`RLE`/`LRO`/`RLO` and `PDF` controls. Paired bracket facts are not
  available in the current generated `UnicodeDataSet` evidence, so this ticket
  makes no paired bracket resolution claim.
- Quality review remediation added regression coverage for malformed UTF-16,
  text ranges that split surrogate pairs, and mixed embedding/isolate closer
  families; the code-quality re-review verdict is `ACCEPT`.
- Evidence report:
  `reports/pure-kotlin-text/2026-06-16-kfont-m5-003-bidi-resolver.md`.
- Fresh validation:
  `rtk ./gradlew --no-daemon :font:text:test --tests '*Bidi*'`,
  `rtk ./gradlew --no-daemon :font:text:test`,
  `rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py`,
  `rtk python3 scripts/validate_pure_kotlin_text_dump_index.py`, and
  `rtk git diff --check`.
- No external UAX #9 comparison was used as normative evidence; future external
  comparisons remain drift-only.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:tracked-gap`
