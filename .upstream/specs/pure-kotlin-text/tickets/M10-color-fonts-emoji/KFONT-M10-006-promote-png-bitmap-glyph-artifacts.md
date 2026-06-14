---
id: "KFONT-M10-006"
title: "Promote PNG bitmap glyph artifacts"
status: "proposed"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M9-002"]
legacy_gate: ["scaledemoji_rendering"]
---

# KFONT-M10-006 - Promote PNG bitmap glyph artifacts

## PM Note

Ce ticket sert à livrer "Promote PNG bitmap glyph artifacts" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M10: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Color Fonts, Bitmap Glyphs, SVG, and Emoji` slice until "Promote PNG bitmap glyph artifacts" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Promote PNG bitmap glyph artifacts" within `color` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `glyph.color.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M10 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM10006Plan(
    val input: ColorGlyphRequest,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM10006Executor {
    fun execute(plan: KFontM10006Plan): ColorGlyphPlan
    fun refusal(code: String = "glyph.color.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `glyph.color.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- Color, bitmap, SVG, or emoji route plan dump.
- Fixture manifest entry with provenance and expected route.
- Refusal diagnostics for unsupported payloads or paint graph states.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `glyph.color.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `scaledemoji_rendering` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Promote PNG bitmap glyph artifacts`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
- `legacy:scaledemoji_rendering`
