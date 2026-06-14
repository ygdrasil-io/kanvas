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

Ce ticket garantit qu'un changement de spec font déclenche les validations qui protègent les claims publics.

## Problem

The pure Kotlin font specs are the source of truth for tickets, claims, diagnostics, and validation gates. If changes under `.upstream/specs/pure-kotlin-text/**` do not trigger the font CI lane, roadmap or ticket edits can weaken evidence requirements without running the checks that detect claim drift.

## Scope

- Add `.upstream/specs/pure-kotlin-text/**` to CI path filters for the pure Kotlin font lane.
- Include the ticket catalog under `.upstream/specs/pure-kotlin-text/tickets/**` in the same trigger set.
- Ensure spec-only changes run `rtk git diff --check` and the dashboard or bundle checks that classify claims.
- Keep generated evidence, archived migrations, and unrelated upstream specs out of this trigger unless they are explicitly referenced by the pure Kotlin text pack.
- Document the trigger behavior in the CI evidence for this ticket.

## Non-Goals

- Do not introduce new ticket content or roadmap scope.
- Do not require full GPU or native demo execution for spec-only changes.
- Do not make archived migration checkboxes active backlog.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class FontSpecTriggerPath(
    val glob: String,
    val requiredLanes: Set<String>,
    val reason: String,
)

val pureKotlinTextSpecTriggers = listOf(
    FontSpecTriggerPath(
        glob = ".upstream/specs/pure-kotlin-text/**",
        requiredLanes = setOf("pure-kotlin-font-foundation", "font-claim-dashboard"),
        reason = "font target specs and tickets define support gates",
    ),
)

fun classifySpecChange(path: String): TriggerDecision =
    if (pureKotlinTextSpecTriggers.any { path.matchesGlob(it.glob) }) {
        TriggerDecision.RunFontValidation
    } else {
        TriggerDecision.NoFontSpecImpact
    }
```

## Acceptance Criteria

- [ ] A diff touching `.upstream/specs/pure-kotlin-text/README.md` schedules the font validation lane.
- [ ] A diff touching any M0-M13 ticket schedules the same claim/dashboard validation used for spec changes.
- [ ] A diff touching unrelated archived specs does not accidentally become active pure Kotlin font backlog.
- [ ] CI logs include the matched glob and lane names for auditability.
- [ ] The path filter behavior is covered by a config test, dry-run output, or equivalent CI evidence.

## Required Evidence

- CI path-filter diff showing `.upstream/specs/pure-kotlin-text/**`.
- Trigger dry-run for one target spec file and one ticket file.
- Negative trigger sample for an archived-only font migration file.
- Output from `rtk git diff --check`.

## Fallback / Refusal Behavior

- If the CI provider cannot express the recursive path filter, keep the ticket `tracked-gap` and attach a manual gate requiring the same checks on every pure Kotlin text spec change.
- Do not treat a manually run Gradle command as a permanent replacement for automated path triggers.

## Dashboard Impact

- Expected row: `pure-kotlin-text spec trigger coverage`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. This only ensures claim-affecting spec changes are validated.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePerformanceTrendWarnings pipelinePmBundle
```

## Status Notes

- `proposed`: Trigger paths are specified, but no CI path-filter evidence is attached yet.
- Move to `ready` after KFONT-M0-001 defines the font CI lane name and required tasks.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:ci`
- `claim:tracked-gap`
