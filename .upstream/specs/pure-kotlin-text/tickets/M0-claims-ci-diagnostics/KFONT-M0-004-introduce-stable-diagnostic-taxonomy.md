---
id: "KFONT-M0-004"
title: "Introduce stable diagnostic taxonomy"
status: "done"
milestone: "M0"
priority: "P0"
owner_area: "diagnostics"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-003"]
legacy_gate: ["font.native-engine-unavailable", "font.bitmap-strike-unavailable", "font.emoji-sequence-shaping-unsupported"]
---

# KFONT-M0-004 - Introduce stable diagnostic taxonomy

## PM Note

Ce ticket transforme les refus font en raisons stables, compréhensibles et suivables dans les reports.

## Problem

Current and legacy font gaps can be described with broad labels such as native engine unavailable, bitmap strike unavailable, or emoji shaping unsupported. The pure Kotlin target needs stable diagnostic namespaces before later tickets can claim support, refuse a route, or retire a legacy gate without losing traceability.

## Scope

- Define stable namespace families: `font.source.*`, `font.sfnt.*`, `font.scaler.*`, `text.shaping.*`, `text.paragraph.*`, `glyph.artifact.*`, and `text.gpu.*`.
- Map transitional GPU and renderer refusals into `text.gpu.*`, `unsupported.text.*`, or `glyph.artifact.*` as appropriate.
- Map durable legacy diagnostics from `legacy_gate` to target classifications without closing them.
- Require diagnostics to include subject, source identity when available, route, severity, and claim impact.
- Add review examples for malformed font data, dependency-gated behavior, expected unsupported behavior, and drift-only external comparisons.

## Non-Goals

- Do not implement support for any refused behavior.
- Do not remove or retire legacy gates in this ticket.
- Do not introduce generic labels such as `font missing` as accepted diagnostics.
- Do not make external engines normative oracles.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class FontClaimImpact {
    TargetSupported,
    CurrentSupported,
    TrackedGap,
    DependencyGated,
    FixtureGated,
    GPUGated,
    ExpectedUnsupported,
    DriftOnly,
}

data class FontDiagnosticCode(
    val code: String,
    val namespace: String,
    val impact: FontClaimImpact,
    val requiredFields: Set<String>,
)

data class LegacyDiagnosticMapping(
    val legacyCode: String,
    val targetCode: String,
    val classification: FontClaimImpact,
)
```

## Acceptance Criteria

- [x] Every accepted diagnostic code belongs to one of the target namespace families.
- [x] `font.native-engine-unavailable` maps to `expected-unsupported` or `drift-only`, never to a product dependency.
- [x] `font.bitmap-strike-unavailable` maps to a route-specific bitmap/color refusal, not a generic font failure.
- [x] `font.emoji-sequence-shaping-unsupported` maps to shaping or emoji route diagnostics without claiming complex shaping support.
- [x] Diagnostics are serializable and contain enough deterministic fields to appear in evidence dumps.

## Required Evidence

- `font-diagnostic-taxonomy.json` or markdown table listing code, namespace, classification, and required fields.
- Legacy mapping table for `font.native-engine-unavailable`, `font.bitmap-strike-unavailable`, and `font.emoji-sequence-shaping-unsupported`.
- Snapshot diagnostics for at least one source failure, one SFNT failure, one scaler failure, one shaping refusal, and one GPU/text route refusal.
- Dashboard sample proving generic labels are rejected.

## Fallback / Refusal Behavior

- Unknown or generic diagnostics must be classified as `tracked-gap` until mapped to a stable namespace.
- Legacy gate(s) `font.native-engine-unavailable`, `font.bitmap-strike-unavailable`, `font.emoji-sequence-shaping-unsupported` remain open until implementation evidence, diagnostics, and dashboard updates are linked.
- Silent fallback to native font behavior is not allowed.

## Dashboard Impact

- Expected row: `pure-kotlin-font diagnostic taxonomy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. The taxonomy enables future promotion but is not support evidence.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*DiagnosticTaxonomy*'
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle
```

## Status Notes

- `done`: Merged into `master` by PR #1661 (`3fb53af78`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `review`: `FontDiagnosticTaxonomy` and
  `reports/pure-kotlin-text/font-diagnostic-taxonomy.json` define accepted
  namespaces, required fields, sample diagnostics, legacy mappings, and
  rejected generic diagnostic evidence. Classification stays `tracked-gap`;
  legacy gates remain open.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:diagnostics`
- `claim:tracked-gap`
- `legacy:font.native-engine-unavailable`
- `legacy:font.bitmap-strike-unavailable`
- `legacy:font.emoji-sequence-shaping-unsupported`
