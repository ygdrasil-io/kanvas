# KFONT-M1-002 Typeface ID Evidence

Date: 2026-06-15
Status: implemented, pending review.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/KFONT-M1-002-complete-typefaceid-glyph-affecting-identity.md`

## Scope

This slice adds a deterministic `TypefaceID` identity preimage contract and
checked-in evidence only. It does not parse SFNT bytes, scale glyphs, shape
text, build glyph artifacts, implement fallback coverage, or promote a GPU
route.

## Files

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/TypefaceIdentityTest.kt`
- `reports/pure-kotlin-text/typeface-id.json`
- `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/KFONT-M1-002-complete-typefaceid-glyph-affecting-identity.md`
- `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`

## Evidence

- `TypefaceIdentityPreimage` records `FontSourceID`, collection index,
  PostScript name, family/style metadata, outline format, selected Unicode
  `cmap`, scaler mode, variation coordinates, palette selection, fallback
  catalog generation, table tags, and stable diagnostics.
- `TypefaceID` is derived from the canonical preimage with a namespace distinct
  from `FontSourceID`: `kanvas-typeface-id-v1`.
- Variation coordinates are sorted by axis tag, table tags are sorted and
  deduplicated, palette overrides are sorted and deduplicated, and `-0.0`
  variation coordinates normalize to `0.0` for equality, hashes, JSON, and IDs.
- Diagnostic-only rows do not derive anonymous typeface IDs. The report records
  `font.collection-index-invalid`, `font.sfnt.cmap-unusable`, and
  `font.sfnt.identity-facts-incomplete`.
- `defaultTypefaceIdentityReport()` emits `typeface-id.json` rows for a
  single-face TTF fixture-equivalent, a TTC face-index variant, a variation
  axis change, a palette change, invalid collection index, and no usable
  Unicode `cmap`.
- The report JSON contains `claimPromotionAllowed=false`, `legacyGate=typeface`,
  and `gateStatus=open`.

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*Typeface*'
```

Result: failed as expected at `:font:core:compileTestKotlin` because
`TypefaceVariationCoordinate`, `TypefacePaletteSelection`,
`TypefaceIdentityPreimage`, `TypefaceOutlineFormat`, `TypefaceCMapSelection`,
`TypefaceScalerMode`, `typefaceIdentityPreimage`, and
`defaultTypefaceIdentityReport` were not yet exposed.

Green focused result:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*Typeface*'
```

Result: passed; `TypefaceIdentityTest` executed 6 focused tests successfully.

Full module result:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test
```

Result: passed; `font/core` executed 31 tests successfully.

Review result:

- Spec compliance review: approved after replacing the `cmap` diagnostic with
  `font.sfnt.cmap-unusable`.
- Code quality review: approved after adding the `-0.0` variation-coordinate
  normalization regression test.

## Non-Claims

- No rendering support is claimed.
- No shaping, fallback completeness, glyph cache, native engine, or GPU route is
  claimed.
- No Ganesh, Graphite, SkSL, WGSL/WebGPU, HarfBuzz, FreeType, Fontations, AWT,
  JNI, platform shaper, or native dependency is introduced.

## Remaining Gate

The legacy gate `typeface` remains open. It can only be retired by later
facade/core migration evidence that links this identity contract to scoped
behavior, tests, diagnostics, fixture provenance, and dashboard updates.
