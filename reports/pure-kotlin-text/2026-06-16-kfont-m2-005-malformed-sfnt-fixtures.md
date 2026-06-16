# KFONT-M2-005 Malformed SFNT Fixture Suite

Status: done; independently reviewed and freshly validated.

## Scope

KFONT-M2-005 adds a deterministic, pure Kotlin malformed SFNT fixture-suite
dump. The suite records generated fixture provenance for bad SFNT version,
truncated header, invalid TTC index, out-of-bounds table record, overlapping
tables, duplicate tag, missing required table, malformed optional table, and
unsupported `cmap` format.

## Files

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTParserEntryPointTest.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTSurfaceTest.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/MalformedSFNTFixtureSuiteTest.kt`
- `reports/pure-kotlin-text/sfnt-directory.json`
- `reports/pure-kotlin-text/cmap-map.json`
- `reports/pure-kotlin-text/malformed-sfnt-fixtures.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-005-add-malformed-sfnt-fixture-suite.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `MalformedSFNTFixtureSuiteReportWriter` emits canonical
  `org.graphiks.kanvas.font.sfnt.MalformedSFNTFixtureSuiteReport.v1` JSON.
- `malformed-sfnt-fixtures.json` covers all nine scoped malformed cases with
  fixture ID, generator ID, sorted generator parameters, byte length,
  content SHA-256, primary expected diagnostic, expected parser outcome, linked
  evidence dump path, and diagnostic snapshots.
- Linked evidence paths stay within `sfnt-directory.json`, `sfnt-tables.json`,
  and `cmap-map.json`; focused tests verify that linked entry IDs resolve and
  match fixture IDs, byte lengths, hashes, diagnostics, and source-face facts.
- Focused tests prove deterministic generation, byte-for-byte golden stability,
  stable hashes, primary diagnostic coverage, and no hidden external/native/GPU
  support tokens.
- `dump-evidence-index.json` tracks `malformed-sfnt-fixtures` as producer-only
  evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*MalformedSFNT*' --tests '*SFNTParser*' --tests '*CMap*' --tests '*TableFactDump*' --rerun-tasks
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Review

- Independent spec review accepted after linked evidence IDs, hashes, status
  counts, and no-`__pycache__` status were verified.
- Independent code review accepted with no findings.

## Non-Claims

This is fixture-suite evidence only. It does not promote malformed recovery,
parser support, scaler support, shaping support, color glyph rendering, native
engine parity, fallback behavior, paragraph layout, or GPU support.

## Remaining Gate

No remaining gate for KFONT-M2-005. Remaining format-14 family fixture gates stay
explicit in `fixture-evidence-manifest.json` and do not promote support claims.
