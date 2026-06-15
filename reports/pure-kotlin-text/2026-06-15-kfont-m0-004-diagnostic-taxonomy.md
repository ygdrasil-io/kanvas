# KFONT-M0-004 Diagnostic Taxonomy Evidence

Date: 2026-06-15
Status: implemented
Classification: tracked-gap
Claim promotion allowed: false

## Scope

This slice adds a stable pure Kotlin font diagnostic taxonomy contract in
`font/core` and the checked-in canonical artifact
`reports/pure-kotlin-text/font-diagnostic-taxonomy.json`.

The taxonomy accepts these namespace families:

- `font.source.*`
- `font.sfnt.*`
- `font.scaler.*`
- `text.shaping.*`
- `text.paragraph.*`
- `glyph.artifact.*`
- `text.gpu.*`
- `unsupported.text.*`

## Evidence

- `FontDiagnosticCode` records code, namespace, route, severity, claim impact,
  required fields, and `claimPromotionAllowed=false`.
- `FontDiagnosticTaxonomy` rejects generic or unknown labels as `tracked-gap`.
- `font-diagnostic-taxonomy.json` includes sample diagnostics for source, SFNT,
  scaler, shaping, and GPU/text route refusal cases.
- `font missing` appears only as a rejected dashboard sample with reason
  `generic-or-unknown-diagnostic`.
- Legacy gates remain open and map as follows:
  - `font.native-engine-unavailable` to
    `font.source.native-engine-request-unsupported`
    (`expected-unsupported`)
  - `font.bitmap-strike-unavailable` to
    `glyph.artifact.bitmap-strike-unavailable` (`tracked-gap`)
  - `font.emoji-sequence-shaping-unsupported` to
    `text.shaping.emoji-sequence-unsupported` (`tracked-gap`)

## Validation

RED:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
```

Result: failed as expected at `:font:core:compileTestKotlin` because
`defaultFontDiagnosticTaxonomy`, `FontDiagnosticClaimImpact`,
`FontDiagnosticCode`, `LegacyFontDiagnosticMapping`, and
`FontDiagnosticTaxonomyWriter` were not implemented yet.

GREEN:

```bash
rtk ./gradlew --no-daemon --rerun-tasks :font:core:test --tests '*DiagnosticTaxonomy*'
```

Result: passed. Six `FontDiagnosticTaxonomyTest` tests passed.

## Remaining Gates

This is taxonomy and evidence plumbing only. It does not implement SFNT parsing
support, scaler support, shaping support, paragraph support, glyph artifact
support, renderer route support, GPU route support, fixture coverage, CPU oracle
evidence, or GPU evidence. Legacy gates remain open until later implementation
evidence and dashboard updates retire them explicitly.
