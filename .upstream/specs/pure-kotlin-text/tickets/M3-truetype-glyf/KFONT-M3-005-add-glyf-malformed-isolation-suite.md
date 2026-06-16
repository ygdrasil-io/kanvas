---
id: "KFONT-M3-005"
title: "Add glyf malformed isolation suite"
status: "done"
milestone: "M3"
priority: "P0"
owner_area: "fixtures"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M3-001", "KFONT-M3-003"]
legacy_gate: null
---

# KFONT-M3-005 - Add glyf malformed isolation suite

## PM Note

Ce ticket prouve qu'un glyph TrueType cassé ne fait pas tomber toute la fonte quand l'isolation est possible.

## Problem

The target requires malformed glyph isolation so one bad optional glyph does not poison the face when safe to skip. M3 needs fixture-backed evidence for invalid `loca`/`glyf` offsets, contour decoding failures, composite cycles, invalid components, transform abuse, and malformed variation data. Without this suite, scaler support can be marked complete while important refusal paths remain untested.

## Scope

- Add malformed `glyf` fixtures for invalid `loca` offset, truncated glyph header, bad contour end points, flag-repeat overflow, coordinate run truncation, composite cycle, missing component glyph, transform overflow, and malformed `gvar` tuple/point data.
- Define `GlyphFailurePolicy` for `.notdef` substitution, per-glyph refusal, and face-level refusal.
- Record fixture provenance, expected diagnostic, expected isolation behavior, and affected glyph ID in the manifest.
- Emit `glyph-outline.json`, `glyph-metrics.json`, or `variation-deltas.json` refusal snapshots for each case.
- Prove safe glyphs in the same face remain parseable when the policy allows isolation.

## Non-Goals

- Do not add broad fuzzing or random mutation tests.
- Do not handle CFF/CFF2 malformed charstrings.
- Do not claim that every malformed font can continue safely.
- Do not mask scaler bugs with `.notdef` substitution when a face-level refusal is required.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class GlyphFailureAction { SubstituteNotDef, RefuseGlyph, RefuseFace }

data class GlyphFailurePolicy(
    val glyphId: GlyphID,
    val diagnostic: String,
    val action: GlyphFailureAction,
    val safeGlyphsStillAvailable: Boolean,
)

data class MalformedGlyfFixture(
    val fixtureId: String,
    val malformedGlyphId: GlyphID,
    val expectedPolicy: GlyphFailurePolicy,
    val expectedDump: String,
)
```

## Acceptance Criteria

- [x] Each malformed `glyf` fixture has one primary diagnostic and expected isolation action.
- [x] Safe glyphs in the same face remain dumpable when `GlyphFailureAction.RefuseGlyph` or `.notdef` substitution is expected.
- [x] Composite cycle and missing component cases are diagnosed separately.
- [x] Malformed variation data uses `font.variation-data-malformed` or a more precise scaler diagnostic.
- [x] The suite stays `fixture-gated` until all listed malformed cases have manifest and dump evidence.

## Required Evidence

- Malformed `glyf` fixture manifest entries with hash, glyph ID, failure case, expected diagnostic, and expected isolation action.
- Refusal snapshots in `glyph-outline.json`, `glyph-metrics.json`, or `variation-deltas.json` for every listed case.
- Positive control dump proving an unaffected glyph in the same face remains available for isolation-eligible cases.
- Dashboard row showing `fixture-gated` until the full suite is attached.

## Fallback / Refusal Behavior

- Unsafe malformed glyph data must refuse the glyph or face with stable `font.scaler.*` diagnostics.
- `.notdef` substitution is allowed only when the policy and evidence show the rest of the face remains valid.
- Classification remains `fixture-gated` until all required malformed evidence is attached.

## Dashboard Impact

- Expected row: `malformed glyf isolation suite`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no until fixture manifest, dumps, diagnostics, and isolation policy evidence are complete.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*MalformedGlyf*' --tests '*GlyphFailurePolicy*' --tests '*CompositeGlyph*' --tests '*Gvar*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

## Status Notes

- `proposed`: Malformed `glyf` cases are specified, but no suite evidence is attached yet.
- `done`: `truetype-malformed-glyf-isolation.json` now captures face-level `loca` refusal, per-glyph `glyf` malformed isolation snapshots, positive-control safe glyph dumps, and malformed `gvar` diagnostics while keeping `.notdef` substitution as an explicit non-claim for the current runtime slice.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:fixtures`
- `claim:fixture-gated`
