# KFONT-M6-007 - Arabic Runtime Trace Wave

Date: 2026-06-18
Status: review with ticket-local runtime GSUB/GPOS traces and independent review complete.

## Scope

This wave adds bounded runtime lookup-trace evidence for the Arabic rows
already under review:

- `arabic-gsub-trace.json` captures the actual GSUB lookup chain that changes
  vendored Arabic joining-form and bounded `lam-alef` rows.
- `arabic-gpos-trace.json` captures the actual GPOS mark-attachment lookup on
  vendored `NotoNaskhArabic-Regular.ttf` plus the empty-lookup
  `text.shaping.gdef-required` refusal row on `gpos-missing-gdef.otf`.
- Both trace families now take `featureOrder` from the runtime-shaped Arabic
  run instead of reconstructing it from expected lookup subsets.
- `BasicOpenTypeShapingEngine` now exposes an `internal` runtime trace path for
  tests without widening the public shaping API or promoting Arabic support.

## Evidence

- `ArabicShapingFixtureTest` now compares checked-in `arabic-gsub-trace.json`
  and `arabic-gpos-trace.json` against bounded runtime-generated evidence from
  `BasicOpenTypeShapingEngine.shapeWithRuntimeTrace(...)`.
- The GSUB golden records the bounded `init`/`fina` lookup sequence that
  changes glyph IDs for `سلام` and `لا`, while the trace metadata keeps the
  full required Arabic runtime feature order for the run.
- The GPOS golden records the bounded `mark` lookup and attachment vector for
  `اَ`, plus the empty-lookup generic `text.shaping.gdef-required` refusal row
  for the missing-GDEF fixture, and uses the runtime pre-GPOS cluster metrics
  instead of synthetic baseline values.
- The existing Arabic shaping report now references these runtime traces and no
  longer keeps ticket-local GSUB/GPOS dump families as open gates.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ArabicShapingFixtureTest
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Remaining Gate

- Positive `lam-alef` evidence is still bounded runtime divergence only.
- Positive vendored-font cursive attachment evidence is still absent.
- Dedicated `arabic-missing-cursive.otf` / `arabic-missing-mark.otf` fixtures
  and narrower `text.shaping.arabic-*` refusal codes remain open.
