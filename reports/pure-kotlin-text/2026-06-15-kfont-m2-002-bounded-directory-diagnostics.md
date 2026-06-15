# KFONT-M2-002 Bounded Table Directory Diagnostics

Date: 2026-06-15
Status: review
Classification: tracked-gap
Claim promotion allowed: false

## Scope

This slice adds bounded SFNT directory diagnostic evidence on top of the
M2-001 parser entry point. It keeps the report route non-promoting and does
not claim full SFNT conformance, scaler support, shaping support, color glyph
support, rendering support, or GPU text-route support.

## What Changed

- `SFNTParseRequest` now carries an explicit `requiredTables` set.
- `DefaultSFNTParser` passes that set into
  `SFNTTableDirectoryValidator.validate(...)` so directory diagnostics are
  attached to the selected bounded face.
- `sfnt-directory.json` now includes a generated
  `sfnt-directory-diagnostics-generated` entry with duplicate, overlap,
  out-of-bounds, and missing-required-table diagnostics.
- `sfnt-directory.json` also includes
  `generated-optional-table-malformed`, a generated `fvar` malformed fixture
  with `sourceSha256` and `faceDiagnostics`.
- Malformed optional table parser failures are classified with
  `font.sfnt.optional-table-malformed` instead of generic invalid-table
  labels.
- The pure Kotlin font diagnostic taxonomy now accepts the SFNT directory
  diagnostic codes while preserving `claimPromotionAllowed=false`.

## Evidence

- `reports/pure-kotlin-text/sfnt-directory.json` records:
  - `font.sfnt.required-table-missing` for `glyf` and `head`;
  - `font.sfnt.table-duplicate` for duplicate `name`;
  - `font.sfnt.table-out-of-bounds` for `post`;
  - `font.sfnt.table-overlap` for overlapping `cmap` and `post`;
  - `font.sfnt.optional-table-malformed` for malformed optional `fvar`.
- `reports/pure-kotlin-text/font-fixture-inventory.json` records the generated
  optional-table fixture source SHA-256 and intended diagnostic.
- `SFNTParserEntryPointTest.requiredTablesFlowIntoSfntParserDirectoryDiagnostics`
  proves `requiredTables` reaches the default parser and yields stable dumps.
- `SFNTParserEntryPointTest.sfntParserUsesOneBoundedRequestForSingleSfntAndTtcDirectoryReports`
  checks the `generated-optional-table-malformed` report entry, its source
  hash, and the `fvar` diagnostic.
- `SFNTSurfaceTest.tableDirectoryValidatorReportsBoundedDiagnosticsDeterministically`
  keeps deterministic ordering for missing, duplicate, out-of-bounds, and
  overlap diagnostics.
- `SFNTSurfaceTest.defaultOpenTypeFaceParserReportsMalformedFvarTablesAsDiagnostics`
  and related `fvar`, `GPOS`, and `kern` tests assert malformed optional
  tables use `font.sfnt.optional-table-malformed`.
- `FontDiagnosticTaxonomyTest` confirms all new SFNT codes remain accepted
  tracked-gap diagnostics and do not permit claim promotion.

## Validation

Fresh local validation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon --rerun-tasks :font:sfnt:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_font_fixtures.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_font_fixtures.py
```

Result: passed locally on 2026-06-15.

## Non-Claims

- No complete SFNT/OpenType conformance claim.
- No complete malformed fixture suite claim; KFONT-M2-005 still owns that gate.
- No scaler, shaping, paragraph, color glyph, rendering, font fallback, or GPU
  text-route support claim.
- No host/native font parser fallback claim.
- No readiness delta is claimed without denominator evidence.
- No search-field formula validation or checksum verification claim.

## Remaining Gates

- Independent subagent spec and quality reviews were accepted for this wave
  after remediation.
- PR validation and merge.
- Search-field formula validation and checksum verification remain future
  hardening, not a closed gate in this slice.
- Later M2 tickets still own complete `cmap` coverage, table fact dumps, and
  the malformed SFNT fixture suite.
