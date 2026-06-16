# KFONT-M6-006 - Script-Specific Default Feature Policy Slice

## Scope

This wave implements a bounded `review` slice for script-specific default
feature policy:

- `RequiredScriptFeaturePolicies` now defines explicit rows for Latin, Greek,
  Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK, and Emoji.
- `ResolvedFeatureSet` now carries `requested`, `enabled`, `disabled`,
  `defaulted`, `unsupported`, and a deterministic language-system choice.
- `feature-policy-matrix.json` is checked in and tracked by the dump index,
  fixture manifest, and claim dashboard.

## Validation

Fresh validations for this wave:

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.OpenTypeLayoutEngineContractTest
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
```

## Remaining Gates

- Execute the resolved defaults through runtime GSUB/GPOS behavior instead of
  contract-only serialization.
- Add per-script positive/refusal shaping fixtures beyond the contract layer.
- Record explicit `drawString` compatibility non-enablement for complex shaping
  defaults before promotion beyond `review`.

## Non-Claims

This wave does not claim complete feature-policy support, complete script
shaping support, GSUB/GPOS runtime completeness, complex-script promotion,
native shaper parity, CPU oracle evidence, or GPU evidence.
