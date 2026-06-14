---
id: "KFONT-M3-002"
title: "Implement TrueType IUP interpolation tests"
status: "proposed"
milestone: "M3"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M3-001"]
legacy_gate: null
---

# KFONT-M3-002 - Implement TrueType IUP interpolation tests

## PM Note

Ce ticket sert à livrer "Implement TrueType IUP interpolation tests" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M3: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `TrueType glyf Scaler` slice until "Implement TrueType IUP interpolation tests" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Implement TrueType IUP interpolation tests" within `font-scaler` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.scaler.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M3 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM3002Plan(
    val input: ScalerGlyphInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM3002Executor {
    fun execute(plan: KFontM3002Plan): ScaledGlyphEvidence
    fun refusal(code: String = "font.scaler.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.scaler.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- `glyph-outline.json`, metrics, bounds, or charstring trace dump.
- CPU path/hash expectation for supported fixtures.
- Malformed glyph refusal diagnostic.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.scaler.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Implement TrueType IUP interpolation tests`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:font-scaler`
- `claim:tracked-gap`
