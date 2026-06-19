# KFONT-M6-010 - GSUB Extension Lookup Bounded Wave

Date: 2026-06-19
Status: blocked after bounded generated-memory-font evidence landed.

## Scope

This wave takes the first actionable `KFONT-M6-010` slice without widening the
ticket beyond what the current parser/runtime can prove safely:

- `DefaultOpenTypeFaceParser` now resolves GSUB lookup type `7` extension
  subtables when they target single substitutions or ligature substitutions.
- `ExtensionLookupFixtureTest` now proves end-to-end parsing plus shaping on
  deterministic generated-memory-font fixtures for one `ccmp` single
  substitution row and one `liga` ligature row.
- `extension-lookup-report.json` records the bounded cases and the remaining
  advanced-lookup gates explicitly.

## Evidence

- The generated `A` fixture now maps through a GSUB extension lookup to glyph
  `15`, proving that bounded extension resolution feeds the existing
  single-substitution runtime path.
- The generated `fi` fixture now maps through a GSUB extension lookup to glyph
  `42`, proving that bounded extension resolution also feeds the existing
  ligature-substitution runtime path.
- No native shaper oracle, no broader chaining support, and no GPOS or
  variation/device claim is introduced by this wave.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.ExtensionLookupFixtureTest
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Non-Claims

- No complete advanced-lookup support claim.
- No GSUB chaining or reverse-chaining support claim.
- No GPOS contextual/chaining/extension support claim.
- No device or variation-adjustment support claim.
- No native shaper oracle claim.

## Remaining Gate

- GSUB extension targets beyond single and ligature substitutions remain open.
- GSUB chaining contextual substitution remains open.
- GSUB reverse chaining substitution remains open.
- GPOS contextual/chaining/extension positioning remains open.
- Device and variation-adjustment parsing, diagnostics, and trace evidence
  remain open.
- Ticket-local `gsub-trace.json`, `gpos-trace.json`, and
  `variation-adjustment-trace.json` evidence remains open before `done`.
