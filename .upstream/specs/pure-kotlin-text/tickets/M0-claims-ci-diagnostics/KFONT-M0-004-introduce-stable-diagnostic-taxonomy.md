---
id: "KFONT-M0-004"
title: "Introduce stable diagnostic taxonomy"
status: "proposed"
milestone: "M0"
priority: "P0"
owner_area: "diagnostics"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-003"]
legacy_gate: ["font.native-engine-unavailable", "font.bitmap-strike-unavailable", "font.emoji-sequence-shaping-unsupported"]
---

# KFONT-M0-004 - Introduce stable diagnostic taxonomy

## PM Note

Ce ticket sert à livrer "Introduce stable diagnostic taxonomy" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M0: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Claims, CI, and Diagnostics` slice until "Introduce stable diagnostic taxonomy" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Introduce stable diagnostic taxonomy" within `diagnostics` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.diagnostic.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M0 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM0004Plan(
    val input: DiagnosticTaxonomyInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM0004Executor {
    fun execute(plan: KFontM0004Plan): DiagnosticTaxonomyReport
    fun refusal(code: String = "font.diagnostic.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.diagnostic.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Target dump.
- Fixture evidence.
- Stable diagnostic snapshot.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.diagnostic.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `font.native-engine-unavailable`, `font.bitmap-strike-unavailable`, `font.emoji-sequence-shaping-unsupported` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Introduce stable diagnostic taxonomy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:diagnostics`
- `claim:tracked-gap`
- `legacy:font.native-engine-unavailable`
- `legacy:font.bitmap-strike-unavailable`
- `legacy:font.emoji-sequence-shaping-unsupported`
