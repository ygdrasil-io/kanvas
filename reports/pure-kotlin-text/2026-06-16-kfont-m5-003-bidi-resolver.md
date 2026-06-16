# KFONT-M5-003 Bidi Resolver

Date: 2026-06-16
Status: done

## Scope

KFONT-M5-003 replaces the default basic bidi resolver with a bounded
run-level resolver for the M5 fixture matrix. It emits stable logical ranges,
embedding levels, run direction, paragraph direction, source controls, trace
facts, and diagnostics from Kanvas-owned code and fixtures.

The resolver remains scoped to shaping handoff facts. Paragraph visual line
ordering stays owned by M8, and shaping substitutions/positioning stay owned
by M6.

## Evidence

- `DefaultBidiResolver` resolves mixed Latin/Hebrew, Arabic plus Arabic-number
  and neutral punctuation, isolate controls, unbalanced controls, and
  explicit embedding/override controls plus single-run paragraph-required
  fixtures.
- `BasicBidiResolver()` now delegates to `DefaultBidiResolver` by default,
  while `BasicBidiResolver(UnicodeData)` keeps the previous bounded legacy
  behavior for explicit compatibility callers.
- The default `BasicOpenTypeShapingEngine` path propagates
  `text.shaping.paragraph-bidi-required` from detailed bidi resolution instead
  of hiding the paragraph-level ordering gate behind basic run grouping.
- `bidi-runs.json` records Unicode version, input hashes, grapheme cluster
  references from KFONT-M5-002, resolved bidi classes, embedding levels,
  paragraph direction, source controls, trace rule IDs, diagnostics, and
  explicit non-claims.
- Fixtures are checked in under `reports/font/fixtures/expected/unicode/`:
  `bidi-hebrew-latin.txt`, `bidi-arabic-number-neutral.txt`,
  `bidi-isolate-controls.txt`, `bidi-embedding-override-controls.txt`,
  `bidi-unbalanced-controls.txt`, and
  `bidi-single-run-needs-paragraph.txt`.
- `BidiSegmentationTest` asserts `text.shaping.unicode-data-version-mismatch`,
  `text.shaping.paragraph-bidi-required`, and
  `text.unicode.bidi-control-unbalanced`.
- Regression tests assert malformed UTF-16 and split surrogate text ranges
  return stable `text.unicode.invalid-scalar` diagnostics without throwing.
- Regression tests assert mismatched embedding/isolate closers remain
  unbalanced through a typed expected-closer stack.

## Validation

```bash
rtk ./gradlew --no-daemon :font:text:test --tests '*Bidi*'
rtk ./gradlew --no-daemon :font:text:test
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk git diff --check
```

## Review

- Independent spec review verdict: `ACCEPT`.
- Independent code-quality review verdict: initial `REJECT` for malformed
  UTF-16 handling and mixed embedding/isolate closer handling; remediation
  added targeted regression tests and was re-reviewed as `ACCEPT`.

## Non-Claims

- No complete UAX #9 conformance claim.
- No paired bracket resolution claim; paired bracket facts are not available in
  the current generated `UnicodeDataSet` evidence.
- No paragraph visual line ordering, line breaking, ellipsis, hit testing, or
  selection claim.
- No Arabic joining, Hebrew mark shaping, GSUB, GPOS, fallback, paragraph, or
  GPU text route promotion.
- No native/platform text engine is used as a normative oracle.

## Remaining Gates

None for KFONT-M5-003 closeout. This remains bounded M5 fixture evidence only;
broader bidi conformance, full paragraph ordering, script itemization, shaping,
and GPU text support remain gated on later tickets.
