---
id: "KFONT-M2-005"
title: "Add malformed SFNT fixture suite"
status: "proposed"
milestone: "M2"
priority: "P0"
owner_area: "fixtures"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M2-002", "KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M2-005 - Add malformed SFNT fixture suite

## PM Note

Ce ticket ajoute les fontes volontairement cassées qui prouvent que le parser refuse proprement.

## Problem

Malformed SFNT behavior must be tested with narrow, reviewed fixtures. Without a fixture suite, parser diagnostics can look correct in code review but remain unproven for dangerous inputs such as bad headers, invalid offsets, overlapping tables, missing required tables, unsupported `cmap` formats, or malformed optional tables.

## Scope

- Add generated or bundled malformed fixtures for bad SFNT version, truncated header, invalid TTC index, out-of-bounds table record, overlapping tables, duplicate tag, missing required table, malformed optional table, and unsupported `cmap` format.
- Record fixture provenance, hash, generator parameters, intended diagnostic, and expected parser outcome in the fixture manifest.
- Ensure each fixture is small and focused on one failure mode.
- Link each fixture to `sfnt-directory.json`, `sfnt-tables.json`, or `cmap-map.json` evidence.
- Keep malformed fixtures pure Kotlin and independent from external parser behavior.

## Non-Goals

- Do not add broad fuzzing infrastructure in this ticket.
- Do not create fixtures that require proprietary font data.
- Do not mark malformed recovery as support unless the spec explicitly allows safe optional-table fallback.
- Do not test glyph outline malformation here; that belongs to M3.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class MalformedSFNTCase {
    BadVersion,
    TruncatedHeader,
    InvalidCollectionIndex,
    TableOutOfBounds,
    OverlappingTables,
    DuplicateTag,
    MissingRequiredTable,
    MalformedOptionalTable,
    UnsupportedCMapFormat,
}

data class MalformedSFNTFixture(
    val fixtureId: String,
    val case: MalformedSFNTCase,
    val sha256: String,
    val expectedDiagnostic: String,
    val expectedOutcome: ParserOutcome,
)
```

## Acceptance Criteria

- [ ] Each malformed fixture has one primary expected diagnostic and a stable source hash.
- [ ] Required-table malformed fixtures reject the face instead of producing partial support claims.
- [ ] Optional-table malformed fixtures preserve ordinary outline parsing only when the target spec allows fallback.
- [ ] Unsupported `cmap` fixture emits `font.cmap-format-unsupported`.
- [ ] Fixture generation is deterministic and does not depend on external font engines.

## Required Evidence

- Malformed fixture manifest entries with fixture ID, generator or source path, hash, case, and expected diagnostic.
- Diagnostic snapshots for each malformed SFNT case listed in Scope.
- `sfnt-directory.json`, `sfnt-tables.json`, or `cmap-map.json` outputs proving each failure mode.
- Determinism output proving generated malformed fixtures are byte-identical across runs.

## Fallback / Refusal Behavior

- Malformed fixture evidence remains `fixture-gated` until every listed case has manifest and diagnostic output.
- Parser recovery must be explicit: required failures refuse, optional failures fail closed, and unsupported formats diagnose.

## Dashboard Impact

- Expected row: `malformed SFNT fixture suite`.
- Expected classification: `fixture-gated`.
- Claim promotion allowed: no until every required malformed fixture and diagnostic snapshot is attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*MalformedSFNT*' --tests '*FixtureManifest*'
```

## Status Notes

- `proposed`: Malformed fixture cases are specified, but no suite evidence is attached yet.
- Move to `ready` after bounded directory diagnostics and OpenType fact dumps define the expected outputs.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M2`
- `area:fixtures`
- `claim:fixture-gated`
