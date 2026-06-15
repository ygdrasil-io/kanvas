# KFONT-M2-003 CMap Format Coverage

Date: 2026-06-15
Status: implemented and independently reviewed

## Scope

This checkpoint is parser-only work in `:font:sfnt`. It completes deterministic
`cmap` evidence for formats 12, 4, 14, 6, and 0, plus stable refusal evidence
for unsupported format 13 and no-usable-Unicode-`cmap` selection.

## Evidence

- `OpenTypeCMapTableParser` parses generated fixtures for formats 0, 4, 6, 12,
  and 14.
- `CMapTable.lookupGlyphId(codePoint, variationSelector: Int? = null)` returns
  stable glyph ID `0` for missing code points while preserving existing
  one-argument call sites.
- Format 14 default UVS ranges preserve base mapping semantics; non-default UVS
  mappings return the explicit glyph ID for `(codePoint, variationSelector)`.
- Format 12 wins over format 4 and legacy 6/0 fallback subtables; format 4 also
  wins over legacy 6/0 when no usable format 12 subtable is present.
- Unsupported format 13 emits `font.sfnt.cmap-format-unsupported` with format,
  platform ID, encoding ID, and subtable offset facts.
- A no-usable-Unicode-`cmap` case emits `font.sfnt.cmap-unusable`.
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json` includes the
  `font.sfnt.cmap-format-unsupported` and `font.sfnt.cmap-unusable` taxonomy
  rows plus the `sfnt-cmap-refusal` sample diagnostic.
- `CMapGlyphMapper` preserves the existing shaping boundary contract by
  translating parser `.notdef` glyph ID `0` back to `null` for shaping
  fallback and missing-glyph diagnostics.
- `reports/pure-kotlin-text/cmap-map.json` records selected subtable facts,
  platform/encoding IDs, mapped ranges or compact facts, missing-codepoint
  behavior, variation selector facts, source face identities, unsupported-format
  diagnostics, and `claimPromotionAllowed=false`.
- Independent spec re-review verdict: `ACCEPT`.
- Independent code-quality re-review verdict: `Ready to merge: Yes`.

## Validations

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParserReportsUnsupportedAndUnusableCMapDiagnostics' --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParserPrefersFormat4OverLegacyFallbackSubtables' --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapMapReportCoversKfontM2CMapEvidence'
rtk ./gradlew --no-daemon :font:text:test --tests 'org.graphiks.kanvas.text.TextStackSurfaceTest.cmapGlyphMapperUsesSfntCMapLookupForBasicShapingAndTypefaceRouting'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapTableParser*'
rtk ./gradlew --no-daemon :font:sfnt:test --tests 'org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.cmapMapReportCoversKfontM2CMapEvidence'
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*CMap*'
rtk ./gradlew --no-daemon :font:text:test
```

Expected final hygiene:

```bash
rtk git diff --check
```

## Non-Claims

- No shaping, GSUB/GPOS, bidi, Unicode segmentation, fallback runs, paragraph
  layout, scaler, glyph outline, rendering, color glyph, native font-engine, or
  GPU text-route support is claimed.
- No HarfBuzz, FreeType, Fontations, platform shaper, browser, or native font
  API is used as a normative oracle.
- Successful `cmap` lookup is parser evidence only; it does not prove glyph
  outline availability or text rendering support.

## Remaining Gates

- Format 13 remains fixture-gated/refused until reviewed product fixtures prove
  a many-to-one mapping need.
- KFONT-M2-004 table fact dumps and KFONT-M2-005 malformed SFNT fixture suite
  remain separate tickets and are not marked ready or done by this checkpoint.
