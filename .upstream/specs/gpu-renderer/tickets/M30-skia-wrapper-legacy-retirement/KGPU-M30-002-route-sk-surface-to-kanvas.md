---
id: KGPU-M30-002
title: "Route SkSurface to KanvasSurface — replace gpu-raster path"
status: done
milestone: M30
priority: P0
owner_area: kanvas-skia-bridge
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M30-001]
legacy_gate: gpu-raster-legacy-path
---

# KGPU-M30-002 - Route SkSurface to KanvasSurface — replace gpu-raster path

## PM Note

Ce ticket remplace le chemin de rendu `gpu-raster` par le pont `KanvasSkiaBridge`
dans les surfaces Skia. Apres ce ticket, les `SkSurface` utilisent le pipeline
GPU Kanvas natif au lieu du chemin legacy `gpu-raster`, sans changement visible
pour le code appelant.

## Problem

`SkSurface` and `GPUSurface` currently route rendering through the legacy
`gpu-raster` code path. The `KanvasSkiaBridge` exists (M30-001) but is not wired
as the rendering backend. The legacy path must be replaced to complete the
migration.

## Scope

- Wire `KanvasSkiaBridge` as the render backend for `SkSurface`/`GPUSurface`
- Route `SkSurface.flush()` through `KanvasSurface.flush()`
- Remove or gate the legacy `gpu-raster` rendering path behind a flag
- Ensure existing scene tests produce identical visual output
- Emit `route-migrated-to-kanvas` diagnostic on first activation

## Non-Goals

- No removal of `gpu-raster` module code (KGPU-M30-004)
- No new rendering features or draw families
- No performance optimization of the bridge path
- No SkSurface API surface changes

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/tickets/M10-legacy-gpu-raster-migration/README.md`
- `.upstream/specs/gpu-renderer/tickets/M24-gpu-native-rendering/README.md`

## Design Sketch

```kotlin
// In GPUSurface or equivalent:
class KanvasSkiaSurface(width: Int, height: Int) {
    private val kanvasSurface = KanvasSurface(width, height, backend)
    private val kanvasCanvas = KanvasCanvas(kanvasSurface)
    private val bridge = KanvasSkiaBridge(kanvasCanvas)

    fun getCanvas(): SkCanvas = bridge.asSkCanvas()

    fun flush() {
        kanvasSurface.flush()
    }
}
```

## Acceptance Criteria

- [ ] `SkSurface.flush()` routes through `KanvasSurface.flush()` when bridge is active
- [ ] All existing M0-M28 scene tests produce identical output through the bridge
- [ ] Legacy `gpu-raster` path is gated behind `useLegacyGpuRaster` flag (default false)
- [ ] First activation emits `route-migrated-to-kanvas` diagnostic
- [ ] Visual regression diff confirms pixel-identical output for all scene tests

## Required Evidence

- Route migration transcript showing `SkSurface.flush()` → `KanvasSurface.flush()` call chain
- Scene test output comparison: old gpu-raster path vs new Kanvas bridge path
- Legacy gate flag configuration dump
- Diagnostic transcript showing `route-migrated-to-kanvas` on first activation

## Fallback / Refusal Behavior

If the bridge fails, the system emits `kanvas-bridge-route-failed` diagnostic
and refuses to render rather than silently falling back to `gpu-raster`. The
legacy path can be manually re-enabled via the `useLegacyGpuRaster` flag for
emergency rollback.

## Dashboard Impact

- Expected row: `gpu-renderer.m30.route-to-kanvas-surface`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Implemented. Added RollbackConfig with useLegacyGpuRaster flag (default false); flipped default renderer to Kanvas-native; route-migrated-to-kanvas diagnostic emitted on first activation; gpu-raster deprecation messages updated. Visual parity verification deferred to M30-003. Evidence at `reports/gpu-renderer/2026-06-25-M30-002-evidence.md`.
- `done` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.

## Linear Labels

- `gpu-renderer`
- `milestone:M30`
- `area:kanvas-skia-bridge`
