---
id: "KFONT-M6-006"
title: "Add script-specific default feature policy"
status: "review"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-001", "KFONT-M5-004"]
legacy_gate: null
---

# KFONT-M6-006 - Add script-specific default feature policy

## PM Note

Ce ticket rend explicites les features OpenType activees par script, au lieu de copier un comportement cache d'une plateforme.

## Problem

GSUB and GPOS lookup implementations are not enough unless Kanvas knows which features are enabled for each script, language system, and user request. The target script matrix requires explicit defaults for Latin, Greek, Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK, and Emoji, plus diagnostics for unsupported or disabled features.

## Scope

- Define a `ScriptFeaturePolicy` for each required script matrix row and OpenType script tag.
- Resolve requested features, disabled features, script defaults, language-system defaults, and compatibility choices into a deterministic `ResolvedFeatureSet`.
- Serialize requested, enabled, disabled, unsupported, and defaulted features in `shaping-plan.json`.
- Add diagnostics for unsupported features, missing language systems, and features that are valid but intentionally disabled for the current route.
- Keep `SkCanvas.drawString` outside complex default feature enablement unless an explicit shaping API requests it.

## Non-Goals

- Do not implement feature lookup execution; GSUB/GPOS tickets own behavior.
- Do not add new script rows beyond the target matrix.
- Do not infer defaults from HarfBuzz, CoreText, DirectWrite, or browser shaping at runtime.
- Do not implement paragraph style inheritance or rich text spans.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class ScriptFeaturePolicy(
    val script: ScriptCode,
    val openTypeTags: List<OpenTypeScriptTag>,
    val requiredDefaults: Set<OpenTypeFeatureTag>,
    val optionalDefaults: Set<OpenTypeFeatureTag>,
    val refusalWhenMissing: Set<OpenTypeFeatureTag>,
)

class FeatureResolver(
    private val policies: RequiredScriptFeaturePolicies,
) {
    fun resolve(run: ScriptRun, tableFacts: OpenTypeLayoutFacts, request: FeatureRequestSet): ResolvedFeatureSet
}

data class ResolvedFeatureSet(
    val enabled: List<ResolvedFeature>,
    val disabled: List<ResolvedFeature>,
    val unsupported: List<FeatureDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Latin, Greek, Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK, and Emoji script rows have explicit default feature sets.
- [ ] `shaping-plan.json` records user-requested features, script defaults, enabled lookups, disabled features, unsupported features, and language-system choice.
- [ ] Arabic defaults include joining/form features and cursive/mark requirements; Devanagari defaults include required Indic phase features; CJK defaults include vertical alternates only for vertical text requests.
- [ ] Unsupported discretionary features diagnose without blocking required defaults for simple scripts.
- [ ] Simple `drawString` path records that complex shaping defaults were not implicitly enabled.

## Current Slice

- `RequiredScriptFeaturePolicies` now defines explicit policy rows for Latin, Greek, Cyrillic, Hebrew, Arabic, Devanagari, Thai, CJK, and Emoji.
- The contract-level `ResolvedFeatureSet` now serializes `requested`, `enabled`, `disabled`, `defaulted`, `unsupported`, and a deterministic language-system choice through `shaping-plan.json`.
- `feature-policy-matrix.json` is now checked in and tracked by the dump index, fixture manifest, and claim dashboard without support promotion.
- The runtime `BasicOpenTypeShapingEngine` now resolves per-run script policy before GSUB, the bounded `kern`-routed GPOS single subset, GPOS anchor lookup, and pair-kerning gating, while preserving legacy enable-unless-disabled behavior for scripts that still have no explicit policy row.
- The Arabic policy row now names `curs` explicitly so cursive attachment defaults no longer arrive only through a raw request-feature fallback.
- `SkFontTest` now adds a bounded compatibility-path guard that `drawString` forwards raw text to the typeface path builder, but this wave does not yet close the OpenType-specific `drawString` evidence gate.
- This slice does not yet prove per-script positive/refusal shaping fixture families beyond the contract layer.

## Required Evidence

- `feature-policy-matrix.json` mapping each required script family to OpenType script tags, default features, optional features, and refusal-on-missing features.
- `shaping-plan.json` fixtures for Latin ligature/kerning, Arabic forms/cursive, Devanagari Indic features, Thai marks, CJK vertical alternates, Emoji variation selectors, and unsupported discretionary feature request.
- Diagnostics asserted in tests: `text.shaping.feature-unsupported`, `text.shaping.script-unsupported`, `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`, `text.shaping.cursive-attachment-unavailable`.
- Review note documenting that external shaper defaults are drift-only references.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.feature-unsupported`, `text.shaping.feature-policy-missing`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add script-specific default feature policy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.OpenTypeLayoutEngineContractTest
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk ./gradlew --no-daemon :font:text:test
```

## Status Notes

- `proposed`: Policy ticket depends on script itemization and the M6 shaping contract.
- `review`: Contract-level feature policy rows, shaping-plan serialization, and bounded runtime GSUB/`kern`-routed-GPOS-single/GPOS-anchor/pair-kerning policy gating are implemented and freshly validated. Remaining gate: land the per-script shaping fixture families owned by `KFONT-M6-007`, `KFONT-M6-008`, and `KFONT-M6-009`, and attach OpenType-specific `drawString` non-enablement evidence before `done`.
- Move to `ready` only after the matrix row feature sets are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
