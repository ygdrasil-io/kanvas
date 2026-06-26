---
id: KGPU-M32-021
title: "Legacy decommission: clear-discard-target-background port (surface-init clear)"
status: proposed
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-021 - Legacy decommission: clear-discard-target-background port (surface-init clear)

## PM Note

Le « clear / discard » et le fond de la cible sont gérés trivialement par
l'initialisation de la surface Kanvas (effacement en noir transparent avant tout
dessin). Ce ticket documente que cette famille est portée par le contrat
d'initialisation, sans route de clear par commande. Le PM suit ce ticket car il
ferme la dernière dépendance legacy de clear/discard.

## Problem

`GpuRendererLegacyRouteFamily.clear-discard-target-background` is a **trivial
port** family (decision matrix row 12). The Kanvas surface initializes with
`DEFAULT_CLEAR_COLOR = (0,0,0,0)` at `Surface.kt:145,468-470` via
`t.encode(clearColor=...)` before rendering any command. There is no explicit
`ClearDraw` command type — clear is part of the render-target initialization
contract (the legacy device equivalent is `SkWebGpuDevice` target clear). Legacy
retirement for this family requires documenting that the bridge surface init
covers the clear/discard/target-background behavior and verifying it.

## Scope

- Document and verify that Kanvas surface init clears to transparent black per
  the `GPUOffscreenTargetRequest` contract, so no per-command clear dispatch is
  needed.
- Confirm an empty surface (no draw commands) reads back as fully transparent
  (`nonTransparentPixels == 0`), demonstrating no remaining legacy clear-route
  dependency.

## Non-Goals

- Do not add a per-command `ClearDraw`/`Discard` dispatch route — clear is the
  init contract, not a draw command.
- Do not add hidden CPU-rendered clear/target-background compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 12)

## Graphite Algorithm References

- n/a — not required for this slice (trivial surface-init clear; no algorithm
  study required).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (surface-init clear)
    val dumpRefs: List<String>,       // empty-surface readback evidence (when produced)
    val diagnostics: List<String>,    // none — clear is the init contract
)
```

## Acceptance Criteria

- [ ] Documented that Kanvas surface init clears to transparent black per the
      `GPUOffscreenTargetRequest` contract (`Surface.kt:145,468-470`).
- [ ] An empty surface (no draw commands) is verified to read back with
      `nonTransparentPixels == 0`, confirming no remaining legacy clear-route
      dependency.

## Required Evidence

- Empty-surface readback (`nonTransparentPixels == 0`) demonstrating the
  surface-init clear — **not produced yet** (`proposed`).
- Pointer to `Surface.kt:145,468-470` (`DEFAULT_CLEAR_COLOR`, `t.encode(clearColor=...)`).

## Fallback / Refusal Behavior

- There is no per-command clear route to refuse; the init-contract clear is the
  only path. Any future explicit clear/discard command (if introduced) must emit
  a stable diagnostic rather than silently no-op.
- The `gpu-raster legacy` gate remains visible for this family until the
  empty-surface readback evidence is linked and reviewed.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.clear-discard-target-background`
- Expected classification: `ImplementationCandidate` (trivial surface-init clear)
- Claim promotion allowed: only after the empty-surface readback evidence is
  attached and independently reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=empty-surface-clear -PsceneOutput=build/gpu-renderer-scenes/empty-surface-clear.png
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 12 (`port`, trivial
  — surface init). Evidence (empty-surface readback) not yet produced.
- Discrepancy / open item (matrix concern #4): the matrix's
  `defaultReplacementTicket` for this family is the placeholder
  `route-specific-clear-discard-ticket-required`, which is NOT a real ticket id.
  It is therefore intentionally omitted from `depends_on` (no invented link). A
  real replacement ticket id must be assigned before Phase 3 retirement
  authorization (KGPU-M32-003).

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
