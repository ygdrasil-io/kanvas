# KFONT-M2-004 Table Fact Dumps

Status: done; independently reviewed and freshly validated.

## Scope

KFONT-M2-004 adds a metadata-only OpenType table fact bundle for the M2 SFNT
parser. The bundle records required/high-value table presence, bounded byte
ranges, checksums, parser status, role, support classification, diagnostics,
and deterministic links to KFONT-M2-003 `cmap-map.json` facts.

## Files

- `font/sfnt/src/main/kotlin/org/graphiks/kanvas/font/sfnt/SFNT.kt`
- `font/sfnt/src/test/kotlin/org/graphiks/kanvas/font/sfnt/SFNTTableFactDumpTest.kt`
- `reports/pure-kotlin-text/sfnt-tables.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `scripts/validate_pure_kotlin_text_dump_index.py`
- `scripts/test_validate_pure_kotlin_text_dump_index.py`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/KFONT-M2-004-add-opentype-table-fact-dumps.md`
- `.upstream/specs/pure-kotlin-text/tickets/M2-sfnt-opentype-parser/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `OpenTypeTableFactReportWriter` emits canonical
  `org.graphiks.kanvas.font.sfnt.OpenTypeTableFactReport.v1` JSON.
- `sfnt-tables.json` records one Liberation Sans TTF row linked to M1
  `font-source.json` label `bundled-fixture` and `typeface-id.json` label
  `single-face-ttf`.
- The Liberation row includes present facts for the TrueType table set:
  `cmap`, `head`, `hhea`, `hmtx`, `maxp`, `name`, `OS/2`, `post`, `loca`,
  and `glyf`, plus optional shaping metadata for `GDEF`, `GSUB`, `GPOS`, and
  `kern`.
- Generated rows cover a malformed optional `fvar` table with
  `font.sfnt.optional-table-malformed` and a missing required `loca`/`glyf`
  snapshot with `font.sfnt.required-table-missing`.
- The table fact order is canonical and independent of SFNT directory order.
- `cmapMapLink` points to `reports/pure-kotlin-text/cmap-map.json` and lists
  KFONT-M2-003 entry IDs as metadata-only links.
- `dump-evidence-index.json` now tracks `sfnt-table-facts` as producer-only
  evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*TableFactDump*' --tests '*CMap*'
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk ./gradlew --no-daemon :font:sfnt:test
rtk git diff --check
```

## Non-Claims

This is table metadata evidence only. It does not claim shaping support, glyph
scaling, CFF/CFF2 outline support, color glyph rendering, bitmap/SVG rendering,
native engine parity, fallback behavior, paragraph layout, or GPU support.

## Review

Independent spec review accepted the evidence with no remediations. Independent
code review accepted with non-blocking notes on writer-level entry ordering and
validation-command consistency.

## Remaining Gate

No remaining gate for KFONT-M2-004. Downstream scaler, shaping, color, bitmap,
SVG, fallback, paragraph, and GPU text-route tickets still need their own
evidence before any support promotion.
