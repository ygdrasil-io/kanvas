---
id: "KFONT-M4-004"
title: "Implement CFF scaler path output"
status: "proposed"
milestone: "M4"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M4-002", "KFONT-M4-003", "KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M4-004 - Implement CFF scaler path output

## PM Note

Ce ticket est le point ou une fonte OTF/CFF devient un glyph outline Kanvas verifiable, avec metrics et refus visibles.

## Problem

Parsing and executing CFF charstrings is not enough to claim scaler support. Kanvas needs CFF glyph IDs from `cmap`, `.notdef` handling, widths, font metrics, path conversion, bounds, and path hashes that match the pure Kotlin glyph representation contracts.

## Scope

- Connect CFF parser facts, Type 2 execution, `cmap` mapping, and scaler input into a `CffScaler`.
- Convert Type 2 commands into the immutable Kanvas path representation with deterministic contour order and fill rule.
- Compute glyph bounds, advance width, vertical metrics when available, and `.notdef` fallback facts.
- Emit `glyph-outline.json`, `glyph-metrics.json`, and the linked `cff-charstring-trace.json` for every fixture glyph under test.
- Preserve hint metadata as drift-only context; it must not alter normative outline output.

## Non-Goals

- Do not implement CFF2 variation output; KFONT-M4-005 owns that route.
- Do not generate A8, SDF, atlas, or GPU artifacts.
- Do not claim pixel-perfect parity with FreeType, Skia native, or platform rasterizers.
- Do not add fallback font selection; this ticket only owns glyph output inside one selected CFF face.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class CffScaler(
    private val fontSet: CffFontSet,
    private val machine: Type2CharStringMachine,
    private val cmap: UnicodeCmap,
) {
    fun scale(request: GlyphScaleRequest): CffScaledGlyph
}

data class CffScaledGlyph(
    val glyphId: GlyphId,
    val sourceCodePoint: Int?,
    val path: KanvasGlyphPath?,
    val metrics: CffGlyphMetrics,
    val bounds: FontBounds?,
    val notdefUsed: Boolean,
    val diagnostics: List<RouteDiagnostic>,
)

data class CffGlyphMetrics(
    val advanceX: FontUnit,
    val advanceY: FontUnit?,
    val defaultWidthX: FontUnit?,
    val nominalWidthX: FontUnit?,
)
```

## Acceptance Criteria

- [ ] OTF/CFF fixture with line, curve, flex, and subroutine glyphs produces stable path command hashes.
- [ ] Metrics dump records width source, advance, bounds, and font/source identity for each tested glyph.
- [ ] Missing code point and malformed glyph fixtures produce `.notdef` or a glyph-local refusal with the original diagnostic retained.
- [ ] Path output is deterministic across repeated runs and independent of host-installed fonts.
- [ ] External FreeType or Skia comparisons, if present, are labeled drift-only and cannot update pass/fail expectations.

## Required Evidence

- `glyph-outline.json` with glyph ID, source cluster/code point, path commands, fill rule, bounds, path hash, and linked charstring trace ID.
- `glyph-metrics.json` with advance, width source, vertical facts when present, `.notdef` decision, and typeface identity.
- Fixtures: `cff-scaler-basic.otf`, `cff-scaler-subroutines.otf`, `cff-scaler-flex.otf`, `cff-scaler-missing-glyph.otf`, `cff-scaler-malformed-glyph.otf`.
- Diagnostics asserted in tests: `font.cff-stack-malformed`, `font.cff-operator-unsupported`, `font.fallback-glyph-unavailable`, `text.glyph.outline-unavailable`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.scaler.cff.path-output-unavailable`, `font.scaler.cff.glyph-malformed`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement CFF scaler path output`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CffScaler*' --tests '*CffPath*'
```

## Status Notes

- `proposed`: This is the M4 CFF single-master support promotion ticket.
- Move to `ready` only after path dump schema and fixture glyph set are accepted.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M4`
- `area:font-scaler`
- `claim:tracked-gap`
