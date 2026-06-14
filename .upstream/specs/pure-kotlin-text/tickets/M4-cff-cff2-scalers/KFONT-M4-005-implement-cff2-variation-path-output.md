---
id: "KFONT-M4-005"
title: "Implement CFF2 variation path output"
status: "proposed"
milestone: "M4"
priority: "P1"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M4-004", "KFONT-M2-004", "KFONT-M3-002", "KFONT-M3-003"]
legacy_gate: null
---

# KFONT-M4-005 - Implement CFF2 variation path output

## PM Note

Ce ticket couvre les fontes CFF2 variables, afin que les variations changent vraiment les contours et metrics au lieu d'etre ignorees.

## Problem

CFF2 fonts encode variable outlines through `vsindex`, `blend`, variation stores, and normalized axis coordinates. Kanvas cannot claim variable OTF/CFF2 support until glyph paths and metrics respond deterministically to axis positions and malformed variation data refuses precisely.

## Scope

- Parse and apply CFF2 variation store data needed by charstring `blend` operations.
- Add `vsindex` and `blend` execution to the charstring machine with stack-effect validation.
- Use pinned variation coordinates from `fvar`/`avar` and metrics deltas from HVAR/VVAR/MVAR when required by the face.
- Produce CFF2 glyph paths, bounds, advances, and trace dumps for default, min, max, and named instance coordinates.
- Include variation coordinates and CFF2 variation store generation in scaler cache key preimages and evidence dumps.

## Non-Goals

- Do not implement TrueType `gvar`; this ticket only consumes variation foundations already owned by M3.
- Do not claim color glyph, paragraph, GPU, A8, SDF, or fallback support.
- Do not approximate malformed `blend` behavior by using default outlines unless the diagnostic explicitly records default-coordinate fallback as safe.
- Do not depend on Fontations, FreeType, CoreText, DirectWrite, or platform font APIs for normative variation output.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class Cff2FontSet(
    val source: TypefaceID,
    val topDict: Cff2TopDict,
    val privateDicts: List<CffPrivateDict>,
    val variationStore: Cff2VariationStore,
    val charStrings: CffIndex<CharStringBytes>,
)

class Cff2CharStringMachine(
    private val variationStore: Cff2VariationStore,
    private val coordinates: NormalizedVariationPosition,
    private val trace: MutableList<Cff2VariationTraceEvent>,
) {
    fun run(program: CharStringBytes, privateDict: CffPrivateDict): Cff2GlyphProgram
}

data class Cff2BlendVector(
    val vsIndex: Int,
    val baseValues: List<Int>,
    val deltas: List<List<Int>>,
    val blendedValues: List<Int>,
)
```

## Acceptance Criteria

- [ ] Variable CFF2 fixture produces distinct path hashes for at least default, min, max, and one named instance.
- [ ] `cff2-variation-trace.json` records `vsindex`, `blend` operands, selected variation region, normalized coordinates, and blended values.
- [ ] Bounds and advances change when variation data affects them, and remain stable when a coordinate change has no applicable region.
- [ ] Malformed `blend` stack count, invalid `vsindex`, missing variation store, and unsupported axis fixtures refuse with stable diagnostics.
- [ ] CFF2 evidence does not promote CFF single-master behavior without KFONT-M4-004 evidence.

## Required Evidence

- `cff2-variation-trace.json` with coordinates, variation store references, blend vectors, path command deltas, metric deltas, and diagnostics.
- `glyph-outline.json` and `glyph-metrics.json` for the same glyphs at default, min, max, and named instance positions.
- Fixtures: `cff2-variable-basic.otf`, `cff2-variable-metrics.otf`, `cff2-blend-bad-stack.otf`, `cff2-vsindex-invalid.otf`, `cff2-missing-varstore.otf`.
- Diagnostics asserted in tests: `font.variation-data-malformed`, `font.variation-axis-unsupported`, `font.metrics-variation-unavailable`, `font.scaler.cff2.blend-stack-malformed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.scaler.cff2.blend-malformed`, `font.variation-data-malformed`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement CFF2 variation path output`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*Cff2*' --tests '*Variation*'
```

## Status Notes

- `proposed`: Depends on CFF path output plus shared variation foundations.
- Move to `ready` only after CFF2 variation fixture provenance and dump fields are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M4`
- `area:font-scaler`
- `claim:tracked-gap`
