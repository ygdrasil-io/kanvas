---
id: "KFONT-M0-005"
title: "Harden dashboard claim classification"
status: "proposed"
milestone: "M0"
priority: "P0"
owner_area: "validation-dashboard"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-004"]
legacy_gate: ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering", "dftext", "fontations", "fontations_ft_compare", "pdf_never_embed"]
---

# KFONT-M0-005 - Harden dashboard claim classification

## PM Note

Ce ticket sert à livrer "Harden dashboard claim classification" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M0: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Claims, CI, and Diagnostics` slice until "Harden dashboard claim classification" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Harden dashboard claim classification" within `validation-dashboard` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.claim.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M0 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM0005Plan(
    val input: ClaimDashboardInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM0005Executor {
    fun execute(plan: KFontM0005Plan): ClaimDashboardReport
    fun refusal(code: String = "font.claim.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.claim.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Target dump.
- Fixture evidence.
- Stable diagnostic snapshot.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.claim.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Harden dashboard claim classification`.
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
- `area:validation-dashboard`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:dftext`
- `legacy:fontations`
- `legacy:fontations_ft_compare`
- `legacy:pdf_never_embed`
