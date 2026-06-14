---
id: "KFONT-M10-005"
title: "Add COLRv1 recursion, cycle and bounds fixtures"
status: "proposed"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M10-002", "KFONT-M10-004"]
legacy_gate: ["coloremoji_blendmodes"]
---

# KFONT-M10-005 - Add COLRv1 recursion, cycle and bounds fixtures

## PM Note

Ce ticket fournit les fixtures qui empêchent les graphes COLRv1 cycliques ou trop grands de devenir des claims implicites.

## Problem

COLRv1 graph traversal needs adversarial evidence. Solid, gradient, transform, composite, and clip support is not complete unless recursion depth, cycle detection, operation budget, bounds propagation, and malformed offsets are tested with generated or bundled fixtures. This ticket is fixture-gated because the implementation cannot be promoted without durable fixture provenance.

## Scope

- Add a COLRv1 fixture manifest for bounded recursion, explicit cycle, deep `PaintColrGlyph`, composite/clip bounds, transform bounds, excessive operation count, and malformed paint offsets.
- Record fixture provenance, generated source bytes or generation recipe, expected route, expected diagnostics, and expected dump names.
- Emit `colrv1-fixture-manifest.json` and link each fixture to `colrv1-paint-graph.json` expectations.
- Add refusal expectations for `text.color.COLRv1-cycle-detected` and `text.color.COLRv1-budget-exceeded`.
- Preserve `coloremoji_blendmodes` until these fixtures and downstream renderer evidence are linked.

## Non-Goals

- Do not add broad real-world font corpora without minimized provenance.
- Do not use Skia or browser output as normative fixture expectations.
- Do not change COLRv1 operation support scope in this fixture-only ticket.
- Do not retire any legacy color emoji gate.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class COLRv1FixtureCase(
    val fixtureId: String,
    val fontSourceId: FontSourceID,
    val glyphId: GlyphId,
    val operationFocus: COLRv1FixtureFocus,
    val expectedRoute: ColorGlyphRoute,
    val expectedDiagnostics: List<String>,
    val expectedDumpFiles: List<String>,
    val provenance: FixtureProvenance,
)

data class COLRv1BoundsExpectation(
    val fixtureId: String,
    val expectedConservativeBounds: RectF,
    val expectedTightBoundsHash: StableHash?,
)
```

## Acceptance Criteria

- [ ] The manifest includes positive bounds fixtures and negative cycle, recursion-depth, operation-budget, and malformed-offset fixtures.
- [ ] Every fixture records provenance and expected diagnostic codes before support is promoted.
- [ ] Cycle and recursion cases are minimized and deterministic.
- [ ] Bounds fixtures cover transform, composite, clip, nested glyph, and nested COLR glyph cases.
- [ ] Dashboard classification remains `fixture-gated` until fixture bytes, expectations, and review diffs are attached.

## Required Evidence

- `colrv1-fixture-manifest.json` with provenance and expected route for every fixture.
- Expected `colrv1-paint-graph.json` dumps for positive bounds fixtures.
- Refusal snapshots for `text.color.COLRv1-cycle-detected` and `text.color.COLRv1-budget-exceeded`.
- Review diff showing fixture expectation changes during rebaseline, if any.

## Fallback / Refusal Behavior

- Cycle or recursion overflow refuses the color glyph route and may fall back to monochrome only under explicit fallback policy.
- Missing fixture provenance keeps the support row `fixture-gated`.
- Legacy gate `coloremoji_blendmodes` remains open until fixture and renderer evidence exist.

## Dashboard Impact

- Expected row: `COLRv1 recursion, cycle, and bounds fixtures`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no, unless fixture provenance and expected diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*COLRv1*Fixture*'
```

## Status Notes

- `proposed`: Fixture-gated support evidence for COLRv1 traversal and bounds.
- Move to `ready` only after fixture manifest fields and generated-font provenance are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:fixture-gated`
- `legacy:coloremoji_blendmodes`
