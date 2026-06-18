---
id: "KFONT-M10-010"
title: "Add color/emoji fixture manifest"
status: "done"
milestone: "M10"
priority: "P0"
owner_area: "color"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M10-001", "KFONT-M10-002", "KFONT-M10-003", "KFONT-M10-004", "KFONT-M10-005", "KFONT-M10-006", "KFONT-M10-007", "KFONT-M10-008", "KFONT-M10-009"]
legacy_gate: ["scaledemoji", "scaledemoji_rendering", "coloremoji_blendmodes"]
---

# KFONT-M10-010 - Add color/emoji fixture manifest

## PM Note

Ce ticket rassemble les preuves couleur/emoji pour que les gates historiques restent auditables.

## Problem

M10 spans COLRv0, COLRv1, PNG bitmap glyphs, SVG glyphs, and emoji routes. Support cannot be promoted if fixtures are scattered, unlicensed, or missing expected diagnostics. The gap is a single color/emoji manifest that records fixture provenance, route expectations, dump names, source hashes, diagnostic expectations, legacy gate coverage, and whether GPU evidence is still required.

## Scope

- Create a manifest covering COLRv0 layers, COLRv1 solid/glyph/gradient/transform/composite/clip/cycle/budget cases, CBDT/CBLC PNG, sbix PNG, SVG supported/refused cases, and emoji sequence routes.
- Record fixture ID, font source ID, glyph IDs or text sequence, provenance, license note, generated source recipe when applicable, expected route, expected diagnostics, expected dumps, and linked legacy gates.
- Mark CPU/text evidence separately from future GPU evidence so metadata-only or CPU-only support cannot retire GPU rows.
- Emit `color-emoji-fixture-manifest.json` with deterministic ordering and stable hashes.
- Define rebaseline rules requiring old/new expected dump diffs and a reason for behavior changes.

## Non-Goals

- Do not implement missing color, bitmap, SVG, or emoji behavior in this manifest ticket.
- Do not add large external font corpora without minimized fixture extraction and license review.
- Do not update top-level statuses outside this milestone scope.
- Do not retire `scaledemoji`, `scaledemoji_rendering`, or `coloremoji_blendmodes` without implementation and GPU evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class ColorEmojiFixtureManifestEntry(
    val fixtureId: String,
    val family: ColorEmojiFixtureFamily,
    val fontSourceId: FontSourceID,
    val textOrGlyphTarget: FixtureTarget,
    val provenance: FixtureProvenance,
    val expectedRoute: GlyphRepresentationRoute,
    val expectedDiagnostics: List<String>,
    val expectedDumpFiles: List<String>,
    val legacyGates: List<String>,
    val gpuEvidenceRequired: Boolean,
)
```

## Acceptance Criteria

- [x] The manifest covers every M10 ticket family and links each fixture to expected dump files.
- [x] Fixture provenance, license notes, source hashes, and generated recipes are present for every entry.
- [x] Legacy gates `scaledemoji`, `scaledemoji_rendering`, and `coloremoji_blendmodes` are mapped to specific fixtures and remaining evidence.
- [x] GPU-required rows remain blocked until M11 evidence is linked.
- [x] Rebaseline updates require reviewed old/new expectation diffs and cannot auto-overwrite goldens.

## Required Evidence

- `color-emoji-fixture-manifest.json` with COLRv0, COLRv1, bitmap PNG, SVG, and emoji entries.
- Cross-reference dump listing expected `color-glyph-plan.json`, `colrv1-paint-graph.json`, `bitmap-glyph-plan.json`, `svg-glyph-plan.json`, and `emoji-route-trace.json` files.
- Dashboard snapshot keeping the `emoji/color` support surface non-promotable while the convergence manifest, CPU oracle gap, and M11 GPU route gate remain explicit.

## Fallback / Refusal Behavior

- Missing fixture provenance keeps the affected route `fixture-gated`.
- Missing GPU evidence keeps GPU-dependent legacy gates open even when CPU/text fixtures exist.
- External drift reports may be linked as non-normative only and cannot replace manifest expectations.

## Dashboard Impact

- Expected row: `emoji/color`.
- Expected classification: `DependencyGated`.
- Claim promotion allowed: no, unless manifest provenance, expected diagnostics, and required evidence links are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.colorEmojiFixtureManifestConvergesM10FamiliesLegacyGatesAndRemainingGpuEvidence
```

## Status Notes

- `done`: `color-emoji-fixture-manifest.json` now converges the M10 color/emoji fixture families, component dump hashes, legacy gate mapping, and explicit M11 remaining gates without widening support claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:fixture-gated`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:coloremoji_blendmodes`
