---
id: "KFONT-M6-009"
title: "Add Thai and CJK shaping boundaries"
status: "proposed"
milestone: "M6"
priority: "P1"
owner_area: "shaping"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M5-004", "KFONT-M6-004", "KFONT-M6-005", "KFONT-M6-006"]
legacy_gate: null
---

# KFONT-M6-009 - Add Thai and CJK shaping boundaries

## PM Note

Ce ticket sert à livrer "Add Thai and CJK shaping boundaries" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M6: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `OpenType Layout Shaping` slice until "Add Thai and CJK shaping boundaries" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add Thai and CJK shaping boundaries" within `shaping` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `text.shaping.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M6 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM6009Plan(
    val input: ShapingInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM6009Executor {
    fun execute(plan: KFontM6009Plan): ShapedGlyphRun
    fun refusal(code: String = "text.shaping.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `text.shaping.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `fixture-gated` until all evidence and validation criteria are satisfied.

## Required Evidence

- `shaping-plan.json` and `shaped-glyph-run.json` dump.
- GSUB/GPOS trace when lookups are involved.
- Cluster invariant or script refusal fixture.
- Classification remains `fixture-gated` until all evidence is attached.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `text.shaping.*` diagnostic and keep the ticket classified as `fixture-gated`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Add Thai and CJK shaping boundaries`.
- Expected classification: `fixture-gated`.
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
- `milestone:M6`
- `area:shaping`
- `claim:fixture-gated`
