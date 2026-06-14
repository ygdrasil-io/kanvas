---
id: "KFONT-M4-001"
title: "Implement CFF INDEX and DICT parser"
status: "proposed"
milestone: "M4"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M2-002", "KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M4-001 - Implement CFF INDEX and DICT parser

## PM Note

Ce ticket transforme les fontes OTF/CFF en donnees inspectables avant tout rendu, ce qui evite de promettre un support CFF sur une simple detection de table.

## Problem

The SFNT layer can expose `CFF ` and `CFF2` tables, but the target scaler still lacks a bounded pure Kotlin parser for CFF INDEX blocks, DICT operands, FDArray, FDSelect, and Private DICT data. Without those parsed facts, later Type 2 execution cannot know which charstring, subroutine set, widths, or variation store belongs to a glyph.

## Scope

- Parse CFF header, Name INDEX, Top DICT INDEX, String INDEX, Global Subr INDEX, CharStrings INDEX metadata, Encoding, Charset, FDArray, FDSelect, and Private DICT with explicit bounds checks.
- Parse CFF2 top dict and private dict metadata enough to locate charstrings, local subrs, variation store references, and default/nominal width facts for later tickets.
- Normalize DICT operands into typed records instead of exposing raw byte slices to the scaler.
- Preserve unknown DICT operators in a dumpable `unknownOperators` list without treating them as support claims.
- Emit table-local diagnostics with byte range, INDEX name, object index, and operator when offsets, counts, or operands are malformed.

## Non-Goals

- Do not execute Type 2 charstrings in this ticket.
- Do not produce glyph paths, bounds, or metrics beyond parsed CFF metadata.
- Do not implement CFF2 `blend` behavior here.
- Do not use FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or platform font APIs as normative parsing behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class CffFontSet(
    val source: TypefaceID,
    val format: CffFormat,
    val names: List<String>,
    val topDicts: List<CffTopDict>,
    val stringIndex: CffIndex<CffStringId>,
    val globalSubrs: CffIndex<CharStringBytes>,
    val charsets: Map<CffDictId, CffCharset>,
    val fdArrays: Map<CffDictId, List<CffFontDict>>,
    val diagnostics: List<CffParseDiagnostic>,
)

data class CffIndex<T>(
    val name: String,
    val count: Int,
    val offSize: Int,
    val objectRanges: List<ByteRange>,
    val decoded: List<T>,
)

data class CffTopDict(
    val charStringsOffset: Int,
    val privateDictRange: ByteRange?,
    val fdArrayOffset: Int?,
    val fdSelectOffset: Int?,
    val nominalWidthX: Int?,
    val defaultWidthX: Int?,
    val unknownOperators: List<CffDictOperator>,
)
```

## Acceptance Criteria

- [ ] Minimal single-master OTF/CFF fixture parses into a `CffFontSet` with Name, Top DICT, String, Global Subr, CharStrings, and Private DICT ranges.
- [ ] CID-keyed CFF fixture records FDArray and FDSelect facts for at least two font dicts.
- [ ] CFF2 fixture records top dict, charstrings, local subrs, and variation store offset metadata without claiming variation output.
- [ ] Malformed INDEX count, offSize, descending offset, and out-of-table offset fixtures fail with stable diagnostics instead of exceptions.
- [ ] Unknown DICT operators are preserved in dumps and do not block parsing unless they are required to locate charstrings or private data.

## Required Evidence

- `cff-index-dict.json` dump containing source/typeface IDs, CFF format, INDEX counts, object byte ranges, dict operators, FDArray facts, Private DICT facts, and diagnostics.
- Fixtures: `cff-minimal.otf`, `cff-cid-keyed.otf`, `cff2-minimal.otf`, `cff-index-bad-offset.otf`, `cff-dict-bad-operand.otf`.
- Diagnostics asserted in tests: `font.scaler.cff.index-bounds`, `font.scaler.cff.index-offsize-unsupported`, `font.scaler.cff.dict-operand-malformed`, `font.scaler.cff.required-operator-missing`.
- Review diff showing that no expected output is generated from host-installed fonts.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.scaler.cff.index-bounds`, `font.scaler.cff.dict-operand-malformed`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement CFF INDEX and DICT parser`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*CffIndex*' --tests '*CffDict*'
```

## Status Notes

- `proposed`: Parser scope is bounded to CFF/CFF2 table facts required by later scaler tickets.
- Move to `ready` only after the fixture names, dump schema, and diagnostic names are accepted.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M4`
- `area:font-scaler`
- `claim:tracked-gap`
