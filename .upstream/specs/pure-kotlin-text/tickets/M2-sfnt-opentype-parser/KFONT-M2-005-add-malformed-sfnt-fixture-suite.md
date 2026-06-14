---
id: "KFONT-M2-005"
title: "Add malformed SFNT fixture suite"
status: "proposed"
milestone: "M2"
priority: "P0"
owner_area: "fixtures"
claim_impact: "fixture-gated"
depends_on: ["KFONT-M2-002", "KFONT-M2-004"]
legacy_gate: null
---

# KFONT-M2-005 - Add malformed SFNT fixture suite

## PM Note

Ce ticket sert à livrer "Add malformed SFNT fixture suite" de façon vérifiable. Pour le PM, il donne un statut clair au gap du milestone M2: tant que les preuves demandées ne sont pas là, on ne promet pas le support complet.

## Problem

The pure Kotlin text target cannot promote the `SFNT/OpenType Parser` slice until "Add malformed SFNT fixture suite" is implemented or explicitly refused with deterministic evidence. This ticket turns the roadmap item into one auditable work unit with clear ownership, diagnostics, and validation.

## Scope

- Deliver the capability described by "Add malformed SFNT fixture suite" within `fixtures` ownership.
- Use pure Kotlin normative behavior; external engines may appear only in optional drift reports.
- Emit stable `font.fixture.*` diagnostics for unsupported, malformed, or dependency-gated behavior.
- Produce deterministic dumps or fixture evidence that can be reviewed without host-specific state.
- Keep the work inside milestone M2 boundaries and update status metadata when execution starts.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not migrate or rewrite Skia-like facade APIs in this ticket.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class KFontM2005Plan(
    val input: FixtureManifestInput,
    val sourceRefs: List<SpecRef>,
    val diagnostics: MutableList<RouteDiagnostic> = mutableListOf(),
)

interface KFontM2005Executor {
    fun execute(plan: KFontM2005Plan): FixtureManifestEntry
    fun refusal(code: String = "font.fixture.unsupported"): RouteDiagnostic
}
```

## Acceptance Criteria

- [ ] The ticket capability has a reviewed implementation or a reviewed explicit refusal path.
- [ ] Relevant diagnostics use `font.fixture.*` and include enough subject data to debug the failure.
- [ ] Fixture or dump output is deterministic across repeated runs on the same inputs.
- [ ] Status metadata, milestone README, and top-level status summary are updated when the ticket moves out of `proposed`.
- [ ] Dashboard classification remains `fixture-gated` until all evidence and validation criteria are satisfied.

## Required Evidence

- Target dump.
- Fixture evidence.
- Stable diagnostic snapshot.
- Classification remains `fixture-gated` until all evidence is attached.

## Fallback / Refusal Behavior

- Unsupported paths must emit a stable `font.fixture.*` diagnostic and keep the ticket classified as `fixture-gated`.
- Silent fallback to host/platform/native font behavior is not allowed.

## Dashboard Impact

- Expected row: `Add malformed SFNT fixture suite`.
- Expected classification: `fixture-gated`.
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
- `milestone:M2`
- `area:fixtures`
- `claim:fixture-gated`
