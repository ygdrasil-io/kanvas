# KFONT-M1-003 Identity Dumps Evidence

Date: 2026-06-15
Status: review

## Scope

KFONT-M1-003 adds a small deterministic dump contract in `font/core` for the
existing font source and typeface identity reports. The contract wraps canonical
JSON outputs, records schema/order rules, bundles the source and typeface dumps,
and compares repeated dump bundles byte-for-byte.

The existing checked-in goldens remain the evidence files:

- `reports/pure-kotlin-text/font-source.json`
- `reports/pure-kotlin-text/typeface-id.json`

No new font fixture bytes were added.

## Evidence

- `FontIdentityDumpWriter.writeFontSourceJson()` emits the current
  `defaultFontSourceIdentityReport().toCanonicalJson()` output.
- `FontIdentityDumpWriter.writeTypefaceIdJson()` emits the current
  `defaultTypefaceIdentityReport().toCanonicalJson()` output.
- `FontIdentityDumpBundle` combines `font-source.json`, `typeface-id.json`, and
  `identity-dump-schema.json` while keeping `claimPromotionAllowed=false`.
- The host-dependent row remains visible through
  `system-scanned-host-dependent`, `hostDependent`, and
  `font.source.host-dependent`.

## Determinism

`FontIdentityDumpWriter.assertDeterministicDump { ... }` runs the supplied dump
producer twice and compares these UTF-8 JSON strings:

- `font-source.json`
- `typeface-id.json`
- `identity-dump-schema.json`

The result records `matches`, `firstSha256`, `secondSha256`, and
`differingFiles`. Focused tests assert that identical runs match with equal
hashes and that a changed `typeface-id.json` is reported exactly as
`typeface-id.json`.

The file writers include the checked-in terminal newline for `font-source.json`
and `typeface-id.json`, so golden comparisons are raw string comparisons rather
than `.trim()` comparisons.

## Schema

Schema version: `1`

Required fields and ordering rules are described by
`FontIdentityDumpSchema.Default`:

- source kind, face count, table tags, host-dependent marker, and diagnostics;
- typeface collection index, selected cmap, variation coordinates, palette
  identity, and diagnostics;
- stable output file order;
- sorted table tags;
- sorted variation coordinates;
- sorted palette overrides;
- diagnostics sorted by code and detail;
- byte-for-byte UTF-8 comparison includes the schema description.

## Non-Claims

This is evidence plumbing only. It does not claim rendering, shaping, glyph
scaling, glyph cache, fallback completeness, GPU, WebGPU, WGSL, native engine,
or external-engine behavior.

`claimPromotionAllowed` remains `false` in source rows, typeface rows, schema
description, and dump bundles.

## Validation

RED before implementation:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*IdentityDump*'
```

Expected failure: `compileTestKotlin` reported unresolved references for
`FontIdentityDumpWriter` and `CanonicalFontIdentityJson`.

GREEN focused validation:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*IdentityDump*'
```

Result: passed; `FontIdentityDumpTest` executed 7 focused tests successfully.

Full module validation:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test
```

Result: passed; `font/core` executed 38 tests successfully.

Review remediation:

- Spec compliance review approved the dump scope and coverage matrix update.
- Code quality review requested raw golden comparisons, defensive copies for
  schema/result lists, and stronger canonical JSON validation. The focused test
  suite now covers those cases and passes with 7 tests.

Remaining gate: KFONT-M1-003 is in review. This change does not close the M1
milestone or promote any font/text rendering claim.
