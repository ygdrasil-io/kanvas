---
id: "KFONT-M13-001"
title: "Add facade adapter inventory"
status: "proposed"
milestone: "M13"
priority: "P0"
owner_area: "skia-facade"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-004", "KFONT-M2-005", "KFONT-M6-010", "KFONT-M8-006", "KFONT-M9-006", "KFONT-M11-010", "KFONT-M12-005"]
legacy_gate: ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering", "dftext", "fontations", "fontations_ft_compare", "pdf_never_embed"]
---

# KFONT-M13-001 - Add facade adapter inventory

## PM Note

Ce ticket sert à livrer "Add facade adapter inventory" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M13: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Skia-like Facade Migration` slice until "Add facade adapter inventory" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add facade adapter inventory" within `skia-facade` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `kanvas.facade.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M13 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM13001Plan(
    val input: SkiaFacadeCall,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM13001Executor {
    fun execute(plan: KFontM13001Plan): FacadeMigrationEvidence
    fun refusal(code: String = "kanvas.facade.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `kanvas.facade.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Facade/core parity dump or migration inventory row.
- Diagnostic mapping and remaining legacy gate evidence.
- PM bundle or dashboard diff.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `kanvas.facade.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Add facade adapter inventory`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M13`
- `area:skia-facade`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:dftext`
- `legacy:fontations`
- `legacy:fontations_ft_compare`
- `legacy:pdf_never_embed`
