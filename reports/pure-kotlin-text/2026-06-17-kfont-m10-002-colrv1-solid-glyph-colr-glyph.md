# KFONT-M10-002 - Implement COLRv1 solid/glyph/colr-glyph operation group

Status: implemented with fresh deterministic dump evidence and validation.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-002-implement-colrv1-solid-glyph-colr-glyph-operation-group.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/color-glyph-plan.json`
- `reports/font/fixtures/expected/color/colrv1-paint-graph.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `COLRV1ColorGlyphPlanner` now emits deterministic `ColorGlyphPlan` and `COLRV1PaintGraphEvidence` dumps for the bounded `PaintSolid` / `PaintVarSolid` / `PaintGlyph` / `PaintColrGlyph` group, including stable node IDs, bounds propagation, M9 artifact-key refs, resolved CPAL colors, and explicit fallback policy.
- `ColorGlyphSurfaceTest` proves the accepted nested `PaintColrGlyph` -> `PaintGlyph` -> `PaintSolid` walk, the stable `PaintVarSolid` refusal when variation support is unavailable, and explicit monochrome outline fallback only when the caller allows it.
- `reports/font/fixtures/expected/color/colrv1-paint-graph.json` checks in the reviewed graph dump for the accepted operation group, while `reports/font/fixtures/expected/color/color-glyph-plan.json` now bundles the prior COLRv0 case with the new accepted and refused COLRv1 cases.
- `reports/pure-kotlin-text/dump-evidence-index.json`, `reports/pure-kotlin-text/fixture-evidence-manifest.json`, and `reports/pure-kotlin-text/font-fixture-inventory.json` keep remaining gates explicit for gradients, transforms, composites, clips, bitmap/SVG routes, emoji planning, and GPU execution.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this closes only the bounded COLRv1 solid/glyph/colr-glyph planning slice. It does not claim COLRv1 gradient/transform/composite/clip execution, bitmap/SVG route support, emoji sequence planning, GPU artifact registration, GPU composite execution, or platform/native fallback behavior.
