# KFONT-M6-008 - Devanagari Shaping Fixture Review Wave

Date: 2026-06-18
Status: review with bounded vendored-font evidence.

## Scope

This wave advances `KFONT-M6-008` from `proposed` to `review` without
promoting Devanagari or complete Indic shaping support:

- `DevanagariShapingFixtureTest` now injects a pinned Script_Extensions
  itemization route locally so the reviewed `deva` / `dev2` feature-policy
  path can classify the vendored pre-base matra case as `Deva` without
  changing the engine defaults used by existing shaping tickets.
- `DevanagariShapingFixtureTest` now proves bounded vendored-font evidence for
  pre-base matra script selection, consonant-cluster preservation, reph-like
  shaping, and mark placement on `NotoSansDevanagari-Regular.ttf`.
- `reports/font/fixtures/expected/shaping/devanagari-shaping-report.json` now
  summarizes the bounded positive rows and the remaining Devanagari gates.

## Evidence

- `BasicOpenTypeShapingEngine` now emits `Deva` on the vendored pre-base matra
  case when it is given the pinned Script_Extensions itemizer route injected by
  this test wave, proving bounded Devanagari evidence no longer relies on the
  legacy `BasicUnicodeData` script classifier.
- `DevanagariShapingFixtureTest` proves that vendored `क्षा` remains a single
  cluster spanning the original UTF-16 range, while vendored `र्क` and `कं`
  expose reordered or positioned zero-advance cluster behavior without
  claiming full syllable-plan or phase serialization.
- `devanagari-shaping-report.json` records these bounded rows against fixture
  `single-ttf-noto-sans-devanagari` and keeps syllable-plan dumps, full
  required feature-set evidence, dedicated refusal fixtures/codes, and
  ticket-local trace dumps as explicit remaining gates.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.DevanagariShapingFixtureTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No Devanagari shaping support promotion.
- No complete Indic shaping claim.
- No native shaper oracle claim.
- No CPU or GPU rendering evidence claim.

## Remaining Gate

- `indic-syllable-plan.json` or equivalent per-fixture phase/reorder evidence
  remains absent.
- The full required feature set, especially `blwf`, `half`, `pstf`, `vatu`,
  `pres`, `haln`, and `dist`, is not yet proven by reviewed ticket-local
  evidence.
- Dedicated unsupported-syllable and phase refusal fixtures/codes remain
  absent.
- Ticket-local `gsub-trace.json`, `gpos-trace.json`, `shaped-glyph-run.json`,
  and `unicode-segments.json` families remain open gates before `done`.
