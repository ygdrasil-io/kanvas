---
id: "KFONT-M10-008"
title: "Implement SVG glyph refusal classes and bounds fixtures"
status: "proposed"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M10-007"]
legacy_gate: null
---

# KFONT-M10-008 - Implement SVG glyph refusal classes and bounds fixtures

## PM Note

Ce ticket fixe les refus SVG attendus pour éviter qu'un glyph dynamique ou externe soit rendu par accident.

## Problem

SVG glyph support is only safe if unsupported features refuse predictably and supported features have bounds fixtures. The missing evidence is a fixture set for scripts, external resources, network references, animation, filters, `foreignObject`, embedded text layout, unsupported CSS selectors, malformed documents, and budget overflow. This ticket is fixture-gated because the acceptance proof is the durable refusal and bounds corpus.

## Scope

- Add SVG glyph fixtures for supported bounds cases: viewBox, transformed path, gradient, clip path, and bounded `use`.
- Add refusal fixtures for script, external resource, network reference, animation, filter, `foreignObject`, embedded text, unsupported CSS selector, malformed XML/path data, and budget overflow.
- Emit `svg-glyph-fixture-manifest.json` with provenance, expected route, expected diagnostics, expected bounds hash, and expected dump files.
- Ensure `text.SVG.document-malformed`, `text.SVG.feature-unsupported`, `text.SVG.external-resource-refused`, and `text.SVG.budget-exceeded` are asserted.
- Keep SVG support pure Kotlin and glyph-scoped.

## Non-Goals

- Do not expand the supported SVG subset in this fixture ticket.
- Do not import a broad SVG conformance suite without minimization and license review.
- Do not use native SVG renderers for fixture generation.
- Do not claim GPU vector route support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class SVGGlyphFixtureCase(
    val fixtureId: String,
    val glyphId: GlyphId,
    val sourceDocumentHash: StableHash,
    val focus: SVGFixtureFocus,
    val expectedRoute: SVGExpectedRoute,
    val expectedBoundsHash: StableHash?,
    val expectedDiagnostics: List<String>,
    val provenance: FixtureProvenance,
)
```

## Acceptance Criteria

- [ ] Positive bounds fixtures cover path, transform, gradient, clip, and bounded `use`.
- [ ] Refusal fixtures cover scripts, external resources, network references, animation, filters, `foreignObject`, embedded text, unsupported CSS, malformed document, and budget overflow.
- [ ] Each fixture has provenance, expected diagnostic codes, and expected dump files.
- [ ] Bounds hashes are deterministic for supported fixtures.
- [ ] Dashboard classification remains `fixture-gated` until fixture bytes and expectation diffs are reviewed.

## Required Evidence

- `svg-glyph-fixture-manifest.json` with provenance and expected route per fixture.
- `svg-glyph-plan.json` bounds dumps for positive fixtures.
- Refusal snapshots for `text.SVG.document-malformed`, `text.SVG.feature-unsupported`, `text.SVG.external-resource-refused`, and `text.SVG.budget-exceeded`.

## Fallback / Refusal Behavior

- Unsupported SVG features refuse per glyph and may use monochrome outline fallback only when policy explicitly records it.
- Missing fixture provenance keeps SVG support `fixture-gated`.
- Dynamic SVG behavior must never be accepted as host-dependent fallback.

## Dashboard Impact

- Expected row: `SVG glyph refusal and bounds fixtures`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless SVG fixture provenance, bounds, and refusal expectations are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*SVGGlyph*Fixture*'
```

## Status Notes

- `proposed`: Fixture-gated proof for the bounded SVG glyph subset and explicit non-support classes.
- Move to `ready` only after fixture manifest fields and refusal class names are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:fixture-gated`
