# KFONT-M6 - Named Fixture Resource Seed Wave

Date: 2026-06-23
Status: coordination evidence; checked-in resource wave only.

## Scope

This wave lands the still-missing named fixture resources for the blocked M6
complex-shaping and advanced-lookup tickets without promoting any runtime or
support claim:

- `KFONT-M6-007`: checked-in Arabic subset/refusal seed fonts for joining
  forms, `lam-alef`, marks/cursive, missing cursive, and missing mark rows.
- `KFONT-M6-008`: checked-in Devanagari subset/refusal seed fonts for
  consonant clusters, reph, pre-base matra, below-base coverage, mark
  placement, and unsupported syllable rows.
- `KFONT-M6-009`: checked-in Thai/CJK subset/refusal seed fonts plus
  `thai-latin-mixed.txt` for Thai base/tone marks, Han UVS (`cmap` format 14),
  kana vertical alternates, missing vertical alternates, and Hangul direct-map
  coverage.
- `KFONT-M6-010`: checked-in named advanced-lookup seed fonts plus
  `variation-adjustment-trace.json` as an explicit resource-only stub.

## Evidence

- `SFNTSurfaceTest.m6RemainingFixtureResourceWaveIsCheckedInWithReviewedProvenance`
  now has repo-local files for every still-missing M6 resource name.
- `reports/font/fixtures/provenance/index.json` now records grouped reviewed
  provenance entries for:
  - `arabic-ticket-local-fixture-wave`
  - `devanagari-ticket-local-fixture-wave`
  - `thai-cjk-ticket-local-fixture-wave`
  - `advanced-lookup-ticket-local-fixture-wave`
- The checked-in CJK seed wave keeps the useful source facts explicit:
  `cjk-han-variation-selector.otf` preserves `cmap` format 14 from
  `NotoSansSC-Regular.otf`, `cjk-kana-vertical.otf` keeps `GSUB`, and
  `cjk-missing-vertical-alt.otf` deliberately drops `GSUB`.
- `gpos-variation-device.otf` is a bounded variable-font subset seed from
  `RobotoFlex-Variable.ttf`; `variation-adjustment-trace.json` stays
  resource-only and explicitly does not claim runtime variation/device support.

## Validation

```bash
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.m6RemainingFixtureResourceWaveIsCheckedInWithReviewedProvenance
rtk python3 scripts/validate_font_fixture_assets.py
rtk git diff --check
```

## Non-Claims

- No complete Arabic, Devanagari, Thai, CJK, or advanced-lookup support claim.
- No runtime/parser closeout claim for chaining, reverse-chaining,
  contextual/chaining/extension GPOS, or variation/device adjustments.
- No ticket-local trace-family promotion claim from the seed resources alone.
- No native shaper oracle claim.

## Remaining Gate

The named resources are now present in-repo, but the owning tickets remain
`blocked` until their runtime assertions, ticket-local trace dumps, refusal
diagnostics, and bounded non-claims are refreshed against these checked-in
resources.
