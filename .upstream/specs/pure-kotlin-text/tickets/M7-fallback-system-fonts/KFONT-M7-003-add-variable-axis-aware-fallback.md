---
id: "KFONT-M7-003"
title: "Add variable-axis-aware fallback"
status: "proposed"
milestone: "M7"
priority: "P1"
owner_area: "fallback"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M7-001", "KFONT-M7-002", "KFONT-M3-003", "KFONT-M4-005"]
legacy_gate: null
---

# KFONT-M7-003 - Add variable-axis-aware fallback

## PM Note

Ce ticket sert à livrer "Add variable-axis-aware fallback" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M7: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Fallback and System Fonts` slice until "Add variable-axis-aware fallback" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add variable-axis-aware fallback" within `fallback` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `text.fallback.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M7 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM7003Plan(
    val input: FallbackRequest,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM7003Executor {
    fun execute(plan: KFontM7003Plan): FallbackDecisionTrace
    fun refusal(code: String = "text.fallback.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `text.fallback.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- `font-catalog.json` or `resolved-font-runs.json` dump.
- `fallback-decision-trace.json` with selected/refused faces.
- Host-dependent or missing-glyph diagnostic snapshot.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `text.fallback.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Add variable-axis-aware fallback`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:tracked-gap`
