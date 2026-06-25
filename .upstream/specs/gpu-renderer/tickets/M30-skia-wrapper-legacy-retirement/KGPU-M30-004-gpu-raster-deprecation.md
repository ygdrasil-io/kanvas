---
id: KGPU-M30-004
title: "gpu-raster deprecation and legacy route freeze"
status: review
milestone: M30
priority: P0
owner_area: kanvas-skia-bridge
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M30-002]
legacy_gate: gpu-raster-legacy-freeze
---

# KGPU-M30-004 - gpu-raster deprecation and legacy route freeze

## PM Note

Ce ticket gele officiellement le chemin de rendu legacy `gpu-raster` apres la
migration vers Kanvas. Le PM verra que le module `gpu-raster` est marque comme
deprecated, les tests sont migres, et la documentation reflete le changement de
route par defaut.

## Problem

After routing SkSurface through KanvasSurface (M30-002), the legacy `gpu-raster`
module remains active and could confuse routing decisions. The module must be
formally deprecated, its tests migrated, and its routes frozen to prevent new
dependencies on the legacy path.

## Scope

- Mark `:gpu-raster` module as `@Deprecated` in build configuration
- Add deprecation warnings to all public `gpu-raster` API classes
- Migrate any remaining `gpu-raster`-specific tests to the Kanvas bridge path
- Update route taxonomy documentation to mark `gpu-raster` as frozen
- Ensure the legacy flag (`useLegacyGpuRaster`) is documented as emergency-only
- Update `05-routing-policy.md` to remove `gpu-raster` from active routes

## Non-Goals

- No deletion of `gpu-raster` source code (archive only)
- No breaking changes to the `gpu-raster` module API
- No rewriting of `gpu-raster` internals
- No removal of historical evidence or benchmarks

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/tickets/M10-legacy-gpu-raster-migration/README.md`

## Design Sketch

```kotlin
// In gpu-raster build.gradle.kts or equivalent:
// Mark as deprecated, frozen for new development

// In routing policy:
// Route: gpu-raster-legacy-path -> FROZEN (emergency rollback only)
// Route: kanvas-bridge-path -> ACTIVE (default)

@Deprecated(
    message = "Use KanvasSkiaBridge path. gpu-raster is frozen.",
    replaceWith = ReplaceWith("KanvasSkiaBridge"),
    level = DeprecationLevel.WARNING,
)
class LegacyGpuRasterSurface { ... }
```

## Acceptance Criteria

- [ ] `:gpu-raster` module emits deprecation warnings on compilation
- [ ] All public `gpu-raster` API classes are annotated `@Deprecated`
- [ ] Route taxonomy updated: `gpu-raster` marked as `FROZEN`
- [ ] Legacy flag `useLegacyGpuRaster` documented as emergency-only
- [ ] No new code depends on `gpu-raster` (verified by dependency analysis)
- [ ] All existing `gpu-raster` tests pass or are migrated

## Required Evidence

- Deprecation annotation diff for `gpu-raster` classes
- Route taxonomy diff showing `gpu-raster` as FROZEN
- Dependency analysis report (no new `gpu-raster` consumers)
- Test migration transcript (tests moved to bridge path)
- Legacy flag documentation

## Fallback / Refusal Behavior

If the legacy path is activated via `useLegacyGpuRaster`, emit a
`legacy-gpu-raster-active` warning diagnostic. The legacy path is frozen and
receives no new features or bug fixes.

## Dashboard Impact

- Expected row: `gpu-renderer.m30.gpu-raster-deprecation`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlinJvm 2>&1 | grep -c "deprecated"
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Implemented. Deprecated WebGpuContext and HeadlessTarget with @Deprecated annotations; updated route taxonomy in 05-routing-policy.md marking gpu-raster-legacy-path as FROZEN and kanvas-bridge-path as ACTIVE; documented useLegacyGpuRaster as emergency-only rollback. Evidence at `reports/gpu-renderer/2026-06-25-M30-004-evidence.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M30`
- `area:kanvas-skia-bridge`
