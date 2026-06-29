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
- `cmap` selection prefers Unicode format 12 and 4 subtables, with legacy
  MacRoman format 6 and format 0 accepted only as lower-priority fallbacks.
- Family names, PostScript names, localized family-name iteration, advance
  widths, bounds, font metrics, and typeface-level kerning pair adjustments.
- Defensive raw SFNT table reads via `SkTypeface.copyTableData(tag)` for
  binary-backed OpenType typefaces.
- Variable-font axis enumeration through OpenType `fvar` metadata.
- Initial variable-font outline support for simple TrueType glyphs through
  OpenType `gvar` version 1.0 shared/embedded peak tuples, applied from
  `SkFontArguments.VariationPosition` on `OpenTypeTypeface.makeClone(args)`.
- Color-font metadata parsing for `CPAL` v0 palettes and `COLR` v0 base
  glyph/layer records, plus COLRv0 layered glyph rendering through
  `SkCanvas.drawString` with `SkFontArguments.Palette` palette selection
  and per-entry overrides on `OpenTypeTypeface.makeClone(args)`.
- Non-rendered COLRv1 paint graph metadata parsing for a first safe subset:
  `PaintSolid`, `PaintGlyph`, `PaintTransform`, and `PaintTranslate`.
- Non-rendered SVG-in-OpenType document-list metadata parsing for `SVG `
  table version 0. The backend can associate glyph IDs with SVG document
  bytes, but rendering still falls back to the monochrome outline path.
- Bundled Liberation TTF resources used by the current tests.
- Pure Kotlin system font discovery through `SystemFontScanner` and
  `OpenTypeSystemFontMgr`, scanning standard OS font directories and parsing
  loadable OpenType files without AWT.

The backend has no AWT or JNI dependency. It is intended as the first portable
font path for environments where JVM desktop font APIs or native Skia bindings
are unavailable.

## System Font Fallback Policy

There are two pure Kotlin font-manager modes:

- `LiberationFontMgr.Make()` is deterministic and limited to bundled
  Liberation faces.
- `OpenTypeSystemFontMgr.Create()` enumerates host system font files through
  `SystemFontScanner`, then loads only files supported by the current OpenType
  parser.

The JVM/cpu-raster `SkFontMgr.RefDefault()` extension now uses
`OpenTypeSystemFontMgr` and never calls `java.awt.GraphicsEnvironment`. The
old AWT system font manager and `RefAwtDefault()` entry point have been
removed from the font scope.

`OpenTypeSystemFontMgr` now exposes a portable fallback policy model
(`OpenTypeSystemFallbackPolicy`) that orders fallback families by generic
family, script class, locale/BCP-47 hints, and emoji preference. A host may
optionally provide a policy boundary (`OpenTypeSystemFallbackPolicyProvider`)
to adjust ordering without introducing a mandatory platform dependency.

Diagnostics are opt-in and silent by default. `CreateWithPolicy(...,
diagnosticsSink = ...)` reports ignored unreadable/malformed/unsupported font
files and planned fallback order events for troubleshooting.

The old JVM/AWT shaper entry points, `SkShaper.MakeJvmAwtTextLayout()` and
`SkShaper.MakeJavaTextLayout()`, have been removed from the font scope.
The built-in portable shaping entry points are `SkShaper.MakePrimitive()`,
`SkShaper.MakePortable()`, and `SkShaper.MakeOpenType()`. Today they resolve
to the same minimal primitive shaping behavior until a pure Kotlin complex
shaper is introduced.

The older `org.skia.foundation.awt.AwtTypeface` and
`org.skia.foundation.awt.LiberationFontMgr` font surfaces have also been
removed. Portable font code should use `OpenTypeTypeface` and
`org.skia.foundation.LiberationFontMgr.Make()`.

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
- Variable font support beyond the initial simple-glyph `gvar` subset:
  composite glyph variation deltas, `avar`, IUP edge cases, hinting, and
  phantom advance deltas are separate work.
- Color font rendering beyond COLRv0: COLRv1 draw-path integration,
  gradients, composites, reusable layer graphs, decoded CBDT/sbix bitmap
  strike rendering, and SVG-in-OpenType glyph rendering are not part of the
  current pure Kotlin backend. CBDT/CBLC, sbix, and SVG metadata parsing may
  exist without enabling those draw paths.
- Platform font fallback through native desktop APIs remains out of scope.
  System font enumeration itself is now provided by the pure Kotlin
  `OpenTypeSystemFontMgr`, limited to files the current OpenType parser can
  load.
- There is still no portable equivalent of upstream `SkFontMgr::Request` for
  variable-axis-aware fallback. Current pure Kotlin behavior uses
  `matchFamilyStyleCharacter` with policy-driven fallback ordering.
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

## Issue 927 Strategy

Issue [#927](https://github.com/ygdrasil-io/kanvas/issues/927) is the umbrella
for font work that remains intentionally outside the current portable OpenType
backend. The strategy is to keep the default font path pure Kotlin and split
large policy/rendering decisions into explicit follow-up tickets:

- Complex shaping is tracked by
  [#976](https://github.com/ygdrasil-io/kanvas/issues/976). `OpenTypeTypeface`
  remains the SFNT table reader and scaler. Full shaping belongs behind an
  explicit `SkShaper` or text-layout factory, while `SkShaper.MakePrimitive()`
  remains the stable minimal fallback.
- System fallback policy is tracked by
  [#977](https://github.com/ygdrasil-io/kanvas/issues/977). The existing
  `OpenTypeSystemFontMgr` provides pure Kotlin system font enumeration, but
  script/locale/emoji fallback ordering and any platform policy providers must
  be added without making AWT or JNI mandatory.
- Complete COLRv1 rendering is tracked by
  [#978](https://github.com/ygdrasil-io/kanvas/issues/978). COLRv1 metadata can
  be parsed safely today, but paint graph evaluation, gradients, composites,
  clip boxes, palette semantics, and GM re-enablement need a separate rendering
  project.
- HarfBuzz, FreeType, and Fontations integrations remain optional module or
  factory choices. They must not become transitive requirements of
  `kanvas-skia` portable font paths.

## Shaping Audit

The repository may integrate platform shapers outside `kanvas-skia`, but the
pure Kotlin OpenType backend must remain usable without AWT or JNI. The current
`kanvas-skia` text path for `OpenTypeTypeface` maps Unicode codepoints through
`cmap`, places glyph paths in order, and now applies legacy `kern` pair
adjustments plus `GPOS` pair-position fallback. It does not perform full
shaping.

The first pure Kotlin shaping increment is `GPOS` pair positioning lookup type
2 as a kerning fallback when a font has no legacy `kern` table. This is
directly measurable through the existing `measureTextInternal` and
`makeTextPath` paths, requires no new public API, and is tested with bundled
Liberation fonts that contain `GPOS` data.

`SkCanvas.drawString` remains intentionally simple-text in this architecture:
it uses the font path pipeline and does not implicitly run a complex shaper.
Portable complex shaping must be requested through explicit `SkShaper` or
text-layout entry points so fallback behavior stays predictable and measurable.

`GSUB` substitutions, including standard ligatures, are tracked by #976/#1048
because they need cluster mapping and a clear policy for enabled features.
Bidi, script itemization, mark positioning, cursive attachment, Indic/Arabic
shaping, HarfBuzz parity, and multi-font fallback belong outside the OpenType
typeface reader and should be tracked as dedicated `SkShaper` or text-layout
work.

The current `SkShaper` slice provides an explicit portable shaping entry point
with Unicode bidi runs, script itemization, stable original-text clusters,
text-local glyph positions, missing-glyph diagnostics, and opt-in multi-font
fallback run splitting. It also adds conservative feature controls for standard
ligatures, Arabic joining presentation forms, Devanagari pre-base vowel
reordering, script/language gating, and external mark/cursive positioning
providers.

Defaults remain conservative: no implicit ligature substitution, no implicit
complex shaping through `SkCanvas.drawString`, and no silent approximation when
mark/cursive positioning is requested without a provider. HarfBuzz-equivalent
coverage, full GSUB/GPOS table interpretation, and broad Indic/Arabic shaping
remain future depth work, but the portable API boundary and deterministic
fixture surface are now in place.

## Bitmap And SVG Color Font Plan

Issue [#926](https://github.com/ygdrasil-io/kanvas/issues/926) should stay
split by table family. Bitmap and SVG color fonts should be implemented in
generated-fixture slices, not by adding external binary fonts to the
repository. The existing `OpenTypeFontTest` table replacement helpers are
sufficient for PR-sized fixtures.

Current state:

- **CBDT/CBLC PNG**: the backend parses bounded bitmap strikes, the generated
  fixture path, supported PNG image formats, and glyph-to-PNG metadata
  selection. PNG decode, requested-size strike selection, origin placement, and
  draw-path integration remain separate follow-ups. Unsupported or malformed
  optional bitmap tables fail closed and text rendering keeps using `glyf`
  outlines.
- **sbix PNG**: the backend parses bounded strike tables, per-glyph records,
  image format tags, origins, and PNG payload metadata. This parser remains
  separate from `CBDT/CBLC` because `sbix` has a different table layout and
  placement model even when the payload is also PNG. PNG decode and rendering
  integration remain follow-ups.
- **SVG-in-OpenType**: version 0 document records are parsed as internal
  metadata and malformed optional `SVG ` tables fail closed without rejecting
  the font. The explicit current strategy is to keep SVG records as unsupported
  render metadata and preserve the monochrome outline fallback. A future SVG
  draw path must first introduce or select a pure Kotlin SVG renderer; it must
  not add AWT, JNI, or a native bridge implicitly.

Fixture and implementation plan:

- **CBDT/CBLC**: keep the generated bitmap-strike fixture that replaces two
  optional tables in a bundled TTF. The current parser proves metadata
  extraction from PNG payloads before any rendering integration.
- **sbix**: keep the separate generated fixture with a single PNG strike and
  one glyph record. Keep it independent from CBDT/CBLC because the table
  layout and origin handling are different even when the embedded image format
  is also PNG.
- **SVG-in-OpenType**: generate an `SVG ` table fixture containing one compact
  SVG document record for one glyph ID. The first parser slice now exposes
  document metadata and a draw-disabled fallback test; renderer dependency
  selection remains separate.

Implementation should be split in this order:

1. Keep CBDT/CBLC metadata parsing green against the generated PNG fixture.
2. Render the selected CBDT/CBLC PNG strike through the pure Kotlin codec path
   if `:codec:png` is available to the target module without introducing
   AWT or JNI.
3. Render the selected sbix PNG strike as a separate slice after preserving the
   current sbix metadata tests.
4. Keep SVG records as unsupported metadata and use the monochrome outline
   fallback until a dedicated pure Kotlin SVG renderer is accepted for
   `kanvas-skia`.

Fallback behavior is fixed for all bitmap/SVG color formats: missing decode
dependencies, unsupported strike formats, malformed optional color tables,
unmapped glyphs, or absent strikes must fall back to the current monochrome
outline path and must not reject an otherwise usable font. Rendering slices
should preserve the existing COLRv0/COLRv1 behavior and should add focused
malformed-table tests before enabling any new draw path.

## Follow-Up Tickets

The remaining roadmap items from issue
[#807](https://github.com/ygdrasil-io/kanvas/issues/807) were split because
they require dedicated fixtures, larger layout decisions, or format-specific
rendering work:

- [#871](https://github.com/ygdrasil-io/kanvas/issues/871): legacy and
  advanced `cmap` formats. MacRoman format 0/6 fallbacks are covered by
  generated fixtures; broader formats such as 2, 8, 10, 13, and 14 are
  deferred until a fixture or product need requires them.
- [#874](https://github.com/ygdrasil-io/kanvas/issues/874): `GPOS` pair
  positioning as the minimal shaping increment and kerning fallback.
- [#875](https://github.com/ygdrasil-io/kanvas/issues/875): apply broader
  `fvar` / `gvar` variation positions to TrueType outlines beyond the initial
  simple-glyph subset.
- [#876](https://github.com/ygdrasil-io/kanvas/issues/876): parse COLRv0 and
  CPAL metadata with a synthetic color-font fixture.
- [#877](https://github.com/ygdrasil-io/kanvas/issues/877): plan color-font
  rendering, palette overrides, COLRv1, CBDT/sbix, and SVG-in-OpenType work.
- [#976](https://github.com/ygdrasil-io/kanvas/issues/976): optional pure
  Kotlin shaping path and complex text boundary.
- [#977](https://github.com/ygdrasil-io/kanvas/issues/977): pure Kotlin system
  fallback catalog and optional platform provider boundary.
- [#978](https://github.com/ygdrasil-io/kanvas/issues/978): complete COLRv1
  paint graph rendering.

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
