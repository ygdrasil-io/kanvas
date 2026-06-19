# KFONT-M12-002 - Parser and scaler metrics

## Scope landed

- `FontTelemetryEvidenceWriter` now emits dedicated deterministic dumps for
  parser and scaler producer telemetry through
  `reports/pure-kotlin-text/parser-metrics.json` and
  `reports/pure-kotlin-text/scaler-metrics.json`.
- The parser dump covers stable TTF, TTC, CFF-selected, variable-axis,
  malformed-directory, malformed-optional-table, and missing-required-table
  fixture IDs with repeated cold/warm samples and explicit `font.parser.*`
  trend series.
- The scaler dump covers simple `glyf`, composite `glyf`, variable `glyf`,
  CFF, CFF2 variation-store, and malformed CFF refusal fixtures with repeated
  samples and explicit `font.scaler.*` trend series.

## Evidence

- `parser-metrics.json` records stable fixture IDs, parsed table tags, bytes
  read, table counts, cache hit/miss counters, malformed/bounds failure
  counters, and deterministic semantic diagnostics such as
  `font.sfnt.table-overlap`, `font.sfnt.optional-table-malformed`, and
  `font.sfnt.required-table-missing`.
- `scaler-metrics.json` records glyph counts, outline command counts, bounds
  and metrics lookup timings, variation and charstring timings where
  applicable, cache hit/miss counters, `.notdef` fallback counts, and stable
  diagnostics such as `font.metrics-variation-unavailable`,
  `font.cff-table-malformed`, and `font.telemetry.scaler-domain-missing`.
- `font-claim-dashboard.json` now exposes separate `Font parser metrics` and
  `Font scaler metrics` advisory rows, while the older
  `font-telemetry-schema` row now points remaining downstream producer work only
  at `KFONT-M12-003`, `KFONT-M12-004`, and `KFONT-M12-005`.
- `font-telemetry-pm-bundle.json` now carries the checked-in parser/scaler
  dumps as advisory PM artifacts, and `pipelinePmBundle` now copies those
  exact checked-in JSON files without changing `warning-only` posture.
- The trend-series excerpt remains advisory only: `font.parser.scan.time`,
  `font.parser.parse.time`, `font.scaler.outline.time`,
  `font.scaler.metrics.time`, `font.scaler.variation.time`, and
  `font.scaler.charstring.time` are deterministic report keys, not release
  gates.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk ./gradlew --no-daemon pipelinePmBundle
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

No ticket-local gate remains for `KFONT-M12-002`. Shaping/paragraph,
glyph/cache, and GPU handoff producer emission stay with `KFONT-M12-003`,
`KFONT-M12-004`, and `KFONT-M12-005`. This evidence remains advisory telemetry
only and keeps `no-performance-release-gate-claim`,
`no-gpu-route-support-claim`, and all broader subsystem non-claims explicit.
