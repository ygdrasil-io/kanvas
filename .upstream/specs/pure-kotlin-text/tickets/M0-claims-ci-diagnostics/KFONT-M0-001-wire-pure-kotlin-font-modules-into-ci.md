---
id: "KFONT-M0-001"
title: "Wire pure Kotlin font modules into CI"
status: "done"
milestone: "M0"
priority: "P0"
owner_area: "ci"
claim_impact: "tracked-gap"
depends_on: []
legacy_gate: null
---

# KFONT-M0-001 - Wire pure Kotlin font modules into CI

## PM Note

Ce ticket rend le futur chantier font visible dans la CI avant que des claims produit puissent passer inaperçus.

## Problem

The pure Kotlin text roadmap names candidate font modules, but CI does not yet provide one auditable lane that fails when the font core, SFNT parser, scaler, text, glyph, or GPU API contracts regress. Without that lane, later tickets can add evidence dumps or support claims while the foundation is still unvalidated.

## Scope

- Add or reserve CI coverage for `:font:core:test`, `:font:sfnt:test`, `:font:scaler:test`, `:font:text:test`, `:font:glyph:test`, and `:font:gpu-api:test`.
- Keep the lane pure Kotlin; no HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig dependency may be required for pass/fail behavior.
- Define how missing candidate modules are reported until their Gradle projects exist.
- Ensure CI output names the exact font task, module path, and failure category.
- Document which later milestones are blocked while this lane is absent.

## Non-Goals

- Do not implement font parsing, shaping, scaling, or GPU text handoff.
- Do not claim support for any text rendering route.
- Do not migrate Skia-like facade APIs in this ticket.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class FontCiModule(val gradlePath: String) {
    Core(":font:core:test"),
    SFNT(":font:sfnt:test"),
    Scaler(":font:scaler:test"),
    Text(":font:text:test"),
    Glyph(":font:glyph:test"),
    GPUApi(":font:gpu-api:test"),
}

data class FontCiLane(
    val name: String = "pure-kotlin-font-foundation",
    val tasks: List<FontCiModule>,
    val missingModulePolicy: MissingModulePolicy,
)

data class FontCiDiagnostic(
    val code: String,
    val gradlePath: String,
    val blockingMilestones: Set<String>,
)
```

## Acceptance Criteria

- [x] The CI configuration invokes every listed `FontCiModule` task when the module exists.
- [x] A missing candidate module is surfaced as a reviewed `tracked-gap` diagnostic, not silently skipped.
- [x] A failing font task fails the CI lane and identifies the exact Gradle path.
- [x] The lane remains independent from platform-native or external font engines.
- [x] The milestone dashboard cannot use this ticket as support evidence for parsing, shaping, scaling, fallback, or GPU rendering.

## Required Evidence

- CI workflow diff or generated CI plan showing the six `:font:*` test tasks.
- A dry-run or local CI transcript that includes the font lane name and task list.
- Diagnostic sample for an absent candidate module, using a stable `font.ci.module-missing` or equivalent code.
- Confirmation that no native font engine task is required by the lane.

## Fallback / Refusal Behavior

- If a candidate module does not exist yet, the lane must emit a stable module-missing diagnostic and keep dependent milestones blocked as `tracked-gap`.
- CI must not fall back to `:kanvas-skia:test` alone as proof that pure Kotlin font modules are validated.

## Dashboard Impact

- Expected row: `pure-kotlin-font-foundation CI`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. This ticket only creates the validation lane that later evidence depends on.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test
```

## Status Notes

- `done`: Merged into `master` by PR #1661 (`3fb53af78`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `review`: CI lane `pure-kotlin-font-foundation` is wired in
  `.github/workflows/test.yml`, validates
  `reports/pure-kotlin-text/font-ci-lane.json`, invokes the six `:font:*`
  test tasks, and records `font.ci.module-missing` as the non-promoting
  missing-module policy.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:ci`
- `claim:tracked-gap`
