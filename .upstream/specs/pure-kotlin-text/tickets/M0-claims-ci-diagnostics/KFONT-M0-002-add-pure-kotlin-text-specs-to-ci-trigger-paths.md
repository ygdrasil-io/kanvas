---
id: "KFONT-M0-002"
title: "Add pure-kotlin-text specs to CI trigger paths"
status: "proposed"
milestone: "M0"
priority: "P0"
owner_area: "ci"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-001"]
legacy_gate: null
---

# KFONT-M0-002 - Add pure-kotlin-text specs to CI trigger paths

## PM Note

Ce ticket sert à livrer "Add pure-kotlin-text specs to CI trigger paths" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M0: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Claims, CI, and Diagnostics` slice until "Add pure-kotlin-text specs to CI trigger paths" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add pure-kotlin-text specs to CI trigger paths" within `ci` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.ci.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
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
data class KFontM0002Plan(
    val input: FontCiInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM0002Executor {
    fun execute(plan: KFontM0002Plan): FontCiEvidence
    fun refusal(code: String = "font.ci.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.ci.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- CI workflow diff or local equivalent command output.
- Module coverage note listing the affected `:font:*` tasks.
- Spec-only validation output when only markdown changed.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.ci.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Add pure-kotlin-text specs to CI trigger paths`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:test :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:ci`
- `claim:tracked-gap`
