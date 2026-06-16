# KFONT-M9-001 - GlyphStrikeKey Contract Evidence

Date: 2026-06-16
Status: done; independently reviewed and freshly validated
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M9-glyph-artifacts/KFONT-M9-001-complete-glyphstrikekey.md`

## Scope

This checkpoint completes the contract-only `GlyphStrikeKey` identity work needed
before glyph artifact planning, A8/SDF generation, atlas lifecycle, and GPU text
handoff can rely on deterministic key evidence.

## Files

- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/GlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/GlyphStrikeKeyContractTest.kt`
- `reports/font/fixtures/expected/glyph/glyph-strike-key.json`
- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontDiagnosticTaxonomyTest.kt`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`

## Evidence

- `GlyphStrikeKey` now carries `glyphId`, source cluster facts, size, scale,
  variation coordinates, palette identity, route, mask format, transform bucket,
  subpixel bucket, edging, SDF spread/source resolution, Unicode data version,
  and renderer descriptor version in deterministic preimages.
- The legacy `rendererVersion` constructor argument remains as a compatibility
  alias, while canonical dumps emit `rendererDescriptorVersion`.
- `glyph-strike-key.json` covers A8, SDF, outline, COLR, bitmap PNG, SVG, and
  unsupported routes with compact hashes checked by `GlyphStrikeKeyContractTest`.
- The fixture includes variation, palette, renderer descriptor, and a
  Unicode-sensitive non-trivial cluster in a refused unsupported route.
- Refusal records cover missing `TypefaceID`, nondeterministic host source,
  forbidden live-handle fields, LCD future research, and route-specific key gaps.
- `font-diagnostic-taxonomy.json` includes `text.glyph.cache-key-nondeterministic`
  and `text.glyph.LCD-future-research` with claim promotion disabled.
- Dashboard and evidence manifests expose the contract as `tracked-gap` evidence
  and keep GPU/support promotion disabled.

## Validation

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphStrikeKey*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk ./gradlew --no-daemon :font:core:test --tests '*FontDiagnosticTaxonomy*'
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest scripts/test_validate_pure_kotlin_text_dump_index.py scripts/test_validate_pure_kotlin_text_fixture_manifest.py scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk git diff --check
```

## Independent Review

- Spec review rejected two iterations, then accepted after `glyphId`, variation,
  COLR renderer descriptor, diagnostic taxonomy, and Unicode-sensitive fixture
  gaps were remediated.
- Code-quality review initially rejected alias ordering, mutable evidence-list
  capture, and `fixtureIds` provenance scope; the remediated patch was
  re-reviewed and accepted.

## Remaining Gate

This checkpoint does not claim A8 rasterization, SDF generation, atlas packing,
GPU text routes, color/emoji rendering, LCD support, or `dftext` retirement.
Next gates remain `KFONT-M9-002`, `KFONT-M9-003`, `KFONT-M9-004`, and
`KFONT-M9-005` before M11 A8 GPU handoff can be re-evaluated.
