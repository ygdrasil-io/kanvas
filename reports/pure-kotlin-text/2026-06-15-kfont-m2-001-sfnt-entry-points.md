# KFONT-M2-001 SFNT/TTC Parser Entry Points

Status: review.

## What Changed

- Added `BoundedFontBytes`, `SFNTParseRequest`, `SFNTParser`,
  `DefaultSFNTParser`, `SFNTParseResult`, and directory report value objects in
  `font/sfnt`.
- Normalized single-face SFNT and TTC collection requests through the same
  bounded request/result contract.
- Converted invalid collection indices into
  `font.collection-index-invalid` diagnostics with no selected face and no
  fallback parse of face `0`.
- Added stable unsupported-wrapper diagnostics for unknown wrappers without
  platform APIs or external parsers.
- Added checked-in `reports/pure-kotlin-text/sfnt-directory.json` with one
  Liberation Sans single TTF entry, one generated TTC face-index entry, and one
  invalid TTC index diagnostic entry.
- Remediated spec review feedback by keeping `DefaultSFNTParser`
  directory-only: it reads the selected SFNT directory and raw bounded table
  slices without delegating to `DefaultOpenTypeFaceParser` or invoking typed
  layout/color table payload parsers.

## API Review Note

The parser entry point is:

```kotlin
interface SFNTParser {
    fun parse(request: SFNTParseRequest): SFNTParseResult
}
```

`SFNTParseRequest` carries `FontSourceID`, `FontSourceKind`, display name,
`BoundedFontBytes`, requested collection index, and parser generation. The
bounded byte object validates `[byteOffset, byteOffset + byteLength)` before
the parser builds a `FontSource`, so table directory reads are limited to the
requested slice.

## Evidence

- `sfnt-directory.json` includes source ID, source kind, parser generation,
  byte bounds, container kind, requested/selected collection index, face count,
  directory table records, directory diagnostics, and container diagnostics.
- The generated TTC evidence is labeled `GeneratedFixtureFontSource` and uses
  the planned fixture ID `ttc-face-index-planned-generated`; it remains
  non-promoting.
- The invalid TTC request for collection index `3` records
  `font.collection-index-invalid`, `selectedFaceIndex=null`, and empty table
  records.
- `faceFacts` remains `null` in this ticket's result surface so the report does
  not imply typed table payload, scaler, shaping, or rendering readiness.
- Repeated report construction is compared byte-for-byte in
  `SFNTParserEntryPointTest.sfntParserUsesOneBoundedRequestForSingleSfntAndTtcDirectoryReports`.

## Red / Green Validation

Red:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*'
```

Result: failed at `:font:sfnt:compileTestKotlin` because
`SFNTDirectoryReportWriter`, `DefaultSFNTParser`, `SFNTParseRequest`,
`BoundedFontBytes`, `SFNTContainerKind`, `SFNTDirectoryReport`, and
`SFNTDirectoryReportEntry` were missing.

Green:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*'
```

Result: passed after implementation and checked-in `sfnt-directory.json`.

Final validation:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*'
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test
```

Result: all passed locally.

## Non-Claims

This is entry-point and directory evidence only. It does not implement or claim
glyph outlines, CFF charstrings, GSUB/GPOS behavior, color payload rendering,
fallback, shaping, paragraph layout, glyph artifacts, rendering, or broad SFNT
conformance. Dashboard classification remains `tracked-gap` and
`claimPromotionAllowed=false`.

## Remaining Gate

Later M2 tickets still own bounded table diagnostics beyond directory slices,
complete `cmap` coverage, table fact dumps, and malformed SFNT fixture suites.
