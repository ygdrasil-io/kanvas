---
id: KGPU-M31-001
title: "Default renderer activation — Kanvas as the production rendering path"
status: review
milestone: M31
priority: P0
owner_area: product-validation
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: true
release_blocking: true
adapter_required: true
depends_on: [KGPU-M30-004]
legacy_gate: null
---

# KGPU-M31-001 - Default renderer activation — Kanvas as the production rendering path

## PM Note

Ce ticket active Kanvas comme moteur de rendu par defaut en production. Apres ce
ticket, toutes les surfaces de dessin utilisent le pipeline GPU Kanvas natif.
C'est le point de basculement definitif ou le PM voit Kanvas devenir le renderer
principal.

## Problem

Kanvas has been validated for parity (M30) but is not the default rendering path.
The system still defaults to legacy routes unless explicitly opted in. Production
activation requires flipping the default so that all new surfaces use Kanvas
without explicit configuration.

## Scope

- Set Kanvas as the default rendering backend in `GPUSurface` factory
- Update default route configuration to use `kanvas-bridge-path` as primary
- Ensure `product_activation` flag is set to `true` at the system level
- Emit `renderer-activated-kanvas-production` diagnostic on startup
- Keep `useLegacyGpuRaster` as emergency rollback (default false)
- Update PM dashboard to reflect production activation status

## Non-Goals

- No removal of rollback capability (KGPU-M31-002)
- No API stability freeze (KGPU-M31-004)
- No new rendering features or draw families
- No performance tuning for production load

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/05-routing-policy.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation/README.md`
- `.upstream/specs/gpu-renderer/tickets/M23-performance-gates-pm-evidence/README.md`

## Design Sketch

```kotlin
// Default renderer configuration
object KanvasRendererConfig {
    const val DEFAULT_RENDERER = "kanvas-bridge-path"
    const val LEGACY_FALLBACK = "gpu-raster-legacy-path"
    const val PRODUCT_ACTIVATION = true
}

// On startup
fun initializeRenderer() {
    val config = KanvasRendererConfig
    if (config.PRODUCT_ACTIVATION) {
        emit(Diagnostic.rendererActivated("kanvas-production"))
    }
}
```

## Acceptance Criteria

- [ ] Kanvas is the default renderer for all new `GPUSurface` instances
- [ ] `product_activation` is `true` at the system configuration level
- [ ] Startup emits `renderer-activated-kanvas-production` diagnostic
- [ ] All existing scene tests pass with Kanvas as default
- [ ] PM dashboard shows production activation status
- [ ] Legacy rollback flag remains functional (default false)

## Required Evidence

- Default renderer configuration dump showing Kanvas as primary
- `product_activation` flag confirmation transcript
- Startup diagnostic transcript (`renderer-activated-kanvas-production`)
- Scene test results with Kanvas as default renderer
- PM dashboard row confirming production activation

## Fallback / Refusal Behavior

If Kanvas activation fails (GPU unavailable, backend error), the system emits
`kanvas-activation-failed` diagnostic and refuses to start rather than silently
falling back to legacy. The `useLegacyGpuRaster` flag can be manually set for
emergency recovery.

## Dashboard Impact

- Expected row: `gpu-renderer.m31.production-activation`
- Expected classification: `PromotedSupported` (after acceptance)
- Claim promotion allowed: yes, after all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test
rtk ./gradlew --no-daemon :integration-tests:test
```

## Status Notes

- `proposed`: Initial ticket.
- `review` (2026-06-25): Implemented. productActivation flag (default true) in RollbackConfig; renderer-activated-kanvas-production diagnostic emitted on first bridge activation; disable via -Dkanvas.product.activation.disable=true. Evidence at reports/gpu-renderer/2026-06-25-M31-001-evidence.md.

## Linear Labels

- `gpu-renderer`
- `milestone:M31`
- `area:product-validation`
