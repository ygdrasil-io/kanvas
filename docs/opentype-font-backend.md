# Pure Kotlin OpenType Font Backend

This page records the Phase 0/1 scope for issue
[#807](https://github.com/ygdrasil-io/kanvas/issues/807), building on the
font work from PR #786.

## Supported Scope

- Pure Kotlin OpenType/TrueType loading through `OpenTypeFontMgr` and
  `OpenTypeTypeface`.
- Single-face TTF files backed by the core `cmap`, `head`, `hhea`, `hmtx`,
  `loca`, `glyf`, and `maxp` tables, with optional `name` and `OS/2` data.
- Simple TrueType `glyf` outlines converted to `SkPath`.
- Unicode-to-glyph lookup through `cmap`.
- Family names, advance widths, bounds, and font metrics.
- Bundled Liberation TTF resources used by the current tests.

The backend has no AWT or JNI dependency. It is intended as the first portable
font path for environments where JVM desktop font APIs or native Skia bindings
are unavailable.

## Explicitly Unsupported

- Full text shaping, bidi shaping, ligatures, and script-specific shaping.
- Kerning when the font path does not provide it.
- Variable font axes or variation deltas.
- Color fonts and color glyph tables.
- System font family enumeration and platform font fallback.
- Advanced OpenType layout tables such as `GSUB`, `GPOS`, and complex `kern`
  behavior.

## Validation

Run the focused OpenType tests:

```bash
./gradlew :kanvas-skia:test --tests org.skia.foundation.opentype.OpenTypeFontTest
```

Run the full `kanvas-skia` test suite before merging broader font changes:

```bash
./gradlew :kanvas-skia:test
```
