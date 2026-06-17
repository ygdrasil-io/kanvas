# KFONT-M10-004 - Implement COLRv1 transform/composite/clip operation group

Status: implemented with fresh deterministic dump evidence and validation.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-004-implement-colrv1-transform-composite-clip-operation-group.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `font/glyph/src/main/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurface.kt`
- `font/glyph/src/test/kotlin/org/graphiks/kanvas/glyph/color/ColorGlyphSurfaceTest.kt`
- `reports/font/fixtures/expected/color/color-glyph-plan.json`
- `reports/font/fixtures/expected/color/colrv1-paint-graph.json`
- `reports/font/fixtures/expected/color/color-glyph-composite-plan.json`
- `reports/pure-kotlin-text/dump-evidence-index.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`
- `reports/pure-kotlin-text/font-fixture-inventory.json`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`

Evidence:

- `COLRV1ColorGlyphPlanner` now records bounded transform/composite/clip planning facts for translate, generic transform, classified scale/rotate/skew, and clip-wrapped composite operations, including deterministic bounds, determinant facts, destination-read hints, and layer-isolation hints.
- `ColorGlyphSurfaceTest` proves accepted-path serialization for the transform/composite/clip bundle and refused-path serialization for singular transforms, unsupported composite modes, and clip-budget overflow with explicit outline fallback only when allowed.
- `reports/font/fixtures/expected/color/color-glyph-plan.json` and `reports/font/fixtures/expected/color/colrv1-paint-graph.json` now bundle the accepted transform/composite/clip cases plus refusal diagnostics, while `reports/font/fixtures/expected/color/color-glyph-composite-plan.json` records the ordered composite-plan handoff facts for `colrv1-composite-clip`.
- `reports/pure-kotlin-text/dump-evidence-index.json`, `reports/pure-kotlin-text/fixture-evidence-manifest.json`, and `reports/pure-kotlin-text/font-fixture-inventory.json` keep `coloremoji_blendmodes` open and classify this as bounded CPU planning evidence only.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1TransformCompositeClipPlansAndDeterministicPaintGraphDumps --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1SingularTransformUnsupportedCompositeModeAndClipBudgetOverflowWhenFallbackAllowed
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this closes only the bounded COLRv1 transform/composite/clip planning slice. It does not claim GPU composite execution, dedicated `PaintScale` / `PaintRotate` / `PaintSkew` parse-format support beyond classified generic transforms, bitmap/SVG route support, emoji sequence planning, GPU artifact registration, or platform/native color fallback behavior. The legacy gate `coloremoji_blendmodes` remains open until CPU oracle and GPU route evidence are linked.
