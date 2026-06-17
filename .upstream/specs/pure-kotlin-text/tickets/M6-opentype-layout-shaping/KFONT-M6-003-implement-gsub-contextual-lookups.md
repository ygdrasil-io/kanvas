---
id: "KFONT-M6-003"
title: "Implement GSUB contextual lookups"
status: "done"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-002"]
legacy_gate: null
---

# KFONT-M6-003 - Implement GSUB contextual lookups

## PM Note

Ce ticket permet les substitutions qui dependent du voisinage, indispensables aux alternates et formes contextuelles des scripts complexes.

## Problem

Many required script features need context-sensitive substitution, not just direct glyph replacement. Without bounded contextual matching, Kanvas cannot evaluate input sequence, class, and coverage-based rules, nor explain why a contextual rule matched or refused.

## Scope

- Implement GSUB contextual substitution LookupType 5 formats 1, 2, and 3.
- Match glyph sequence, class-based, and coverage-based contexts against the current glyph buffer.
- Apply nested substitution records only when context matches and cluster invariants remain valid.
- Emit `gsub-trace.json` events for context match attempts, selected rule, nested lookup application, and refusal diagnostics.
- Add cycle and re-entry guards for nested lookup application inside a contextual rule.

## Non-Goals

- Do not implement chaining contextual substitution, reverse chaining substitution, or extension substitution; KFONT-M6-010 owns those lookup classes.
- Do not implement Indic syllable reordering or Arabic joining policy in this ticket.
- Do not implement GPOS contextual positioning.
- Do not use HarfBuzz trace output as normative evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class GsubContextMatcher(
    private val classDefs: Map<ClassDefId, GlyphClassDef>,
    private val coverageTables: Map<CoverageId, GlyphCoverage>,
) {
    fun match(ruleSet: ContextRuleSet, cursor: GlyphCursor): ContextMatch?
}

data class ContextMatch(
    val lookupIndex: Int,
    val format: ContextFormat,
    val inputRange: IntRange,
    val matchedGlyphs: List<GlyphId>,
    val substitutions: List<NestedSubstitutionRecord>,
)

data class NestedSubstitutionRecord(
    val sequenceIndex: Int,
    val nestedLookupIndex: Int,
)
```

## Acceptance Criteria

- [x] Format 1 glyph-sequence context fixture applies a nested substitution only for the matching neighbor sequence.
- [x] Format 2 class-based context fixture matches glyph classes, enforces the first-glyph coverage gate, keeps same-lookup subtables isolated, and rejects malformed class definitions.
- [x] Format 3 coverage-based context fixture matches multiple coverage tables deterministically.
- [x] Nested lookup cycles or excessive re-entry emit a stable diagnostic and stop before corrupting the glyph buffer.
- [x] Contextual substitutions preserve cluster ranges or refuse with `text.shaping.cluster-invariant-failed`.

## Required Evidence

- `gsub-trace.json` with context format, cursor position, matched rule, nested lookup records, before/after glyph buffer, and diagnostics.
- `shaped-glyph-run.json` for positive contextual alternate and negative no-match fixtures.
- Fixtures: `gsub-context-format1.otf`, `gsub-context-format2-class.otf`, `gsub-context-format3-coverage.otf`, `gsub-context-nested-cycle.otf`, `gsub-context-malformed-classdef.otf`.
- Diagnostics asserted in tests: `text.shaping.lookup-malformed`, `text.shaping.lookup-type-unsupported`, `text.shaping.cluster-invariant-failed`, `text.shaping.lookup-cycle-detected`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.lookup-malformed`, `text.shaping.context-match-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement GSUB contextual lookups`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test :font:text:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `proposed`: Context matching builds on the basic GSUB buffer from KFONT-M6-002.
- `done` (2026-06-17): LookupType 5 formats 1/2/3 now ship checked-in Latin fixture provenance, parser/runtime coverage for first-glyph coverage gating plus isolated format 2 subtables, stable nested-cycle and out-of-range nested-index refusals, refreshed `gsub-trace.json` / `shaped-glyph-run.json` evidence, fresh `:font:sfnt:test` + `:font:text:test` validation, and an independent re-review with no remaining findings.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
