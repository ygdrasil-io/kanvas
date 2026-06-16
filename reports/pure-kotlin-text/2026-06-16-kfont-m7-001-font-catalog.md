# KFONT-M7-001 - Bundled deterministic font catalog

## Scope landed

- `font/core` now exposes a deterministic bundled catalog surface through
  `BundledFontCatalog`, `BundledFontCatalogEntry`,
  `BundledFontCatalogInput`, `BundledFontCatalogBuilder`, and
  `BundledFontCatalogWriter`.
- `reports/pure-kotlin-text/font-catalog.json` is a checked-in canonical dump
  generated from repo-owned bundled fixtures only.
- The current dump records three deterministic entries:
  `single-ttf-liberation-sans`, `otf-cff-source-serif`, and
  `variable-ttf-roboto-flex`.

## Evidence

- `FontCatalogTest` proves byte-identical output across repeated loads and
  shuffled input order.
- Catalog entries record `FontSourceID`, `TypefaceID`, source SHA-256,
  family/style facts, generic family facts, script coverage labels,
  locale hints, variation axes, outline/scaler facts, and
  license/provenance metadata.
- Duplicate family/style rows keep the first deterministic entry and emit
  `font.catalog.duplicate-face`.
- Incomplete provenance emits `font.catalog.provenance-missing`.
- Unsupported outline and missing required-table diagnostics are preserved in
  catalog evidence, and existing fallback coverage still asserts
  `font.fallback-family-unavailable`.
- Host-dependent system-scanned sources are excluded from the bundled catalog
  and emit `font.source.host-dependent` instead of entering the dump.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontCatalog*'
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:core:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Remaining gate

This slice keeps `KFONT-M7-001` in `review`, not `done`. The repo still lacks
deterministic bundled Hebrew/Arabic, Devanagari/Thai, and CJK catalog entries,
the optional emoji-capable metadata row, and a checked-in duplicate-family
conflict golden requested by the ticket. No fallback support, shaping support,
platform-font parity, or GPU text support claim is promoted by this evidence.
