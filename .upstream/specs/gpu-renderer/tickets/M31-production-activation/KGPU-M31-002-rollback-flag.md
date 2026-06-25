---
id: KGPU-M31-002
title: "Rollback flag — emergency gpu-raster fallback control"
status: proposed
milestone: M31
priority: P0
owner_area: product-validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M31-001]
legacy_gate: null
---

# KGPU-M31-002 - Rollback flag — emergency gpu-raster fallback control

## PM Note

Ce ticket garantit que le PM dispose d'un levier de rollback d'urgence pour
revenir au chemin legacy `gpu-raster` si Kanvas rencontre un probleme en
production. Le flag est documente, teste, et emet des diagnostics clairs
lorsqu'il est active.

## Problem

Kanvas is the default renderer (M31-001), but production deployments need an
emergency rollback mechanism. Without a documented and tested rollback flag,
a Kanvas regression would require a full deployment rollback instead of a
simple configuration change.

## Scope

- Implement `useLegacyGpuRaster` runtime flag with clear documentation
- Flag can be set via system property, environment variable, or config file
- When enabled, emit `legacy-gpu-raster-rollback-active` warning diagnostic
- Ensure rollback path still passes all scene tests
- Document rollback procedure in operational runbook
- Add integration test verifying rollback flag behavior

## Non-Goals

- No automatic fallback detection (manual flag only)
- No partial rollback (all-or-nothing flag)
- No performance optimization of the rollback path
- No new features for the legacy path

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/tickets/M10-legacy-gpu-raster-migration/README.md`

## Design Sketch

```kotlin
object RollbackConfig {
    val useLegacyGpuRaster: Boolean by lazy {
        System.getProperty("kanvas.rollback.legacy-gpu-raster", "false").toBoolean()
            || System.getenv("KANVAS_ROLLBACK_LEGACY_GPU_RASTER")?.toBoolean() == true
    }
}

fun initializeRenderer() {
    if (RollbackConfig.useLegacyGpuRaster) {
        emit(Diagnostic.warning("legacy-gpu-raster-rollback-active"))
        activateLegacyPath()
    } else {
        activateKanvasPath()
    }
}
```

## Acceptance Criteria

- [ ] `useLegacyGpuRaster` flag is configurable via system property and environment variable
- [ ] When enabled, `legacy-gpu-raster-rollback-active` warning diagnostic is emitted
- [ ] Rollback path passes all existing scene tests
- [ ] Rollback procedure is documented
- [ ] Integration test verifies flag switch changes active renderer

## Required Evidence

- Flag configuration dump for system property and environment variable methods
- Diagnostic transcript showing `legacy-gpu-raster-rollback-active` warning
- Scene test results with rollback flag enabled
- Rollback procedure documentation
- Integration test source and transcript

## Fallback / Refusal Behavior

If Kanvas activation fails and the rollback flag is not set, the system refuses
to start with `kanvas-activation-failed` diagnostic. The rollback flag is the
documented recovery path. No automatic or silent fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.rollback-flag`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test -Dkanvas.rollback.legacy-gpu-raster=true
rtk ./gradlew --no-daemon :gpu-renderer:test -Dkanvas.rollback.legacy-gpu-raster=false
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :integration-tests:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:product-validation`
