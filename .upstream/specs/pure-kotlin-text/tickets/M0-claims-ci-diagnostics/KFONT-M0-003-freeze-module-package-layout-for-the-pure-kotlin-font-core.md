---
id: "KFONT-M0-003"
title: "Freeze module/package layout for the pure Kotlin font core"
status: "done"
milestone: "M0"
priority: "P0"
owner_area: "font-architecture"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M0-001"]
legacy_gate: null
---

# KFONT-M0-003 - Freeze module/package layout for the pure Kotlin font core

## PM Note

Ce ticket fixe les frontières techniques pour éviter que les prochains tickets mélangent parsing font, shaping et renderer GPU.

## Problem

The target architecture defines package ownership and dependency direction, but the font ticket catalog still needs an auditable implementation slice that freezes the initial module/package layout. Without a frozen boundary, later SFNT, scaler, glyph artifact, or facade work can leak `Sk*` APIs into pure Kotlin modules or add a back dependency from font code to `:gpu-renderer`.

## Scope

- Define the initial Gradle module candidates and package roots for font core, SFNT parsing, scaler, text shaping, glyph artifacts, and GPU-facing API contracts.
- Encode dependency direction rules from `:kanvas-skia` adapters toward pure Kotlin font/text modules, never the reverse.
- Add boundary diagnostics for `Sk*` leakage, GPU renderer back edges, and package-root mismatches.
- Produce a reviewable module-boundary dump or report that maps package root to owner area.
- Keep exact Gradle module names aligned with KFONT-M0-001, while treating package roots as normative.

## Non-Goals

- Do not implement the modules' font behavior.
- Do not rename existing production packages outside the reviewed architecture change.
- Do not add a dependency from pure Kotlin font/text modules to `:gpu-renderer`.
- Do not make native font APIs normative.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/README.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class FontOwnerArea { Core, SFNT, Scaler, Shaping, Paragraph, Glyph, GPUApi, Facade }

data class FontPackageBoundary(
    val owner: FontOwnerArea,
    val modulePath: String,
    val packageRoot: String,
    val allowedDependencies: Set<FontOwnerArea>,
)

data class BoundaryViolation(
    val code: String,
    val sourcePackage: String,
    val forbiddenSymbol: String,
)

fun validateFontBoundaries(boundaries: List<FontPackageBoundary>): List<BoundaryViolation>
```

## Acceptance Criteria

- [x] The boundary report lists `org.graphiks.kanvas.font`, `org.graphiks.kanvas.font.scaler`, and text/glyph package roots with their owner areas.
- [x] Pure Kotlin font/text/glyph modules have no direct dependency on `:gpu-renderer`.
- [x] Pure Kotlin modules do not expose `SkFont`, `SkTypeface`, `SkPaint`, or other `Sk*` facade types in their public contracts.
- [x] Boundary violations use stable diagnostics such as `font.architecture.skia-api-leak` and `font.architecture.gpu-backedge`.
- [x] The dashboard keeps this row as `tracked-gap` until the boundary report and validation output are attached.

## Required Evidence

- `font-module-boundaries.json` or equivalent architecture report.
- Dependency graph or Gradle output proving allowed module direction.
- Diagnostic snapshot for a synthetic `Sk*` leak or forbidden GPU back edge.
- Output from the focused boundary validation command.

## Fallback / Refusal Behavior

- If exact Gradle modules are not created yet, the ticket may stay `tracked-gap` with package-root rules and a missing-module diagnostic.
- Do not allow `:kanvas-skia` compatibility needs to weaken pure Kotlin package boundaries.

## Dashboard Impact

- Expected row: `pure-kotlin-font module boundaries`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no. This is architecture evidence, not rendering support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*ModuleBoundary*'
```

## Status Notes

- `done`: Merged into `master` by PR #1661 (`3fb53af78`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. Remaining non-claims and later gates stay active.
- `review`: `reports/pure-kotlin-text/boundary-contracts.json` and
  `scripts/validate_pure_kotlin_text_boundary_contracts.py` provide package
  root and import-boundary evidence. The M0 CI lane now invokes the boundary
  validator before the six `:font:*` test tasks.
- `review-unblock`: `reports/pure-kotlin-text/2026-06-15-kfont-m0-003-boundary-diagnostics.md`
  records stable `font.architecture.*` import-boundary diagnostics. The focused
  unit snapshot asserts `font.architecture.skia-api-leak` for a synthetic
  `SkFont` leak and `font.architecture.gpu-backedge` for a pure Kotlin
  `org.graphiks.kanvas.gpu.renderer.*` backedge.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M0`
- `area:font-architecture`
- `claim:tracked-gap`
