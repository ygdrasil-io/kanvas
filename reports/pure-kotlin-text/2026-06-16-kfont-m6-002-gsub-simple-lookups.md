# KFONT-M6-002 - GSUB Single/Multiple/Ligature Lookup Slice

## Scope

This wave implements a bounded `review` slice for simple GSUB support:

- `font/sfnt` now parses LookupType 1 single substitution, LookupType 2
  multiple substitution, and LookupType 4 ligature substitution into
  `OpenTypeLayoutTables.gsub`.
- `font/text` now applies those parsed lookups during basic shaping while
  preserving cluster ranges for one-to-one, one-to-many, and many-to-one
  substitutions.
- Explicit feature disable remains supported for this slice when a request sets
  `FeatureSet.values[tag] == 0`, including `liga=0`.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserExposesParsedGsubSingleMultipleAndLigatureLookupsInLayout
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesParsedGsubSingleMultipleAndLigatureLookups --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineRespectsDisabledParsedGsubLigatureFeature
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest
```

## Remaining Gates

- Promote `gsub-trace.json` and `shaped-glyph-run.json` at the
  `OpenTypeLayoutEngineContract` layer.
- Add malformed/refusal GSUB fixture coverage with stable diagnostics instead
  of synthetic surface-only parser fixtures.
- Prove explicit `ShapingPlan`-driven feature ordering and keep script-policy
  ownership with `KFONT-M6-006`.

## Non-Claims

This wave does not claim complete GSUB support, contextual lookups, full
default feature policy, Greek/Cyrillic/Hebrew readiness promotion, complex
script shaping, native shaper parity, CPU oracle evidence, or GPU evidence.
