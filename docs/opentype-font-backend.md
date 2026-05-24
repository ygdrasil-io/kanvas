# Pure Kotlin OpenType Font Backend

This page records the current pure Kotlin font scope for issue
[#807](https://github.com/ygdrasil-io/kanvas/issues/807), building on the
font work from PR #786.

## Supported Scope

- Pure Kotlin OpenType/TrueType loading through `OpenTypeFontMgr` and
  `OpenTypeTypeface`.
- Single-face TTF files and basic TTC face selection backed by the core
  `cmap`, `head`, `hhea`, `hmtx`, `loca`, `glyf`, and `maxp` tables.
- Optional `name`, `OS/2`, `post`, and legacy `kern` format 0 data.
- Simple and composite TrueType `glyf` outlines converted to `SkPath`.
- Unicode-to-glyph lookup through `cmap`.
- Family names, PostScript names, localized family-name iteration, advance
  widths, bounds, font metrics, and typeface-level kerning pair adjustments.
- Defensive raw SFNT table reads via `SkTypeface.copyTableData(tag)` for
  binary-backed OpenType typefaces.
- Bundled Liberation TTF resources used by the current tests.

The backend has no AWT or JNI dependency. It is intended as the first portable
font path for environments where JVM desktop font APIs or native Skia bindings
are unavailable.

## Explicitly Unsupported

- Full text shaping: bidi, script itemization, reordering, mark positioning,
  cursive attachment, and script-specific shaping remain out of scope for the
  OpenType backend itself.
- Advanced OpenType layout: `GSUB` ligatures/substitutions and `GPOS` pair
  positioning are not implemented yet. The current kerning support is limited
  to legacy `kern` format 0 tables.
- Fontations factories: `SkTypeface_Fontations.MakeFromStream` and
  `MakeFromData` remain documented stubs because they require the external
  Rust Fontations stack through UniFFI/JNI or another native bridge.
- Variable fonts beyond carrying arguments: `fvar` axis enumeration, `gvar`
  outline deltas, `avar`, and applying `SkFontArguments.VariationPosition`
  to OpenType outlines are separate work.
- Color fonts: `CPAL`/`COLR`, COLRv1 paint graphs, CBDT/sbix bitmap strikes,
  and SVG-in-OpenType glyphs are not part of the current pure Kotlin backend.
- System font family enumeration and platform font fallback beyond the bundled
  portable Liberation manager are out of scope.
- Pixel-perfect FreeType/HarfBuzz parity is not guaranteed. This backend reads
  the font data directly, then converts outlines into `SkPath` and relies on
  the Kanvas raster path.

## Native Boundary

The OpenType backend is intentionally pure Kotlin: no AWT, no FreeType JNI, no
HarfBuzz JNI, and no Fontations native bridge. Features that require shaping or
native font engines should remain explicit stubs or separate tickets until a
native dependency is accepted at the repository level.

APIs backed by simple SFNT tables should be implemented here when practical.
APIs requiring shaping, variable-outline interpolation, color glyph paint
graphs, or platform font fallback should stay documented as unsupported rather
than silently approximated.

## Validation

Run the focused OpenType tests:

```bash
./gradlew :kanvas-skia:test --tests org.skia.foundation.opentype.OpenTypeFontTest
```

Run the OpenType package tests:

```bash
./gradlew :kanvas-skia:test --tests 'org.skia.foundation.opentype.*'
```

Run the full `kanvas-skia` test suite before merging broader font changes:

```bash
./gradlew :kanvas-skia:test
```
