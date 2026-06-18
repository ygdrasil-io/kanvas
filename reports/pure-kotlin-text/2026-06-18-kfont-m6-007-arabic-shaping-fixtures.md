# KFONT-M6-007 - Arabic Shaping Fixture Review Wave

Date: 2026-06-18
Status: review with bounded vendored-font evidence and independent review complete.

## Scope

This wave advances `KFONT-M6-007` from `proposed` to `review` without
promoting Arabic shaping support:

- `font/text` now has focused runtime coverage for vendored
  `NotoNaskhArabic-Regular.ttf` on contextual joining forms, base-plus-mark
  positioning, and mixed-bidi single-run refusal.
- `reports/font/fixtures/expected/shaping/arabic-mixed-bidi.txt` now checks in
  the deterministic mixed-direction text input for the paragraph-owned bidi
  refusal row.
- `reports/font/fixtures/expected/shaping/arabic-shaping-report.json` now
  summarizes the bounded positive/refusal rows and the remaining Arabic gates.

## Evidence

- `ArabicShapingFixtureTest` proves that vendored `NotoNaskhArabic-Regular.ttf`
  no longer stays on raw cmap glyph IDs for the `سلام` joining-forms case.
- The same test now adds a bounded non-promotional `لا` lam-alef runtime
  check by asserting that shaping does not stay on the raw two-glyph `cmap`
  sequence, while keeping the ticket-local positive ligature gate explicit.
- The same test proves a bounded `اَ` mark-positioning case with positioned or
  zero-advance mark clusters on the vendored font.
- `arabic-mixed-bidi.txt` plus `ArabicShapingFixtureTest` prove the stable
  `text.shaping.paragraph-bidi-required` refusal for mixed Arabic/LTR text
  shaped without paragraph context.
- `arabic-shaping-report.json` records these bounded rows against fixture
  `single-ttf-noto-naskh-arabic` and keeps `lam-alef`, vendored positive
  cursive attachment, and Arabic-specific refusal fixtures/codes as explicit
  remaining gates.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ArabicShapingFixtureTest
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No Arabic shaping support promotion.
- No complete complex-shaping claim.
- No native shaper oracle claim.
- No CPU or GPU rendering evidence claim.

## Remaining Gate

- Positive vendored-font `cursive attachment` evidence remains separate from
  the bounded M6-005 reviewed cursive fixtures.
- Positive `lam-alef` evidence remains missing as a ticket-ready ligature/trace
  proof on the current ticket-local evidence wave.
- Dedicated `arabic-missing-cursive.otf` / `arabic-missing-mark.otf` fixtures,
  narrower `text.shaping.arabic-*` refusals, and ticket-local
  `shaping-plan.json` / `gsub-trace.json` / `gpos-trace.json` /
  `shaped-glyph-run.json` families remain open gates before `done`.
