# 2026-06-23 KFONT-M6-010 Compatible Source Intake

Date: 2026-06-23
Status: coordination evidence.

## Scope

- Vendor a checked-in offline source asset for future `KFONT-M6-010`
  advanced-lookup fixture derivation.
- Keep license provenance explicit and compatible with the fixture policy.
- Preserve the current `blocked` status until the exact named fixture family
  and trace evidence land.

## Source

- Project: `simoncozens/test-fonts`
- Asset: `FallbackPlus-Small.otf`
- Upstream raw URL:
  `https://raw.githubusercontent.com/simoncozens/test-fonts/master/FallbackPlus-Small.otf`
- Upstream blob:
  `c41f518d3f49ab112170123780a0bf8f50231ec4`
- License: Apache-2.0 via
  `reports/font/fixtures/licenses/test-fonts-Apache-2.0.txt`

## Outcome

- `reports/font/fixtures/fonts/shaping/FallbackPlus-Small.otf` is now checked
  in with deterministic hash/provenance validation.
- `reports/font/fixtures/provenance/index.json` now records the source asset as
  `fallbackplus-small-source` owned by `KFONT-M6-010`.
- `SFNTSurfaceTest` now proves the source asset is present offline and pinned
  to the expected Apache-2.0 provenance.

## Remaining Gate

- This intake does not add the named `KFONT-M6-010` fixture family:
  `gsub-chaining-context.otf`, `gsub-reverse-chaining.otf`,
  `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`,
  `gpos-extension-positioning.otf`, `gpos-variation-device.otf`.
- This intake does not add `variation-adjustment-trace.json`.
- This intake does not claim chaining lookup support, reverse-chaining support,
  advanced GPOS support, device/variation-adjustment support, or ticket
  closeout readiness.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.m6FallbackPlusSourceAssetIsCheckedInWithApacheProvenance
rtk python3 scripts/validate_font_fixture_assets.py
rtk git diff --check
```

## Non-Claims

- No complete advanced-lookup support claim.
- No exact fixture-family completion claim.
- No native shaper oracle claim.
