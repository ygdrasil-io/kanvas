# KFONT-M7-001 - Bundled deterministic font catalog

## Scope landed

- `font/core` now exposes a deterministic bundled catalog surface through
  `BundledFontCatalog`, `BundledFontCatalogEntry`,
  `BundledFontCatalogInput`, `BundledFontCatalogBuilder`, and
  `BundledFontCatalogWriter`.
- `reports/pure-kotlin-text/font-catalog.json` is a checked-in canonical dump
  generated from repo-owned bundled fixtures only.
- `reports/pure-kotlin-text/font-catalog-duplicate-face.json` is a checked-in
  duplicate-family conflict golden for `font.catalog.duplicate-face`.
- The current dump records nine deterministic entries spanning
  `single-ttf-liberation-sans`, `otf-cff-source-serif`,
  `variable-ttf-roboto-flex`, `single-ttf-noto-sans-hebrew`,
  `single-ttf-noto-naskh-arabic`, `single-ttf-noto-sans-devanagari`,
  `single-ttf-noto-sans-thai`, `single-otf-noto-sans-sc`, and
  `emoji-colrv1-noto-color`.

## Evidence

- `FontCatalogTest` proves byte-identical output across repeated loads and
  shuffled input order.
- Catalog entries record `FontSourceID`, `TypefaceID`, source SHA-256,
  family/style facts, generic family facts, script coverage labels,
  locale hints, variation axes, outline/scaler facts, and
  license/provenance metadata.
- Official vendored fixtures and licenses are now checked in under
  `reports/font/fixtures/fonts/fallback/`,
  `reports/font/fixtures/licenses/`, and
  `reports/font/fixtures/provenance/index.json` with deterministic hashes and
  the fixture validator-enforced 20 MiB budget still satisfied.
- Duplicate family/style rows keep the first deterministic entry and emit
  `font.catalog.duplicate-face`, with the checked-in
  `font-catalog-duplicate-face.json` golden locking the regression surface.
- Incomplete provenance emits `font.catalog.provenance-missing`.
- Unsupported outline and missing required-table diagnostics are preserved in
  catalog evidence, and existing fallback coverage still asserts
  `font.fallback-family-unavailable`.
- Host-dependent system-scanned sources are excluded from the bundled catalog
  and emit `font.source.host-dependent` instead of entering the dump.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontCatalog*' --tests '*FontFixtureManifest*' --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:core:test
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining gate

This slice closes the deterministic catalog-breadth gate requested by
`KFONT-M7-001`, but it still does not promote fallback support, cluster-safe
fallback promotion, shaping support, platform-font parity, color-glyph
rendering support, or GPU text support. The fallback surface still keeps CPU
oracle evidence and the explicit `scaledemoji` gate on the owning tickets.
