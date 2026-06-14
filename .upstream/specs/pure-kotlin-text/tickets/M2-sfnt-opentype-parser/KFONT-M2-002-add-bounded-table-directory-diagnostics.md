---
id: "KFONT-M2-002"
title: "Add bounded table directory diagnostics"
status: "proposed"
milestone: "M2"
priority: "P0"
owner_area: "font-sfnt"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-004", "KFONT-M2-001"]
legacy_gate: null
---

# KFONT-M2-002 - Add bounded table directory diagnostics

## PM Note

Ce ticket rend les fontes cassées compréhensibles sans mettre en danger tout le parseur.

## Problem

SFNT table directories define offsets, lengths, checksums, tags, and required table presence. The parser must reject out-of-bounds reads, overlapping or duplicated records, missing required tables, and malformed optional tables with precise diagnostics. Without these diagnostics, malformed fixtures can crash the parse or be misreported as generic font failure.

## Scope

- Validate SFNT header, table count, search fields, table record offsets, lengths, and checksum fields with bounded reads.
- Detect duplicate table tags, overlapping slices, zero-length required tables, and records that exceed source byte bounds.
- Emit required-table diagnostics for `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`, `OS/2`, `post`, `loca`, and `glyf` when the outline contract requires them.
- Distinguish malformed required tables from malformed optional tables.
- Add deterministic diagnostics to `sfnt-directory.json`.

## Non-Goals

- Do not implement detailed parsing of each table payload.
- Do not decide scaler support or glyph rendering policy.
- Do not silently repair malformed offsets or lengths.
- Do not widen parser bounds to satisfy a malformed fixture.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class SFNTTableRecord(
    val tag: String,
    val checksum: UInt,
    val offset: UInt,
    val length: UInt,
)

data class TableDirectoryDiagnostic(
    val code: String,
    val tag: String?,
    val offset: UInt?,
    val length: UInt?,
    val sourceLength: UInt,
)

class SFNTTableDirectoryValidator {
    fun validate(records: List<SFNTTableRecord>, sourceLength: UInt): List<TableDirectoryDiagnostic>
}
```

## Acceptance Criteria

- [ ] Out-of-bounds table records emit a stable diagnostic such as `font.sfnt.table-out-of-bounds`.
- [ ] Missing required tables emit `font.required-table-missing` with the missing tag and face identity.
- [ ] Malformed optional tables emit `font.optional-table-malformed` without invalidating ordinary outline text when safe.
- [ ] Duplicate or overlapping table records are reported deterministically and do not depend on map iteration order.
- [ ] The parser never reads beyond the declared bounded byte source while producing diagnostics.

## Required Evidence

- `sfnt-directory.json` diagnostics for out-of-bounds, duplicate tag, overlapping table, missing required table, and malformed optional table fixtures.
- Malformed fixture manifest entries with source hash and intended diagnostic.
- Unit test output proving invalid offsets/lengths do not crash the parser.
- Negative evidence that no generic `font missing` dashboard label is emitted for table directory failures.

## Fallback / Refusal Behavior

- Required-table failures reject the face with the precise missing or malformed table diagnostic.
- Optional-table failures must fail closed and keep parsing eligible outline text only when the spec permits that fallback.
- Silent offset clamping or host-parser fallback is not allowed.

## Dashboard Impact

- Expected row: `SFNT bounded table directory diagnostics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no until malformed fixture evidence and diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*TableDirectory*' --tests '*MalformedSFNT*'
```

## Status Notes

- `proposed`: Directory diagnostic cases are specified, but no malformed fixture evidence is attached yet.
- Move to `ready` after KFONT-M0-004 accepts diagnostic namespace rules and KFONT-M2-001 defines parse entry points.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M2`
- `area:font-sfnt`
- `claim:tracked-gap`
