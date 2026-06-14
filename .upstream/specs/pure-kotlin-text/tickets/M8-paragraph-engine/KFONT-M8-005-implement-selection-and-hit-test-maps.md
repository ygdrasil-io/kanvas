---
id: "KFONT-M8-005"
title: "Implement selection and hit-test maps"
status: "proposed"
milestone: "M8"
priority: "P1"
owner_area: "paragraph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M8-002", "KFONT-M8-003"]
legacy_gate: null
---

# KFONT-M8-005 - Implement selection and hit-test maps

## PM Note

Ce ticket sert à livrer "Implement selection and hit-test maps" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M8: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Paragraph Engine` slice until "Implement selection and hit-test maps" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Implement selection and hit-test maps" within `paragraph` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `text.paragraph.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M8 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/03-paragraph-engine.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class KFontM8005Plan(
    val input: ParagraphInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM8005Executor {
    fun execute(plan: KFontM8005Plan): ParagraphLayoutResult
    fun refusal(code: String = "text.paragraph.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `text.paragraph.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- `paragraph-input.json` or `paragraph-layout.json` dump.
- Line, box, selection, hit-test, or placeholder fixture.
- Paragraph diagnostic snapshot for invalid constraints.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `text.paragraph.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Implement selection and hit-test maps`.
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
- `milestone:M8`
- `area:paragraph`
- `claim:tracked-gap`
