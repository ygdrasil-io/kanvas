# KFONT-M6-007 - Arabic Shaping Fixture Review Wave

Date: 2026-06-18
Status: review with bounded vendored-font evidence and independent review complete.

## Scope

This wave advances `KFONT-M6-007` from `proposed` to `review` without
promoting Arabic shaping support:

- `font/text` now has focused runtime coverage for vendored
  `NotoNaskhArabic-Regular.ttf` on contextual joining forms, base-plus-mark
  positioning, and mixed-bidi single-run paragraph-owned diagnostics.
- `reports/font/fixtures/expected/shaping/arabic-mixed-bidi.txt` now checks in
  the deterministic mixed-direction text input for the paragraph-owned bidi
  diagnostic row.
- `reports/font/fixtures/expected/shaping/arabic-shaped-glyph-run.json` now
  checks in ticket-local glyph/cluster evidence for the bounded Arabic rows
  already proven by the runtime tests.
- `reports/font/fixtures/expected/shaping/arabic-shaping-plan.json` now checks
  in ticket-local feature-policy evidence for the same bounded Arabic rows,
  including required defaults and refusal-on-missing expectations.
- `reports/font/fixtures/expected/shaping/arabic-shaping-report.json` now
  summarizes the bounded positive/diagnostic rows and the remaining Arabic gates.

## Evidence

- `ArabicShapingFixtureTest` proves that vendored `NotoNaskhArabic-Regular.ttf`
  no longer stays on the raw visual-order `cmap` glyph sequence for the `سلام`
  joining-forms case.
- The same test now keeps a bounded non-promotional `لا` lam-alef runtime check
  by asserting that both visual-order component glyph IDs change away from the
  raw `cmap` pair, without treating that divergence as ticket-local positive
  lam-alef evidence.
- The same test proves a bounded `اَ` mark-positioning case by tying the
  positioned-or-zero-advance check to the mark cluster itself on the vendored
  font.
- `arabic-shaped-glyph-run.json` now pins the shaped glyph ids, cluster ranges,
  and cluster metrics for vendored joining forms, vendored marks, bounded
  `lam-alef` runtime divergence, and the reviewed generic
  `text.shaping.gdef-required` refusal row.
- `arabic-shaping-plan.json` now pins the `Arab`/`arab` script-policy
  selection, RTL bidi level, required Arabic default features
  (`init`, `medi`, `fina`, `isol`, `rlig`, `liga`, `calt`, `mark`, `mkmk`,
  `curs`), and the refusal-on-missing expectations that still gate narrower
  Arabic refusal assets.
- The same test also proves that reviewed repo fixture
  `gpos-missing-gdef.otf` emits the stable generic
  `text.shaping.gdef-required` refusal for Arabic base+mark input instead of
  approximating mark attachment without GDEF glyph classes.
- `arabic-mixed-bidi.txt` plus `ArabicShapingFixtureTest` prove the stable
  `text.shaping.paragraph-bidi-required` diagnostic for mixed Arabic/LTR text
  shaped without paragraph context while still returning shaped output.
- `arabic-shaping-report.json` records these bounded rows against fixture
  `single-ttf-noto-naskh-arabic`, attaches the ticket-local shaped-glyph-run
  and shaping-plan goldens plus the reviewed generic missing-mark refusal row,
  and keeps explicit `lam-alef`, vendored positive cursive attachment,
  Arabic-specific refusal fixtures/codes, and ticket-local `gsub-trace` /
  `gpos-trace` dumps as explicit remaining gates.

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

- Positive `lam-alef` evidence remains missing as a ticket-ready
  feature-local/trace-backed proof on the current ticket-local evidence wave.
- Positive vendored-font `cursive attachment` evidence remains separate from
  the bounded M6-005 reviewed cursive fixtures.
- Dedicated `arabic-missing-cursive.otf` / `arabic-missing-mark.otf` fixtures,
  narrower `text.shaping.arabic-*` refusals, and ticket-local
  `gsub-trace.json` / `gpos-trace.json` families remain open gates before
  `done`.
