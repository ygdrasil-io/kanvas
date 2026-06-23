# KFONT-M12-004 - Glyph artifact and cache telemetry

## Scope landed

- `reports/pure-kotlin-text/glyph-artifact-metrics.json` now records
  deterministic glyph artifact telemetry slices for route taxonomy, A8/SDF
  generation, and bounded COLR/bitmap/SVG planning costs.
- `reports/pure-kotlin-text/glyph-cache-metrics.json` now records
  deterministic cache telemetry slices for cold misses, warm hits, atlas pack
  cost, resident/upload bytes, eviction, invalidation-token changes, artifact
  budget refusals, atlas-capacity pressure, and stale-generation refusals.
- `reports/pure-kotlin-text/glyph-atlas-occupancy.json` now records stable
  atlas artifact IDs, dimensions, key hashes, entry counts, and occupancy
  ratios without widening any GPU or `dftext` claim.

## Evidence

- `glyph-artifact-metrics.json` keeps route counts explicit across outline, A8,
  SDF, COLR, bitmap PNG, SVG, and unsupported/refusal paths instead of hiding
  them behind one cache-hit counter.
- A8 and SDF generation remain separated: `glyph-artifact.a8.time` is attached
  to the mixed route/refusal sample, while `glyph-artifact.sdf.time` is pinned
  to the `sdf-default-spread` fixture with stable spread/source-resolution
  counters.
- `glyph-cache-metrics.json` separates atlas pack time, resident cache bytes,
  and upload bytes while serializing stable diagnostics for
  `text.glyph.artifact-budget-exceeded`,
  `text.glyph.atlas-capacity-exceeded`, and
  `text.glyph.atlas-generation-stale`.
- `glyph-atlas-occupancy.json` is the ticket-local equivalent occupancy dump:
  it records stable atlas artifact IDs, strike-key hashes, key-preimage hashes,
  atlas dimensions, entry counts, and occupancy ratios while keeping all
  evidence CPU-side and advisory-only.
- Dashboard, PM bundle packaging, fixture manifest, and dump index now expose
  separate `Glyph artifact metrics`, `Glyph cache metrics`, and
  `Glyph atlas occupancy` rows as `tracked-gap` evidence while leaving
  `dftext` visible and keeping GPU handoff owned by `KFONT-M12-005`.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontTelemetrySchemaTest*'
rtk ./gradlew --no-daemon validateKfontM12001TelemetryPmEvidence
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
rtk ./gradlew --no-daemon pipelinePmBundle
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining gate

No ticket-local gate remains for `KFONT-M12-004`. `dftext` retirement, GPU
text route claims, upload-execution claims, and any performance-gate promotion
remain owned by `KFONT-M12-005` and the broader M11 GPU handoff chain.
