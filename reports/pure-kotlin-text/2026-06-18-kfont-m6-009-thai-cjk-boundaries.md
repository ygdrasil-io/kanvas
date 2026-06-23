# KFONT-M6-009 - Thai And CJK Boundary Review Wave

Date: 2026-06-18
Status: blocked with bounded vendored-font evidence; independent review complete.

## Scope

This wave lands bounded `KFONT-M6-009` evidence while the ticket remains
`blocked` on narrower Thai/CJK gates, without promoting Thai or CJK shaping
support:

- `ThaiCjkBoundaryFixtureTest` now injects a pinned Script_Extensions
  itemization route locally so the mixed `Aก้A` case can prove Thai script
  boundaries without changing the engine defaults used by existing shaping
  tickets.
- `ThaiCjkBoundaryFixtureTest` now proves bounded vendored-font evidence for
  Thai tone-mark positioning, mixed Latin/Thai script boundaries, and CJK
  kana vertical alternates on `NotoSansThai-Regular.ttf` and
  `NotoSansSC-Regular.otf`.
- `reports/font/fixtures/expected/shaping/thai-cjk-boundary-report.json` now
  records those bounded positive rows and the remaining Thai/CJK gates.

## Evidence

- `BasicOpenTypeShapingEngine` now proves the vendored `ก้` case stays a Thai
  run with positioned or zero-advance tone-mark behavior, while `Aก้A`
  remains split into `Latn` / `Thai` / `Latn` runs under the reviewed local
  Script_Extensions route.
- `BasicOpenTypeShapingEngine` now proves the vendored `ア` case switches to a
  distinct glyph ID when the optional `vert` feature is requested on Noto Sans
  SC, keeping this wave bounded to kana vertical-alternate evidence only.
- `thai-cjk-boundary-report.json` records these bounded rows against the
  vendored Thai and CJK fixtures and keeps dictionary diagnostics,
  variation-selector evidence, Han/Hangul rows, paragraph-owned ruby/line-break
  diagnostics, and ticket-local trace dumps as explicit remaining gates.

## Validation

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ThaiCjkBoundaryFixtureTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Non-Claims

- No Thai shaping support promotion.
- No CJK shaping support promotion.
- No paragraph dictionary or East Asian line-break support claim.
- No native shaper oracle claim.
- No CPU or GPU rendering evidence claim.

## Remaining Gate

- Paragraph-owned Thai dictionary diagnostics remain absent.
- Dedicated Thai refusal fixtures/codes remain absent.
- `cmap` format 14 variation-selector shaping evidence remains absent.
- Han direct-mapping and Hangul coverage/refusal rows remain absent.
- Paragraph-owned ruby and East Asian line-break diagnostics remain absent.
- Ticket-local `shaping-plan.json`, `gsub-trace.json`, `gpos-trace.json`,
  `shaped-glyph-run.json`, `cmap-map.json`, and `unicode-segments.json`
  families remain open before `done`.
