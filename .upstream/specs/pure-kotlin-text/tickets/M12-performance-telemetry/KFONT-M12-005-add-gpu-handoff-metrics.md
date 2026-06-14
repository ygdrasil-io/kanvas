---
id: "KFONT-M12-005"
title: "Add GPU handoff metrics"
status: "proposed"
milestone: "M12"
priority: "P1"
owner_area: "telemetry"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M12-001", "KFONT-M11-004", "KFONT-M11-005"]
legacy_gate: ["dftext"]
---

# KFONT-M12-005 - Add GPU handoff metrics

## PM Note

Ce ticket sert à livrer "Add GPU handoff metrics" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M12: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Performance and Telemetry` slice until "Add GPU handoff metrics" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add GPU handoff metrics" within `telemetry` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.telemetry.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M12 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM12005Plan(
    val input: TelemetrySampleInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM12005Executor {
    fun execute(plan: KFontM12005Plan): FontTelemetrySample
    fun refusal(code: String = "font.telemetry.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.telemetry.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Telemetry schema or metric dump.
- Repeated-run sample showing deterministic dimensions.
- Dashboard or trend report sample.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.telemetry.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `dftext` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Add GPU handoff metrics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M12`
- `area:telemetry`
- `claim:tracked-gap`
- `legacy:dftext`
