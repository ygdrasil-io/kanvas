---
id: "KFONT-M3-001"
title: "Complete composite glyph transform coverage"
status: "done"
milestone: "M3"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M3-001 - Complete composite glyph transform coverage

## PM Note

Ce ticket prouve que les glyphes TrueType composés sortent avec les bons contours au lieu de dégrader tout le texte.

## Problem

The TrueType `glyf` scaler must support composite glyphs with component transforms, nested components, depth limits, cycle detection, bounds, and deterministic path output. Without this coverage, many real TTF fixtures can parse successfully but fail at outline generation or silently produce wrong glyph geometry.

## Scope

- Implement or validate composite glyph handling for translate, uniform scale, non-uniform scale, two-by-two transform, and component point alignment.
- Support nested composites with explicit recursion and component-count limits.
- Detect composite cycles and invalid component glyph IDs with stable diagnostics.
- Preserve contour winding and produce deterministic bounds for composed outlines.
- Emit `glyph-outline.json` and `glyph-metrics.json` for composite fixtures.

## Non-Goals

- Do not implement CFF/CFF2 charstring scaling.
- Do not implement full TrueType hinting VM or pixel-perfect FreeType parity.
- Do not claim A8/SDF glyph artifact or GPU atlas support.
- Do not add shaping or fallback behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class CompositeGlyphComponent(
    val glyphId: GlyphID,
    val transform: Affine2x2,
    val offset: Point26Dot6,
    val useMyMetrics: Boolean,
)

data class CompositeGlyphPlan(
    val rootGlyphId: GlyphID,
    val components: List<CompositeGlyphComponent>,
    val maxDepth: Int,
)

class TrueTypeGlyfScaler {
    fun outlineComposite(plan: CompositeGlyphPlan): GlyphOutlineResult
}
```

## Acceptance Criteria

- [ ] Composite fixtures cover translation, uniform scale, non-uniform scale, two-by-two transform, and nested components.
- [ ] Cycle detection emits a stable `font.scaler.composite-cycle` or equivalent diagnostic.
- [ ] Invalid component glyph ID emits a precise scaler diagnostic and does not crash the face parser.
- [ ] `glyph-outline.json` contains deterministic path commands, contour winding facts, component trace, and bounds.
- [ ] Path hashes for supported composite fixtures are stable across repeated runs.

## Required Evidence

- `glyph-outline.json` for composite transform fixtures.
- `glyph-metrics.json` showing component metrics source and `useMyMetrics` behavior when present.
- CPU path hash or stat artifact for each supported composite transform case.
- Diagnostic snapshot for composite cycle, invalid component glyph ID, and
  excessive composite component count.

## Fallback / Refusal Behavior

- Unsupported composite flags or unsafe recursion must refuse that glyph with `font.scaler.*` diagnostics and preserve the rest of the face when safe.
- The scaler must not replace composite glyphs with empty paths without a route diagnostic.

## Dashboard Impact

- Expected row: `TrueType composite glyf outlines`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: only for the composite slice with required outline, metrics, CPU hash, and diagnostics evidence.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CompositeGlyph*' --tests '*Glyf*'
```

## Status Notes

- `done` (2026-06-16): Implemented composite point matching inside
  `ParsedTrueTypeGlyphScaler` through recursive resolved outline/point state.
  Evidence now includes deterministic outline commands, bounds, component
  trace, metrics/useMyMetrics facts, path hash artifacts, cycle recursion
  diagnostics, invalid component glyph ID diagnostics, component-count
  diagnostics, and invalid point-index refusals.
- Validation bundle for local review:
  `rtk ./gradlew --no-daemon :font:scaler:test --tests '*CompositeGlyph*' --tests '*Glyf*'`,
  `rtk python3 scripts/validate_pure_kotlin_text_dump_index.py`,
  `rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py`,
  `rtk python3 scripts/validate_font_fixture_assets.py`, and
  `rtk git diff --check`.
- Independent spec review accepted the implementation, evidence, status, and
  non-claim wording after remediation for the component-count cap and missing
  variation non-claims.
- Independent quality review accepted the bounded implementation after KDoc
  wording was updated to match the implemented point-matching resolver.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:font-scaler`
- `claim:tracked-gap`
