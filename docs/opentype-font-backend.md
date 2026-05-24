# Pure Kotlin OpenType Font Backend

This page records the current pure Kotlin font scope for issue
[#807](https://github.com/ygdrasil-io/kanvas/issues/807), building on the
font work from PR #786.

## Supported Scope

- Pure Kotlin OpenType/TrueType loading through `OpenTypeFontMgr` and
  `OpenTypeTypeface`.
- Single-face TTF files and basic TTC face selection backed by the core
  `cmap`, `head`, `hhea`, `hmtx`, `loca`, `glyf`, and `maxp` tables.
- Optional `name`, `OS/2`, `post`, legacy `kern` format 0 data, and `GPOS`
  pair positioning lookup type 2 as a kerning fallback.
- Simple and composite TrueType `glyf` outlines converted to `SkPath`.
- Unicode-to-glyph lookup through `cmap`.
- Family names, PostScript names, localized family-name iteration, advance
  widths, bounds, font metrics, and typeface-level kerning pair adjustments.
- Defensive raw SFNT table reads via `SkTypeface.copyTableData(tag)` for
  binary-backed OpenType typefaces.
- Variable-font axis enumeration through OpenType `fvar` metadata.
- Color-font metadata parsing for `CPAL` v0 palettes and `COLR` v0 base
  glyph/layer records. Rendering layered color glyphs remains out of scope.
- Bundled Liberation TTF resources used by the current tests.

The backend has no AWT or JNI dependency. It is intended as the first portable
font path for environments where JVM desktop font APIs or native Skia bindings
are unavailable.

## Explicitly Unsupported

- Full text shaping: bidi, script itemization, reordering, mark positioning,
  cursive attachment, and script-specific shaping remain out of scope for the
  OpenType backend itself.
- Advanced OpenType layout beyond pair kerning: `GSUB` ligatures/substitutions
  and full `GPOS` shaping remain unsupported. The current `GPOS` support is
  limited to pair positioning lookup type 2 as a fallback when legacy `kern`
  is absent.
- Fontations factories: `SkTypeface_Fontations.MakeFromStream` and
  `MakeFromData` remain documented stubs because they require the external
  Rust Fontations stack through UniFFI/JNI or another native bridge.
- Variable fonts beyond axis metadata: `gvar` outline deltas, `avar`, and
  applying `SkFontArguments.VariationPosition` to OpenType outlines are
  separate work.
- Color font rendering: painting `COLR` layers, palette overrides, COLRv1
  paint graphs, CBDT/sbix bitmap strikes, and SVG-in-OpenType glyphs are not
  part of the current pure Kotlin backend.
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

## Shaping Audit

The repository already has a JVM/AWT shaper in `cpu-raster`, but the pure
Kotlin OpenType backend must remain usable without AWT or JNI. The current
`kanvas-skia` text path for `OpenTypeTypeface` maps Unicode codepoints through
`cmap`, places glyph paths in order, and now applies legacy `kern` pair
adjustments plus `GPOS` pair-position fallback. It does not perform full
shaping.

The first pure Kotlin shaping increment is `GPOS` pair positioning lookup type
2 as a kerning fallback when a font has no legacy `kern` table. This is
directly measurable through the existing `measureTextInternal` and
`makeTextPath` paths, requires no new public API, and is tested with bundled
Liberation fonts that contain `GPOS` data.

`GSUB` substitutions, including standard ligatures, should be split into a
separate ticket because they need cluster mapping and a clear policy for
enabled features. Bidi, script itemization, mark positioning, cursive
attachment, Indic/Arabic shaping, HarfBuzz parity, and multi-font fallback
belong outside the OpenType typeface reader and should be tracked as dedicated
`SkShaper` or text-layout work.

## Follow-Up Tickets

The remaining roadmap items from issue
[#807](https://github.com/ygdrasil-io/kanvas/issues/807) were split because
they require dedicated fixtures, larger layout decisions, or format-specific
rendering work:

- [#871](https://github.com/ygdrasil-io/kanvas/issues/871): legacy and
  advanced `cmap` formats. Current bundled fonts all have a usable format 4
  Unicode mapping, so formats beyond 4/12 are deferred until a fixture or
  product need requires them.
- [#874](https://github.com/ygdrasil-io/kanvas/issues/874): `GPOS` pair
  positioning as the minimal shaping increment and kerning fallback.
- [#875](https://github.com/ygdrasil-io/kanvas/issues/875): apply `fvar` /
  `gvar` variation positions to TrueType outlines.
- [#876](https://github.com/ygdrasil-io/kanvas/issues/876): parse COLRv0 and
  CPAL metadata with a synthetic color-font fixture.
- [#877](https://github.com/ygdrasil-io/kanvas/issues/877): plan color-font
  rendering, palette overrides, COLRv1, CBDT/sbix, and SVG-in-OpenType work.

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
