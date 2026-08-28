# W64 — Font dependency gates

The pure Kotlin text stack has executable shaping and segmentation evidence for
the checked-in fixtures (Arabic, Devanagari, Thai/CJK, fallback boundaries,
emoji clusters and variable-fallback diagnostics). Those fixtures prove parser
and shaping behavior only; they are not a production font bundle.

The production route remains dependency-gated for fallback font selection,
complete variable-font behavior, color-font/emoji delivery and native GPU text
promotion. Host system font scans stay explicitly host-dependent and are not
used as a renderer input. No fake glyph, implicit system font, or temporary CPU
raster fallback is accepted as support.

Verification:

```text
./gradlew --offline --no-daemon :font:text:test --rerun-tasks \
  --tests '*Fallback*' \
  --tests '*Variable*' \
  --tests '*SystemFontScanTest' \
  --tests '*ArabicShapingFixtureTest' \
  --tests '*DevanagariShapingFixtureTest' \
  --tests '*ThaiCjkBoundaryFixtureTest' \
  --tests '*TextStackSurfaceTest'
```

Result: 137 targeted tests passed. This wave records dependency boundaries; it
does not promote font/emoji GMs or add a GPU route. No `gpu-renderer-scenes`
files were modified.
