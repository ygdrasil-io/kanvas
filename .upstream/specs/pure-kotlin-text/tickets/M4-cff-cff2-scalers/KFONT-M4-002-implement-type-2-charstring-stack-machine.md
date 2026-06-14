---
id: "KFONT-M4-002"
title: "Implement Type 2 charstring stack machine"
status: "proposed"
milestone: "M4"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M4-001"]
legacy_gate: null
---

# KFONT-M4-002 - Implement Type 2 charstring stack machine

## PM Note

Ce ticket rend les glyphes CFF executables en pure Kotlin, avec une trace lisible pour expliquer chaque contour produit ou refuse.

## Problem

CFF glyph outlines are encoded as Type 2 charstrings, not as direct SFNT contours. Kanvas needs a deterministic stack machine for operands, path operators, widths, hints-as-metadata, escaped operators, and malformed stack effects before it can generate paths or metrics for OTF/CFF fonts.

## Scope

- Implement Type 2 number decoding, operand stack handling, transient width extraction, and `endchar` handling.
- Support path movement, line, curve, and flex operators required by the target: `rmoveto`, `hmoveto`, `vmoveto`, `rlineto`, `hlineto`, `vlineto`, `rrcurveto`, `vhcurveto`, `hvcurveto`, `rcurveline`, `rlinecurve`, `vvcurveto`, `hhcurveto`, `flex`, `hflex`, `hflex1`, and `flex1`.
- Parse stem and mask hint operators as metadata so they are represented in traces but do not affect normative path output.
- Record every executed operator, operand consumption, stack depth, path command, width decision, and diagnostic in `cff-charstring-trace.json`.
- Refuse unsupported escaped operators and malformed stack effects with stable diagnostics.

## Non-Goals

- Do not resolve local or global subroutines; KFONT-M4-003 owns subroutine call limits.
- Do not integrate with `cmap`, glyph metrics, or `.notdef` route selection; KFONT-M4-004 owns scaler output.
- Do not claim hinted raster parity with FreeType or platform renderers.
- Do not execute Type 1, PFA, PFB, or CFF2 `blend` operators in this ticket.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class Type2CharStringMachine(
    private val limits: Type2ExecutionLimits,
    private val trace: MutableList<CharStringTraceEvent>,
) {
    fun run(program: CharStringBytes, privateDict: CffPrivateDict): Type2GlyphProgram
}

data class Type2GlyphProgram(
    val glyphId: GlyphId,
    val width: FontUnit?,
    val commands: List<CffPathCommand>,
    val hintMetadata: List<CffHintMask>,
    val diagnostics: List<CffExecutionDiagnostic>,
)

sealed interface CharStringTraceEvent {
    data class Operator(
        val offset: Int,
        val op: Type2Operator,
        val operandsBefore: List<Int>,
        val stackAfter: List<Int>,
    ) : CharStringTraceEvent
    data class PathCommand(val command: CffPathCommand) : CharStringTraceEvent
    data class Refusal(val diagnostic: CffExecutionDiagnostic) : CharStringTraceEvent
}
```

## Acceptance Criteria

- [ ] Line-only, curve-only, mixed line/curve, flex, width, and hintmask fixtures produce stable charstring traces.
- [ ] Operand stack overflow, underflow, leftover operands at `endchar`, and unsupported escaped operators are refused with stable diagnostics.
- [ ] Hint operators are serialized as metadata and do not change path command hashes.
- [ ] `endchar` closes execution deterministically and rejects trailing executable bytes unless a fixture documents an accepted compatibility case.
- [ ] Repeated execution of the same charstring bytes produces byte-identical trace dumps.

## Required Evidence

- `cff-charstring-trace.json` with charstring offset, operator list, operand snapshots, path commands, width source, hint metadata, and diagnostics.
- Fixtures: `cff-type2-lines.otf`, `cff-type2-curves.otf`, `cff-type2-flex.otf`, `cff-type2-hints-width.otf`, `cff-type2-stack-underflow.otf`, `cff-type2-unsupported-operator.otf`.
- Diagnostics asserted in tests: `font.cff-stack-malformed`, `font.cff-operator-unsupported`, `font.scaler.cff.stack-overflow`, `font.scaler.cff.trailing-bytes`.
- Path command hash expectations for the positive fixtures, separate from final glyph metrics.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.scaler.cff.stack-underflow`, `font.scaler.cff.operator-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement Type 2 charstring stack machine`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*Type2CharString*'
```

## Status Notes

- `proposed`: Execution scope excludes subroutine resolution and scaler integration.
- Move to `ready` only after Type 2 operator coverage and diagnostics are reviewed against the target spec.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M4`
- `area:font-scaler`
- `claim:tracked-gap`
