# KFONT-M6-006 - Runtime ResolvedFeatureSet Adoption

## Scope

This wave narrows the remaining `review` gate for script-specific default
feature policy:

- `BasicOpenTypeShapingEngine` now resolves per-run script policy before GSUB,
  the bounded `kern`-routed GPOS single subset, and pair-kerning gating
  instead of consulting raw `FeatureSet` entries directly.
- GPOS anchor lookup routing now uses the resolved runtime policy as well, so
  unsupported `curs`/`mark`/`mkmk` lookups no longer execute through the raw
  request-feature fallback.
- The Arabic feature-policy row now explicitly includes `curs`, matching the
  ticket requirement that Arabic defaults carry cursive and mark requirements.
- Unsupported discretionary feature requests no longer execute lookups for
  scripts whose policy does not enable them.
- Scripts without a policy row keep the legacy enable-unless-disabled fallback
  instead of silently losing default GSUB/GPOS behavior.
- `drawString` now has explicit portable OpenType regression evidence on a
  synthetic facade font that embeds the reviewed `GSUB` ligature bytes plus a
  matching `cmap`; raw `"fi"` text stays distinct from the ligature target
  glyph, closing the local OpenType-specific `drawString` non-enablement gate
  without widening shaping support claims.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineSkipsParsedGsubLookupWhenFeatureIsUnsupportedForScriptPolicy
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineSkipsGposSingleAdjustmentsWhenFeatureIsUnsupportedForScriptPolicy
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineSkipsUnsupportedCursiveLookupsForScriptPolicy
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEnginePreservesLegacyFeatureDefaultsForScriptsWithoutPolicy
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.foundation.SkFontTest.drawString\ forwards\ raw\ text\ to\ typeface\ path\ builder\ without\ implicit\ shaping
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.foundation.opentype.OpenTypeFontTest.drawString\ keeps\ portable\ OpenType\ ligature\ codepoint\ distinct\ from\ raw\ fi\ text
```

## Remaining Gates

- Per-script shaping fixture families still belong to `KFONT-M6-007`,
  `KFONT-M6-008`, and `KFONT-M6-009`.
- This wave does not promote complete scripted shaping support or attach new
  Arabic, Devanagari, Thai, or CJK positive/refusal fixture bundles.

## Non-Claims

This wave does not claim complete script shaping support, native shaper parity,
GPU evidence, paragraph layout readiness, or promotion beyond `review`.
