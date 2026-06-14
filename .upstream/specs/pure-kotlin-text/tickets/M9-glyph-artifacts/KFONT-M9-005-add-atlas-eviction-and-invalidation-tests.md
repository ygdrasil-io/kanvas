---
id: "KFONT-M9-005"
title: "Add atlas eviction and invalidation tests"
status: "proposed"
milestone: "M9"
priority: "P1"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M9-001", "KFONT-M9-003", "KFONT-M9-004"]
legacy_gate: ["dftext"]
---

# KFONT-M9-005 - Add atlas eviction and invalidation tests

## PM Note

Ce ticket sert à livrer "Add atlas eviction and invalidation tests" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M9: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `Glyph Artifacts, A8, SDF, Outline, and Cache` slice until "Add atlas eviction and invalidation tests" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add atlas eviction and invalidation tests" within `glyph` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `glyph.artifact.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M9 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM9005Plan(
    val input: GlyphArtifactRequest,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM9005Executor {
    fun execute(plan: KFontM9005Plan): GlyphArtifactPlan
    fun refusal(code: String = "glyph.artifact.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `glyph.artifact.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `tracked-gap` until all evidence and validation criteria are satisfied.

## Required Evidence

- `glyph-artifact-plan.json` or `glyph-atlas.json` dump.
- CPU artifact hash, bounds, and cache key preimage.
- Route refusal or stale-generation diagnostic.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `glyph.artifact.*` diagnostic and keep the ticket classified as `tracked-gap`.
- Silent fallback to host/platform/native font behavior is not allowed.
- Legacy gate(s) `dftext` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Add atlas eviction and invalidation tests`.
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
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
- `legacy:dftext`
