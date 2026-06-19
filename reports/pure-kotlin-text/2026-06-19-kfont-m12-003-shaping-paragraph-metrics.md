# KFONT-M12-003 - Shaping and paragraph telemetry

## Scope landed

- `reports/pure-kotlin-text/shaping-metrics.json` now records deterministic
  shaping telemetry slices for Latin, Arabic, Devanagari, Thai, mixed bidi,
  CJK variation-selector, and explicit emoji-sequence refusal cases.
- `reports/pure-kotlin-text/paragraph-metrics.json` now records deterministic
  paragraph telemetry slices for shaping requests, wrapped layout, line-break
  pressure, hit testing, selection queries, and placeholder conflict cases.
- The wave keeps the telemetry posture advisory-only and does not widen
  shaping, paragraph, emoji, GPU, or release-gate claims.

## Evidence

- Shaping samples separate segmentation, bidi, script itemization, fallback,
  GSUB, GPOS, glyph-count, and cluster-count series in one stable dump.
- Paragraph samples separate layout, line-break opportunity count, shaped-run
  count, line count, hit-test time, selection-query time, ellipsis attempts,
  and placeholder count in one stable dump.
- Refusal visibility remains explicit: `text.shaping.fallback-missing`,
  `text.shaping.emoji-sequence-unsupported`, and
  `text.paragraph.placeholder-ellipsis-conflict` stay serialized as
  diagnostics attached to telemetry samples rather than hidden behind success
  counters.
- `scaledemoji` remains open. This telemetry wave is still `tracked-gap`
  evidence and does not claim emoji shaping support, color glyph rendering, or
  GPU text readiness.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_kfont_m12_001_telemetry_pm_evidence.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

## Remaining gate

No ticket-local gate remains for `KFONT-M12-003`. Broader shaping promotion,
emoji fallback/color/rendering evidence, complete paragraph bidi visual-order
evidence, and any GPU text claim remain owned by their separate tickets and
legacy gates.
