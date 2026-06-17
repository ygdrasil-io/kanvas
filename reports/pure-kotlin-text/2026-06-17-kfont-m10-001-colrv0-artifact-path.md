# KFONT-M10-001 - Complete COLRv0 plan to artifact path

Status: implemented with fresh deterministic dump evidence and validation.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-001-complete-colrv0-plan-to-artifact-path.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/color-glyph-plan.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `COLRV0ColorGlyphPlanner` promotes parsed COLRv0/CPAL facts into a typed `ColorGlyphPlan` with M9-derived artifact-key hashes, palette identity and override counts, ordered layer plans, aggregate bounds, fallback policy, and stable `text.color.CPAL-malformed` / `text.color.COLR-malformed` diagnostics.
- `ColorGlyphSurfaceTest` asserts deterministic COLRv0 parsing, paint-graph ordering, typed plan serialization, and explicit monochrome outline fallback only when the route policy allows it.
- `reports/font/fixtures/expected/color/color-glyph-plan.json` checks in the reviewed deterministic dump for a multi-layer COLRv0 base glyph with a non-default palette selection and explicit override.
- `reports/pure-kotlin-text/dump-evidence-index.json` and `reports/pure-kotlin-text/fixture-evidence-manifest.json` classify this as golden-gated planning evidence and keep COLRv1/bitmap/SVG/emoji/GPU gates explicit.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.parsesCOLRV0BaseGlyphsFromRawTableBytes --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV0PaintGraphWithPaletteIndexes --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV0ColorGlyphPlanWithPaletteOverridesArtifactKeysAndDeterministicDump --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.fallsBackToOutlineWhenCOLRPaletteResolutionFailsAndFallbackIsAllowed
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this closes only the COLRv0 typed plan slice. It does not claim
COLRv1 paint-operation support, bitmap/SVG glyph routing, emoji sequence
planning, GPU artifact registration, GPU composite execution, or platform/native
fallback behavior.
