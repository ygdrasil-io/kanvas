---
id: KGPU-M32-014
title: "Legacy decommission: device-scissor-simple-clips port (WideOpen/DeviceRect) / refuse (complex clips)"
<<<<<<< HEAD
status: done
=======
status: review
>>>>>>> master
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M32-001, KGPU-M2-003]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-014 - Legacy decommission: device-scissor-simple-clips port (WideOpen/DeviceRect) / refuse (complex clips)

## PM Note

Le clip simple par scissor de device (aucun clip + clip rectangulaire device) est
porté et appliqué dans chaque commande de dessin ; les clips complexes
(rectangle arrondi, chemin, clip composé) restent refusés et dépendent de
KGPU-M2-003. Le PM suit ce ticket car il distingue le clip simple déjà couvert
de la pile de clip complète encore non supportée.

## Problem

`GpuRendererLegacyRouteFamily.device-scissor-simple-clips` is a partial family
(decision matrix row 5):

- `WideOpen` and `DeviceRect` clips are dispatched in all 4 commands; the scissor
  rect is computed from clip bounds (`Surface.kt:213-215,270-273,387-392`) —
  **ported** baseline.
- `RoundedRect` / `Path` / `Complex` clips are **refused** with `unsupported_clip`
  at `Surface.kt:191,244,342` and `TextRunDispatch.kt:101`.

A true standalone clip-stack dispatch would require saveLayer/restore support
(see matrix concern #2); only the embedded device scissor is ported.

## Scope

Ported sub-cases (route_kind GPUNative):

- `WideOpen` and `DeviceRect` clips applied as a device scissor across the
  supported draw commands, with real GPU pixel parity vs an independent CPU
  reference for a clipped scene.

## Non-Goals

- Do not port RoundedRect/Path/Complex clips or a standalone clip-stack here.
- Do not add a short-lived complex-clip substitute (per AGENTS.md: refused clips
  stay formal refusals linked to KGPU-M2-003).
- Do not add hidden CPU-rendered clip texture compatibility.
- Refused clip kinds emit a stable `refuse:clip:unsupported_clip:<Kind>`
  diagnostic; they must not be silently served.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 5)

## Graphite Algorithm References

- n/a — not required for this slice (device scissor reuses existing dispatch;
  stencil/discard complex-clip algorithm study belongs to KGPU-M2-003).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "GPUNative" (WideOpen/DeviceRect) / "RefuseDiagnostic" (complex)
    val dumpRefs: List<String>,       // reports/gpu-renderer/... (clipped scene parity, when produced)
    val diagnostics: List<String>,    // refuse:clip:unsupported_clip:RoundedRect, ...:Path, ...:Complex
)
```

## Acceptance Criteria

- [ ] WideOpen and DeviceRect clip applied via device scissor with real GPU pixel
      parity vs an INDEPENDENT CPU reference for a clipped scene (similarity %,
      max channel delta, diff artifact) committed under `reports/gpu-renderer/`.
- [ ] RoundedRect/Path/Complex clips emit a stable
      `refuse:clip:unsupported_clip:<Kind>` diagnostic; a hermetic regression
      test asserts each diagnostic and links KGPU-M2-003.
- [ ] No short-lived complex-clip substitute is introduced (per AGENTS.md).

## Required Evidence

- WideOpen/DeviceRect baseline is exercised implicitly by every ported parity
  scene (all use WideOpen/DeviceRect clips):
  `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md` and
  `KGPU-M31-005`. A dedicated DeviceRect-clipped scene parity dump is
  **not produced yet** and should be added for this family.
- Complex-clip port: independent CPU pixel parity for rounded-rect/path clip +
  WGSL stencil-clip / discard-clip dispatch — **not produced yet** (refused;
  tracked by KGPU-M2-003).
- Hermetic refuse regression tests — **not produced yet**.

## Fallback / Refusal Behavior

- Complex clip kinds emit stable `refuse:clip:unsupported_clip:<Kind>`
  diagnostics; no silent CPU-rendered clip fallback.
- The `gpu-raster legacy` gate remains visible for the refused clip kinds until
  KGPU-M2-003 lands real port evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.device-scissor-simple-clips`
- Expected classification: `ImplementationCandidate` (WideOpen/DeviceRect) +
  `RefuseRequired`/`DependencyGated` (complex clips)
- Claim promotion allowed: no, unless Required Evidence is attached and reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer-scenes:compareKanvasSurfaceOffscreen -PsceneName=device-rect-clip -PsceneOutput=build/gpu-renderer-scenes/device-rect-clip.png
rtk ./gradlew --no-daemon :kanvas:test --tests "*ComplexClipRefuse*"
rtk ./gradlew --no-daemon :kanvas:test :kanvas-skia-bridge:test
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 5 (partial — port
  WideOpen/DeviceRect device scissor / refuse complex clips). Baseline covered
  implicitly by existing parity scenes; a dedicated clipped-scene dump is still
  owed. Complex-clip remainder refused and dependency-linked to KGPU-M2-003.
  Matrix concern #2 noted: only the embedded scissor is ported, not a standalone
  clip stack. No new evidence produced here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): added a DISPATCH-LEVEL hermetic
  refuse test — a fill command with a non-`WideOpen`/`DeviceRect` clip refuses
  `unsupported_clip:<ClipKind>`. HONESTY: complex clips are NOT constructible via
  the public bridge/Canvas API (`Canvas` always emits `WideOpen`; the bridge has
  no clip entrypoint), so this is a guard test, not a reachable end-to-end
  complex-clip refuse (asserted by `drawRect via public API produces a WideOpen
  clip`). Tests: `ClipRefuseTest`. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Device-scissor port
  remains dependency-gated.
- 2026-06-26 (Phase 2.C, still `proposed`): port-evidence consolidation. HONEST
  scoping — the "simple clip" port is effectively **WideOpen (full-surface)**,
  implicitly covered by all parity scenes (`Canvas` defaults to WideOpen; the
  bridge has no clip entrypoint). The **DeviceRect** scissor path is
  dispatch-capable (`Surface.kt:70`, `TextRunDispatch.kt:100`) but **NOT
  bridge-reachable end-to-end** — DeviceRect end-to-end parity is **NOT claimed**.
  Complex clips refuse `unsupported_clip`. See
  `reports/gpu-renderer/2026-06-26-m32-port-evidence.md` §KGPU-M32-014.
  Documentation-only; no new evidence; independent review still owed.


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
<<<<<<< HEAD
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
