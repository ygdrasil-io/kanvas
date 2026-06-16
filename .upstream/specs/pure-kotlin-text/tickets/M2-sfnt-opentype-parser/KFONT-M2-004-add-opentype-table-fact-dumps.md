---
id: "KFONT-M2-004"
title: "Add OpenType table fact dumps"
status: "done"
milestone: "M2"
priority: "P0"
owner_area: "validation"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-003", "KFONT-M2-001", "KFONT-M2-003"]
legacy_gate: null
---

# KFONT-M2-004 - Add OpenType table fact dumps

## PM Note

Ce ticket produit les dumps qui montrent quelles tables OpenType existent, sans prétendre les rendre.

## Problem

The roadmap requires `sfnt-directory.json`, `sfnt-tables.json`, and `cmap-map.json` before scaler, shaping, color, or GPU tickets can claim support. Directory parsing alone is not enough: reviewers need stable facts for required tables, optional shaping tables, vertical metrics, variation tables, and color/bitmap/SVG dispatch tables without implying those payloads are already implemented.

## Scope

- Add canonical table fact dumps for required/high-value tables: `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`, `OS/2`, `post`, `loca`, `glyf`, `CFF `, `CFF2`, `vhea`, `vmtx`, `GDEF`, `GSUB`, `GPOS`, `BASE`, `kern`, `fvar`, `avar`, `gvar`, `HVAR`, `VVAR`, `MVAR`, `COLR`, `CPAL`, `CBDT`, `CBLC`, `sbix`, and `SVG `.
- Record table presence, bounded byte range, checksum, parser status, required/optional role, and support classification.
- Link `cmap-map.json` output from KFONT-M2-003 into the table fact bundle.
- Keep dump ordering deterministic and independent from table directory order when producing review bundles.
- Include diagnostics for malformed optional tables and missing required tables.

## Non-Goals

- Do not parse full GSUB/GPOS shaping behavior, color glyph paint graphs, bitmap payloads, SVG contents, or scaler outlines.
- Do not promote support for CFF/CFF2, COLR, PNG bitmap glyphs, or SVG glyphs from table presence alone.
- Do not include raw copyrighted font table payloads in dumps.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class OpenTypeTableFact(
    val tag: String,
    val role: TableRole,
    val present: Boolean,
    val byteRange: UIntRange?,
    val checksum: UInt?,
    val parserStatus: ParserStatus,
    val claimImpact: FontClaimImpact,
    val diagnostics: List<SerializedFontDiagnostic>,
)

data class OpenTypeFactDump(
    val sourceId: FontSourceID,
    val typefaceId: TypefaceID,
    val sfntDirectory: List<SFNTTableRecord>,
    val tableFacts: List<OpenTypeTableFact>,
    val cmapSelection: CMapSelection?,
)
```

## Acceptance Criteria

- [x] `sfnt-tables.json` lists required, optional shaping, vertical, variation, and color/bitmap/SVG table facts with stable ordering.
- [x] Table presence is classified as metadata evidence only unless a later owner ticket supplies payload support evidence.
- [x] Missing required tables and malformed optional tables carry stable diagnostics in the dump.
- [x] Dumps include source and typeface IDs from M1.
- [x] Repeated dump generation over the same fixture is byte-identical.

## Required Evidence

- `sfnt-tables.json` for a TTF fixture with `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`, `OS/2`, `post`, `loca`, and `glyf`.
- Table fact dump for a fixture containing at least one optional shaping, vertical, variation, or color/bitmap/SVG table.
- Determinism diff for repeated table fact dump generation.
- Diagnostic snapshot for malformed optional table and missing required table.

## Fallback / Refusal Behavior

- A present table with unsupported payload parsing must remain metadata-only and use a route-specific diagnostic or gated classification.
- Unknown table tags may be copied as bounded facts, but they must not imply support.

## Dashboard Impact

- Expected row: `OpenType table fact dumps`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. Table facts unlock later scaler/shaping/color tickets but do not complete them.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*TableFactDump*' --tests '*CMap*'
```

## Status Notes

- `proposed`: Dump contents are specified, but no `sfnt-tables.json` evidence is attached yet.
- Move to `ready` after identity dumps, parser entry points, and `cmap` coverage are available.
- `review`: `sfnt-tables.json`, focused TableFactDump coverage, dump-index coverage, and metadata-only non-claims were implemented for independent review.
- `done` (2026-06-16): Independent spec review accepted the table fact dump evidence with no remediations, code review accepted with non-blocking notes, and fresh validation remained green. Evidence: `reports/pure-kotlin-text/2026-06-16-kfont-m2-004-table-fact-dumps.md`.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M2`
- `area:validation`
- `claim:tracked-gap`
