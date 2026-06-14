---
id: "KFONT-M0-003"
title: "Freeze module/package layout for the pure Kotlin font core"
status: "proposed"
milestone: "M0"
priority: "P0"
owner_area: "font-architecture"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-001"]
legacy_gate: null
---

# KFONT-M0-003 - Freeze module/package layout for the pure Kotlin font core

## PM Note

Ce ticket sert à livrer "Freeze module/package layout for the pure Kotlin font core" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M0: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Claims, CI, and Diagnostics` slice until "Freeze module/package layout for the pure Kotlin font core" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Freeze module/package layout for the pure Kotlin font core" within `font-architecture` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.architecture.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
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
data class KFontM0003Plan(
    val input: ModuleBoundaryInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM0003Executor {
    fun execute(plan: KFontM0003Plan): ModuleBoundaryReport
    fun refusal(code: String = "font.architecture.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.architecture.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Stable identity or architecture dump.
- Determinism test over the same fixture input twice.
- Diagnostic snapshot for invalid or unsupported input.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.architecture.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Freeze module/package layout for the pure Kotlin font core`.
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
- `area:font-architecture`
- `claim:tracked-gap`
