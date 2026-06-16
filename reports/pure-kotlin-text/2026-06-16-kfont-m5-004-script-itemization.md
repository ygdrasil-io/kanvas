# KFONT-M5-004 Script Itemization Evidence

Date: 2026-06-16
Status: done with bounded fixture evidence; independently reviewed.

## Scope

KFONT-M5-004 adds bounded pure Kotlin script itemization evidence for the M5
Unicode foundation. It builds script runs from pinned grapheme clusters,
uses pinned Script and Script_Extensions data, maps the required matrix scripts
to OpenType script tags, and emits stable diagnostics for unsupported scripts,
ambiguous extension-only clusters, and Common/Inherited clusters whose strong
context is missing or conflicting.

## Evidence

- `ScriptExtensionsItemizer` records cluster ranges, UTF-16 ranges, code point
  ranges, selected script, OpenType script tags, extension candidates, language
  hint, reason, Unicode version, source hash, and diagnostics.
- `script-runs.json` covers Latin combining marks, Greek marks, Hebrew niqqud,
  Arabic marks, Devanagari matra, Thai tone mark, CJK variation-selector
  context, emoji ZWJ context, unsupported Georgian, ambiguous
  Script_Extensions-only ditto mark, isolated TATWEEL, and a neutral Common
  cluster between conflicting Latin/Greek strong context.
- `ScriptItemizationTest` regenerates `script-runs.json` from the checked-in
  fixture text files, trims only trailing CR/LF line endings for canonical
  source text, and compares the generated JSON byte-for-byte with the checked
  golden.
- The dump producer escapes JSON control characters and isolated UTF-16
  surrogate code units with `\\uXXXX` sequences.
- The dump asserts `text.shaping.script-unsupported` and
  `text.shaping.script-run-ambiguous`.
- `dump-evidence-index.json` registers `script-runs` as golden-gated producer
  evidence with non-claims.
- `fixture-evidence-manifest.json` keeps the broader shaping-scripts family
  fixture-gated and explicitly gates GSUB/GPOS shaping dumps, fallback runs,
  and GPU text routes on later tickets.
- Pinned Unicode source extracts and generated Unicode table goldens were
  refreshed only for the bounded script itemization inputs.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests 'org.graphiks.kanvas.text.ScriptItemizationTest.scriptExtensionsItemizerUsesPinnedDataClustersExtensionsAndStableDiagnostics'
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk ./gradlew --no-daemon :font:text:test --tests '*ScriptItem*'
rtk ./gradlew --no-daemon :font:text:test --tests '*UnicodeData*' --tests '*Grapheme*' --tests '*Bidi*'
rtk git diff --check
```

## Review

- Independent spec re-review verdict: `ACCEPT`.
- Independent code-quality re-review verdict: `Ready to merge: Yes`.

## Non-Claims

- No complete UCD, UAX #9, UAX #14, or UAX #29 support claim.
- No GSUB/GPOS shaping, default feature policy, font fallback, glyph mapping,
  paragraph layout, emoji rendering, or GPU text route support claim.
- No native/platform shaper or external engine was used as normative evidence.

## Remaining Gate

No remaining gate for bounded KFONT-M5-004 script itemization closeout.
KFONT-M5-005 remains the next M5 prerequisite for combined cluster safety
evidence before M6/M9/M11 work can progress.
