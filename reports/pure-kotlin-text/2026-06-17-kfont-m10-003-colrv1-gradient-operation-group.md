# KFONT-M10-003 - Implement COLRv1 gradient and variable-gradient operation group

Status: implemented with fresh deterministic dump evidence and validation.

Files:

- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/KFONT-M10-003-implement-colrv1-gradient-and-variable-gradient-operation-group.md`
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

- `COLRV1ColorGlyphPlanner` now records bounded linear, radial, sweep, and variable-stop linear gradient nodes as typed `COLRV1GradientEvidence`, including stable geometry payloads, resolved CPAL colors, stop ordering, sorted variation coordinates, stop-level alpha deltas, and propagated glyph bounds.
- `ColorGlyphSurfaceTest` proves accepted-path serialization for the linear/radial/sweep/variable-stop cases and refused-path serialization for missing variation data, malformed linear coordinates, and gradient-stop budget overflow with explicit monochrome outline fallback only when allowed.
- `reports/font/fixtures/expected/color/colrv1-paint-graph.json` now bundles the accepted solid/glyph/colr-glyph case from KFONT-M10-002 with the new gradient accepted/refusal cases, and `reports/font/fixtures/expected/color/color-glyph-plan.json` mirrors those bundle cases inside `ColorGlyphPlan` evidence.
- `reports/pure-kotlin-text/dump-evidence-index.json`, `reports/pure-kotlin-text/fixture-evidence-manifest.json`, and `reports/pure-kotlin-text/font-fixture-inventory.json` keep remaining gates explicit for `PaintVar*Gradient` geometry, transform/composite/clip execution, bitmap/SVG routing, emoji planning, and GPU execution.

Validation:

```bash
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1SolidGlyphColrGlyphPlanAndDeterministicPaintGraphDump --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1GradientPlansAndDeterministicPaintGraphDumps --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1VarSolidWithoutVariationSupportAndFallsBackToOutlineWhenAllowed --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1VariableGradientStopWithoutVariationSupportAndFallsBackToOutlineWhenAllowed --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1GradientStopBudgetOverflowAndFallsBackToOutlineWhenAllowed --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1MalformedGradientCoordinatesAndFallsBackToOutlineWhenAllowed --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1BudgetExceededRefusalDiagnostic --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.detectsCOLRV1PaintColrGlyphCyclesWithStableDiagnostic
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

Remaining gate: this closes only the bounded COLRv1 gradient planning slice. It does not claim `PaintVarLinearGradient` / `PaintVarRadialGradient` / `PaintVarSweepGradient` geometry support, transform/composite/clip execution, bitmap/SVG route support, emoji sequence planning, GPU artifact registration, GPU composite execution, or platform/native color fallback behavior.
