# COLRv1 — Complete Pipeline Integration Design

**Date:** 2026-07-03
**Status:** Design approved
**Depends on:** `11-color-glyph-emoji.md` (emoji pipeline Phase 1-3)

## Goal

Integrate the existing COLRv1 paint graph planner into the Kanvas GlyphScaler/GPU pipeline, extend the parser for missing paint formats (16-31), and migrate the ColrV1GM to `integration-tests/skia/gm/text/`.

## Scope

1. New module `font:colr` hosting shared COLRv1 types — breaks circular dependency `font:glyph → font:scaler`
2. Parser extensions — PaintScale, PaintRotate, PaintSkew formats (16-31) + multi-palette CPAL
3. GlyphScaler COLRv1 integration — parse via `COLRV1Parser`, produce `GlyphRepresentation.ColorLayersV1`
4. GPU renderer COLRv1 pipeline — Solid, Glyph, Gradient, Transform, Composite, ClipBox per-node dispatch
5. ColrV1GM migration — default category `gradient_stops_repeat`, LiberationSans "ABCD" fallback

## Non-scope

- Variable paint types (VarSolid handled via alpha only; VarGradient, VarTransform, VarTranslate etc. rejected)
- PaintColrGlyph cycle resolution beyond planner's existing depth limit
- Full 62-reference migration — only `colrv1_gradient_stops_repeat.png` moved; 61 refs remain in original-888/
- Sweep coincident, extend mode, foreground color, variable alpha categories — deferred to later per-category port

## Module Architecture

### New module: `font:colr`

```
font:colr/
  src/main/kotlin/org/graphiks/kanvas/font/colr/
    COLRV1Parser.kt           ← from font/glyph
    COLRV1Table.kt            ← from font/glyph (BaseGlyphV1List, LayerV1List, PaintTable, etc.)
    CPALV0Parser.kt           ← from font/glyph (extended for multi-palette)
    CPALTable.kt              ← from font/glyph
    COLRV1ColorGlyphPlanner.kt ← from font/glyph (plan(), walk(), budget limits, diagnostics)
    COLRV1PaintGraphTypes.kt  ← existing paint node sealed types
```

Dependencies:
- `font:core` — `TypefaceID`, `FontCore` types
- `font:sfnt` — raw SFNT byte reading utilities (no new deps needed; COLR/CPAL are self-contained table parsers)

### Updated dependency graph

```
font:colr ← NEW
  ├── font:core
  └── font:sfnt

font:scaler
  ├── font:core
  ├── font:sfnt
  └── font:colr ← NEW

font:glyph
  ├── font:core
  ├── font:scaler
  ├── font:sfnt
  └── font:colr ← replaces inline COLRv1 types
```

## Parser Extensions

### Paint formats 16-31

Add to `COLRV1Parser` dispatch:

| Format | Name | Planner kind | Var variant? |
|--------|------|-------------|-------------|
| 16 | PaintScale | `colrv1-paint-scale` | 17 (rejected) |
| 20 | PaintScaleAroundCenter | `colrv1-paint-scale-around-center` | 21 (rejected) |
| 22 | PaintScaleUniform | `colrv1-paint-scale-uniform` | 23 (rejected) |
| 24 | PaintScaleUniformAroundCenter | `colrv1-paint-scale-uniform-around-center` | 25 (rejected) |
| 28 | PaintRotate | `colrv1-paint-rotate` | 29 (rejected) |
| 30 | PaintRotateAroundCenter | `colrv1-paint-rotate-around-center` | 31 (rejected) |
| 32 | PaintSkew | `colrv1-paint-skew` | 33 (rejected) |
| 34 | PaintSkewAroundCenter | `colrv1-paint-skew-around-center` | 35 (rejected) |

All Var* variants emit `colrv1-paint-var-unsupported` diagnostic. Non-var variants are accepted and classified identically to their Transform equivalents.

### CPAL multi-palette

Extend `CPALV0Parser` to parse all palettes when `numPalettes > 1`:

```kotlin
data class CPALTable(
    val version: Int,
    val palettes: List<List<Int>>,   // ARGB int per entry, one list per palette
    val paletteTypes: List<Int>,      // 0=default, 1=dark, 2=light
    val paletteLabels: List<Int>,     // nameID
    val paletteEntryLabels: List<Int>?,
)
```

## GlyphScaler Integration

### Init additions

```kotlin
private val colrV1Table: COLRV1Table? = parseColrV1()
private val cpalTable: CPALTable? = parseCpalMulti()
```

`parseColrV1()` checks COLR version >= 1, delegates to `COLRV1Parser.parse()`. `parseCpalMulti()` uses `CPALV0Parser.parse()`.

Existing `parseCpal()` (single-palette `IntArray?`) preserved for COLRv0 backward compat.

### GlyphRepresentation extension

```kotlin
// in GlyphRepresentation.kt (font:scaler):
data class ColorLayersV1(
    val paintGraph: COLRV1PaintGraphEvidence,  // from font:colr
) : GlyphRepresentation
```

### scaleGlyph() flow update

After COLRv0 check, before bitmap check:

```
if colrV1Table != null && colrV1Table.paintForGlyph(glyphId) != null:
    paintGraph = colrV1Table.flattenedPaintGraph(glyphId)
    if paintGraph != null:
        return ScaledGlyph(..., representation = ColorLayersV1(paintGraph))
```

`flattenedPaintGraph()` traverses the paint tree to produce a pre-order `COLRV1PaintGraphEvidence` without resolving palette colors (that happens in the planner).

## GPU Render Pipeline

### Detection

`hasColorGlyphs()` extended to check `GlyphRepresentation.ColorLayersV1`.

### DrawText handler

Instead of `diagnostics.degrade("color_glyphs_not_yet_routed")`, calls `renderColorText()` which dispatches by representation type.

### renderColorLayersV1 per-node dispatch

| Planner node kind | GPU rendering |
|---|---|
| `colrv1-paint-solid` | `dispatchFillPath` with palette-resolved color on child glyph outline |
| `colrv1-paint-var-solid` | Same + variation alpha delta applied to color |
| `colrv1-paint-glyph` | Scale referenced glyph via `scaler.scaleGlyph()`, tessellate outline, `dispatchFillPath` |
| `colrv1-paint-colr-glyph` | Recurse into referenced glyph's paint graph |
| `colrv1-paint-linear-gradient` | Build `Shader.LinearGradient` from planner's `COLRV1GradientEvidence` (stops + geometry), `dispatchFillPath` |
| `colrv1-paint-radial-gradient` | Build `Shader.RadialGradient`, `dispatchFillPath` |
| `colrv1-paint-sweep-gradient` | Build `Shader.SweepGradient` (if available) or degrade |
| `colrv1-paint-translate` | Apply translate matrix via `canvas.concat()` |
| `colrv1-paint-transform` / `colrv1-paint-scale` / `colrv1-paint-rotate` / `colrv1-paint-skew` | Apply affine matrix via `canvas.concat()` |
| `colrv1-paint-composite-{mode}` | Save, render source, render backdrop via `canvas.saveLayer()`, restore with `paint.copy(blendMode=mode)` |
| `colrv1-paint-clipbox` | `canvas.save()`, `canvas.clipRect(bounds)`, render child, `canvas.restore()` |

### Degrade diagnostics (non-blocking)

| Condition | Diagnostic |
|-----------|-----------|
| VarSolid / VarTranslate / VarScale / VarRotate / VarSkew | `colrv1-var-paint-unsupported` |
| PaintColrGlyph cycle detected | `colrv1-colr-glyph-cycle` |
| Gradient with variable stops | `colrv1-var-gradient-unsupported` |
| Budget exceeded (depth > 8, nodes > 64) | `colrv1-budget-exceeded` |

Degrade diagnostics continue rendering other glyphs without aborting the draw call.

## ColrV1GM Migration

### Port strategy

- Single `ColrV1Gm` class with no-arg constructor → `gradient_stops_repeat` (default upstream variant)
- Fallback: when COLRv1 test font `test_glyphs-glyf_colr_1.ttf` is unavailable, draws "ABCD" at 160pt via LiberationSans
- Full upstream body: iterate codepoints with line-breaking at 4 text sizes (12/18/30/120pt) × 4 colors (black/green/red/blue)
- `minSimilarity = 0.0`

### Simplifications vs upstream

- Single category (18 → 1)
- No skew/rotate constructor params
- No variable font axes
- No `SkFontHinting`
- Fixed canvas size (1200×1200, not conditional 1700×1200 for sweep)
- No synthetic COLRv1 font construction (upstream's `withColrV1SubsetTableContent` + `colrV1SubsetColr/Cpal`)

### Reference images

Move: `original-888/colrv1_gradient_stops_repeat.png` → `reference/colrv1_gradient_stops_repeat.png`

Remaining 61 ColrV1 refs stay in `original-888/` for future per-category ports.

### ServiceLoader

One entry: `org.graphiks.kanvas.skia.gm.text.ColrV1Gm`

## Implementation Phases

| Phase | Content | Effort |
|-------|---------|--------|
| 1 | Create `font:colr`, migrate COLRv1 types from `font/glyph`, add PaintScale/Rotate/Skew parser + multi-CPAL | High |
| 2 | Wire `font:colr` into `GlyphScaler` — COLRv1 detection, `ColorLayersV1`, `flattenedPaintGraph()` | Medium |
| 3 | GPU renderer `renderColorText()` — Solid, Glyph, Transform per-node dispatch | High |
| 4 | GPU renderer gradient + composite + clipbox support | High |
| 5 | Migrate ColrV1GM | Medium |

## Risks

| Risk | Mitigation |
|------|-----------|
| Module migration breaks `font/glyph` compilation | Phase 1 ends with `./gradlew :font:glyph:compileKotlin` green before proceeding |
| PaintScale/Rotate/Skew not tested by existing parser tests | New test with synthetic COLRv1 font exercising formats 16-31 |
| GPU gradient dispatch requires `Shader.SweepGradient` not yet in Kanvas | Degrade sweep to diagnostic; implement sweep shader separately |
| `canvas.saveLayer` inside per-glyph loop may be expensive | Composite nodes are rare (only 1 category in ColrV1GM); acceptable for MVP |
| 61 remaining ColrV1 refs clutter original-888 | Documented as "future per-category ports" in GM migration plan |

## Verification

- `./gradlew :font:colr:test :font:scaler:test :font:glyph:test` — all existing tests pass
- `./gradlew :integration-tests:skia:compileTestKotlin` — ColrV1Gm compiles
- ColrV1Gm produces deterministic output (ABCD fallback when test font absent)
