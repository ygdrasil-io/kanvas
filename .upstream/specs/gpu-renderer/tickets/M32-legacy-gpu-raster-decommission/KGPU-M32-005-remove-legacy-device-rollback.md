---
id: KGPU-M32-005
title: "Remove legacy device, rollback branch, and module include (Option B)"
<<<<<<< HEAD
status: done
=======
status: review
>>>>>>> master
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: true
adapter_required: false
depends_on: [KGPU-M32-004]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-005 - Remove legacy device, rollback branch, and module include

## PM Note

Le peripherique GPU legacy (`SkWebGpuDevice` + classes dependantes) a ete
supprime chirurgicalement (Option B). Le module `:gpu-raster` est conserve
pour l'infrastructure partagee (validation WGSL, conformite pipeline, portes
retirement/shadow, WGSL genere, inventaire). La branche de rollback
(`-Dkanvas.rollback.legacy-gpu-raster` / `useLegacyGpuRaster`) a ete coupee.
L'Option A (suppression complete du module + relocalisation de l'infra) est
differee.

## Problem

Phase 5 of the legacy gpu-raster decommission: after the per-family retirement
gate is authorized and shared infra is relocated (KGPU-M32-004), delete the
legacy device source, sever the rollback branch, remove the module include
from `settings.gradle.kts`, and update docs/specs/routing accordingly.

## Scope

- Delete legacy device source (`SkWebGpuDevice`, `WebGpuContext`,
  `HeadlessTarget`, `WebGpuCoveragePlanSelector`, `SkWebGpuGlyphAtlas`,
  `GpuRendererFirstRoute*`, `GpuRendererShadowAdapter`) from `:gpu-raster`
- Delete device-dependent test files (~405 tests; keep 11 WGSL/conformance/gate tests)
- Delete `CrossBackendHarness`, `CrossTestHarness`, benchmarks, scene evidence/capture
- Delete `kanvas-skia-bridge/.../CompareBridgeVsLegacyGpuRaster.kt`
- Remove `useLegacyGpuRaster` + `SYSTEM_PROPERTY` + `ENV_VARIABLE` from `RollbackConfig.kt`
- Make `isKanvasRendererEnabled()` unconditional `true`; simplify `wrapIfEnabled`
- Remove rollback tests from `KanvasSkiaBridgeTest.kt`
- Remove `:gpu-raster` dependency from `:kanvas-skia-bridge`
- Clean up gpu-raster and root `build.gradle.kts` references to deleted files
- Update spec/routing/doc files to reflect legacy device removal
- Set KGPU-M32-005 → review

## Non-Goals

- Does NOT remove the `:gpu-raster` module include from `settings.gradle.kts`
  (module kept for shared WGSL/conformance/gate/inventory infra; Option A deferred)
- Does NOT relocate shared infra to `:gpu-renderer` (deferred to Option A later step)
- Does NOT clean up stale `inputs.file(...)` references in root `build.gradle.kts`
  custom verify tasks (non-breaking, outside `build` lifecycle; deferred)

## Spec Sources

- `docs/superpowers/plans/2026-06-26-legacy-gpu-raster-decommission.md` — Phase 5 spec
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`

## Design Sketch

Surgical deletion (Option B): remove the legacy device and its direct
dependencies while preserving the module and shared infra. The `:gpu-raster`
module stays for WGSL validation, generated WGSL, pipeline conformance tests,
retirement/shadow gates, and inventory tooling.

```
DELETE: SkWebGpuDevice.kt, WebGpuContext.kt, HeadlessTarget.kt,
        WebGpuCoveragePlanSelector.kt, SkWebGpuGlyphAtlas.kt,
        GpuRendererFirstRoute*, GpuRendererShadowAdapter.kt
DELETE: ~405 device-dependent test files
DELETE: benchmarks/, crossbackend/, testing/Cross*Harness*.kt,
        *SceneEvidence*, *SceneCapture*, PipelineKeyTelemetryTest.kt
DELETE: kanvas-skia-bridge/.../CompareBridgeVsLegacyGpuRaster.kt

KEEP: PipelineKey.kt, BlendPlan.kt, GpuRendererLegacyRetirementGates.kt,
      GpuRendererShadowParityGates.kt, tools/*, resources/shaders/*,
      resources/wgsl-diagnostics-allowlist.txt
KEEP: 11 WGSL/conformance/gate tests in gpu-raster/src/test/

SEVER: RollbackConfig.useLegacyGpuRaster (removed)
SEVER: SkiaKanvasSurface rollback branch (always wraps)
SEVER: kanvas-skia-bridge → :gpu-raster dependency
```

## Acceptance Criteria

- [x] All legacy device source files deleted (9 main + device-dependent tests)
- [x] `useLegacyGpuRaster` / `kanvas.rollback.legacy-gpu-raster`: 0 matches across codebase
- [x] `RollbackConfig.kt`: `useLegacyGpuRaster` + flags removed; `productActivation` kept
- [x] `SkiaKanvasSurface.kt`: `isKanvasRendererEnabled()` unconditional `true`
- [x] Rollback tests removed from `KanvasSkiaBridgeTest.kt`
- [x] `:kanvas-skia-bridge` no longer depends on `:gpu-raster`
- [x] Full build succeeds (188 tasks: 4 executed, 184 up-to-date)
- [x] Kept infra tasks pass (wgslValidateStrict, wgslValidateAll, pipelineConformanceTest, test 26/26)
- [x] Module tests pass (:kanvas-skia-bridge, :gpu-renderer, :kanvas)
- [x] `rtk git diff --check` clean
- [x] Spec/routing/doc/README updates committed
- [x] Deferred cleanup recorded in deletion report
- [x] M32 README updated (KGPU-M32-005 → `review`)

## Required Evidence

- `reports/gpu-renderer/2026-06-26-m32-005-legacy-device-deletion.md` — deletion report with file inventory, verification results, deferred cleanup
- `rtk git diff --check` — clean
- `rtk ./gradlew --no-daemon build` — BUILD SUCCESSFUL
- `rtk rg -n "useLegacyGpuRaster|kanvas.rollback.legacy-gpu-raster" --glob '*.kt'` — 0 matches
- `rtk rg -n "SkWebGpuDevice|WebGpuContext|HeadlessTarget|WebGpuCoveragePlanSelector|GpuRendererFirstRoute" --glob '!gpu-raster/**' --glob '*.kt'` — 0 matches (or only in reports/docs)

## Fallback / Refusal Behavior

No rollback target exists. Kanvas-native bridge is the sole, unconditional
render route. If the bridge cannot render a command, it emits a stable
`RefuseDiagnostic` (no silent CPU fallback).

## Dashboard Impact

- Expected row: `gpu-renderer.m32.legacy-device-deletion`
- Expected classification: `PolicyGated` → `ImplementationCandidate` (after acceptance)
- Claim promotion allowed: after independent review.

## Validation

```bash
rtk ./gradlew --no-daemon build
rtk ./gradlew --no-daemon :gpu-raster:wgslValidateStrict :gpu-raster:wgslValidateAll :gpu-raster:pipelineConformanceTest
rtk ./gradlew --no-daemon :gpu-raster:test
rtk ./gradlew --no-daemon :kanvas-skia-bridge:test :gpu-renderer:test :kanvas:test
rtk rg -n "useLegacyGpuRaster|kanvas.rollback.legacy-gpu-raster" --glob '*.kt'
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 5 ticket created in M32 scaffold.
- `review` (2026-06-26): Legacy device deleted (424 files, 106K lines removed);
  rollback branch severed; `:gpu-raster` module preserved for shared infra. Full
  build green. Docs/specs/routing updated to reflect sole Kanvas-bridge route.
  Deferred cleanup (Option A module removal, stale inputs.file references)
  recorded in deletion report. Deletion evidence at
  `reports/gpu-renderer/2026-06-26-m32-005-legacy-device-deletion.md`.
  Commit: 4bfdd9f.
<<<<<<< HEAD
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `legacy-cleanup`
- `legacy-gate:gpu-raster`
