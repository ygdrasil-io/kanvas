---
id: "KFONT-M0-005"
title: "Harden dashboard claim classification"
status: "review"
milestone: "M0"
priority: "P0"
owner_area: "validation-dashboard"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-004"]
legacy_gate: ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering", "dftext", "fontations", "fontations_ft_compare", "pdf_never_embed"]
---

# KFONT-M0-005 - Harden dashboard claim classification

## PM Note

Ce ticket empêche le dashboard de transformer un début de preuve en promesse produit trop large.

## Problem

The pure Kotlin font target separates many support surfaces: outline paths, simple Latin atlas text, complex shaping, fallback, emoji/color, SDF, LCD, GPU handoff, and external drift. The dashboard must reject generic success labels and classify each row by the real blocker. Otherwise legacy gates such as emoji, SDF, and Fontations comparisons can look like product support before evidence exists.

## Scope

- Add dashboard rules for `target-supported`, `current-supported`, `tracked-gap`, `DependencyGated`, `fixture-gated`, `GPU-gated`, `expected-unsupported`, and `drift-only`.
- Split text/font dashboard rows into at least `outline/path`, `simple-latin atlas`, `complex shaping`, `fallback`, `emoji/color`, `SDF`, and `LCD`.
- Require evidence links for fixture provenance, dumps, CPU oracle artifacts, GPU artifacts when claimed, route diagnostics, and refusal diagnostics.
- Reject generic labels such as `font missing`, `text works`, or `emoji supported` without route-specific proof.
- Preserve all `legacy_gate` rows until the new evidence bundle names the replacement support or refusal scope.

## Non-Goals

- Do not close any legacy gate in this ticket.
- Do not add new rendering support, shaping support, or GPU support.
- Do not treat external drift reports as normative support evidence.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class FontEvidenceSet(
    val fixtureProvenance: String?,
    val parserDump: String?,
    val glyphDump: String?,
    val cpuOracle: String?,
    val gpuArtifact: String?,
    val routeDiagnostics: List<String>,
)

data class FontDashboardClaimRule(
    val row: String,
    val requestedClassification: FontClaimImpact,
    val requiredEvidence: Set<EvidenceKind>,
)

fun classifyFontClaim(rule: FontDashboardClaimRule, evidence: FontEvidenceSet): FontClaimImpact
```

## Acceptance Criteria

- [x] Rows without fixture provenance and deterministic dumps cannot become `target-supported`.
- [x] CPU-only evidence cannot imply GPU renderer support; those rows stay `GPU-gated` when GPU is claimed without GPU artifacts.
- [x] External FreeType, Fontations, or HarfBuzz comparisons classify as `drift-only` unless Kanvas-owned normative evidence exists.
- [x] Legacy rows for `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, and `pdf_never_embed` remain visible.
- [x] `pipelinePmBundle` or the dashboard gate fails on generic text/font labels that do not name route and evidence.

## Required Evidence

- Dashboard classification report with one row for each claim taxonomy value.
- Negative dashboard fixture showing rejection of a generic `font missing` or `emoji supported` label.
- Evidence-link sample for a CPU-only row and a GPU-gated row.
- Legacy gate mapping table for all gates listed in the front matter.

## Fallback / Refusal Behavior

- Rows missing required evidence must downgrade to `tracked-gap`, `fixture-gated`, `DependencyGated`, `GPU-gated`, `expected-unsupported`, or `drift-only` according to the missing blocker.
- Legacy gate(s) `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `pure-kotlin-font claim classification`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. This ticket controls classification, not feature support.

## Validation

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py
rtk python3 scripts/validate_pure_kotlin_text_ci.py
rtk ./gradlew --no-daemon validatePureKotlinTextClaimDashboard
rtk git diff --check
```

## Status Notes

- `proposed`: Dashboard rules are specified, but no report or negative fixture is attached yet.
- `review`: `font-claim-dashboard.json` and
  `validate_pure_kotlin_text_claim_dashboard.py` enforce stable
  classifications, negative generic-label refusals, GPU artifact gating,
  legacy gate visibility, and Gradle dashboard/PM bundle wiring. This is
  validation infrastructure only; no legacy gate is closed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:validation-dashboard`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:dftext`
- `legacy:fontations`
- `legacy:fontations_ft_compare`
- `legacy:pdf_never_embed`
