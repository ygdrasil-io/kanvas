---
id: "KFONT-M1-003"
title: "Add deterministic source/typeface dumps"
status: "done"
milestone: "M1"
priority: "P0"
owner_area: "validation"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-001", "KFONT-M1-002"]
legacy_gate: null
---

# KFONT-M1-003 - Add deterministic source/typeface dumps

## PM Note

Ce ticket fournit les fichiers que le PM et la review pourront comparer pour vérifier que l'identité font ne dérive pas.

## Problem

`FontSourceID` and `TypefaceID` are only useful as support gates if their inputs are serialized in stable, reviewable evidence. The target needs `font-source.json` and `typeface-id.json` dumps that avoid nondeterministic ordering and explain host-dependent sources before parser or scaler support is promoted.

## Scope

- Add canonical serializers for source identity preimages and typeface identity preimages.
- Use stable field names, sorted collections, normalized paths or fixture IDs, and explicit schema versioning.
- Emit diagnostics alongside dumps without relying on object identity or host-specific temp paths.
- Provide a determinism check that runs the same fixtures twice and compares byte-for-byte dump output.
- Include dump examples for source kind, face count, table tags, selected `cmap`, variation coordinates, and palette identity.

## Non-Goals

- Do not add new font fixture bytes except where KFONT-M1-004 requires manifest entries.
- Do not serialize full table payloads, glyph outlines, shaping results, or GPU artifacts.
- Do not use external engine output as a normative dump oracle.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class FontIdentityDumpBundle(
    val schemaVersion: Int,
    val fontSource: FontSourceIdentityPreimage,
    val typefaces: List<TypefaceIdentityPreimage>,
    val diagnostics: List<SerializedFontDiagnostic>,
)

interface FontIdentityDumpWriter {
    fun writeFontSourceJson(source: FontSourceIdentityPreimage): CanonicalJson
    fun writeTypefaceIdJson(typefaces: List<TypefaceIdentityPreimage>): CanonicalJson
}

fun assertDeterministicDump(fixture: FontFixtureRef): DumpDiffResult
```

## Acceptance Criteria

- [x] `font-source.json` uses sorted table tags and stable provenance fields.
- [x] `typeface-id.json` uses sorted variation coordinates and deterministic typeface ordering.
- [x] Re-running the dump command on the same fixture produces byte-identical output.
- [x] Host-dependent sources are visible in dumps and cannot be used as normative fixture evidence.
- [x] Dump schema version changes are explicit and reviewable.

## Required Evidence

- Golden `font-source.json` and `typeface-id.json` for at least one bundled TTF fixture.
- Determinism report comparing two dump runs over the same input.
- Snapshot with a host-dependent source diagnostic.
- Schema description listing required fields and ordering rules.

## Fallback / Refusal Behavior

- If a field cannot be serialized deterministically, the related claim must stay `tracked-gap` until the field is normalized or removed from the identity preimage.
- Dumps must not hide host-dependent source state behind a stable-looking ID.

## Dashboard Impact

- Expected row: `source/typeface identity dumps`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. Dumps are evidence plumbing for later parser and scaler support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*IdentityDump*'
```

## Status Notes

- `done`: Merged into `master` by PR #1657 (`8ef0f9b72`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `review`: `FontIdentityDumpWriter` emits the checked-in `font-source.json`
  and `typeface-id.json` goldens through canonical wrappers, records explicit
  schema version/order rules, and verifies repeated dump bundles byte-for-byte.
  Evidence report: `reports/pure-kotlin-text/2026-06-15-kfont-m1-003-identity-dumps.md`.
- `review` validation: `rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*IdentityDump*'`
  passed 7 focused tests, and
  `rtk ./gradlew --no-daemon --rerun-tasks :font:core:test` passed 38
  `font/core` tests.
- Independent reviews: spec compliance and code quality approved after
  remediation of raw golden comparison, defensive-copy, and canonical JSON
  validation findings.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M1`
- `area:validation`
- `claim:tracked-gap`
