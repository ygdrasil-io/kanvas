---
id: "KFONT-M6-007"
title: "Add Arabic shaping fixtures"
status: "review"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-003", "KFONT-M6-003", "KFONT-M6-005", "KFONT-M6-006"]
legacy_gate: null
---

# KFONT-M6-007 - Add Arabic shaping fixtures

## PM Note

Ce ticket prouve les formes arabes attendues, les marques et l'attache cursive avant toute promesse de support Arabic.

## Problem

Arabic support cannot be inferred from generic GSUB/GPOS lookup coverage. The required script matrix needs fixtures for joining forms, lam-alef, required ligatures, marks, cursive attachment, and mixed bidi behavior, with explicit refusals when required features or positioning data are missing.

## Scope

- Add Arabic fixture fonts and text inputs for isolated, initial, medial, and final forms; lam-alef; required ligatures; mark attachment; cursive attachment; and mixed LTR/RTL runs.
- Record shaping plans, GSUB traces, GPOS traces, shaped glyph runs, bidi run links, feature policy choices, and refusal diagnostics.
- Assert cluster preservation through Arabic joining substitutions and mark positioning.
- Include negative fixtures for missing cursive attachment, missing mark data, unsupported lookup, malformed GDEF, and single-run request that needs paragraph bidi context.
- Label external HarfBuzz comparisons as drift-only if they are generated for review.

## Non-Goals

- Do not implement color emoji, fallback catalog, paragraph visual ordering, or renderer output.
- Do not claim every Arabic language system; the fixture set covers the target matrix minimum.
- Do not approximate joining with Unicode presentation forms when OpenType shaping data is missing.
- Do not retire any unrelated legacy font gates.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class ArabicShapingFixture(
    val name: String,
    val text: TextInput,
    val expectedFeatures: Set<OpenTypeFeatureTag>,
    val expectedGlyphClasses: List<ArabicJoiningForm>,
    val requiredDiagnostics: Set<String> = emptySet(),
)

data class ArabicFixtureEvidence(
    val fixture: ArabicShapingFixture,
    val shapingPlanHash: Sha256,
    val gsubTraceHash: Sha256,
    val gposTraceHash: Sha256,
    val shapedRunHash: Sha256,
)
```

## Acceptance Criteria

- [ ] Fixtures cover isolated, initial, medial, final, lam-alef, mark attachment, cursive attachment, and mixed bidi Arabic cases.
- [ ] `shaping-plan.json` enables required Arabic defaults: `init`, `medi`, `fina`, `isol`, `rlig`, `liga`, `calt`, `mark`, `mkmk`, and cursive attachment where tables exist.
- [ ] `gsub-trace.json` and `gpos-trace.json` show the specific lookups that changed forms, ligatures, marks, and cursive attachment.
- [ ] Missing cursive or required mark positioning refuses the affected run instead of claiming approximate support.
- [ ] Bidi run references from M5 are preserved in the shaped output.

## Required Evidence

- `arabic-shaping-report.json` summarizing fixture provenance, expected feature set, dump hashes, positive/refusal status, and drift-only comparison links if present.
- `shaping-plan.json`, `gsub-trace.json`, `gpos-trace.json`, `shaped-glyph-run.json`, and `bidi-runs.json` for each Arabic fixture.
- Fixtures: `arabic-joining-forms.otf`, `arabic-lam-alef.otf`, `arabic-marks-cursive.otf`, `arabic-mixed-bidi.txt`, `arabic-missing-cursive.otf`, `arabic-missing-mark.otf`.
- Diagnostics asserted in tests: `text.shaping.cursive-attachment-unavailable`, `text.shaping.mark-positioning-unavailable`, `text.shaping.gdef-required`, `text.shaping.paragraph-bidi-required`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.arabic-cursive-unsupported`, `text.shaping.arabic-mark-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `fixture-gated` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add Arabic shaping fixtures`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk ./gradlew --no-daemon :font:text:test --tests '*ArabicShaping*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Status Notes

- `proposed`: Arabic fixture ticket depends on contextual GSUB, mark/cursive GPOS, feature policy, and bidi runs.
- `review`: `ArabicShapingFixtureTest`, `arabic-shaped-glyph-run.json`, and the checked-in `arabic-shaping-report.json` now prove bounded vendored-font evidence for joining-form behavior beyond pure RTL reordering, pin the Arabic shaped glyph/cluster facts for joining and mark rows, capture a bounded `lam-alef` runtime-divergence row without promoting it to positive feature-local evidence, prove a reviewed generic `text.shaping.gdef-required` refusal row on `gpos-missing-gdef.otf` for Arabic base+mark input, and keep the single-run `text.shaping.paragraph-bidi-required` diagnostic on `arabic-mixed-bidi.txt`.
- `review`: This wave intentionally keeps explicit `lam-alef` positive evidence, vendored-font positive cursive-attachment evidence, dedicated `arabic-missing-cursive` / `arabic-missing-mark` refusal fixtures, narrower `text.shaping.arabic-*` diagnostics, and ticket-local `shaping-plan.json` / `gsub-trace.json` / `gpos-trace.json` dump families as explicit remaining gates.
- Move to `ready` only after fixture fonts and expected dump names are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:fixture-gated`
