---
id: "KFONT-M4-003"
title: "Add CFF subroutine limits and diagnostics"
status: "done"
milestone: "M4"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M4-001", "KFONT-M4-002"]
legacy_gate: null
---

# KFONT-M4-003 - Add CFF subroutine limits and diagnostics

## PM Note

Ce ticket protege le scaler CFF contre les fontes abusives tout en gardant des diagnostics precis pour les glyphes refuses.

## Problem

Type 2 charstrings rely on local and global subroutines. Without biased subroutine index resolution, recursion limits, return validation, and bytecode budgets, a malformed CFF font can crash, loop, or hide the real reason a glyph cannot be scaled.

## Scope

- Resolve local and global subroutine indexes using the CFF bias rules for each INDEX size.
- Add `callsubr`, `callgsubr`, and `return` support to the Type 2 machine.
- Enforce maximum call depth, instruction count, byte count, and operand stack depth with deterministic refusal diagnostics.
- Track call frames in `cff-subroutine-trace.json`, including caller offset, resolved subr index, local/global scope, and return offset.
- Isolate subroutine failures to the current glyph unless the shared subr INDEX itself is malformed.

## Non-Goals

- Do not add new drawing operators beyond the KFONT-M4-002 operator set.
- Do not implement CFF2 `blend` or variation-store lookup.
- Do not tune execution limits from host memory, CPU speed, or platform font behavior.
- Do not promote full CFF scaler support without KFONT-M4-004 path, metrics, and bounds evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class CffSubroutineResolver(
    val globalSubrs: CffIndex<CharStringBytes>,
    val localSubrsByFd: Map<CffDictId, CffIndex<CharStringBytes>>,
    val limits: Type2ExecutionLimits,
) {
    fun resolve(scope: SubrScope, rawOperand: Int, fd: CffDictId?): SubrTarget
}

data class SubrCallFrame(
    val scope: SubrScope,
    val rawOperand: Int,
    val resolvedIndex: Int,
    val callerByteOffset: Int,
    val depth: Int,
)

data class Type2ExecutionLimits(
    val maxOperandStack: Int,
    val maxCallDepth: Int,
    val maxInstructionCount: Int,
    val maxExpandedBytes: Int,
)
```

## Acceptance Criteria

- [x] Local and global subroutines execute with the correct bias for small, medium, and large INDEX counts.
- [x] Nested subroutine fixture records every call and return in a stable trace.
- [x] Recursive and over-budget subroutine fixtures refuse with the configured limit name and current call frame.
- [x] Out-of-range subroutine indexes and missing `return` opcodes refuse the glyph without crashing the face parser.
- [x] Diagnostics include enough source data to reproduce the failing subroutine path from the trace dump.

## Required Evidence

- `cff-subroutine-trace.json` with call frames, bias, resolved subroutine index, depth, byte budget, instruction budget, return events, and refusal diagnostics.
- Fixtures: `cff-subr-local.otf`, `cff-subr-global.otf`, `cff-subr-nested.otf`, `cff-subr-recursive.otf`, `cff-subr-out-of-range.otf`, `cff-subr-missing-return.otf`.
- Diagnostics asserted in tests: `font.scaler.cff.subr-out-of-range`, `font.scaler.cff.subr-depth-limit`, `font.scaler.cff.instruction-limit`, `font.cff-stack-malformed`.
- Review evidence that execution limits are constants or config values serialized in the dump, not host-derived heuristics.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.scaler.cff.subr-index-invalid`, `font.scaler.cff.subr-recursion-limit`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add CFF subroutine limits and diagnostics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CffSubr*' --tests '*CffDiagnostics*'
```

## Status Notes

- `done`: `cff-subroutine-trace.json` now serializes fixed execution limits, local/global/nested call traces, and deterministic refusal diagnostics for out-of-range, depth-limit, missing-return, and instruction-limit paths.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M4`
- `area:font-scaler`
- `claim:tracked-gap`
