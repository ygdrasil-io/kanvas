---
id: "KFONT-M5-004"
title: "Add Script_Extensions itemizer"
status: "done"
milestone: "M5"
priority: "P0"
owner_area: "unicode"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001", "KFONT-M5-002"]
legacy_gate: null
---

# KFONT-M5-004 - Add Script_Extensions itemizer

## PM Note

Ce ticket donne au shaping le bon script par cluster, ce qui evite d'appliquer des features OpenType au mauvais alphabet.

## Problem

OpenType feature selection depends on script runs, but many code points are `Common`, `Inherited`, emoji, or have Script_Extensions rather than a single Script value. Without a deterministic itemizer, M6 cannot choose script tags, language systems, default features, or fallback boundaries reliably.

## Scope

- Build script runs from grapheme clusters using pinned Script and Script_Extensions data.
- Resolve `Common` and `Inherited` code points from neighboring strong script context while preserving diagnostics for ambiguous clusters.
- Map target script matrix rows to OpenType script tags: `latn`, `grek`, `cyrl`, `hebr`, `arab`, `deva`, `dev2`, `thai`, `hani`, `kana`, `hira`, `hang`, `Zsye`, and `Zsym`.
- Record cluster range, code point range, selected script, candidate extension set, language hint, and reason in `script-runs.json`.
- Provide refusal diagnostics for unsupported scripts and ambiguous extension sets when shaping would otherwise silently approximate.

## Non-Goals

- Do not implement default feature policy; KFONT-M6-006 owns feature enablement.
- Do not perform font fallback, glyph mapping, GSUB, or GPOS.
- Do not implement dictionary segmentation or paragraph line breaking.
- Do not depend on platform locale APIs as normative script classification.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class ScriptItemizer(
    private val unicode: UnicodeDataSet,
    private val targetMatrix: RequiredScriptMatrix,
) {
    fun itemize(input: TextInput, clusters: List<GraphemeCluster>): ScriptItemization
}

data class ScriptRun(
    val clusterRange: IntRange,
    val utf16Range: IntRange,
    val selectedScript: ScriptCode,
    val openTypeScriptTags: List<OpenTypeScriptTag>,
    val extensionCandidates: Set<ScriptCode>,
    val reason: ScriptResolutionReason,
)

data class ScriptExtensionsLookup(
    val codePoint: Int,
    val script: ScriptCode,
    val extensions: Set<ScriptCode>,
)
```

## Acceptance Criteria

- [x] Latin with combining marks, Greek polytonic marks, Hebrew niqqud, Arabic marks, Devanagari matras, Thai tone marks, CJK variation selectors, and emoji sequences itemize into stable script runs.
- [x] `script-runs.json` records selected script, OpenType script tag candidates, extension candidates, cluster ranges, and Unicode data version.
- [x] `Common` and `Inherited` clusters inherit script only when neighboring context makes the result deterministic.
- [x] Unsupported scripts emit `text.shaping.script-unsupported` with the affected range and candidate script facts.
- [x] Script run boundaries do not split grapheme clusters.

## Required Evidence

- `script-runs.json` with input hash, Unicode version, cluster references, selected script, OpenType tags, extension candidates, language hints, and diagnostics.
- Fixtures: `script-latin-combining.txt`, `script-greek-polytonic.txt`, `script-hebrew-niqqud.txt`, `script-arabic-marks.txt`, `script-devanagari-matra.txt`, `script-thai-tone.txt`, `script-cjk-vs.txt`, `script-emoji-zwj.txt`, `script-unsupported.txt`.
- Diagnostics asserted in tests: `text.shaping.script-unsupported`, `text.shaping.cluster-invariant-failed`, `text.shaping.unicode-data-version-mismatch`.
- Matrix coverage table tying each required script family to at least one itemization fixture.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.script-unsupported`, `text.shaping.script-run-ambiguous`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add Script_Extensions itemizer`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*ScriptItem*'
```

## Status Notes

- `proposed`: Script itemization is a prerequisite for M6 script-specific feature selection.
- Move to `ready` only after the required script matrix mapping is reviewed.
- `done`: `ScriptExtensionsItemizer` and `script-runs.json` now provide
  bounded KFONT-M5-004 itemization evidence for Latin, Greek, Hebrew, Arabic,
  Devanagari, Thai, CJK Script_Extensions context, emoji ZWJ context,
  unsupported scripts, ambiguous extension-only clusters, isolated TATWEEL
  without strong context, and neutral Common clusters between conflicting
  strong scripts.
- Fresh validation:
  `rtk ./gradlew --no-daemon :font:text:test --tests '*ScriptItem*'`,
  `rtk ./gradlew --no-daemon :font:text:test --tests '*UnicodeData*' --tests '*Grapheme*' --tests '*Bidi*'`,
  `rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py`,
  `rtk python3 scripts/validate_pure_kotlin_text_dump_index.py`, and
  `rtk git diff --check`.
- Evidence report:
  `reports/pure-kotlin-text/2026-06-16-kfont-m5-004-script-itemization.md`.
- Independent spec re-review verdict: `ACCEPT` by subagent
  `019ecf3d-67ab-7a53-8d8c-8ff325724c92`.
- Independent code-quality re-review verdict: `Ready to merge: Yes` by
  subagent `019ecf4a-e437-7221-bb48-63858d1120e4`.
- No external shaper or native engine was used as normative evidence.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M5`
- `area:unicode`
- `claim:tracked-gap`
