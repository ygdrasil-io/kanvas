# KFONT-M6-010 - Checked-In Extension Fixture Wave

Date: 2026-06-23
Status: blocked after partial advanced-lookup fixture pack landed.

## Scope

This wave converts the first actionable `KFONT-M6-010` slice from inline
memory-only evidence into repo-backed synthetic fixtures with reviewed
provenance:

- `gsub-extension-substitution.otf` is now checked in and proves bounded GSUB
  extension single substitution plus ligature substitution from one reviewed
  synthetic font.
- `layout-extension-cycle.otf` is now checked in and proves deterministic
  refusal when a GSUB extension lookup targets another extension lookup type
  `7`.
- `ExtensionLookupFixtureTest`, `SFNTSurfaceTest`, provenance metadata, and
  `extension-lookup-report.json` now all point at the checked-in assets rather
  than treating the slice as memory-only evidence.

## Evidence

- The checked-in extension substitution fixture shapes `A` to glyph `15` under
  `ccmp` defaults and `fi` to glyph `42` under `liga`.
- The checked-in self-targeting extension fixture emits a stable
  `font.sfnt.optional-table-malformed` refusal instead of silently falling back
  or widening support claims.
- No native shaper oracle, no chaining/reverse-chaining claim, no advanced
  GPOS claim, and no variation/device claim is introduced by this wave.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.m6ExtensionLayoutFixturesAreCheckedInWithSyntheticProvenance
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ExtensionLookupFixtureTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No complete advanced-lookup support claim.
- No GSUB chaining or reverse-chaining support claim.
- No GPOS contextual/chaining/extension support claim.
- No device or variation-adjustment support claim.
- No native shaper oracle claim.

## Remaining Gate

- The named advanced-lookup fixture family still lacks
  `gsub-chaining-context.otf`, `gsub-reverse-chaining.otf`,
  `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`,
  `gpos-extension-positioning.otf`, and `gpos-variation-device.otf`.
- GSUB chaining contextual substitution and reverse chaining substitution
  remain open.
- GPOS contextual/chaining/extension positioning remains open.
- Device and variation-adjustment parsing, diagnostics, and
  `variation-adjustment-trace.json` remain open.
